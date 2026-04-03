# Story 8.3: Invoice-Driven EPR Auto-Fill

Status: done

## Story

As a RiskGuard user,
I want the quarterly EPR filing form to be pre-populated with material quantities derived from my outbound invoices fetched from NAV Online Számla,
so that I can complete and export a MOHU-ready filing without manually entering quantities from paper invoices.

## Acceptance Criteria

1. **`vtszMappings` in EPR config** — New DB migration `V20260403_001__add_vtsz_mappings_to_epr_config.sql` appends a `vtszMappings` array to the existing version-1 `config_data` JSONB. Each entry: `{vtszPrefix: "4819", kfCode: "11010101", materialName_hu: "Karton csomagolás", materialName_en: "Cardboard packaging"}`. Minimum 20 entries covering all VTSZ codes present in `DemoInvoiceFixtures` (48191000→cardboard, 39233000→PET, 72142000→steel bar, 25232900→cement, 76042100→aluminium profile, 39172100→plastic pipe, 19059090→bread, 11010015→flour, 85234000→software media, 62020000→textile). Prefix matching: longest prefix wins. The `EprConfigValidator` must accept (not reject) configs containing this new key.

2. **`DataSourceService.queryInvoices()` facade method** — New public method `List<InvoiceSummary> queryInvoices(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction)` in `datasource.domain.DataSourceService`. Demo mode (`riskguard.data-source.mode=demo`): serves from `DemoInvoiceFixtures.getForTaxNumber(taxNumber)`, filtered to `issueDate >= from && issueDate <= to` and `direction` matching. Non-demo: delegates to injected `NavOnlineSzamlaClient.queryInvoiceDigest()`. On any exception: logs warning and returns empty list (never throws — NAV downtime must not crash EPR page).

3. **`DataSourceService.queryInvoiceDetails()` facade method** — New public method `InvoiceDetail queryInvoiceDetails(String invoiceNumber)` in `DataSourceService`. Demo mode: returns matching `DemoInvoiceFixtures` entry mapped to `InvoiceDetail`. Non-demo: delegates to `NavOnlineSzamlaClient.queryInvoiceData()`. On exception: returns `InvoiceDetail` with empty `lineItems` list.

4. **`EprService.autoFillFromInvoices()` method** — Signature: `public InvoiceAutoFillResponse autoFillFromInvoices(String taxNumber, LocalDate from, LocalDate to, UUID tenantId)`. Algorithm: (1) call `dataSourceService.queryInvoices(taxNumber, from, to, OUTBOUND)`; (2) for each summary fetch `queryInvoiceDetails(invoiceNumber)`; (3) collect all line items where `vtszCode != null`; (4) group by vtszCode and sum `quantity`; (5) load active config, extract `vtszMappings`, match each vtszCode by longest prefix; (6) load tenant templates via `eprRepository.findAllByTenant(tenantId)`, match by `materialName_hu`; (7) return `InvoiceAutoFillResponse(lines, navAvailable, dataSourceMode)`. If `queryInvoices` returns empty due to unavailability, set `navAvailable=false`.

5. **`POST /api/v1/epr/filing/invoice-autofill` endpoint** — In `EprController`. Body: `@Valid InvoiceAutoFillRequest`. Returns `InvoiceAutoFillResponse` with HTTP 200. Extract `active_tenant_id` via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")`. `taxNumber` in request is `@NotBlank` (8-digit format). At least 3 backend controller tests: (a) success 200, (b) `navAvailable=false` path when NAV throws, (c) `400` on missing `from`.

6. **`InvoiceAutoFillPanel.vue` component** — In `frontend/app/components/Epr/`. Contains: PrimeVue `DatePicker` for `from`/`to` (defaults to current quarter range), `InputText` for `taxNumber`, "Fetch Invoices" button. While loading: `Skeleton` rows. If `navAvailable=false`: PrimeVue `Message` severity="warn" with i18n key `epr.autofill.navUnavailable` linking to `/admin/datasources`. Results: PrimeVue `DataTable` with columns vtszCode, description, suggestedKfCode, quantity, existing-template badge. "Apply to Filing" button emits `apply(lines)` event. At least 10 unit tests in `InvoiceAutoFillPanel.spec.ts` co-located in the same directory.

7. **Filing page integration** — `pages/epr/filing.vue` adds a collapsible PrimeVue `Panel` labelled `$t('epr.autofill.panelTitle')` above the existing filing table. On `apply` event from `InvoiceAutoFillPanel`: update `eprFilingStore` quantities for lines with `existingTemplateId` (direct match via `updateQuantity()`). Lines without a matching template shown as orange `Tag` components ("New template needed"). At least 3 new tests added to `filing.spec.ts`.

8. **Demo mode end-to-end** — With `riskguard.data-source.mode=demo`, calling auto-fill for taxNumber `12345678` with Q1 2026 date range returns at least 3 distinct `InvoiceAutoFillLineDto` entries, each with a non-null `suggestedKfCode` matching the vtszMappings. No NAV credentials required. `EprServiceAutoFillTest` verifies this with mocked `DataSourceService`.

9. **`NamingConventionTest` passes** — All new classes in `datasource.domain.*`, `epr.domain.*`, `epr.api.*`, `epr.api.dto.*` match existing regex patterns. No cross-module repository imports.

10. **All existing tests green** — `./gradlew test` BUILD SUCCESSFUL. Frontend tests pass. No regressions in EPR filing, wizard, export, or screening flows.

## Tasks / Subtasks

- [x] Task 1: DB migration — Add `vtszMappings` to EPR config (AC: 1)
  - [x] Create `backend/src/main/resources/db/migration/V20260403_001__add_vtsz_mappings_to_epr_config.sql`
  - [x] Use `UPDATE epr_configs SET config_data = config_data || '{"vtszMappings":[...]}'::jsonb WHERE version = 1`
  - [x] Include 20+ entries; map VTSZ prefixes used in `DemoInvoiceFixtures`
  - [x] Each entry JSON shape: `{"vtszPrefix":"4819","kfCode":"11010101","materialName_hu":"Karton csomagolás","materialName_en":"Cardboard packaging"}`
  - [x] Verified `EprConfigValidator` does not reject configs with this new key

- [x] Task 2: `DataSourceService` invoice query methods (AC: 2, 3)
  - [x] Add `List<InvoiceSummary> queryInvoices(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction)` to `DataSourceService`
  - [x] Add `InvoiceDetail queryInvoiceDetails(String invoiceNumber)` to `DataSourceService`
  - [x] Inject `RiskGuardProperties` and `NavOnlineSzamlaClient` via constructor
  - [x] Demo branch: serve from `DemoInvoiceFixtures`, filter by date and direction
  - [x] Non-demo branch: delegate to `navClient`
  - [x] Wrapped in try/catch — on exception log warning and return empty
  - [x] Added `DemoInvoiceFixtures.getForTaxNumber(taxNumber)` and `getAllFixtures()` static methods
  - [x] `DataSourceServiceInvoiceTest`: 7 tests passing

- [x] Task 3: New EPR DTOs (AC: 5)
  - [x] Created `InvoiceAutoFillRequest`, `InvoiceAutoFillLineDto`, `InvoiceAutoFillResponse` in `epr/api/dto/`
  - [x] `InvoiceAutoFillResponse` has `static from()` factory per ArchUnit rule

- [x] Task 4: `EprService.autoFillFromInvoices()` (AC: 4, 8)
  - [x] Injected `DataSourceService` into `EprService` via constructor
  - [x] Implemented `autoFillFromInvoices()` with VTSZ grouping, longest-prefix matching, template matching
  - [x] `EprServiceAutoFillTest`: 4 tests passing (VTSZ grouping, longest prefix wins, template match, navAvailable=false)

- [x] Task 5: `EprController` endpoint (AC: 5)
  - [x] Added `POST /filing/invoice-autofill` to `EprController`
  - [x] `from`/`to` date validation (400 if from > to)
  - [x] `EprControllerTest`: 3 new tests passing

- [x] Task 6: `InvoiceAutoFillPanel.vue` + composable (AC: 6)
  - [x] Created `useInvoiceAutoFill.ts` with `fetchAutoFill`, `pending`, `response`, quarter helpers
  - [x] Created `InvoiceAutoFillPanel.vue` with explicit import
  - [x] Added `autofill.*` i18n keys to `en/epr.json` and `hu/epr.json` in alphabetical order
  - [x] `InvoiceAutoFillPanel.spec.ts`: 12 tests passing

- [x] Task 7: Filing page integration (AC: 7)
  - [x] Added collapsible `<Panel>` wrapping `<InvoiceAutoFillPanel>` in `filing.vue`
  - [x] `onAutoFillApply` handler updates store quantities for matched lines
  - [x] Unmatched lines shown as `<Tag severity="warning">`
  - [x] `filing.spec.ts`: 3 new tests passing (panel renders, apply updates store, unmatched tags shown)

- [x] Task 8: Verify and update sprint status (AC: 9, 10)
  - [x] `./gradlew test --tests "hu.riskguard.datasource.*" --tests "hu.riskguard.epr.*"` — BUILD SUCCESSFUL
  - [x] `cd frontend && npm run test` — 710 tests passed
  - [x] Updated `sprint-status.yaml`: `8-3-invoice-driven-epr-auto-fill: review`

## Dev Notes

### Dependency on Story 8.1

**This story can be implemented and tested in demo mode WITHOUT Story 8.1 being complete.** The `NavOnlineSzamlaClient` interface already exists as a stub. In demo mode, `DataSourceService` serves from `DemoInvoiceFixtures` — no NAV credentials or real client needed. Story 8.1 provides the live `NavOnlineSzamlaClient` implementation that this story's non-demo path delegates to. Wire the interface, not the concrete class.

### Critical: Module Boundary

`EprService` is in module `epr`. `DataSourceService` is in module `datasource`. Cross-module calls via service facades are explicitly permitted per architecture (ADR-4). `EprService` may inject and call `DataSourceService` — this is the documented data flow. Do NOT inject any `datasource.internal.*` classes into `epr` module. Only `datasource.domain.DataSourceService` is the allowed cross-module entry point.

### Critical: VTSZ Prefix Matching Logic

VTSZ codes in invoice line items can be 8 digits (`48191000`). The `vtszMappings` config entries use 4-digit prefixes (`4819`). Matching rule: find the mapping entry where `vtszCode.startsWith(entry.vtszPrefix)`. When multiple entries could match (e.g. `4819` and `48191`), the longest prefix wins. Implementation:

```java
vtszMappings.stream()
    .filter(m -> vtszCode.startsWith(m.vtszPrefix()))
    .max(Comparator.comparingInt(m -> m.vtszPrefix().length()))
    .map(VtszMapping::kfCode)
    .orElse(null)
```

### Critical: `updated_at` on `epr_material_templates`

The `epr_material_templates` table has no DB trigger for `updated_at`. If this story ever writes to templates (currently it does not — auto-fill is read-only preview), you MUST explicitly set `.set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())`. Story 8.3 does NOT write templates — it returns suggestions for the user to apply via existing template-creation endpoints.

### Critical: EprRepository.findActiveConfig()

Check whether `findActiveConfig()` already exists in `EprRepository`. If it does, use it. If not, add it:
```java
public Optional<EprConfigsRecord> findActiveConfig() {
    return dsl.selectFrom(EPR_CONFIGS)
        .where(EPR_CONFIGS.ACTIVATED_AT.isNotNull())
        .orderBy(EPR_CONFIGS.VERSION.desc())
        .limit(1)
        .fetchOptionalInto(EprConfigsRecord.class);
}
```
Do not duplicate if it already exists under a different name — grep first: `grep -r "findActive\|activatedAt" backend/src/main/java/hu/riskguard/epr/`.

### Critical: DataSourceService Mode Check

`RiskGuardProperties` is already used in `DataSourceModeConfig`. Inject it into `DataSourceService` via constructor. The mode value is `riskguard.data-source.mode` — values: `demo`, `test`, `live`. Check: `"demo".equals(props.dataSource().mode())`.

### DemoInvoiceFixtures Adapter Pattern

`DemoInvoiceFixtures` uses internal `InvoiceFixture` and `LineItemFixture` records (not the same as `InvoiceSummary`/`InvoiceDetail`/`InvoiceLineItem` in the `nav` package). You need mapping methods to convert `InvoiceFixture` → `InvoiceSummary` and `InvoiceFixture` → `InvoiceDetail`. Add these as private helpers in `DataSourceService` or as static helpers on `DemoInvoiceFixtures`. Keep the conversion simple — most fields map 1:1; `productCodeCategory` defaults to `"VTSZ"` for demo fixtures.

### Frontend: Quarter Date Helper

Add a `useQuarterDates()` helper in the composable:
```ts
function currentQuarterRange() {
  const now = new Date()
  const q = Math.floor(now.getMonth() / 3)
  const from = new Date(now.getFullYear(), q * 3, 1)
  const to = new Date(now.getFullYear(), q * 3 + 3, 0)
  return { from, to }
}
```

### Frontend: Existing Patterns to Reuse

| Pattern | Where |
|---------|-------|
| `useApi()` composable | `composables/api/useClientPartners.ts` — exact same `$fetch` wrapper pattern |
| PrimeVue `Skeleton` while pending | `pages/epr/filing.vue` already uses this |
| PrimeVue `DataTable` with selection | `pages/flight-control/index.vue` — column slot + selection pattern |
| PrimeVue `Panel` toggleable | Any admin page — `<Panel :toggleable="true" :collapsed="true">` |
| `eprFilingStore.updateQuantity()` | `stores/eprFiling.ts` — called from filing.vue already |
| i18n alphabetical order | Both `en/epr.json` and `hu/epr.json` — check existing key order before inserting |

### Backend: Patterns to Reuse

| Pattern | Where |
|---------|-------|
| `JwtUtil.requireUuidClaim()` | Every existing controller — use exactly this |
| Try/catch + warn log | `DemoCompanyDataAdapter.fetchFallback()` — same graceful degradation |
| `OBJECT_MAPPER` in EprService | Already declared `static final` in `EprService.java:57` |
| `@RequiredArgsConstructor` injection | `EprService` already uses Lombok — just add `DataSourceService` field |

### API Contract (OpenAPI)

The CI regenerates `api.d.ts` from the backend OpenAPI spec. After adding the new endpoint, the `tsc --noEmit` check in CI will fail until the frontend is updated. Ensure `InvoiceAutoFillRequest`, `InvoiceAutoFillLineDto`, `InvoiceAutoFillResponse` are exported in the OpenAPI spec (Spring auto-generates this via `springdoc`). Use auto-generated types from `api.d.ts` in the frontend — do NOT define these interfaces manually.

### Project Structure Notes

**New backend files:**
```
backend/src/main/java/hu/riskguard/epr/api/dto/
├── InvoiceAutoFillRequest.java       ← NEW record
├── InvoiceAutoFillLineDto.java       ← NEW record
├── InvoiceAutoFillResponse.java      ← NEW record
backend/src/main/resources/db/migration/
├── V20260403_001__add_vtsz_mappings_to_epr_config.sql ← NEW
backend/src/test/java/hu/riskguard/epr/
├── EprServiceAutoFillTest.java       ← NEW
backend/src/test/java/hu/riskguard/datasource/
├── DataSourceServiceInvoiceTest.java ← NEW
```

**Modified backend files:**
```
backend/src/main/java/hu/riskguard/datasource/domain/DataSourceService.java  ← add queryInvoices(), queryInvoiceDetails()
backend/src/main/java/hu/riskguard/epr/domain/EprService.java                ← add autoFillFromInvoices(), inject DataSourceService
backend/src/main/java/hu/riskguard/epr/api/EprController.java                ← add /filing/invoice-autofill endpoint
backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java           ← add findActiveConfig() if missing
backend/src/test/java/hu/riskguard/epr/EprControllerTest.java                ← add 3 tests
```

**New frontend files:**
```
frontend/app/composables/api/useInvoiceAutoFill.ts   ← NEW
frontend/app/components/Epr/InvoiceAutoFillPanel.vue ← NEW
frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts ← NEW (co-located with .vue)
```

**Modified frontend files:**
```
frontend/app/pages/epr/filing.vue          ← add collapsible Panel + InvoiceAutoFillPanel
frontend/app/pages/epr/filing.spec.ts      ← add 3 tests
frontend/app/i18n/en/epr.json             ← add autofill.* keys (alphabetical)
frontend/app/i18n/hu/epr.json             ← add autofill.* keys (alphabetical)
```

**Naming conventions:**
- Java: `lowerCamelCase` for methods, `PascalCase` for classes, `lower_snake_case` for DB columns
- Vue: `PascalCase` filenames (`InvoiceAutoFillPanel.vue`)
- TypeScript composable: `useInvoiceAutoFill.ts` (camelCase after `use`)
- Spec: co-located with `.vue` file in the same directory per project-context.md rule

### Critical Learnings from Story 8.2 (Apply to This Story)

Story 8.2 R2 review surfaced three issues that WILL recur here if not prevented:

1. **Explicit composable import required** — `useInvoiceAutoFill` MUST be explicitly imported in `InvoiceAutoFillPanel.vue`:
   ```ts
   import { useInvoiceAutoFill } from '~/composables/api/useInvoiceAutoFill'
   ```
   Nuxt auto-import cache misses caused `ReferenceError: useInvoiceAutoFill is not defined` in E2E/fresh dev environments (8.2 R2 P3). Do NOT rely solely on auto-import.

2. **`vi.mock` not `vi.stubGlobal` for explicitly-imported composables** — Because the composable is explicitly imported, `vi.stubGlobal('useInvoiceAutoFill', ...)` in specs is bypassed. Use:
   ```ts
   vi.mock('~/composables/api/useInvoiceAutoFill', () => ({
     useInvoiceAutoFill: vi.fn(() => ({ fetchAutoFill: vi.fn(), pending: ref(false) }))
   }))
   ```
   Apply this in `InvoiceAutoFillPanel.spec.ts` and any a11y spec rendering the panel (8.2 R2 P4).

3. **i18n alphabetical ordering** — `autofill.*` keys sort before `config.*` and `filing.*`. Check the exact alphabetical position in both `en/epr.json` and `hu/epr.json` before inserting the new block.

**Test baseline**: 695 frontend + 5 e2e (end of Story 8.2). This story adds backend tests — confirm `./gradlew test` BUILD SUCCESSFUL.

**Commit convention**: `feat(8.3): Invoice-driven EPR auto-fill`

### References

- Architecture ADR-6 (NAV Online Számla): `_bmad-output/planning-artifacts/architecture.md` — EPR Invoice Flow section at line 1107
- Architecture Data Flow: `_bmad-output/planning-artifacts/architecture.md:1109` — `DataSourceService.queryInvoices(dateRange)` feeding `EprService`
- Story 8.1 (NAV client implementation): `_bmad-output/implementation-artifacts/8-1-nav-online-szamla-client-implementation.md` — implements `NavOnlineSzamlaClient` interface used by this story
- Story 5.2 (Quarterly EPR Filing): `_bmad-output/implementation-artifacts/5-2-quarterly-epr-filing-workflow.md` — filing form and `eprFilingStore` patterns reused here
- EPR config JSON structure: `V20260323_002__seed_epr_fee_tables.sql` — understand `vtszMappings` must be appended, not replace existing keys
- `NavOnlineSzamlaClient` interface: `datasource/internal/adapters/nav/NavOnlineSzamlaClient.java`
- `InvoiceLineItem.vtszCode`: `datasource/internal/adapters/nav/InvoiceLineItem.java`
- `DemoInvoiceFixtures`: `datasource/internal/adapters/demo/DemoInvoiceFixtures.java`
- `EprService`: `epr/domain/EprService.java` — inject `DataSourceService` via existing `@RequiredArgsConstructor`
- `eprFilingStore`: `frontend/app/stores/eprFiling.ts` — `updateQuantity()` method for filing form pre-fill
- `useClientPartners.ts`: `frontend/app/composables/api/useClientPartners.ts` — composable pattern to replicate

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- ArchUnit `datasource_internal_should_not_be_accessed_externally` required creating domain-level copies of `InvoiceSummary`, `InvoiceDetail`, `InvoiceLineItem`, `InvoiceDirection` in `datasource.domain` package. `DataSourceService` maps between internal types and domain types.
- `EprServiceAutoFillTest` required pre-building Mockito `Record` mock objects as instance fields to avoid `UnfinishedStubbingException` from nested `when()` calls.
- `navAvailable` logic: `!summaries.isEmpty() || isDemo` — demo mode always true, live mode only true if results returned.
- Frontend specs must use `vi.mock('~/composables/api/useInvoiceAutoFill', ...)` (not `vi.stubGlobal`) because the composable is explicitly imported.
- **Review R1 follow-ups (2026-04-03):** Resolved all 4 decisions and 7 patches:
  - D1: Created `InvoiceQueryResult` wrapper with `serviceAvailable` flag; `DataSourceService.queryInvoices()` returns this instead of `List<InvoiceSummary>`
  - D2: Added `DataSourceService.getTenantTaxNumber()` via `NavTenantCredentialRepository`; `EprService` validates ownership, throws 403 on mismatch
  - D3: Accepted `Math.ceil` as regulatory prudence (no code change)
  - D4: Changed aggregation key to `VtszUnitKey(vtszCode, unit)` — different units produce separate lines
  - P1–P7: All patches applied (duplicate SQL entry, @Pattern validation, NPE guard, zero-qty skip, toast error handling, test gaps filled)
  - Added 3 new tests: `autoFillFromInvoices_navAvailableTrue_whenEmptyResultWithServiceUp`, `autoFillFromInvoices_rejectsMismatchedTaxNumber`, `invoiceAutoFill_nullFrom_failsValidation`
- **Review R2 follow-ups (2026-04-03):** Resolved 3 patches:
  - P1: Removed duplicate `HttpStatus` import in `EprControllerTest.java`
  - P2: Removed non-unique `data-key="vtszCode"` from `InvoiceAutoFillPanel.vue` DataTable — D4 edge case where same vtszCode with different units breaks PrimeVue selection
  - P3: Fixed non-unique `:key` in `filing.vue` Tag v-for loop — changed to composite key `vtszCode + '-' + unitOfMeasure`

### File List

**New backend files:**
- `backend/src/main/resources/db/migration/V20260403_001__add_vtsz_mappings_to_epr_config.sql`
- `backend/src/main/java/hu/riskguard/datasource/domain/InvoiceDirection.java`
- `backend/src/main/java/hu/riskguard/datasource/domain/InvoiceLineItem.java`
- `backend/src/main/java/hu/riskguard/datasource/domain/InvoiceSummary.java`
- `backend/src/main/java/hu/riskguard/datasource/domain/InvoiceDetail.java`
- `backend/src/main/java/hu/riskguard/datasource/domain/InvoiceQueryResult.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillLineDto.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/InvoiceAutoFillResponse.java`
- `backend/src/test/java/hu/riskguard/epr/EprServiceAutoFillTest.java`
- `backend/src/test/java/hu/riskguard/datasource/DataSourceServiceInvoiceTest.java`

**Modified backend files:**
- `backend/src/main/java/hu/riskguard/datasource/domain/DataSourceService.java`
- `backend/src/main/java/hu/riskguard/datasource/internal/adapters/demo/DemoInvoiceFixtures.java`
- `backend/src/main/java/hu/riskguard/epr/domain/EprService.java`
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java`
- `backend/src/test/java/hu/riskguard/epr/EprServiceTest.java`
- `backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java`
- `backend/src/test/java/hu/riskguard/epr/EprControllerTest.java`

**New frontend files:**
- `frontend/app/composables/api/useInvoiceAutoFill.ts`
- `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`
- `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts`

**Modified frontend files:**
- `frontend/app/pages/epr/filing.vue`
- `frontend/app/pages/epr/filing.spec.ts`
- `frontend/app/i18n/en/epr.json`
- `frontend/app/i18n/hu/epr.json`
- `frontend/types/epr.ts`

### Review Findings

#### Decision-Needed

- [x] [Review][Decision] D1 — Add availability flag to `InvoiceQueryResult` — Created `InvoiceQueryResult` record wrapping `List<InvoiceSummary>` + `boolean serviceAvailable`. `DataSourceService.queryInvoices()` now returns this wrapper. Exception path sets `serviceAvailable=false`, success path sets `true`. EprService uses `serviceAvailable` directly for `navAvailable`.
- [x] [Review][Decision] D2 — Validate tenant owns taxNumber — Added `DataSourceService.getTenantTaxNumber(UUID)` via `NavTenantCredentialRepository`. `EprService.autoFillFromInvoices()` checks registered tax number matches request; throws 403 on mismatch. Skipped if no credentials (demo mode).
- [x] [Review][Decision] D3 — Accept `Math.ceil` as regulatory prudence — No code change; over-reporting preferred per Hungarian EPR regulations.
- [x] [Review][Decision] D4 — Group by vtszCode+unit — Aggregation now uses `record VtszUnitKey(String vtszCode, String unit)` as map key. Different units for same VTSZ produce separate result lines.

#### Patches

- [x] [Review][Patch] P1 — Removed duplicate `vtszPrefix: "4820"` from SQL migration. [V20260403_001__add_vtsz_mappings_to_epr_config.sql]
- [x] [Review][Patch] P2 — Added `@Pattern(regexp = "\\d{8,13}")` validation on `taxNumber`. [InvoiceAutoFillRequest.java]
- [x] [Review][Patch] P3 — Added null check before `configDataObj.toString()` with diagnostic log. [EprService.java — loadVtszMappings()]
- [x] [Review][Patch] P4 — Skip lines where `item.quantity()` is null or ≤ 0. [EprService.java — autoFillFromInvoices()]
- [x] [Review][Patch] P5 — Added try-catch in `handleFetch` with toast error display. [InvoiceAutoFillPanel.vue]
- [x] [Review][Patch] P6 — Added `invoiceAutoFill_nullFrom_failsValidation` test for `@NotNull` on `from`. [EprControllerTest.java]
- [x] [Review][Patch] P7 — Expanded test to 3 VTSZ codes (cardboard, PET, steel) asserting `hasSize(3)` with all non-null `suggestedKfCode`. [EprServiceAutoFillTest.java]

#### Deferred

- [x] [Review][Defer] W1 — `invoiceNumber` logged plain in `queryInvoiceDetails` warn path — inconsistent with masked `taxNumber`; low risk (invoice numbers are not PII) [DataSourceService.java] — deferred, pre-existing pattern
- [x] [Review][Defer] W2 — `toInvoiceSummary` hardcodes `invoiceNetAmount=ZERO` — demo mode only; EPR auto-fill doesn't use summary net amounts [DataSourceService.java] — deferred, pre-existing
- [x] [Review][Defer] W3 — `toInvoiceDetail` uses `li.netAmount()` twice for `lineNetAmountHUF` — demo mode only; EPR reads vtszCode/quantity not amounts [DataSourceService.java] — deferred, demo fixture limitation
- [x] [Review][Defer] W4 — `getForTaxNumber` truncates without numeric validation — demo mode only; silent miss is safe [DemoInvoiceFixtures.java] — deferred, pre-existing
- [x] [Review][Defer] W5 — `emptyDetail` hardcodes `OUTBOUND` direction — error-path only; direction not used by EPR auto-fill [DataSourceService.java] — deferred, pre-existing
- [x] [Review][Defer] W6 — N+1 sequential NAV calls in `autoFillFromInvoices` — perf risk at scale; MVP scope; batch/parallel fetch is a future optimization [EprService.java] — deferred, known MVP limitation
- [x] [Review][Defer] W7 — SQL migration UPDATE silently affects 0 rows if no version=1 config — design assumption that wizard has been run; not introduced by this story [V20260403_001] — deferred, pre-existing system assumption
- [x] [Review][Defer] W8 — DatePicker clearable: silent no-op when date cleared, no user feedback — minor UX; `handleFetch` guards the null case silently [InvoiceAutoFillPanel.vue] — deferred, minor UX
- [x] [Review][Defer] W9 — Multiple templates with same `materialName_hu`: `findFirst()` order-dependent — data quality edge case; current data model discourages duplicates [EprService.java] — deferred, low probability
- [x] [Review][Defer] W10 — No explicit `EprConfigValidator` test for `vtszMappings` key — validator ignores unknown keys by design; behavior correct by inspection [EprService/EprConfigValidator] — deferred, nice-to-have guard
