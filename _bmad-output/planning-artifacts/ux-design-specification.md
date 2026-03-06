---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
lastStep: 14
inputDocuments: 
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/prd.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/product-brief-risk_guard-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/market-Hungarian-SME-behavior-and-red-flag-response-research-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/project-context.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/architecture.md"
---

# UX Design Specification risk_guard

**Author:** Andras
**Date:** 2026-03-05

---

## 1. Executive Summary

### 1.1 Project Vision
risk_guard (PartnerRadar) is a high-integrity B2B SaaS platform for Hungarian SMEs and accountants. It provides deterministic partner risk screening, automated EPR compliance, and **court-ready due diligence proof**. The system acts as a "Compliance Vault," allowing users to monitor partner portfolios and generate immutable evidence of risk-mitigation for NAV audits.

### 1.2 Target Users
- **Decision Makers (SME Owners):** Manage portfolios of up to 100 partners. They need mobile-responsive lists with algorithmic prioritization of "Red" (At-Risk) and "Grey" (Unavailable) statuses.
- **Precision Operators (Accountants):** Desktop-first power users managing 40+ client portfolios. They require high-volume efficiency and batch processing.
- **System Admins:** Technical users monitoring scraper success rates and DOM integrity.

---

## 2. Core User Experience

### 2.1 Defining Experience: "The Compliance Pulse"
The defining experience of risk_guard is the **"Risk Pulse Dashboard."** For authenticated users, the interaction begins with a prioritized view of their partner ecosystem. The system transforms from a lookup tool into a passive monitoring shield that only demands attention when a status deviates.

### 2.2 User Mental Model: "Inventory & Monitor"
- **Risk:** "My partners are a list I need to keep green."
- **EPR:** "My packaging is a catalog where I just need to record weights."

### 2.3 Success Criteria
- **Zero-Noise Monitoring:** Alerts only on actual status changes.
- **Effortless EPR:** Generating a MOHU report takes < 2 minutes post-setup.
- **Outage Transparency:** 100% clarity on data age during portal downtime.

---

## 3. Visual Design Foundation: "The Safe Harbor"

### 3.1 Color System
- **Authority (Primary):** Deep Navy (#0F172A)
- **Reliable (Success):** Forest Emerald (#15803D)
- **At-Risk (Danger):** Crimson Alert (#B91C1C)
- **Stale/Outage (Warning):** Amber (#B45309)

### 3.2 Typography
- **Primary:** **Inter** (Sans-Serif) for legibility.
- **Data:** **JetBrains Mono** for Tax IDs and Hashes.

---

## 4. Design Direction: The "Vault" Pivot

### 4.1 Transition Strategy
A sharp visual and functional transition between the public "Gateway" and the private "Professional Workspace."
- **Public:** Airy, horizontal, marketing-focused.
- **Private:** High-density, sidebar-driven, "Sober/Legal" aesthetic.

---

## 5. User Journey Flows

### 5.1 The Morning Risk Pulse
Automated promotion of status changes to the top of the dashboard with one-tap access to the **Audit Proof PDF**.

### 5.2 The EPR Reporting Cycle
Inventory-based weights entry with seasonal template support and "Copy from Previous" logic.

### 5.3 Outage Resilience
Displaying the **Grey Shield** with stale data and explicit timestamps when government sources are offline.

---

## 6. Page & Component Strategy

### 6.1 Critical Components
- **`TheShieldCard`:** Authoritative verdict anchor with integrity hashes.
- **`AuditDispatcher`:** Native mobile share integration for legal proofs.
- **`MaterialInventoryBlock`:** High-speed weight entry grid for EPR.
- **`ContextGuard`:** Safety interstitial for multi-tenant switching.

### 6.2 Page Map
- **Landing Page:** Zero-friction Tax ID search.
- **Risk Pulse Dashboard:** Prioritized partner monitoring.
- **Flight Control:** Accountant's aggregate client view.
- **Mission Control:** Impact-driven scraper health monitoring for admins.

---

## 7. UX Consistency Patterns

### 7.1 Button Hierarchy
- **Primary (Authority):** Deep Navy (#0F172A). For terminal actions (Generate Proof, Submit).
- **Secondary (Utility):** Slate Grey border (#64748B). For management (Add to Watchlist).
- **Tertiary (Evidence):** Borderless Slate. For deep-linking to sources.

### 7.2 Feedback Patterns
- **Integrity Validation (Success):** Emerald Green + SHA-256 Badge.
- **Regulatory Warning (Amber):** Amber Banner for stale data.
- **Immediate Correction (Error):** Crimson Red for validation failures.

### 7.3 Form & Validation
- **The "MOHU Gate":** Real-time weight validation enforcing decimal precision.
- **Intelligent Masking:** Auto-formatting for 8/11 digit Hungarian Tax Numbers.

### 7.4 Loading & Empty States
- **Skeletal Trust:** Layout-mimicking Skeletons during scraper fetches.
- **Interstitial Safety:** Full-screen "Context Guard" during tenant switches.

---

## 8. Responsive Design & Accessibility

### 8.1 Responsive Strategy
risk_guard utilizes a **"Dual-Context"** responsive strategy to serve distinct user behaviors:
- **Mobile (Decision Context):** Optimized for one-handed thumb interaction. Priority is given to the "Shield Verdict" and "Audit Dispatcher." Navigation uses a bottom-sheet pattern for secondary partner data.
- **Desktop (Operation Context):** Optimized for mouse/keyboard efficiency. Priority is given to multi-tenant aggregation, dense data grids, and batch EPR reporting.

### 8.2 Breakpoint Strategy
- **Mobile (< 768px):** Single-column layout; card-based data display; centered primary verdicts.
- **Tablet (768px - 1024px):** Hybrid layout; sidebar collapses to icons; 2-column dashboard grids.
- **Desktop (> 1024px):** Persistent sidebar; multi-column "Quiet Grid" tables; expanded side-panel summaries for EPR.

### 8.3 Accessibility Strategy (WCAG 2.1 AA)
- **Contrast:** Strict 4.5:1 ratio for all metadata; 7:1 for primary verdicts.
- **Redundancy:** All status colors are paired with unique icons (Shield-Check, Shield-X, Shield-Clock).
- **Navigation:** Skip-links for accountants to bypass sidebars; logical tab order through the "MOHU Gate" weight inputs.
- **Screen Readers:** ARIA-live regions for asynchronous scraper status updates.

### 8.4 Testing Strategy
- **Cross-Browser:** Automated CI testing on Chromium (Desktop) and WebKit (Mobile) via Playwright.
- **A11y Audit:** Weekly **Pa11y** or **Axe-core** scans of core user journeys.
- **Real-World Sim:** Performance testing on throttled 3G networks to ensure "Behind-the-Glass" skeletons work effectively.

---

## 9. Implementation Roadmap

### 9.1 Phase 1: Core Vault (MVP)
- Implement `TheShieldCard` and `SearchBar` auto-masking.
- Scaffold multi-tenant `ContextGuard` and Dashboard shell.
- Establish "The Safe Harbor" color tokens in Tailwind.

### 9.2 Phase 2: Compliance Center
- Implement `MaterialInventoryBlock` and seasonal templates.
- Integrate `AuditDispatcher` with native mobile sharing.
- Build "Flight Control" aggregate view for accountants.

### 9.3 Phase 3: Solo-Dev Resilience
- Build "Mission Control" health gauges and MTBF scoring.
- Finalize WCAG 2.1 AA accessibility audit and keyboard mapping.
