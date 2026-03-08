# Deployment Runbook — Risk Guard GCP

## Overview

Risk Guard deploys to GCP using:
- **Backend:** Cloud Run (`europe-west3`) — containerized Spring Boot
- **Frontend:** Cloud Storage + Cloud CDN — Nuxt hybrid rendering
- **Database:** Cloud SQL PostgreSQL 17 (private IP)
- **Secrets:** GCP Secret Manager (no secrets in code or GitHub env vars)
- **CI/CD:** GitHub Actions with Workload Identity Federation (no SA key files)

---

## GCP Setup (First-Time Bootstrap)

### Prerequisites

- GCP project with billing enabled
- `gcloud` CLI authenticated: `gcloud auth login && gcloud config set project YOUR_PROJECT_ID`
- Terraform >= 1.6 installed

### Step 1: Create Terraform state bucket

```bash
gsutil mb -l europe-west3 gs://risk-guard-terraform-state
gsutil versioning set on gs://risk-guard-terraform-state
```

### Step 2: Populate Secret Manager secrets

Terraform creates the Secret Manager *containers* automatically. After the first
`terraform apply`, add secret versions for the external credentials:

```bash
ENV=staging  # or 'production'

echo -n "your-jwt-secret-32-chars+"    | gcloud secrets versions add JWT_SECRET_${ENV^^}      --data-file=-
echo -n "your-google-client-secret"    | gcloud secrets versions add GOOGLE_CLIENT_SECRET_${ENV^^}    --data-file=-
echo -n "your-microsoft-client-secret" | gcloud secrets versions add MICROSOFT_CLIENT_SECRET_${ENV^^} --data-file=-
echo -n "your-resend-api-key"          | gcloud secrets versions add RESEND_API_KEY_${ENV^^}  --data-file=-
```

### Step 3: Set the GitHub repository for Workload Identity

Provide your repository via the `github_repository` variable (e.g., `andras-org/risk_guard`) when running Terraform.

### Step 4: Apply Terraform

```bash
cd infra/

terraform init

# Staging
terraform workspace new staging || terraform workspace select staging
terraform apply \
  -var="project_id=YOUR_GCP_PROJECT_ID" \
  -var="environment=staging" \
  -var="backend_image=europe-west3-docker.pkg.dev/YOUR_GCP_PROJECT_ID/risk-guard/backend:latest" \
  -var="frontend_bucket_name=risk-guard-frontend-staging"

# Note Terraform outputs — you'll need these for GitHub secrets
terraform output

# Production
terraform workspace new production || terraform workspace select production
terraform apply \
  -var="project_id=YOUR_GCP_PROJECT_ID" \
  -var="environment=production" \
  -var="backend_image=europe-west3-docker.pkg.dev/YOUR_GCP_PROJECT_ID/risk-guard/backend:latest" \
  -var="frontend_bucket_name=risk-guard-frontend-production"
```

### Step 5: Configure GitHub repository secrets and variables

Go to **Settings → Secrets and variables → Actions** in your GitHub repository.

**Secrets** (sensitive — encrypted):

| Name | Value |
|------|-------|
| `GCP_PROJECT_ID` | Your GCP project ID |
| `WIF_PROVIDER` | `terraform output workload_identity_pool_provider` |
| `CLOUD_RUN_SA_EMAIL` | `terraform output cloud_run_service_account_email` |

**Variables** (non-sensitive — plaintext):

| Name | Value |
|------|-------|
| `CLOUD_RUN_SERVICE_STAGING` | `risk-guard-backend-staging` |
| `CLOUD_RUN_SERVICE_PRODUCTION` | `risk-guard-backend-production` |
| `CLOUD_SQL_CONNECTION_NAME` | `terraform output cloud_sql_connection_name` (staging) |
| `CLOUD_SQL_CONNECTION_NAME_PROD` | `terraform output cloud_sql_connection_name` (production) |
| `FRONTEND_BUCKET_STAGING` | `risk-guard-frontend-staging` |
| `FRONTEND_BUCKET_PRODUCTION` | `risk-guard-frontend-production` |
| `STAGING_CLOUD_RUN_URL` | Cloud Run staging URL (from `terraform output cloud_run_url`) |
| `PRODUCTION_CLOUD_RUN_URL` | Cloud Run production URL |
| `ARTIFACT_REGISTRY_REGION` | `europe-west3` |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID (optional until SSO is configured) |
| `MICROSOFT_CLIENT_ID` | Microsoft OAuth client ID (optional until SSO is configured) |

---

## Deploy Process

### Deploy to Staging

Push any commit to the `main` branch:

```bash
git push origin main
```

The `deploy.yml` workflow runs automatically:
1. Builds backend Docker image and pushes to Artifact Registry (tagged with `$GITHUB_SHA`)
2. Deploys backend to Cloud Run staging
3. Builds Nuxt frontend
4. Syncs frontend assets to Cloud Storage staging bucket
5. Invalidates Cloud CDN cache

### Deploy to Production

Create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `deploy-production` job requires **manual approval** via GitHub environment protection rules. Configure this in **Settings → Environments → production → Required reviewers**.

---

## Environment Variables Reference

### Backend (Cloud Run env vars)

All injected automatically from Secret Manager by the deploy workflow.

| Variable | Source | Description |
|----------|--------|-------------|
| `SPRING_PROFILES_ACTIVE` | Deploy step | Must be `prod` |
| `INSTANCE_CONNECTION_NAME` | Cloud Run var | Cloud SQL connection string |
| `DB_NAME` | Deploy step | `riskguard` |
| `DB_USER` | Deploy step | `riskguard` |
| `DB_PASSWORD` | Secret Manager | Cloud SQL password |
| `JWT_SECRET` | Secret Manager | JWT signing key (min 32 chars) |
| `GOOGLE_CLIENT_ID` | Deploy step | Google OAuth app client ID |
| `GOOGLE_CLIENT_SECRET` | Secret Manager | Google OAuth secret |
| `MICROSOFT_CLIENT_ID` | Deploy step | Microsoft Entra app client ID |
| `MICROSOFT_CLIENT_SECRET` | Secret Manager | Microsoft Entra secret |
| `RESEND_API_KEY` | Secret Manager | Resend transactional email API key |
| `FRONTEND_URL` | Deploy step | Frontend HTTPS URL |

### Frontend (Nuxt runtime config)

| Variable | Description |
|----------|-------------|
| `NUXT_PUBLIC_API_BASE` | Backend Cloud Run URL (e.g., `https://risk-guard-backend-staging-xxx.run.app`) |

---

## Health Checks

The backend exposes Spring Boot Actuator health endpoints:

- **Liveness:** `GET /actuator/health/liveness` — returns `200` if the app is alive
- **Readiness:** `GET /actuator/health/readiness` — returns `200` if the app is ready to serve (DB + Flyway healthy)

Cloud Run uses these for automatic traffic management and rollback.

---

## Rollback

### Automatic Rollback

Cloud Run automatically rolls back to the previous revision if:
- Health checks fail after deployment
- The new revision crashes on startup

### Manual Rollback

```bash
# List recent revisions
gcloud run revisions list \
  --service=risk-guard-backend-staging \
  --region=europe-west3

# Roll back to a specific revision
gcloud run services update-traffic risk-guard-backend-staging \
  --to-revisions=risk-guard-backend-staging-00042-xyz=100 \
  --region=europe-west3

# Roll back production
gcloud run services update-traffic risk-guard-backend-production \
  --to-revisions=PREVIOUS_REVISION_ID=100 \
  --region=europe-west3
```

---

## Architecture Notes

- **EU Data Residency (NFR5):** All resources in `europe-west3` (Frankfurt)
- **GraalVM:** Deferred (ADR-3) — JVM (`eclipse-temurin:25-jre-alpine`) used for MVP
- **Cloud SQL Auth Proxy:** Socket-based connection, no DB password in env for connection auth
- **Min instances:** 1 always-on (MVP, ~$15-20/month). Cloud Scheduler can be added later for 08:00-17:00 CET scaling
- **Workload Identity Federation:** GitHub Actions authenticates without storing GCP service account key files
