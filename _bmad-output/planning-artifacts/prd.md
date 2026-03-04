---
stepsCompleted: ["step-01-init", "step-02-discovery", "step-03-success", "step-04-journeys", "step-05-domain", "step-06-innovation", "step-07-project-type", "step-08-scoping", "step-09-functional", "step-10-nonfunctional", "step-11-polish"]
inputDocuments: ["/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/product-brief-risk_guard-2026-03-04.md", "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md", "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/market-Hungarian-SME-behavior-and-red-flag-response-research-2026-03-04.md", "/home/andras/dev/risk_guard/partnerRadar.md"]
documentCounts:
  briefs: 1
  research: 2
  brainstorming: 0
  projectDocs: 0
  initialIdeas: 1
classification:
  projectType: "SaaS B2B"
  domain: "Fintech / Govtech"
  complexity: "High"
  projectContext: "Greenfield"
workflowType: 'prd'
---

# Product Requirements Document - risk_guard

**Author:** Andras
**Date:** 2026-03-04

## Executive Summary
risk_guard (PartnerRadar) is a multi-tenant B2B SaaS platform providing Hungarian SMEs and bookkeepers with deterministic partner risk screening and automated EPR (Extended Producer Responsibility) compliance. The system bridges the "Enterprise Gap" by offering professional-grade reliability verdicts at an SME price point, utilizing a high-integrity state-machine engine and 2026-era AI reasoning.

## Success Criteria

### User Success
- **Zero-Edit EPR Export:** 100% of users generate MOHU-ready exports that pass local schema validation without manual data correction.
- **Accountant Validation:** 100% match between the system's State-Machine Verdict and professional manual assessments for verified "Golden Case" scenarios.
- **Time-to-Verdict:** < 30 seconds from tax-number input to a full deterministic risk profile.
- **Strict Integrity UX:** System displays "Data Temporarily Unavailable" during government portal failures to maintain absolute factual trust.

### Business Success
- **Retention:** 90% return rate for subsequent quarterly EPR reporting cycles.
- **Conversion:** > 15% conversion from the 10-company/3-check demo limit to paid subscriptions.
- **Sustainability:** Solo-developer maintenance overhead < 10 hours/week.

### Technical Success
- **Deterministic Integrity:** 100% pass rate on a regression suite of 50+ immutable gov-data snapshots.
- **Edge-Case Coverage:** Successful detection of "Suspended Tax Numbers" and "Walking Dead" legal statuses.
- **Scraper Health:** > 98% successful automated retrieval rate across all target portals.

## Product Scope

### MVP (Phase 1: 4-Week Sprint)
- **State-Machine Engine:** Factual retrieval and logic processing of NAV Debt, Insolvency, and Tax Status.
- **Verdict Dashboard:** Mobile-optimized UI providing binary "Reliable/At-Risk" statuses.
- **Watchlist:** Automated 24h monitoring for assigned partners with Resend email alerts.
- **JSON-Driven EPR Wizard:** Linear questionnaire for material classification based on a "Top-20 Common KF-Codes" semantic constructor.
- **Schema-Perfect Export:** High-fidelity CSV/XLS export for MOHU reporting with built-in "Submission Guide."
- **Demo Mode:** Guest searches limited to 10 companies and 3 instant checks per day.

### Growth & Vision (Roadmap)
- **AI Narrative Agent (v1.1):** Human-readable summaries explaining the "Why" behind risk verdicts.
- **Human-in-the-Loop Scraper Repair (v1.1):** LLM-driven auto-triage and one-click repair proposals for government UI changes.
- **Accountant Pro-View:** Portfolio-wide dashboard for managing multiple SME client accounts.
- **Predictive Analytics:** Anomaly detection in financial trends before public default listing.

## User Journeys

### Journey 1: The Midnight Risk Check (Gábor, SME Owner)
Gábor is finalizing a 10M HUF contract at 10:00 PM. He enters the partner's tax number into risk_guard. The system executes a real-time state-logic check and displays a **Red Shield** due to active NAV debt. Gábor requests 100% prepayment, saving his cash flow instantly.

### Journey 2: The Quarterly EPR Sprint (Judit, Bookkeeper)
Judit must file EPR reports for 40 clients. She selects a client in the "Pro View" and enters weights into the JSON Wizard. The system maps inputs to valid KF-codes. She generates a schema-perfect CSV and uploads it to the MOHU portal without errors, finishing in 5 minutes instead of 45.

### Journey 3: Error Recovery (System Resilience)
A user attempts a search while a government portal is down. The UI clearly states "Data Temporarily Unavailable" and offers an "Alert me when portal returns" button. The user feels supported by a system that "stays on it."

## Domain-Specific Requirements

### Compliance & Regulatory
- **2026 EPR Law:** Precise material category mapping and weight reporting.
- **Liability Protection:** Every report displays an "Informational Purpose Only" disclaimer clarifying third-party source origins.
- **GDPR Audit Trail:** Full logging of partner searches to support "Legitimate Interest" data processing.

### Technical Constraints
- **State-Machine Verdicts:** Risk assessment must be a closed-loop system with zero "fuzzy" logic for core status.
- **Suspended Tax Number Detection:** Detects `TAX_SUSPENDED` flag from NAV to trigger "Manual Review" UI states.
- **Snapshot Traceability:** Display source timestamp and official server URL for every search result.

### Risk Mitigations
- **Data Freshness Guard:** Auto-shift to "Unavailable" status if source data age exceeds 48 hours.
- **Visual Diff Monitoring:** Automated detection of government UI layout deviations to protect the maintenance budget.

## B2B SaaS Requirements

### Architecture & Multi-Tenancy
- **Multi-Tenant Model:** Shared-database approach with strict `tenant_id` filtering in the Spring Boot Data layer.
- **Async Ingestor:** Background processing for daily NAV debt updates isolated from user search latency.
- **EU Data Residency:** Infrastructure hosted in GCP Frankfurt or Warsaw regions.

### Access Control (RBAC)
- **Guest:** Transient storage (10 companies), rate-limited search access.
- **SME Admin:** Full CRUD on Watchlist and EPR state.
- **Accountant:** "Context Switching" between client accounts; aggregate portfolio risk view.

### Subscription Management
- **Feature Flags:** Decoupling billing status from capability access (e.g., locking EPR Wizard for Alap tier).
- **SSO Integration:** Minimalist Google and Microsoft Entra ID authentication using read-only profile scopes.

## Functional Requirements

### Partner Screening
- **FR1:** Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers.
- **FR2:** System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék.
- **FR3:** System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts.
- **FR4:** System flags "Suspended Tax Numbers" for manual review.

### Monitoring & Alerts
- **FR5:** Authenticated Users can manage a private Watchlist of partners.
- **FR6:** System monitors Watchlist partners for status changes every 24 hours.
- **FR7:** System triggers Resend email alerts on partner status deviations.

### EPR Compliance
- **FR8:** Users can navigate a multi-step questionnaire for material classification.
- **FR9:** System validates inputs via backend JSON-driven Directed Graph (DAG).
- **FR10:** System generates schema-perfect CSV/XLSX exports for MOHU reporting.

### Administration
- **FR11:** Administrators can monitor scraper success rates via a health dashboard.
- **FR12:** Administrators can update EPR logic via JSON configuration without code redeploys.

## Non-Functional Requirements

### Performance & Scaling
- **NFR1:** 95% of search requests return a verdict in **< 30 seconds**.
- **NFR2:** GraalVM native images ensure container startup times of **< 200ms**.
- **NFR3:** System maintains **Min-Instances: 1** on Cloud Run during Hungarian business hours (8:00-17:00 CET).

### Trust & Legal Integrity
- **NFR4:** Every risk check is recorded with a **SHA-256 Cryptographic Hash** for court-ready due diligence evidence.
- **NFR5:** Encryption at rest (AES-256) and in transit (TLS 1.3).

### SEO & Growth
- **NFR6:** System generates indexable public "Gateway Stubs" for Hungarian companies using JSON-LD Rich Snippets to drive organic traffic.
- **NFR7:** System scales to zero during off-peak hours to minimize solo-developer costs.
