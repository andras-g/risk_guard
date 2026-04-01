#!/usr/bin/env bash
# Spin up the staging environment.
# Uses the latest image already in Artifact Registry (:latest tag).
# Builds the frontend locally and uploads it to GCS.
#
# Prerequisites: gcloud authenticated, npm available, node 20+
set -euo pipefail

PROJECT_ID="gen-lang-client-0264363511"
REGION="europe-west3"
SERVICE="risk-guard-backend-staging"
BUCKET="risk-guard-frontend-staging"
IMAGE="europe-west3-docker.pkg.dev/${PROJECT_ID}/risk-guard/backend:latest"

echo "==> Deploying backend to Cloud Run..."
gcloud run deploy "$SERVICE" \
  --image="$IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --min-instances=0 \
  --max-instances=10 \
  --set-secrets="NEON_DATABASE_URL=NEON_DATABASE_URL_STAGING:latest,JWT_SECRET=JWT_SECRET_STAGING:latest,GOOGLE_CLIENT_SECRET=GOOGLE_CLIENT_SECRET_STAGING:latest,GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID_STAGING:latest,MICROSOFT_CLIENT_SECRET=MICROSOFT_CLIENT_SECRET_STAGING:latest,MICROSOFT_CLIENT_ID=MICROSOFT_CLIENT_ID_STAGING:latest,RESEND_API_KEY=RESEND_API_KEY_STAGING:latest" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=staging,FRONTEND_URL=https://storage.googleapis.com/${BUCKET}" \
  --service-account="risk-guard-backend-sa@${PROJECT_ID}.iam.gserviceaccount.com" \
  --allow-unauthenticated \
  --project="$PROJECT_ID"

BACKEND_URL=$(gcloud run services describe "$SERVICE" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --format="value(status.url)")

echo ""
echo "==> Backend is live: $BACKEND_URL"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../frontend"

echo ""
echo "==> Building frontend (Nuxt static generation)..."
cd "$FRONTEND_DIR"
npm ci
NUXT_PUBLIC_API_BASE="$BACKEND_URL" NODE_OPTIONS="--max-old-space-size=4096" npm run generate

echo ""
echo "==> Uploading frontend to GCS..."
gsutil -m rsync -r -d .output/public "gs://${BUCKET}"
gsutil iam ch allUsers:objectViewer "gs://${BUCKET}"

echo ""
echo "======================================"
echo "Staging is UP"
echo "  Backend:  $BACKEND_URL"
echo "  Frontend: https://storage.googleapis.com/${BUCKET}/index.html"
echo "======================================"
