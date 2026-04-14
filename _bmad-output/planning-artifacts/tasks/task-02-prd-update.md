# Task 02: PRD Update — Product-Packaging Registry FR

**Goal:** Update `_bmad-output/planning-artifacts/prd.md` to formally include the Product-Packaging Registry feature with correct legal basis and ICP statement.
**Scope:** Planning document edit via BMad skill.
**Skill:** `bmad-edit-prd` (PM agent John).
**Prerequisite:** Task 01 (OKIRkapu + KF refresh research) complete — findings may influence FR wording.
**Estimated effort:** 1 hour.

---

## Prompt for the new session

```
/bmad-edit-prd

Context: Sprint Change Proposal CP-5 (_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md) was approved and routes a PRD update to John (PM). I need the PRD revised to formally include the Product-Packaging Registry feature.

Key decisions from CP-5 to reflect in the PRD:

1. ICP clarification. The current PRD should make explicit that risk_guard targets every Hungarian KKV, with the majority being manufacturers and importers. The invoice-driven EPR approach alone is insufficient for this segment — they need a product-packaging registry.

2. New FR: Product-Packaging Registry (Termék-Csomagolás Nyilvántartás). Per-tenant CRUD for products with multi-component packaging bill-of-materials. Legal basis:
   - 80/2023 3. melléklet pont 1 (producer registration identity)
   - 80/2023 4. melléklet pont 1 (per-transaction operational reporting: megnevezés, mennyiség [kg], eredet, KF kód, átvállalási nyilatkozat)
   - 80/2023 1. melléklet pont 2 (KF code 8-digit structure)

3. New FR: NAV-Invoice-Driven Registry Bootstrap. On empty registry, pull the tenant's last 6-12 months of outbound NAV invoices, dedupe line items, present as candidate products in a triage queue.

4. New FR: AI-Assisted KF-Code Classification. Gemini 2.5 Flash suggestion layer. User must confirm before save — audit trail captures AI_SUGGESTED_CONFIRMED vs AI_SUGGESTED_EDITED. Fallback to VTSZ-prefix matcher if AI unavailable or below confidence threshold. OpenRouter / alternative providers explicitly deferred.

5. New FR: Registry-Driven EPR Autofill + MOHU Export. When user generates the quarterly report, invoices × registry components → aggregated KF totals → MOHU-compatible export. Format per Task 01 research findings (see research/okirkapu-and-kf-refresh-2026-04-XX.md).

6. Differentiator framing. The PRD should explicitly call out that invoice-native auto-mapping is the product's key differentiator vs. existing Hungarian EPR tools (e.g., Körforgó, which requires manual/Excel/API entry of sales data).

7. PPWR forward-compatibility. Data model must be designed for PPWR (EU Reg 2025/40) compatibility per CP-5 §5 invariants:
   - Packaging modelled at SKU/component level
   - KF code is one classifier among many; nullable PPWR fields (recyclabilityGrade, recycledContentPct, reusable, substancesOfConcern) present in schema from day one
   - Report target pluggable (MOHU today, EU registry post-2029)

Also: the gap doc (epr-packaging-calculation-gap-2026-04-14.md Finding 2) contains an incorrect citation — it references 80/2023 3. melléklet pont 2.2/2.3, which are actually about batteries, not packaging. Correct legal basis is 3. melléklet 1. pont + 4. melléklet 1. pont + 1. melléklet 2. pont. If the PRD has this same incorrect citation, fix it.

Reference artefacts to load:
- _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md (the authoritative decision)
- _bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md (original gap analysis)
- _bmad-output/planning-artifacts/prd.md (current PRD to edit)
- _bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-XX.md (Task 01 output)

Output: updated prd.md with the new FRs slotted into the appropriate section, ICP statement refined, PPWR forward-compatibility note added. Preserve all existing FRs — this is additive plus the one citation correction.
```

## Success criteria

- New FRs are numbered consistently with existing PRD convention.
- Legal basis cited correctly (3. melléklet 1 + 4. melléklet 1 + 1. melléklet 2).
- ICP statement explicitly mentions "manufacturers and importers" as the majority segment.
- PPWR decoupling note appears in non-functional / architectural constraints section.
- Incorrect gap doc citation fixed if present in current PRD.

## Do NOT

- Remove any existing FRs.
- Make design decisions beyond what CP-5 already committed to. If the PM agent wants to add new ideas, route them back to the PO (user) for approval, not into the PRD.
