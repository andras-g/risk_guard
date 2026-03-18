# risk_guard - Multi-tenant Partner Risk Screening & EPR Compliance

## 🚀 Quick Start (Development)

1.  **Start Database:**
    ```bash
    docker compose up -d
    ```

2.  **Run Backend:**
    ```bash
    cd backend
    ./gradlew bootRun
    ```

3.  **Run Frontend:**
    ```bash
    cd frontend
    npm install
    npm run dev
    ```

## 🧪 E2E Tests (Playwright)

E2E tests run against a real backend + real frontend using Playwright (Chromium).

### Prerequisites

- Backend running with `SPRING_PROFILES_ACTIVE=test` (enables auth bypass endpoint + test seed data)
- PostgreSQL running (Docker Compose or CI service)
- Frontend running (`npm run dev`)

### Running E2E Tests Locally

1.  **Start the database and seed test data:**
    ```bash
    docker compose up -d
    cd backend
    ./gradlew flywayMigrate \
      -Dflyway.locations=filesystem:src/main/resources/db/migration,filesystem:src/test/resources/db/test-seed
    ```

2.  **Start the backend with test profile:**
    ```bash
    cd backend
    SPRING_PROFILES_ACTIVE=test JWT_SECRET=local-dev-secret-32-chars-long-at-least-123456 \
      ./gradlew bootRun -Dspring.docker.compose.enabled=false
    ```

3.  **Start the frontend:**
    ```bash
    cd frontend
    npm run dev
    ```

4.  **Run the E2E tests:**
    ```bash
    cd frontend
    npx playwright test            # headless
    npx playwright test --ui       # interactive UI mode
    npx playwright show-report     # view HTML report
    ```

### Test Auth Bypass

E2E tests authenticate via `POST /api/test/auth/login` which issues an HttpOnly JWT cookie without requiring real OAuth2 SSO. This endpoint is only available when `SPRING_PROFILES_ACTIVE=test` and returns 404 in production.

### CI Integration

Playwright E2E tests run automatically in GitHub Actions CI after backend and frontend CI jobs pass. See `.github/workflows/ci.yml` → `e2e` job.

## 🏗️ Architecture Summary

- **Backend:** Java 25, Spring Boot 4.0.3, Spring Modulith, jOOQ.
- **Frontend:** Nuxt 4, PrimeVue 4, Tailwind CSS 4.
- **Rules:** See `_bmad-output/project-context.md` for AI agent guidance.
