#!/usr/bin/env bash
# start-local.sh — Start the full Risk Guard stack locally
# Usage: ./start-local.sh
#
# Starts: PostgreSQL (Docker) → Backend (Spring Boot) → Frontend (Nuxt dev)
# Stop:   Ctrl+C (kills backend & frontend, keeps PostgreSQL running)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  echo ""
  echo "Shutting down..."
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null && echo "  Frontend stopped"
  [[ -n "$BACKEND_PID" ]]  && kill "$BACKEND_PID"  2>/dev/null && echo "  Backend stopped"
  echo "  PostgreSQL left running (docker compose down to stop)"
  exit 0
}
trap cleanup SIGINT SIGTERM

# ── 1. PostgreSQL ─────────────────────────────────────────────────────────────
echo "==> Starting PostgreSQL..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d --wait
echo "    PostgreSQL ready on localhost:5432"

# ── 2. Backend (Spring Boot) ─────────────────────────────────────────────────
echo "==> Starting Backend (Spring Boot)..."
cd "$ROOT_DIR/backend"
./gradlew bootRun \
  -Dspring.docker.compose.enabled=false \
  -Dspring.devtools.restart.enabled=true \
  > >(while IFS= read -r line; do echo "[backend]  $line"; done) \
  2>&1 &
BACKEND_PID=$!
echo "    Backend PID=$BACKEND_PID (http://localhost:8080)"

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
echo "  Risk Guard is running locally!"
echo "  Frontend:  http://localhost:3000"
echo "  Backend:   http://localhost:8080"
echo "  Database:  localhost:5432/riskguard"
echo "  Press Ctrl+C to stop"
echo "=========================================="

wait
