# Encryption & TLS Runbook

**Last updated:** 2026-03-18
**Story:** 3.4 — Encryption & Infrastructure Hardening

---

## 1. Encryption at Rest

GCP Cloud SQL PostgreSQL 17 uses **AES-256 encryption at rest** by default with Google-managed keys. No code or configuration action is required.

### Verification

1. Open GCP Console > SQL > Instance > Configuration > Storage.
2. Confirm **Encryption: Google-managed key** is shown.

### CMEK (Customer-Managed Encryption Keys)

- **Not configured for MVP.** Google-managed AES-256 is sufficient.
- If a GDPR audit requires CMEK, it must be set at **instance creation time** — this means creating a new Cloud SQL instance and migrating data.
- CMEK adds ~€5-10/month for Cloud KMS key costs.

---

## 2. SSL Enforcement (Cloud SQL)

SSL enforcement ensures that any **direct TCP connection** (bypassing the Cloud SQL Auth Proxy) is rejected if it does not use TLS.

> **Note:** Cloud Run connects via the Cloud SQL Auth Proxy socket factory, which handles encryption independently. `require_ssl` is a defense-in-depth measure for direct connections.

### Enable SSL Enforcement

```bash
gcloud sql instances patch riskguard-prod \
  --require-ssl \
  --project=YOUR_GCP_PROJECT_ID
```

### Verify SSL Enforcement

```bash
gcloud sql instances describe riskguard-prod \
  --project=YOUR_GCP_PROJECT_ID \
  --format='value(settings.ipConfiguration.requireSsl)'
# Expected output: True
```

---

## 3. TLS in Transit Verification

### Via /actuator/health Endpoint

The `DatabaseTlsHealthIndicator` Spring Boot component reports TLS status in the health endpoint.

**Staging (details visible to all callers):**

```bash
curl -s https://risk-guard-backend-staging-XXXX.a.run.app/actuator/health | jq '.components.databaseTls'
```

Expected response:

```json
{
  "status": "UP",
  "details": {
    "ssl": true,
    "tlsVersion": "TLSv1.3",
    "cipher": "TLS_AES_256_GCM_SHA384"
  }
}
```

**Production (details visible only to authenticated callers):**

```bash
curl -s -H "Authorization: Bearer <JWT_TOKEN>" \
  https://app.riskguard.hu/actuator/health | jq '.components.databaseTls'
```

### Via PostgreSQL Directly

If you have direct database access (psql or Cloud SQL Auth Proxy):

```sql
-- Check if current connection uses SSL
SELECT ssl_is_used();
-- Expected: true

-- Check TLS version and cipher
SELECT version, cipher FROM pg_stat_ssl WHERE pid = pg_backend_pid();
-- Expected: TLSv1.3 | TLS_AES_256_GCM_SHA384
```

### If DatabaseTlsHealthIndicator Reports DOWN

1. **Check Cloud SQL Auth Proxy logs** in Cloud Run (GCP Console > Cloud Run > Logs).
2. **Verify JDBC URL** includes `socketFactory=com.google.cloud.sql.postgres.SocketFactory` in `application-prod.yml`.
3. **Check INSTANCE_CONNECTION_NAME** environment variable matches the Cloud SQL instance connection name.
4. **For Neon (staging):** If `ssl_is_used()` returns `false`, add `?sslmode=require` to the `NEON_DATABASE_URL` in GCP Secret Manager. Do NOT add sslmode to `application-staging.yml` directly.

---

## 4. Cloud Scheduler: Business-Hours Scaling

Two Cloud Scheduler jobs manage Cloud Run min-instances to balance availability and cost.

| Job Name | Schedule | Effect |
|---|---|---|
| `riskguard-scale-up` | 08:00 Budapest local (CET/CEST) Mon-Fri | Sets `minInstanceCount = 1` |
| `riskguard-scale-down` | 17:00 Budapest local (CET/CEST) Mon-Fri | Sets `minInstanceCount = 0` |

> **Time zone:** `Europe/Budapest` (handles CET/CEST transitions automatically).

### Create Scale-Up Job

```bash
gcloud scheduler jobs create http riskguard-scale-up \
  --schedule='0 8 * * 1-5' \
  --time-zone='Europe/Budapest' \
  --location=europe-west3 \
  --uri='https://run.googleapis.com/v2/projects/${GCP_PROJECT_ID}/locations/europe-west3/services/riskguard-backend-production?updateMask=template.scaling.minInstanceCount' \
  --http-method=PATCH \
  --headers='Content-Type=application/json' \
  --message-body='{"template":{"scaling":{"minInstanceCount":1}}}' \
  --oauth-service-account-email=${CLOUD_RUN_SA_EMAIL} \
  --project=${GCP_PROJECT_ID}
```

### Create Scale-Down Job

```bash
gcloud scheduler jobs create http riskguard-scale-down \
  --schedule='0 17 * * 1-5' \
  --time-zone='Europe/Budapest' \
  --location=europe-west3 \
  --uri='https://run.googleapis.com/v2/projects/${GCP_PROJECT_ID}/locations/europe-west3/services/riskguard-backend-production?updateMask=template.scaling.minInstanceCount' \
  --http-method=PATCH \
  --headers='Content-Type=application/json' \
  --message-body='{"template":{"scaling":{"minInstanceCount":0}}}' \
  --oauth-service-account-email=${CLOUD_RUN_SA_EMAIL} \
  --project=${GCP_PROJECT_ID}
```

### Manually Trigger a Job

```bash
# Trigger scale-up manually
gcloud scheduler jobs run riskguard-scale-up --location=europe-west3

# Trigger scale-down manually
gcloud scheduler jobs run riskguard-scale-down --location=europe-west3
```

### Update Schedule (if business hours change)

```bash
gcloud scheduler jobs update http riskguard-scale-up \
  --schedule='0 7 * * 1-5' \
  --location=europe-west3
# Example: changes scale-up to 07:00 Budapest local time (Mon-Fri)
```

### Cost Impact

- Scaling to 0 overnight (~15h) and weekends (~62h/week) eliminates idle Cloud Run charges for ~77/168 hours per week (~46% compute idle cost reduction).
- Off-hours cold starts take ~3-5s (JVM, not GraalVM native). Acceptable for off-hours access (no SLA outside business hours).

### Deployment Interaction

The `deploy.yml` workflow sets `--min-instances=1` for production deployments. Cloud Scheduler will reset it to 0 at 17:00 as normal. This is intentional — do NOT remove `--min-instances=1` from deploy.yml.

---

## 5. Quick Reference

| Concern | Status | How to Verify |
|---|---|---|
| Encryption at rest (AES-256) | Automatic (GCP-managed) | GCP Console > Cloud SQL > Encryption |
| SSL enforcement on Cloud SQL | Enabled via `gcloud` | `gcloud sql instances describe ... --format=...` |
| TLS in transit | Verified by health indicator | `GET /actuator/health` → `databaseTls.status: UP` |
| Business-hours scaling | Cloud Scheduler jobs | `gcloud scheduler jobs list --location=europe-west3` |
