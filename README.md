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

## 🏗️ Architecture Summary

- **Backend:** Java 25, Spring Boot 4.0.3, Spring Modulith, jOOQ.
- **Frontend:** Nuxt 3, PrimeVue 4, Tailwind CSS 4.
- **Rules:** See `_bmad-output/project-context.md` for AI agent guidance.
