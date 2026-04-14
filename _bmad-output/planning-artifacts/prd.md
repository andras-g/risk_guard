---
stepsCompleted: ["step-01-init", "step-02-discovery", "step-03-success", "step-04-journeys", "step-05-domain", "step-06-innovation", "step-07-project-type", "step-08-scoping", "step-09-functional", "step-10-nonfunctional", "step-11-polish", "edit-2026-04-14-CP-5-product-packaging-registry"]
edits:
  - date: "2026-04-14"
    trigger: "Sprint Change Proposal CP-5 (sprint-change-proposal-2026-04-14.md)"
    task: "task-02-prd-update.md"
    scope: "Additive — ICP clarification, differentiator framing, 4 new FRs (FR13–FR16 Product-Packaging Registry), 2 new NFRs (NFR8 PPWR forward-compat, NFR9 AI guardrails), compliance section expanded with 80/2023 Annex 3.1+4.1+1.2 legal basis and OKIRkapu XML submission channel correction (supersedes CP-5 §8 CSV assumption per Task 01 research)."
    preserved: "All existing FR1–FR12 and NFR1–NFR7 retained verbatim."
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

### Ideal Customer Profile
risk_guard targets **every Hungarian KKV (SME)**, with the **majority being manufacturers and importers** who place packaged products on the Hungarian market. Invoice-driven EPR auto-fill alone is insufficient for this majority segment: their NAV invoices list *packaged products*, not packaging materials. They require a **per-product packaging registry (csomagolási nyilvántartás)** mandated by 80/2023. (III. 14.) Korm. rendelet as the join table between invoice quantities and EPR-reportable material masses. A minority segment — companies whose product *is* packaging material (paper-bag distributors, PET bottle sellers) — remains fully served by the invoice-driven path alone.

### Key Differentiator
**Invoice-native auto-mapping** is risk_guard's unique market wedge. No EU EPR scheme (LUCID, CITEO) and no Hungarian competitor (notably Körforgó, which requires manual/Excel/API entry of sales data) ingests NAV Online Számla data natively. risk_guard combines the invoice-driven bootstrap with per-SKU registry and AI-assisted KF-code classification to deliver the shortest onboarding path to a compliant quarterly submission on the Hungarian market.

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
- **Watchlist:** Automated 24h monitoring for assigned partners with automated email alerts.
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
- **Product-Packaging Registry Legal Basis:** Per-tenant packaging registry is grounded in **80/2023. (III. 14.) Korm. rendelet Annex 3 point 1** (producer registration identity), **Annex 4 point 1** (per-transaction operational reporting: megnevezés, mennyiség [kg], eredet, KF kód, átvállalási nyilatkozat), and **Annex 1 point 2** (KF code 8-character structure).
- **Submission Channel:** EPR quarterly data service `KG:KGYF-NÉ` is submitted as **XML (validated against the published XSD) to OKIRkapu** (`kapu.okir.hu`) — not directly to MOHU, and not in CSV form. The authority forwards aggregated data to MOHU; MOHU issues the invoice. Deadline: 20th of the month following the reporting quarter.
- **Liability Protection:** Every report displays an "Informational Purpose Only" disclaimer clarifying third-party source origins.
- **GDPR Audit Trail:** Full logging of partner searches to support "Legitimate Interest" data processing.
- **Accessibility Standards:** 100% compliance with WCAG 2.1 AA for all public-facing and SME-admin interfaces, ensuring GovTech compatibility.

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
- **FR7:** System triggers automated email alerts on partner status deviations.

### EPR Compliance
- **FR8:** Users can navigate a multi-step questionnaire for material classification.
- **FR9:** System validates inputs via a backend-driven Directed Graph (DAG).
- **FR10:** System generates schema-perfect CSV/XLSX exports for MOHU reporting.

### Administration
- **FR11:** Administrators can monitor scraper success rates via a health dashboard.
- **FR12:** Administrators can update EPR logic via JSON configuration without code redeploys.

### Product-Packaging Registry (Termék-Csomagolás Nyilvántartás)
- **FR13:** Tenants can maintain a per-tenant Product-Packaging Registry with full CRUD. Each product entry supports a multi-component packaging bill-of-materials (1..N components), and each component captures material description, 8-character KF code, weight per unit (kg), component order, and nullable PPWR-forward fields (recyclability grade, recycled content %, reusable flag, substances of concern, supplier declaration reference). Every field change is recorded in a registry audit log with source tag (`MANUAL` / `AI_SUGGESTED_CONFIRMED` / `AI_SUGGESTED_EDITED` / `VTSZ_FALLBACK` / `NAV_BOOTSTRAP`).
- **FR14:** System can bootstrap an empty registry from the tenant's NAV Online Számla history. On demand (or on first-time registry setup), the system pulls 6–12 months of outbound invoices, deduplicates line items by (product name, VTSZ), ranks by frequency and quantity, and presents candidates in a triage queue with states `PENDING` / `APPROVED` / `REJECTED_NOT_OWN_PACKAGING` / `NEEDS_MANUAL_ENTRY`. Triage persists across sessions; rejected items are retained for audit.
- **FR15:** System provides AI-assisted KF-code classification. For each registry candidate or manual product entry, the classifier suggests KF code(s) and likely packaging components with a confidence signal. Primary implementation is Gemini 2.5 Flash via Vertex AI (pinned to EU region, workload-identity authenticated). Below the confidence threshold, or when the AI path is unavailable, the router falls back to a VTSZ-prefix matcher (reusing Story 8.3's existing logic); if both fail, the field is left empty for manual entry. **User confirmation is mandatory before any suggestion is persisted**; suggestions are never silently applied. All AI interactions are tagged with model version and timestamp in the audit trail.
- **FR16:** System generates the tenant's quarterly EPR submission as a **schema-valid OKIRkapu XML file (`KG:KGYF-NÉ`)**. The exporter pulls outbound invoices for the reporting period, multiplies invoiced units by registry component weights where the product is registered, falls back to VTSZ-prefix matching where it is not, aggregates per KF code (kg, 3 decimals), and emits XML validated against the published XSD catalog. The export surfaces per-line provenance (registry match / VTSZ fallback / unmatched), and flags unmatched lines for registration. The report target is accessed via a pluggable interface (`EprReportTarget`) so future targets (EU single registry post-2029) can be added without touching `EprService`. The existing FR10 CSV/XLS export remains available as a human-readable summary artifact alongside the authoritative XML.

## Non-Functional Requirements

### Performance & Scaling
- **NFR1:** 95% of search requests return a verdict in **< 30 seconds**.
- **NFR2:** Native-optimized binaries ensure container startup times of **< 200ms**.
- **NFR3:** System maintains high-availability during Hungarian business hours (8:00-17:00 CET) with zero cold-starts.

### Trust & Legal Integrity
- **NFR4:** Every risk check is recorded with a cryptographic hash for court-ready due diligence evidence.
- **NFR5:** Industry-standard encryption at rest and in transit.

### SEO & Growth
- **NFR6:** System generates indexable public "Gateway Stubs" for Hungarian companies using JSON-LD Rich Snippets to drive organic traffic.
- **NFR7:** System scales to zero during off-peak hours to minimize solo-developer costs.

### Regulatory Forward-Compatibility
- **NFR8:** The packaging data model must be designed for **PPWR (EU Regulation 2025/40) forward-compatibility** from day one, so that national-to-EU registry migration (single EU producer registry from 2029; eco-modulated fees mandatory from 2030) requires no schema rewrite. Concretely: (a) packaging is stored at SKU/component granularity — aggregation occurs at report-generation time, never at storage time; (b) the KF code is one classifier among many — nullable PPWR fields (`recyclability_grade`, `recycled_content_pct`, `reusable`, `substances_of_concern`) are present in schema from first release even if unpopulated; (c) the EPR report target is pluggable via the `EprReportTarget` interface — no service treats MOHU/OKIRkapu as the system of record; (d) fee modulation rules are loaded as data (config/DB), not branched in code; (e) `supplier_declaration_ref` provenance is captured from day one to support future chain-of-custody evidence.
- **NFR9:** AI-assisted classification must operate within explicit guardrails: confidence-threshold gating below which no suggestion is surfaced; circuit breaker around the AI strategy; per-tenant monthly call cap; EU-region endpoint pinning for GDPR/MOHU audit posture; graceful degradation to VTSZ-prefix fallback on any AI outage so user workflows are never blocked. Per-tenant AI spend must be visible to platform administrators.
