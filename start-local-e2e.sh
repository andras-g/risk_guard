#!/usr/bin/env bash
# start-local-e2e.sh — Start the Risk Guard stack for Playwright E2E tests
# Usage: ./start-local-e2e.sh
#
# Differences from start-local.sh:
#   - Backend runs with SPRING_PROFILES_ACTIVE=e2e, which:
#       * Enables TestAuthController (POST /api/test/auth/login bypass)
#       * Applies R__e2e_test_data.sql seed (e2e@riskguard.hu test user)
#   - OAuth secrets are set to dummy values (E2E tests bypass OAuth)
#   - After the stack is up, prints the Playwright run command
#
# Then in a second terminal run:
#   cd frontend && npx playwright test
#   cd frontend && npx playwright test --ui   (interactive UI mode)
#
# Stop: Ctrl+C (kills backend & frontend, keeps PostgreSQL running)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""
GCP_PROJECT="gen-lang-client-0264363511"

cleanup() {
  echo ""
  echo "Shutting down E2E stack..."
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null && echo "  Frontend stopped"
  [[ -n "$BACKEND_PID" ]]  && kill "$BACKEND_PID"  2>/dev/null && echo "  Backend stopped"
  echo "  PostgreSQL left running (docker compose down to stop)"
  exit 0
}
trap cleanup SIGINT SIGTERM

# ── 0. Fetch JWT secret from GCP (needed for token signing) ─────────────────
echo "==> Fetching JWT secret from GCP Secret Manager..."
JWT_SECRET_VAL=$(gcloud secrets versions access latest \
  --secret="JWT_SECRET_STAGING" \
  --project="$GCP_PROJECT" 2>/dev/null) || true

if [[ -z "$JWT_SECRET_VAL" || "$JWT_SECRET_VAL" == placeholder* ]]; then
  echo "    ⚠  JWT_SECRET_STAGING not found — using local dev default"
  JWT_SECRET_VAL="local-dev-secret-32-chars-long-at-least-123456"
else
  echo "    ✓ JWT_SECRET loaded"
fi
export JWT_SECRET="$JWT_SECRET_VAL"

# OAuth credentials are not needed for E2E (tests bypass OAuth entirely)
export GOOGLE_CLIENT_ID="dummy-e2e"
export GOOGLE_CLIENT_SECRET="dummy-e2e"
export MICROSOFT_CLIENT_ID="dummy-e2e"
export MICROSOFT_CLIENT_SECRET="dummy-e2e"

# ── 1. PostgreSQL ─────────────────────────────────────────────────────────────
echo "==> Starting PostgreSQL..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d --wait
echo "    PostgreSQL ready on localhost:5432"

# ── 2. Backend (Spring Boot with e2e profile) ─────────────────────────────────
echo "==> Starting Backend with SPRING_PROFILES_ACTIVE=e2e..."
cd "$ROOT_DIR/backend"
SPRING_PROFILES_ACTIVE=e2e ./gradlew bootRun \
  -Dspring.docker.compose.enabled=false \
  -Dspring.devtools.restart.enabled=false \
  > >(while IFS= read -r line; do echo "[backend]  $line"; done) \
  2>&1 &
BACKEND_PID=$!
echo "    Backend PID=$BACKEND_PID (http://localhost:8080)"
echo "    Profile: e2e (TestAuthController active, test seed applied)"

# Wait for backend health
echo "    Waiting for backend to become healthy..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "    Backend healthy!"
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "    ERROR: Backend process died. Check logs above."
    exit 1
  fi
  sleep 2
done

# ── 3. Frontend (Nuxt dev) ───────────────────────────────────────────────────
echo "==> Starting Frontend (Nuxt dev)..."
cd "$ROOT_DIR/frontend"
npm run dev \
  > >(while IFS= read -r line; do echo "[frontend] $line"; done) \
  2>&1 &
FRONTEND_PID=$!
echo "    Frontend PID=$FRONTEND_PID (http://localhost:3000)"

# ── Ready ────────────────────────────────────────────────────────────────────
echo ""
echo "=========================================="
echo "  Risk Guard E2E stack is running!"
echo "  Frontend:  http://localhost:3000"
echo "  Backend:   http://localhost:8080"
echo "  Profile:   e2e (auth bypass active)"
echo ""
echo "  Run E2E tests in a second terminal:"
echo "  cd frontend && npx playwright test"
echo "  cd frontend && npx playwright test --ui"
echo ""
echo "  Press Ctrl+C to stop"
echo "=========================================="

wait
