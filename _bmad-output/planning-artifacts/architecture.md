---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7]
inputDocuments:
  - "_bmad-output/planning-artifacts/prd.md"
  - "_bmad-output/planning-artifacts/product-brief-risk_guard-2026-03-04.md"
  - "_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md"
  - "_bmad-output/planning-artifacts/research/market-Hungarian-SME-behavior-and-red-flag-response-research-2026-03-04.md"
  - "partnerRadar.md"
workflowType: 'architecture'
project_name: 'risk_guard'
user_name: 'Andras'
date: '2026-03-04'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
12 FRs spanning 4 domains: Partner Screening (FR1-FR4: tax number search, NAV/e-Cégjegyzék retrieval, state-machine verdicts, suspended tax detection), Monitoring & Alerts (FR5-FR7: watchlist CRUD, 24h status checks, Resend email triggers), EPR Compliance (FR8-FR10: multi-step questionnaire, JSON DAG validation, schema-perfect exports), and Administration (FR11-FR12: scraper health dashboard, hot-swappable EPR JSON config).

**Non-Functional Requirements:**
7 NFRs driving architecture: < 30s verdict latency (NFR1), GraalVM native images < 200ms startup (NFR2), min-instances during business hours (NFR3), SHA-256 cryptographic hashing for due diligence (NFR4), AES-256/TLS 1.3 encryption (NFR5), SEO gateway stubs with JSON-LD (NFR6), and scale-to-zero off-peak (NFR7).

**Scale & Complexity:**

- Primary domain: Full-stack B2B SaaS (Java backend-heavy)
- Complexity level: High
- Estimated architectural components: 5 Spring Modulith modules (`screening`, `epr`, `scraping`, `notification`, `identity`)

### Technical Constraints & Dependencies

- **Solo developer maintenance** (< 10 hrs/week) — drives modular monolith over microservices
- **JVM for MVP, native-image-ready design** — avoid reflection/dynamic proxies; GraalVM native compilation deferred to post-MVP cost optimization
- **Government portal fragility** — requires adapter pattern for each source with independent health tracking
- **EU data residency** — GCP Frankfurt or Warsaw regions only
- **MOHU API unavailability** — EPR fee tables must be manually maintained from published legislation until API access is granted
- **Dual deployment model** — API (Cloud Run) + Scraper Worker (Cloud Run Jobs with sidecar Chrome for Playwright)

### Cross-Cutting Concerns Identified

- **Tenant Isolation:** `tenant_id` enforced at the Spring Data repository layer via `TenantFilter` reading from `SecurityContextHolder`. Database-level `NOT NULL` constraint on `tenant_id`. Guests receive synthetic tenant IDs (`guest-{session_uuid}`). No null tenant_id anywhere in the system.
- **Audit Trail:** Every search produces a SHA-256 hashed, timestamped record with source URLs. Disclaimer text included in hash.
- **Data Freshness (Tiered Model):** < 6h: serve cached ("Verified today"); 6-24h: auto-trigger background re-scrape, serve cached with "Refreshing..." indicator; 24-48h: "Stale" warning badge, block "Reliable" verdict → "Review Recommended"; > 48h: hard shift to "Unavailable."
- **Graceful Degradation:** Portal outages surface as global health banner + per-verdict timestamps. "Alert me when portal returns" queues a retry. Degraded mode serves partial verdicts with explicit "Unavailable" fields.
- **Feature Flags (Simplified):** Tier-based enum (`ALAP`/`PRO`/`PRO_EPR`) with `@TierRequired` annotation on controller methods. Tier embedded in JWT claims and verified server-side.
- **Observability:** Scraper success rates, search latency, error budgets, and per-adapter MTBF exposed via admin dashboard.
- **Per-Tenant Cache with Response Jitter:** Cache is per-tenant (Tenant A's cache does not warm Tenant B's). 50-200ms random jitter on all search responses to prevent timing oracle attacks.

### Architecture Decision Records

#### ADR-1: Application Architecture — Spring Modulith (Modular Monolith)

**Decision:** Spring Modulith with 5 modules (`screening`, `epr`, `scraping`, `notification`, `identity`) as a single deployable. Playwright scraper worker is a separate Cloud Run Job due to Chrome sidecar requirements.

**Rationale:** Solo developer maintainability. Enforced module boundaries with the operational simplicity of a single deployable. One CI/CD pipeline, one database connection pool, shared transaction context. Module boundaries make future extraction clean if scale demands it. Notification stays in-process — 100 users × 10 watchlist entries = trivial load for `@Scheduled`.

**Rejected:** Microservices (operational complexity), selective decomposition with Pub/Sub (premature optimization).

#### ADR-2: Persistence — PostgreSQL 17 Only

**Decision:** Single PostgreSQL 17 database (Cloud SQL) for all data patterns.

**Rationale:** JSONB for snapshots, relational for domain entities, BRIN indexes for time-series queries, dedicated `guest_sessions` table with scheduled purge for transient data. One backup strategy, one connection pool, one migration tool (Flyway). Adding Redis/Elasticsearch doesn't pay for itself below 1000+ concurrent users.

**Rejected:** PostgreSQL + Redis (operational overhead for 10 guest rows), polyglot persistence (massive burden for solo dev).

#### ADR-3: GraalVM Strategy — JVM for MVP, Native-Image-Ready Design

**Decision:** Ship MVP on JVM with Min-Instances: 1. Design all code to be native-image-compatible. Add GraalVM native compilation as post-MVP cost optimization.

**Rationale:** Cold start is irrelevant with Min-Instances: 1 during business hours. GraalVM native images are a cost optimization (scale-to-zero off-peak), not a functional requirement. Avoiding AOT compilation pain during 4-week sprint maximizes development velocity. Architectural discipline (no runtime reflection, no dynamic proxies) ensures easy switch later.

**Rejected:** GraalVM native from day 1 (build time overhead, library risk), JVM-only with no native plan (locked out of future cost savings).

#### ADR-4: Scraper Architecture — Hexagonal Ports & Adapters with Parallel Virtual Threads

**Decision:** Each government source implements a `CompanyDataPort` interface with `fetch()`, `requiredFields()`, `health()`, and `validateCanary()`. `CompanyDataAggregator` orchestrates all adapters in parallel using Java 25 Virtual Threads (`StructuredTaskScope`). Each adapter has its own Resilience4j circuit breaker.

**Rationale:** 30s latency budget requires parallel execution (latency = slowest adapter, not sum). Canary validation with known-good companies detects semantic breakage proactively. Independent circuit breakers prevent NAV failure from taking down e-Cégjegyzék. Failed adapters return explicit `SourceUnavailable` results for deterministic verdict handling.

**Rejected:** Sequential adapters (breaks latency budget), async with callbacks (harder to reason about).

#### ADR-5: Authentication — OAuth2 SSO + Dual-Claim JWT

**Decision:** Spring Security 6 OAuth2 Login (Google + Microsoft Entra ID) with email/password fallback. Stateless JWTs with `home_tenant_id` and `active_tenant_id` claims. Accountant context-switch issues a fresh short-lived JWT validated against `tenant_mandates` table.

**Rationale:** Stateless sessions are Cloud Run friendly. Dual-claim JWT enables fast Accountant context-switching without server-side sessions. `TenantFilter` at repository layer always reads `active_tenant_id` from security context. Every context switch is audit-logged.

**Rejected:** Single tenant claim JWT (context switch requires full re-auth), server-side sessions (breaks Cloud Run statelessness).

### Module-Level Failure Mode Analysis

#### `scraping` Module
- **DOM layout changes:** Mandatory field contracts per adapter + DOM fingerprint hashing (>30% divergence = circuit break) + canary company validation on every monitoring cycle.
- **Anti-bot blocking:** Rotating proxy support in Playwright sidecar, exponential backoff, fallback to cached last-known-good snapshot with staleness badge.
- **Portal downtime:** Per-adapter circuit breaker (Resilience4j), global health banner, "Alert me when portal returns" queues retry.
- **Playwright OOM/crash:** Cloud Run Jobs auto-restart, isolated jobs per adapter, dead-letter alerting.
- **Parse exceptions:** Defensive parsing with fallback to raw JSONB storage. Flag record as "Parse Error" for manual review.

#### `screening` Module
- **Logic bug (wrong verdict):** Immutable regression suite of 50+ golden-case snapshots. CI blocks deployment on failure. State machine is a pure function.
- **Partial data verdict:** Mandatory source checklist. Missing NAV data = verdict CANNOT be "Reliable" → must be "Incomplete" or "Manual Review Required." No silent gaps.
- **Suspended tax number:** `TAX_SUSPENDED` is a first-class state-machine state, equivalent to "At-Risk" with distinct "Manual Review Required" badge.
- **Race conditions:** Idempotency guard — check for fresh snapshot (< 15 min) before scraping. Database advisory lock on tax_number during active scrape.

#### `epr` Module
- **Legislation changes:** JSON-driven versioned configuration. Admin updates without code redeploy. Each export stamps config version used.
- **MOHU schema drift:** Schema version gate — if schema is unverified, export button is disabled. MOHU schema stored as JSON schema in database.
- **DAG logic errors:** Each decision tree path is a test case. User traversal path stored alongside result for auditability.
- **Encoding issues:** UTF-8 BOM for Excel compatibility, semicolon delimiters (Hungarian locale), automated round-trip test.
- **EPR golden test cases:** 5-10 known-correct calculations validated nightly against current config. Unexpected output changes block the wizard.

#### `notification` Module
- **Resend API downtime:** Outbox pattern — notifications persisted to `notification_outbox` table first. Retry scheduler with exponential backoff. Pending/failed notifications visible in UI.
- **Missed change detection:** Snapshot diff is structural (normalized JSONB), not textual. Every comparison logged (even "no change"). Admin dashboard shows last-check timestamp.
- **Alert storms:** Digest mode if > 5 alerts in a single cycle. Rate limit per-tenant: max 10 individual alerts/day.
- **Outbox backpressure:** Max queue depth per tenant. Exceeding threshold auto-switches to digest mode.

#### `identity` Module
- **Tenant data leakage:** Dual-layer enforcement — repository-layer `TenantFilter` + JWT claims. `tenant_id NOT NULL` constraint at database level. Integration tests with multi-tenant fixtures asserting isolation.
- **SSO provider outage:** Email/password fallback. Self-contained JWT sessions — SSO outage doesn't affect active sessions.
- **Tier bypass:** `@TierRequired` on controller methods. Tier in JWT claims, verified server-side. VerdictProjection pattern: same verdict, different views per tier.
- **Guest abuse:** Multi-signal rate limiting (IP + browser fingerprint + tax number query history). CAPTCHA on 3rd+ search. Synthetic guest tenant IDs.
- **Accountant context-switch leak:** Explicit `tenant_id` override validated against `tenant_mandates`. Audit log every switch.

### Cross-Module Cascade Safeguards

| Cascade Scenario | Safeguard |
|---|---|
| Scraper fails → Verdict serves stale data | Tiered freshness model. Stale data = "Incomplete" verdict, never "Reliable." |
| Scraper fails → Watchlist diff misses change | Watchlist monitor logs "scraper unavailable" as explicit event. Triggers "monitoring interrupted" notification. |
| Identity breach → Tenant leak | Defense in depth: repository-layer filtering is the last line even if session is compromised. |
| EPR config update → Old exports invalid | Versioned configs. Old exports retain config version reference. Admin alert on new version. |

### Pre-mortem Risk Mitigations

#### Risk 1: Scraper Treadmill (Priority: 🔴 Critical)
- **Scraper Repair Playbooks:** Each adapter ships with `repair-guide.md` documenting selector strategy and common government change patterns.
- **LLM-Assisted Repair Triage (v1.1):** Auto-generate diff report showing old vs. new selectors on DOM fingerprint divergence.
- **Adapter Independence Scoring:** Track MTBF per adapter. Prioritize API migration for most fragile sources.
- **Degraded Mode Architecture:** Define "minimum viable verdict" — partial data renders partial verdict with explicit gaps.

#### Risk 2: Accountant Persona Mismatch (Priority: 🟡 Medium)
- **Validate workflows before building:** Paper prototype with 2-3 real bookkeepers before full implementation.
- **Batch-First Design:** CSV upload → batch screening → aggregate report as PRIMARY flow. Context-switching is secondary drill-down.
- **Feature Spike Protocol:** Thin vertical spike (API + minimal UI) validated with real user before full investment.

#### Risk 3: Conversion Failure (Priority: 🟡 Medium)
- **Multi-signal rate limiting:** IP + browser fingerprint + tax number query history. Same tax_number queried 3× in 24h across ANY session = require auth.
- **Teaser Verdict Architecture:** Guest searches return partial verdict ("Risk signals detected: 2. Sign up to see full report."). `verdict_visibility` field in API response controlled by tier.
- **VerdictProjection Pattern:** One state machine, multiple views. Full details for PRO, summary for ALAP, teaser for GUEST.

#### Risk 4: EPR Staleness (Priority: 🔴 Critical)
- **Regulatory Monitor:** Scrape MOHU announcements + Magyar Közlöny for EPR-related keywords. Alert admin on any content change.
- **EPR Golden Test Cases:** 5-10 known-correct calculations validated nightly. Unexpected changes block EPR wizard.
- **User Upload Feedback Loop:** Post-upload prompt: "Did your MOHU upload succeed?" Track success rates. Spike in "No" triggers config review.

#### Risk 5: Solo Developer Bus Factor (Priority: 🔴 Critical)
- **Auto-Degradation Protocol:** Canary fail + no admin response in 4h → auto circuit-break adapter, update health banner, send bulk notification to affected watchlist users.
- **Outbox Backpressure:** Max queue depth per tenant with auto-digest fallback.
- **Automated Status Page:** Static page (GitHub Pages/Cloudflare Pages) updated by health check job. Users see status even if main API is down.
- **Operational Runbooks:** Every procedure (scraper repair, EPR config update, deployment, incident response) documented as a runbook in the repo.

## Starter Template Evaluation

### Primary Technology Domain

Java/Spring Boot backend-heavy B2B SaaS, based on project requirements analysis and established tech stack decisions.

### Starter Options Considered

| Option | Verdict | Rationale |
|---|---|---|
| **Spring Initializr** | ✅ Selected | Official, maintained, flexible. Establishes build tooling and auto-configuration without imposing opinions beyond Spring Boot conventions. |
| JHipster | ❌ Rejected | Too opinionated, includes unwanted frontend framework, overwhelming for solo dev with specific architectural decisions. |
| Custom Skeleton | ❌ Rejected | Spring Initializr provides the same flexibility with proper auto-configuration wiring and less boilerplate. |

### Selected Starter: Spring Initializr (start.spring.io)

**Current Versions Verified (March 2026):**
- Spring Boot: 4.0.3 (latest stable)
- Spring Modulith: 2.0.3 (compatible with Spring Boot 3.5-4.x)

**Initialization Command:**

```bash
curl https://start.spring.io/starter.tgz \
  -d type=gradle-project \
  -d language=java \
  -d bootVersion=4.0.3 \
  -d groupId=hu.riskguard \
  -d artifactId=risk-guard \
  -d name=risk-guard \
  -d description="Partner risk screening and EPR compliance platform for Hungarian SMEs" \
  -d packageName=hu.riskguard \
  -d javaVersion=25 \
  -d dependencies=modulith,data-jpa,flyway,oauth2-client,oauth2-resource-server,actuator,web,validation,mail,docker-compose,devtools,configuration-processor,native,cloud-resilience4j \
  | tar -xzf -
```

**Architectural Decisions Provided by Starter:**

**Language & Runtime:**
- Java 25 with Virtual Threads, Gradle build with Kotlin DSL
- Spring Boot 4.0.3 auto-configuration

**Build Tooling:**
- Gradle wrapper (reproducible builds)
- GraalVM native support via `native` dependency (ready for post-MVP activation)
- Spring Boot Gradle plugin (fat JAR, bootRun, native compile tasks)

**Testing Framework:**
- JUnit 5 + Spring Boot Test (auto-configured)
- Spring Modulith test support for module boundary verification

**Code Organization:**
- `hu.riskguard` root package
- Spring Modulith module discovery via top-level packages:
  - `hu.riskguard.screening` — Partner screening and state-machine verdicts
  - `hu.riskguard.epr` — EPR compliance wizard and MOHU exports
  - `hu.riskguard.scraping` — Scraper adapters (JSoup/Playwright), health tracking
  - `hu.riskguard.notification` — Watchlist monitoring, Resend email alerts
  - `hu.riskguard.identity` — Auth, RBAC, tenant isolation, tier management

**Development Experience:**
- DevTools hot reload
- Docker Compose support for local PostgreSQL
- Configuration processor for IDE autocompletion on custom properties

**Dependencies Added Manually (Not in Initializr):**
- JSoup (HTML parsing for static government pages)
- Playwright Java (browser-based scraping with sidecar Chrome)
- Tabula-java / Apache POI (PDF/Excel parsing for EPR fee tables)
- Spring AI BOM (OpenAI o3/GPT-5 integration)
- Resend Java SDK (email delivery)

**Note:** Project initialization using this command should be the first implementation story.

## Core Architectural Decisions (Step 4)

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**

| Decision | Choice | Rationale |
|---|---|---|
| Data Access Layer | jOOQ (open-source edition) | Type-safe SQL, first-class JSONB/PostgreSQL support, build-time code generation (GraalVM ready) |
| Caching | Spring Cache + Caffeine | In-process, TTL-based, per-tenant cache keys. Fits single-instance Cloud Run. |
| Frontend Framework | Vue 3 + Nuxt 3 | AI-agent consistency, hybrid SEO, cohesive ecosystem (Pinia, VueRouter, PrimeVue) |
| Frontend Meta-framework | Nuxt 3 | File-based routing, hybrid rendering (SSR for SEO stubs, SPA for app), auto-imports |
| UI Components | PrimeVue 4 | Production-ready DataTable, Stepper, Charts. Less custom code = less AI-generated bugs. |
| CSS Framework | Tailwind CSS 4 | Utility-first, mobile-responsive, zero custom CSS maintenance |
| State Management | Pinia | Official Vue state library. Feature-scoped stores. |
| Form Validation | VeeValidate + Zod (auto-generated from OpenAPI) | Single source of truth: Java @Valid → OpenAPI → Zod schemas |
| Repository Structure | Monorepo (`/backend` + `/frontend`) | Atomic feature PRs, API contract consistency, single context for AI agents |
| Rate Limiting | Bucket4j | In-process token bucket. Per-tenant and per-IP rate limits. No gateway needed. |
| API Documentation | springdoc-openapi (auto-generated) | OpenAPI spec drives TypeScript types and Zod validators |

**Important Decisions (Shape Architecture):**

| Decision | Choice | Rationale |
|---|---|---|
| API Protocol | REST (JSON) | Standard, well-supported by Spring Boot 4 and Nuxt `useFetch` |
| API Versioning | URL-based (`/api/v1/`) | Simple, unambiguous. When v2 needed, same pattern. |
| Input Validation | Bean Validation (JSR-380) + custom `@HungarianTaxNumber` | Server-side authority; frontend mirrors via Zod |
| SEO Gateway Stubs | Nuxt hybrid rendering (`routeRules`) | Public company pages SSR/ISR for SEO; authenticated routes SPA. No separate SSR service. |
| Frontend Hosting | Cloud Storage + Cloud CDN (or Cloudflare Pages) | Static/ISR output, independent from backend scaling |

**Deferred Decisions (Post-MVP):**

| Decision | Rationale for Deferral |
|---|---|
| GraphQL API | REST sufficient for MVP. GraphQL adds complexity without clear benefit at 100 users. |
| WebSocket real-time updates | Polling via htmx-style `useFetch` with interval is sufficient for MVP. |
| PWA / Offline capability | Not required per PRD. Can add service worker later. |

### Cross-Component Dependencies

- **OpenAPI Pipeline:** Backend DTO changes cascade to TypeScript types → Zod validators → Frontend components. CI verifies the full chain.
- **Business Constants:** `risk-guard-tokens.json` feeds both `@ConfigurationProperties` (backend) and direct import (frontend). A threshold change propagates to both stacks.
- **i18n:** Frontend i18n JSON files are the single source of truth for all user-facing text. Backend only handles export locale and email templates.

## Implementation Patterns & Consistency Rules (Step 5)

### Pattern Categories Defined

**Critical Conflict Points Identified:** 14+ areas where AI agents could make different choices, all locked down with automated enforcement.

### Naming Patterns

**Database Naming Conventions:**
- Tables: `lower_snake_case`, plural (e.g., `users`, `company_snapshots`).
- Columns: `lower_snake_case` (e.g., `tax_number`, `tenant_id`).
- Primary Keys: Always `id` (UUID or Long as per entity).
- Foreign Keys: `{target_table_singular}_id` (e.g., `tenant_id`, `user_id`).
- Indexes: `idx_{table}_{column}` (e.g., `idx_users_email`).
- Enforcement: Flyway callback rejects `CREATE TABLE` with uppercase characters.

**API Naming Conventions:**
- Endpoints: Plural nouns, `kebab-case` — `/api/v1/partner-reports/{id}`.
- Query Params: `camelCase` (e.g., `?pageNumber=1&taxNumber=12345678`).
- JSON Fields: `camelCase` (e.g., `taxNumber`, `verdictStatus`).
- Pagination: Cursor-based in response body: `{ "items": [...], "cursor": "abc123", "hasMore": true }`.
- Responses: Flat JSON (no `{ data: ... }` wrapper). Errors via RFC 7807.
- Enforcement: ArchUnit verifies all `@RequestMapping` paths match `/api/v[0-9]+/[a-z-]+`. Jackson global config enforces `LOWER_CAMEL_CASE`.

**Code Naming Conventions:**
- Java: PascalCase for classes, camelCase for methods/variables. Standard Java conventions.
- Vue/Nuxt: PascalCase for component files (`VerdictCard.vue`), camelCase for composables and variables.
- i18n Keys: `{module}.{page}.{component}.{element}` (e.g., `screening.dashboard.verdictCard.statusReliable`). Common keys under `common.*`.
- Enforcement: ESLint `vue/component-name-in-template-casing` for Vue; standard Java conventions enforced by Checkstyle.

### Structure Patterns

**Backend Module Structure (Reference: `screening` module):**
```
hu.riskguard.{module}/
├── api/
│   ├── {Module}Controller.java           // @RestController — API endpoints
│   └── dto/
│       ├── {Feature}Request.java          // Request DTO (Java record, @Valid)
│       └── {Feature}Response.java         // Response DTO (Java record, from() factory)
├── domain/
│   ├── {Module}Service.java              // Module facade @Service — the ONLY public entry point
│   ├── {DomainLogic}.java                // Pure domain functions
│   └── events/
│       └── {EventName}.java              // Application Events published by this module
└── internal/
    └── {Module}Repository.java           // jOOQ queries scoped to owned tables
```

**Frontend Structure:**
```
frontend/
├── components/{Module}/
│   ├── ComponentName.vue                  // PascalCase, co-located spec
│   └── ComponentName.spec.ts
├── composables/
│   └── use{Feature}.ts                    // Shared composables
├── i18n/
│   ├── hu/{module}.json                   // Hungarian translations (namespace-per-file)
│   └── en/{module}.json                   // English translations
├── pages/{module}/
│   └── index.vue                          // Nuxt file-based routing
├── stores/
│   └── {module}.ts                        // Pinia store (feature-scoped)
└── types/
    └── api.d.ts                           // AUTO-GENERATED from OpenAPI — never edit manually
```

**Table Ownership Per Module:**
- `screening`: `company_snapshots`, `verdicts`, `search_audit_log`
- `epr`: `epr_configs`, `epr_calculations`, `epr_exports`
- `scraping`: `scraper_health`, `canary_companies`, `dom_fingerprints`
- `notification`: `watchlist_entries`, `notification_outbox`
- `identity`: `users`, `tenants`, `tenant_mandates`, `guest_sessions`

Enforcement: jOOQ codegen per module with explicit `includeTables` whitelist.

### Data Flow Patterns

**DTO Mapping Strategy:**
- All DTOs are Java records in the `api.dto` sub-package.
- Every response DTO has a `static from(DomainType)` factory method. No MapStruct.
- Controllers MUST use `DtoClass.from()`, never direct DTO construction.
- `api.dto` packages may ONLY contain records (ArchUnit enforced).
- Masked `toString()` on all domain records — only last 3 digits of sensitive fields visible in logs.

**OpenAPI → TypeScript Contract Pipeline:**
```
Java DTO (@Valid annotations) → springdoc-openapi → OpenAPI spec
    → openapi-typescript → TypeScript interfaces (api.d.ts)
    → openapi-zod-client → Zod validators
```
CI verifies: no `type: object` without properties, no `Record<string, unknown>`, no `any` in generated types.

**jOOQ Configuration:**
- Code generator produces `camelCase` Java field names from `snake_case` columns.
- Per-module codegen scoping: each module only generates classes for its owned tables.
- Enforcement: CI task verifies no module's codegen includes another module's tables.

### Communication Patterns

**Cross-Module Communication Matrix:**

| Scenario | Pattern | Example |
|---|---|---|
| Need data from another module (request/response) | **Facade call** via `@Service` | `scrapingService.fetchCompanyData(taxNumber)` |
| Broadcast that something happened (fire-and-forget) | **Application Event** | `PartnerStatusChanged` event |
| Need another module to act, don't need to wait | **Application Event** | `EprExportGenerated` event |

**Rule: Need return value → facade call. Broadcasting → event.**

**Event Catalog:** Maintained in `core/events/package-info.java` with Javadoc listing ALL application events by publishing module. CI verifies every `ApplicationEvent` subclass is documented in the catalog.

**Spring Modulith Event Configuration:**
- `@ApplicationModuleListener` for event consumers.
- Event Publication Registry for guaranteed delivery (after transaction commit).
- Module tests verify events are published without needing the listener.

### i18n & l10n Patterns

**Language Configuration:**
- Default: Hungarian (`hu`). Fallback: English (`en`).
- Frontend i18n JSON files are the SINGLE source of truth for all user-facing text.
- Backend `messages_*.properties` reserved for: email templates (Resend), government export content (`@ExportLocale`), audit log descriptions.
- Backend API returns error codes only (RFC 7807 `type` field: `urn:riskguard:error:{error-key}`). Frontend maps codes to localized messages.

**Display Locale vs. Export Locale:**
- User-facing content follows `user.preferred_language`.
- Government-bound exports (MOHU, NAV) are ALWAYS Hungarian via `@ExportLocale("hu")` annotation.
- When export is generated, UI shows toast in user's language: 'Export generated in Hungarian (required by MOHU).'

**i18n File Organization:**
- Namespace-per-file: `i18n/hu/common.json`, `i18n/hu/screening.json`, `i18n/hu/epr.json`, etc.
- Files MUST be sorted alphabetically by key.
- `common.*` namespace for reusable text (actions, states, errors). Module keys may NOT duplicate `common.*` values.

**Localization Formatters (Frontend — 4 composables, no direct `Intl` calls):**
- `useDateShort()` — HU: `2026. 03. 04.` | EN: `Mar 4, 2026`
- `useDateFull()` — HU: `2026. március 4., csütörtök` | EN: `Thursday, March 4, 2026`
- `useDateRelative()` — HU: `2 órája` | EN: `2 hours ago`
- `useDateApi()` — Always: `2026-03-04T12:00:00Z` (ISO 8601 UTC, non-localized)

**Enforcement:**
- ESLint `@intlify/vue-i18n/no-raw-text` — zero tolerance for hardcoded strings in templates.
- CI script: parse all `.vue` files for `t('...')` calls, compare keys against `hu/*.json` and `en/*.json`. Missing keys = CI failure.
- CI key-parity check: every key in `hu/*.json` must exist in `en/*.json` and vice versa.

### Business Constants Token Pattern

**Single source of truth:** `risk-guard-tokens.json` in monorepo root.
```json
{
  "freshness": { "freshThresholdHours": 6, "staleThresholdHours": 24, "unavailableThresholdHours": 48 },
  "guest": { "maxCompanies": 10, "maxDailyChecks": 3, "captchaAfterChecks": 3 },
  "tiers": ["ALAP", "PRO", "PRO_EPR"],
  "rateLimits": { "searchesPerMinutePerTenant": 30, "maxAlertsPerDayPerTenant": 10 }
}
```
- Backend: bound to `RiskGuardProperties` via `@ConfigurationProperties`. Agents inject the bean, never use literals.
- Frontend: direct import. ESLint flags numeric literals in business logic files.
- CI: verifies every token key is referenced in source code. Orphaned keys = warning. Used literals not in tokens = error.

### Logging Patterns

**Backend Structured Logging:**
- SLF4J + Logback with JSON encoder for production.
- MDC context: `tenantId`, `traceId`, `module`. Set by a servlet filter.
- Log levels: `INFO` for business events, `WARN` for recoverable issues, `ERROR` for failures requiring attention, `DEBUG` for development.
- NEVER log PII at any level. Use `@LogSafe` annotation on types safe for logging.
- Logback redaction filter: regex-matches and redacts 8-digit patterns (tax numbers), email patterns, UUID patterns (tenant IDs).
- ArchUnit enforces: only `@LogSafe` types may appear as log arguments.

**Frontend Logging:**
- `console.warn()` and `console.error()` in development.
- Vite build strips all `console.*` in production via `drop: ['console']`.
- No custom logger composable needed.

### Error Handling Patterns

**Backend:**
- Global `@ControllerAdvice` maps exceptions to RFC 7807 responses.
- Error type format: `urn:riskguard:error:{error-key}`.
- Exception hierarchy: `RiskGuardException` (base) → `NotFoundException`, `ValidationException`, `ScrapingException`, `TenantAccessDeniedException`.

**Frontend:**
- Unified `useApiError` composable handles error responses.
- Maps RFC 7807 `type` field to i18n key: `urn:riskguard:error:tax-number-invalid` → `common.errors.taxNumberInvalid`.
- Displays PrimeVue Toast notification in user's locale.

### Process Patterns

**Loading States:**
- Use Nuxt `useFetch` `pending` ref with PrimeVue `Skeleton` components.
- No global loading state — each data fetch has its own `pending`.

**Retry & Resilience:**
- Backend: Resilience4j circuit breakers and retries (configured per scraper adapter).
- Frontend: `ofetch` (via Nuxt `$fetch`) retry configuration for network errors.
- Scraper health: Resilience4j Actuator endpoints (`/actuator/circuitbreakers`) polled by admin dashboard.

### Automated Fail-Safes & CI Pipeline

**ArchUnit Test Organization:**
```
backend/src/test/java/hu/riskguard/architecture/
├── NamingConventionTest.java      // DB table names, API paths, JSON fields
├── ModuleBoundaryTest.java        // Cross-module import bans, facade enforcement
├── DtoConventionTest.java         // from() factory, no untyped endpoints, records-only in api.dto
├── LoggingConventionTest.java     // @LogSafe enforcement, no PII in logs
├── BusinessConstantTest.java      // No magic numbers, token file references
└── ModulithVerificationTest.java  // Spring Modulith verify()
```

**CI Pipeline Order:**
```
1.  Compile Java (catches jOOQ/type errors)
2.  Run ArchUnit tests (naming, module, DTO, logging, magic number violations)
3.  Run Spring Modulith verify (module boundary violations)
4.  Run Flyway migrations against test DB (DB naming violations)
5.  Generate OpenAPI spec (untyped endpoint detection)
6.  Generate TypeScript types from OpenAPI (contract drift)
7.  Generate Zod validators from OpenAPI (validation drift)
8.  Run ESLint on frontend (hardcoded strings, naming, magic numbers)
9.  Run i18n key parity check (missing translations)
10. Run tsc --noEmit (TypeScript errors from generated types)
11. Run unit tests (backend + frontend)
12. Run integration tests (full pipeline with canary data)
```

**Local vs. CI Split:**

| When | What Runs | Time |
|---|---|---|
| On save (IDE) | ESLint auto-fix + Checkstyle | < 1s |
| Pre-commit hook | ESLint + ArchUnit naming rules + i18n key parity | ~15s |
| Local `./gradlew check` | Compile + ArchUnit + Modulith verify + unit tests | ~90s |
| CI Pipeline (full) | All 12 steps including OpenAPI gen, TypeScript gen, integration tests | ~5 min |

### Frontend Implementation Checklist

Every AI agent implementing a frontend feature MUST follow this sequence:
1. ✅ Check generated types in `frontend/types/api.d.ts` — what does the API return?
2. ✅ Check `risk-guard-tokens.json` — are there business constants relevant to this feature?
3. ✅ Check `frontend/i18n/hu/{module}.json` — do translation keys exist, or do I need to add them?
4. ✅ Check existing Pinia store in `frontend/stores/{module}.ts` — does state management exist, or do I create it?
5. ✅ Check PrimeVue components — is there a pre-built component (DataTable, Stepper, Toast) that fits?
6. 🔨 Implement the component in `frontend/components/{Module}/ComponentName.vue`
7. ✅ Add missing i18n keys to BOTH `hu/{module}.json` AND `en/{module}.json`
8. ✅ Write `ComponentName.spec.ts` co-located with the component

### Reference Implementation

The `screening` module serves as the reference implementation. All other modules MUST follow its patterns. Before any non-screening story begins, validate the reference against this checklist:
- [ ] Controller with `@Valid` request body and typed response
- [ ] DTO record with `from()` factory method
- [ ] Module facade `@Service` with `@Transactional`
- [ ] Application Event publication
- [ ] jOOQ query scoped to owned tables only
- [ ] i18n: no hardcoded strings, keys in `hu/screening.json` + `en/screening.json`
- [ ] Logging: `@LogSafe` types only, MDC context
- [ ] Business constants from `RiskGuardProperties`
- [ ] Unit test + integration test
- [ ] Co-located frontend component with `spec.ts`

## Project Structure & Boundaries (Step 6)

### Complete Project Directory Structure

```
risk-guard/                                    # Monorepo root
├── .github/
│   └── workflows/
│       ├── ci.yml                             # Full 12-step CI pipeline
│       └── deploy.yml                         # GCP Cloud Run deployment
├── .gitignore
├── docker-compose.yml                         # Root: PostgreSQL for all stacks
├── docker-compose.dev.yml                     # Dev overrides (debug ports, volumes)
├── risk-guard-tokens.json                     # Business constants (shared by both stacks)
├── CONTRIBUTING.md                            # Quick-start guide for AI agents
├── README.md
│
├── backend/                                   # Spring Boot 4.0.3 application
│   ├── build.gradle.kts                       # Gradle Kotlin DSL build config
│   ├── settings.gradle.kts
│   ├── gradle/
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── .env.example                           # Environment variable documentation
│   ├── Dockerfile                             # JVM image (GraalVM native post-MVP)
│   │
│   └── src/
│       ├── main/
│       │   ├── java/hu/riskguard/
│       │   │   ├── RiskGuardApplication.java  # @SpringBootApplication entry point
│       │   │   │
│       │   │   ├── core/                      # Shared infrastructure (NOT a Spring Modulith module)
│       │   │   │   ├── config/
│       │   │   │   │   ├── SecurityConfig.java        # OAuth2 + JWT + CORS + Bucket4j
│       │   │   │   │   ├── JacksonConfig.java         # camelCase global naming
│       │   │   │   │   ├── JooqConfig.java            # jOOQ DSLContext bean
│       │   │   │   │   ├── CacheConfig.java           # Caffeine cache manager
│       │   │   │   │   ├── ResilienceConfig.java      # Resilience4j defaults
│       │   │   │   │   ├── OpenApiConfig.java         # springdoc-openapi customization
│       │   │   │   │   └── RiskGuardProperties.java   # @ConfigurationProperties from tokens.json
│       │   │   │   ├── events/
│       │   │   │   │   └── package-info.java          # EVENT CATALOG — all application events documented
│       │   │   │   ├── security/
│       │   │   │   │   ├── TenantFilter.java          # Servlet filter: sets tenantId in MDC + SecurityContext
│       │   │   │   │   ├── TierRequired.java          # @TierRequired annotation
│       │   │   │   │   ├── TierRequiredAspect.java    # AOP enforcing tier checks
│       │   │   │   │   ├── ExportLocale.java          # @ExportLocale annotation
│       │   │   │   │   └── LogSafe.java               # @LogSafe annotation for logging-safe types
│       │   │   │   ├── exception/
│       │   │   │   │   ├── RiskGuardException.java    # Base exception
│       │   │   │   │   ├── NotFoundException.java
│       │   │   │   │   ├── ValidationException.java
│       │   │   │   │   ├── ScrapingException.java
│       │   │   │   │   ├── TenantAccessDeniedException.java
│       │   │   │   │   └── GlobalExceptionHandler.java # @ControllerAdvice → RFC 7807
│       │   │   │   └── util/
│       │   │   │       ├── HashUtil.java               # SHA-256 hashing for audit trail
│       │   │   │       └── HungarianTaxNumber.java     # @HungarianTaxNumber validator
│       │   │   │
│       │   │   ├── screening/                 # MODULE: Partner Screening (FR1-FR4)
│       │   │   │   ├── api/
│       │   │   │   │   ├── ScreeningController.java           # REST endpoints
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── PartnerSearchRequest.java      # @Valid request
│       │   │   │   │       ├── VerdictResponse.java           # Response with from()
│       │   │   │   │       └── CompanySnapshotResponse.java
│       │   │   │   ├── domain/
│       │   │   │   │   ├── ScreeningService.java              # Module FACADE @Service
│       │   │   │   │   ├── VerdictEngine.java                 # State machine (pure function)
│       │   │   │   │   ├── CompanyDataAggregator.java         # Orchestrates parallel scraping via Virtual Threads
│       │   │   │   │   └── events/
│       │   │   │   │       ├── PartnerStatusChanged.java
│       │   │   │   │       └── PartnerSearchCompleted.java
│       │   │   │   └── internal/
│       │   │   │       └── ScreeningRepository.java           # jOOQ (tables: company_snapshots, verdicts, search_audit_log)
│       │   │   │
│       │   │   ├── epr/                       # MODULE: EPR Compliance (FR8-FR10)
│       │   │   │   ├── api/
│       │   │   │   │   ├── EprController.java
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── QuestionnaireStepRequest.java
│       │   │   │   │       ├── QuestionnaireStepResponse.java
│       │   │   │   │       └── EprExportResponse.java
│       │   │   │   ├── domain/
│       │   │   │   │   ├── EprService.java                    # Module FACADE @Service
│       │   │   │   │   ├── DagEngine.java                     # JSON DAG traversal
│       │   │   │   │   ├── MohuExporter.java                  # CSV/XLSX generation (@ExportLocale("hu"))
│       │   │   │   │   ├── FeeCalculator.java                 # EPR fee computation
│       │   │   │   │   └── events/
│       │   │   │   │       └── EprExportGenerated.java
│       │   │   │   └── internal/
│       │   │   │       └── EprRepository.java                 # jOOQ (tables: epr_configs, epr_calculations, epr_exports)
│       │   │   │
│       │   │   ├── scraping/                  # MODULE: Scraper Engine (FR1-FR2, FR11)
│       │   │   │   ├── api/
│       │   │   │   │   ├── ScrapingAdminController.java       # Admin health dashboard API
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── AdapterHealthResponse.java
│       │   │   │   │       └── CanaryStatusResponse.java
│       │   │   │   ├── domain/
│       │   │   │   │   ├── ScrapingService.java               # Module FACADE @Service
│       │   │   │   │   ├── CompanyDataPort.java               # PORT INTERFACE
│       │   │   │   │   ├── CanaryValidator.java               # Canary company verification
│       │   │   │   │   └── events/
│       │   │   │   │       ├── AdapterHealthChanged.java
│       │   │   │   │       └── CanaryValidationFailed.java
│       │   │   │   └── internal/
│       │   │   │       ├── adapters/
│       │   │   │       │   ├── NavDebtAdapter.java            # JSoup — NAV debt list
│       │   │   │       │   ├── ECegjegyzekAdapter.java        # JSoup — company registry
│       │   │   │       │   ├── CegkozlonyAdapter.java         # JSoup — insolvency gazette
│       │   │   │       │   └── PlaywrightAdapterClient.java   # Client — sends ScrapeRequest to worker
│       │   │   │       ├── ScrapingRepository.java            # jOOQ (tables: scraper_health, canary_companies, dom_fingerprints)
│       │   │   │       └── repair-guides/
│       │   │   │           ├── nav-repair-guide.md
│       │   │   │           ├── ecegjegyzek-repair-guide.md
│       │   │   │           └── cegkozlony-repair-guide.md
│       │   │   │
│       │   │   ├── notification/              # MODULE: Alerts & Watchlist (FR5-FR7)
│       │   │   │   ├── api/
│       │   │   │   │   ├── WatchlistController.java
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── WatchlistEntryRequest.java
│       │   │   │   │       ├── WatchlistEntryResponse.java
│       │   │   │   │       └── NotificationStatusResponse.java
│       │   │   │   ├── domain/
│       │   │   │   │   ├── NotificationService.java           # Module FACADE @Service
│       │   │   │   │   ├── WatchlistMonitor.java              # @Scheduled 24h cycle
│       │   │   │   │   ├── OutboxProcessor.java               # Outbox pattern retry scheduler
│       │   │   │   │   ├── ResendEmailSender.java             # Resend API integration
│       │   │   │   │   └── events/
│       │   │   │   │       └── AlertDeliveryFailed.java
│       │   │   │   └── internal/
│       │   │   │       └── NotificationRepository.java        # jOOQ (tables: watchlist_entries, notification_outbox)
│       │   │   │
│       │   │   └── identity/                  # MODULE: Auth, RBAC, Tenants (cross-cutting)
│       │   │       ├── api/
│       │   │       │   ├── AuthController.java                # Login, SSO callbacks
│       │   │       │   ├── TenantController.java              # Tenant management, context switch
│       │   │       │   └── dto/
│       │   │       │       ├── LoginRequest.java
│       │   │       │       ├── UserProfileResponse.java
│       │   │       │       ├── TenantSwitchRequest.java
│       │   │       │       └── TenantSwitchResponse.java
│       │   │       ├── domain/
│       │   │       │   ├── IdentityService.java               # Module FACADE @Service
│       │   │       │   ├── TenantMandateService.java          # Accountant tenant validation
│       │   │       │   ├── GuestSessionManager.java           # Synthetic guest tenants
│       │   │       │   └── events/
│       │   │       │       └── TenantContextSwitched.java
│       │   │       └── internal/
│       │   │           └── IdentityRepository.java            # jOOQ (tables: users, tenants, tenant_mandates, guest_sessions)
│       │   │
│       │   └── resources/
│       │       ├── application.yml                            # Main config (profiles: dev, prod)
│       │       ├── application-dev.yml                        # Local dev overrides
│       │       ├── application-prod.yml                       # Production config
│       │       ├── logback-spring.xml                         # Structured JSON logging + redaction filter
│       │       ├── messages_hu.properties                     # Backend-only i18n (emails, exports)
│       │       ├── messages_en.properties
│       │       └── db/migration/                              # Flyway migrations (timestamp-based)
│       │           ├── V20260304_001__create_identity_tables.sql
│       │           ├── V20260304_002__create_screening_tables.sql
│       │           ├── V20260304_003__create_scraping_tables.sql
│       │           ├── V20260304_004__create_notification_tables.sql
│       │           ├── V20260304_005__create_epr_tables.sql
│       │           └── V20260304_006__seed_canary_companies.sql
│       │
│       └── test/
│           └── java/hu/riskguard/
│               ├── architecture/                              # ArchUnit test suite
│               │   ├── NamingConventionTest.java
│               │   ├── ModuleBoundaryTest.java
│               │   ├── DtoConventionTest.java
│               │   ├── LoggingConventionTest.java
│               │   ├── BusinessConstantTest.java
│               │   └── ModulithVerificationTest.java
│               ├── screening/
│               │   ├── VerdictEngineTest.java                 # Golden snapshot regression (50+ cases)
│               │   ├── ScreeningServiceIntegrationTest.java
│               │   └── ScreeningControllerTest.java
│               ├── epr/
│               │   ├── DagEngineTest.java
│               │   ├── MohuExporterTest.java                  # Schema validation + round-trip
│               │   └── FeeCalculatorTest.java                 # EPR golden test cases
│               ├── scraping/
│               │   ├── NavDebtAdapterTest.java
│               │   ├── CanaryValidatorTest.java
│               │   └── CompanyDataAggregatorTest.java
│               ├── notification/
│               │   ├── OutboxProcessorTest.java
│               │   └── WatchlistMonitorTest.java
│               ├── identity/
│               │   ├── TenantIsolationIntegrationTest.java    # Multi-tenant fixture tests
│               │   └── GuestSessionManagerTest.java
│               └── fixtures/
│                   ├── CanaryCompanyFixtures.java             # Shared canary test data
│                   └── TenantFixtures.java                    # Multi-tenant test helpers
│
├── worker/                                    # Playwright scraper worker (Cloud Run Jobs)
│   ├── Dockerfile                             # JVM + Chrome sidecar
│   ├── build.gradle.kts
│   └── src/main/java/hu/riskguard/worker/
│       ├── WorkerApplication.java
│       ├── PlaywrightScraperJob.java
│       └── ScrapeRequest.java
│
├── frontend/                                  # Nuxt 3 application
│   ├── package.json
│   ├── nuxt.config.ts                         # Nuxt config: hybrid rendering, i18n, PrimeVue
│   ├── tsconfig.json
│   ├── tailwind.config.ts
│   ├── .eslintrc.cjs                          # ESLint: @intlify/no-raw-text, naming rules
│   ├── .env.example                           # Environment variable documentation
│   ├── vitest.config.ts
│   ├── Dockerfile                             # Static build output for CDN
│   │
│   ├── app.vue                                # Root app component
│   ├── error.vue                              # Global error page
│   │
│   ├── assets/
│   │   └── css/
│   │       └── main.css                       # Tailwind base imports
│   │
│   ├── components/
│   │   ├── Common/
│   │   │   ├── HealthBanner.vue               # Global portal health banner
│   │   │   ├── HealthBanner.spec.ts
│   │   │   ├── LocaleSwitcher.vue             # HU/EN language toggle
│   │   │   └── LocaleSwitcher.spec.ts
│   │   ├── Screening/
│   │   │   ├── SearchBar.vue                  # Tax number input + search
│   │   │   ├── SearchBar.spec.ts
│   │   │   ├── VerdictCard.vue                # Reliable/At-Risk verdict display
│   │   │   ├── VerdictCard.spec.ts
│   │   │   ├── CompanySnapshot.vue            # Detailed company data view
│   │   │   └── CompanySnapshot.spec.ts
│   │   ├── Epr/
│   │   │   ├── WizardStepper.vue              # Multi-step questionnaire
│   │   │   ├── WizardStepper.spec.ts
│   │   │   ├── MaterialSelector.vue           # DAG-driven material classification
│   │   │   ├── MaterialSelector.spec.ts
│   │   │   ├── ExportButton.vue               # CSV/XLSX download with locale notice
│   │   │   └── ExportButton.spec.ts
│   │   ├── Notification/
│   │   │   ├── WatchlistTable.vue             # PrimeVue DataTable + filter/sort
│   │   │   ├── WatchlistTable.spec.ts
│   │   │   ├── AlertHistoryList.vue
│   │   │   └── AlertHistoryList.spec.ts
│   │   ├── Identity/
│   │   │   ├── TenantSwitcher.vue             # Accountant context-switch
│   │   │   ├── TenantSwitcher.spec.ts
│   │   │   ├── TierBadge.vue                  # ALAP/PRO/PRO_EPR indicator
│   │   │   └── TierBadge.spec.ts
│   │   └── Admin/
│   │       ├── ScraperHealthDashboard.vue      # Adapter status + charts
│   │       ├── ScraperHealthDashboard.spec.ts
│   │       ├── EprConfigManager.vue            # EPR JSON config editor
│   │       └── EprConfigManager.spec.ts
│   │
│   ├── composables/
│   │   ├── formatting/
│   │   │   ├── useDateShort.ts                # HU: '2026. 03. 04.' | EN: 'Mar 4, 2026'
│   │   │   ├── useDateFull.ts                 # Full localized date
│   │   │   ├── useDateRelative.ts             # '2 órája' / '2 hours ago'
│   │   │   └── useDateApi.ts                  # ISO 8601 UTC (non-localized)
│   │   ├── api/
│   │   │   ├── useApi.ts                      # API base URL + typed endpoint methods
│   │   │   └── useApiError.ts                 # RFC 7807 → i18n Toast mapper
│   │   └── auth/
│   │       ├── useAuth.ts                     # Login/logout, current user
│   │       └── useTierGate.ts                 # Check tier access for UI elements
│   │
│   ├── i18n/
│   │   ├── hu/
│   │   │   ├── common.json                    # Shared: actions, states, errors
│   │   │   ├── screening.json
│   │   │   ├── epr.json
│   │   │   ├── notification.json
│   │   │   ├── identity.json
│   │   │   └── admin.json
│   │   └── en/
│   │       ├── common.json
│   │       ├── screening.json
│   │       ├── epr.json
│   │       ├── notification.json
│   │       ├── identity.json
│   │       └── admin.json
│   │
│   ├── layouts/
│   │   ├── default.vue                        # Authenticated layout (sidebar + header)
│   │   ├── guest.vue                          # Guest/demo layout
│   │   └── public.vue                         # SEO public pages layout
│   │
│   ├── middleware/
│   │   ├── auth.ts                            # Redirect unauthenticated users
│   │   └── tier.ts                            # Check tier for gated routes
│   │
│   ├── pages/
│   │   ├── index.vue                          # Landing / marketing page
│   │   ├── login.vue                          # SSO login page
│   │   ├── demo.vue                           # Guest demo mode
│   │   ├── dashboard/
│   │   │   └── index.vue                      # Main verdict dashboard
│   │   ├── screening/
│   │   │   └── [taxNumber].vue                # Single company detail view
│   │   ├── epr/
│   │   │   ├── index.vue                      # EPR wizard entry
│   │   │   └── export/[id].vue                # Export detail + download
│   │   ├── watchlist/
│   │   │   └── index.vue                      # Watchlist management
│   │   ├── admin/
│   │   │   ├── scrapers.vue                   # Scraper health dashboard
│   │   │   └── epr-config.vue                 # EPR config management
│   │   └── company/
│   │       └── [taxNumber].vue                # PUBLIC: SEO gateway stub (SSR/ISR)
│   │
│   ├── plugins/
│   │   └── primevue.ts                        # PrimeVue 4 initialization
│   │
│   ├── public/
│   │   ├── favicon.ico
│   │   └── robots.txt
│   │
│   ├── server/
│   │   └── routes/
│   │       └── company/[taxNumber].get.ts     # Nuxt server route for JSON-LD SEO data
│   │
│   ├── stores/
│   │   ├── screening.ts                       # Pinia: search state, verdict cache
│   │   ├── epr.ts                             # Pinia: wizard state, DAG traversal
│   │   ├── notification.ts                    # Pinia: watchlist entries
│   │   ├── identity.ts                        # Pinia: user, tenant, tier
│   │   └── health.ts                          # Pinia: global portal health status
│   │
│   ├── test/                                  # Shared test infrastructure
│   │   ├── fixtures/
│   │   │   ├── mockCompany.ts                 # Canonical mock VerdictResponse
│   │   │   ├── mockEprCalculation.ts          # Canonical mock EprExportResponse
│   │   │   ├── mockUser.ts                    # Canonical mock UserProfileResponse
│   │   │   └── mockWatchlist.ts               # Canonical mock WatchlistEntryResponse
│   │   ├── helpers/
│   │   │   ├── renderWithProviders.ts         # Wraps component with Pinia + i18n + PrimeVue
│   │   │   └── mockFetch.ts                   # Standardized $fetch mock
│   │   └── setup.ts                           # Vitest global setup
│   │
│   └── types/
│       └── api.d.ts                           # AUTO-GENERATED from OpenAPI — never edit manually
│
├── scripts/                                   # Build & CI helper scripts
│   ├── generate-types.sh                      # OpenAPI → TypeScript + Zod generation
│   ├── check-i18n-parity.sh                   # Verify HU/EN key parity
│   ├── check-token-usage.sh                   # Verify risk-guard-tokens.json references
│   └── check-log-safety.sh                    # Scan for PII in log statements
│
├── docs/                                      # Operational documentation
│   ├── runbooks/
│   │   ├── scraper-repair.md                  # How to fix a broken scraper adapter
│   │   ├── epr-config-update.md               # How to update EPR fee tables
│   │   ├── deployment.md                      # GCP Cloud Run deployment procedure
│   │   └── incident-response.md               # What to do when alerts fire
│   └── status-page/
│       └── index.html                         # Automated status page (deployed to Cloudflare Pages)
│
└── _bmad-output/                              # BMad planning artifacts (existing)
    ├── planning-artifacts/
    │   ├── prd.md
    │   ├── product-brief-risk_guard-2026-03-04.md
    │   ├── architecture.md                    # THIS DOCUMENT
    │   └── research/
    └── excalidraw-diagrams/
```

### Architectural Boundaries

**API Boundaries:**
- All REST endpoints behind `/api/v1/` prefix.
- Public SEO pages served by Nuxt SSR at `/company/{taxNumber}`.
- Admin endpoints require `ADMIN` role (checked by `@TierRequired`).
- Guest endpoints rate-limited by Bucket4j (IP + fingerprint).
- OpenAPI spec auto-generated at `/v3/api-docs`.

**Module Boundaries (Spring Modulith enforced):**
- Each module exposes: 1 facade `@Service` + public DTOs in `api.dto` + Application Events.
- No direct imports of another module's `internal/` or `domain/` packages.
- Cross-module data access via facade calls (synchronous) or events (asynchronous).
- jOOQ codegen scoped per module — each module can only query its owned tables.

**Data Boundaries:**
- `tenant_id NOT NULL` on every tenant-scoped table.
- `TenantFilter` at repository layer reads `active_tenant_id` from `SecurityContextHolder`.
- Guest sessions use synthetic tenant IDs (`guest-{session_uuid}`).
- Caffeine cache keys include `tenant_id` prefix for isolation.

### Requirements to Structure Mapping

| FR | Module | Backend Key Files | Frontend Key Files |
|---|---|---|---|
| FR1: Tax Number Search | `screening` | `ScreeningController`, `PartnerSearchRequest` | `SearchBar.vue`, `pages/dashboard/` |
| FR2: NAV/e-Cégjegyzék Retrieval | `scraping` | `NavDebtAdapter`, `ECegjegyzekAdapter` | (backend only) |
| FR3: State-Machine Verdict | `screening` | `VerdictEngine`, `VerdictResponse` | `VerdictCard.vue` |
| FR4: Suspended Tax Detection | `screening` | `VerdictEngine` (TAX_SUSPENDED state) | `VerdictCard.vue` (badge) |
| FR5: Watchlist CRUD | `notification` | `WatchlistController`, DTOs | `WatchlistTable.vue`, `pages/watchlist/` |
| FR6: 24h Status Checks | `notification` | `WatchlistMonitor` (@Scheduled) | (results in WatchlistTable) |
| FR7: Email Alerts | `notification` | `OutboxProcessor`, `ResendEmailSender` | `AlertHistoryList.vue` |
| FR8: EPR Questionnaire | `epr` | `EprController`, `DagEngine` | `WizardStepper.vue`, `MaterialSelector.vue` |
| FR9: Fee Calculation | `epr` | `FeeCalculator` | `WizardStepper.vue` (results step) |
| FR10: MOHU Export | `epr` | `MohuExporter`, `EprExportResponse` | `ExportButton.vue`, `pages/epr/export/` |
| FR11: Scraper Dashboard | `scraping` | `ScrapingAdminController` | `ScraperHealthDashboard.vue`, `pages/admin/scrapers.vue` |
| FR12: EPR Config Admin | `epr` | `EprController` (config endpoints) | `EprConfigManager.vue`, `pages/admin/epr-config.vue` |

### Cross-Cutting Concerns Mapping

| Concern | Backend Location | Frontend Location |
|---|---|---|
| Tenant Isolation | `core/security/TenantFilter.java` + all `*Repository.java` | `stores/identity.ts` (active tenant) |
| Audit Trail (SHA-256) | `core/util/HashUtil.java` + `screening/internal/` | (backend only) |
| Data Freshness | `core/config/RiskGuardProperties.java` + `VerdictEngine.java` | `VerdictCard.vue` (freshness badges) |
| Rate Limiting | `core/config/SecurityConfig.java` (Bucket4j) | (transparent to frontend) |
| i18n | `resources/messages_*.properties` (exports/emails) | `i18n/**/*.json` (all user-facing text) |
| Health Banner | Resilience4j Actuator endpoints | `stores/health.ts` + `HealthBanner.vue` |

### External Integration Points

| Integration | Module | Backend File | Protocol |
|---|---|---|---|
| NAV Open Data | `scraping` | `NavDebtAdapter.java` | JSoup HTTP scraping |
| e-Cégjegyzék | `scraping` | `ECegjegyzekAdapter.java` | JSoup HTTP scraping |
| Cégközlöny | `scraping` | `CegkozlonyAdapter.java` | JSoup HTTP scraping |
| Anti-bot Sites | `worker` | `PlaywrightScraperJob.java` | Playwright (Chrome sidecar) |
| MOHU Portal | `epr` | `MohuExporter.java` | CSV/XLSX file generation |
| Google SSO | `identity` | `SecurityConfig.java` | OAuth2/OIDC |
| Microsoft Entra ID | `identity` | `SecurityConfig.java` | OAuth2/OIDC |
| Resend Email API | `notification` | `ResendEmailSender.java` | REST API |
| OpenAI (Spring AI) | `screening` | `ScreeningService.java` | REST API (async enrichment) |
| GCP Cloud Run | infrastructure | `Dockerfile`, `deploy.yml` | Container |
| GCP Cloud Run Jobs | infrastructure | `worker/Dockerfile` | Container (batch) |

### Data Flow

```
User → Nuxt Frontend (SPA)
         → $fetch(/api/v1/...) → Spring Boot API (Cloud Run)
                                    → ScreeningService (facade)
                                        → CompanyDataAggregator
                                            → [Virtual Threads]
                                                → NavDebtAdapter (JSoup)
                                                → ECegjegyzekAdapter (JSoup)
                                                → PlaywrightAdapterClient → Worker (Cloud Run Jobs)
                                        → VerdictEngine (state machine)
                                        → ScreeningRepository (jOOQ → PostgreSQL)
                                    → publishes PartnerStatusChanged event
                                        → NotificationService listens
                                            → OutboxProcessor → Resend API
         ← VerdictResponse (JSON) ←
```

### Development Workflow

**Local Development:**
1. `docker compose up -d` — Start PostgreSQL
2. `cd backend && ./gradlew bootRun` — Start Spring Boot (dev profile)
3. `cd frontend && npm run dev` — Start Nuxt dev server (hot reload)

**Flyway Migration Naming:**
```
V{YYYYMMDD}_{NNN}__{description}.sql
Example: V20260315_001__add_risk_score_to_verdicts.sql
```

**Build Process:**
- Backend: `./gradlew build` → fat JAR → Docker image → Cloud Run
- Frontend: `npm run build` → static output → Cloud Storage/CDN
- Worker: `./gradlew :worker:build` → Docker image (JVM + Chrome) → Cloud Run Jobs

## Architecture Validation Results (Step 7)

### Coherence Validation ✅

**Decision Compatibility:**
All technology choices verified compatible. Spring Boot 4.0.3 + Spring Modulith 2.0.3 + jOOQ OSS + Nuxt 3 + PrimeVue 4 form a coherent stack. One dependency swap applied: use standalone `io.github.resilience4j:resilience4j-spring-boot3` instead of `cloud-resilience4j` (Spring Cloud compatibility concern with Boot 4.0.3).

**Pattern Consistency:**
All patterns support architectural decisions. Naming chain is clean: DB (snake_case) → jOOQ codegen (camelCase) → DTO (camelCase) → JSON (camelCase). i18n boundary is crisp: frontend owns all user-facing text, backend owns export/email text. Error handling pipeline is end-to-end: RFC 7807 codes → `useApiError` → i18n Toast.

**Structure Alignment:**
Monorepo structure (`backend/` + `frontend/` + `worker/`) supports atomic PRs and shared `risk-guard-tokens.json`. Module-per-package with scoped jOOQ codegen enforces physical isolation matching logical isolation. Frontend composables organized by concern (`formatting/`, `api/`, `auth/`).

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:**

| FR | Status | Covered By |
|---|---|---|
| FR1: Tax number search | ✅ | `screening` module, `ScreeningController`, `SearchBar.vue` |
| FR2: NAV/e-Cégjegyzék data retrieval | ✅ | `scraping` module, `CompanyDataPort` adapters, Virtual Thread parallel orchestration |
| FR3: State-machine verdicts | ✅ | `VerdictEngine` (pure function), 50+ golden snapshot regression tests |
| FR4: Suspended tax detection | ✅ | `TAX_SUSPENDED` first-class state in VerdictEngine |
| FR5: Watchlist CRUD | ✅ | `notification` module, `WatchlistController`, `WatchlistTable.vue` |
| FR6: 24h status checks | ✅ | `WatchlistMonitor` (`@Scheduled`), canary validation |
| FR7: Email alerts | ✅ | Outbox pattern, `ResendEmailSender`, digest mode |
| FR8: EPR questionnaire | ✅ | `epr` module, `DagEngine`, `WizardStepper.vue` |
| FR9: Fee calculation | ✅ | `FeeCalculator`, versioned JSON config, EPR golden test cases |
| FR10: MOHU export | ✅ | `MohuExporter`, `@ExportLocale("hu")`, schema version gate |
| FR11: Scraper dashboard | ✅ | Resilience4j Actuator endpoints, `ScraperHealthDashboard.vue` |
| FR12: EPR config admin | ✅ | Hot-swappable JSON config, `EprConfigManager.vue` |

**Non-Functional Requirements Coverage:**

| NFR | Status | How |
|---|---|---|
| NFR1: < 30s verdict latency | ✅ | Virtual Thread parallel scraping, Caffeine cache, 15-min idempotency guard |
| NFR2: GraalVM < 200ms startup | ⏳ Deferred | JVM for MVP, native-image-ready design. Post-MVP optimization. |
| NFR3: Min-Instances during business hours | ✅ | Cloud Run config in `deploy.yml` |
| NFR4: SHA-256 cryptographic hashing | ✅ | `HashUtil.java`, audit trail in `search_audit_log` |
| NFR5: AES-256/TLS 1.3 | ✅ | GCP Cloud SQL encryption at rest, Cloud Run TLS |
| NFR6: SEO gateway stubs with JSON-LD | ✅ | Nuxt hybrid rendering, `pages/company/[taxNumber].vue` (SSR/ISR) |
| NFR7: Scale-to-zero off-peak | ⏳ Partial | Requires GraalVM native (deferred). JVM + Min-Instances: 1 for MVP. |

### Implementation Readiness Validation ✅

**Decision Completeness:**
- All critical decisions documented with verified versions ✅
- 5 ADRs + comprehensive Step 4 decisions table ✅
- Implementation patterns cover 14+ conflict points with automated enforcement ✅
- Reference implementation (`screening` module) with review checklist ✅

**Structure Completeness:**
- ~120 files defined across backend/frontend/worker ✅
- All files mapped to FRs via requirements-to-structure table ✅
- External integration points specified with protocols ✅
- Module boundaries enforced at code AND SQL level ✅

**Pattern Completeness:**
- Naming conventions: DB, API, JSON, Java, Vue, i18n keys ✅
- Communication: facade (sync) vs. event (async) matrix ✅
- Error handling: RFC 7807 → codes → i18n pipeline ✅
- Logging: structured JSON, `@LogSafe`, redaction filter ✅
- Testing: ArchUnit (6 files), frontend fixtures, canary multi-layer ✅
- CI: 12-step pipeline with local vs. CI split ✅

### Gap Analysis Results

**Critical Gaps: None Found ✅**

**Important Gaps Addressed:**

| # | Gap | Resolution |
|---|---|---|
| 1 | No database schema reference | ER summary added with all 15 tables, columns, relationships, and key indexes |
| 2 | Resilience4j Spring Cloud compatibility | Swapped to standalone `resilience4j-spring-boot3` |

**Nice-to-Have Gaps (Documented for Future):**

| # | Gap | Recommendation |
|---|---|---|
| 1 | No rate limit response format | Add `urn:riskguard:error:rate-limit-exceeded` to RFC 7807 types with `Retry-After` header |
| 2 | No WebSocket/SSE for real-time updates | Polling via `useFetch` (60s interval) sufficient for MVP |
| 3 | No WCAG standard specified | PrimeVue 4 is WCAG 2.1 compliant by default. Rely on built-in a11y. |
| 4 | No E2E testing strategy | Unit + integration tests sufficient for MVP. Defer Playwright/Cypress E2E to post-MVP. |

### Entity-Relationship Summary

#### `identity` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `tenants` | `id` (UUID, PK), `name`, `tier` (ENUM: ALAP/PRO/PRO_EPR), `created_at` | — |
| `users` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `email`, `name`, `role` (ENUM: GUEST/SME_ADMIN/ACCOUNTANT), `preferred_language` (ENUM: hu/en), `sso_provider`, `sso_subject`, `created_at` | belongs to `tenants` |
| `tenant_mandates` | `id` (UUID, PK), `accountant_user_id` (FK → users), `tenant_id` (FK → tenants), `valid_from`, `valid_to` | links accountant to client tenants |
| `guest_sessions` | `id` (UUID, PK), `tenant_id` (UUID, synthetic, NOT NULL), `session_fingerprint`, `companies_checked` (INT), `daily_checks` (INT), `created_at`, `expires_at` | TTL-based, purged daily |

#### `screening` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `company_snapshots` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `tax_number` (VARCHAR(11)), `snapshot_data` (JSONB), `source_urls` (JSONB), `dom_fingerprint_hash` (VARCHAR(64)), `checked_at` (TIMESTAMPTZ), `created_at` | belongs to `tenants` |
| `verdicts` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `snapshot_id` (FK → company_snapshots), `tax_number`, `status` (ENUM: RELIABLE/AT_RISK/INCOMPLETE/TAX_SUSPENDED/UNAVAILABLE), `confidence` (ENUM: FRESH/STALE/UNAVAILABLE), `sha256_hash` (VARCHAR(64)), `disclaimer_text`, `ai_narrative` (TEXT, nullable), `created_at` | belongs to `company_snapshots` |
| `search_audit_log` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `user_id` (FK → users), `tax_number`, `verdict_id` (FK → verdicts), `source_urls` (JSONB), `sha256_hash` (VARCHAR(64)), `searched_at` (TIMESTAMPTZ) | GDPR audit trail |

#### `scraping` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `scraper_health` | `id` (UUID, PK), `adapter_name` (VARCHAR), `status` (ENUM: HEALTHY/DEGRADED/CIRCUIT_OPEN), `last_success_at` (TIMESTAMPTZ), `last_failure_at` (TIMESTAMPTZ), `failure_count` (INT), `mtbf_hours` (DECIMAL) | per-adapter health |
| `canary_companies` | `id` (UUID, PK), `tax_number`, `adapter_name`, `expected_data` (JSONB), `last_validated_at` (TIMESTAMPTZ), `validation_status` (ENUM: PASS/FAIL) | known-good reference |
| `dom_fingerprints` | `id` (UUID, PK), `adapter_name`, `selector_paths_hash` (VARCHAR(64)), `baseline_hash` (VARCHAR(64)), `divergence_pct` (DECIMAL), `recorded_at` (TIMESTAMPTZ) | DOM change detection |

#### `notification` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `watchlist_entries` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `user_id` (FK → users), `tax_number`, `company_name`, `last_verdict_status`, `last_checked_at` (TIMESTAMPTZ), `created_at` | per-tenant watchlist |
| `notification_outbox` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `user_id` (FK → users), `type` (ENUM: ALERT/DIGEST/MONITORING_INTERRUPTED), `payload` (JSONB), `status` (ENUM: PENDING/SENT/FAILED), `retry_count` (INT), `next_retry_at` (TIMESTAMPTZ), `created_at`, `sent_at` | outbox pattern |

#### `epr` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `epr_configs` | `id` (UUID, PK), `version` (INT), `config_data` (JSONB), `schema_version` (VARCHAR), `schema_verified` (BOOLEAN), `created_at`, `activated_at` | versioned config with schema gate |
| `epr_calculations` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `config_version` (INT, FK → epr_configs.version), `traversal_path` (JSONB), `material_classification`, `fee_amount` (DECIMAL), `currency` (VARCHAR, default 'HUF'), `created_at` | DAG traversal + result |
| `epr_exports` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `calculation_id` (FK → epr_calculations), `config_version` (INT), `export_format` (ENUM: CSV/XLSX), `file_hash` (VARCHAR(64)), `exported_at` (TIMESTAMPTZ) | export tracking |

#### Key Indexes

```sql
-- Time-series queries (BRIN for chronological data)
CREATE INDEX idx_snapshots_checked_at ON company_snapshots USING BRIN (checked_at);
CREATE INDEX idx_verdicts_created_at ON verdicts USING BRIN (created_at);
CREATE INDEX idx_audit_searched_at ON search_audit_log USING BRIN (searched_at);

-- Lookup queries (B-tree)
CREATE INDEX idx_snapshots_tenant_tax ON company_snapshots (tenant_id, tax_number);
CREATE INDEX idx_verdicts_tenant_tax ON verdicts (tenant_id, tax_number);
CREATE INDEX idx_watchlist_tenant ON watchlist_entries (tenant_id);
CREATE INDEX idx_outbox_status ON notification_outbox (status, next_retry_at);
CREATE INDEX idx_guest_expires ON guest_sessions (expires_at);
```

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed (High)
- [x] Technical constraints identified (solo dev, EU residency, government portal fragility)
- [x] Cross-cutting concerns mapped (tenant isolation, audit trail, data freshness, i18n)

**✅ Architectural Decisions**
- [x] 5 ADRs documented with rationale and rejected alternatives
- [x] Technology stack fully specified with verified versions
- [x] Integration patterns defined (facade + events)
- [x] Performance considerations addressed (Virtual Threads, Caffeine, parallel scraping)

**✅ Implementation Patterns**
- [x] Naming conventions established (DB, API, JSON, Java, Vue, i18n keys)
- [x] Structure patterns defined (module anatomy, DTO mapping, jOOQ scoping)
- [x] Communication patterns specified (sync facade vs. async events matrix)
- [x] Process patterns documented (error handling, logging, loading states)
- [x] i18n/l10n patterns defined (display vs. export locale, namespace-per-file)
- [x] Automated enforcement designed (ArchUnit, ESLint, CI scripts, 12-step pipeline)

**✅ Project Structure**
- [x] Complete directory structure defined (~120 files across 3 subprojects)
- [x] Component boundaries established (module facades, scoped jOOQ codegen)
- [x] Integration points mapped (9 external integrations with protocols)
- [x] Requirements-to-structure mapping complete (12 FRs → specific files)
- [x] Database schema reference with 15 tables, relationships, and indexes

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION ✅

**Confidence Level:** HIGH — based on extensive elicitation (Code Review Gauntlet, Red Team, SCAMPER, Failure Mode Analysis, Rubber Duck, Critique & Refine) across Steps 2-6.

**Key Strengths:**
- Deterministic state machine with immutable golden snapshot regression suite
- Defense-in-depth tenant isolation (JWT claims + repository filter + DB constraint)
- Self-enforcing architecture (ArchUnit + ESLint + CI scripts catch violations automatically)
- OpenAPI → TypeScript → Zod pipeline eliminates API contract drift
- Business constants token file prevents magic number divergence across stacks
- Event catalog + communication matrix give agents unambiguous cross-module guidance

**Areas for Future Enhancement (Post-MVP):**
- GraalVM native image compilation for scale-to-zero cost optimization
- E2E testing with Playwright/Cypress
- WebSocket/SSE for real-time health dashboard updates
- LLM-assisted scraper repair triage
- Batch-first Accountant workflow (CSV upload → portfolio screening)
- Mobile PWA with offline capability

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use the `screening` module as the reference implementation for all other modules
- Respect module boundaries — facade calls for data, events for notifications
- Use the Frontend Implementation Checklist (8 steps) for every UI feature
- Run `./gradlew check` before committing (pre-commit hook enforces subset)
- Never hardcode user-facing strings — use i18n keys
- Never hardcode business constants — use `risk-guard-tokens.json`
- Never log domain objects — use `@LogSafe` types only

**First Implementation Priority:**
```bash
curl https://start.spring.io/starter.tgz \
  -d type=gradle-project \
  -d language=java \
  -d bootVersion=4.0.3 \
  -d groupId=hu.riskguard \
  -d artifactId=risk-guard \
  -d name=risk-guard \
  -d description="Partner risk screening and EPR compliance platform for Hungarian SMEs" \
  -d packageName=hu.riskguard \
  -d javaVersion=25 \
  -d dependencies=modulith,data-jpa,flyway,oauth2-client,oauth2-resource-server,actuator,web,validation,mail,docker-compose,devtools,configuration-processor,native \
  | tar -xzf -
```
Then add manually: jOOQ, Resilience4j (`resilience4j-spring-boot3`), JSoup, Spring AI BOM, Resend SDK, Bucket4j, Caffeine.

**Next Workflow Step:** Step 8 (Architecture Completion) — pending for next session.
