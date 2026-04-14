# Task 04: Epic 9 + Stories 9.1–9.4 Skeleton

**Goal:** Add Epic 9 to `epics.md` with the four story skeletons (9.1–9.4), per CP-5's recommended scope and sequencing. Also fix the stale Epic 8 header line.
**Scope:** Planning document edit via BMad skill.
**Skill:** `bmad-create-epics-and-stories` (SM agent Bob).
**Prerequisite:** Task 02 (PRD) + Task 03 (Architecture) complete — stories should reference concrete FRs and ADRs.
**Estimated effort:** 1 hour.

---

## Prompt for the new session

```
/bmad-create-epics-and-stories

Context: Sprint Change Proposal CP-5 (_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md) was approved. PRD has been updated (Task 02) and Architecture ADRs have been written (Task 03). I need Epic 9 + stories 9.1–9.4 added to epics.md.

Also: fix the Epic 8 header line — currently lists only Stories 8.1 and 8.2, but we have since added 8.3 (Invoice-Driven EPR Auto-Fill), 8.4 (Accountant NAV credential access), and 8.5 (PLATFORM_ADMIN role). Those additions came via prior sprint change proposals (2026-03-12 and 2026-04-08) and the Epic 8 header never got reconciled.

=== Epic 9: Product-Packaging Registry & Automated EPR Filing ===

Goal: Enable Hungarian KKV manufacturers and importers to maintain a per-product packaging composition registry as the legal basis for correct EPR filing, bootstrapped automatically from NAV invoice data and augmented with AI-assisted KF code classification.

Legal basis: 80/2023 Korm. rendelet 3. melléklet 1. pont (producer registration), 4. melléklet 1. pont (per-transaction reporting), 1. melléklet 2. pont (KF code structure).

Differentiator: invoice-native registry bootstrap — no other Hungarian EPR tool does this.

=== Story 9.1: Product-Packaging Registry Foundation ===

Goal: Per-tenant CRUD for products with multi-component packaging bill-of-materials, legally grounded in 80/2023 Annex 3.1+4.1+1.2, with PPWR-ready nullable fields.

Data model (per CP-5 §4.2):
- products (tenant_id, id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
- product_packaging_components (id, product_id, material_description, kf_code, weight_per_unit_kg, component_order, recyclability_grade NULL, recycled_content_pct NULL, reusable NULL, substances_of_concern NULL, supplier_declaration_ref NULL, created_at, updated_at)
- registry_entry_audit_log (id, product_id, field_changed, old_value, new_value, changed_by_user_id, source, timestamp)

Acceptance criteria:
- Registry list page with search/filter by name, VTSZ, KF code, status
- Product editor supporting 1..N packaging components with full 8-digit structured KF input
- PPWR nullable fields present in schema from day one
- Full audit trail on every field change
- ArchUnit rule: no service outside registry package writes to product_packaging_components

=== Story 9.2: NAV-Invoice-Driven Registry Bootstrap + Triage UI ===

Depends on: 9.1

Goal: On first registry setup, pull tenant's outbound NAV invoices (reusing Story 8.1 infra), dedupe by (product_name, VTSZ), present as candidate products in a triage queue.

Flow (per CP-5 §4.3):
1. Trigger: user clicks "Bootstrap from NAV invoices" or opens empty registry
2. RegistryBootstrapService fetches invoices
3. Dedupe line items; rank by frequency and total quantity
4. For each candidate, call 9.3 classifier to pre-suggest KF code + component structure
5. Candidates land in triage queue (PENDING, APPROVED, REJECTED_NOT_OWN_PACKAGING, NEEDS_MANUAL_ENTRY)
6. User triages at own pace; approved → products table

Acceptance criteria:
- Dedup handles slight name variations (trim, case, whitespace)
- Triage queue persists across sessions
- Skip/reject states kept for audit (no silent data loss)
- No AI call made for already-rejected items
- Keyboard shortcuts for triage actions (competitive wedge: speed)

=== Story 9.3: AI-Assisted KF-Code Classification ===

Depends on: 9.1

Goal: Given a product (name, VTSZ), suggest KF codes with confidence scores. User confirms before save.

Architecture (per CP-5 §4.4 and the AI classification ADR from Task 03):
- KfCodeClassifierService interface (minimal abstraction)
- VertexAiGeminiClassifier (Gemini 2.5 Flash, EU region, workload identity)
- VtszPrefixFallbackClassifier (reuse Story 8.3 logic)
- Router falls through primary → fallback → empty field if all fail
- Explicitly NOT building: OpenRouter integration, A-B harness (deferred per CP-5 non-goals)

Guardrails:
- Confidence threshold below which no suggestion shown
- Per-tenant monthly call cap
- Circuit breaker on Gemini strategy
- Every suggestion tagged AI_SUGGESTED_CONFIRMED / AI_SUGGESTED_EDITED in audit log

Acceptance criteria:
- Mocked classifier in unit tests; real Vertex AI only in env-flag integration tests
- Graceful degradation: Vertex AI outage → VTSZ-prefix → empty, never block user
- Cost meter visible to PLATFORM_ADMIN (per-tenant monthly spend)

=== Story 9.4: Registry-Driven EPR Autofill + MOHU Export ===

Depends on: 9.1, 9.2, 9.3

Goal: At quarterly report time, invoices × registry components → aggregated per-KF totals → MOHU-compatible export.

Flow (per CP-5 §4.5):
1. Pull outbound invoices for the period
2. For each line item: registry match → multiply units × component weights; else VTSZ-prefix fallback (Story 8.3 logic)
3. Aggregate per KF code, in kg with 3 decimals
4. Produce MOHU-compatible export (format per Task 01 research)

PPWR decoupling invariant:
- EprReportTarget interface; MohuCsvExporter today; EuRegistryAdapter future placeholder
- EprService never references MohuCsvExporter directly (ArchUnit enforced)

Acceptance criteria:
- Report shows per-line-item provenance (registry / VTSZ-fallback / unmatched)
- Unmatched lines flagged with "Register this product" shortcut
- CSV matches current MOHU-acceptable format (per Task 01 findings)
- Export includes human-readable summary alongside machine CSV

=== Fix for Epic 8 header ===

Current line in epics.md (under "### Epic 8: Real Data Integration & Single-Verdict Export"):
"**Stories:** 8.1 (NAV Online Számla Client Implementation), 8.2 (Screening Verdict PDF Export)."

Replace with:
"**Stories:** 8.1 (NAV Online Számla Client Implementation), 8.2 (Screening Verdict PDF Export), 8.3 (Invoice-Driven EPR Auto-Fill), 8.4 (Accountant NAV Credential Access & Demo Mode), 8.5 (PLATFORM_ADMIN Role Separation)."

Reference artefacts to load:
- _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md
- _bmad-output/planning-artifacts/prd.md (updated per Task 02)
- _bmad-output/planning-artifacts/architecture.md (updated per Task 03)
- _bmad-output/planning-artifacts/epics.md (current, to edit)
- _bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-XX.md (Task 01 output)
```

## Success criteria

- Epic 9 added in the correct position (after Epic 8) in epics.md.
- Stories 9.1–9.4 each have: goal, acceptance criteria, dependencies noted, reference to CP-5 section.
- Epic 8 header fixed to list all five stories.
- Format matches existing epic/story convention in epics.md.

## Do NOT

- Flesh out each story with full context yet — that's `bmad-create-story`'s job, done one story at a time before dev picks it up.
- Redesign the data model or flows. CP-5 and the ADRs are authoritative.
