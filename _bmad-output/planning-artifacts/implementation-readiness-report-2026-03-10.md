# Implementation Readiness Assessment Report

**Date:** 2026-03-10
**Project:** risk_guard

---

## stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]

---

## Step 1: Document Inventory

### Documents Selected for Assessment

| Document Type | Primary File | Supporting Files |
|---|---|---|
| PRD | `prd.md` (8,625 bytes, 2026-03-05) | `prd-validation-report.md` |
| Architecture | `architecture.md` (80,244 bytes, 2026-03-05) | `architecture-validation-report.md` |
| Epics & Stories | `epics.md` (28,228 bytes, 2026-03-10) | `future-epics.md` |
| UX Design | `ux-design-specification.md` (7,451 bytes, 2026-03-06) | — |

### Discovery Results

- **Duplicates Found:** None
- **Missing Documents:** None
- **Conflicts:** None
- **Status:** ✅ All required documents present and accounted for

---

## Step 2: PRD Analysis

### Functional Requirements

| ID | Requirement |
|---|---|
| FR1 | Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers. |
| FR2 | System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék. |
| FR3 | System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts. |
| FR4 | System flags "Suspended Tax Numbers" for manual review. |
| FR5 | Authenticated Users can manage a private Watchlist of partners. |
| FR6 | System monitors Watchlist partners for status changes every 24 hours. |
| FR7 | System triggers automated email alerts on partner status deviations. |
| FR8 | Users can navigate a multi-step questionnaire for material classification. |
| FR9 | System validates inputs via a backend-driven Directed Graph (DAG). |
| FR10 | System generates schema-perfect CSV/XLSX exports for MOHU reporting. |
| FR11 | Administrators can monitor scraper success rates via a health dashboard. |
| FR12 | Administrators can update EPR logic via JSON configuration without code redeploys. |

**Total FRs: 12**

### Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR1 | 95% of search requests return a verdict in < 30 seconds. |
| NFR2 | Native-optimized binaries ensure container startup times of < 200ms. |
| NFR3 | System maintains high-availability during Hungarian business hours (8:00-17:00 CET) with zero cold-starts. |
| NFR4 | Every risk check is recorded with a cryptographic hash for court-ready due diligence evidence. |
| NFR5 | Industry-standard encryption at rest and in transit. |
| NFR6 | System generates indexable public "Gateway Stubs" for Hungarian companies using JSON-LD Rich Snippets to drive organic traffic. |
| NFR7 | System scales to zero during off-peak hours to minimize solo-developer costs. |

**Total NFRs: 7**

### Additional Requirements (Unlabeled in PRD)

1. Multi-Tenant Model: Shared-database with strict `tenant_id` filtering in Spring Boot Data layer.
2. Async Ingestor: Background processing for daily NAV debt updates isolated from user search latency.
3. EU Data Residency: Infrastructure hosted in GCP Frankfurt or Warsaw regions.
4. RBAC Roles: Guest (transient, rate-limited), SME Admin (full CRUD), Accountant (context switching, portfolio view).
5. Feature Flags: Decoupling billing status from capability access.
6. SSO Integration: Google and Microsoft Entra ID authentication (read-only profile scopes).
7. 2026 EPR Law Compliance: Precise material category mapping and weight reporting.
8. Liability Disclaimer: "Informational Purpose Only" on every report.
9. GDPR Audit Trail: Full logging of partner searches for "Legitimate Interest" processing.
10. WCAG 2.1 AA Accessibility: 100% compliance for all public-facing and SME-admin interfaces.
11. Snapshot Traceability: Display source timestamp and official server URL for every search result.
12. Data Freshness Guard: Auto-shift to "Unavailable" if source data age exceeds 48 hours.
13. Visual Diff Monitoring: Automated detection of government UI layout deviations.
14. Demo Mode: Guest searches limited to 10 companies and 3 instant checks per day.

### PRD Completeness Assessment

- The PRD is well-structured with clearly numbered FR and NFR sections.
- 12 Functional Requirements and 7 Non-Functional Requirements are explicitly labeled.
- 14 additional requirements are embedded in Domain-Specific, B2B SaaS, and Scope sections that will need to be validated against epic coverage.
- Success Criteria provide measurable targets for validation.

---

## Step 3: Epic Coverage Validation

### Coverage Matrix

| FR | PRD Requirement | Epic Coverage | Status |
|---|---|---|---|
| FR1 | Users can search partners via 8-digit or 11-digit Hungarian Tax Numbers | Epic 2, Story 2.1 | ✅ Covered |
| FR2 | System retrieves factual status from NAV Open Data and registration data from e-Cégjegyzék | Epic 2, Story 2.2 | ✅ Covered |
| FR3 | System executes deterministic state-machine check for "Reliable/At-Risk" binary verdicts | Epic 2, Story 2.3 | ✅ Covered |
| FR4 | System flags "Suspended Tax Numbers" for manual review | Epic 2, Story 2.3/2.4 | ✅ Covered |
| FR5 | Authenticated Users can manage a private Watchlist of partners | Epic 3, Story 3.1 | ✅ Covered |
| FR6 | System monitors Watchlist partners for status changes every 24 hours | Epic 3, Story 3.2 | ✅ Covered |
| FR7 | System triggers automated email alerts on partner status deviations | Epic 3, Story 3.3 | ✅ Covered |
| FR8 | Users can navigate a multi-step questionnaire for material classification | Epic 4, Story 4.2 | ✅ Covered |
| FR9 | System validates inputs via a backend-driven Directed Graph (DAG) | Epic 4, Story 4.2 | ✅ Covered |
| FR10 | System generates schema-perfect CSV/XLSX exports for MOHU reporting | Epic 5, Story 5.3 | ✅ Covered |
| FR11 | Administrators can monitor scraper success rates via a health dashboard | Epic 6, Story 6.1 | ✅ Covered |
| FR12 | Administrators can update EPR logic via JSON configuration without code redeploys | Epic 6, Story 6.3 | ✅ Covered |

### Missing Requirements

**No PRD Functional Requirements are missing from the epics.** All 12 FRs have traceable coverage.

### Scope Expansion (FRs in Epics Not in PRD)

- **FR13:** Users can save and manage material templates in a personal "EPR Library" — New in epics
- **FR14:** Users can generate a bulk PDF export of Watchlist status with SHA-256 hashes — New in epics

### Coverage Statistics

- **Total PRD FRs:** 12
- **FRs covered in epics:** 12
- **Coverage percentage:** 100%
- **Additional FRs in epics (not in PRD):** 2 (FR13, FR14)

---

## Step 4: UX Alignment Assessment

### UX Document Status

✅ Found: `ux-design-specification.md` (7,451 bytes, 2026-03-06)

### UX ↔ PRD Alignment

| UX Spec Element | PRD Requirement | Status |
|---|---|---|
| Shield Verdict Cards (Reliable/At-Risk/Stale) | FR3 (binary verdicts) | ✅ Aligned |
| Skeleton Screen animations | Journey 3 (Error Recovery) | ✅ Aligned |
| Provenance Sidebar | Domain Req (Snapshot Traceability) | ✅ Aligned |
| MaterialInventoryBlock | FR8/FR9 (EPR Wizard) | ✅ Aligned |
| AuditDispatcher mobile share | NFR4 (cryptographic hash evidence) | ✅ Aligned |
| Context Switcher for accountants | RBAC (Accountant role) | ✅ Aligned |
| WCAG 2.1 AA compliance | Domain Req (WCAG 2.1 AA) | ✅ Aligned |
| Grey Shield for outages | Journey 3 / Data Freshness Guard | ✅ Aligned |
| Landing page zero-friction search | Demo Mode | ✅ Aligned |

### UX ↔ Architecture Alignment

| UX Requirement | Architecture Support | Status |
|---|---|---|
| PrimeVue 4 components | Specified in Architecture | ✅ Aligned |
| Tailwind CSS tokens | Specified in Architecture | ✅ Aligned |
| Nuxt 3 hybrid rendering | Specified in Architecture | ✅ Aligned |
| Responsive breakpoints | Tailwind mobile-responsive | ✅ Aligned |
| ARIA-live regions | PrimeVue WCAG compliance | ✅ Aligned |
| Playwright cross-browser testing | Architecture CI pipeline | ✅ Aligned |

### Warnings

1. ⚠️ **NFR6 (SEO Gateway Stubs):** UX Spec does not detail JSON-LD structure. Architecture covers it but epics have no dedicated story for SEO stub implementation.
2. ⚠️ **Demo Mode Rate-Limit UX:** PRD specifies guest limits (10 companies, 3 checks/day). Architecture has `guest_sessions` table. UX Spec and epics lack explicit rate-limit feedback UX (what the user sees when hitting the limit).
3. ⚠️ **Visual Diff Monitoring Admin UI:** PRD mentions it, architecture has `dom_fingerprints` table, but no specific story covers presenting visual diff results to admins (partially covered by Story 6.1 scraper health).

---

## Step 5: Epic Quality Review

### Epic Structure Validation

#### User Value Focus

| Epic | User-Centric? | Notes |
|---|---|---|
| Epic 1: Identity, Multi-Tenancy & Foundation | ⚠️ Borderline | "Foundation" + Story 1.1 is pure technical setup |
| Epic 2: The Partner Risk Radar | ✅ Yes | Clear user value |
| Epic 3: Automated Monitoring & Alerts | ✅ Yes | Clear user value |
| Epic 4: EPR Material Library & Questionnaire | ✅ Yes | Clear user value |
| Epic 5: Compliance Reporting & Exports | ✅ Yes | Clear user value |
| Epic 6: System Administration & Integrity | ✅ Yes | Admin persona user value |

#### Epic Independence

All epics are independently functional with forward-only dependencies. No circular dependencies found. Dependency chain: Epic 1 → Epic 2 → Epic 3; Epic 1 → Epic 4 → Epic 5; Epic 1+2 → Epic 6.

### Violations Found

#### Critical Violations

None that block implementation. Stories 1.1 and 2.0 are pure technical/infra stories, but acceptable in greenfield context.

#### Major Issues

1. **Story 3.0 is EPIC-sized:** Bundles 10+ deliverables (Tailwind tokens, typography, app shell, sidebar, responsive breakpoints, button hierarchy, feedback patterns, landing page, WCAG baseline, skeleton components). Should be split into 3+ stories.
2. **Missing NFR stories:** NFR5 (encryption), NFR6 (SEO Gateway Stubs), NFR3 (min-instances) have no dedicated stories or explicit ACs.
3. **PRD requirements without story coverage:**
   - Async Ingestor (daily NAV background updates) — no story
   - Feature Flags (tier gating) — no story
   - Demo Mode (guest rate limiting) — no story
   - Liability Disclaimer on verdict display — only on PDF export (Story 5.1)
   - i18n infrastructure setup — no dedicated story

#### Minor Concerns

1. Duplicate FR2 in Coverage Map (formatting error)
2. Story 2.6 (Email/Password Registration) placed in Epic 2 instead of Epic 1
3. Several stories missing error/failure ACs (1.1, 1.5, 2.0, 2.5, 4.4, 5.3, 6.4)

### Acceptance Criteria Quality

- 85% of stories use proper Given/When/Then BDD format
- Most stories include error handling scenarios
- Story 3.0 ACs are comprehensive but story scope is too large
- Database tables appear to be created when first needed (no upfront anti-pattern)

### Best Practices Compliance Summary

| Check | Status |
|---|---|
| Epics deliver user value | ✅ 5/6 clear, 1 borderline |
| Epic independence maintained | ✅ All pass |
| Stories appropriately sized | ⚠️ Story 3.0 oversized |
| No forward dependencies | ✅ All pass |
| Database tables created when needed | ✅ All pass |
| Clear acceptance criteria | ✅ 85% good quality |
| FR traceability maintained | ✅ 100% PRD FRs traced |

---

## Step 6: Summary and Recommendations

### Overall Readiness Status

## ✅ READY — with recommended improvements

The risk_guard project artifacts are in strong shape for implementation. All 12 PRD Functional Requirements have 100% traceable coverage in the epics. The PRD, Architecture, UX Spec, and Epics are well-aligned with no critical blockers. The issues identified are improvements that will strengthen implementation quality but do not prevent starting Phase 4.

### Issue Summary

| Severity | Count | Description |
|---|---|---|
| 🔴 Critical | 0 | No blockers found |
| 🟠 Major | 3 | Story sizing, missing NFR stories, uncovered PRD requirements |
| 🟡 Minor | 6 | Formatting, story placement, missing error ACs |
| ⚠️ Warnings | 3 | UX alignment gaps (SEO stubs, demo mode UX, visual diff UI) |

### Critical Issues Requiring Immediate Action

**None.** No critical blockers prevent implementation from starting.

### Recommended Improvements Before or During Implementation

1. **Split Story 3.0** into 3 separate stories: (a) Design System Tokens & App Shell, (b) Public Landing Page, (c) WCAG Accessibility Foundation. The current scope is too large for a single story and will be difficult to estimate, review, and complete incrementally.

2. **Add missing stories for uncovered PRD requirements:**
   - **Demo Mode / Guest Rate Limiting:** Add a story covering the guest experience (10 companies, 3 checks/day) and what the user sees when hitting limits.
   - **Feature Flags / Tier Gating:** Add a story covering the `useTierGate` composable and how billing status gates feature access.
   - **Liability Disclaimer on Verdict Display:** Expand Story 2.4 ACs or add a cross-cutting AC to ensure "Informational Purpose Only" appears on all verdict views, not just PDF exports.
   - **i18n Infrastructure:** Add a story or expand Story 1.1/3.0 to cover Hungarian primary / English fallback setup.

3. **Add explicit NFR coverage:**
   - **NFR5 (Encryption):** Add AC to Story 1.5 or 1.2 confirming AES-256 at rest and TLS 1.3 in transit.
   - **NFR6 (SEO Gateway Stubs):** Add a story for implementing `pages/company/[taxNumber].vue` with JSON-LD Rich Snippets. Architecture supports it but no story implements it.
   - **NFR3 (Min-Instances):** Add AC to Story 1.5 confirming Cloud Run min-instances: 1 during business hours.

4. **Move Story 2.6 (Email/Password Registration)** to Epic 1, or document the rationale for deferring fallback auth to Epic 2.

5. **Add error/failure ACs** to Stories 1.1, 1.5, 2.0, 2.5, 4.4, 5.3, and 6.4 for completeness.

6. **Fix duplicate FR2** entry in the epics FR Coverage Map (minor formatting).

### Strengths Noted

- **100% FR coverage** — Every PRD functional requirement is traceable to a specific epic and story.
- **Excellent PRD-UX-Architecture alignment** — The three documents reinforce each other with minimal contradictions.
- **Strong acceptance criteria** — 85% of stories have proper BDD format with error handling.
- **Clean epic dependency chain** — No circular dependencies, forward-only progression.
- **Scope-appropriate additions** — FR13 (Material Library) and FR14 (Watchlist PDF Export) are logical enhancements that strengthen the product.
- **Database design is incremental** — Tables created when first needed, not upfront.

### Final Note

This assessment identified **12 issues** across **3 severity categories**. The planning artifacts demonstrate a high level of maturity and alignment. The BMad Master recommends addressing the 3 major issues (Story 3.0 splitting, missing stories, NFR coverage) to strengthen implementation readiness, but confirms the project **is ready to proceed to Phase 4 implementation**.

---

**Assessment completed by:** BMad Master
**Date:** 2026-03-10
**Workflow:** check-implementation-readiness v6.0.0
