---
stepsCompleted: [1, 2, 3, 4]
status: 'corrected'
correctedAt: '2026-03-13'
correctionApplied: 'sprint-change-proposal-2026-03-12 (CP-1, CP-2, CP-3)'
correctionRef: '_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md'
inputDocuments: 
  - "_bmad-output/planning-artifacts/prd.md"
  - "_bmad-output/planning-artifacts/architecture.md"
  - "_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md"
  - "Party Mode Synthesis (March 2026)"
---

> **CORRECTION APPLIED (2026-03-13)**
>
> This document was updated on 2026-03-13 to integrate the course correction approved on 2026-03-12. Key changes: scraping stories removed/deferred, NAV Online Számla integration story added, Story 2.2 rebranded as Demo Mode, Epic 6 reframed from scraper monitoring to data source health.
>
> **Reference:** `_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md`

# risk_guard - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for risk_guard, decomposing the requirements from the PRD, UX Design concepts, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

- **FR1:** Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers.
- **FR2:** System retrieves factual partner data from NAV Online Számla (QueryTaxpayer) and demo fixtures; future: NAV M2M Adózó API for tax compliance signals.
- **FR3:** System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts.
- **FR4:** System flags "Suspended Tax Numbers" for manual review.
- **FR5:** Authenticated Users can manage a private Watchlist of partners.
- **FR6:** System monitors Watchlist partners for status changes every 24 hours.
- **FR7:** System triggers Resend email alerts on partner status deviations.
- **FR8:** Users can navigate a multi-step questionnaire for material classification (EPR Wizard).
- **FR9:** System validates inputs via backend JSON-driven Directed Graph (DAG).
- **FR10:** System generates schema-perfect CSV/XLSX exports for MOHU reporting.
- **FR11:** Administrators can monitor data source health (API connectivity, circuit breaker state, credential status) via a health dashboard.
- **FR12:** Administrators can update EPR logic via JSON configuration without code redeploys.
- **FR13:** Users can save and manage material templates in a personal "EPR Library."
- **FR14:** Users can generate a bulk PDF export of their Watchlist status including SHA-256 verification hashes.
- **FR15:** Guest users can try the product with limited access (10 companies, 3 checks/day) before signing up.
- **FR16:** Feature access is controlled by subscription tier via feature flags, with clear upgrade prompts.

### NonFunctional Requirements

- **NFR1:** 95% of search requests return a verdict in < 30 seconds.
- **NFR2:** GraalVM native images ensure container startup times of < 200ms (Post-MVP optimization).
- **NFR3:** System maintains Min-Instances: 1 on Cloud Run during Hungarian business hours (8:00-17:00 CET).
- **NFR4:** Every risk check is recorded with a SHA-256 Cryptographic Hash for court-ready evidence.
- **NFR5:** Encryption at rest (AES-256) and in transit (TLS 1.3).
- **NFR6:** System generates indexable public "Gateway Stubs" for Hungarian companies using JSON-LD.
- **NFR7:** System scales to zero during off-peak hours (Requires NFR2).

### Additional Requirements (UX & Tech)

- **TECH-1:** Initialize project using Java 25, Spring Boot 4.0.3, Spring Modulith, and jOOQ.
- **TECH-2:** Enforce Multi-tenancy via `TenantFilter` and database-level `tenant_id NOT NULL`.
- **TECH-3:** Use standalone `resilience4j-spring-boot3` for data source adapter circuit breakers.
- **UX-1:** Visual Color Palette: Slate 900 (Nav), Indigo 600 (Primary), Emerald 500 (Reliable), Rose 600 (At-Risk).
- **UX-2:** Implement Skeleton Screen animations showing source resolution (e.g., "[✔] NAV checked").
- **UX-3:** Searchable Context Switcher in top-bar for accountants to jump between client tenants.
- **UX-4:** Provenance Sidebar on verdict pages showing specific data source check timestamps.
- **i18n:** Primary language is Hungarian (hu); secondary fallback is English (en).

### FR Coverage Map

- **FR1:** Epic 2 - Tax Number Search (Story 2.1)
- **FR2:** Epic 2 - Data Source Retrieval (Story 2.2 Demo Mode, Story 2.2.2 Demo Fixtures)
- **FR3:** Epic 2 - State-Machine Verdict (Story 2.3)
- **FR4:** Epic 2 - Suspended Tax Flag (Story 2.3/2.4)
- **FR5:** Epic 3 - Watchlist CRUD (Story 3.6)
- **FR6:** Epic 3 - 24h Monitoring (Story 3.7)
- **FR7:** Epic 3 - Email Alerts (Story 3.8)
- **FR8:** Epic 4 - Material Questionnaire (Story 4.2)
- **FR9:** Epic 4 - DAG Validation (Story 4.2)
- **FR10:** Epic 5 - MOHU CSV Export (Story 5.3)
- **FR11:** Epic 6 - Data Source Health Dashboard (Story 6.1)
- **FR12:** Epic 6 - JSON Logic Hot-swap (Story 6.3)
- **FR13:** Epic 4 - Material Library (Story 4.1)
- **FR14:** Epic 5 - Watchlist PDF Export (Story 5.1)
- **FR15 (new):** Demo Mode - Guest Rate Limiting (Story 3.12)
- **FR16 (new):** Feature Flags - Tier Gating (Story 3.3)

### NFR Coverage Map

- **NFR1:** Epic 2 - Search < 30 seconds (Story 2.2)
- **NFR3:** Epic 3 - Min-Instances during business hours (Story 3.4)
- **NFR4:** Epic 2 - SHA-256 Audit Logging (Story 2.5)
- **NFR5:** Epic 3 - Encryption at rest and in transit (Story 3.4)
- **NFR6:** Epic 3 - SEO Gateway Stubs (Story 3.11)

### Additional Requirement Coverage Map

- **i18n:** Epic 3 - Internationalization Infrastructure (Story 3.1)
- **Async Ingestor:** Epic 3 - Background Data Freshness (Story 3.5)
- **Liability Disclaimer:** Epic 2 - Verdict Display (Story 2.4 AC)
- **WCAG 2.1 AA:** Epic 3 - Accessibility Foundation (Story 3.0c)

## Epic List

### Epic 1: Identity, Multi-Tenancy & Foundation ✅ IMPLEMENTED
Users can securely log in via SSO (Google/Microsoft) and operate within a strictly isolated tenant environment. Accountants can switch between client tenants.
**FRs covered:** TECH-1, TECH-2, UX-3.
**Stories:** 1.1 (Project Init), 1.2 (Multi-Tenant Schema), 1.3 (SSO), 1.4 (Context Switcher), 1.5 (CI/CD & GCP).

### Epic 2: The Partner Risk Radar (Screening Engine) ✅ IMPLEMENTED
Users can search any tax number and receive a deterministic binary "Reliable/At-Risk" verdict in < 30 seconds.
**FRs covered:** FR1, FR2, FR3, FR4, NFR1, NFR4, UX-2.
**Stories:** 2.0 (Staging Bootstrap), 2.1 (Tax Search UI), 2.2 (Demo Mode Data Layer), 2.2.1 (DEFERRED — NAV M2M Adapter), 2.2.2 (Adapter Switching & Demo Fixtures), 2.3 (State-Machine), 2.4 (Verdict Card), 2.5 (Audit Logging).
**Moved to Epic 3:** 2.6 (SEO Stubs) → 3.11, 2.7 (Guest Rate Limiting) → 3.12.

### Epic 3: Automated Monitoring & Alerts (Watchlist)
Users can save partners to a watchlist and receive automated email alerts if their status changes. Accountants get a portfolio-wide "Flight Control" pulse. Includes foundational application shell, design system, landing page, WCAG accessibility foundation, cross-cutting infrastructure (i18n, fallback auth, feature flags, encryption hardening), and growth features (SEO gateway stubs, guest rate limiting) relocated from Epic 2.
**FRs covered:** FR5, FR6, FR7, FR15, FR16, NFR6, i18n, NFR3, NFR5, UX-1, UX-3, UX-FlightControl, UX-DesignSystem, WCAG.
**Stories:** 3.0a (Design System), 3.0b (Landing Page), 3.0c (WCAG), 3.1 (i18n), 3.2 (Email/Password Auth), 3.3 (Feature Flags), 3.4 (Encryption Hardening), 3.5 (Async Ingestor), 3.6 (Watchlist CRUD), 3.7 (24h Monitoring), 3.8 (Email Alerts), 3.9 (Portfolio Pulse), 3.10 (Flight Control), 3.11 (SEO Gateway Stubs — moved from 2.6), 3.12 (Guest Rate Limiting — moved from 2.7).
**Note:** Stories 3.0a-c are foundational UX stories and 3.1-3.4 are cross-cutting infrastructure stories — all should be implemented early in Epic 3 as they establish the framework for all subsequent UI and business logic stories.

### Epic 4: EPR Material Library & Questionnaire
Users can save their own company's material templates (e.g., "Plastic Bottle A") and use a smart wizard to find the correct KF-codes. UI implements `MaterialInventoryBlock` component and form validation patterns per UX Spec.
**FRs covered:** FR8, FR9, FR13, UX-MaterialInventoryBlock, UX-FormValidation.
**Stories:** 4.1 (Material Library), 4.2 (DAG Wizard), 4.3 (Manual Override), 4.4 (Template KF-Code Mapping).

### Epic 5: Compliance Reporting & Exports
Users can generate schema-perfect CSV exports for MOHU and high-integrity PDF reports for the Watchlist. Implements `AuditDispatcher` mobile share and "MOHU Gate" validation patterns per UX Spec.
**FRs covered:** FR10, FR14, UX-AuditDispatcher, UX-MOHUGate.
**Stories:** 5.1 (PDF Export & Dispatcher), 5.2 (Quarterly Filing), 5.3 (MOHU CSV), 5.4 (Locale Enforcement).

### Epic 6: System Administration & Integrity
Administrators can monitor data source health (NAV API connectivity, circuit breaker state, credential status), quarantine failing adapters, and hot-swap EPR logic without code changes. Admin pages follow "Mission Control" page spec with accessibility-first health gauges per UX Spec.
**FRs covered:** FR11, FR12, UX-MissionControl.
**Stories:** 6.1 (Data Source Health Dashboard), 6.2 (Adapter Quarantine), 6.3 (EPR JSON Manager), 6.4 (Audit Viewer).

## Epic 1: Identity, Multi-Tenancy & Foundation
**Goal:** Users can securely log in via SSO (Google/Microsoft) and operate within a strictly isolated tenant environment. Accountants can switch between client tenants.

### Story 1.1: Project Initialization & Monorepo Foundation (Kick-off)
As a Lead Developer,
I want to initialize the Java 25 / Spring Boot 4.0.3 backend and Nuxt 3 frontend monorepo,
So that the team has a consistent, type-safe foundation for all future modules.

**Acceptance Criteria:**

**Given** the Architecture-specified Spring Initializr command
**When** I run the initialization scripts
**Then** the `/backend` (Java/Gradle) and `/frontend` (Nuxt) directories are created
**And** the root `docker-compose.yml` starts a PostgreSQL 17 database
**And** the `risk-guard-tokens.json` file is present in the root and readable by both stacks.

### Story 1.2: Multi-Tenant Schema & Isolation
As a User,
I want my data to be strictly isolated from other companies at the database and repository layers,
So that I can trust that my sensitive information is never accidentally exposed.

**Acceptance Criteria:**

**Given** a PostgreSQL 17 database
**When** I create the `tenants` and `users` tables via Flyway
**Then** every table contains a `tenant_id NOT NULL` column
**And** the Spring `TenantFilter` correctly populates the `SecurityContext` from a mock JWT
**And** any jOOQ query without a `tenant_id` filter is rejected by a test.

### Story 1.3: Google & Microsoft SSO Integration
As a User,
I want to log in using my existing Google or Microsoft account,
So that I don't have to manage another password.

**Acceptance Criteria:**

**Given** a Nuxt 3 frontend and Spring Security 6 backend
**When** I click the "Login with Google" button
**Then** I am redirected to the provider's OAuth2 consent screen
**And** upon successful return, a new User/Tenant record is created if none exists
**And** a dual-claim JWT (`home_tenant_id`, `active_tenant_id`) is issued.
**And** failed login attempts show a clear, localized error message.

### Story 1.4: Accountant Context-Switcher UI
As an Accountant,
I want a searchable dropdown in the top-bar to switch between client accounts,
So that I can check different companies' status without logging out.

**Acceptance Criteria:**

**Given** a user with the `ACCOUNTANT` role and multiple `tenant_mandates`
**When** I search for a client name in the Context Switcher
**Then** the UI displays matching clients
**And** selecting one triggers an API call that issues a new short-lived JWT with the updated `active_tenant_id`
**And** the entire dashboard reloads with the new client's context.
**And** if the tenant switch fails (e.g., token expired), a safety interstitial (ContextGuard) blocks access.

### Story 1.5: Automated CI/CD & GCP Infrastructure
As an Admin,
I want a GitHub Actions pipeline that builds and deploys the monorepo to GCP,
So that I can deliver features continuously and reliably to a production-like environment.

**Acceptance Criteria:**

**Given** a GitHub repository and a GCP Project (Frankfurt/Warsaw region)
**When** I push code to the `main` branch
**Then** the 12-step CI pipeline (Compile -> ArchUnit -> OpenAPI -> tsc) runs successfully
**And** the backend is containerized and deployed to Cloud Run with Min-Instances: 1
**And** the frontend is built and deployed to Cloud Storage + Cloud CDN (Nuxt Hybrid Rendering enabled)
**And** sensitive secrets (DB password, SSO keys) are managed via GCP Secret Manager.

## Epic 2: The Partner Risk Radar (Screening Engine)
**Goal:** Users can search any tax number and receive a deterministic binary "Reliable/At-Risk" verdict in < 30 seconds.

### Story 2.0: Bootstrap GCP Staging + Deploy Epic 1 Baseline
As an Admin,
I want a staging environment provisioned and the Epic 1 baseline deployed,
So that Epic 2 development has a production-like target for smoke tests and integration checks.

**Acceptance Criteria:**

**Given** the Terraform infrastructure definitions
**When** I run `terraform init/plan/apply` for the staging environment
**Then** Cloud Run, Artifact Registry, Secret Manager, and required networking are provisioned
**And** the backend is deployed to staging and responds on `/actuator/health`
**And** the frontend is deployed and serves the staging landing page
**And** required secrets (DB password, JWT key, SSO keys) are present in Secret Manager
**And** a smoke test confirms the baseline is reachable and healthy

### Story 2.1: Tax Number Search & Skeleton UI
As a User,
I want to enter an 8 or 11-digit tax number and see a loading animation that tracks the search progress,
So that I know the system is actively retrieving government data.

**Acceptance Criteria:**

**Given** the `screening` module frontend dashboard
**When** I enter a valid 11-digit Hungarian tax number
**Then** a `PartnerSearchRequest` is sent to the backend
**And** the UI displays Skeleton Screen animations listing the sources: `[ ] NAV Debt`, `[ ] Legal Status`, etc.
**And** invalid tax number formats are rejected by frontend Zod validation.

### Story 2.2: Demo Mode Data Layer & Parallel Adapter Orchestration
As a User,
I want the system to retrieve partner data from demo fixtures (or live APIs when available) in parallel using virtual threads,
So that I receive my partner risk verdict in under 30 seconds with a fully functional demo experience.

**Acceptance Criteria:**

**Given** a valid tax number and `riskguard.data-source.mode=demo|test|live`
**When** the `CompanyDataAggregator` is triggered
**Then** it spawns `StructuredTaskScope` virtual threads for the active adapters (DemoCompanyDataAdapter in demo mode, NavOnlineSzamlaAdapter + future NavM2mAdapter in live mode)
**And** it handles timeouts/outages via Resilience4j circuit breakers
**And** it returns a consolidated `CompanySnapshot` JSON object
**And** if a source is timed out, the result is marked as `SourceUnavailable` to trigger the "Grey Shield" UI
**And** in demo mode, the `DemoCompanyDataAdapter` returns realistic Hungarian company data (debt-free, arrears, missing filings, insolvency scenarios) from in-memory fixtures — no network calls.

### Story 2.2.1: Real Data Source Adapter Replacement — DEFERRED
> **DEFERRED (2026-03-12).** Original scope assumed scraping Cégközlöny and NAV multi-query. Both portals are bot-protected. Will be redesigned as "NAV M2M Adapter" when accountant API access is secured (~1-2 months). See ADR-7.

### Story 2.2.2: Profile-Based Adapter Switching & Enriched Demo Fixtures
As a Developer,
I want the data source adapter selection driven by the `riskguard.data-source.mode` property with enriched, realistic demo data,
So that the demo mode delivers a compelling, scenario-rich experience and the codebase has a clear extension point for real NAV adapters when credentials become available.

**Acceptance Criteria:**

**Given** the `riskguard.data-source.mode` property set to `demo`, `test`, or `live`
**When** the Spring context starts
**Then** a `@Configuration` class conditionally creates the appropriate `CompanyDataPort` bean(s): `demo` → `DemoCompanyDataAdapter`; `test`/`live` → reserved for future NAV adapters (not yet implemented)
**And** the `DemoCompanyDataAdapter` is moved to `datasource.internal.adapters.demo` package
**And** a stub package `datasource.internal.adapters.nav` exists with a `package-info.java` referencing ADR-6 as the future extension point
**And** the demo fixtures include 5-10 realistic Hungarian companies covering key verdict scenarios: debt-free, has arrears, missing filings, insolvency proceedings, and suspended tax number
**And** the demo fixtures include 20-50 invoices per quarter with realistic line items (product descriptions, VTSZ codes, quantities, unit prices) for both OUTBOUND and INBOUND directions — seeding future EPR module needs
**And** if `test` or `live` mode is configured but no NAV adapter bean exists, the application fails fast at startup with a clear error message rather than silently falling back to demo data.

**Effort estimate:** ~2 days. **Source:** Sprint Change Proposal CP-2, Architecture ADR-4. NAV Online Számla XML/JAXB integration (ADR-6) deferred until company registration provides test credentials.

### Story 2.3: Deterministic Verdict State-Machine
As a User,
I want a clear, deterministic "Reliable" or "At-Risk" verdict based on my partner's data,
So that I can make quick, objective business decisions without guessing.

**Acceptance Criteria:**

**Given** a `CompanySnapshot` containing debt and legal data
**When** the `VerdictEngine` runs
**Then** it detects `TAX_SUSPENDED` flags or active debt to produce an `AT_RISK` status
**And** it produces a `RELIABLE` status only if all critical markers are clean
**And** it produces an `INCOMPLETE` status if critical sources are `SourceUnavailable` (Grey Shield logic).
**And** the logic passes 100% of the 50+ Golden Case regression tests.

### Story 2.4: Verdict Result Card & Provenance Sidebar
As a User,
I want to see the final verdict in a high-contrast card with a sidebar showing exact data timestamps,
So that I can trust the freshness of the data.

**Acceptance Criteria:**

**Given** a completed search
**When** the verdict is returned to the UI
**Then** the UI shows an Emerald Shield (Reliable), Rose Shield (At-Risk), or Grey Shield (Stale/Unavailable)
**And** a Provenance Sidebar lists the data source names and "Last Checked" times (e.g., `2 minutes ago`)
**And** "Amber" warnings are shown for suspended tax numbers.
**And** if the data age exceeds the "Freshness Guard" (48h), the Grey Shield is automatically displayed with a "Stale" warning.
**And** every verdict display includes an "Informational Purpose Only" liability disclaimer clarifying that data originates from third-party government sources.

### Story 2.5: SHA-256 Audit Logging (Legal Proof)
As a User,
I want every search to be cryptographically signed and logged,
So that I have court-ready evidence of my due diligence.

**Acceptance Criteria:**

**Given** a search result and a disclaimer text
**When** the verdict is rendered
**Then** the backend generates a SHA-256 hash of the (Snapshot + Verdict + Disclaimer)
**And** the record is stored in the `search_audit_log` table with the hash and a timestamp
**And** the hash is displayed on the frontend result card.
**And** if hash generation fails (e.g., null snapshot data), the search result is still displayed but the hash field shows "Audit hash unavailable" and the failure is logged for admin review.

### ~~Story 2.6~~ → Moved to Story 3.11
### ~~Story 2.7~~ → Moved to Story 3.12
> Stories 2.6 (SEO Gateway Stubs) and 2.7 (Guest Rate Limiting) were moved to Epic 3 on 2026-03-16. They are growth/conversion features that fit better alongside the landing page (3.0b) and feature flags (3.3). Epic 2 is now closed with all core screening stories (2.0–2.5) complete.

## Epic 3: Automated Monitoring & Alerts (Watchlist)
**Goal:** Users can save partners to a watchlist and receive automated email alerts if their status changes. Accountants get a portfolio-wide pulse.

### Story 3.0a: Design System Tokens & Application Shell
As a User,
I want the application to have a consistent, professional visual identity with reliable navigation,
So that the product feels trustworthy and I can orient myself across all features.

**Acceptance Criteria:**

**Given** the UX Design Specification ("The Safe Harbor" design direction)
**When** I visit any authenticated page in the application
**Then** the Tailwind color tokens are implemented (Deep Navy #0F172A, Forest Emerald #15803D, Crimson Alert #B91C1C, Amber #B45309)
**And** typography uses Inter (primary) and JetBrains Mono (data: Tax IDs, hashes)
**And** the app shell includes a persistent sidebar (desktop), responsive nav, and Slate 900 top bar with Context Switcher placement
**And** the responsive breakpoints follow the Dual-Context strategy: Mobile (<768px), Tablet (768-1024px), Desktop (>1024px)
**And** the button hierarchy is implemented: Primary (Deep Navy), Secondary (Slate Grey border), Tertiary (Borderless Slate)
**And** feedback patterns are consistent: Emerald for success, Amber for warnings, Crimson for errors
**And** the private workspace uses the "high-density, sidebar-driven, sober/legal" aesthetic
**And** loading states use PrimeVue Skeleton components per the "Skeletal Trust" pattern
**And** if the Tailwind config fails to load or tokens are missing, the application falls back to browser-default styles without crashing.

**Source:** UX Design Specification sections 3 (Visual Design), 4 (Vault Pivot), 7 (Consistency Patterns).

### Story 3.0b: Public Landing Page
As a Visitor,
I want a public landing page that lets me instantly search a tax number without signing up,
So that I can experience the product's value before committing to registration.

**Acceptance Criteria:**

**Given** a public (unauthenticated) visitor
**When** I navigate to the root URL
**Then** the Landing Page provides zero-friction Tax ID search with the "airy, horizontal, marketing-focused" aesthetic
**And** the page clearly communicates the product value proposition and trust signals
**And** the search input uses intelligent masking for 8/11-digit Hungarian Tax Numbers (UX Spec §7.3)
**And** the page is server-side rendered by Nuxt for SEO performance
**And** if the search service is unavailable, the page displays a clear "Service Temporarily Unavailable" message instead of a broken UI.

**Source:** UX Design Specification sections 4 (Vault Pivot), 6 (Page & Component Strategy).

### Story 3.0c: WCAG 2.1 AA Accessibility Foundation
As a User with accessibility needs,
I want the application to meet WCAG 2.1 AA standards,
So that I can use all features regardless of my abilities.

**Acceptance Criteria:**

**Given** any page in the application (public or authenticated)
**When** I navigate using a screen reader or keyboard
**Then** WCAG 2.1 AA baseline is met: 4.5:1 contrast ratio for all metadata text, 7:1 for primary verdicts
**And** all status colors are paired with unique icons (Shield-Check, Shield-X, Shield-Clock) for color-blind users
**And** skip-links are provided for accountants to bypass sidebars
**And** logical tab order is maintained through all form inputs including the "MOHU Gate" weight inputs
**And** ARIA-live regions announce asynchronous data source status updates
**And** a Pa11y or Axe-core CI scan passes for all core user journeys without critical violations.

**Source:** UX Design Specification section 8 (Responsive & Accessibility).

### Story 3.1: Internationalization (i18n) Infrastructure
As a User,
I want the application to be available in Hungarian (primary) and English (fallback),
So that I can use the product comfortably in my preferred language.

**Acceptance Criteria:**

**Given** a Nuxt 3 frontend with `@nuxtjs/i18n` configured
**When** the application loads
**Then** Hungarian (hu) is the default locale and English (en) is the fallback
**And** all user-facing text is sourced from JSON message files (`frontend/i18n/hu/*.json`, `frontend/i18n/en/*.json`)
**And** the user's `preferred_language` from the `users` table is respected on login
**And** a language switcher allows toggling between HU and EN
**And** the backend uses `messages_hu.properties` and `messages_en.properties` for server-generated content (emails, exports)
**And** if a translation key is missing in Hungarian, the English fallback is displayed without crashing.

### Story 3.2: Email/Password Registration (Fallback Auth)
As a User,
I want to register and sign in with email and password,
So that I can access the product without a third-party SSO provider.

**Acceptance Criteria:**

**Given** a new user with no SSO account
**When** I create an account with email + password
**Then** the system creates a User and Tenant record
**And** a dual-claim JWT (`home_tenant_id`, `active_tenant_id`) is issued
**And** password storage uses a strong one-way hash (BCrypt/Argon2)
**And** invalid credentials return a localized error message
**And** if the email is already registered, the system returns a clear "Email already in use" message without revealing account details.

### Story 3.3: Feature Flags & Subscription Tier Gating
As a Product Owner,
I want feature access to be controlled by subscription tier via feature flags,
So that free-tier users are guided toward upgrading and paid features are properly protected.

**Acceptance Criteria:**

**Given** a user with a specific subscription tier (ALAP/PRO/PRO_EPR)
**When** they attempt to access a tier-gated feature (e.g., EPR Wizard on ALAP tier)
**Then** the backend `TierGate` interceptor checks the tenant's `tier` field against the feature's required tier
**And** if the user's tier is insufficient, the API returns a `403 TIER_UPGRADE_REQUIRED` response
**And** the frontend `useTierGate` composable renders a localized upgrade prompt instead of the locked feature
**And** the upgrade prompt clearly communicates what tier is required and what benefits it unlocks
**And** if the tier check fails due to a backend error, the feature defaults to locked with a "temporarily unavailable" message (fail-closed).

### Story 3.4: Encryption & Infrastructure Hardening
As an Admin,
I want the production infrastructure to enforce encryption at rest and in transit, and maintain availability during business hours,
So that user data is protected and the service is reliable when it matters most.

**Acceptance Criteria:**

**Given** the GCP Cloud Run + Cloud SQL production environment
**When** the infrastructure is configured
**Then** encryption at rest is enabled on the PostgreSQL database (AES-256 via GCP Cloud SQL or disk-level encryption)
**And** all database connections use TLS 1.3 for encryption in transit
**And** the Cloud Run service is configured with Min-Instances: 1 during Hungarian business hours (8:00-17:00 CET) to ensure zero cold-starts (NFR3)
**And** a health check confirms encryption settings are active and min-instances scheduling is functional.

### Story 3.5: Async NAV Debt Ingestor (Background Data Freshness)
As a User,
I want the system to proactively refresh NAV debt data in the background on a daily schedule,
So that my partner verdicts are always based on recent data without waiting for manual searches.

**Acceptance Criteria:**

**Given** a scheduled daily job (outside of user search latency path)
**When** the `AsyncIngestor` triggers during off-peak hours
**Then** it retrieves updated NAV debt status for all actively monitored partners (watchlist entries across all tenants)
**And** updated snapshots are stored in the `company_snapshots` table with a fresh `checked_at` timestamp
**And** the ingestor runs in isolation from user-facing search requests (separate thread pool / worker process)
**And** if a source is unavailable during ingestion, the existing snapshot is retained and the failure is logged without marking the data as stale
**And** the ingestor respects rate limits to avoid overwhelming data source APIs (configurable delay between requests).

> **Dependency Note (2026-03-13):** This story's production data path depends on NAV M2M Adózó API access (ADR-7, deferred). In demo mode, the AsyncIngestor refreshes demo fixture data (which is static — effectively a no-op but validates the scheduling/retry infrastructure). When NAV M2M credentials are available, this story activates with real debt data.

### Story 3.6: Watchlist Management (CRUD)
As a User,
I want to add searched partners to a private Watchlist,
So that I don't have to re-enter their tax number every time I want to check them.

**Acceptance Criteria:**

**Given** a search result or the Watchlist page
**When** I click "Add to Watchlist"
**Then** the partner is saved to the `watchlist_entries` table (scoped to `tenant_id`)
**And** I can view a PrimeVue `DataTable` of all my watched partners with their current status.
**And** I can remove partners from the list.

### Story 3.7: 24h Background Monitoring Cycle
As a User,
I want the system to automatically monitor my watchlist for status changes every 24 hours,
So that I am proactively informed of any risks without manual effort.

**Acceptance Criteria:**

**Given** a list of `watchlist_entries`
**When** the @Scheduled `WatchlistMonitor` triggers
**Then** it initiates a background data source check for each partner
**And** it compares the new verdict against the `last_verdict_status`
**And** it logs any deviations (e.g., `RELIABLE` -> `AT_RISK`).
**And** it handles transient failures with retries without skipping monitoring for the entire list.

### Story 3.8: Resend Email Alerts & Outbox Pattern
As a User,
I want to receive an email notification the moment a partner's status changes,
So that I can take immediate business action.

**Acceptance Criteria:**

**Given** a detected status change
**When** the `PartnerStatusChanged` event is published
**Then** a notification record is created in the `notification_outbox`
**And** the `OutboxProcessor` sends a localized (HU/EN) email via Resend
**And** failed emails are retried with exponential backoff.
**And** the email includes the "Audit Proof" SHA-256 hash for verification.

### Story 3.9: The Accountant "Portfolio Pulse" Feed
As an Accountant,
I want a global alert feed on my main dashboard showing status changes across all my clients' partners,
So that I can proactively advise my clients.

**Acceptance Criteria:**

**Given** a user with the `ACCOUNTANT` role
**When** I log in to the dashboard
**Then** I see a "Portfolio Alerts" sidebar listing recent status changes for ALL client tenants I have mandates for
**And** clicking an alert instantly switches my context to that client's dashboard.
**And** the dashboard implements the "Morning Risk Pulse" UX flow: status changes are promoted to the top with one-tap access to Audit Proof PDF (UX Spec §5.1).
**And** the alert feed uses the established feedback patterns: Emerald for resolved, Amber for degraded, Crimson for new At-Risk (UX Spec §7.2).

### Story 3.10: Accountant "Flight Control" Dashboard
As an Accountant,
I want a dedicated, high-density dashboard that aggregates my entire client portfolio,
So that I can see at a glance which clients have the most "At-Risk" or "Stale" partners.

**Acceptance Criteria:**

**Given** a user with the `ACCOUNTANT` role
**When** I navigate to the "Flight Control" page
**Then** I see a sortable table of all client tenants I manage
**And** each row displays a summary count of "Reliable", "At-Risk", and "Stale" partners
**And** I can filter by client name or risk level.
**And** clicking a client name navigates to their specific dashboard using the context switch.
**And** the page follows the "Flight Control" page spec: desktop-optimized, multi-column "Quiet Grid" tables with persistent sidebar (UX Spec §6.2, §8.2).
**And** the layout uses dense data grids optimized for mouse/keyboard efficiency per the "Operation Context" responsive strategy (UX Spec §8.1).

### Story 3.11: SEO Gateway Stubs (Public Company Pages)
> Moved from Story 2.6 on 2026-03-16. Growth/conversion feature — fits alongside Landing Page (3.0b).

As a Product Owner,
I want public, indexable company pages with structured data for every Hungarian company searched,
So that organic search traffic drives new users to the platform.

**Acceptance Criteria:**

**Given** a previously searched Hungarian company
**When** a search engine or unauthenticated user visits `/company/{taxNumber}`
**Then** Nuxt renders the page via SSR/ISR with the company name, tax number, and a generic status indicator
**And** the page includes JSON-LD structured data (Organization schema) with the company's publicly available information
**And** the page includes a clear CTA to "Check this company's live risk status" requiring login
**And** the page does NOT expose any tenant-specific verdict data or audit information
**And** Nuxt `routeRules` are configured so public company pages use SSR/ISR while authenticated routes use SPA mode
**And** if the company has never been searched, the page returns a 404 with a "Company not found — search now" prompt.

### Story 3.12: Demo Mode & Guest Rate Limiting
> Moved from Story 2.7 on 2026-03-16. Growth/conversion feature — fits alongside Feature Flags (3.3) and Landing Page (3.0b).

As a Guest (unauthenticated visitor),
I want to try the product with limited access before signing up,
So that I can evaluate its value with real data before committing to a paid plan.

**Acceptance Criteria:**

**Given** an unauthenticated visitor on the Landing Page
**When** I perform a partner search without logging in
**Then** the system creates a transient `guest_sessions` record with a session fingerprint
**And** I can search up to 10 unique companies and perform 3 instant checks per day
**And** the UI displays a progress indicator showing remaining searches (e.g., "7 of 10 companies used")
**And** when the daily check limit is reached, the UI shows a clear, localized message: "Daily limit reached — sign up for unlimited checks" with a prominent registration CTA
**And** when the company limit is reached, the UI shows: "Demo limit reached — sign up to monitor unlimited partners"
**And** expired guest sessions are purged daily by the scheduled cleanup job
**And** guest data is stored with a synthetic `tenant_id` and is never accessible to authenticated users.

## Epic 4: EPR Material Library & Questionnaire
**Goal:** Users can save their own company's material templates (e.g., "Plastic Bottle A") and use a smart wizard to find the correct KF-codes.

### Story 4.1: EPR Material Library & Seasonal Templates
As a User,
I want to manage a library of recurring packaging materials, including seasonal templates and "Copy from Previous" logic,
So that my quarterly filing process is as efficient as possible.

**Acceptance Criteria:**

**Given** the EPR Dashboard
**When** I create a new material entry
**Then** I can name it (e.g., "Standard Cardboard Box 1") and assign its base weight.
**And** the record is saved to the `epr_material_templates` table.
**And** I can mark templates as "Seasonal" (e.g., "Christmas Packaging") to toggle their visibility.
**And** I can click "Copy from Previous" to duplicate an entire library structure from the prior quarter.
**And** I can edit or delete these templates later.
**And** the material library uses the `MaterialInventoryBlock` component: a high-speed weight entry grid following the "Inventory & Monitor" mental model (UX Spec §6.1, §2.2).
**And** on desktop, the EPR section uses an expanded side-panel summary layout (UX Spec §8.2).

### Story 4.2: Smart Material Wizard (DAG Questionnaire)
As a User,
I want a multi-step questionnaire that guides me to the correct KF-code based on my material's characteristics,
So that I am legally compliant without needing to memorize environmental law.

**Acceptance Criteria:**

**Given** a PrimeVue Stepper component
**When** I select options (Material Type -> Usage -> Subtype)
**Then** the DagEngine.java validates the path through the JSON configuration.
**And** the system returns the specific KF-code (e.g., `99 01 01`) and its associated fee rate.
**And** the UI shows a breadcrumb of the logic path used (e.g., `Packaging -> Paper -> Corrugated`).
**And** form inputs follow the UX validation patterns: real-time feedback with Crimson for errors, Emerald for valid steps (UX Spec §7.2, §7.3).

### Story 4.3: Manual Override & Confidence Score
As an Accountant,
I want to see the system's confidence in a KF-code mapping and manually override it if necessary,
So that I maintain professional control over the final filing.

**Acceptance Criteria:**

**Given** a KF-code suggestion from the Wizard
**When** the system is unsure (low confidence)
**Then** the UI displays an Amber Warning on the result.
**And** a "Manual Override" button allows the user to select a different code from a searchable list.
**And** the override is logged as a separate audit event.

### Story 4.4: Material Template with KF-Code Mapping
As a User,
I want to link a specific KF-code to a Material Template in my library,
So that the template is "Filing-Ready."

**Acceptance Criteria:**

**Given** a Material Template in the Library
**When** I complete the Wizard for that material
**Then** the KF-code is permanently saved to that template.
**And** the template is marked as "Verified" in the inventory list.
**And** if the KF-code save fails (e.g., network error), the template retains its "Unverified" status and the user sees a clear retry prompt.

## Epic 5: Compliance Reporting & Exports
**Goal:** Users can generate schema-perfect CSV exports for MOHU and high-integrity PDF reports for the Watchlist.

### Story 5.1: Watchlist Bulk PDF Export & Mobile Dispatcher
As a User,
I want to select partners from my watchlist and export a professional PDF status report with a native mobile share option,
So that I can provide court-ready evidence of my due diligence to banks or auditors instantly from my device.

**Acceptance Criteria:**

**Given** the Watchlist page with a selection of partners
**When** I click the "Export Status PDF" button
**Then** the system generates a branded PDF containing Partner Name, Tax Number, Current Verdict, and Last Check Timestamp.
**And** each entry includes its unique SHA-256 verification hash.
**And** the PDF includes the "Informational Purpose Only" liability disclaimer.
**And** on mobile devices, the `AuditDispatcher` uses `navigator.share` to allow instant dispatching to email, Slack, or WhatsApp.
**And** the `AuditDispatcher` component follows the mobile-first "Decision Context" pattern: optimized for one-handed thumb interaction with the share action as the primary CTA (UX Spec §6.1, §8.1).

### Story 5.2: Quarterly EPR Filing Workflow
As a User,
I want to enter the quantities sold for my "Verified" material templates,
So that I can calculate my total EPR liability for the quarter.

**Acceptance Criteria:**

**Given** a list of "Verified" templates in the EPR Library
**When** I start a "New Filing" and enter the Quantity (pcs) for each item
**Then** the FeeCalculator computes the total weight and total fee based on the current JSON fee tables.
**And** I see a summary of the calculation before generating the final file.
**And** quantity inputs use the "MOHU Gate" real-time validation pattern: enforcing decimal precision with immediate Crimson/Emerald feedback (UX Spec §7.3).

### Story 5.3: MOHU-Ready CSV Export
As a User,
I want to generate a CSV file that matches the exact schema required by the MOHU portal,
So that I can complete my quarterly filing with zero manual edits.

**Acceptance Criteria:**

**Given** a completed EPR Filing calculation
**When** I click "Export for MOHU"
**Then** the backend generates a CSV file using semicolon delimiters and Hungarian UTF-8 BOM.
**And** the file columns and KF-code formats match the latest MOHU schema version.
**And** the export is logged in the `epr_exports` table with a config version reference.
**And** if the export generation fails (e.g., missing template data or backend error), the user sees a localized error message identifying the issue and no corrupt file is downloaded.

### Story 5.4: Export Locale Enforcement & UX Notice
As a User,
I want to be notified that my government exports are being generated in Hungarian, even if my UI is set to English,
So that I am not confused by the language change in the final file.

**Acceptance Criteria:**

**Given** a user with UI language set to English
**When** they trigger an "Export for MOHU"
**Then** the UI displays an Indigo Toast notification: "Export generated in Hungarian (required by MOHU portal)."
**And** the generated CSV content uses Hungarian material category names from messages_hu.properties.

## Epic 6: System Administration & Integrity
**Goal:** Administrators can monitor data source health, quarantine failing adapters, and hot-swap EPR logic without code changes.

### Story 6.1: Data Source Health Dashboard (The Heartbeat)
As an Admin,
I want to see a live dashboard of all data source adapters (NAV Online Számla, demo fixtures, future NAV M2M) and their current health,
So that I can detect API issues, credential expiry, or rate limiting before users are affected.

**Acceptance Criteria:**

**Given** the Admin Dashboard
**When** I load the "Data Sources" tab
**Then** the UI displays a grid of cards showing MTBF, Success Rate %, Circuit Breaker State, and current data source mode (demo/test/live) for each adapter
**And** the data is pulled directly from the Resilience4j Actuator endpoints
**And** the UI shows the "Last Successful Check" timestamp per source and NAV credential status (valid/expired/missing)
**And** in demo mode, all adapters show HEALTHY status with a clear "Demo Mode" indicator
**And** the page follows the "Mission Control" page spec: impact-driven health gauges with MTBF scoring (UX Spec §6.2, §9.3)
**And** ARIA-live regions announce data source status changes for screen reader accessibility (UX Spec §8.3).

### Story 6.2: Manual Adapter Quarantine
As an Admin,
I want a "Force Quarantine" toggle for each data source adapter,
So that I can manually disable a failing API integration if the canary hasn't caught it yet.

**Acceptance Criteria:**

**Given** a healthy or failing adapter in the Data Sources dashboard
**When** I toggle the "Quarantine" switch
**Then** the backend instantly opens the Resilience4j circuit breaker for that adapter
**And** the global frontend health banner updates to show that specific source is "Under Maintenance"
**And** all subsequent verdicts for that source are marked as "Unavailable"
**And** the quarantine action is logged in the admin audit trail.

### Story 6.3: Hot-Swappable EPR JSON Manager
As an Admin,
I want to edit the EPR fee tables and KF-code logic via a browser-based editor,
So that I can comply with legislation changes in seconds without a redeployment.

**Acceptance Criteria:**

**Given** the Admin "EPR Config" page
**When** I edit the JSON configuration in the Monaco/Code editor
**Then** clicking "Validate" runs the EPR Golden Test Cases against the new JSON.
**And** if validation passes, clicking "Publish" increments the `epr_configs` version and makes it the active one.
**And** old calculations retain their reference to previous config versions for auditability.

### Story 6.4: GDPR Search Audit Viewer
As an Admin,
I want to search and view the `search_audit_log`,
So that I can provide proof of a specific risk check if requested by legal authorities or users.

**Acceptance Criteria:**

**Given** a tax number or tenant ID
**When** I search the Audit Log
**Then** the UI displays the timestamped entry, the SHA-256 hash, and the source URLs that were used.
**And** I can see which User ID performed the search.
**And** if no matching audit records are found, the UI displays a clear "No records found for this search" message instead of an empty table.




