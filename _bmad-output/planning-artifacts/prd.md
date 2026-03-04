---
stepsCompleted: ["step-01-init", "step-02-discovery", "step-03-success", "step-04-journeys", "step-05-domain", "step-06-innovation", "step-07-project-type", "step-08-scoping", "step-09-functional"]
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

## Success Criteria

### User Success
- **The "Zero-Edit" EPR Export:** 100% of users generate a MOHU-ready export that passes local schema validation (XLS/XML) before download. This is achieved via a **Semantic KF-Code Constructor** ensuring legal validity by construction.
- **The "Accountant Validation" moment:** 100% match between the app's State-Machine Verdict and professional bookkeeper manual assessments for all "Golden Case" scenarios.
- **Completion Time:** < 30 seconds from input to a full deterministic verdict.
- **Strict Integrity UX:** In the event of government portal failure, the system shows "Data Temporarily Unavailable" rather than providing estimated or uncertain results.

### Business Success
- **High-Retention Target:** 90% return rate for subsequent quarterly EPR reporting.
- **Conversion Efficiency:** > 15% conversion from the 10-company/3-check demo limit to full paid subscriptions.
- **Operational Sustainability:** Solo-dev maintenance budget < 10 hours/week, enabled by **Visual Diff Monitoring** for government UI changes.

### Technical Success
- **Deterministic Integrity:** 100% adherence to a **"Golden Case" Regression Suite** using immutable gov-data snapshots.
- **Edge-Case Coverage:** Successful detection of "Suspended Tax Numbers" (Walking Dead status) and Mass-Registration addresses.
- **Scraper Health:** > 98% successful retrieval rate across NAV and e-Cégjegyzék portals without manual code fixes.

## Product Scope

### MVP - Minimum Viable Product (The 30-Day Shield)
- **Deterministic State-Machine Engine:** factual retrieval and state-logic processing of NAV Debt, Insolvency, and Tax Number Status.
- **Strict-Integrity Verdict Dashboard:** A clear, mobile-optimized UI providing binary "Reliable/At-Risk" statuses based on factual records.
- **The Watchlist:** Automated monitoring for a set list of partners with email triggers (Resend/SendGrid) for status changes.
- **JSON-Driven EPR Wizard:** A dynamic, questionnaire-based engine for material classification, allowing for regulatory updates via data changes rather than code.
- **EPR Export:** Generation of MOHU-ready CSV/PDF/XLS files based on the Semantic KF-Code Constructor.
- **Demo Mode:** Guest users can upload 10 companies and trigger 3 instant checks per day to validate the tool's utility.

### Growth Features (Post-Launch v1.1)
- **AI-Enhanced Narrative:** Integration of OpenAI o3/GPT-5 to generate human-readable Hungarian summaries explaining the "Why" behind a verdict.
- **Accountant Pro-View:** Multi-client management dashboard for external bookkeepers to manage client portfolios at scale.
- **Bulk Import:** mass-uploading of partner lists via CSV/Excel.
- **AI Self-Healing Scrapers:** Automated detection and repair proposal for broken CSS selectors using LLM-driven DOM analysis.

### Vision (The Financial OS for SMEs)
- **Predictive Anomaly Detection:** Identifying "hidden" financial risks in partner data trends before they become public defaults.
- **Automated ESG Reporting:** Holistic compliance mapping for SME supply chains.
- **Supply Chain Ownership Mapping:** Visualizing ownership webs ("Cégháló") to detect systemic risks.

---

## User Journeys

### Journey 1: Gábor's "Midnight Risk Check" (The Primary Success Path)
- **Opening Scene:** It's 10:00 PM. Gábor is finalizing a 10M HUF contract. He opens PartnerRadar on his phone.
- **Rising Action:** He enters the partner's tax number. The system performs a real-time state-logic check.
- **Climax:** The screen shows a **Red Shield**: "AT RISK - NAV Debt > 180 Days detected."
- **Resolution:** Gábor requests 100% prepayment. The product just saved his cash flow.

### Journey 2: Judit's "Quarterly EPR Sprint" (The Accountant Efficiency Path)
- **Opening Scene:** Judit has 40 clients who need EPR reporting. She dreads the manual KF-code mapping.
- **Rising Action:** She logs into the "Pro View," selects a client, and enters production weights into the JSON Wizard.
- **Climax:** One click on "Export for MOHU." She gets a schema-perfect CSV.
- **Resolution:** She uploads the file to the gov portal. It shows "Validated - No errors." She finishes in 5 minutes instead of 45.

### Journey 3: The "Broken Gatekeeper" (The Error Recovery Path)
- **Opening Scene:** Gábor tries to check a company while the NAV portal is down for maintenance.
- **Climax:** The UI clearly states: "Data Temporarily Unavailable - Gov Portal is Down."
- **Resolution:** The app offers a "Monitor and Alert me when portal returns" button. Gábor feels the system is "on it."

### Journey Requirements Summary
- **High-Availability Landing Page:** Optimized for mobile tax-number entry.
- **State-Logic Engine:** Decoupled risk assessment logic.
- **Semantic KF-Code Constructor:** Internal graph-based validation for EPR codes.
- **Last-Mile Export Service:** Generation of MOHU-ready files with a built-in "Submission Guide."
- **Persistence & Alerting:** Reliable tracking of status changes and notification triggers.

---

## Domain-Specific Requirements

### Compliance & Regulatory
- **Hungarian EPR Law:** Strict adherence to 2026 material category mapping.
- **GDPR & Privacy:** Full audit logging of all partner searches to comply with Hungarian "Jogos érdek" (Legitimate Interest) data processing rules.
- **Information Provider Disclaimer:** A standard "No Advice" and "Third-Party Source" disclaimer displayed on every report.
    - *Text:* "A szolgáltatott adatok tájékoztató jellegűek. Az adatok hivatalos állami forrásokból (NAV, e-Cégjegyzék) származnak; a PartnerRadar nem vállal felelősséget a forrásadatok esetleges pontatlanságáért vagy az ezek alapján meghozott üzleti döntésekért."

### Technical Constraints
- **State-Machine Verdicts:** Risk assessment must be a closed-loop state machine with no "fuzzy" logic for core status.
- **Suspended Tax Number Handling:** Detection of `TAX_SUSPENDED` flag from NAV, triggering a "Manual Review Required" state.
- **Source-to-Snapshot Traceability:** Display timestamp and official source URL for every data point.

### Integration Requirements
- **NAV Open Data Ingestor:** Daily processing of bulk JSON/CSV debt lists.
- **MOHU Schema Validation:** Internal XSD validation for all EPR exports.

### Risk Mitigations
- **Liability Shield:** "Informational Purpose Only" clause in Terms of Service.
- **Data Freshness Guard:** Auto-shift to "Unavailable" status if source data age > 48 hours.

---

## Innovation & Novel Patterns

### Detected Innovation Areas
- **Policy-as-Code EPR Wizard:** A "Safe-by-Construction" Directed Acyclic Graph (DAG) for generating Hungarian KF-Codes.
- **Human-in-the-Loop Scraper Repair (v1.1):** AI-driven auto-triage of broken scrapers. The system captures HTML screenshots/snippets and sends a one-click repair proposal to the developer via mobile notification.
- **Binary Trust Architecture:** Rejection of "Confidence Scores" in favor of absolute factual integrity, ensuring Judit's professional reputation is protected.

### Market Context & Competitive Landscape
- **Deterministic Disruption:** Competitors (Opten/Bisnode) focus on data volume; risk_guard focuses on **Verdict Clarity**.

### Validation Approach
- **The "Golden Case" Regression Suite:** Continuous verification against immutable gov-data snapshots to ensure logic stability.
- **Visual Diff Observability:** Automated detection of government UI layout deviations.

---

## B2B SaaS Specific Requirements

### Project-Type Overview
risk_guard is a multi-tenant B2B SaaS platform utilizing a modular monolithic backend to provide partner screening and regulatory compliance as a service.

### Technical Architecture Considerations
- **Multi-Tenant Model:** Shared-database approach with strict `tenant_id` filtering in the Spring Boot Data layer to ensure Judit cannot see Gábor's partners unless authorized.
- **Scalable Ingestor:** Background processing for daily NAV debt list ingestion using Spring Batch to prevent performance degradation during business hours.

### Role-Based Access Control (RBAC)
- **Guest:** Read-only access to search; transient storage for 10 companies; rate-limited.
- **SME User:** Full CRUD on private Watchlist; access to personal EPR Wizard.
- **Pro Accountant:** Ability to "Switch Context" between different client SME accounts; bulk dashboard view of client risks.

### Subscription & Feature Management
- **Feature Flags:** Decoupling subscription logic from code. The "EPR Wizard" and "Monitoring Alerts" are enabled based on the user's billing status in the `user_profile` table.

### Data Compliance & Integration
- **EU Data Residency:** All GCP resources (Cloud Run, Cloud SQL) must be hosted in the `europe-west3` (Frankfurt) or `europe-central2` (Warsaw) regions.
- **External Webhooks:** Pre-configured integration for Resend (Email) and Stripe (Payment status).

---

## Project Scoping & Phased Development (Optimized)

### MVP Strategy & Philosophy
- **MVP Approach:** Problem-Solving Vertical Slice. Delivering a shippable "Tax Status" unit by Week 2, followed by the "EPR Wizard" by Week 4.
- **Resource Hack:** Solo Staff Engineer utilizing **HTMX + Spring Boot** to minimize frontend/backend integration overhead.

### MVP Feature Set (Phase 1: 4-Week Sprint)
- **Week 1-2 (Foundational):** NAV Ingestor, Deterministic State Machine, and "Verdict" UI.
- **Week 3 (EPR Core):** JSON-Driven Linear Wizard for the Top-20 most common KF-Codes.
- **Week 4 (UX & Demo):** Guest Demo Mode (Rate-limited), High-Fidelity CSV/XLS Export, and Submission Guide.

**Must-Have Capabilities:**
- Factual retrieval: NAV Debt, Insolvency, and Suspended Tax Numbers.
- Strict-Integrity Verdict UI (Reliable/At-Risk).
- Top-20 common KF-Code Constructor (80/20 compliance rule).
- Schema-perfect CSV/XLS Export for MOHU reporting.

### Out of Scope for MVP (Moved to Phase 2)
- **MOHU XML Generation:** Pivot to Excel-only for faster launch.
- **Visual DAG Interface:** Replaced with a backend-driven linear sequence.
- **Advanced AI Summaries:** Moved to post-launch growth phase.

### Risk Mitigation Strategy (Solo Dev)
- **Vertical Slice Security:** If Week 3/4 stalls, the product launches as a high-value "Tax Risk Shield."
- **Paretean Logic:** Ensuring 100% deterministic accuracy for the most common compliance cases rather than "maybe" accuracy for all.
- **Architecture Guardrails:** Utilizing `ArchUnit` to maintain the 100% deterministic mandate during fast-paced solo development.

---

## Functional Requirements

### Partner Screening & Risk Assessment
- **FR1:** Users can search for Hungarian partners using a valid 8-digit or 11-digit Tax Number (Adószám).
- **FR2:** The system can retrieve factual debt and enforcement status from the NAV Open Data Portal.
- **FR3:** The system can retrieve basic company registration details (Alapadatok) from the e-Cégjegyzék portal.
- **FR4:** The system can detect "Suspended Tax Number" status and flag it for manual review.
- **FR5:** The system can execute a deterministic state-machine check to assign a binary "Reliable/At-Risk" status.
- **FR6:** The system can provide a timestamped "Data Source Snapshot" for every search result.
- **FR7:** The system can display a legally compliant informational disclaimer on all partner reports.

### Partner Monitoring (The Watchlist)
- **FR8:** Authenticated Users can add partners to a private "Watchlist."
- **FR9:** The system can monitor partners on the Watchlist for status changes every 24 hours.
- **FR10:** The system can trigger automated email alerts when a monitored partner's status changes.
- **FR11:** Users can view a dashboard summarizing the current health of their entire Watchlist.

### EPR Compliance Wizard
- **FR12:** Users can navigate a linear multi-step questionnaire to classify packaging and waste materials.
- **FR13:** The system can validate material classification using a backend JSON-driven Directed Graph (DAG).
- **FR14:** The system can map user inputs to legally valid Hungarian KF-Codes (Semantic Constructor).
- **FR15:** Users can input production weights for each material category.
- **FR16:** The system can generate a MOHU-ready CSV and XLSX export file.
- **FR17:** The system can display a "Submission Guide" with step-by-step instructions for government portal uploads.

### Multi-Tenancy & User Management
- **FR18:** The system can isolate user data using a shared-database multi-tenant model.
- **FR19:** Pro Accountants can manage and switch between multiple SME client accounts.
- **FR20:** Guest Users can perform a limited number of partner checks (Rate-limited).
- **FR21:** Guest Users can upload a small sample list (max 10) of partners to the temporary watchlist.

### System Administration & Observability
- **FR22:** Administrators (Andras) can monitor gov-portal scraper success rates via a health dashboard.
- **FR23:** The system can trigger "Visual Diff" alerts when government UI layouts change significantly.
- **FR24:** Administrators can update the EPR logic by modifying a central JSON configuration file without code changes.

