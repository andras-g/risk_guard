---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: 
  - "_bmad-output/planning-artifacts/prd.md"
  - "_bmad-output/planning-artifacts/architecture.md"
  - "Party Mode Synthesis (March 2026)"
---

# risk_guard - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for risk_guard, decomposing the requirements from the PRD, UX Design concepts, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

- **FR1:** Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers.
- **FR2:** System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék.
- **FR3:** System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts.
- **FR4:** System flags "Suspended Tax Numbers" for manual review.
- **FR5:** Authenticated Users can manage a private Watchlist of partners.
- **FR6:** System monitors Watchlist partners for status changes every 24 hours.
- **FR7:** System triggers Resend email alerts on partner status deviations.
- **FR8:** Users can navigate a multi-step questionnaire for material classification (EPR Wizard).
- **FR9:** System validates inputs via backend JSON-driven Directed Graph (DAG).
- **FR10:** System generates schema-perfect CSV/XLSX exports for MOHU reporting.
- **FR11:** Administrators can monitor scraper success rates via a health dashboard.
- **FR12:** Administrators can update EPR logic via JSON configuration without code redeploys.
- **FR13:** Users can save and manage material templates in a personal "EPR Library."
- **FR14:** Users can generate a bulk PDF export of their Watchlist status including SHA-256 verification hashes.

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
- **TECH-3:** Use standalone `resilience4j-spring-boot3` for scraper circuit breakers.
- **UX-1:** Visual Color Palette: Slate 900 (Nav), Indigo 600 (Primary), Emerald 500 (Reliable), Rose 600 (At-Risk).
- **UX-2:** Implement Skeleton Screen animations showing source resolution (e.g., "[✔] NAV checked").
- **UX-3:** Searchable Context Switcher in top-bar for accountants to jump between client tenants.
- **UX-4:** Provenance Sidebar on verdict pages showing specific scrape timestamps.
- **i18n:** Primary language is Hungarian (hu); secondary fallback is English (en).

### FR Coverage Map

- **FR1:** Epic 2 - Tax Number Search
- **FR2:** Epic 2 - Gov Data Retrieval
- **FR2:** Epic 2 - Gov Data Retrieval
- **FR3:** Epic 2 - State-Machine Verdict
- **FR4:** Epic 2 - Suspended Tax Flag
- **FR5:** Epic 3 - Watchlist CRUD
- **FR6:** Epic 3 - 24h Monitoring
- **FR7:** Epic 3 - Email Alerts
- **FR8:** Epic 4 - Material Questionnaire
- **FR9:** Epic 4 - DAG Validation
- **FR10:** Epic 5 - MOHU CSV Export
- **FR11:** Epic 6 - Scraper Health Dashboard
- **FR12:** Epic 6 - JSON Logic Hot-swap
- **FR13:** Epic 4 - Material Library
- **FR14:** Epic 5 - Watchlist PDF Export

## Epic List

### Epic 1: Identity, Multi-Tenancy & Foundation
Users can securely log in via SSO (Google/Microsoft) and operate within a strictly isolated tenant environment. Accountants can switch between client tenants.
**FRs covered:** TECH-1, TECH-2, UX-3.

### Epic 2: The Partner Risk Radar (Screening Engine)
Users can search any tax number and receive a deterministic binary "Reliable/At-Risk" verdict in < 30 seconds.
**FRs covered:** FR1, FR2, FR3, FR4, NFR1, NFR4, UX-2.

### Epic 3: Automated Monitoring & Alerts (Watchlist)
Users can save partners to a watchlist and receive automated email alerts if their status changes. Accountants get a portfolio-wide "Flight Control" pulse. Includes foundational application shell, design system, and landing page (Story 3.0) per UX Design Specification.
**FRs covered:** FR5, FR6, FR7, UX-1, UX-3, UX-FlightControl, UX-DesignSystem.

### Epic 4: EPR Material Library & Questionnaire
Users can save their own company's material templates (e.g., "Plastic Bottle A") and use a smart wizard to find the correct KF-codes. UI implements `MaterialInventoryBlock` component and form validation patterns per UX Spec.
**FRs covered:** FR8, FR9, FR13, UX-MaterialInventoryBlock, UX-FormValidation.

### Epic 5: Compliance Reporting & Exports
Users can generate schema-perfect CSV exports for MOHU and high-integrity PDF reports for the Watchlist. Implements `AuditDispatcher` mobile share and "MOHU Gate" validation patterns per UX Spec.
**FRs covered:** FR10, FR14, UX-6, UX-AuditDispatcher, UX-MOHUGate.

### Epic 6: System Administration & Integrity
Administrators can monitor scraper health, quarantine broken adapters, and hot-swap EPR logic without code changes. Admin pages follow "Mission Control" page spec with accessibility-first health gauges per UX Spec.
**FRs covered:** FR11, FR12, UX-5, UX-MissionControl.

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

### Story 2.2: Parallel Scraper Engine & Outage Resilience
As a User,
I want the system to retrieve government data in parallel using modern virtual threads,
So that I receive my partner risk verdict in under 30 seconds even if some sources are slow.

**Acceptance Criteria:**

**Given** a valid tax number
**When** the `CompanyDataAggregator` is triggered
**Then** it spawns `StructuredTaskScope` virtual threads for the JSoup adapters (NAV and e-Cégjegyzék)
**And** it handles timeouts/outages via Resilience4j circuit breakers
**And** it returns a consolidated `CompanySnapshot` JSON object.
**And** if a source is timed out, the result is marked as `SourceUnavailable` to trigger the "Grey Shield" UI.

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
**And** a Provenance Sidebar lists the source URLs and "Last Scraped" times (e.g., `2 minutes ago`)
**And** "Amber" warnings are shown for suspended tax numbers.
**And** if the data age exceeds the "Freshness Guard" (48h), the Grey Shield is automatically displayed with a "Stale" warning.

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

### Story 2.6: Email/Password Registration (Fallback Auth)
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

## Epic 3: Automated Monitoring & Alerts (Watchlist)
**Goal:** Users can save partners to a watchlist and receive automated email alerts if their status changes. Accountants get a portfolio-wide pulse.

### Story 3.0: Application Shell, Design System & Landing Page
As a User,
I want the application to have a polished, professional visual identity with consistent navigation and a public landing page,
So that the product feels trustworthy and I can orient myself across all features.

**Acceptance Criteria:**

**Given** the UX Design Specification ("The Safe Harbor" design direction)
**When** I visit the application
**Then** the Tailwind color tokens are implemented (Deep Navy #0F172A, Forest Emerald #15803D, Crimson Alert #B91C1C, Amber #B45309)
**And** typography uses Inter (primary) and JetBrains Mono (data: Tax IDs, hashes)
**And** the app shell includes a persistent sidebar (desktop), responsive nav, and Slate 900 top bar with Context Switcher placement
**And** the responsive breakpoints follow the Dual-Context strategy: Mobile (<768px), Tablet (768-1024px), Desktop (>1024px)
**And** the button hierarchy is implemented: Primary (Deep Navy), Secondary (Slate Grey border), Tertiary (Borderless Slate)
**And** feedback patterns are consistent: Emerald for success, Amber for warnings, Crimson for errors
**And** the public Landing Page provides zero-friction Tax ID search with the "airy, horizontal, marketing-focused" aesthetic
**And** the private workspace transitions to the "high-density, sidebar-driven, sober/legal" aesthetic
**And** WCAG 2.1 AA baseline is met: 4.5:1 contrast for metadata, 7:1 for verdicts, skip-links, ARIA-live regions
**And** loading states use PrimeVue Skeleton components per the "Skeletal Trust" pattern

**Source:** UX Design Specification sections 3 (Visual Design), 4 (Vault Pivot), 6 (Page & Component Strategy), 7 (Consistency Patterns), 8 (Responsive & Accessibility).

### Story 3.1: Watchlist Management (CRUD)
As a User,
I want to add searched partners to a private Watchlist,
So that I don't have to re-enter their tax number every time I want to check them.

**Acceptance Criteria:**

**Given** a search result or the Watchlist page
**When** I click "Add to Watchlist"
**Then** the partner is saved to the `watchlist_entries` table (scoped to `tenant_id`)
**And** I can view a PrimeVue `DataTable` of all my watched partners with their current status.
**And** I can remove partners from the list.

### Story 3.2: 24h Background Monitoring Cycle
As a User,
I want the system to automatically monitor my watchlist for status changes every 24 hours,
So that I am proactively informed of any risks without manual effort.

**Acceptance Criteria:**

**Given** a list of `watchlist_entries`
**When** the @Scheduled `WatchlistMonitor` triggers
**Then** it initiates a background scrape for each partner
**And** it compares the new verdict against the `last_verdict_status`
**And** it logs any deviations (e.g., `RELIABLE` -> `AT_RISK`).
**And** it handles transient failures with retries without skipping monitoring for the entire list.

### Story 3.3: Resend Email Alerts & Outbox Pattern
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

### Story 3.4: The Accountant "Portfolio Pulse" Feed
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

### Story 3.5: Accountant "Flight Control" Dashboard
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
**Goal:** Administrators can monitor scraper health, quarantine broken adapters, and hot-swap EPR logic without code changes.

### Story 6.1: Scraper Health Dashboard (The Heartbeat)
As an Admin,
I want to see a live dashboard of all scraper adapters and their current health,
So that I can detect government portal changes before users complain.

**Acceptance Criteria:**

**Given** the Admin Dashboard
**When** I load the "Scrapers" tab
**Then** the UI displays a grid of cards showing MTBF, Success Rate %, and current Circuit Breaker State for each adapter.
**And** the data is pulled directly from the Resilience4j Actuator endpoints.
**And** the UI shows the "Last Successful Scrape" timestamp per source.
**And** the page follows the "Mission Control" page spec: impact-driven health gauges with MTBF scoring (UX Spec §6.2, §9.3).
**And** ARIA-live regions announce scraper status changes for screen reader accessibility (UX Spec §8.3).

### Story 6.2: Manual Adapter Kill-Switch (Quarantine)
As an Admin,
I want a "Force Quarantine" toggle for each scraper adapter,
So that I can manually disable a broken source if the canary hasn't caught it yet.

**Acceptance Criteria:**

**Given** a healthy or failing adapter in the dashboard
**When** I toggle the "Quarantine" switch
**Then** the backend instantly opens the Resilience4j circuit breaker for that adapter.
**And** the global frontend health banner updates to show that specific source is "Under Maintenance."
**And** all subsequent verdicts for that source are marked as "Unavailable."

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




