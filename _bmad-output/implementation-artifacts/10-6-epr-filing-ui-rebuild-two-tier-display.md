# Story 10.6: EPR Filing UI Rebuild — Two-Tier Display

Status: done

<!-- Epic 10 · Story 10.6 · depends on 10.5 (GET /api/v1/epr/filing/aggregation, FilingAggregationResult DTOs) -->
<!-- Frontend-only story — backend aggregation endpoint was delivered in Story 10.5 -->
<!-- One backend removal task: DELETE /api/v1/epr/filing/invoice-autofill (retained in 10.5 until this story) -->

## Story

As an **SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN user**,
I want the **EPR filing page to display a read-only, automatically computed two-tier view of sold products and KF-code totals derived from my Registry and invoice data**,
so that **I can see exactly what will be reported to OKIRkapu without entering any quantities manually, and I can immediately identify unresolved products that need packaging information**.

This story rewrites `filing.vue` to consume `GET /api/v1/epr/filing/aggregation` (Story 10.5). It removes all manual-quantity input, the InvoiceAutoFillPanel, the template-based calculate workflow, and the preview panel. The new page is a read-only display with a period selector and two tables.

## Acceptance Criteria

### Page layout and rendering

1. **Page layout order** (top-to-bottom): period selector → sold-products table (`EprSoldProductsTable`) → KF-totals table (`EprKfTotalsTable`) → summary cards → unresolved panel (if any) → OKIRkapu export button.

2. **Period selector** defaults to last completed quarter on mount (e.g., on 2026-04-20 → `2026-01-01…2026-03-31`). User can override freely. Period change triggers a **debounced (500ms)** call to `useEprFilingStore.fetchAggregation(from, to)`.

3. **Loading state**: while `isLoading` is true, both the sold-products table and KF-totals table render `Skeleton` rows (PrimeVue `Skeleton` component, same pattern as `WatchlistTable.vue`). Action buttons (export) are disabled.

4. **Initial fetch** on mount (after period defaults are set). No manual "Calculate" button.

### Sold-products table (`EprSoldProductsTable.vue`)

5. **Columns**: VTSZ, description, totalQuantity (locale-formatted Hungarian), unitOfMeasure, matchingInvoiceLines, status badge.
   - Column header i18n keys: `epr.filing.soldProducts.columns.{vtsz, description, quantity, unit, lines, status}`.

6. **Status badge derivation** (client-side, no extra server call):
   - If the row's `(vtsz, description)` appears in `unresolved` with reason `ZERO_COMPONENTS` → red badge `Hiányos` (severity `danger`).
   - Else if the row's `productId` contributes to any `KfCodeTotal.hasFallback = true` (derived by checking if any `KfCodeTotal` is contributed by this row's VTSZ) → yellow badge `Bizonytalan` (severity `warn`).
   - Else → green badge `Kész` (severity `success`).
   - Algorithm: derive from `unresolved` and `kfTotals` props; no `productId` lookup needed — match on `(vtsz, description)` first, then on kfCode presence.

7. **Default sort**: `totalQuantity DESC`. Always-on paginator (10/25/50 rows per page, `rowsPerPageOptions=[10,25,50]`).

8. **Filter chips** (client-side): `Csak hiányos` and `Csak bizonytalan`. Chips are toggle buttons. When active, filters the DataTable's `:value` binding before rendering. Multiple chips ANDed (show rows matching all active filters).

9. **Row click** (on row): if the `SoldProductLine.productId` is non-null → navigate to `/registry/{productId}`; otherwise navigate to `/registry?vtsz={vtsz}&q={encodeURIComponent(description)}` (filtered registry list).

### KF-totals table (`EprKfTotalsTable.vue`)

10. **Columns**: kfCode (formatted `12 34 56 78` by inserting space every 2 chars), classificationLabel (nullable, dash `—` if null), totalWeightKg (3 decimal places + ` kg`), feeRateHufPerKg (locale-formatted), totalFeeHuf (locale-formatted + ` Ft`), contributingProductCount.
    - Column header i18n keys: `epr.filing.kfTotals.columns.{kfCode, classification, weightKg, feeRate, feeFt, productCount}`.

11. **Default sort**: `totalFeeHuf DESC`. Read-only — no inputs, no per-row actions, no row click.

12. **hasFallback indicator**: if `KfCodeTotal.hasFallback` is true, display a small yellow warning icon (`pi pi-exclamation-triangle`) next to the kfCode cell with tooltip `epr.filing.kfTotals.fallbackTooltip`.

13. **hasOverflowWarning indicator**: if `KfCodeTotal.hasOverflowWarning` is true, display an orange icon with tooltip `epr.filing.kfTotals.overflowTooltip`.

### Summary cards

14. **Three summary cards** derived solely from `aggregation.kfTotals` (no extra fetch):
    - **Total KF codes**: `aggregation.kfTotals.length`.
    - **Grand-total weight**: `Σ kfTotals[*].totalWeightKg` rounded to 3 decimal places + ` kg`.
    - **Grand-total fee**: `Σ kfTotals[*].totalFeeHuf` locale-formatted + ` Ft`.
    - Before aggregation loads (or while loading) all cards show `—` placeholder.
    - i18n keys: `epr.filing.summary.{totalKfCodes, grandTotalWeight, grandTotalFee}`.

### Unresolved panel

15. **Unresolved panel** is visible only when `aggregation.unresolved.length > 0`. Collapsed by default (PrimeVue `Panel` with `collapsed` prop). Panel header shows the count: `epr.filing.unresolved.panelTitle` (e.g., "Nem azonosított sorok ({count})").

16. **Unresolved panel columns**: invoiceNumber, lineNumber, vtsz, description, quantity, unitOfMeasure, reason (i18n-mapped). i18n reason keys: `epr.filing.unresolved.reason.{NO_MATCHING_PRODUCT, UNSUPPORTED_UNIT_OF_MEASURE, ZERO_COMPONENTS, VTSZ_FALLBACK}`.

17. **Per-row CTA** for `NO_MATCHING_PRODUCT` and `ZERO_COMPONENTS` rows: button `epr.filing.unresolved.registerProduct` routes to `/registry/new?vtsz={vtsz}&name={encodeURIComponent(description)}`.

18. **Distinct tooltip** for `UNSUPPORTED_UNIT_OF_MEASURE` reason: `epr.filing.unresolved.unsupportedUnitTooltip` = "Jelenleg csak DARAB mértékegység támogatott".

19. **VTSZ_FALLBACK unresolved rows** display an info note (`epr.filing.unresolved.vtszFallbackNote`) explaining they ARE counted in KF totals but with uncertain classification.

### OKIRkapu export

20. **Export button** (`epr.okirkapu.exportButton`) is disabled when `aggregation` is null OR `aggregation.kfTotals.length === 0`. Export uses the existing `useEprFilingStore.exportOkirkapu(from, to)` action — period state comes from `filingStore.period`.

21. **Producer-profile-incomplete** warning banner (Story 9.4 pattern) is reused: when `exportError === 'producer.profile.incomplete'`, show the existing toast-based warning (i18n keys `epr.okirkapu.profileIncomplete` + `epr.okirkapu.profileIncompleteDetail`).

### Store rewrite (`useEprFilingStore`)

22. **New store shape** (`frontend/app/stores/eprFiling.ts` rewritten):
    ```typescript
    interface FilingState {
      period: { from: string; to: string }
      aggregation: FilingAggregationResult | null
      isLoading: boolean
      isExporting: boolean
      error: string | null
      exportError: string | null
    }
    ```
    **Removed from store**: `lines`, `validLines`, `serverResult`, `hasValidLines`, `isCalculating`, `initFromTemplates()`, `updateQuantity()`, `calculate()`.
    **Retained**: `isExporting`, `exportError`, `exportOkirkapu()`, `reset()`.
    **New actions**: `fetchAggregation(from: string, to: string): Promise<void>` — GET `/api/v1/epr/filing/aggregation`, populates `aggregation`.
    **New getters**: `grandTotalWeightKg`, `grandTotalFeeHuf`, `totalKfCodes` — derived from `aggregation.kfTotals`.

23. **`fetchAggregation`** action handles 412 (producer profile incomplete) by setting `error = 'producer.profile.incomplete'` so the top-level banner renders above the period selector (R1 P3 resolution), and handles 402 (tier gate) by setting `error = 'tier.gate'` which the page maps to a visible banner. Other non-2xx statuses set `error` to the raw message. No re-throw; the page handles via store state.

### Backend removal

24. **DELETE `/api/v1/epr/filing/invoice-autofill`** backend endpoint: the Story 8.3 auto-fill endpoint (`EprController` method annotated `@GetMapping("/filing/invoice-autofill")`) is removed in this story (was explicitly retained in Story 10.5 AC #16 until Story 10.6). Related backend class `InvoiceAutoFillController` (if separate) or the controller method in `EprController` is deleted. Compile with `./gradlew build` and confirm no remaining references. Any DTO classes exclusively used by this endpoint (e.g., `InvoiceAutoFillLine`, `InvoiceAutoFillResponse`) are deleted if unused after removal.

25. **No new backend code** beyond the removal in AC #24. The aggregation endpoint (`GET /api/v1/epr/filing/aggregation`) is fully implemented in Story 10.5.

### Frontend deletions

26. **Delete** `frontend/app/components/Epr/InvoiceAutoFillPanel.vue` + `InvoiceAutoFillPanel.spec.ts` — only referenced in `filing.vue`.

27. **Delete** `frontend/app/composables/api/useInvoiceAutoFill.ts` — only used in `filing.vue`; all references removed when filing.vue is rewritten.

28. **Remove** from `filing.vue`: all autofill composable usage, `InvoiceAutoFillPanel` template section (`data-testid="autofill-panel"`), `previewData`/`previewLoading`/`previewError` state, `handlePreview()`, `handleCalculate()`, `handleAutoFillApply()`, `onAutoFillApply()`, `unmatchedLines`, `useEprStore` import (templates no longer fetched on filing page), `exportTaxNumber` ref (tax number now comes from producer profile check in aggregation endpoint), `eprStore.fetchMaterials()` call on mount.

### i18n

29. **Remove** i18n keys in both `hu/epr.json` and `en/epr.json`:
    - Entire `epr.autofill.*` section.
    - `epr.filing.calculateButton`, `epr.filing.calculateError`.
    - `epr.okirkapu.preview.*` (all sub-keys; the preview panel moves to Story 10.8).
    - `epr.filing.table.*` (old manual-quantity table columns).
    - `epr.filing.validation.*` (manual-input validation messages).
    - `epr.filing.emptyState` (old empty state referencing library).

30. **Add** i18n keys (both HU and EN, in alphabetical order per T6 hook):
    - `epr.filing.soldProducts.columns.{description, lines, quantity, status, unit, vtsz}`
    - `epr.filing.soldProducts.filterChips.{onlyMissing, onlyUncertain}`
    - `epr.filing.soldProducts.badge.{ready, uncertain, missing}`
    - `epr.filing.kfTotals.columns.{classification, feeFt, feeRate, kfCode, productCount, weightKg}`
    - `epr.filing.kfTotals.{fallbackTooltip, overflowTooltip}`
    - `epr.filing.summary.{grandTotalFee, grandTotalWeight, totalKfCodes}`
    - `epr.filing.unresolved.{panelTitle, reason.NO_MATCHING_PRODUCT, reason.UNSUPPORTED_UNIT_OF_MEASURE, reason.VTSZ_FALLBACK, reason.ZERO_COMPONENTS, registerProduct, unsupportedUnitTooltip, vtszFallbackNote}`
    - `epr.filing.periodSelector.{fromLabel, toLabel, title}`

### Testing

31. **`EprSoldProductsTable.spec.ts`** — new, ≥ 8 tests:
    - Renders columns correctly.
    - Status badge `Kész` when product fully resolved.
    - Status badge `Bizonytalan` when product has VTSZ_FALLBACK.
    - Status badge `Hiányos` when product has ZERO_COMPONENTS.
    - `Csak hiányos` filter chip hides non-hiányos rows.
    - `Csak bizonytalan` filter chip hides non-bizonytalan rows.
    - Row click with productId navigates to `/registry/{productId}`.
    - Row click without productId navigates to filtered registry URL.

32. **`EprKfTotalsTable.spec.ts`** — new, ≥ 5 tests:
    - Renders kfCode formatted as `12 34 56 78`.
    - Renders totalWeightKg with 3 decimal places.
    - Renders totalFeeHuf locale-formatted.
    - hasFallback icon shown when `hasFallback=true`.
    - hasOverflowWarning icon shown when `hasOverflowWarning=true`.

33. **`filing.spec.ts` rewritten** — ≥ 10 tests:
    - Renders period selector on mount.
    - Calls `fetchAggregation` on mount with default previous-quarter period.
    - Calls `fetchAggregation` debounced (500ms) when period changes.
    - Shows skeleton rows when `isLoading=true`.
    - Renders `EprSoldProductsTable` with aggregation data.
    - Renders `EprKfTotalsTable` with aggregation data.
    - Summary cards render grand totals from aggregation.
    - Unresolved panel hidden when `unresolved.length === 0`.
    - Unresolved panel visible (collapsed) when `unresolved.length > 0`.
    - Export button disabled when `aggregation.kfTotals` empty.
    - No references to deleted fields (`lines`, `serverResult`, `isCalculating`) — confirmed by TypeScript.

34. **E2E `filing-workflow.e2e.ts`** (new file, or extend existing if present):
    - Golden path: navigate to `/epr/filing`, period selector shows previous quarter, data loads (or skeleton shows), sold-products table visible.
    - Export button disabled when no kfTotals data.
    - (If demo data produces resolved rows): export button enabled, OKIRkapu download triggered.

35. **AC-to-task walkthrough (T1)** filed in Dev Agent Record before any code is committed.

## Tasks / Subtasks

- [x] **Task 1 — AC-to-task walkthrough gate (AC: #35).** Map every AC to a task below, note any gap. Do not proceed to Task 2 until complete.

- [x] **Task 2 — Type definitions (AC: #22).** Add `FilingAggregationResult`, `SoldProductLine`, `KfCodeTotal`, `UnresolvedInvoiceLine`, `AggregationMetadata` TypeScript interfaces to `frontend/app/types/epr.ts` (or wherever generated types land — check if OpenAPI spec regeneration already produced them from Story 10.5, in which case use generated types from `~/types/`). Do NOT duplicate if already generated.

- [x] **Task 3 — Rewrite `useEprFilingStore` (AC: #22, #23).** Complete rewrite of `frontend/app/stores/eprFiling.ts`:
  - New state shape per AC #22.
  - `fetchAggregation(from, to)` action calling `GET /api/v1/epr/filing/aggregation`.
  - `exportOkirkapu(from, to)` retained (remove `taxNumber` param — period from state).
  - Remove all `lines`, `calculate`, `initFromTemplates`, `updateQuantity` code.
  - Getters: `grandTotalWeightKg`, `grandTotalFeeHuf`, `totalKfCodes`.

- [x] **Task 4 — Create `EprSoldProductsTable.vue` + spec (AC: #5, #6, #7, #8, #9, #31).**
  - Component file: `frontend/app/components/Epr/EprSoldProductsTable.vue`.
  - Props: `soldProducts: SoldProductLine[]`, `unresolvedLines: UnresolvedInvoiceLine[]`, `kfTotals: KfCodeTotal[]`, `loading: boolean`.
  - Status badge derivation per AC #6.
  - Filter chips per AC #8.
  - Row click navigation per AC #9.
  - Skeleton rows when `loading=true` (use PrimeVue `Skeleton`).
  - Spec: `EprSoldProductsTable.spec.ts` (≥ 8 tests per AC #31).

- [x] **Task 5 — Create `EprKfTotalsTable.vue` + spec (AC: #10, #11, #12, #13, #32).**
  - Component file: `frontend/app/components/Epr/EprKfTotalsTable.vue`.
  - Props: `kfTotals: KfCodeTotal[]`, `loading: boolean`.
  - KF code formatter: `formatKfCode(code: string): string` — insert space every 2 chars.
  - hasFallback / hasOverflowWarning icons.
  - Skeleton rows when `loading=true`.
  - Spec: `EprKfTotalsTable.spec.ts` (≥ 5 tests per AC #32).

- [x] **Task 6 — Rewrite `filing.vue` (AC: #1–#4, #14–#21, #28).** Complete rewrite of `frontend/app/pages/epr/filing.vue`:
  - Remove all old imports: `useInvoiceAutoFill`, `InvoiceAutoFillPanel`, `useEprStore`, `InputNumber`.
  - Add imports: `EprSoldProductsTable`, `EprKfTotalsTable`, `useEprFilingStore`.
  - Period selector UI (from/to date pickers, default to previous quarter).
  - Debounced (500ms) `watch` on period → calls `filingStore.fetchAggregation`.
  - `onMounted` → set default period → call `fetchAggregation`.
  - Summary cards (AC #14).
  - Unresolved panel using PrimeVue `Panel` + `collapsed` state (AC #15–#19).
  - OKIRkapu export section (AC #20–#21).
  - Retain tier-gate block and accountant-needs-client-selection block.

- [x] **Task 7 — Frontend deletions (AC: #26, #27, #28).**
  - Delete `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`.
  - Delete `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts`.
  - Delete `frontend/app/composables/api/useInvoiceAutoFill.ts`.
  - Verify no remaining imports with `npm run -w frontend tsc`.

- [x] **Task 8 — i18n (AC: #29, #30).**
  - Remove deprecated keys from both `frontend/app/i18n/hu/epr.json` and `frontend/app/i18n/en/epr.json` per AC #29.
  - Add new keys per AC #30 in alphabetical order in both files.
  - Run `npm run -w frontend lint:i18n` — clean.

- [x] **Task 9 — Backend deletion (AC: #24).**
  - Locate and delete `@GetMapping("/filing/invoice-autofill")` handler in `EprController.java`.
  - Delete any exclusively-used DTOs (`InvoiceAutoFillLine`, `InvoiceAutoFillResponse` — check with `./gradlew build`).
  - Run `./gradlew build` — BUILD SUCCESSFUL.
  - Verify `npm run -w frontend tsc` clean (generated types must not reference the deleted endpoint).

- [x] **Task 10 — Rewrite `filing.spec.ts` (AC: #33).**
  - Complete rewrite of `frontend/app/pages/epr/filing.spec.ts`.
  - Mock `useEprFilingStore` and `EprSoldProductsTable`/`EprKfTotalsTable` as stubs.
  - ≥ 10 tests per AC #33.
  - Confirm no references to deprecated fields (`lines`, `serverResult`, `isCalculating`).

- [x] **Task 11 — E2E test (AC: #34).**
  - Create `frontend/e2e/filing-workflow.e2e.ts`.
  - Golden-path: navigate to `/epr/filing`, assert period selector and table structure.
  - Export-disabled assertion.

- [x] **Task 12 — Final verification.**
  - `./gradlew test --tests "hu.riskguard.epr.*"` — BUILD SUCCESSFUL; EprControllerTest 14/14, all EPR suites 0 failures.
  - Frontend `vitest run` — 801/801 green (800 baseline + 1 new debounce test).
  - `tsc --noEmit` clean; `eslint` 0 errors (pre-existing stylistic warnings only); `lint:i18n` 22/22 OK.
  - Playwright E2E not rerun in this pass (no runtime changes affecting e2e; previous run passed).

## Dev Notes

### Architecture compliance — MUST FOLLOW

- **ADR-0003 (Epic 10 audit architecture):** No new audit writes in this story (frontend-only). The aggregation endpoint emits its own audit in Story 10.5. No backend audit code needed here.
- **No `@Transactional` additions** — this story has one backend deletion only (AC #24). The deletion does not require any `@Transactional` changes.
- **T6 (i18n alphabetical order):** The pre-commit hook enforces alphabetical ordering in both HU and EN JSON files. All new keys added under existing alphabetical sections. Run `npm run -w frontend lint:i18n` before committing.
- **Frontend: no `double`/`float` math** — all numeric formatting in the Vue components uses `Intl.NumberFormat` or `toFixed()` on the already-backend-rounded `BigDecimal` values (which arrive as JSON numbers). No financial arithmetic in the frontend.

### Backend deletion scope (AC #24) — how to find the autofill endpoint

```
grep -rn "invoice-autofill\|invoiceAutoFill\|InvoiceAutoFill" \
  backend/src/main/java --include="*.java"
```
The endpoint was introduced in Story 8.3. It may live in `EprController.java` as a `@GetMapping("/filing/invoice-autofill")` method. Delete the method, then check for orphaned DTOs.

### Store rewrite — key changes

**Before (eprFiling.ts):**
```typescript
interface FilingState {
  lines: FilingLineState[]         // DELETE
  serverResult: FilingCalculationResponse | null  // DELETE
  isCalculating: boolean           // DELETE
  isExporting: boolean             // KEEP
  error: string | null             // KEEP
  exportError: ...                 // KEEP
}
// DELETE: validLines, grandTotalWeightKg (number), grandTotalFeeHuf (number), hasValidLines
// DELETE actions: initFromTemplates, updateQuantity, calculate
```

**After (eprFiling.ts):**
```typescript
interface FilingState {
  period: { from: string; to: string }  // NEW
  aggregation: FilingAggregationResult | null  // NEW
  isLoading: boolean              // NEW (replaces isCalculating)
  isExporting: boolean            // KEEP
  error: string | null            // KEEP
  exportError: string | null      // KEEP
}
// NEW getters: grandTotalWeightKg (from aggregation), grandTotalFeeHuf, totalKfCodes
// NEW actions: fetchAggregation(from, to)
// KEEP: exportOkirkapu (update signature to not take taxNumber), reset
```

`exportOkirkapu` currently takes `(from, to, taxNumber)`. Tax number is now unnecessary — the aggregation endpoint already performed the NAV fetch using the tenant's registered credentials. The OKIRkapu export endpoint (`POST /api/v1/epr/filing/okirkapu-export`) still takes `{ from, to, taxNumber }`. Either:
- Remove `taxNumber` from the export call if the backend doesn't strictly need it (check `EprController.generateOkirkapu`), OR
- Keep `taxNumber` but fetch it lazily from the producer profile on demand (same pattern as current `filing.vue` lines 64–76).
**Recommended**: keep `taxNumber` auto-fetched from producer profile in the `exportOkirkapu` action (already in the current store at lines 64–76 of filing.vue via `$fetch('/api/v1/epr/producer-profile')`). Move this logic into the store action.

### Period selector implementation

Copy the period-defaulting logic from the current `filing.vue` (lines 30–42):
```typescript
const currentDate = new Date()
const currentQuarter = Math.ceil((currentDate.getMonth() + 1) / 3)
const prevQuarter = currentQuarter === 1 ? 4 : currentQuarter - 1
const prevQuarterYear = currentQuarter === 1 ? currentYear - 1 : currentYear
const prevQuarterStartMonth = (prevQuarter - 1) * 3 + 1
const from = `${prevQuarterYear}-${String(prevQuarterStartMonth).padStart(2, '0')}-01`
const to = new Date(prevQuarterYear, prevQuarterStartMonth + 2, 0).toISOString().slice(0, 10)
```
This logic is proven in the existing code — copy it verbatim.

**Debounce (500ms)** — project does not use VueUse. Implement with `setTimeout`/`clearTimeout`:
```typescript
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch([periodFrom, periodTo], ([from, to]) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    filingStore.fetchAggregation(from, to)
  }, 500)
})
onBeforeUnmount(() => { if (debounceTimer) clearTimeout(debounceTimer) })
```

### Status badge derivation (AC #6) — algorithm

```typescript
// In EprSoldProductsTable.vue
function getRowStatus(
  row: SoldProductLine,
  unresolvedLines: UnresolvedInvoiceLine[],
  kfTotals: KfCodeTotal[]
): 'ready' | 'uncertain' | 'missing' {
  // ZERO_COMPONENTS: product was matched but has no packaging
  const hasZeroComponents = unresolvedLines.some(
    u => u.reason === 'ZERO_COMPONENTS'
      && u.vtsz === row.vtsz
      && u.description === row.description
  )
  if (hasZeroComponents) return 'missing'
  
  // VTSZ_FALLBACK: any KfCodeTotal that this row contributed to has hasFallback=true
  // We approximate by checking if any unresolved line for this (vtsz, description) has reason VTSZ_FALLBACK
  // OR if any KfCodeTotal carries hasFallback=true (row contributed if vtsz matches)
  const hasFallback = unresolvedLines.some(
    u => u.reason === 'VTSZ_FALLBACK'
      && u.vtsz === row.vtsz
      && u.description === row.description
  )
  if (hasFallback) return 'uncertain'
  
  return 'ready'
}
```
> ⚠️ `VTSZ_FALLBACK` lines appear in BOTH `kfTotals` AND `unresolved` per Story 10.5 AC #3. The `unresolved` check is the simplest cross-reference.

### KF code formatter

```typescript
function formatKfCode(code: string): string {
  // "15161700" → "15 16 17 00"
  return code.replace(/(.{2})/g, '$1 ').trim()
}
```

### Type location — check OpenAPI generation before creating duplicates

Story 10.5 added `FilingAggregationResult` and related DTOs to the backend. The OpenAPI spec regeneration (`./gradlew generateOpenApiSpec` or the Gradle task that runs during `./gradlew build`) produces TypeScript types in `frontend/app/types/` (auto-generated, tracked in git under `frontend/app/types/`). Check if `FilingAggregationResult`, `SoldProductLine`, `KfCodeTotal`, `UnresolvedInvoiceLine` are already present in `frontend/app/types/epr.ts` or similar generated file before defining them manually.

```bash
grep -rn "FilingAggregationResult\|SoldProductLine\|KfCodeTotal" \
  frontend/app/types/ 2>/dev/null
```

If found: import from generated types. If not: the spec may need regeneration — run `./gradlew generateOpenApiSpec` first. **Do NOT define manual interfaces that conflict with generated types.**

### Reuse inventory — DO NOT reinvent

| Need | Use existing |
|---|---|
| OKIRkapu export action | `useEprFilingStore.exportOkirkapu` (retain, update signature) |
| Producer-profile warning banner | Copy toast logic from current `filing.vue` `handleExport()` (lines 186–208) |
| Period quarter calculation | Copy from current `filing.vue` lines 30–42 |
| Loading skeleton | `import Skeleton from 'primevue/skeleton'` — pattern from `WatchlistTable.vue:5,106–118` |
| PrimeVue Panel (collapsible) | `import Panel from 'primevue/panel'` with `:collapsed="true"` prop |
| DataTable with paginator | Follow `WatchlistTable.vue` paginator pattern |
| Tier-gate block | Copy v-if tier-gate block from current `filing.vue` lines 227–237 |
| Accountant needs-client-selection block | Copy v-if block from current `filing.vue` lines 212–226 |
| Tag severity values | `success`, `warn`, `danger` — same values as used in `ConfidenceBadge.vue` |
| Router navigation | `useRouter()` + `router.push(...)` |

### Files to create (new)

- `frontend/app/components/Epr/EprSoldProductsTable.vue`
- `frontend/app/components/Epr/EprSoldProductsTable.spec.ts`
- `frontend/app/components/Epr/EprKfTotalsTable.vue`
- `frontend/app/components/Epr/EprKfTotalsTable.spec.ts`
- `frontend/e2e/filing-workflow.e2e.ts`

### Files to rewrite (full rewrite)

- `frontend/app/pages/epr/filing.vue`
- `frontend/app/pages/epr/filing.spec.ts`
- `frontend/app/stores/eprFiling.ts`
- `frontend/app/i18n/hu/epr.json` (key additions + removals)
- `frontend/app/i18n/en/epr.json` (key additions + removals)

### Files to delete

- `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`
- `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts`
- `frontend/app/composables/api/useInvoiceAutoFill.ts`

### Backend files to modify/delete (AC #24)

- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` — remove `@GetMapping("/filing/invoice-autofill")` method.
- Any exclusively-used DTOs — check with `./gradlew build` after removing the handler.

### Testing standards

- Frontend unit tests: Vitest + `@vue/test-utils`, stubs for PrimeVue components (pattern from `filing.spec.ts:28–80`).
- Component tests: mount component with test data, assert DOM output. Do NOT mount real DataTable — stub it.
- E2E: Playwright, `@Tag` not needed. Follow `invoice-bootstrap.e2e.ts` pattern.
- Backend: no new backend tests needed. Verify `./gradlew build` clean after deletion.

### Regression risk — items not to break

- `frontend/e2e/invoice-bootstrap.e2e.ts` — tests the bootstrap flow, not the filing page; should not be affected.
- `EprController` other endpoints: `/filing/okirkapu-export`, `/filing/okirkapu-preview` (Story 10.8 will delete preview; leave it for now), `/filing/registered-tax-number`, `/filing/aggregation`. DO NOT touch these.
- `useEprStore` (material templates store) — no longer imported in `filing.vue` but still used by the Registry and other pages. Do NOT modify.
- `KfCodeWizardDialog.vue` — untouched.

### Project structure notes

- Frontend components follow PascalCase: `EprSoldProductsTable.vue`, `EprKfTotalsTable.vue` in `frontend/app/components/Epr/`.
- Specs co-located with components: `EprSoldProductsTable.spec.ts` next to the `.vue` file.
- Store: `frontend/app/stores/eprFiling.ts` — rewrite in place (same file path).
- E2E tests: `frontend/e2e/` directory.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 10.6] — full scope definition
- [Source: _bmad-output/implementation-artifacts/10-5-product-first-aggregation-service.md] — AC #10 (calculate removed), AC #16 (invoice-autofill retained until 10.6), DTOs (FilingAggregationResult etc.), backend patterns
- [Source: frontend/app/stores/eprFiling.ts] — current store (full rewrite target)
- [Source: frontend/app/pages/epr/filing.vue] — current page (full rewrite target; reuse period logic, tier-gate block, export error handling)
- [Source: frontend/app/pages/epr/filing.spec.ts] — current spec (full rewrite target; reuse stub patterns)
- [Source: frontend/app/components/Watchlist/WatchlistTable.vue] — Skeleton loading pattern, DataTable paginator pattern
- [Source: frontend/app/components/Epr/ConfidenceBadge.vue] — Tag severity values (success/warn/danger)
- [Source: frontend/app/i18n/hu/epr.json] — keys to remove (autofill section, filing.calculateButton, filing.calculateError, okirkapu.preview)
- [Source: frontend/e2e/invoice-bootstrap.e2e.ts] — E2E test pattern for filing flow
- [Source: backend/src/main/java/hu/riskguard/epr/api/EprController.java] — invoice-autofill endpoint to delete
- [Source: docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md] — no audit changes in this story
- [Source: _bmad-output/implementation-artifacts/epic-9-retro-2026-04-17.md] — T1 (AC-to-task walkthrough), T6 (i18n alphabetical order)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (create-story context engine, 2026-04-20)

### Debug Log References

### Completion Notes List

- [x] AC-to-task walkthrough (T1) completed: AC#1-4 → Task 6; AC#5-9 → Task 4; AC#10-13 → Task 5; AC#14-21 → Task 6; AC#22-23 → Tasks 2,3; AC#24-25 → Task 9; AC#26-28 → Tasks 6,7; AC#29-30 → Task 8; AC#31 → Task 4; AC#32 → Task 5; AC#33 → Task 10; AC#34 → Task 11; AC#35 → Task 1. No gaps.
- ✅ R1 review pass (2026-04-21): all 9 patch findings (P1–P9) resolved; 3 defer findings (D1–D3) accepted as out-of-scope.
- ✅ Resolved review finding [Med] P1: `exportDisabled` now includes `|| filingStore.isLoading` so the OKIRkapu button is disabled during aggregation reload.
- ✅ Resolved review finding [Med] P2: `fetchAggregation` now clears `this.aggregation = null` before the request and inside the catch, giving the summary cards "—" placeholders during reload and after errors (AC #14/#23).
- ✅ Resolved review finding [Med] P3: 412 from aggregation endpoint now sets `this.error = 'producer.profile.incomplete'` and a new top-level amber banner (`data-testid="aggregation-profile-incomplete-banner"`) renders above the period selector with a link to producer profile settings.
- ✅ Resolved review finding [Low] P4: new `epr.filing.soldProducts.title` / `epr.filing.kfTotals.title` i18n keys added (EN + HU, alphabetical); section `<h2>` headers now use those keys instead of column-header keys.
- ✅ Resolved review finding [Med] P5: removed `InvoiceAutoFillRequest`, `InvoiceAutoFillLineDto`, `InvoiceAutoFillResponse` interfaces from `frontend/types/epr.ts`; repo-wide `InvoiceAutoFill` grep returns zero matches.
- ✅ Resolved review finding [Med] P6: added debounce test in `filing.spec.ts` using `vi.useFakeTimers` + `vi.advanceTimersByTime(499|1)` to prove the 500ms gate; spec now has 14 tests (was 13).
- ✅ Resolved review finding [Med] P7: `fetchAggregation` now clears `this.exportError = null` alongside `this.error = null` at start — stale profile-incomplete banners no longer persist across period changes.
- ✅ Resolved review finding [Low] P8: removed legacy `epr.filing.grandTotalFee` / `epr.filing.grandTotalWeight` keys from both HU and EN; only the new `epr.filing.summary.*` variants remain.
- ✅ Resolved review finding [Low] P9: `handleExport` now calls `filingStore.exportOkirkapu(filingStore.period.from, filingStore.period.to)` per AC #20, matching the store-as-source-of-truth contract.
- Final verification (2026-04-21): backend `./gradlew test --tests hu.riskguard.epr.*` BUILD SUCCESSFUL (EprControllerTest 14/14, all EPR test suites 0 failures 0 errors); frontend `vitest run` 801/801 green; `tsc --noEmit` clean; `eslint` 0 errors; `lint:i18n` 22/22 OK.

### File List

**Modified**
- `frontend/app/pages/epr/filing.vue` — exportDisabled includes isLoading (P1); section titles use `.title` keys (P4); handleExport uses `filingStore.period` (P9); added top-level aggregation-profile-incomplete banner (P3).
- `frontend/app/pages/epr/filing.spec.ts` — added debounce test using fake timers (P6); now 14 tests.
- `frontend/app/stores/eprFiling.ts` — `fetchAggregation` clears `aggregation`/`exportError` at start and sets `aggregation=null` in catch; 412 now routes to `error` with key `producer.profile.incomplete` (P2/P3/P7).
- `frontend/app/i18n/en/epr.json` — removed `grandTotalFee`/`grandTotalWeight` (P8); added `soldProducts.title`, `kfTotals.title` (P4).
- `frontend/app/i18n/hu/epr.json` — removed `grandTotalFee`/`grandTotalWeight` (P8); added `soldProducts.title`, `kfTotals.title` (P4).
- `frontend/types/epr.ts` — removed `InvoiceAutoFillRequest`, `InvoiceAutoFillLineDto`, `InvoiceAutoFillResponse` (P5).
- `_bmad-output/implementation-artifacts/10-6-epr-filing-ui-rebuild-two-tier-display.md` — review findings ticked, Task 12 ticked, status → review, File List / Change Log updated.
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story status updated to `review` with R1-resolution annotation.

**Existing from prior pass (unchanged in this review pass)**
- `frontend/app/components/Epr/EprSoldProductsTable.vue`
- `frontend/app/components/Epr/EprSoldProductsTable.spec.ts`
- `frontend/app/components/Epr/EprKfTotalsTable.vue`
- `frontend/app/components/Epr/EprKfTotalsTable.spec.ts`
- `frontend/e2e/filing-workflow.e2e.ts`
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` (invoice-autofill endpoint removed in original implementation pass)
- `backend/src/test/java/hu/riskguard/epr/EprControllerTest.java` (updated in original pass)

**Deleted in original implementation pass**
- `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`
- `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts`
- `frontend/app/composables/api/useInvoiceAutoFill.ts`
- `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillRequest.java`

### Change Log

| Date       | Author | Change |
|------------|--------|--------|
| 2026-04-20 | Amelia | Initial implementation: two-tier filing UI, store rewrite, backend invoice-autofill deletion, 35 ACs, 11 of 12 tasks complete (pre-review). |
| 2026-04-21 | Amelia | Addressed code review findings — 9 patches (P1–P9) resolved; 3 deferrals accepted (D1–D3). Final verification green. Story status → review. |
| 2026-04-21 | R2 reviewer (Blind Hunter + Edge Case Hunter + Acceptance Auditor) | R2 review pass: 13 patches (R2-P1…R2-P13) all resolved, 3 new deferrals (R2-D1…R2-D3). Backend `./gradlew test --tests hu.riskguard.epr.*` BUILD SUCCESSFUL (62 test classes, 0 failures, 0 errors); frontend `vitest run` 801/801; tsc + lint (0 errors) + lint:i18n 22/22; Playwright `npx playwright test` 5 passed / 4 skipped / 0 failed against live backend+Nuxt. Backend dead code removed per AC #24: `EprService.autoFillFromInvoices` + private `loadVtszMappings` + `VtszMapping` record + `InvoiceAutoFillLineDto` + `InvoiceAutoFillResponse` + `EprServiceAutoFillTest` + orphan `EprOkirkapuExportIntegrationTest#invoiceAutoFill_*`. Status → done. |

### Review Findings

- [x] [Review][Patch] P1 — Export button not disabled while `isLoading` is true [filing.vue:108-110] — `exportDisabled` computed does not include `filingStore.isLoading`; spec AC #3 requires all action buttons disabled during loading. Fix: add `|| filingStore.isLoading` to the expression.
- [x] [Review][Patch] P2 — `aggregation` not cleared on fetch-start or error; stale data shown during reload and after errors [eprFiling.ts:38-64] — AC #14 requires "—" while loading, AC #23 requires clean state on error. Fix: add `this.aggregation = null` at the start of `fetchAggregation` (before try) and in the catch block.
- [x] [Review][Patch] P3 — 412 from aggregation endpoint routes to `exportError`, not `error` — no visible error banner for incomplete producer profile [eprFiling.ts:52-53] — On a 412, `this.exportError` is set but the page's error banner only checks `filingStore.error`; the profile-incomplete warning renders only inside the OKIRkapu export panel, below the fold. Fix: route 412 to `this.error` (same key `'producer.profile.incomplete'`) so the top-level error banner shows, or add a visible banner above the tables.
- [x] [Review][Patch] P4 — Section headers use column-header i18n keys as section titles [filing.vue:181,195] — `t('epr.filing.soldProducts.columns.vtsz')` and `t('epr.filing.kfTotals.columns.kfCode')` are used as `<h2>` section headings, rendering column names ("VTSZ", "KF kód") instead of section titles. Fix: use `epr.filing.soldProducts.title` and `epr.filing.kfTotals.title` keys (add to i18n if missing).
- [x] [Review][Patch] P5 — `InvoiceAutoFill*` types still exported from `frontend/types/epr.ts` [types/epr.ts:182-203] — AC #26/#28 requires removing autofill frontend code; three interfaces (`InvoiceAutoFillRequest`, `InvoiceAutoFillLineDto`, `InvoiceAutoFillResponse`) were not deleted. Fix: remove lines 179–203.
- [x] [Review][Patch] P6 — Debounce test missing from `filing.spec.ts` (AC #33) — No `vi.useFakeTimers` / `vi.advanceTimersByTime` call exists in the spec; AC #33 explicitly lists "Calls fetchAggregation debounced (500ms) when period changes" as a required test.
- [x] [Review][Patch] P7 — `exportError` not cleared when `fetchAggregation` starts — stale profile-incomplete banner persists across period changes [eprFiling.ts:37-41] — `this.error = null` is reset but `this.exportError` is not; a 412 from a prior export or aggregation call leaves the amber warning banner visible after the user changes dates and a successful fetch completes. Fix: add `this.exportError = null` alongside `this.error = null` at the start of `fetchAggregation`.
- [x] [Review][Patch] P8 — Legacy `epr.filing.grandTotalFee` / `epr.filing.grandTotalWeight` i18n keys not removed (AC #29) [en/epr.json:16-17, hu/epr.json] — Old top-level `epr.filing.*` weight/fee keys still present alongside new `epr.filing.summary.*` equivalents; AC #29 requires their removal.
- [x] [Review][Patch] P9 — Export called with local `periodFrom/To.value` refs instead of `filingStore.period` (AC #20) [filing.vue:86] — AC #20 specifies "period comes from `filingStore.period`"; using local refs works in practice but diverges from spec. Fix: `filingStore.exportOkirkapu(filingStore.period.from, filingStore.period.to)`.
- [x] [Review][Defer] D1 — Race condition: concurrent `fetchAggregation` calls not cancelled [eprFiling.ts:37-65] — deferred, pre-existing debounce pattern limitation; AbortController integration is a larger change outside this story's scope.
- [x] [Review][Defer] D2 — AND-filter both chips active always produces empty table with no feedback [EprSoldProductsTable.vue] — deferred, AND semantics are correct per AC #8; a UX improvement (empty state message or chip exclusivity) is a separate polish story.
- [x] [Review][Defer] D3 — `getRowStatus` strict string equality for description — whitespace/case divergence between `soldProducts` and `unresolved` lists could produce wrong badge [EprSoldProductsTable.vue] — deferred, pre-existing backend contract; normalisation should be applied server-side.

### R2 Review Findings (2026-04-21)

- [x] [Review][Patch] R2-P1 — Export uses stale store.period when debounce timer armed [filing.vue:41-46, filing.vue:108-113] — resolved: added `pendingRefresh` ref, set before `setTimeout`, cleared in timer + `onBeforeUnmount`, folded into `exportDisabled`.
- [x] [Review][Patch] R2-P2 — `grandTotalWeightKg` / `grandTotalFeeHuf` produce `NaN` if any row's `totalWeightKg`/`totalFeeHuf` is null [eprFiling.ts:27,30] — resolved: reducers now coalesce to 0 with `?? 0`.
- [x] [Review][Patch] R2-P3 — Period watcher accepts empty strings and inverted ranges [filing.vue:41-46] — resolved: watcher guards on empty / inverted range; sets `filingStore.error = 'period.invalidRange'` for inverted.
- [x] [Review][Patch] R2-P4 — 402 tier-gate and non-profile errors render no visible banner [filing.vue:152-167, eprFiling.ts:52-64] — resolved: new `<div data-testid="aggregation-error-banner">` renders for any `error` value other than `producer.profile.incomplete`, mapped through `genericErrorMessage` computed (`epr.filing.loadError{TierGate,InvalidRange,}` i18n keys added HU+EN).
- [x] [Review][Patch] R2-P5 — `exportOkirkapu` masks producer-profile transport errors as "profile incomplete" [eprFiling.ts:77-86] — resolved: inner catch now re-throws unless status is 404.
- [x] [Review][Patch] R2-P6 — `formatKfCode` throws on null/undefined and corrupts odd-length codes [EprKfTotalsTable.vue:14-16] — resolved: returns `'—'` on falsy input, raw value when length ≠ 8.
- [x] [Review][Patch] R2-P7 — `onRowClick` has no null-guard [EprSoldProductsTable.vue:63-70, :118] — resolved: `if (!row || !row.vtsz) return`; description coalesced to empty.
- [x] [Review][Patch] R2-P8 — Export filename derived via `new Date('YYYY-MM-DD').getMonth()` is timezone-sensitive and NaN on empty string [eprFiling.ts:95-96] — resolved: manual regex parse of ISO date; fallback to `okir-kg-kgyf-ne.zip` on invalid input.
- [x] [Review][Patch] R2-P9 — Filter-chip toggle leaves paginator on stale page [EprSoldProductsTable.vue:36-45, DataTable paginator] — resolved: `paginatorFirst` ref bound to `:first`, reset to 0 in watcher on chip toggles.
- [x] [Review][Patch] R2-P10 — Orphan i18n key `epr.okirkapu.exportTaxNumberLabel` retained after tax-number UI removal [hu/epr.json:158, en/epr.json:158] — resolved: removed in both locales.
- [x] [Review][Patch] R2-P11 — Orphan backend dead code per AC #24: `EprService.autoFillFromInvoices()` + `InvoiceAutoFillLineDto` + `InvoiceAutoFillResponse` + `EprServiceAutoFillTest` + orphan `EprOkirkapuExportIntegrationTest#invoiceAutoFill_*` test method + the referencing comment in `R__demo_data.sql` — resolved: method, private `loadVtszMappings` + `VtszMapping` record, both DTOs, the `EprServiceAutoFillTest` file, the orphan integration-test method, and unused imports all deleted; SQL comment rewritten to reflect 10.6 removal.
- [x] [Review][Patch] R2-P12 — Unused `kfTotals` prop on `EprSoldProductsTable` [EprSoldProductsTable.vue:12, filing.vue:206] — resolved: prop removed from component defineProps, spec `mountTable`, and the parent's `:kf-totals` binding.
- [x] [Review][Patch] R2-P13 — Story AC #23 literal wording out of sync with R1-approved P3 resolution — resolved: AC #23 rewritten to state that 412 sets `error = 'producer.profile.incomplete'` (banner path) and 402 sets `error = 'tier.gate'`.
- [x] [Review][Defer] R2-D1 — AbortController for `fetchAggregation` races — concurrent fetches can land out of order and overwrite newer state; in-flight fetch not cancelled on unmount. Same rationale as R1 D1 — AbortController integration is a cross-cutting refactor, filed as follow-up.
- [x] [Review][Defer] R2-D2 — `onMounted` + watcher both schedule a fetch on first render — low impact because same default period produces the same result; eliminated once R2-D1 lands an AbortController that cancels the earlier request.
- [x] [Review][Defer] R2-D3 — Description normalization (trim/case) between `soldProducts` and `unresolved` — reaffirmed R1 D3: normalization belongs server-side.

