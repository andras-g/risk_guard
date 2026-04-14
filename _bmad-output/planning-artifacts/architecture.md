---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
status: 'complete'
completedAt: '2026-03-05'
correctedAt: '2026-04-14'
corrections:
  - applied: 'sprint-change-proposal-2026-03-12 (CP-1, CP-2, CP-3)'
    ref: '_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md'
    date: '2026-03-13'
  - applied: 'sprint-change-proposal-2026-04-14 (CP-5) — Epic 9 Product Registry'
    ref: '_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md'
    date: '2026-04-14'
    addendum: '## Epic 9 Addendum — Product Registry Data Model & Pluggable Report Target (CP-5)'
    relatedAdrs:
      - 'docs/architecture/adrs/ADR-0001-ai-kf-classification.md'
      - 'docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md'
inputDocuments:
  - "_bmad-output/planning-artifacts/prd.md"
  - "_bmad-output/planning-artifacts/product-brief-risk_guard-2026-03-04.md"
  - "_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md"
  - "_bmad-output/planning-artifacts/research/market-Hungarian-SME-behavior-and-red-flag-response-research-2026-03-04.md"
  - "_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md"
  - "_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md"
  - "_bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-14.md"
  - "partnerRadar.md"
workflowType: 'architecture'
project_name: 'risk_guard'
user_name: 'Andras'
date: '2026-03-04'
---

> **CORRECTION APPLIED (2026-03-13)**
>
> This document was updated on 2026-03-13 to integrate the course correction approved on 2026-03-12. Key changes: scraping/Playwright architecture removed, `scraping` module renamed to `datasource`, NAV Online Számla integration layer added, demo mode formalized, EPR module updated with NAV invoice data dependency.
>
> **Reference:** `_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md`

> **ADDENDUM APPLIED (2026-04-14) — CP-5 Epic 9**
>
> A new top-level section (`## Epic 9 Addendum — Product Registry Data Model & Pluggable Report Target`) is appended at the end of this document with the data model for `products`, `product_packaging_components`, `registry_entry_audit_log`, ERD relationships to `tenants`, and the PPWR-decoupling note. The earlier sections of this document are unchanged.
>
> The addendum corrects two framings carried in earlier text and in CP-5 itself:
> 1. **The EPR report target is OKIRkapu (national authority portal), not MOHU.** MOHU only invoices.
> 2. **The submission format is XML against the `KG:KGYF-NÉ` XSD, not CSV.** Source: `_bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-14.md`.
>
> The two ADRs landed alongside this addendum capture the decisions in detail:
> - [ADR-0001 — AI-assisted KF-code classification](../../docs/architecture/adrs/ADR-0001-ai-kf-classification.md)
> - [ADR-0002 — Pluggable EPR report target](../../docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md)
>
> **Reference:** `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md`

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
12 FRs spanning 4 domains: Partner Screening (FR1-FR4: tax number search, NAV Online Számla QueryTaxpayer + demo data retrieval, state-machine verdicts, suspended tax detection), Monitoring & Alerts (FR5-FR7: watchlist CRUD, 24h status checks, Resend email triggers), EPR Compliance (FR8-FR10: multi-step questionnaire, JSON DAG validation, schema-perfect exports), and Administration (FR11-FR12: data source health dashboard, hot-swappable EPR JSON config).

**Non-Functional Requirements:**
7 NFRs driving architecture: < 30s verdict latency (NFR1), GraalVM native images < 200ms startup (NFR2), min-instances during business hours (NFR3), SHA-256 cryptographic hashing for due diligence (NFR4), AES-256/TLS 1.3 encryption (NFR5), SEO gateway stubs with JSON-LD (NFR6), and scale-to-zero off-peak (NFR7).

**Scale & Complexity:**

- Primary domain: Full-stack B2B SaaS (Java backend-heavy)
- Complexity level: High
- Estimated architectural components: 5 Spring Modulith modules (`screening`, `epr`, `datasource`, `notification`, `identity`)

### Technical Constraints & Dependencies

- **Solo developer maintenance** (< 10 hrs/week) — drives modular monolith over microservices
- **JVM for MVP, native-image-ready design** — avoid reflection/dynamic proxies; GraalVM native compilation deferred to post-MVP cost optimization
- **NAV API access dependency** — NAV Online Számla available immediately (technical user credentials); NAV M2M Adózó API requires representation registration (1-2 months via accountant). Demo mode bridges the gap.
- **EU data residency** — GCP Frankfurt or Warsaw regions only
- **MOHU API unavailability** — EPR fee tables must be manually maintained from published legislation until API access is granted
- **Single deployment model** — API (Cloud Run) only. No scraper worker or Chrome sidecar — all data sources are API-based or demo fixtures.

### Cross-Cutting Concerns Identified

- **Tenant Isolation:** `tenant_id` enforced at the Spring Data repository layer via `TenantFilter` reading from `SecurityContextHolder`. Database-level `NOT NULL` constraint on `tenant_id`. Guests receive synthetic tenant IDs (`guest-{session_uuid}`). No null tenant_id anywhere in the system.
- **Audit Trail:** Every search produces a SHA-256 hashed, timestamped record with source URLs. Disclaimer text included in hash.
- **Data Freshness (Tiered Model):** < 6h: serve cached ("Verified today"); 6-24h: auto-trigger background re-fetch from data source, serve cached with "Refreshing..." indicator; 24-48h: "Stale" warning badge, block "Reliable" verdict → "Review Recommended"; > 48h: hard shift to "Unavailable."
- **Graceful Degradation:** API outages (NAV Online Számla, future NAV M2M) surface as global health banner + per-verdict timestamps. "Alert me when source returns" queues a retry. Degraded mode serves partial verdicts with explicit "Unavailable" fields. In demo mode, degradation does not apply — demo data is always available.
- **Feature Flags (Simplified):** Tier-based enum (`ALAP`/`PRO`/`PRO_EPR`) with `@TierRequired` annotation on controller methods. Tier embedded in JWT claims and verified server-side.
- **Data Source Mode:** `riskguard.data-source.mode=demo|test|live` controls which adapters are active. `demo`: in-app fixtures, no network. `test`: NAV test environment (`api-test.onlineszamla.nav.gov.hu`). `live`: production NAV APIs.
- **Observability:** Data source success rates, search latency, error budgets, and per-adapter health exposed via admin dashboard. In demo mode, health is always HEALTHY.
- **Per-Tenant Cache with Response Jitter:** Cache is per-tenant (Tenant A's cache does not warm Tenant B's). 50-200ms random jitter on all search responses to prevent timing oracle attacks.

### Architecture Decision Records

#### ADR-1: Application Architecture — Spring Modulith (Modular Monolith)

**Decision:** Spring Modulith with 5 modules (`screening`, `epr`, `datasource`, `notification`, `identity`) as a single deployable.

**Rationale:** Solo developer maintainability. Enforced module boundaries with the operational simplicity of a single deployable. One CI/CD pipeline, one database connection pool, shared transaction context. Module boundaries make future extraction clean if scale demands it. Notification stays in-process — 100 users × 10 watchlist entries = trivial load for `@Scheduled`. All data sources are API-based (NAV Online Számla, future NAV M2M) or demo fixtures — no scraper worker or Chrome sidecar needed.

**Rejected:** Microservices (operational complexity), selective decomposition with Pub/Sub (premature optimization), separate scraper worker (eliminated — no scraping, all data from APIs).

#### ADR-2: Persistence — PostgreSQL 17 Only

**Decision:** Single PostgreSQL 17 database (Cloud SQL) for all data patterns.

**Rationale:** JSONB for snapshots, relational for domain entities, BRIN indexes for time-series queries, dedicated `guest_sessions` table with scheduled purge for transient data. One backup strategy, one connection pool, one migration tool (Flyway). Adding Redis/Elasticsearch doesn't pay for itself below 1000+ concurrent users.

**Rejected:** PostgreSQL + Redis (operational overhead for 10 guest rows), polyglot persistence (massive burden for solo dev).

#### ADR-3: GraalVM Strategy — JVM for MVP, Native-Image-Ready Design

**Decision:** Ship MVP on JVM with Min-Instances: 1. Design all code to be native-image-compatible. Add GraalVM native compilation as post-MVP cost optimization.

**Rationale:** Cold start is irrelevant with Min-Instances: 1 during business hours. GraalVM native images are a cost optimization (scale-to-zero off-peak), not a functional requirement. Avoiding AOT compilation pain during 4-week sprint maximizes development velocity. Architectural discipline (no runtime reflection, no dynamic proxies) ensures easy switch later.

**Rejected:** GraalVM native from day 1 (build time overhead, library risk), JVM-only with no native plan (locked out of future cost savings).

#### ADR-4: Data Source Architecture — Hexagonal Ports & Adapters with Parallel Virtual Threads

> **Corrected 2026-03-13.** Original ADR assumed JSoup/Playwright scraping of government portals. Research (2026-03-10, 2026-03-12) proved all government portals are bot-protected (reCAPTCHA, Cloudflare Turnstile). Owner decision: no CAPTCHA bypass. Adapters are now NAV Online Számla API client (XML/JAXB) and demo fixtures. Hexagonal pattern and Virtual Thread orchestration remain valid.

**Decision:** Each data source implements a `CompanyDataPort` interface with `fetch()`, `requiredFields()`, `health()`, and `validateCanary()`. `CompanyDataAggregator` orchestrates all adapters in parallel using Java 25 Virtual Threads (`StructuredTaskScope`). Each adapter has its own Resilience4j circuit breaker. Data source mode (`riskguard.data-source.mode=demo|test|live`) selects which adapter implementations are active.

**Adapters:**

| Adapter | Mode | Protocol | Data Provided |
|---------|------|----------|---------------|
| `DemoCompanyDataAdapter` | `demo` | In-memory fixtures | Realistic Hungarian company data (debt, insolvency, tax status) for demos and development |
| `NavOnlineSzamlaAdapter` | `test`, `live` | XML/HTTPS (JAXB) | `QueryTaxpayer`: partner name, address, incorporation type, VAT group status |
| `NavM2mAdapter` (deferred) | `live` | REST/JSON | `KoztartozasEgyenleg`: public debt balance, `HianyzoBevallas`: missing filings |

**Rationale:** 30s latency budget requires parallel execution (latency = slowest adapter, not sum). Canary validation with known-good tax numbers detects API contract breakage proactively. Independent circuit breakers prevent one NAV service failure from affecting another. Failed adapters return explicit `SourceUnavailable` results for deterministic verdict handling. Demo mode provides a fully functional product for sales demos and development without any external dependencies.

**Rejected:** Government portal scraping (all portals bot-protected, owner decision: no bypass), sequential adapters (breaks latency budget), async with callbacks (harder to reason about).

#### ADR-5: Authentication — OAuth2 SSO + Dual-Claim JWT

**Decision:** Spring Security 6 OAuth2 Login (Google + Microsoft Entra ID) with email/password fallback. Stateless JWTs with `home_tenant_id` and `active_tenant_id` claims. Accountant context-switch issues a fresh short-lived JWT validated against `tenant_mandates` table.

**Rationale:** Stateless sessions are Cloud Run friendly. Dual-claim JWT enables fast Accountant context-switching without server-side sessions. `TenantFilter` at repository layer always reads `active_tenant_id` from security context. Every context switch is audit-logged.

**Rejected:** Single tenant claim JWT (context switch requires full re-auth), server-side sessions (breaks Cloud Run statelessness).

#### ADR-6: NAV Online Számla Integration — XML/JAXB with SHA-512/SHA3-512 Request Signing

> **Added 2026-03-13** as part of correct course integration (CP-3).

**Decision:** Integrate with NAV Online Számla API v3 (`api.onlineszamla.nav.gov.hu/invoiceService/v3/`) using JAXB-generated classes from official NAV XSD schemas, with a dedicated `NavOnlineSzamlaClient` Spring service encapsulating authentication, request signing, and XML marshalling.

**Authentication Model:** No OAuth. User provides 4 credentials from the NAV Online Számla portal (technical user):

| Credential | Storage | Used For |
|-----------|---------|----------|
| `login` (technical user name) | AES-256 encrypted in DB | All API requests |
| `password` | SHA-512 hashed → sent as `passwordHash` | All API requests |
| `signing key` (cserekulcs) | AES-256 encrypted in DB, never transmitted | Computing SHA3-512 `requestSignature` per request |
| `tax number` (adószám) | Plain text | Identifies the company |

**Request Signing:** Every request requires `SHA3-512(requestId + timestamp + signingKey + <operation-specific-data>)`. The `software` block identifies RiskGuard as the calling application (required by NAV).

**Operations Used:**

| Operation | Use Case | Module |
|-----------|----------|--------|
| `QueryTaxpayer` | Verify partner tax number → name, address, incorporation type | `screening` (via `datasource`) |
| `QueryInvoiceDigest` | List invoices by date range for EPR quarterly aggregation | `epr` (via `datasource`) |
| `QueryInvoiceData` | Full invoice with line items (product descriptions, VTSZ codes, quantities) | `epr` (via `datasource`) |

**Three-Layer Mode Switching:**

| Mode | Behavior | When |
|------|----------|------|
| `mock` | Returns realistic Hungarian invoice/company data from in-app Java fixtures. No network, no XML. | Development, demos, UI work |
| `test` | Calls `api-test.onlineszamla.nav.gov.hu` with real test credentials | Integration testing |
| `live` | Calls `api.onlineszamla.nav.gov.hu` with production credentials | Production |

**Implementation Architecture:**
```
NavOnlineSzamlaClient (Spring @Service)
├── AuthService          — credential management, SHA-512 password hash
├── SignatureService      — SHA3-512 request signature computation
├── XmlMarshaller        — JAXB from NAV XSD schemas (invoiceApi, invoiceData, invoiceBase)
└── Operations:
    ├── queryTaxpayer(taxNumber) → TaxpayerInfo
    ├── queryInvoiceDigest(dateRange, direction) → [InvoiceSummary]
    └── queryInvoiceData(invoiceNumber) → InvoiceDetail
```

**Rationale:** NAV Online Számla is the single most valuable integration because it serves both product modules simultaneously. `QueryTaxpayer` provides real partner verification (not representation-bound — any technical user can query any tax number). `QueryInvoiceDigest` + `QueryInvoiceData` provide full invoice data for EPR calculations. When an accountant connects their NAV credentials, both screening and EPR modules activate with real data.

**Rejected:** Direct HTTP + manual XML parsing (error-prone, ignores NAV's XSD contract), JSON-based approach (NAV API is XML-only), scraping NAV portal (bot-protected, owner decision: no bypass).

#### ADR-7: NAV M2M Adózó API — Deferred Production Data Path

> **Added 2026-03-13** as part of correct course integration (CP-1).

**Decision:** Defer NAV M2M REST API integration (`m2m.nav.gov.hu/rest-api/1.0/`) to post-MVP. When available, it provides `KoztartozasEgyenleg` (public debt balance) and `HianyzoBevallas` (missing filings) — the core tax compliance signals for screening.

**Critical Constraint:** The M2M Adózó API is representation-bound — you can only query companies you legally represent (via törvényes képviselő or EGYKE meghatalmazás). An accountant with representation for many companies is the ideal first customer AND the data access channel.

**Auth Model (for future implementation):** API Key activation → phantom token → signing key → bearer token + signature. Registration via NAV Ügyfélportál (1-2 months via accountant's company).

**Rationale for Deferral:** No credentials available today. Registration takes 1-2 months. Demo-first MVP proves product value to an accountant, who then provides both market validation and legitimate data access. Once registered, `NavM2mAdapter` replaces demo data for tax compliance signals.

### Module-Level Failure Mode Analysis

#### `datasource` Module
- **NAV API downtime:** Per-adapter Resilience4j circuit breaker, global health banner, "Alert me when source returns" queues retry. In demo mode, this cannot occur — demo data is always available.
- **NAV XSD schema changes:** JAXB-generated classes are pinned to a specific schema version. Canary validation with known-good tax numbers detects response structure drift. Schema version mismatch → circuit break + admin alert.
- **Token/credential expiry:** NAV Online Számla technical user password or signing key rotation. `AuthService` detects `INVALID_SECURITY_CONTENT` error → surfaces "Credentials expired" in admin dashboard with re-entry prompt.
- **Rate limiting:** NAV APIs have undocumented rate limits. Resilience4j rate limiter per adapter with conservative defaults. Exponential backoff on HTTP 429 / throttling responses.
- **XML parse exceptions:** JAXB unmarshalling failure → defensive fallback to raw XML string storage in JSONB. Flag record as "Parse Error" for manual review.
- **Request signing errors:** SHA3-512 signature computation failure (corrupted signing key, encoding mismatch) → immediate circuit break for that adapter, admin alert. Canary validation catches this proactively.
- **Demo mode data staleness:** Demo fixtures are static. Not a failure mode per se, but admin dashboard clearly labels data source as "Demo" to prevent confusion.

#### `screening` Module
- **Logic bug (wrong verdict):** Immutable regression suite of 50+ golden-case snapshots. CI blocks deployment on failure. State machine is a pure function.
- **Partial data verdict:** Mandatory source checklist. Missing NAV data = verdict CANNOT be "Reliable" → must be "Incomplete" or "Manual Review Required." No silent gaps.
- **Suspended tax number:** `TAX_SUSPENDED` is a first-class state-machine state, equivalent to "At-Risk" with distinct "Manual Review Required" badge.
- **Race conditions:** Idempotency guard — check for fresh snapshot (< 15 min) before fetching from data sources. Database advisory lock on tax_number during active fetch.

#### `epr` Module
- **Legislation changes:** JSON-driven versioned configuration. Admin updates without code redeploy. Each export stamps config version used.
- **MOHU schema drift:** Schema version gate — if schema is unverified, export button is disabled. MOHU schema stored as JSON schema in database.
- **DAG logic errors:** Each decision tree path is a test case. User traversal path stored alongside result for auditability.
- **Encoding issues:** UTF-8 BOM for Excel compatibility, semicolon delimiters (Hungarian locale), automated round-trip test.
- **EPR golden test cases:** 5-10 known-correct calculations validated nightly against current config. Unexpected output changes block the wizard.
- **NAV Online Számla invoice data unavailable:** EPR module depends on `QueryInvoiceDigest`/`QueryInvoiceData` for automatic invoice aggregation. If NAV API is down or credentials missing → EPR wizard falls back to manual data entry mode with clear "Automatic invoice import unavailable" notice. In demo mode, mock invoice fixtures provide full EPR flow.

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
| NAV API fails → Verdict serves stale data | Tiered freshness model. Stale data = "Incomplete" verdict, never "Reliable." In demo mode, data is always fresh (static fixtures). |
| NAV API fails → Watchlist diff misses change | Watchlist monitor logs "data source unavailable" as explicit event. Triggers "monitoring interrupted" notification. |
| NAV credentials expire → All API adapters fail | `AuthService` detects auth errors, surfaces credential re-entry prompt in admin dashboard. Demo mode unaffected. |
| NAV API fails → EPR invoice import unavailable | EPR wizard falls back to manual data entry mode. Clear "automatic import unavailable" notice. |
| Identity breach → Tenant leak | Defense in depth: repository-layer filtering is the last line even if session is compromised. |
| EPR config update → Old exports invalid | Versioned configs. Old exports retain config version reference. Admin alert on new version. |

### Pre-mortem Risk Mitigations

#### Risk 1: Data Source Availability (Priority: 🟡 Medium — reduced from 🔴 Critical)

> **Corrected 2026-03-13.** Original risk assumed scraping government portals with constant DOM breakage. With API-based data sources, the risk profile shifts from "continuous repair treadmill" to "dependency on NAV API registration and stability."

- **NAV M2M Registration Delay:** Production tax compliance data requires M2M API registration via an accountant's company (1-2 months). Demo mode bridges the gap completely — the product is fully functional for demos and development without real data.
- **NAV Online Számla Availability:** QueryTaxpayer is available immediately to any technical user. API uptime is historically excellent (government SLA). Circuit breakers + health banner handle transient outages.
- **NAV API Versioning/Deprecation:** NAV publishes XSD schema updates. JAXB-generated classes are pinned to a version. Canary validation detects contract drift. Schema change → admin alert, regenerate JAXB classes, test, deploy.
- **Degraded Mode Architecture:** Define "minimum viable verdict" — partial data renders partial verdict with explicit gaps. In demo mode, degradation does not apply.

#### Risk 2: Accountant Persona Mismatch (Priority: 🟡 Medium)
- **Validate workflows before building:** Paper prototype with 2-3 real bookkeepers before full implementation.
- **Batch-First Design:** CSV upload → batch screening → aggregate report as PRIMARY flow. Context-switching is secondary drill-down.
- **Feature Spike Protocol:** Thin vertical spike (API + minimal UI) validated with real user before full investment.

#### Risk 3: Conversion Failure (Priority: 🟡 Medium)
- **Multi-signal rate limiting:** IP + browser fingerprint + tax number query history. Same tax_number queried 3× in 24h across ANY session = require auth.
- **Teaser Verdict Architecture:** Guest searches return partial verdict ("Risk signals detected: 2. Sign up to see full report."). `verdict_visibility` field in API response controlled by tier.
- **VerdictProjection Pattern:** One state machine, multiple views. Full details for PRO, summary for ALAP, teaser for GUEST.

#### Risk 4: EPR Staleness (Priority: 🔴 Critical)
- **Regulatory Monitor:** Monitor MOHU announcements + Magyar Közlöny for EPR-related keywords (manual review or RSS). Alert admin on any content change.
- **EPR Golden Test Cases:** 5-10 known-correct calculations validated nightly. Unexpected changes block EPR wizard.
- **User Upload Feedback Loop:** Post-upload prompt: "Did your MOHU upload succeed?" Track success rates. Spike in "No" triggers config review.

#### Risk 5: Solo Developer Bus Factor (Priority: 🔴 Critical)
- **Auto-Degradation Protocol:** Canary fail + no admin response in 4h → auto circuit-break adapter, update health banner, send bulk notification to affected watchlist users.
- **Outbox Backpressure:** Max queue depth per tenant with auto-digest fallback.
- **Automated Status Page:** Static page (GitHub Pages/Cloudflare Pages) updated by health check job. Users see status even if main API is down.
- **Operational Runbooks:** Every procedure (NAV credential rotation, EPR config update, deployment, incident response) documented as a runbook in the repo.

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
  - `hu.riskguard.epr` — EPR compliance wizard, NAV invoice aggregation, MOHU exports
  - `hu.riskguard.datasource` — Data source adapters (NAV Online Számla XML/JAXB, demo fixtures), health tracking
  - `hu.riskguard.notification` — Watchlist monitoring, Resend email alerts
  - `hu.riskguard.identity` — Auth, RBAC, tenant isolation, tier management

**Development Experience:**
- DevTools hot reload
- Docker Compose support for local PostgreSQL
- Configuration processor for IDE autocompletion on custom properties

**Dependencies Added Manually (Not in Initializr):**
- JAXB Runtime + Maven JAXB plugin (XSD code generation from NAV Online Számla schemas — 5 XSD files)
- Tabula-java / Apache POI (PDF/Excel parsing for EPR fee tables)
- Spring AI BOM (OpenAI o3/GPT-5 integration)
- Resend Java SDK (email delivery)
- Bouncy Castle (SHA3-512 for NAV request signing — JDK may not include SHA3 provider)
- WireMock (test dependency — NAV Online Számla API simulator for integration tests)

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
- `datasource`: `adapter_health`, `canary_companies`, `nav_credentials`
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
| Need data from another module (request/response) | **Facade call** via `@Service` | `dataSourceService.fetchCompanyData(taxNumber)` |
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
- Exception hierarchy: `RiskGuardException` (base) → `NotFoundException`, `ValidationException`, `DataSourceException`, `TenantAccessDeniedException`.

**Frontend:**
- Unified `useApiError` composable handles error responses.
- Maps RFC 7807 `type` field to i18n key: `urn:riskguard:error:tax-number-invalid` → `common.errors.taxNumberInvalid`.
- Displays PrimeVue Toast notification in user's locale.

### Process Patterns

**Loading States:**
- Use Nuxt `useFetch` `pending` ref with PrimeVue `Skeleton` components.
- No global loading state — each data fetch has its own `pending`.

**Retry & Resilience:**
- Backend: Resilience4j circuit breakers and retries (configured per data source adapter).
- Frontend: `ofetch` (via Nuxt `$fetch`) retry configuration for network errors.
- Data source health: Resilience4j Actuator endpoints (`/actuator/circuitbreakers`) polled by admin dashboard.

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
│       │   │   │   │   ├── DataSourceException.java
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
│       │   │   │   │   ├── CompanyDataAggregator.java         # Orchestrates parallel data source fetching via Virtual Threads
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
│       │   │   ├── datasource/                # MODULE: Data Source Engine (FR1-FR2, FR11)
│       │   │   │   ├── api/
│       │   │   │   │   ├── DataSourceAdminController.java     # Admin health dashboard API
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── AdapterHealthResponse.java
│       │   │   │   │       ├── CanaryStatusResponse.java
│       │   │   │   │       └── NavCredentialRequest.java      # NAV Online Számla credential entry
│       │   │   │   ├── domain/
│       │   │   │   │   ├── DataSourceService.java             # Module FACADE @Service
│       │   │   │   │   ├── CompanyDataPort.java               # PORT INTERFACE
│       │   │   │   │   ├── CanaryValidator.java               # Canary tax number verification
│       │   │   │   │   └── events/
│       │   │   │   │       ├── AdapterHealthChanged.java
│       │   │   │   │       └── CanaryValidationFailed.java
│       │   │   │   └── internal/
│       │   │   │       ├── adapters/
│       │   │   │       │   ├── demo/
│       │   │   │       │   │   └── DemoCompanyDataAdapter.java    # In-memory fixtures — realistic Hungarian company data
│       │   │   │       │   └── nav/
│       │   │   │       │       ├── NavOnlineSzamlaClient.java     # Spring @Service — XML/HTTPS client for NAV API v3
│       │   │   │       │       ├── NavOnlineSzamlaAdapter.java    # CompanyDataPort impl — QueryTaxpayer for screening
│       │   │   │       │       ├── NavInvoiceAdapter.java         # Invoice queries for EPR (QueryInvoiceDigest/Data)
│       │   │   │       │       ├── AuthService.java               # Credential management, SHA-512 password hash
│       │   │   │       │       ├── SignatureService.java          # SHA3-512 request signature computation
│       │   │   │       │       └── XmlMarshaller.java             # JAXB marshal/unmarshal from NAV XSD schemas
│       │   │   │       ├── DataSourceRepository.java          # jOOQ (tables: adapter_health, canary_companies, nav_credentials)
│       │   │   │       └── generated/                         # JAXB-generated classes from NAV XSD (build-time, not edited)
│       │   │   │           └── hu.riskguard.datasource.internal.generated.nav/
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
│       │           ├── V20260304_003__create_datasource_tables.sql
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
│               ├── datasource/
│               │   ├── NavOnlineSzamlaClientTest.java         # XML marshalling, request signing
│               │   ├── NavOnlineSzamlaAdapterTest.java        # QueryTaxpayer integration (WireMock)
│               │   ├── DemoCompanyDataAdapterTest.java         # Demo fixture completeness
│               │   ├── SignatureServiceTest.java               # SHA-512/SHA3-512 computation
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
│   │       ├── DataSourceHealthDashboard.vue   # Adapter status + charts (NAV API health, demo mode indicator)
│   │       ├── DataSourceHealthDashboard.spec.ts
│   │       ├── NavCredentialManager.vue        # NAV Online Számla credential entry/rotation
│   │       ├── NavCredentialManager.spec.ts
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
│   │   │   ├── datasources.vue                # Data source health dashboard + NAV credential management
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
│   │   ├── nav-credential-rotation.md         # How to rotate NAV Online Számla technical user credentials
│   │   ├── nav-schema-update.md               # How to regenerate JAXB classes after NAV XSD update
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
| FR2: NAV Data Retrieval | `datasource` | `NavOnlineSzamlaAdapter` (QueryTaxpayer), `DemoCompanyDataAdapter` | (backend only) |
| FR3: State-Machine Verdict | `screening` | `VerdictEngine`, `VerdictResponse` | `VerdictCard.vue` |
| FR4: Suspended Tax Detection | `screening` | `VerdictEngine` (TAX_SUSPENDED state) | `VerdictCard.vue` (badge) |
| FR5: Watchlist CRUD | `notification` | `WatchlistController`, DTOs | `WatchlistTable.vue`, `pages/watchlist/` |
| FR6: 24h Status Checks | `notification` | `WatchlistMonitor` (@Scheduled) | (results in WatchlistTable) |
| FR7: Email Alerts | `notification` | `OutboxProcessor`, `ResendEmailSender` | `AlertHistoryList.vue` |
| FR8: EPR Questionnaire | `epr` | `EprController`, `DagEngine` | `WizardStepper.vue`, `MaterialSelector.vue` |
| FR9: Fee Calculation | `epr` | `FeeCalculator`, `NavInvoiceAdapter` (QueryInvoiceDigest/Data) | `WizardStepper.vue` (results step) |
| FR10: MOHU Export | `epr` | `MohuExporter`, `EprExportResponse` | `ExportButton.vue`, `pages/epr/export/` |
| FR11: Data Source Dashboard | `datasource` | `DataSourceAdminController` | `DataSourceHealthDashboard.vue`, `pages/admin/datasources.vue` |
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
| Data Source Mode | `application.yml` (`riskguard.data-source.mode`) | `stores/health.ts` (mode indicator) |
| NAV Credentials | `datasource/internal/DataSourceRepository` (encrypted) | `NavCredentialManager.vue` |

### External Integration Points

| Integration | Module | Backend File | Protocol |
|---|---|---|---|
| NAV Online Számla API v3 | `datasource` | `NavOnlineSzamlaClient.java` | XML/HTTPS (JAXB), SHA-512/SHA3-512 signed requests |
| NAV M2M Adózó API (deferred) | `datasource` | `NavM2mAdapter.java` (future) | REST/JSON, bearer token + signature |
| Demo Data Fixtures | `datasource` | `DemoCompanyDataAdapter.java` | In-memory (no network) |
| MOHU Portal | `epr` | `MohuExporter.java` | CSV/XLSX file generation |
| Google SSO | `identity` | `SecurityConfig.java` | OAuth2/OIDC |
| Microsoft Entra ID | `identity` | `SecurityConfig.java` | OAuth2/OIDC |
| Resend Email API | `notification` | `ResendEmailSender.java` | REST API |
| OpenAI (Spring AI) | `screening` | `ScreeningService.java` | REST API (async enrichment) |
| GCP Cloud Run | infrastructure | `Dockerfile`, `deploy.yml` | Container |

### Data Flow

```
User → Nuxt Frontend (SPA)
         → $fetch(/api/v1/...) → Spring Boot API (Cloud Run)
                                    → ScreeningService (facade)
                                        → CompanyDataAggregator
                                            → [Virtual Threads]
                                                → NavOnlineSzamlaAdapter (XML/JAXB — QueryTaxpayer)
                                                → DemoCompanyDataAdapter (in-memory fixtures)
                                                → [future: NavM2mAdapter (REST/JSON)]
                                            → mode switch: demo|test|live selects active adapters
                                        → VerdictEngine (state machine)
                                        → ScreeningRepository (jOOQ → PostgreSQL)
                                    → publishes PartnerStatusChanged event
                                        → NotificationService listens
                                            → OutboxProcessor → Resend API
         ← VerdictResponse (JSON) ←

EPR Invoice Flow (parallel path):
         → EprService (facade)
             → DataSourceService.queryInvoices(dateRange)
                 → NavOnlineSzamlaClient (QueryInvoiceDigest → QueryInvoiceData)
             → FeeCalculator (VTSZ codes → EPR category → fee)
             → MohuExporter (CSV/XLSX)
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

## Architecture Validation Results (Step 7)

### Coherence Validation ✅

**Decision Compatibility:**
All technology choices verified compatible. Spring Boot 4.0.3 + Spring Modulith 2.0.3 + jOOQ OSS + Nuxt 3 + PrimeVue 4 form a coherent stack. One dependency swap applied: use standalone `io.github.resilience4j:resilience4j-spring-boot3` instead of `cloud-resilience4j` (Spring Cloud compatibility concern with Boot 4.0.3).

**Pattern Consistency:**
All patterns support architectural decisions. Naming chain is clean: DB (snake_case) → jOOQ codegen (camelCase) → DTO (camelCase) → JSON (camelCase). i18n boundary is crisp: frontend owns all user-facing text, backend owns export/email text. Error handling pipeline is end-to-end: RFC 7807 codes → `useApiError` → i18n Toast.

**Structure Alignment:**
Monorepo structure (`backend/` + `frontend/`) supports atomic PRs and shared `risk-guard-tokens.json`. Module-per-package with scoped jOOQ codegen enforces physical isolation matching logical isolation. Frontend composables organized by concern (`formatting/`, `api/`, `auth/`).

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:**

| FR | Status | Covered By |
|---|---|---|
| FR1: Tax number search | ✅ | `screening` module, `ScreeningController`, `SearchBar.vue` |
| FR2: NAV Data Retrieval | ✅ | `datasource` module, `CompanyDataPort` adapters (NavOnlineSzamlaAdapter + DemoCompanyDataAdapter), Virtual Thread parallel orchestration |
| FR3: State-machine verdicts | ✅ | `VerdictEngine` (pure function), 50+ golden snapshot regression tests |
| FR4: Suspended tax detection | ✅ | `TAX_SUSPENDED` first-class state in VerdictEngine |
| FR5: Watchlist CRUD | ✅ | `notification` module, `WatchlistController`, `WatchlistTable.vue` |
| FR6: 24h status checks | ✅ | `WatchlistMonitor` (`@Scheduled`), canary validation |
| FR7: Email alerts | ✅ | Outbox pattern, `ResendEmailSender`, digest mode |
| FR8: EPR questionnaire | ✅ | `epr` module, `DagEngine`, `WizardStepper.vue` |
| FR9: Fee calculation | ✅ | `FeeCalculator`, versioned JSON config, EPR golden test cases |
| FR10: MOHU export | ✅ | `MohuExporter`, `@ExportLocale("hu")`, schema version gate |
| FR11: Data Source Dashboard | ✅ | Resilience4j Actuator endpoints, `DataSourceHealthDashboard.vue` |
| FR12: EPR config admin | ✅ | Hot-swappable JSON config, `EprConfigManager.vue` |

**Non-Functional Requirements Coverage:**

| NFR | Status | How |
|---|---|---|
| NFR1: < 30s verdict latency | ✅ | Virtual Thread parallel data source fetching, Caffeine cache, 15-min idempotency guard |
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
- ~110 files defined across backend/frontend ✅
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
| `company_snapshots` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `tax_number` (VARCHAR(11)), `snapshot_data` (JSONB), `source_urls` (JSONB), `data_source_mode` (ENUM: DEMO/TEST/LIVE), `checked_at` (TIMESTAMPTZ), `created_at` | belongs to `tenants` |
| `verdicts` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `snapshot_id` (FK → company_snapshots), `tax_number`, `status` (ENUM: RELIABLE/AT_RISK/INCOMPLETE/TAX_SUSPENDED/UNAVAILABLE), `confidence` (ENUM: FRESH/STALE/UNAVAILABLE), `sha256_hash` (VARCHAR(64)), `disclaimer_text`, `ai_narrative` (TEXT, nullable), `created_at` | belongs to `company_snapshots` |
| `search_audit_log` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `user_id` (FK → users), `tax_number`, `verdict_id` (FK → verdicts), `source_urls` (JSONB), `sha256_hash` (VARCHAR(64)), `searched_at` (TIMESTAMPTZ) | GDPR audit trail |

#### `datasource` Module Tables

| Table | Key Columns | Relationships |
|---|---|---|
| `adapter_health` | `id` (UUID, PK), `adapter_name` (VARCHAR), `status` (ENUM: HEALTHY/DEGRADED/CIRCUIT_OPEN), `last_success_at` (TIMESTAMPTZ), `last_failure_at` (TIMESTAMPTZ), `failure_count` (INT), `mtbf_hours` (DECIMAL) | per-adapter health |
| `canary_companies` | `id` (UUID, PK), `tax_number`, `adapter_name`, `expected_data` (JSONB), `last_validated_at` (TIMESTAMPTZ), `validation_status` (ENUM: PASS/FAIL) | known-good reference |
| `nav_credentials` | `id` (UUID, PK), `tenant_id` (FK → tenants, NOT NULL), `login` (VARCHAR, AES-256 encrypted), `password_hash` (VARCHAR(128), SHA-512), `signing_key` (VARCHAR, AES-256 encrypted), `tax_number` (VARCHAR(11)), `environment` (ENUM: TEST/LIVE), `created_at`, `rotated_at` | NAV Online Számla technical user credentials |

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
- [x] Performance considerations addressed (Virtual Threads, Caffeine, parallel data source fetching)

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
- LLM-assisted NAV API response anomaly detection
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
Then add manually: jOOQ, Resilience4j (`resilience4j-spring-boot3`), JAXB Runtime + Maven JAXB plugin, Bouncy Castle (SHA3-512), Spring AI BOM, Resend SDK, Bucket4j, Caffeine, WireMock (test).

## Architecture Completion Summary (Step 8)

### Workflow Completion

**Architecture Decision Workflow:** COMPLETED ✅
**Total Steps Completed:** 8
**Date Completed:** 2026-03-05
**Document Location:** `_bmad-output/planning-artifacts/architecture.md`

### Final Architecture Deliverables

**📋 Complete Architecture Document**
- All architectural decisions documented with specific versions
- Implementation patterns ensuring AI agent consistency
- Complete project structure with all files and directories
- Requirements-to-architecture mapping
- Validation confirming coherence and completeness

**🏗️ Implementation Ready Foundation**
- 11+ architectural decisions made (5 ADRs + 6 core decisions)
- 14+ implementation patterns defined with automated enforcement
- 2 deployable units specified (backend, frontend)
- 12 functional requirements fully supported
- 15 database tables designed with relationships and indexes

**📚 AI Agent Implementation Guide**
- Technology stack: Spring Boot 4.0.3, jOOQ, Nuxt 3, PrimeVue 4, PostgreSQL 17
- Consistency rules: ArchUnit (6 test files), ESLint, 12-step CI pipeline
- Project structure: ~120 files with clear module boundaries
- Communication: facade (sync) + events (async) with event catalog

### Development Sequence

1. Initialize backend using Spring Initializr command (documented above)
2. Initialize frontend with `npx nuxi@latest init frontend`
3. Set up Docker Compose (PostgreSQL) and Flyway migrations
4. Implement `screening` module as reference implementation
5. Validate reference against the Review Checklist
6. Implement remaining modules following the reference patterns
7. Set up CI pipeline (12-step) and pre-commit hooks

---

**Architecture Status:** READY FOR IMPLEMENTATION ✅

**Document Maintenance:** Update this architecture when major technical decisions are made during implementation.

---

## Epic 9 Addendum — Product Registry Data Model & Pluggable Report Target (CP-5)

> **Date:** 2026-04-14
> **Driver:** [Sprint Change Proposal CP-5](sprint-change-proposal-2026-04-14.md), grounded in [80/2023 Annex 3.1, Annex 4.1, Annex 1.2 research](research/okirkapu-and-kf-refresh-2026-04-14.md).
> **Companion ADRs:** [ADR-0001 (AI KF classification)](../../docs/architecture/adrs/ADR-0001-ai-kf-classification.md), [ADR-0002 (Pluggable EPR report target)](../../docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md).
> **Scope:** Additive. The earlier sections of this document remain accurate for everything outside the Product Registry surface.

### Why this section exists

Story 8.3 shipped invoice-driven EPR autofill on the assumption that the producer's invoice line items *are* the packaging materials. That assumption holds for packaging-material distributors (paper-bag vendors, PET sellers) but breaks for the actual ICP — KKV manufacturers and importers whose invoices list *packaged products*. For them, the legally mandated *csomagolási nyilvántartás* (per-product packaging registry under 80/2023) is the missing join table between invoice quantity and EPR-reportable material mass.

This addendum records the data model that supports the registry, the boundaries it imposes on the rest of the EPR module, and the PPWR-readiness invariants that make the schema stable against the 2027–2030 EU regulatory transition.

### Architectural invariants introduced (binding for Epic 9 and beyond)

These are the binding rules from CP-5 §5. ArchUnit enforces (1), (3), (4); code review catches (2), (5).

1. **Packaging is modelled at SKU/component level, never as aggregate tonnage.** Aggregation happens at report-generation time, not at storage time. The `product_packaging_components` row is the unit of record.
2. **KF code is one classifier among many** on a packaging component. Nullable PPWR fields (`recyclability_grade`, `recycled_content_pct`, `reusable`, `substances_of_concern`) exist in schema from day one even though they are not populated today.
3. **The EPR report target is pluggable.** `EprService` depends on the `EprReportTarget` interface only. `OkirkapuXmlExporter` is today's implementation; `EuRegistryAdapter` is the post-2029 placeholder. No service references the OKIRkapu exporter directly. See [ADR-0002](../../docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md).
4. **Fee-modulation rules are data, not code.** Future eco-modulation (mandated PPWR 2030) loads from config / DB, not branched in logic.
5. **Supplier-declaration provenance is captured from day one.** `supplier_declaration_ref` nullable field on components — backfilling chain-of-custody evidence later is prohibitive.

### Data model — three new tables

All three tables are tenant-scoped per the existing multi-tenancy convention (`tenant_id` NOT NULL, enforced by `TenantFilter` at the repository layer; see *Cross-Cutting Concerns Identified* earlier in this document). All new tables are owned by the `epr` Spring Modulith module; physical isolation is enforced by ArchUnit (see *ArchUnit additions* below).

#### `products`

Per-tenant catalogue of SKUs the producer places on the Hungarian market. Identity row for the registry.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID FK → `tenants.id`, NOT NULL | Tenant isolation. |
| `article_number` | VARCHAR(64) | Producer's internal SKU identifier. Unique per tenant (composite unique index on `(tenant_id, article_number)`). |
| `name` | VARCHAR(255) NOT NULL | Human-readable product name. |
| `vtsz` | VARCHAR(8) | Combined Nomenclature code from the producer's invoices (4 or 8 digits). Used by the VTSZ-prefix fallback classifier (see ADR-0001). |
| `primary_unit` | VARCHAR(16) NOT NULL | Unit of sale (`db`, `kg`, `l`, etc.) — must align with the unit on NAV invoice line items so quantity multiplication is correct. |
| `status` | ENUM NOT NULL | `ACTIVE`, `DISCONTINUED`, `DRAFT`. Triage queue products land as `DRAFT`. |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

Indexes:
- `(tenant_id, status)` — registry list filter.
- `(tenant_id, vtsz)` — fallback classification lookup.
- `(tenant_id, article_number)` UNIQUE.

#### `product_packaging_components`

The bill-of-materials. One product has one or more packaging components; each component carries its own KF code, weight, and PPWR-future fields.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `product_id` | UUID FK → `products.id` ON DELETE CASCADE, NOT NULL | |
| `component_order` | INT NOT NULL | 1-based ordering for deterministic UI display. |
| `material_description` | VARCHAR(255) NOT NULL | Free-text human label (e.g., "PP cup, 0.7 kg"). |
| `kf_code` | CHAR(8) NOT NULL | Validated against the seeded KF reference table (see *Reference data* below). |
| `weight_per_unit_kg` | DECIMAL(12, 6) NOT NULL | Per-unit material mass in kilograms. Three+ decimal precision to satisfy LUCID-style 3-decimal aggregation. |
| `recyclability_grade` | CHAR(1) NULL | PPWR field. A/B/C/D when populated. **Nullable from day one — populated when 2030 eco-modulation lands.** |
| `recycled_content_pct` | DECIMAL(5, 2) NULL | PPWR field. Percentage 0–100. Nullable. |
| `reusable` | BOOLEAN NULL | PPWR field. Nullable. |
| `substances_of_concern` | JSONB NULL | PPWR field. Structured list, schema TBD when implementing act lands. Nullable. |
| `supplier_declaration_ref` | VARCHAR(255) NULL | Reference to the supplier's chain-of-custody declaration (file path, document ID, or external URL). Nullable but reserved from day one. |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

Indexes:
- `(product_id, component_order)` UNIQUE — deterministic ordering.
- `(kf_code)` — aggregation grouping for `OkirkapuXmlExporter`.

`tenant_id` is intentionally NOT denormalised onto this table. Reaching `tenant_id` is one join through `products`; this is acceptable given the table sits behind `RegistryRepository` and tenant filtering already lives at the `products` query layer. Revisit only if query-plan analysis on production data shows the join cost is material.

#### `registry_entry_audit_log`

Append-only audit trail. Every field change to a `products` or `product_packaging_components` row produces one row here. Supports MOHU/OKIRkapu audit obligations and the AI-classification provenance requirement from ADR-0001.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID FK → `tenants.id`, NOT NULL | Denormalised here — audit queries filter by tenant first. |
| `product_id` | UUID FK → `products.id` ON DELETE CASCADE, NOT NULL | The product whose registry entry changed. (For `product_packaging_components` changes, this is the parent product's id.) |
| `field_changed` | VARCHAR(64) NOT NULL | Dotted path: `products.name`, `products.vtsz`, `components[2].kf_code`, etc. |
| `old_value` | TEXT NULL | NULL when source = creation. |
| `new_value` | TEXT NULL | NULL when source = deletion. |
| `changed_by_user_id` | UUID FK → `users.id`, NULL | NULL when source is `NAV_BOOTSTRAP` (system-driven). |
| `source` | ENUM NOT NULL | `MANUAL`, `AI_SUGGESTED_CONFIRMED`, `AI_SUGGESTED_EDITED`, `VTSZ_FALLBACK`, `NAV_BOOTSTRAP`. Mirrors the audit categories from ADR-0001. |
| `strategy` | VARCHAR(32) NULL | Populated for AI-sourced rows: `VERTEX_GEMINI`, `VTSZ_PREFIX`, `NONE`. |
| `model_version` | VARCHAR(64) NULL | Populated for `VERTEX_GEMINI` rows. |
| `timestamp` | TIMESTAMPTZ NOT NULL | |

Indexes:
- `(tenant_id, product_id, timestamp DESC)` — audit-trail UI per product.
- `(tenant_id, source, timestamp DESC)` — operational filter ("show me everything Gemini classified this month").

### ERD — relationships to existing tables

```
                    ┌──────────────┐
                    │   tenants    │ (existing)
                    │              │
                    │ id (PK)      │
                    └──────┬───────┘
                           │ 1
                           │
                    ┌──────┴───────┐
                    │              │ M
              ┌─────▼────┐   ┌─────▼─────────────────────┐
              │  users   │   │       products            │
              │ (existing)│  │                            │
              │ id (PK)  │   │ id (PK)                    │
              └─────┬────┘   │ tenant_id (FK)             │
                    │ 1      │ article_number             │
                    │        │ name, vtsz, primary_unit,  │
                    │ M      │ status, created_at, updated_at │
              ┌─────▼─────────┐  └──────┬─────────────────┘
              │ registry_entry │         │ 1
              │ _audit_log    │ M       │
              │               ◄─────────┤ (product_id FK)
              │ id (PK)       │         │
              │ tenant_id     │         │
              │ product_id    │         │ M
              │ field_changed │   ┌─────▼──────────────────────┐
              │ old_value     │   │ product_packaging_         │
              │ new_value     │   │ components                 │
              │ changed_by_   │   │                            │
              │   user_id     │   │ id (PK)                    │
              │ source, strategy,  │ product_id (FK)            │
              │ model_version,│   │ component_order            │
              │ timestamp     │   │ material_description       │
              └───────────────┘   │ kf_code, weight_per_unit_kg│
                                  │ recyclability_grade NULL,  │
                                  │ recycled_content_pct NULL, │
                                  │ reusable NULL,             │
                                  │ substances_of_concern NULL,│
                                  │ supplier_declaration_ref   │
                                  │   NULL                     │
                                  │ created_at, updated_at     │
                                  └────────────────────────────┘
```

Foreign keys at a glance:

- `products.tenant_id → tenants.id` (RESTRICT — never delete a tenant with products)
- `product_packaging_components.product_id → products.id` (CASCADE — delete a product → its components)
- `registry_entry_audit_log.tenant_id → tenants.id` (RESTRICT)
- `registry_entry_audit_log.product_id → products.id` (CASCADE — keep the audit log scoped to existing products; for retention, copy to cold storage before product deletion)
- `registry_entry_audit_log.changed_by_user_id → users.id` (SET NULL — never lose audit history when a user is deactivated)

### Reference data — KF code seed

The `KG:KGYF-NÉ` schema requires KF codes that match the codes hatályos on the date of the report. Per the 2026-04-14 research:

- KF codes are an 8-character string (positions: 1–2 product/waste, 3–4 material stream, 5–6 group, 7 form, 8 waste-management activity).
- The 2026-01-01 refresh (203/2025 amendment) is already in force; product was built after it, so no migration is needed.
- Packaging KF codes are stable in the currently-enacted tranche; battery codes changed.

Seed approach (Story 9.1 task):

- A `kf_codes` reference table seeded from 80/2023 Annex 3/4 consolidated text (via jogtar.hu) or from the OKIRkapu `xmlapi` portal's KG code-list download.
- Schema includes `valid_from` (NOT NULL, default `2026-01-01` for the seed) and `valid_to` (NULL for currently-valid codes). Forward-compat hook for the next amendment; cheap to include now.
- `product_packaging_components.kf_code` validates against `kf_codes` at write time. Validation enforced by jOOQ FK or by a `RegistryService` pre-write check — implementer's choice; ArchUnit does not enforce.

### Module / package structure additions

The registry sub-module sits inside the existing `epr` Spring Modulith module. No new top-level module is created. Package layout (extends the `epr` tree shown earlier in this document):

```
hu.riskguard.epr/
├── api/                                  (existing)
├── domain/                               (existing — EprService facade lives here)
├── internal/                             (existing — EprRepository lives here)
│
├── registry/                             ← NEW
│   ├── api/
│   │   ├── RegistryController.java       # /api/v1/products
│   │   └── dto/
│   │       ├── ProductRequest.java
│   │       ├── ProductResponse.java
│   │       └── PackagingComponentRequest.java
│   ├── domain/
│   │   ├── RegistryService.java          # facade for sub-module
│   │   ├── RegistryBootstrapService.java # NAV-driven triage (Story 9.2)
│   │   └── events/
│   │       └── ProductRegistered.java
│   ├── classifier/                       # ADR-0001 strategy package
│   │   ├── KfCodeClassifierService.java  # interface
│   │   ├── ClassifierRouter.java
│   │   ├── ClassificationResult.java
│   │   ├── KfSuggestion.java
│   │   ├── ClassificationStrategy.java   # enum
│   │   ├── ClassificationConfidence.java # enum
│   │   └── internal/
│   │       ├── VertexAiGeminiClassifier.java
│   │       └── VtszPrefixFallbackClassifier.java
│   └── internal/
│       └── RegistryRepository.java       # jOOQ — products, product_packaging_components, registry_entry_audit_log, kf_codes
│
├── report/                               ← NEW (ADR-0002)
│   ├── EprReportTarget.java              # interface
│   ├── EprReportRequest.java             # record
│   ├── EprReportArtifact.java            # record (filename, contentType, bytes, summaryReport, provenanceLines[])
│   ├── EprReportProvenance.java          # record
│   ├── EprReportFormat.java              # enum: OKIRKAPU_XML | EU_REGISTRY (future)
│   └── internal/
│       ├── OkirkapuXmlExporter.java      # EprReportTarget impl — today's only impl
│       ├── KgKgyfNeAggregator.java       # GROUP BY kf_code, sum kg
│       ├── KgKgyfNeMarshaller.java       # JAXB → KG:KGYF-NÉ XML
│       └── jaxb-generated/               # build-time JAXB classes from XSD
│
└── package-info.java                     (existing)

backend/src/main/resources/
└── schemas/
    ├── nav/                              (existing — NAV Online Számla XSDs)
    └── okirkapu/                         ← NEW
        └── KG-KGYF-NE-vN.xsd             # version-pinned, downloaded from kapu.okir.hu/okirkapuugyfel/xmlapi
```

The previously-existing `hu.riskguard.epr.domain.MohuExporter` is misnamed under the corrected framing (the receiver is OKIRkapu, not MOHU). Story 9.4 either renames it into `report/internal/OkirkapuXmlExporter` or replaces it. Either way, after Story 9.4 lands, no class outside `hu.riskguard.epr.report.internal` references "MOHU" by name in its dependency graph.

### EPR module table-ownership update

The existing ArchUnit rule `epr_module_should_only_access_own_tables` (in `NamingConventionTest.java`) currently allows the `epr` module to access `EprConfigs`, `EprCalculations`, `EprExports`, `EprMaterialTemplates`. **Story 9.1 must extend this allow-list** to add the four new tables: `Products`, `ProductPackagingComponents`, `RegistryEntryAuditLog`, `KfCodes`.

### ArchUnit additions (binding for Epic 9)

Three new ArchUnit rules ship as part of Story 9.1's foundation work. They are documented in detail in the ArchUnit test file (`backend/src/test/java/hu/riskguard/architecture/EpicNineInvariantsTest.java`) and summarised here:

1. **Registry write boundary** (CP-5 §5 invariant 1).
   Only classes in `hu.riskguard.epr.registry..` may write to `product_packaging_components` (i.e., depend on the jOOQ `ProductPackagingComponents` table or `ProductPackagingComponentsRecord`). Other modules read aggregated results through `RegistryService`, never the table directly.
2. **Pluggable report target boundary** (CP-5 §5 invariant 3, ADR-0002).
   No class outside `hu.riskguard.epr.report..` may depend on `OkirkapuXmlExporter` or any other concrete `EprReportTarget` implementation. Callers depend on the `EprReportTarget` interface only.
3. **Fee modulation comes from data, not branches** (CP-5 §5 invariant 4).
   No class in `hu.riskguard.epr..` may use a hard-coded `if` / `switch` branch on a recyclability grade enum (`A`/`B`/`C`/`D`) to compute fees. Eco-modulation rules must come from the EPR config / DB. This is enforced as a class-level rule that flags suspicious patterns; full enforcement requires Story 9.4-era code review since some `switch` statements (e.g., on enum status) are legitimate.

### Reporting flow (end-to-end, post-Epic-9)

```
NAV invoices for period
        │
        ▼
EprReportRequest (tenantId, period)
        │
        ▼
EprService.generateReport(request)
        │
        ├─► For each invoice line item:
        │       │
        │       ├─► RegistryService.findByVtszOrArticleNumber(...)
        │       │       ├─► HIT  → REGISTRY_MATCH provenance, components.kf_code + weight × units
        │       │       └─► MISS → KfCodeClassifierService (ADR-0001):
        │       │                    ├─► Gemini Flash (HIGH/MEDIUM confidence) → suggested but not auto-applied
        │       │                    │   (suggestions surface only in registry editor, not in report path)
        │       │                    └─► VTSZ-prefix fallback → VTSZ_FALLBACK provenance
        │       │                                                 (uses Story 8.3 logic via KfCodeClassifierService)
        │       └─► both miss → UNMATCHED provenance, listed in summary for user action
        │
        ├─► KgKgyfNeAggregator: GROUP BY kf_code, SUM(weight × units) → per-KF-code totals (kg, 3 decimals)
        │
        ├─► KgKgyfNeMarshaller: JAXB → KG:KGYF-NÉ XML
        │
        └─► EprReportArtifact (xml bytes + summaryReport + provenanceLines[])
                    │
                    ▼
            User downloads XML → manually uploads to kapu.okir.hu
                    │
                    ▼
            Authority forwards aggregated data to MOHU by 25th of month after quarter
                    │
                    ▼
            MOHU issues invoice (15-day payment terms)
```

Risk Guard's responsibility ends at "User downloads XML". No automated submission round-trip — OKIRkapu has no programmatic ingestion endpoint per the 2026-04-14 research.

### Cross-references

- For the AI classification path's strategy interface, model choice, guardrails, and validation gate: [ADR-0001](../../docs/architecture/adrs/ADR-0001-ai-kf-classification.md).
- For the report-target abstraction, OKIRkapu/XML correction, and PPWR-readiness rationale: [ADR-0002](../../docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md).
- For epic and story breakdown: [Sprint Change Proposal CP-5](sprint-change-proposal-2026-04-14.md), §4.2 – §4.5.
- For the legal basis (80/2023 Annex 3.1/4.1/1.2) and OKIRkapu submission-format research: [OKIRkapu format + KF refresh research](research/okirkapu-and-kf-refresh-2026-04-14.md).

---

**Addendum status:** READY FOR IMPLEMENTATION ✅ — Story 9.1 owns table creation (Flyway), ArchUnit rule additions, and the `kf_codes` seed. Stories 9.2 / 9.3 / 9.4 build on this foundation per CP-5 §4.
