#!/usr/bin/env bash
# Spin up the staging environment.
#
# Backend:  deploys latest image from Artifact Registry to Cloud Run
# Frontend: builds Nuxt static files locally, packages into nginx Docker image,
#           pushes to Artifact Registry, deploys to Cloud Run
#
# Prerequisites: gcloud authenticated, npm + node 20+, Docker running
set -euo pipefail

PROJECT_ID="gen-lang-client-0264363511"
REGION="europe-west3"
REGISTRY="europe-west3-docker.pkg.dev/${PROJECT_ID}/risk-guard"
BACKEND_SERVICE="risk-guard-backend-staging"
FRONTEND_SERVICE="risk-guard-frontend-staging"
BACKEND_IMAGE="${REGISTRY}/backend:latest"
FRONTEND_IMAGE="${REGISTRY}/frontend:staging"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../frontend"

# ── Backend ────────────────────────────────────────────────────────────────
echo "==> Deploying backend to Cloud Run..."
gcloud run deploy "$BACKEND_SERVICE" \
  --image="$BACKEND_IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --min-instances=0 \
  --max-instances=10 \
  --set-secrets="NEON_DATABASE_URL=NEON_DATABASE_URL_STAGING:latest,JWT_SECRET=JWT_SECRET_STAGING:latest,GOOGLE_CLIENT_SECRET=GOOGLE_CLIENT_SECRET_STAGING:latest,GOOGLE_CLIENT_ID=GOOGLE_CLIENT_ID_STAGING:latest,MICROSOFT_CLIENT_SECRET=MICROSOFT_CLIENT_SECRET_STAGING:latest,MICROSOFT_CLIENT_ID=MICROSOFT_CLIENT_ID_STAGING:latest,RESEND_API_KEY=RESEND_API_KEY_STAGING:latest" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=staging" \
  --service-account="risk-guard-backend-sa@${PROJECT_ID}.iam.gserviceaccount.com" \
  --allow-unauthenticated \
  --project="$PROJECT_ID"

BACKEND_URL=$(gcloud run services describe "$BACKEND_SERVICE" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --format="value(status.url)")

echo ""
echo "==> Backend is live: $BACKEND_URL"

# Patch FRONTEND_URL now that we have the backend URL
gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --update-env-vars="FRONTEND_URL=__PLACEHOLDER__" > /dev/null 2>&1 || true
# (will be set properly once frontend URL is known — see below)

# ── Frontend ───────────────────────────────────────────────────────────────
echo ""
echo "==> Building frontend (Nuxt static generation)..."
cd "$FRONTEND_DIR"
npm ci
NUXT_PUBLIC_API_BASE="$BACKEND_URL" NODE_OPTIONS="--max-old-space-size=4096" npm run generate

echo ""
echo "==> Building frontend Docker image..."
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
docker build \
  --file Dockerfile.staging \
  --tag "$FRONTEND_IMAGE" \
  .

echo ""
echo "==> Pushing frontend image..."
docker push "$FRONTEND_IMAGE"

echo ""
echo "==> Deploying frontend to Cloud Run..."
gcloud run deploy "$FRONTEND_SERVICE" \
  --image="$FRONTEND_IMAGE" \
  --region="$REGION" \
  --platform=managed \
  --min-instances=0 \
  --max-instances=3 \
  --allow-unauthenticated \
  --port=8080 \
  --service-account="risk-guard-backend-sa@${PROJECT_ID}.iam.gserviceaccount.com" \
  --project="$PROJECT_ID"

FRONTEND_URL=$(gcloud run services describe "$FRONTEND_SERVICE" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --format="value(status.url)")

# ── Patch backend FRONTEND_URL now that we know the frontend URL ───────────
gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --update-env-vars="FRONTEND_URL=${FRONTEND_URL}" > /dev/null

echo ""
echo "======================================"
echo "Staging is UP"
echo "  Backend:  $BACKEND_URL"
echo "  Frontend: $FRONTEND_URL"
echo "======================================"
