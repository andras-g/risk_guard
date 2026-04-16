# Story 9.6: Multi-Layer Packaging Ratio & AI Weight Suggestions

Status: done

## Story

As a Hungarian KKV manufacturer or importer using the Nyilvántartás,
I want the system to correctly calculate secondary/tertiary packaging weight using a per-component ratio, and I want the AI classifier to suggest all packaging layers with estimated weights,
so that my OKIRkapu EPR report contains legally accurate per-KF-code weight totals — not just primary packaging — and I spend less time on manual data entry.

## Business Context

**Compliance gap (critical):** The current EPR report calculation (`OkirkapuXmlExporter`, line 155) computes `component.weightPerUnitKg × invoiceQuantity` for every component. This is only correct for primary packaging (1:1 ratio). For secondary packaging (e.g., a 6-pack cardboard box holding 6 bottles), multiplying by 100 invoice units produces 100 × box weight — but only ~17 boxes were actually used. Tertiary packaging (pallet wrap) is even worse: 100 × pallet weight vs. ~1 pallet. The result is up to **10x overreporting** of secondary/tertiary packaging weight, causing producers to massively overpay MOHU EPR fees.

**AI gap:** The Gemini classifier currently suggests a single KF code (primary packaging material only). It does not suggest secondary/tertiary packaging layers, nor does it estimate packaging weight. The `suggested_components JSONB` column on `registry_bootstrap_candidates` was designed for multi-component suggestions but is **never populated** (line 47: `String suggestedComponents = null;`). Users must manually add secondary/tertiary components and manually enter all weights — a friction point that the AI was supposed to eliminate.

**Legal basis:** 80/2023 Korm. rendelet requires producers to report the actual weight of packaging placed on the market per KF code. See `epr-packaging-calculation-gap-2026-04-14.md` for the full gap analysis.

## Acceptance Criteria

### Part A — Packaging Ratio Field

1. **`product_packaging_components` gains a `units_per_product INT NOT NULL DEFAULT 1` column.** Meaning: "how many sold product units does one unit of this packaging contain?" For primary packaging the value is 1 (one bottle per bottle). For a 6-pack cardboard box the value is 6 (one box per 6 bottles). For a pallet holding 480 bottles the value is 480. Migration: `V20260416_004__add_units_per_product_to_components.sql`. Column comment: "Number of product units contained in one unit of this packaging (1 = primary, 6 = 6-pack, 480 = pallet, etc.)".

2. **EPR report calculation uses the ratio.** `OkirkapuXmlExporter.processLineItem()` (line 155) changes from `component.weightPerUnitKg().multiply(quantity)` to `component.weightPerUnitKg().multiply(quantity).divide(BigDecimal.valueOf(component.unitsPerProduct()), 6, RoundingMode.HALF_UP)`. Example: 100 bottles sold, 6-pack box weighs 0.050 kg, `unitsPerProduct=6` → 100 × 0.050 / 6 = 0.833 kg (correct), not 100 × 0.050 = 5.0 kg (incorrect).

3. **Backend domain + DTO + validation updated.** `ProductPackagingComponent` record gains `int unitsPerProduct`. `ComponentUpsertRequest` gains `@NotNull @Min(1) Integer unitsPerProduct`. `ComponentUpsertCommand` gains `int unitsPerProduct`. Default is 1; existing data migrated with DEFAULT 1.

4. **Frontend product form shows "Db/csomag" (Units per packaging) column** in the Csomagolási elemek DataTable. Input is `InputNumber` with `min=1`, `showButtons=true`. Default value for new component rows is `1`. i18n key: `registry.form.unitsPerProduct` = "Db/csomag" (hu) / "Units/pkg" (en). Tooltip: `registry.form.unitsPerProductTooltip` = "Hány eladott termék fér egy csomagolási egységbe? Elsődleges = 1, 6-os multipack = 6, raklap = 480." / "How many sold product units fit in one packaging unit? Primary = 1, 6-pack = 6, pallet = 480."

### Part B — Multi-Layer AI Packaging Suggestions

5. **System prompt rewritten for multi-layer suggestions.** The prompt at `prompts/kf-classifier-system-prompt.txt` instructs Gemini to suggest ALL packaging layers (primary, secondary, tertiary) for the given product, not just one material. New JSON response schema: `[{"layer":"primary","kfCode":"11010101","description":"PET palack","weightEstimateKg":0.025,"unitsPerProduct":1,"score":0.85}, {"layer":"secondary","kfCode":"41010201","description":"Karton multipack","weightEstimateKg":0.050,"unitsPerProduct":6,"score":0.70}]`. Rules: (a) `layer` must be one of `primary`, `secondary`, `tertiary`; (b) at most 5 entries total; (c) `weightEstimateKg` is the estimated weight of one packaging unit in kg; (d) `unitsPerProduct` is how many product units fit in that packaging unit; (e) `score` is confidence per layer. If uncertain about secondary/tertiary, omit them (do not hallucinate).

6. **`KfSuggestion` record extended** with `String layer`, `BigDecimal weightEstimateKg`, `int unitsPerProduct`. The `suggestedComponentDescriptions` field (currently `List<String>`) is replaced by `String description` (single description per suggestion, since each suggestion is now one layer, not alternatives).

7. **`VertexAiGeminiClassifier.parseResponse()` parses the new multi-layer schema.** Each entry in the Gemini JSON array becomes one `KfSuggestion`. The overall `ClassificationConfidence` is derived from the minimum score across all layers (conservative: weakest link determines confidence). `ClassificationResult.suggestions()` now returns multiple layers sorted by `componentOrder` (primary=0, secondary=1, tertiary=2), not by score.

8. **`BootstrapRepository.insertCandidateIfNew()` populates `suggested_components JSONB`.** Serialize `ClassificationResult.suggestions()` as JSON into the `suggested_components` column (currently hardcoded to `null` at line 47). Store the full list: `[{"layer":"primary","kfCode":"...","description":"...","weightEstimateKg":0.025,"unitsPerProduct":1}, ...]`. Keep `suggested_kf_code` as the primary-layer KF code for backward-compatible display in the triage table.

9. **`BootstrapApproveDialog` pre-populates multiple component rows** from `suggestedComponents` JSON when available. Each layer becomes one pre-filled component row with `materialDescription` = description, `kfCode` = kfCode, `weightPerUnitKg` = weightEstimateKg, `unitsPerProduct` = unitsPerProduct, `componentOrder` = 0/1/2. When `suggestedComponents` is null/empty, falls back to the existing single-component behavior.

### Part C — AI-Suggested Weight per Unit

10. **AI suggests `weightEstimateKg` for each packaging layer** (covered by AC #5 prompt and AC #6 `KfSuggestion` changes above). Weight is presented in the approve dialog and the manual classifier popover as a pre-filled but editable value. If weight confidence is low, show italic + tooltip "Becsült érték — ellenőrizze a tényleges tömeg alapján" / "Estimated value — verify against actual weight".

11. **Manual classify popover (`/registry/[id].vue`) also shows multi-layer suggestions.** When the user clicks the "Javaslat" button on the product form, the popover lists ALL suggested layers (not just the top-scoring alternative). Each layer shows: material name, KF code, estimated weight, units/pkg. User can accept individual layers (adds as component row) or accept all. Accepting a layer pre-fills `materialDescription`, `kfCode`, `weightPerUnitKg`, and `unitsPerProduct` on the component row.

### Part E — Demo Data Reset for Live Verification

14. **Demo seed data for registry is wiped and rebuilt by the bootstrap flow.** Remove from `R__demo_data.sql`: all `INSERT INTO products`, `INSERT INTO product_packaging_components`, `INSERT INTO registry_bootstrap_candidates`, and `INSERT INTO registry_entry_audit_log` rows (Sections 14, 15, 16 of the seed file). The demo tenants' registries start empty so that on login, the user sees the empty-state CTA ("Bootstrap from NAV invoices"), can trigger the bootstrap, and observe the full flow end-to-end: NAV invoice pull → dedup → multi-layer AI classification → triage → approve → populated registry with all packaging layers and estimated weights. The `ai_classifier_usage` seed rows (if any) should also be removed so the token counter starts fresh.

15. **Demo invoice fixtures (`DemoInvoiceFixtures`) must include products that have obvious multi-layer packaging** so the AI has meaningful material to classify. Verify that the existing demo invoice line items include at least: (a) a beverage product (PET bottle → primary PET + secondary cardboard multipack), (b) a food product (glass jar → primary glass + metal lid + paper label), (c) a furniture/bulky product (corrugated box + EPS corner protectors + stretch wrap). These already exist in the demo fixtures — just verify they are sufficient for a compelling live demo. If not, add 1-2 more illustrative line items.

### Part D — Regression Safety

16. **Existing products with DEFAULT 1 `unitsPerProduct` produce identical EPR report output.** The migration sets DEFAULT 1 and the formula `weight × qty / 1 = weight × qty` is equivalent. A dedicated test (`KgKgyfNeAggregatorTest`) verifies backward compatibility: same inputs as current tests produce same output.

17. **All existing tests pass unchanged** except where they construct `KfSuggestion` or `ClassificationResult` (which gain new fields). Update test fixtures to include the new fields with sensible defaults.

## Tasks / Subtasks

- [x] **Task 1 — DB migration: add `units_per_product` column** (AC: #1)
  - [x] Create `V20260416_004__add_units_per_product_to_components.sql`
  - [x] `ALTER TABLE product_packaging_components ADD COLUMN units_per_product INT NOT NULL DEFAULT 1;`
  - [x] Add column comment
  - [x] Run `./gradlew generateJooq` to regenerate jOOQ classes

- [x] **Task 2 — Backend domain model updates** (AC: #3, #6)
  - [x] Add `int unitsPerProduct` to `ProductPackagingComponent` record
  - [x] Add `@NotNull @Min(1) Integer unitsPerProduct` to `ComponentUpsertRequest`
  - [x] Add `int unitsPerProduct` to `ComponentUpsertCommand`
  - [x] Update `RegistryRepository` mapping (`toComponent`) to read the new column
  - [x] Update `RegistryService.create/update` to persist `unitsPerProduct`
  - [x] Extend `KfSuggestion` record with `String layer`, `BigDecimal weightEstimateKg`, `int unitsPerProduct`; replace `List<String> suggestedComponentDescriptions` with `String description`

- [x] **Task 3 — Fix OKIRkapu calculation formula** (AC: #2)
  - [x] In `OkirkapuXmlExporter.processLineItem()` line 155: change `component.weightPerUnitKg().multiply(quantity)` to `component.weightPerUnitKg().multiply(quantity).divide(BigDecimal.valueOf(component.unitsPerProduct()), 6, RoundingMode.HALF_UP)`
  - [x] Add backward-compatibility test: existing data with `unitsPerProduct=1` produces identical output

- [x] **Task 4 — Rewrite AI system prompt for multi-layer suggestions** (AC: #5)
  - [x] Rewrite `prompts/kf-classifier-system-prompt.txt` to request all packaging layers
  - [x] Define new JSON response schema with `layer`, `kfCode`, `description`, `weightEstimateKg`, `unitsPerProduct`, `score`
  - [x] Instruct model: omit secondary/tertiary if uncertain; never hallucinate weights — use conservative industry averages; cap at 5 entries

- [x] **Task 5 — Update `VertexAiGeminiClassifier.parseResponse()`** (AC: #7)
  - [x] Parse new multi-layer JSON response schema
  - [x] Map `layer` to `componentOrder` (primary=0, secondary=1, tertiary=2)
  - [x] Derive `ClassificationConfidence` from minimum score across layers
  - [x] Sort suggestions by `componentOrder` ascending (not by score)
  - [x] Handle backward-compatible responses (old format without `layer` field)

- [x] **Task 6 — Wire `suggested_components JSONB` in bootstrap flow** (AC: #8)
  - [x] In `BootstrapRepository.insertCandidateIfNew()` (line 47): serialize `ClassificationResult.suggestions()` to JSON and store in `suggested_components`
  - [x] Keep `suggested_kf_code` as the primary-layer KF code (first suggestion where `layer="primary"`)
  - [x] Update `BootstrapCandidateResponse` to expose `suggestedComponents` as parsed JSON (already a String field)

- [x] **Task 7 — Frontend: add "Db/csomag" column to product form** (AC: #4)
  - [x] Add `InputNumber` column in the Csomagolási elemek DataTable at `pages/registry/[id].vue`
  - [x] `min=1`, `showButtons=true`, default `1` for new rows
  - [x] Add i18n keys `registry.form.unitsPerProduct` and `registry.form.unitsPerProductTooltip` (hu + en, alphabetical order)

- [x] **Task 8 — Frontend: update BootstrapApproveDialog for multi-component** (AC: #9)
  - [x] Parse `suggestedComponents` JSON from the candidate
  - [x] Pre-populate one component row per layer with `materialDescription`, `kfCode`, `weightPerUnitKg`, `unitsPerProduct`, `componentOrder`
  - [x] Fallback to single-component behavior when `suggestedComponents` is null

- [x] **Task 9 — Frontend: update classifier popover for multi-layer results** (AC: #11)
  - [x] Show all returned layers in the popover, not just top-scoring alternative
  - [x] Each layer row: material name, KF code, estimated weight (editable hint), units/pkg
  - [x] "Accept all" button adds all layers as component rows
  - [x] "Accept" per-layer button adds individual component
  - [x] Show weight confidence tooltip when score < 0.7

- [x] **Task 10 — Wipe demo registry seed data for live verification** (AC: #14, #15)
  - [x] Remove Sections 14 (products), 15 (bootstrap candidates), and 16 (registry audit log) from `R__demo_data.sql`
  - [x] Remove any `ai_classifier_usage` demo seed rows if present
  - [x] Keep all non-registry demo data (tenants, users, watchlist, etc.) intact
  - [x] Verify `DemoInvoiceFixtures` include beverage (PET), food (glass jar), and bulky (furniture box) line items for compelling multi-layer demo
  - [x] If demo invoice fixtures are insufficient, add 1-2 illustrative line items with obvious multi-layer packaging
  - [x] Test the full flow: start app in demo mode → login → navigate to empty registry → trigger bootstrap → observe AI multi-layer suggestions → approve → verify populated registry

- [x] **Task 11 — Update all test fixtures** (AC: #16, #17)
  - [x] Update `KfSuggestion` constructors in all tests (new fields: `layer`, `weightEstimateKg`, `unitsPerProduct`, `description`)
  - [x] Update `ClassifierRouterTest`, `RegistryClassifyControllerTest`, `KfClassifierValidationTest`
  - [x] Add `OkirkapuXmlExporterTest` cases: (a) primary-only product (ratio=1, same as before), (b) product with 6-pack secondary (ratio=6), (c) product with tertiary pallet (ratio=480)
  - [x] Add `KgKgyfNeAggregatorTest` backward-compatibility case
  - [x] Update frontend `[id].spec.ts` and `bootstrap.spec.ts` for new fields
  - [x] Run full backend test suite: `./gradlew test --tests "hu.riskguard.epr.*"` (~90s)
  - [x] Run frontend tests: `npm run test` (~6s)

## Dev Notes

### Critical: OkirkapuXmlExporter Formula Change

The core fix is a single-line change at `OkirkapuXmlExporter.java:155`:
```java
// BEFORE (incorrect for secondary/tertiary):
BigDecimal weightKg = component.weightPerUnitKg().multiply(quantity);

// AFTER (correct for all layers):
BigDecimal weightKg = component.weightPerUnitKg().multiply(quantity)
        .divide(BigDecimal.valueOf(component.unitsPerProduct()), 6, RoundingMode.HALF_UP);
```
This is the highest-priority change. Everything else (AI, UI) is enhancement — but this formula fix is a **compliance correction**.

### KfSuggestion Breaking Change

`KfSuggestion` currently has `List<String> suggestedComponentDescriptions`. This was designed for multiple material descriptions per single suggestion (alternatives). With multi-layer, each suggestion IS one layer, so this becomes `String description`. This is a breaking change that ripples to:
- `VertexAiGeminiClassifier.parseResponse()` (constructs `KfSuggestion`)
- `VtszPrefixFallbackClassifier.classify()` (constructs `KfSuggestion` with `List.of(...)`)
- `BootstrapRepository.insertCandidateIfNew()` (reads suggestions)
- All test fixtures constructing `KfSuggestion`
- Frontend `useClassifier.ts` and popover rendering

New `KfSuggestion` shape:
```java
public record KfSuggestion(
    String kfCode,
    String description,          // was: List<String> suggestedComponentDescriptions
    double score,
    String layer,                // NEW: "primary" | "secondary" | "tertiary"
    BigDecimal weightEstimateKg, // NEW: nullable — AI's best estimate
    int unitsPerProduct          // NEW: defaults to 1
) {}
```

### AI Prompt Design Guidance

The rewritten system prompt must:
1. Explain the concept of packaging layers (primary = direct contact, secondary = grouping, tertiary = transport)
2. For each layer, request: `layer`, `kfCode` (from taxonomy), `description`, `weightEstimateKg`, `unitsPerProduct`, `score`
3. Emphasize: if uncertain about secondary/tertiary existence, **omit** — do not guess. Better to suggest only primary than hallucinate a non-existent secondary.
4. For `weightEstimateKg`: use conservative industry-standard estimates. E.g., 0.5L PET bottle ~25g, standard corrugated 6-pack tray ~50g, standard pallet wrap ~300g. State these as estimates and instruct score < 0.5 if no confident reference.
5. Product name is the primary signal. VTSZ helps disambiguate material type.
6. Keep the taxonomy reference (KF codes) as-is — it already covers all material categories.

### VtszPrefixFallbackClassifier Adaptation

The fallback classifier returns rule-based matches. It should continue to return single-layer (primary) suggestions with `layer="primary"`, `weightEstimateKg=null`, `unitsPerProduct=1`. It cannot guess packaging layers.

### Gemini Response Backward Compatibility

During transition, the classifier may receive responses in the old format (single-layer, no `layer` field). `parseResponse()` MUST handle this gracefully: if `layer` is missing, default to `"primary"`. If `weightEstimateKg` is missing, default to `null`. If `unitsPerProduct` is missing, default to `1`.

### Frontend: Popover Multi-Layer UX

The current popover shows a ranked list of alternatives (3 KF code options for the same material). The new popover shows **layers** (different packaging components for the same product). The UX is fundamentally different:
- Old: "Pick one of these KF codes" → single selection replaces current row
- New: "Here are the packaging layers we detected" → "Accept all" adds multiple component rows, or accept individually

The popover is hoisted to page root with `appendTo="body"` (Story 9.5, AC #5). Maintain this. The popover content structure changes from a ranked list to a layered card list.

### Files to Modify

**Backend (main):**
- `V20260416_004__add_units_per_product_to_components.sql` (NEW)
- `ProductPackagingComponent.java` — add `unitsPerProduct`
- `ComponentUpsertRequest.java` — add `unitsPerProduct` with validation
- `ComponentUpsertCommand.java` — add `unitsPerProduct`
- `KfSuggestion.java` — restructure (layer, weightEstimateKg, unitsPerProduct, description)
- `ClassificationResult.java` — no structural change (suggestions list stays)
- `VertexAiGeminiClassifier.java` — rewrite `parseResponse()` for multi-layer
- `VtszPrefixFallbackClassifier.java` — adapt KfSuggestion constructor
- `BootstrapRepository.java` — populate `suggested_components` JSONB (line 47)
- `OkirkapuXmlExporter.java` — fix formula (line 155)
- `RegistryRepository.java` — read/write `unitsPerProduct`
- `RegistryService.java` — pass through `unitsPerProduct`
- `prompts/kf-classifier-system-prompt.txt` — rewrite for multi-layer

**Backend (test):**
- `ClassifierRouterTest.java` — update KfSuggestion constructors
- `RegistryClassifyControllerTest.java` — update KfSuggestion constructors
- `KfClassifierValidationTest.java` — update assertions for multi-layer response
- `OkirkapuXmlExporterTest.java` — add ratio test cases
- `KgKgyfNeAggregatorTest.java` — add backward-compatibility case
- `RegistryServiceTest.java` — add `unitsPerProduct` to test fixtures
- `RegistryControllerTest.java` — add `unitsPerProduct` to request fixtures
- `BootstrapServiceTest.java` — verify `suggested_components` populated

**Seed data:**
- `backend/src/main/resources/db/test-seed/R__demo_data.sql` — remove Sections 14, 15, 16 (registry products, components, bootstrap candidates, audit log)

**Frontend:**
- `pages/registry/[id].vue` — add Db/csomag column, update popover for multi-layer
- `pages/registry/[id].spec.ts` — update test fixtures
- `pages/registry/bootstrap.vue` — no change (triage table shows `suggestedKfCode` which stays)
- `components/registry/BootstrapApproveDialog.vue` — multi-component pre-population
- `components/registry/BootstrapApproveDialog.spec.ts` — update for multi-component
- `composables/api/useClassifier.ts` — update TypeScript types for multi-layer response
- `composables/api/useClassifier.spec.ts` — update mock response shape
- `i18n/hu/registry.json` — add `unitsPerProduct`, `unitsPerProductTooltip` keys
- `i18n/en/registry.json` — add `unitsPerProduct`, `unitsPerProductTooltip` keys

### Previous Story Learnings (from 9.5)

- **Popover hoisting:** The AI popover MUST use `appendTo="body"` and z-[60] (Story 9.5 AC #5). Do NOT regress this.
- **i18n alphabetical ordering:** All i18n key additions must maintain alphabetical order within their namespace (project rule).
- **DataTable responsiveLayout:** Story 9.5 AC #3 moved components table to `responsiveLayout="stack"` below 1024px. New column must work with this.
- **PPWR row-expansion:** Story 9.5 AC #3 moved PPWR fields into row-expansion. The `unitsPerProduct` column goes in the main row, not the expansion.
- **Component validation:** Story 9.5 AC #8 added `components.length === 0` validation with inline Message banner. This must still work.

### Project Structure Notes

- Module: `hu.riskguard.epr.registry` (existing sub-module of EPR)
- Classifier: `hu.riskguard.epr.registry.classifier` (existing package)
- Report generation: `hu.riskguard.epr.report.internal` (existing package)
- ArchUnit rule: only registry package writes to `product_packaging_components` (EpicNineInvariantsTest rule 1)
- Frontend spec co-location: `*.vue` and `*.spec.ts` must stay in the same directory

### References

- [Source: `epr-packaging-calculation-gap-2026-04-14.md`] — Full gap analysis with legal formula
- [Source: `sprint-change-proposal-2026-04-14.md` §4.4] — AI classification architecture
- [Source: `V20260414_001__create_product_registry.sql`] — Schema for `products` and `product_packaging_components`
- [Source: `V20260414_002__create_bootstrap_candidates.sql`] — Schema for `registry_bootstrap_candidates` with `suggested_components JSONB`
- [Source: `OkirkapuXmlExporter.java:155`] — Current (incorrect) weight formula
- [Source: `BootstrapRepository.java:47`] — `suggestedComponents = null` placeholder
- [Source: `kf-classifier-system-prompt.txt`] — Current single-layer prompt
- [Source: Story 9.5 AC #5] — Popover hoisting pattern (appendTo="body", z-[60])
- [Source: Story 9.3] — AI classifier architecture and ClassifierRouter flow
- [Source: `KfSuggestion.java`] — Current record structure to be extended
- [Source: `project-context.md`] — Alphabetical i18n, spec co-location, @LogSafe rules

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6 (1M context)

### Completion Notes List
- Task 1: Created V20260416_004 migration adding `units_per_product INT NOT NULL DEFAULT 1` with column comment. Regenerated jOOQ.
- Task 2: Extended KfSuggestion (layer, weightEstimateKg, unitsPerProduct, description replacing suggestedComponentDescriptions), ProductPackagingComponent, ComponentUpsertCommand, ComponentUpsertRequest, KfSuggestionDto. Updated RegistryRepository read/write, RegistryService diff-audit for new field.
- Task 3: Fixed critical EPR compliance bug in OkirkapuXmlExporter — weight formula now divides by unitsPerProduct.
- Task 4: Rewrote kf-classifier-system-prompt.txt for multi-layer packaging (primary/secondary/tertiary), weight estimates, unitsPerProduct.
- Task 5: Updated VertexAiGeminiClassifier.parseResponse() to parse multi-layer JSON, sort by layer order (not score), derive confidence from min score across layers, with backward-compatible defaults.
- Task 6: Wired suggested_components JSONB in BootstrapRepository — serializes all suggestions as JSON, keeps suggested_kf_code as primary-layer code.
- Task 7: Added "Db/csomag" (Units/pkg) InputNumber column to product form DataTable with tooltip.
- Task 8: Updated BootstrapApproveDialog to parse suggestedComponents JSON and pre-populate multiple component rows per layer.
- Task 9: Rebuilt classifier popover for multi-layer display — layer tags, per-layer Accept, Accept All, weight estimate with confidence tooltip.
- Task 10: Removed sections 14-17 from R__demo_data.sql (products, components, bootstrap candidates, audit log, classifier usage). Demo tenants start with empty registries for E2E bootstrap flow.
- Task 11: Updated all KfSuggestion, ComponentUpsertCommand, ProductPackagingComponent constructors in 8+ test files (backend) and 2 frontend spec files. All 776 frontend tests pass, all registry backend tests pass.
- Note: EprOkirkapuExportIntegrationTest has 2 pre-existing failures (Testcontainer connection issues) unrelated to this story.
- R1 Review Follow-ups: P1 — defense-in-depth: reject unitsPerProduct≤0 (→1) and negative weightEstimateKg (→null) in parseResponse. P2 — serialize score to suggested_components JSONB + add _lowWeightConfidence flag to BootstrapApproveDialog for italic+tooltip on low-confidence weights. P3 — normalize layer field with .toLowerCase(). P4 — guard acceptAllSuggestions() against stale target component. P5 — 2 new tests for BootstrapApproveDialog multi-component pre-population (JSON parse + invalid JSON fallback). P6 — 14 new tests for VertexAiGeminiClassifier.parseResponse() covering multi-layer sort, min-score confidence, weight deserialization, defense-in-depth validations, backward compat, and error handling.

### Change Log
- 2026-04-16: Story 9.6 implementation complete. Critical EPR compliance fix (units_per_product ratio), multi-layer AI classification, popover/dialog UX overhaul, demo seed data reset.
- 2026-04-16: Addressed code review R1 findings — 6 patch items resolved (P1-P6): defense-in-depth validation for AI inputs, score serialization in JSONB, layer normalization, stale-component guard, and 2 new test suites.

### File List

**Backend (new):**
- backend/src/main/resources/db/migration/V20260416_004__add_units_per_product_to_components.sql

**Backend (modified):**
- backend/src/main/java/hu/riskguard/epr/registry/classifier/KfSuggestion.java
- backend/src/main/java/hu/riskguard/epr/registry/domain/ProductPackagingComponent.java
- backend/src/main/java/hu/riskguard/epr/registry/domain/ComponentUpsertCommand.java
- backend/src/main/java/hu/riskguard/epr/registry/api/dto/ComponentUpsertRequest.java
- backend/src/main/java/hu/riskguard/epr/registry/api/dto/KfSuggestionDto.java
- backend/src/main/java/hu/riskguard/epr/registry/classifier/internal/VertexAiGeminiClassifier.java
- backend/src/main/java/hu/riskguard/epr/registry/classifier/internal/VtszPrefixFallbackClassifier.java
- backend/src/main/java/hu/riskguard/epr/registry/internal/BootstrapRepository.java
- backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryRepository.java
- backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryService.java
- backend/src/main/java/hu/riskguard/epr/report/internal/OkirkapuXmlExporter.java
- backend/src/main/resources/prompts/kf-classifier-system-prompt.txt
- backend/src/main/resources/db/test-seed/R__demo_data.sql

**Backend (tests new):**
- backend/src/test/java/hu/riskguard/epr/registry/classifier/VertexAiGeminiClassifierParseResponseTest.java

**Backend (tests modified):**
- backend/src/test/java/hu/riskguard/epr/registry/ClassifierRouterTest.java
- backend/src/test/java/hu/riskguard/epr/registry/RegistryClassifyControllerTest.java
- backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java
- backend/src/test/java/hu/riskguard/epr/registry/RegistryControllerTest.java
- backend/src/test/java/hu/riskguard/epr/registry/RegistryRepositoryIntegrationTest.java
- backend/src/test/java/hu/riskguard/epr/registry/BootstrapServiceTest.java
- backend/src/test/java/hu/riskguard/epr/registry/BootstrapControllerTest.java

**Frontend (modified):**
- frontend/app/pages/registry/[id].vue
- frontend/app/pages/registry/[id].spec.ts
- frontend/app/components/registry/BootstrapApproveDialog.vue
- frontend/app/components/registry/BootstrapApproveDialog.spec.ts
- frontend/app/composables/api/useClassifier.ts
- frontend/app/composables/api/useRegistry.ts
- frontend/app/composables/api/useBootstrap.ts
- frontend/app/i18n/hu/registry.json
- frontend/app/i18n/en/registry.json

### Review Findings

Code review R1 (2026-04-16) — Blind Hunter + Edge Case Hunter + Acceptance Auditor. 32 raw findings → 19 unique after dedup → 6 patch, 2 defer, 13 dismissed.

- [x] [Review][Patch] P1: Validate AI-returned unitsPerProduct and weightEstimateKg in parseResponse [VertexAiGeminiClassifier.java:225-233] — AI could return unitsPerProduct=0 (causes ArithmeticException in OkirkapuXmlExporter division) or negative weightEstimateKg. Add: reject unitsPerProduct <= 0 (default to 1), reject negative weight (set to null). Defense-in-depth for the critical EPR formula.
- [x] [Review][Patch] P2: Serialize `score` in BootstrapRepository.serializeSuggestions() [BootstrapRepository.java:186-200] — AC#10 requires italic+tooltip in approve dialog when score < 0.7, but score is not included in the JSONB. BootstrapApproveDialog.SuggestedComponent has no score field. Without it, weight confidence styling cannot be implemented in the approve dialog.
- [x] [Review][Patch] P3: Normalize AI layer field with `.toLowerCase()` in parseResponse [VertexAiGeminiClassifier.java:228] — AI returning "Primary" or "SECONDARY" silently maps to layerToOrder default=3, storing an invalid layer string in JSONB.
- [x] [Review][Patch] P4: Guard acceptAllSuggestions() against stale target component [pages/registry/[id].vue:157-179] — If the target component was deleted between clicking Suggest and Accept All, acceptSuggestion(first) returns silently but the loop continues adding orphaned secondary/tertiary rows without a primary.
- [x] [Review][Patch] P5: Add unit test for BootstrapApproveDialog multi-component pre-population from suggestedComponents JSON — AC#9 core behavior (JSON parse → multiple component rows) has zero test coverage; only the null fallback path is tested.
- [x] [Review][Patch] P6: Add unit test for VertexAiGeminiClassifier.parseResponse() multi-layer parsing — AC#7 behaviors (layer-order sort, min-score confidence, weightEstimateKg deserialization) are untested outside the gated integration test.
- [x] [Review][Defer] D1: Static ObjectMapper in BootstrapRepository [BootstrapRepository.java:35] — thread-safe for current usage but fragile if JavaTimeModule or Jdk8Module needed later. Pre-existing pattern.
- [x] [Review][Defer] D2: candidatesTokenCount field naming in Vertex AI response [VertexAiGeminiClassifier.java:196] — field name may vary across API versions; token counts could silently be 0. Not blocking.

Code review R2 (2026-04-16) — Blind Hunter + Edge Case Hunter + Acceptance Auditor. 31 raw findings → 27 unique after dedup → 10 patch, 7 defer, 10 dismissed.

- [x] [Review][Patch] R2-P1: Remove @NotNull from ComponentUpsertRequest.unitsPerProduct [ComponentUpsertRequest.java:23] — @NotNull breaks existing API clients that omit the field; null-coalesce in toCommand() is dead code under current validation. Spec says "Default is 1" implying optional. Fix: removed @NotNull, kept @Min(1) and null-coalesce.
- [x] [Review][Patch] R2-P2: Fix BigDecimal.valueOf(double) precision in parseResponse [VertexAiGeminiClassifier.java:232-234] — BigDecimal.valueOf(asDouble()) introduces floating-point imprecision for EPR compliance weights (e.g., 0.025 → 0.02499...). Fix: use new BigDecimal(asText()) with NumberFormatException guard.
- [x] [Review][Patch] R2-P3: Add upper-bound cap on AI-returned unitsPerProduct [VertexAiGeminiClassifier.java:242] — AI hallucination could return extreme values (999999), causing near-zero weights in EPR export. Fix: added cap at 10,000.
- [x] [Review][Patch] R2-P4: Create OkirkapuXmlExporterTest with ratio test cases [OkirkapuXmlExporterTest.java] — AC#2 formula change and AC#16 backward-compat had zero unit test coverage. Fix: created 4 test cases (primary ratio=1, secondary ratio=6, tertiary ratio=480, multi-component).
- [x] [Review][Patch] R2-P5: Add backward-compat case to KgKgyfNeAggregatorTest [KgKgyfNeAggregatorTest.java] — AC#16 requires aggregator backward-compat assertion. Fix: added test verifying pre-divided contributions aggregate correctly.
- [x] [Review][Patch] R2-P6: Add suggested_components assertion to BootstrapServiceTest [BootstrapServiceTest.java] — AC#8 core behavior (multi-layer classification forwarded to repository) had zero test coverage. Fix: added test verifying ClassificationResult with suggestions is passed to insertCandidateIfNew.
- [x] [Review][Patch] R2-P7: Fix sprint-status.yaml 9-6 entry text corruption — Story 9.5 comment text duplicated inline on the 9-6 line. Fix: removed duplicated text.
- [x] [Review][Patch] R2-P8: Change inline text to v-tooltip in BootstrapApproveDialog [BootstrapApproveDialog.vue:280-286] — AC#10 specifies tooltip, not inline small text. Fix: replaced <small> with v-tooltip.top on InputNumber.
- [x] [Review][Patch] R2-P9: Add unitsPerProduct ?? 1 default in acceptAllSuggestions loop [pages/registry/[id].vue:181] — Missing null-coalesce for old API responses. Fix: added ?? 1 default.
- [x] [Review][Patch] R2-P10: Stage untracked files — Migration files and new test file were untracked.
- [x] [Review][Defer] R2-D1: Static ObjectMapper in BootstrapRepository — pre-existing pattern, thread-safe for current usage.
- [x] [Review][Defer] R2-D2: candidatesTokenCount field naming — pre-existing, already deferred in R1.
- [x] [Review][Defer] R2-D3: useApiError leaks raw backend validation messages — pre-existing XSS/info-disclosure risk in toast summaries.
- [x] [Review][Defer] R2-D4: weightPerUnitKg nullable in OkirkapuXmlExporter — pre-existing NPE risk for null weights.
- [x] [Review][Defer] R2-D5: useClassifier.spec.ts never existed — pre-existing test coverage gap.
- [x] [Review][Defer] R2-D6: No component-level tests for multi-layer popover UX — popover logic tested via function-level tests only.
- [x] [Review][Defer] R2-D7: classify() with empty productName — pre-existing, produces false-positive VTSZ_FALLBACK matches.
