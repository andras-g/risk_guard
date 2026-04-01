#!/usr/bin/env bash
# Tear down the staging environment.
# Deletes the Cloud Run service (the only resource with ongoing cost).
# The GCS bucket is left intact for the next staging-up.
set -euo pipefail

PROJECT_ID="gen-lang-client-0264363511"
REGION="europe-west3"
SERVICE="risk-guard-backend-staging"

if gcloud run services describe "$SERVICE" \
    --region="$REGION" \
    --project="$PROJECT_ID" > /dev/null 2>&1; then
  echo "==> Deleting Cloud Run service: $SERVICE..."
  gcloud run services delete "$SERVICE" \
    --region="$REGION" \
    --project="$PROJECT_ID" \
    --quiet
  echo ""
  echo "Staging is DOWN. GCS bucket retained for next spin-up."
else
  echo "Staging is already down."
fi
