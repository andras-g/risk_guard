---
stepsCompleted: [1, 2, 3]
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
