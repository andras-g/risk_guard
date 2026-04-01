#!/usr/bin/env bash
# Tear down the staging environment.
# Deletes both Cloud Run services (the only resources with ongoing cost).
set -euo pipefail

PROJECT_ID="gen-lang-client-0264363511"
REGION="europe-west3"
BACKEND_SERVICE="risk-guard-backend-staging"
FRONTEND_SERVICE="risk-guard-frontend-staging"

delete_service() {
  local service="$1"
  if gcloud run services describe "$service" \
      --region="$REGION" \
      --project="$PROJECT_ID" > /dev/null 2>&1; then
    echo "==> Deleting $service..."
    gcloud run services delete "$service" \
      --region="$REGION" \
      --project="$PROJECT_ID" \
      --quiet
  else
    echo "    $service: already down"
  fi
}

delete_service "$BACKEND_SERVICE"
delete_service "$FRONTEND_SERVICE"

echo ""
echo "Staging is DOWN."
