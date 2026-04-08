# Story 8.6: Invoice-to-Material Traceability

Status: ready-for-dev

## Story

As a RiskGuard user reviewing EPR auto-fill results,
I want to see which invoices contributed to each material line in the auto-fill table,
so that I can verify the VTSZ-to-material mapping and quantity aggregation are correct before applying them to my filing.

### Background

Story 8.3 introduced invoice-driven EPR auto-fill. The `autoFillFromInvoices()` method in `EprService` fetches invoice summaries from NAV, iterates each invoice's line items, groups by `VtszUnitKey(vtszCode, unit)`, and sums quantities into `InvoiceAutoFillLineDto`. The per-invoice detail is available inside the loop but is discarded after aggregation. Users see only the totals and cannot verify which invoices contributed what quantities. This story retains that per-invoice breakdown and surfaces it via row expansion in the frontend DataTable.

## Acceptance Criteria

1. **Backend: `ContributingInvoiceDto` record** -- New record `ContributingInvoiceDto` in `epr/api/dto/` with fields: `invoiceNumber` (String), `issueDate` (LocalDate), `partnerName` (String), `quantity` (BigDecimal), `lineDescription` (String). Must have `static from()` factory method per ArchUnit naming convention rule.

2. **Backend: `InvoiceAutoFillLineDto` includes invoice breakdown** -- `InvoiceAutoFillLineDto` gains a new field `List<ContributingInvoiceDto> contributingInvoices`. The sum of all `contributingInvoices[].quantity` equals the line's `aggregatedQuantity`. Existing fields unchanged; serialization backward-compatible (new field defaults to empty list if absent).

3. **Backend: `autoFillFromInvoices` retains per-invoice detail** -- The aggregation loop in `EprService.autoFillFromInvoices()` (lines 503-516) still groups by `VtszUnitKey(vtszCode, unit)` and sums totals, but also collects per-invoice contributions: for each invoice line item, record the invoice number (from the outer `InvoiceSummary`), issue date, partner name, quantity, and line description. These are assembled into `ContributingInvoiceDto` instances attached to each `InvoiceAutoFillLineDto`.

4. **Frontend: Row expansion in DataTable** -- `InvoiceAutoFillPanel.vue` DataTable gains row expansion capability. Each row has an expander toggle (PrimeVue `<Column expander />` + `<template #expansion="slotProps">`). Clicking expands to show a nested table/list of contributing invoices with columns: invoice number, date, partner, quantity, description. Follow the existing `audit-history/index.vue` pattern for `v-model:expanded-rows` with `Record<string, boolean>` typing.

5. **Frontend: Expand-all / collapse-all toggle** -- A `Button` above the DataTable toggles between expanding all rows and collapsing all rows. Label toggles between `autofill.expandAll` and `autofill.collapseAll` i18n keys. Implementation: set `expandedRows` to all row keys (expand) or empty object (collapse).

6. **i18n keys added** -- All new column headers and labels added to both `frontend/app/i18n/en/epr.json` and `frontend/app/i18n/hu/epr.json`, alphabetically placed within the `autofill.*` block. Keys: `autofill.collapseAll`, `autofill.contributingInvoices`, `autofill.expandAll`, `autofill.columns.invoiceNumber`, `autofill.columns.issueDate`, `autofill.columns.lineDescription`, `autofill.columns.partnerName`, `autofill.columns.contributingQuantity`.

7. **Tests: Backend** -- `EprServiceAutoFillTest` gains at least 2 new assertions: (a) `contributingInvoices` is populated on each line and contains the expected invoice numbers; (b) sum of `contributingInvoices[].quantity` equals `aggregatedQuantity` for each line.

8. **Tests: Frontend** -- `InvoiceAutoFillPanel.spec.ts` gains at least 3 new tests: (a) row expansion renders contributing invoices table when clicked; (b) expand-all button expands all rows; (c) contributing invoice columns display correct data (invoice number, date, partner, quantity, description).

9. **All existing tests green** -- No regressions. `./gradlew test` BUILD SUCCESSFUL. `cd frontend && npm run test` all passing.

## Tasks / Subtasks

- [ ] Task 1: Create `ContributingInvoiceDto` record (AC: 1)
  - [ ] Create `backend/src/main/java/hu/riskguard/epr/api/dto/ContributingInvoiceDto.java`
  - [ ] Fields: `invoiceNumber` (String), `issueDate` (LocalDate), `partnerName` (String), `quantity` (BigDecimal), `lineDescription` (String)
  - [ ] Add `static from()` factory method per ArchUnit naming convention rule
  - [ ] Verify `NamingConventionTest` passes with the new record

- [ ] Task 2: Expand `InvoiceAutoFillLineDto` (AC: 2)
  - [ ] Add `List<ContributingInvoiceDto> contributingInvoices` field to the record
  - [ ] Update existing record constructor -- add the new field as the last parameter
  - [ ] Update all call sites that construct `InvoiceAutoFillLineDto` (in `EprService.autoFillFromInvoices()`)
  - [ ] Verify JSON serialization includes the new field (springdoc auto-generates OpenAPI)

- [ ] Task 3: Modify `autoFillFromInvoices()` to retain per-invoice detail (AC: 3)
  - [ ] Change the aggregation map from `Map<VtszUnitKey, BigDecimal>` to `Map<VtszUnitKey, List<ContributionEntry>>` where `ContributionEntry` is a local record holding `invoiceNumber`, `issueDate`, `partnerName`, `quantity`, `lineDescription`
  - [ ] In the inner loop (lines 506-516), for each valid line item, add a `ContributionEntry` to the map list instead of only summing
  - [ ] Compute `aggregatedQuantity` by summing the list entries' quantities
  - [ ] Map `ContributionEntry` list to `List<ContributingInvoiceDto>` when building the result lines
  - [ ] Access `summary.invoiceNumber()`, `summary.issueDate()`, and `summary.supplierName()` (or `summary.customerName()` for outbound) from the outer loop's `InvoiceSummary`
  - [ ] Access `item.description()` (or equivalent) from `InvoiceLineItem` for `lineDescription`

- [ ] Task 4: Backend tests (AC: 7)
  - [ ] Add assertion in `EprServiceAutoFillTest`: each `InvoiceAutoFillLineDto` has non-empty `contributingInvoices`
  - [ ] Add assertion: for each line, `contributingInvoices.stream().map(c -> c.quantity()).reduce(BigDecimal::add)` equals `aggregatedQuantity`
  - [ ] Add assertion: `contributingInvoices` entries contain expected invoice numbers from the demo fixtures
  - [ ] Run `./gradlew test --tests "hu.riskguard.epr.*"` -- BUILD SUCCESSFUL

- [ ] Task 5: Update frontend types (AC: 2)
  - [ ] After backend changes, verify `api.d.ts` regeneration includes `ContributingInvoiceDto` and updated `InvoiceAutoFillLineDto`
  - [ ] If types are manually defined in `frontend/types/epr.ts`, update there too
  - [ ] Update `InvoiceAutoFillLineDto` type import in `useInvoiceAutoFill.ts` composable if needed

- [ ] Task 6: Add row expansion to `InvoiceAutoFillPanel.vue` (AC: 4, 5)
  - [ ] Add `const expandedRows = ref<Record<string, boolean>>({})` state
  - [ ] Add `v-model:expanded-rows="expandedRows"` and `data-key="vtszCode"` (or composite key if needed for uniqueness) to the DataTable
  - [ ] Add `<Column expander style="width: 3rem" />` as the first column
  - [ ] Add `<template #expansion="{ data }">` with a nested table showing `data.contributingInvoices`
  - [ ] Nested table columns: invoice number, issue date, partner name, quantity, line description
  - [ ] Add expand-all / collapse-all `Button` above the DataTable with toggle logic
  - [ ] Style the expansion area with `bg-slate-50 rounded p-4` consistent with audit-history pattern

- [ ] Task 7: Add i18n keys (AC: 6)
  - [ ] Add keys to `frontend/app/i18n/en/epr.json` in alphabetical order within `autofill.*` block
  - [ ] Add keys to `frontend/app/i18n/hu/epr.json` in alphabetical order within `autofill.*` block
  - [ ] Keys: `collapseAll`, `columns.contributingQuantity`, `columns.invoiceNumber`, `columns.issueDate`, `columns.lineDescription`, `columns.partnerName`, `contributingInvoices`, `expandAll`

- [ ] Task 8: Frontend tests (AC: 8)
  - [ ] Test: row expansion renders contributing invoices when expander is clicked
  - [ ] Test: expand-all button sets all rows to expanded state
  - [ ] Test: contributing invoice columns display invoice number, date, partner, quantity, description
  - [ ] Run `cd frontend && npm run test` -- all passing

- [ ] Task 9: Full regression check (AC: 9)
  - [ ] `./gradlew test` -- BUILD SUCCESSFUL
  - [ ] `cd frontend && npm run test` -- all passing
  - [ ] Update `sprint-status.yaml`: `8-6-invoice-to-material-traceability: review`
  - [ ] Commit: `feat(8.6): Invoice-to-material traceability in EPR auto-fill`

## Dev Notes

### Key Files to Touch

| File | Change |
|------|--------|
| `backend/.../epr/api/dto/ContributingInvoiceDto.java` | NEW record with `static from()` factory |
| `backend/.../epr/api/dto/InvoiceAutoFillLineDto.java` | Add `contributingInvoices` list field |
| `backend/.../epr/domain/EprService.java` | Modify `autoFillFromInvoices()` aggregation to retain per-invoice detail |
| `backend/.../epr/EprServiceAutoFillTest.java` | Add assertions for `contributingInvoices` population and quantity sum |
| `frontend/app/components/Epr/InvoiceAutoFillPanel.vue` | Add row expansion with nested invoice table + expand-all button |
| `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts` | Add 3+ expansion tests |
| `frontend/app/i18n/en/epr.json` | Add `autofill.expandAll`, `autofill.collapseAll`, `autofill.columns.invoiceNumber`, etc. |
| `frontend/app/i18n/hu/epr.json` | Same keys in Hungarian |
| `frontend/types/epr.ts` | Update types if manually defined (or rely on auto-generated `api.d.ts`) |

### Existing Patterns to Reuse

| Pattern | Where |
|---------|-------|
| PrimeVue DataTable row expansion with `v-model:expanded-rows` | `pages/audit-history/index.vue:19,194` -- `expandedRows = ref<Record<string, boolean>>({})` |
| Expansion template styling | `pages/audit-history/index.vue` -- `<template #expansion="{ data }">` with `bg-slate-50 rounded p-4` |
| `InvoiceAutoFillPanel.vue` existing DataTable | Current implementation at lines 113-153 to extend |
| `vi.mock` for composable | `InvoiceAutoFillPanel.spec.ts` -- existing `vi.mock('~/composables/api/useInvoiceAutoFill', ...)` pattern |
| Record with `static from()` | All DTOs in `epr/api/dto/` -- e.g., `InvoiceAutoFillResponse.from()` |

### Architecture Compliance

- `ContributingInvoiceDto` must have `static from()` factory method per ArchUnit `NamingConventionTest` rule
- New record goes in `epr/api/dto/` package -- same package as `InvoiceAutoFillLineDto`
- No cross-module imports -- all invoice data is already available within `EprService.autoFillFromInvoices()` from the `DataSourceService` facade calls
- No PII logging -- invoice numbers are not PII per project rules

### Critical: Backend Aggregation Refactor

The current aggregation in `EprService.autoFillFromInvoices()` (lines 503-516) uses:

```java
Map<VtszUnitKey, BigDecimal> vtszQuantities = new LinkedHashMap<>();
// ...
vtszQuantities.merge(new VtszUnitKey(item.vtszCode(), unit), item.quantity(), BigDecimal::add);
```

This must change to collect per-invoice details. Recommended approach:

```java
record ContributionEntry(String invoiceNumber, LocalDate issueDate, String partnerName,
                         BigDecimal quantity, String lineDescription) {}
Map<VtszUnitKey, List<ContributionEntry>> vtszContributions = new LinkedHashMap<>();
// ...
vtszContributions
    .computeIfAbsent(new VtszUnitKey(item.vtszCode(), unit), k -> new ArrayList<>())
    .add(new ContributionEntry(
        summary.invoiceNumber(),
        summary.issueDate(),
        summary.supplierName(),  // check actual field name on InvoiceSummary
        item.quantity(),
        item.description()       // check actual field name on InvoiceLineItem
    ));
```

Then compute `aggregatedQuantity` by summing:

```java
BigDecimal quantity = entries.stream()
    .map(ContributionEntry::quantity)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### Critical: InvoiceSummary and InvoiceLineItem Field Names

Before coding, verify the exact field names on the domain records. Check:
- `InvoiceSummary`: likely has `invoiceNumber()`, `issueDate()`, `supplierName()` or `partnerName()` -- grep the record definition in `datasource/domain/InvoiceSummary.java`
- `InvoiceLineItem`: likely has `description()` or `lineDescription()`, `quantity()`, `vtszCode()`, `unitOfMeasure()` -- grep the record definition in `datasource/domain/InvoiceLineItem.java`

```bash
grep -n "record InvoiceSummary" backend/src/main/java/hu/riskguard/datasource/domain/InvoiceSummary.java
grep -n "record InvoiceLineItem" backend/src/main/java/hu/riskguard/datasource/domain/InvoiceLineItem.java
```

### Critical: DataTable data-key for Expansion

The current DataTable in `InvoiceAutoFillPanel.vue` does NOT have a `data-key` attribute (it was removed in Story 8.3 R2 because `vtszCode` alone is not unique when the same VTSZ has different units -- see D4). For row expansion, PrimeVue requires `data-key` to track expanded state. Use a composite key approach:

Option A: Add a computed `id` field to each line (e.g., `vtszCode + '-' + unitOfMeasure`) and use `data-key="id"`.
Option B: Use array index via PrimeVue's default row identity.

Recommended: Option A -- add a computed property or map the response lines to include an `id` field before passing to DataTable.

### Critical: Explicit Composable Import

Per Story 8.2 R2 learning, `useInvoiceAutoFill` is explicitly imported (not auto-imported). The mock pattern in specs must use `vi.mock('~/composables/api/useInvoiceAutoFill', ...)`, not `vi.stubGlobal`. This is already correct in the existing spec -- just maintain it.

### Frontend: Expand-All Implementation

```ts
const expandedRows = ref<Record<string, boolean>>({})
const allExpanded = ref(false)

function toggleExpandAll() {
  if (allExpanded.value) {
    expandedRows.value = {}
  } else {
    // Expand all rows by setting each row's key to true
    const expanded: Record<string, boolean> = {}
    for (const line of response.value?.lines ?? []) {
      expanded[line.vtszCode + '-' + line.unitOfMeasure] = true
    }
    expandedRows.value = expanded
  }
  allExpanded.value = !allExpanded.value
}
```

### Previous Story Intelligence (8.3)

- `autoFillFromInvoices()` fetches invoice summaries, then for each calls `queryInvoiceDetails(invoiceNumber)` -- the per-invoice data is already available in the loop, just not retained
- Aggregation uses `VtszUnitKey(vtszCode, unit)` as map key
- Demo mode with taxNumber `12345678` and Q1 2026 returns >=3 distinct lines
- Test baseline: 710 frontend + BUILD SUCCESSFUL backend (as of 8.3 completion; may have grown since)
- Commit convention: `feat(8.6): Invoice-to-material traceability in EPR auto-fill`

### Deferred / Out of Scope

- Editing individual invoice contributions (this is read-only drill-down)
- Filtering contributing invoices by date range within the expansion
- PDF export of the expanded view
- Linking to the actual invoice document (NAV does not provide document URLs)
- Sorting or pagination within the expansion nested table (unlikely to have enough rows per line to warrant it)

### References

- Story 8.3 spec: `_bmad-output/implementation-artifacts/8-3-invoice-driven-epr-auto-fill.md`
- `EprService.autoFillFromInvoices()`: `backend/src/main/java/hu/riskguard/epr/domain/EprService.java:488-563`
- `InvoiceAutoFillLineDto`: `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillLineDto.java`
- `InvoiceAutoFillResponse`: `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillResponse.java`
- `InvoiceAutoFillPanel.vue`: `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`
- Row expansion pattern: `frontend/app/pages/audit-history/index.vue:19,194,305`
- Architecture ADR-6 (NAV Online Szamla): `_bmad-output/planning-artifacts/architecture.md`
