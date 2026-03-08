# Risk Guard Infrastructure (Terraform)

GCP infrastructure for Risk Guard — defined as Infrastructure as Code.

## Prerequisites

- [Terraform >= 1.6](https://developer.hashicorp.com/terraform/downloads)
- [gcloud CLI](https://cloud.google.com/sdk/docs/install) authenticated with a GCP account
- A GCP project with billing enabled

## First-Time Bootstrap

### 1. Create Terraform state bucket

```bash
gsutil mb -l europe-west3 gs://risk-guard-terraform-state
gsutil versioning set on gs://risk-guard-terraform-state
```

### 2. Populate Secret Manager secrets

Terraform automatically generates and stores the DB password (`DB_PASSWORD_*`) via `random_password`.
You only need to manually add the external credentials after the first `terraform apply`:

```bash
# Run AFTER terraform apply (secrets must exist before adding versions)
echo -n "your-jwt-secret-32-chars+"   | gcloud secrets versions add JWT_SECRET_STAGING --data-file=-
echo -n "your-google-client-secret"   | gcloud secrets versions add GOOGLE_CLIENT_SECRET_STAGING --data-file=-
echo -n "your-microsoft-client-secret"| gcloud secrets versions add MICROSOFT_CLIENT_SECRET_STAGING --data-file=-
echo -n "your-resend-api-key"         | gcloud secrets versions add RESEND_API_KEY_STAGING --data-file=-
```

### 3. Apply infrastructure

Provide your GitHub repository name via `var.github_repository` (used for Workload Identity Federation).

```bash
cd infra/

# Staging
terraform init
terraform workspace new staging
terraform apply \
  -var="project_id=YOUR_GCP_PROJECT_ID" \
  -var="environment=staging" \
  -var="backend_image=europe-west3-docker.pkg.dev/YOUR_GCP_PROJECT_ID/risk-guard/backend:latest" \
  -var="frontend_bucket_name=risk-guard-frontend-staging" \
  -var="github_repository=YOUR_GITHUB_ORG/risk_guard"

# Production
terraform workspace new production
terraform apply \
  -var="project_id=YOUR_GCP_PROJECT_ID" \
  -var="environment=production" \
  -var="backend_image=europe-west3-docker.pkg.dev/YOUR_GCP_PROJECT_ID/risk-guard/backend:latest" \
  -var="frontend_bucket_name=risk-guard-frontend-production" \
  -var="github_repository=YOUR_GITHUB_ORG/risk_guard"
```

> **Note on DB password:** Terraform generates a secure random password and stores it in Secret Manager
> automatically on first apply. No manual secret population needed for `DB_PASSWORD`. Other secrets
> (JWT, SSO credentials, Resend API key) must still be populated manually.

## GitHub Actions Setup

After Terraform applies successfully, configure GitHub repository secrets/variables:

| Name | Value | Type |
|------|-------|------|
| `GCP_PROJECT_ID` | Your GCP project ID | Secret |
| `WIF_PROVIDER` | Output of `terraform output workload_identity_pool_provider` | Secret |
| `CLOUD_RUN_SA_EMAIL` | Output of `terraform output cloud_run_service_account_email` | Secret |
| `CLOUD_SQL_CONNECTION_NAME` | Output of `terraform output cloud_sql_connection_name` | Variable |
| `FRONTEND_BUCKET_STAGING` | `risk-guard-frontend-staging` | Variable |
| `FRONTEND_BUCKET_PRODUCTION` | `risk-guard-frontend-production` | Variable |
| `CLOUD_RUN_SERVICE_STAGING` | `risk-guard-backend-staging` | Variable |
| `CLOUD_RUN_SERVICE_PRODUCTION` | `risk-guard-backend-production` | Variable |
| `ARTIFACT_REGISTRY_REGION` | `europe-west3` | Variable |

## Architecture Notes

- **Region:** `europe-west3` (Frankfurt) — EU data residency (NFR5)
- **Cloud SQL:** Private IP only, accessed via Cloud SQL Auth Proxy (socket-based)
- **Secrets:** All sensitive values in Secret Manager, injected as Cloud Run env vars
- **Auth:** Workload Identity Federation — no service account key files in GitHub
- **Min instances:** 1 always-on (MVP — cost ~$15-20/month, acceptable for production warmth)
- **GraalVM:** Deferred (ADR-3) — JVM build used for MVP

## Rollback

Cloud Run automatically rolls back to the previous revision on failed health checks.
To manually roll back:

```bash
gcloud run services update-traffic risk-guard-backend-staging \
  --to-revisions=PREVIOUS_REVISION=100 \
  --region=europe-west3
```
