---
stepsCompleted: ["step-01-init", "step-02-discovery", "step-03-success", "step-04-journeys"]
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

