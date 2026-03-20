---
project_name: 'risk_guard'
user_name: 'Andras'
date: '2026-03-06'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'quality_rules', 'workflow_rules', 'anti_patterns']
status: 'complete'
rule_count: 36
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

- **Backend:** Java 25 (LTS), Spring Boot 4.0.3, Spring Modulith 2.0.3, jOOQ (OSS), PostgreSQL 17.
- **Frontend:** Nuxt 4.3.1, Vue 3 (Composition API), PrimeVue 4.5.4, Tailwind CSS 4.2.1, Pinia 3.0.4.
- **Infrastructure:** Monorepo, OpenAPI-driven contract pipeline (Zod/TypeScript generation), Resilience4j 2.x.

## Critical Implementation Rules

### Language-Specific Rules

- **Java 25 (Spring Boot):**
  - **Virtual Threads:** Mandated for I/O bound tasks (Scraping). Use `StructuredTaskScope.ShutdownOnFailure`.
  - **DTOs:** Use Java records in `api.dto`. Every Response record MUST have a `static from(Domain)` factory method.
  - **Logging:** Pass only primitive/enum or `@LogSafe` types to logs. Redaction filter is active in production.
  - **jOOQ:** Favor type-safe DSL over raw SQL. Scoped codegen prevents cross-module table access.

- **TypeScript (Nuxt 4):**
  - **Single Source of Truth:** API interfaces are auto-generated. NEVER define interfaces for backend data manually.
  - **Form Validation:** Use `openapi-zod-client` generated schemas mirroring backend `@Valid` rules.
  - **Composition API:** Use `<script setup lang="ts">`. Avoid Options API.
  - **i18n:** Use `$t('key')`. All user-facing text resides in the frontend JSON namespace.

### Framework-Specific Rules

- **Spring Boot 4 / Modulith:**
  - **Module Facade:** All cross-module calls MUST go through the `@Service` facade. No direct repository/domain imports.
  - **Async Enrichment:** Use AI narrative generation as an async event listener.
  - **Audit Logging:** Every Search endpoint MUST call `HashUtil` and write to `search_audit_log` within the same transaction.
  - **Tenant Context:** Inject `SecurityContextHolder` to retrieve `active_tenant_id`. No `tenant_id` query parameters.

- **Nuxt 4 / PrimeVue 4:**
  - **SEO Stubs:** `/company/[taxNumber]` routes must be configured as `static` or `swr` in `routeRules`.
  - **Composables:** Organize by concern: `formatting/`, `api/`, `auth/`.
  - **Skeleton UX:** Use PrimeVue `Skeleton` for every async component while `pending` is true.
  - **Error Handling:** Use `useApiError()` to map RFC 7807 codes to PrimeVue `Toast` messages.

### Testing Rules

- **Multi-Layer Canary:** Use `CanaryCompanyFixtures` (Backend) and `mockCompany.ts` (Frontend) as canonical test data.
- **Decoupled Golden Cases:** Store "Golden HTML Snapshots" separately from "Golden Parsed JSON" for scraper repair.
- **Audit Hash Validation:** All `screening` tests MUST verify `sha256_hash` integrity (Snapshot + Verdict + Disclaimer).
- **Real-DB Mandate:** Repository tests MUST use Testcontainers with PostgreSQL 17. No H2.
- **Contract-First UI:** `tsc --noEmit` must pass across all specs before implementing new features if `api.d.ts` changes.
- **Modulith Verify:** Run `ModulithVerificationTest` to ensure no illegal cross-module imports.
- **Frontend Spec Co-location:** `*.vue` and `*.spec.ts` must stay in the same directory.

### Code Quality & Style Rules

- **Reference Implementation:** Follow `hu.riskguard.screening` patterns for all modules.
- **Strict Naming:** 
  - Database: `lower_snake_case` (plural tables).
  - API/JSON/Java: `lowerCamelCase`.
  - Vue Files: `PascalCase`.
- **Tokenized Constants:** Import business thresholds from `risk-guard-tokens.json`. No hardcoded logic literals.
- **Alphabetical i18n:** Keep `hu/*.json` and `en/*.json` sorted to minimize Git noise.
- **Public API Contract:** Only `@Service` facades and `api.dto` records are accessible across modules.

### AI Agent Tool Usage Rules

- **NEVER attempt to Write large files (100+ lines) in a single Write tool call.** The Write tool's JSON serialization breaks on large content containing backticks, code fences, pipe tables, curly braces, and mixed quoting — which is every story file, architecture doc, and markdown artifact. This causes repeated `JSON Parse error: Expected '}'` failures that waste time.
- **Mandatory pattern for creating large markdown files (stories, docs, specs):**
  1. `Write` a minimal skeleton file with section headers and short placeholder text (e.g., "(placeholder)")
  2. `Read` the skeleton file back
  3. `Edit` each section one at a time — replace each placeholder with real content
  4. Never attempt to write more than ~80 lines of markdown content in a single tool call
- **This applies to ALL workflows that produce documents** — create-story, create-architecture, create-prd, etc. The Edit tool handles special characters reliably because each replacement chunk is smaller.

### Development Workflow Rules

- **Conventional Commits:** Use `type: description`. Atomic commits only (one PR per feature).
- **CI-Managed Contracts:** CI regenerates types/Zod and fails on drift.
- **Path-Based CI Bypassing:** Skip backend tests for frontend-only changes (and vice-versa) via GitHub Actions `paths`.
- **Health-Aware Integration:** CI tests check `scraper_health` and skip `DEGRADED` providers.
- **Fast-Gate Hook:** Pre-commit hooks MUST run ArchUnit and ESLint raw-text checks.
- **Migration Naming:** Use `V{YYYYMMDD}_{NNN}__description.sql`.
- **Severity-Gated Reviews:** Only CRITICAL/HIGH findings trigger a mandatory re-review round. MEDIUM/LOW are advisory.
- **ALWAYS reproduce CI failures locally before attempting fixes.** Never iterate blind on CI — each push-and-wait cycle takes 15+ minutes. Run the exact same commands locally (same profile, same env vars, same seed data) to get instant feedback. For E2E: start backend with `SPRING_PROFILES_ACTIVE=test`, seed via `./gradlew flywayMigrate -PflywayExtraLocations=...`, start frontend, then `npx playwright test`.

### Critical Don't-Miss Rules

- **No Silent Data Gaps:** If scrapers are down, verdict CANNOT be 'RELIABLE'. Must be 'INCOMPLETE' or 'UNAVAILABLE'.
- **Hungarian-Only Exports:** Use `@ExportLocale("hu")` for government files. UI language does not apply.
- **Strict Module Isolation:** SQL queries are scoped to the module's own tables. Use facades for cross-module data.
- **Legal Proof Integrity:** SHA-256 in `search_audit_log` is the legal truth. Includes exact disclaimer text.
- **PII Zero-Tolerance:** Never log raw tax numbers, names, or emails. All log arguments must be `@LogSafe`.
- **JWT Algorithm Consistency:** `JwtDecoder` MUST derive its `MacAlgorithm` from the signing key length (matching jjwt's `Keys.hmacShaKeyFor()` logic: ≥64B→HS512, ≥48B→HS384, ≥32B→HS256). Hardcoding the algorithm causes silent 401s when the JWT secret length changes between environments.

---

## Usage Guidelines

**For AI Agents:**

- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Update this file if new patterns emerge

**For Humans:**

- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

Last Updated: 2026-03-20
