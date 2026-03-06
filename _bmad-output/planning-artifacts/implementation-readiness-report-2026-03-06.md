---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
includedFiles:
  prd: prd.md
  architecture: architecture.md
  epics: epics.md
  ux: ux-design-specification.md
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-06
**Project:** risk_guard
**Assessor:** opencode (BMM Implementation Readiness Workflow)

## Step 1: Document Discovery Results

### Discovered Documents
- **PRD:** `prd.md` (8.5K, 2026-03-05)
- **Architecture:** `architecture.md` (79K, 2026-03-05)
- **Epics & Stories:** `epics.md` (21K, 2026-03-05)
- **UX Design:** `ux-design-specification.md` (7.3K, 2026-03-06)

### User Notes
- User identified a potential misalignment: Epics were created before the UX design and may not be 100% in line.

## Step 2: PRD Analysis

### Functional Requirements Extracted

FR1: Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers.
FR2: System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék.
FR3: System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts.
FR4: System flags "Suspended Tax Numbers" for manual review.
FR5: Authenticated Users can manage a private Watchlist of partners.
FR6: System monitors Watchlist partners for status changes every 24 hours.
FR7: System triggers automated email alerts on partner status deviations.
FR8: Users can navigate a multi-step questionnaire for material classification.
FR9: System validates inputs via a backend-driven Directed Graph (DAG).
FR10: System generates schema-perfect CSV/XLSX exports for MOHU reporting.
FR11: Administrators can monitor scraper success rates via a health dashboard.
FR12: Administrators can update EPR logic via JSON configuration without code redeploys.

Total FRs: 12

### Non-Functional Requirements Extracted

NFR1: 95% of search requests return a verdict in < 30 seconds.
NFR2: Native-optimized binaries ensure container startup times of < 200ms.
NFR3: System maintains high-availability during Hungarian business hours (8:00-17:00 CET) with zero cold-starts.
NFR4: Every risk check is recorded with a cryptographic hash for court-ready due diligence evidence.
NFR5: Industry-standard encryption at rest and in transit.
NFR6: System generates indexable public "Gateway Stubs" for Hungarian companies using JSON-LD Rich Snippets to drive organic traffic.
NFR7: System scales to zero during off-peak hours to minimize solo-developer costs.

Total NFRs: 7

### Additional Requirements & Constraints

- **Compliance:** 2026 EPR Law compliance, Liability Protection (disclaimers), GDPR Audit Trail (logging), Accessibility Standards (WCAG 2.1 AA).
- **Technical Integrity:** State-Machine Verdicts (zero fuzzy logic), Suspended Tax Number Detection, Snapshot Traceability (source timestamp/URL), Data Freshness Guard (48h limit).
- **Architecture:** Multi-Tenant Model (`tenant_id` filtering), Async Ingestor for NAV updates, EU Data Residency (GCP Frankfurt/Warsaw).
- **Security & Access:** RBAC (Guest, SME Admin, Accountant), SSO Integration (Google, Microsoft Entra ID), Subscription Management (Feature Flags).
- **Maintenance:** Visual Diff Monitoring for scrapers.

### PRD Completeness Assessment

The PRD is highly structured and provides clear, numbered functional and non-functional requirements. It explicitly addresses domain-specific compliance (EPR, GDPR), technical constraints (state-machine logic, data freshness), and business model needs (multi-tenancy, RBAC). The requirements are specific and measurable (e.g., NFR1: < 30s, NFR2: < 200ms). It appears to be a solid foundation for implementation.

## Step 3: Epic Coverage Validation

### Coverage Matrix

| FR Number | PRD Requirement | Epic Coverage | Status |
| :--- | :--- | :--- | :--- |
| FR1 | Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers. | Epic 2 Story 2.1 | ✓ Covered |
| FR2 | System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék. | Epic 2 Story 2.2 | ✓ Covered |
| FR3 | System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts. | Epic 2 Story 2.3 | ✓ Covered |
| FR4 | System flags "Suspended Tax Numbers" for manual review. | Epic 2 Story 2.3 | ✓ Covered |
| FR5 | Authenticated Users can manage a private Watchlist of partners. | Epic 3 Story 3.1 | ✓ Covered |
| FR6 | System monitors Watchlist partners for status changes every 24 hours. | Epic 3 Story 3.2 | ✓ Covered |
| FR7 | System triggers automated email alerts on partner status deviations. | Epic 3 Story 3.3 | ✓ Covered |
| FR8 | Users can navigate a multi-step questionnaire for material classification. | Epic 4 Story 4.2 | ✓ Covered |
| FR9 | System validates inputs via a backend-driven Directed Graph (DAG). | Epic 4 Story 4.2 | ✓ Covered |
| FR10 | System generates schema-perfect CSV/XLSX exports for MOHU reporting. | Epic 5 Story 5.3 | ✓ Covered |
| FR11 | Administrators can monitor scraper success rates via a health dashboard. | Epic 6 Story 6.1 | ✓ Covered |
| FR12 | Administrators can update EPR logic via JSON configuration without code redeploys. | Epic 6 Story 6.3 | ✓ Covered |

### Missing Requirements

None. All 12 Functional Requirements from the PRD are mapped to specific Epics and Stories.

### Coverage Statistics

- Total PRD FRs: 12
- FRs covered in epics: 12
- Coverage percentage: 100%

## Step 4: UX Alignment Assessment

### UX Document Status

**Found:** `ux-design-specification.md` (7.3K, 2026-03-06)

### Alignment Issues

1. **EPR Reporting Logic:** UX specifies "seasonal template support" and "Copy from Previous" logic for EPR reporting (Journey 5.2). These are missing from Epic 4 and Epic 5, which focus on basic library management and filing.
2. **Audit Proof Dispatcher:** UX requires `AuditDispatcher` with native mobile share integration (e.g., `navigator.share`). Epic 5.1 mentions PDF export but does not explicitly include the mobile sharing requirement.
3. **Outage UI (Grey Shield):** UX defines a "Grey Shield" for source outages/stale data. Architecture ADR-4 supports this tiered model, but the specific "Grey Shield" UI state is not explicitly detailed in Epic 2 (which mentions Emerald, Rose, and Amber for suspended tax numbers, but not the Grey/Stale state).
4. **Accountant Aggregate View:** UX identifies "Flight Control" as a specific aggregate client view page. Epic 3 Story 3.4 covers the "Portfolio Pulse Feed" (alert sidebar), but the full aggregate dashboard page is not explicitly story-pointed.

### Warnings

- **Alignment Lag:** As noted by the user, the UX design was finalized after the Epics. The items listed above (Seasonal Templates, Native Share, Grey Shield UI, Flight Control Page) represent requirements introduced in UX that are currently not represented in the Epic/Story breakdown.
- **Architectural Support:** Architecture already accounts for multi-tenancy, tiered freshness (supporting Grey Shield), and EPR DAGs. The gaps are primarily in the Epic/Story decomposition rather than the technical foundation.

## Step 5: Epic Quality Review

### Structural Quality Findings

#### 🔴 Critical Violations
- **Non-User Focused Stories:** Stories 1.2 ("Multi-Tenant Schema"), 1.5 ("CI/CD Pipeline"), 2.2 ("Scraper Engine"), 2.3 ("Verdict Engine"), and 3.2 ("Monitoring Cycle") are written as "As a System" or "As a Developer". They describe technical implementation rather than user value.
- **Independence Risk:** Epic 1 Story 1.1 ("Monorepo Foundation") is a purely technical setup story with no user-facing output.

#### 🟠 Major Issues
- **Missing Error Paths in ACs:** Acceptance criteria across most stories focus on the "Happy Path" (e.g., "Given a valid tax number..."). Error conditions (e.g., "Given an invalid tax number", "Given a portal outage") are often missing or grouped into high-level "Resilience" ADRs without story-level verification.
- **Technical Jargon in ACs:** Acceptance Criteria frequently reference internal technologies (Flyway, jOOQ, Spring Filter, Resilience4j) which should ideally be abstracted in a user-facing story.

#### 🟡 Minor Concerns
- **Story Sizing:** Stories like 1.5 (CI/CD + Infrastructure) and 2.2 (Parallel Scraper Engine) are quite large and could potentially be split into smaller, more testable units.

### Recommendations

1. **Refactor "System" Stories:** Rewrite technical stories from a User or Admin persona to emphasize the value delivered (e.g., "As a User, I want the system to handle portal outages gracefully so I always know the status of my data").
2. **Backport UX Requirements:** Update Epic 2 (Outage UI), Epic 4 (Seasonal Templates), Epic 5 (Mobile Sharing), and Epic 3 (Flight Control Page) to reflect the UX Design Specification.
3. **Enhance Acceptance Criteria:** Add explicit error-handling and edge-case ACs to all screening and reporting stories.
4. **Project Initialization:** While Story 1.1 is technical, it aligns with Architecture's "Spring Initializr" decision. It should remain as the first story but be clarified as a project kick-off.

## Summary and Recommendations

### Overall Readiness Status

**READY**

### Critical Issues Resolved

- **UX-Epic Synchronization:** The "Grey Shield" (Outage UI), "Seasonal EPR Templates", "Copy from Previous" logic, "Mobile Audit Dispatcher", and "Flight Control Dashboard" have been successfully incorporated into the Epic/Story breakdown (`epics.md`).
- **User-Centric Refactoring:** Technical "System" stories in Epics 1, 2, and 3 have been rewritten to focus on user outcomes.
- **Acceptance Criteria Hardening:** Error paths, localized error messages, and edge-case ACs have been added to all screening and reporting stories.

### Final Note

This assessment confirms that all previously identified planning gaps have been addressed. The project artifacts (PRD, Architecture, UX, and Epics) are now fully aligned and provide a comprehensive, implementation-ready foundation for Phase 4.
