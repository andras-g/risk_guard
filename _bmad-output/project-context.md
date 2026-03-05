---
project_name: 'risk_guard'
user_name: 'Andras'
date: '2026-03-05'
sections_completed: ['init', 'technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'code_quality_rules', 'workflow_rules', 'critical_rules']
existing_patterns_found: 14
status: 'complete'
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

### Backend (Java/Spring)
- **Language:** Java 25 (LTS) - Use Virtual Threads (`StructuredTaskScope`) for parallel tasks.
- **Framework:** Spring Boot 4.0.3 / Spring Modulith 2.0.3.
- **Data Access:** jOOQ (OSS Edition) - DB is truth. Codegen: snake_case DB -> camelCase Java.
- **Resilience:** Standalone `resilience4j-spring-boot3` (v2.x).
- **Security:** OAuth2 SSO (Google/Microsoft Entra) + Dual-Claim JWT.

### Frontend (Vue/Nuxt)
- **Framework:** Vue 3 (Composition API, <script setup>) + Nuxt 3.
- **UI:** PrimeVue 4 + Tailwind CSS 4.
- **State:** Pinia (feature-scoped stores).
- **Rendering:** Hybrid (`routeRules`) - SSR/ISR for SEO routes, SPA for dashboard.

### Shared / Infra
- **Database:** PostgreSQL 17 (Cloud SQL).
- **Communication:** Monorepo (backend/ + frontend/ + worker/).
- **CI Pipeline:** 12-step automated pipeline (OpenAPI -> TypeScript -> Zod).

## Critical Implementation Rules

### Language-Specific Rules

#### Java 25 (Spring Boot)
- **Virtual Threads:** Mandated for I/O bound tasks (Scraping). Use `StructuredTaskScope.ShutdownOnFailure`.
- **DTOs:** Use Java records in `api.dto`. Every Response record MUST have a `static from(Domain)` factory method.
- **Logging:** Pass only primitive/enum or `@LogSafe` types to logs. Redaction filter is active in production.
- **jOOQ:** Favor type-safe DSL over raw SQL. Scoped codegen prevents cross-module table access.

#### TypeScript (Nuxt 3)
- **Single Source of Truth:** API interfaces are auto-generated. Do not define `interface` for backend data manually.
- **Form Validation:** Use `openapi-zod-client` generated schemas. Frontend validation must mirror backend `@Valid` rules.
- **Composition API:** Use `<script setup lang="ts">`. Avoid Option API.
- **i18n:** Use `$t('key')`. All user-facing text resides in the frontend JSON namespace.

### Framework-Specific Rules

#### Spring Boot 4 / Modulith
- **Module Facade:** All cross-module calls MUST go through the `@Service` facade. Never import another module's repository or internal domain class.
- **Async Enrichment:** Use AI narrative generation as an async event listener to avoid blocking search latency.
- **Audit Logging:** Every Search endpoint MUST call `HashUtil` and write to `search_audit_log` within the same transaction.
- **Tenant Context:** Inject `SecurityContextHolder` to retrieve the `active_tenant_id`. No `tenant_id` query parameters in APIs.

#### Nuxt 3 / PrimeVue
- **SEO Stubs:** `/company/[taxNumber]` routes must be configured as `static` or `swr` in `routeRules`.
- **Composables:** Organize by concern: `formatting/`, `api/`, `auth/`.
- **Skeleton UX:** Use PrimeVue `Skeleton` for every async component while `pending` is true.
- **Error Handling:** Use `useApiError()` to map RFC 7807 codes to PrimeVue `Toast` messages.

### Testing Rules

- **Multi-Layer Canary:** Use `CanaryCompanyFixtures` (Backend) and `mockCompany.ts` (Frontend) as canonical test data.
- **Decoupled Golden Cases:** Store "Golden HTML Snapshots" separately from "Golden Parsed JSON" to allow independent scraper repair without logic regression.
- **Audit Hash Validation:** All `screening` tests MUST verify `sha256_hash` integrity (Snapshot + Verdict + Disclaimer).
- **Real-DB Mandate:** No H2. Repository tests MUST use Testcontainers with PostgreSQL 17.
- **Contract-First UI:** If `api.d.ts` changes, `tsc --noEmit` must pass across all specs before implementing new features.
- **Modulith Verify:** Run `ModulithVerificationTest` to ensure no illegal cross-module imports are introduced.
- **Frontend Spec Co-location:** `VerdictCard.vue` and `VerdictCard.spec.ts` must stay in the same directory.

### Code Quality & Style Rules

- **Reference Implementation:** Follow `hu.riskguard.screening` patterns for all modules.
- **Strict Naming:** 
  - Database: `lower_snake_case` (plural tables).
  - API/JSON/Java: `lowerCamelCase`.
  - Vue Files: `PascalCase`.
- **Tokenized Constants:** Import business thresholds from `risk-guard-tokens.json`. No hardcoded `Duration` or `Int` literals for logic.
- **Alphabetical i18n:** Keep `hu/*.json` and `en/*.json` sorted to minimize Git noise.
- **Public API Contract:** Only `@Service` facades and `api.dto` records should be accessible across modules.

### Development Workflow Rules

- **Conventional Commits:** Use `type: description`. Atomic commits only (one PR per feature).
- **CI-Managed Contracts:** CI regenerates types/Zod and fails if they differ from committed versions.
- **Path-Based CI Bypassing:** Use GitHub Actions `paths` filter to skip backend tests for frontend-only changes (and vice versa).
- **Health-Aware Integration:** CI integration tests check `scraper_health` and skip providers marked `DEGRADED`.
- **Fast-Gate Hook:** Pre-commit hooks MUST run ArchUnit naming rules and ESLint raw-text checks for immediate feedback.
- **Migration Naming:** Use `V{YYYYMMDD}_{NNN}__description.sql`.

### Critical Don't-Miss Rules

- **No Silent Data Gaps:** If NAV or e-Cégjegyzék is down, the verdict CANNOT be 'RELIABLE'. It must be 'INCOMPLETE' or 'UNAVAILABLE'.
- **Hungarian-Only Exports:** Use `@ExportLocale("hu")` for all government portal files. User interface language does not apply to exports.
- **Strict Module Isolation:** SQL queries are scoped to the module's own tables. Use facades for cross-module data requirements.
- **Legal Proof Integrity:** The SHA-256 hash in `search_audit_log` is the legal truth. It must include the exact disclaimer text shown to the user.
- **PII Zero-Tolerance:** Never log raw tax numbers, personal names, or emails. All log arguments must be `@LogSafe`.
