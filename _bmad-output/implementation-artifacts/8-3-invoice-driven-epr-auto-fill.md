# Story 8.3: Invoice-Driven EPR Auto-Fill

Status: ready-for-dev

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

- [ ] Task 1: DB migration — Add `vtszMappings` to EPR config (AC: 1)
  - [ ] Create `backend/src/main/resources/db/migration/V20260403_001__add_vtsz_mappings_to_epr_config.sql`
  - [ ] Use `UPDATE epr_configs SET config_data = config_data || '{"vtszMappings":[...]}'::jsonb WHERE version = 1`
  - [ ] Include 20+ entries; map VTSZ prefixes used in `DemoInvoiceFixtures`: 4819→`11010101`, 3923→`11020101`, 7214→`91010101`, 2523→`91010101`, 7604→`11040101`, 3917→`11020101`, 1905→`61010101`, 1101→`61010101`, 8523→`23030101`, 6202→`61010101`
  - [ ] Each entry JSON shape: `{"vtszPrefix":"4819","kfCode":"11010101","materialName_hu":"Karton csomagolás","materialName_en":"Cardboard packaging"}`
  - [ ] Verify `EprConfigValidator` does not reject configs with this new key (add `vtszMappings` to allowed-keys list if validator enforces strict keys)

- [ ] Task 2: `DataSourceService` invoice query methods (AC: 2, 3)
  - [ ] Add `List<InvoiceSummary> queryInvoices(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction)` to `DataSourceService`
  - [ ] Add `InvoiceDetail queryInvoiceDetails(String invoiceNumber)` to `DataSourceService`
  - [ ] Inject `RiskGuardProperties` (already available in the service context) and `NavOnlineSzamlaClient`
  - [ ] Demo branch: call `DemoInvoiceFixtures.getForTaxNumber(taxNumber)` → filter by date and direction → map `InvoiceFixture` → `InvoiceSummary` and `InvoiceFixture` → `InvoiceDetail` (including line items)
  - [ ] Non-demo branch: delegate to `navClient.queryInvoiceDigest()` / `navClient.queryInvoiceData()`
  - [ ] Wrap both branches in try/catch — on exception log warning and return empty list / empty `InvoiceDetail`
  - [ ] Add `DemoInvoiceFixtures.getForTaxNumber(taxNumber)` static method if it doesn't exist; filter by invoiceNumber for `queryInvoiceDetails`
  - [ ] Write `DataSourceServiceInvoiceTest` in `hu.riskguard.datasource`: 4 tests — demo returns fixtures, demo date filter, demo direction filter, non-demo delegates to client mock

- [ ] Task 3: New EPR DTOs (AC: 5)
  - [ ] Create `InvoiceAutoFillRequest` record in `epr/api/dto/`: `@NotBlank String taxNumber`, `@NotNull LocalDate from`, `@NotNull LocalDate to`
  - [ ] Create `InvoiceAutoFillLineDto` record in `epr/api/dto/`: `String vtszCode`, `String description`, `String suggestedKfCode`, `BigDecimal aggregatedQuantity`, `String unitOfMeasure`, `boolean hasExistingTemplate`, `UUID existingTemplateId`
  - [ ] Create `InvoiceAutoFillResponse` record in `epr/api/dto/`: `List<InvoiceAutoFillLineDto> lines`, `boolean navAvailable`, `String dataSourceMode`
  - [ ] All records follow `static from(...)` factory pattern only if converting from a domain type; otherwise plain record constructors are fine

- [ ] Task 4: `EprService.autoFillFromInvoices()` (AC: 4, 8)
  - [ ] Inject `DataSourceService` into `EprService` via constructor (`@RequiredArgsConstructor`) — cross-module facade call is permitted per architecture
  - [ ] Implement `autoFillFromInvoices(String taxNumber, LocalDate from, LocalDate to, UUID tenantId)` per AC-4 algorithm
  - [ ] Load active config: `eprRepository.findActiveConfig()` → extract `vtszMappings` from JSONB via `ObjectMapper` (reuse `OBJECT_MAPPER` static field already in `EprService`)
  - [ ] VTSZ prefix matching: for each invoice line's `vtszCode`, find the mapping entry where `vtszCode.startsWith(entry.vtszPrefix)` — longest prefix wins; if no match → `suggestedKfCode=null`
  - [ ] Quantity unit normalisation: if `unitOfMeasure` is "KG" convert to pieces using estimate if base weight known, otherwise pass raw quantity with original unit
  - [ ] Template matching: compare `materialName_hu` case-insensitively against `template.getName()` → set `hasExistingTemplate=true` and `existingTemplateId`
  - [ ] `dataSourceMode`: read from `RiskGuardProperties.dataSource.mode` (inject via new `DataSourceService.getMode()` accessor or read property directly)
  - [ ] Write `EprServiceAutoFillTest` unit test: mock `DataSourceService`, verify VTSZ grouping, longest-prefix match, hasExistingTemplate detection

- [ ] Task 5: `EprController` endpoint (AC: 5)
  - [ ] Add `@PostMapping("/filing/invoice-autofill")` method to `EprController`
  - [ ] Extract `active_tenant_id` via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")`
  - [ ] Validate `request.from()` is before `request.to()` — throw `ResponseStatusException(BAD_REQUEST)` if not
  - [ ] Call `eprService.autoFillFromInvoices(request.taxNumber(), request.from(), request.to(), tenantId)`
  - [ ] Return `ResponseEntity.ok(response)`
  - [ ] Add 3 tests to `EprControllerTest`: success 200, navAvailable=false path (DataSourceService returns empty), 400 on missing `from`

- [ ] Task 6: `InvoiceAutoFillPanel.vue` + composable (AC: 6)
  - [ ] Create `frontend/app/composables/api/useInvoiceAutoFill.ts`
    - `fetchAutoFill(taxNumber: string, from: Date, to: Date): Promise<InvoiceAutoFillResponse>`
    - Uses `useApi()` (same pattern as `useClientPartners.ts`)
    - POST to `/api/v1/epr/filing/invoice-autofill` with JSON body
  - [ ] Create `frontend/app/components/Epr/InvoiceAutoFillPanel.vue`
    - Script setup with `<script setup lang="ts">`
    - Props: none (self-contained); Emits: `apply(lines: InvoiceAutoFillLineDto[])`
    - DatePicker `from`/`to` defaulting to `startOfCurrentQuarter()` / `endOfCurrentQuarter()` (implement helper in composable)
    - `InputText` for taxNumber (user-editable, initially empty)
    - Skeleton loader while `pending` is true (per project-context.md Skeleton UX rule)
    - `Message` severity="warn" when `!response.navAvailable`
    - `DataTable` with selection enabled; "Apply to Filing" button calls `emit('apply', selectedLines)`
  - [ ] i18n: add `autofill.*` keys to `en/epr.json` and `hu/epr.json` in alphabetical order
  - [ ] Create `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts` — 10+ tests

- [ ] Task 7: Filing page integration (AC: 7)
  - [ ] Edit `pages/epr/filing.vue`: add `<Panel :toggleable="true" :collapsed="true">` wrapping `<InvoiceAutoFillPanel>`
  - [ ] Handle `@apply="onAutoFillApply"` — for each line with `existingTemplateId`: call `eprFilingStore.updateQuantity(existingTemplateId, Math.ceil(line.aggregatedQuantity))`
  - [ ] Lines without `existingTemplateId`: display as PrimeVue `Tag` with severity="warning" in a summary section below the panel
  - [ ] Add 3 new tests to `filing.spec.ts`: panel renders collapsed, apply updates store quantities, unmatched lines show warning tags

- [ ] Task 8: Verify and update sprint status (AC: 9, 10)
  - [ ] Run `./gradlew test --tests "hu.riskguard.datasource.*" --tests "hu.riskguard.epr.*"` — must pass
  - [ ] Run `cd frontend && npm run test` — must pass
  - [ ] `NamingConventionTest` passes with all new classes
  - [ ] Update `sprint-status.yaml`: `8-3-invoice-driven-epr-auto-fill: review`

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

### File List
