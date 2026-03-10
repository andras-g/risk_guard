#!/usr/bin/env bash
# store-oauth-secret.sh — Store Google OAuth credentials in GCP Secret Manager
# Usage: ./scripts/store-oauth-secret.sh
#
# Interactive — prompts for Client ID and Client Secret, stores them in
# GCP Secret Manager (replaces placeholder values).

set -euo pipefail

GCP_PROJECT="gen-lang-client-0264363511"

echo "=== Store Google OAuth credentials in GCP Secret Manager ==="
echo "Project: $GCP_PROJECT"
echo ""

read -rp "Google OAuth Client ID: " CLIENT_ID
read -rsp "Google OAuth Client Secret: " CLIENT_SECRET
echo ""

echo ""
echo "Storing GOOGLE_CLIENT_ID_STAGING..."
echo -n "$CLIENT_ID" | gcloud secrets versions add GOOGLE_CLIENT_ID_STAGING \
  --project="$GCP_PROJECT" --data-file=-

echo "Storing GOOGLE_CLIENT_SECRET_STAGING..."
echo -n "$CLIENT_SECRET" | gcloud secrets versions add GOOGLE_CLIENT_SECRET_STAGING \
  --project="$GCP_PROJECT" --data-file=-

echo ""
echo "Done! Secrets stored in GCP Secret Manager."
echo "Run ./start-local.sh to use them."
