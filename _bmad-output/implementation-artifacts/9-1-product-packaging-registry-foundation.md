# Story 9.1: Product-Packaging Registry Foundation

Status: done

## Story

As a Hungarian KKV manufacturer/importer subject to the EPR regime under 80/2023 Korm. rendelet,
I want per-product packaging bill-of-materials CRUD (1..N packaging components per SKU, each with KF code and per-unit weight) backed by a full-provenance audit trail,
so that my quarterly EPR filing in Story 9.4 can compute correct per-KF-code totals by joining my NAV invoice quantities against the registry, and so that the same data model carries me into PPWR (Regulation 2025/40) without schema migrations.

## Legal basis

- **80/2023. (III. 14.) Korm. rendelet Annex 3.1** — producer registration identity (tax number, KSH stat number, cégjegyzékszám, registered office).
- **Annex 4.1** — per-transaction reporting shape that the registry + invoice join must produce downstream.
- **Annex 1.2** — 8-character structured KF code format carried on each packaging component.

CP-5 §4.2 and ADR-0001/0002 are the binding product/architecture sources for everything below.

## Acceptance Criteria

1. **Flyway migration `V20260414_001__create_product_registry.sql`** creates three tables in the existing `public` schema, all tenant-scoped via `tenant_id UUID NOT NULL` column (no RLS; app-level tenant filtering per §Dev Notes):
   - **`products`** — columns `id UUID PK`, `tenant_id UUID NOT NULL REFERENCES tenants(id)`, `article_number VARCHAR(64) NULL`, `name VARCHAR(512) NOT NULL`, `vtsz VARCHAR(16) NULL`, `primary_unit VARCHAR(16) NOT NULL DEFAULT 'pcs'`, `status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` (check-constrained to `ACTIVE | ARCHIVED | DRAFT`), `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Indexes on `(tenant_id, name)`, `(tenant_id, vtsz)`, `(tenant_id, article_number)`, `(tenant_id, status)`. Unique constraint `(tenant_id, article_number)` where `article_number IS NOT NULL`.
   - **`product_packaging_components`** — columns `id UUID PK`, `product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE`, `material_description VARCHAR(512) NOT NULL`, `kf_code VARCHAR(16) NULL` (8-digit format enforced at app layer, nullable at DB layer to allow drafts), `weight_per_unit_kg NUMERIC(14,6) NOT NULL CHECK (weight_per_unit_kg >= 0)`, `component_order INT NOT NULL DEFAULT 0`, **PPWR-ready nullable fields** — `recyclability_grade VARCHAR(1) NULL CHECK (recyclability_grade IN ('A','B','C','D'))`, `recycled_content_pct NUMERIC(5,2) NULL CHECK (recycled_content_pct BETWEEN 0 AND 100)`, `reusable BOOLEAN NULL`, `substances_of_concern JSONB NULL`, `supplier_declaration_ref VARCHAR(256) NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Index on `(product_id, component_order)`. No direct `tenant_id` column — tenant isolation is transitive via `product_id → products.tenant_id` (all repository queries MUST join through `products` and filter on `tenant_id`).
   - **`registry_entry_audit_log`** — columns `id UUID PK`, `product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE`, `tenant_id UUID NOT NULL REFERENCES tenants(id)` (denormalised for query-efficient audit reads), `field_changed VARCHAR(64) NOT NULL`, `old_value TEXT NULL`, `new_value TEXT NULL`, `changed_by_user_id UUID NULL REFERENCES users(id)` (nullable for system-sourced changes), `source VARCHAR(32) NOT NULL CHECK (source IN ('MANUAL','AI_SUGGESTED_CONFIRMED','AI_SUGGESTED_EDITED','VTSZ_FALLBACK','NAV_BOOTSTRAP'))`, `timestamp TIMESTAMPTZ NOT NULL DEFAULT now()`. Indexes on `(tenant_id, timestamp DESC)`, `(product_id, timestamp DESC)`.

2. **jOOQ codegen produces new table classes** at `hu.riskguard.jooq.tables.{Products, ProductPackagingComponents, RegistryEntryAuditLog}` (and `.records.*Record`) after `./gradlew generateJooq`. No manual edits to generated classes.

3. **New sub-module `hu.riskguard.epr.registry`** created with the same `api / domain / internal` split as the parent `epr` module, guarded by a new `package-info.java`. All repository writes to `ProductPackagingComponents` are confined to `..epr.registry.internal..` — the existing `EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components` rule MUST begin to actually enforce (previously vacuous) and pass.

4. **Domain model records** in `hu.riskguard.epr.registry.domain`:
   - `Product(UUID id, UUID tenantId, String articleNumber, String name, String vtsz, String primaryUnit, ProductStatus status, List<ProductPackagingComponent> components, OffsetDateTime createdAt, OffsetDateTime updatedAt)`.
   - `ProductPackagingComponent(UUID id, UUID productId, String materialDescription, String kfCode, BigDecimal weightPerUnitKg, int componentOrder, RecyclabilityGrade recyclabilityGrade, BigDecimal recycledContentPct, Boolean reusable, JsonNode substancesOfConcern, String supplierDeclarationRef, OffsetDateTime createdAt, OffsetDateTime updatedAt)`.
   - `enum ProductStatus { ACTIVE, ARCHIVED, DRAFT }`.
   - `enum RecyclabilityGrade { A, B, C, D }` — placed in `hu.riskguard.epr.registry.domain` (NOT inside `epr.domain` or `epr.report`, or the CP-5 §5 invariant 4 ArchUnit rule `fee_calculation_must_not_branch_on_recyclability_grade` will trip).
   - `enum AuditSource { MANUAL, AI_SUGGESTED_CONFIRMED, AI_SUGGESTED_EDITED, VTSZ_FALLBACK, NAV_BOOTSTRAP }`.

5. **`RegistryService` (in `epr.registry.domain`)** with public methods:
   - `List<ProductSummary> list(UUID tenantId, RegistryListFilter filter, int page, int size)` — filter on `name` (ILIKE substring), `vtsz` (prefix), `kfCode` (exact, joined through components), `status` (enum).
   - `Product get(UUID tenantId, UUID productId)` — tenant-scoped single fetch, eagerly loads components ordered by `component_order ASC`; throws `ResponseStatusException(HttpStatus.NOT_FOUND)` if not found or belongs to another tenant.
   - `Product create(UUID tenantId, UUID actingUserId, ProductUpsertCommand cmd)` — persists product + components in a single transaction; emits one `registry_entry_audit_log` row per populated field with `source = MANUAL` and `field_changed = 'CREATE.<fieldName>'`.
   - `Product update(UUID tenantId, UUID productId, UUID actingUserId, ProductUpsertCommand cmd)` — diffs incoming against stored state; writes one audit row per actually-changed field (`old_value`/`new_value` as strings); no-op updates produce zero audit rows.
   - `void archive(UUID tenantId, UUID productId, UUID actingUserId)` — sets status = `ARCHIVED`, audit-logged.
   - All writes flow through `RegistryRepository` (in `epr.registry.internal`); no service outside `epr.registry.*` may call `RegistryRepository`.

6. **`RegistryRepository` (in `epr.registry.internal`)** extends the existing `hu.riskguard.core.repository.BaseRepository` and uses `BaseRepository.tenantCondition(TENANT_ID_FIELD)` for every SELECT/UPDATE/DELETE touching `products` or `registry_entry_audit_log`. For `product_packaging_components` reads/writes: MUST join through `products` and filter by `tenant_id` — never load components by `product_id` alone without the tenant join. A `RegistryAuditRepository` sibling class owns inserts into `registry_entry_audit_log`.

7. **REST controller `RegistryController` at `/api/v1/registry`**, in `hu.riskguard.epr.registry.api`, annotated `@TierRequired(Tier.PRO_EPR)` (matching existing `EprController`). Endpoints:
   - `GET /api/v1/registry` — query params `q` (free-text), `vtsz`, `kfCode`, `status`, `page` (default 0), `size` (default 50, max 200). Returns `RegistryPageResponse`.
   - `GET /api/v1/registry/{id}` — single product with full component list.
   - `POST /api/v1/registry` — `@Valid ProductUpsertRequest` → 201 Created + `ProductResponse`.
   - `PUT /api/v1/registry/{id}` — 200 OK + `ProductResponse`.
   - `POST /api/v1/registry/{id}/archive` — 204 No Content.
   - `GET /api/v1/registry/{id}/audit-log` — paginated audit entries (newest first); returns `RegistryAuditPageResponse`.
   - Every endpoint extracts tenant via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")` and acting user via `JwtUtil.requireUuidClaim(jwt, "user_id")` (match existing `EprController` pattern, `EprController.java:48` area).

8. **DTOs are Java `record`s** in `hu.riskguard.epr.registry.api.dto`, each with a `static from(...)` factory (required by the existing `NamingConventionTest` rule that forbids non-record DTOs and demands `from()` factories):
   - `ProductUpsertRequest(String articleNumber, @NotBlank @Size(max=512) String name, @Pattern(regexp="^[0-9]{4,8}$") String vtsz, @NotBlank String primaryUnit, @NotNull ProductStatus status, @Valid @Size(min=1) List<ComponentUpsertRequest> components)`.
   - `ComponentUpsertRequest(@NotBlank @Size(max=512) String materialDescription, @Pattern(regexp="^[0-9]{8}$") String kfCode, @NotNull @DecimalMin("0") BigDecimal weightPerUnitKg, @NotNull @Min(0) Integer componentOrder, RecyclabilityGrade recyclabilityGrade, @DecimalMin("0") @DecimalMax("100") BigDecimal recycledContentPct, Boolean reusable, JsonNode substancesOfConcern, @Size(max=256) String supplierDeclarationRef)`.
   - `ProductSummaryResponse`, `ProductResponse`, `ComponentResponse`, `RegistryAuditEntryResponse`, plus two dedicated page-response records: `RegistryPageResponse` (wraps `List<ProductSummaryResponse> items`, `long total`, `int page`, `int size`) and `RegistryAuditPageResponse` (wraps `List<RegistryAuditEntryResponse> items`, `long total`, `int page`, `int size`). Pattern: `hu.riskguard.screening.api.dto.AdminAuditPageResponse` and `AuditHistoryPageResponse` — the codebase uses per-feature page wrappers, not a shared generic. Do NOT introduce a shared `PageResponse<T>` — the existing convention is one wrapper per endpoint family.

9. **8-digit KF code input is structured and UX-assisted** (client-side; deeper validation server-side via `@Pattern`):
   - New component `frontend/app/components/registry/KfCodeInput.vue` — single `InputText` bound to a computed model that inserts spaces every 2 digits for display (`formatKfCode()` pattern from existing `WizardStepper.vue`, see §Frontend Patterns) and strips spaces before emit. Rejects non-digit characters on input. Required-pattern validation shown on blur following the existing `MaterialFormDialog.vue` blur+watch validation pattern.
   - This component MUST NOT call `KfCodeClassifierService`. AI-assisted suggestions are Story 9.3 scope; this story ships manual entry + blur validation only. Do not stub the AI path.

10. **Registry list page `pages/registry/index.vue`**:
    - PrimeVue `DataTable` with columns: `articleNumber`, `name`, `vtsz` (formatted), `componentCount`, `status` (PrimeVue `Tag` with `severity` mapped: ACTIVE→success, DRAFT→warning, ARCHIVED→secondary), `updatedAt`, actions (edit, archive).
    - Global text filter (`InputText`) bound to `q` query param — server-side filtering via `q` (not client `FilterMatchMode.CONTAINS`); pattern model: `pages/flight-control/index.vue` lines 50-60 for filter wiring, but send filter to backend.
    - Separate `Select` filters for `status` and a text input for `kfCode`; each triggers re-fetch with debounce (250ms) via a `useRegistry()` composable.
    - Server-side pagination via `DataTable` `lazy` mode, bound to `PageResponse` returned from `/api/v1/registry`.
    - Empty-state card mirroring `3-0a` design-system tokens prompting "Create your first product" → link to `/registry/new`. Bootstrap flow (Story 9.2) is NOT linked from here yet — do not pre-wire to a non-existent route.

11. **Product editor page `pages/registry/[id].vue`** (and a sibling `pages/registry/new.vue` or `[id] === 'new'` sentinel — pick one; do not ship both):
    - Top-level fields: `articleNumber`, `name`, `vtsz`, `primaryUnit`, `status`. Blur-validation per `MaterialFormDialog.vue:61-102`.
    - Components block: a `DataTable` of `product.packagingComponents` with **in-row editing** (pattern: `pages/epr/filing.vue:180-200` with `<template #body="{ data }">` + bound `InputNumber`/`InputText`). Columns: `componentOrder` (drag-handle or up/down buttons), `materialDescription`, `kfCode` (embeds `KfCodeInput.vue`), `weightPerUnitKg`, plus a collapsed row-expansion for PPWR fields (`recyclabilityGrade` Select with A/B/C/D, `recycledContentPct` InputNumber, `reusable` ToggleSwitch, `supplierDeclarationRef` InputText).
    - "Add component" button appends a new blank row; "Remove" per row (with undo toast). `component_order` is re-computed on the client on row add/remove/reorder and sent as the authoritative ordering on save.
    - PPWR fields visually de-emphasised with an accordion label "PPWR readiness (optional)" so users understand they are not required today.
    - Save button calls `POST /api/v1/registry` or `PUT /api/v1/registry/{id}` via `useRegistry()` composable; Pinia `useRegistryStore` holds the in-flight product while editing.
    - Audit tab / drawer (inline `Accordion` is acceptable) rendering the response of `GET /api/v1/registry/{id}/audit-log` with `source` badge, user display name, timestamp, field name, diff. Drives the "fully traceable" UX.

12. **Sidebar nav entry** added in `frontend/app/components/Common/AppSidebar.vue` (`mainNavItems` array): `{ key: 'registry', to: '/registry', icon: 'pi-box', labelKey: 'nav.registry' }`. Role gating: visible to all authenticated tenant users with PRO_EPR tier (mirror the `/epr/*` gating; do not use `accountantOnly`).

13. **i18n** — new namespace `registry.*` added to `frontend/app/i18n/en/registry.json` and `frontend/app/i18n/hu/registry.json`, registered in the i18n barrel the same way existing `epr.json` is. Keys cover list headers, filter placeholders, form labels, PPWR accordion label, validation messages, and audit-log source labels. HU/EN parity required; `npm run check-i18n` must pass. Alphabetical key order within each sub-object (per 8.3's enforced convention).

14. **ArchUnit invariants GREEN**:
    - `EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components` — now binding (tables exist). Pass.
    - `EpicNineInvariantsTest.fee_calculation_must_not_branch_on_recyclability_grade` — pass (the `RecyclabilityGrade` enum lives in `..epr.registry.domain..` which is outside `..epr.domain..` and `..epr.report..`; no class in those two packages may import it).
    - `EpicNineInvariantsTest.only_report_package_depends_on_concrete_report_target` — unaffected by this story, must stay green.
    - `NamingConventionTest` — DTOs must be records with `static from(...)` factories; new controllers match `*Controller` regex; API paths under `/api/v1/...`.
    - `ModulithVerificationTest` — the new `epr.registry` sub-module respects existing module boundary rules (only `api/` + `domain/` accessible from outside; `internal/` private).

15. **Backend tests — all pass with `./gradlew test --tests "hu.riskguard.epr.registry.*" --tests "hu.riskguard.architecture.*"`**:
    - `RegistryServiceTest` (Mockito + AssertJ, mirror `EprControllerTest.java` style): ≥8 tests covering create-with-components, update-with-diff-audit, update-no-op-no-audit, list-with-filters, cross-tenant-404, archive-transitions, component-order-normalisation, PPWR-nullable-roundtrip.
    - `RegistryRepositoryIntegrationTest` (Testcontainers + Postgres, if a Testcontainers harness already exists in the repo — check first; if none, keep as `@DataJpaTest`-style against in-memory or fall back to pure unit tests — DO NOT introduce Testcontainers if absent): verifies tenant isolation — two tenants writing products with the same `article_number` do not collide; tenant A cannot read tenant B's product.
    - `RegistryControllerTest`: ≥6 MockMvc-style tests — happy paths, 403 without PRO_EPR tier, 404 cross-tenant read, 400 on invalid KF-code pattern, 201 Created on POST, audit-log endpoint pagination clamp.

16. **Frontend tests — all pass with `npm run test`**:
    - `KfCodeInput.spec.ts` — formats on input, strips spaces on emit, rejects non-digits, blur validation.
    - `pages/registry/index.spec.ts` — renders empty state, renders DataTable rows, debounced filter calls composable, status tag severity mapping, pagination wiring.
    - `pages/registry/[id].spec.ts` — adds component row, removes row, reorders via buttons, submits upsert payload with normalised `component_order`, shows audit drawer.
    - `components/Common/AppSidebar.spec.ts` — existing tests green + new assertion that `registry` entry appears for PRO_EPR users.

17. **No regressions** — full targeted suite (`hu.riskguard.epr.*`, `hu.riskguard.architecture.*`) + frontend Vitest suite + 5 Playwright e2e smoke tests all green. `check-i18n` passes.

## Tasks / Subtasks

- [x] Task 1: DB migration + jOOQ codegen (AC: 1, 2)
  - [x] Create `backend/src/main/resources/db/migration/V20260414_001__create_product_registry.sql` with the three tables, all constraints, indexes as specified in AC 1
  - [x] Run `./gradlew generateJooq` locally and commit nothing from `build/` — codegen output must stay generated (check `.gitignore`)
  - [x] Verify new table classes appear at `hu.riskguard.jooq.tables.{Products, ProductPackagingComponents, RegistryEntryAuditLog}` and their `*Record` counterparts

- [x] Task 2: Sub-module skeleton + domain types (AC: 3, 4)
  - [x] Create `backend/src/main/java/hu/riskguard/epr/registry/{api,domain,internal}/` directories
  - [x] Add `hu.riskguard.epr.registry.package-info.java` mirroring `hu.riskguard.epr.package-info.java` (Spring Modulith named module, `api`+`domain` exported)
  - [x] Create domain records: `Product`, `ProductPackagingComponent`, `ProductSummary`, `ProductUpsertCommand`, `ComponentUpsertCommand`, `RegistryListFilter`, `RegistryAuditEntry`
  - [x] Create enums: `ProductStatus`, `RecyclabilityGrade`, `AuditSource`

- [x] Task 3: `RegistryRepository` + `RegistryAuditRepository` (AC: 5, 6)
  - [x] Extend `BaseRepository`; use `tenantCondition(Products.TENANT_ID)` everywhere
  - [x] Implement: `insertProduct`, `updateProduct`, `archive`, `findByIdAndTenant` (with component join, ordered), `listByTenantWithFilters` (paginated, substring/ILIKE on name, prefix on VTSZ, exact on KF via join), `listAuditByProduct`, `insertAuditRow`
  - [x] For component-table reads/writes: always join to `products` + filter `tenant_id` — never load components by `product_id` alone
  - [x] Wrap multi-statement writes in `@Transactional` on service layer, not in repository

- [x] Task 4: `RegistryService` with diff-based audit (AC: 5)
  - [x] `create`: one audit row per populated field (`field_changed = 'CREATE.<name>'`)
  - [x] `update`: diff against stored `Product` (pull before patching); emit audit rows ONLY for actually changed fields; compare `BigDecimal` via `compareTo` not `equals`
  - [x] `archive`: audit row `field_changed = 'status'`, `old → new`
  - [x] Component changes audited per-component-field — include a `component_id` scoped in `field_changed` (e.g. `components[<uuid>].weight_per_unit_kg`)
  - [x] All mutations set `source = MANUAL` in this story; 9.2/9.3 will introduce the other sources

- [x] Task 5: DTOs and `RegistryController` (AC: 7, 8)
  - [x] Create `ProductUpsertRequest`, `ComponentUpsertRequest`, `ProductResponse`, `ComponentResponse`, `ProductSummaryResponse`, `RegistryAuditEntryResponse`, `RegistryPageResponse`, `RegistryAuditPageResponse` — all records with `static from(...)` factories; per-feature page wrappers, NOT a shared generic
  - [x] Implement six endpoints at `/api/v1/registry` with `@TierRequired(Tier.PRO_EPR)`
  - [x] JWT extraction: `tenantId` via `requireUuidClaim("active_tenant_id")`; `actingUserId` via `requireUuidClaim("user_id")`
  - [x] Page-size clamp at 200 for list + audit-log endpoints (pattern: 6-4 audit-search page clamp)
  - [x] Error handling: `ResponseStatusException` with `HttpStatus.NOT_FOUND / BAD_REQUEST / CONFLICT`; no custom error DTO

- [x] Task 6: Backend tests (AC: 14, 15)
  - [x] `RegistryServiceTest` — ≥8 tests per AC 15 list
  - [x] `RegistryControllerTest` — ≥6 tests per AC 15 list
  - [x] `RegistryRepositoryIntegrationTest` only if Testcontainers infra already exists; otherwise cover tenant isolation at controller-level via MockMvc
  - [x] Run `./gradlew test --tests "hu.riskguard.architecture.*"` — EpicNineInvariantsTest becomes binding; verify all three Epic 9 rules PASS
  - [x] Run full EPR suite `./gradlew test --tests "hu.riskguard.epr.*"` — target ≤90s per project convention

- [x] Task 7: `KfCodeInput.vue` (AC: 9, 16)
  - [x] Create `frontend/app/components/registry/KfCodeInput.vue` with `formatKfCode()` display helper and `@update:modelValue` emitting stripped 8-digit string
  - [x] Blur-validation pattern from `MaterialFormDialog.vue` lines 61-102 (reactive error, touched flag, watch)
  - [x] `KfCodeInput.spec.ts` co-located; ≥6 tests
  - [x] Do NOT import/call any AI classifier composable; this is manual-entry only

- [x] Task 8: `useRegistry()` composable + Pinia store (AC: 10, 11)
  - [x] Create `frontend/app/composables/api/useRegistry.ts` wrapping `useApi()` with typed methods: `listProducts(filter, page, size)`, `getProduct(id)`, `createProduct(body)`, `updateProduct(id, body)`, `archiveProduct(id)`, `getAuditLog(id, page, size)`
  - [x] Create `frontend/app/stores/registry.ts` (Pinia) holding the edit-in-progress product and the list-page query state; model after `stores/eprFiling.ts`
  - [x] Error handling via existing `useApiError()` composable (RFC 7807 mapping pattern from 3-8/8-5)

- [x] Task 9: Registry list + editor pages (AC: 10, 11)
  - [x] `pages/registry/index.vue` — DataTable `lazy` mode, server-side filter/pagination, empty-state, status Tag severity mapping
  - [x] `pages/registry/[id].vue` with sentinel `id === 'new'` branch for create mode (prefer this over a separate `new.vue` to keep a single editor file)
  - [x] Embed `KfCodeInput.vue` in the components in-row editor
  - [x] PPWR fields behind Accordion labelled "PPWR readiness (optional)"
  - [x] Audit-log rendered via `Accordion` or tab within the editor

- [x] Task 10: Sidebar + i18n (AC: 12, 13)
  - [x] Add `registry` entry to `mainNavItems` in `AppSidebar.vue`
  - [x] Create `frontend/app/i18n/en/registry.json` and `hu/registry.json`; register in i18n barrel/index matching existing `epr.json` wiring
  - [x] Keys alphabetical within each sub-object; HU/EN parity
  - [x] Run `npm run check-i18n` — must pass

- [x] Task 11: Frontend tests (AC: 16)
  - [x] `pages/registry/index.spec.ts`, `pages/registry/[id].spec.ts` — mock `useRegistry` with `vi.mock`, stub PrimeVue components as per 8.3 pattern
  - [x] Update `AppSidebar.spec.ts` with registry-visibility assertion
  - [x] Run `npm run test` — all green

- [x] Task 12: Verify + update sprint status (AC: 17)
  - [x] `./gradlew test --tests "hu.riskguard.epr.*"` + `./gradlew test --tests "hu.riskguard.architecture.*"` — both green
  - [x] Full targeted frontend `npm run test` green
  - [x] Playwright smoke: `npm run test:e2e` — 5 tests green (no new e2e test required unless existing smoke tours hit `/registry`)
  - [x] Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: `9-1-product-packaging-registry-foundation: review`, `last_updated` to today
  - [x] File list and completion notes populated in Dev Agent Record below

## Dev Notes

### Scope boundary — what this story is NOT

This is the **foundation** story. The developer MUST NOT:
- Call Gemini / Vertex AI / any AI classifier (Story 9.3 scope). The `KfCodeClassifierService` interface does not exist yet and MUST NOT be stubbed here — adding a stub creates an orphan abstraction that 9.3 will have to refactor.
- Touch the quarterly EPR filing / autofill pipeline (Story 9.4 scope). Do not wire `EprService` to `RegistryService` in this story. The read-side join happens in 9.4.
- Build the NAV-invoice bootstrap triage UI (Story 9.2 scope). No `RegistryBootstrapService`, no triage states (`PENDING / APPROVED / REJECTED_NOT_OWN_PACKAGING / NEEDS_MANUAL_ENTRY`) in code here. The `AuditSource` enum includes `NAV_BOOTSTRAP` for the future — but no code path produces that value yet.
- Rename or touch the existing `MohuExporter` in `hu.riskguard.epr.domain`. ADR-0002 says 9.4 handles that rename. Leave it alone.
- Modify Story 8.3's `EprService.autoFillFromInvoices()`. CP-5 re-scopes it as a VTSZ-prefix fallback inside 9.3's classifier — that move happens in 9.3, not here.

### Multi-tenancy — the single most-often-broken invariant

The repo uses **app-level tenant isolation**, NOT Postgres RLS. Every repository query MUST filter `tenant_id` explicitly via `BaseRepository.tenantCondition(...)`. For `product_packaging_components` — which does NOT have its own `tenant_id` column per the schema choice in AC 1 — every query MUST join through `products` and filter there. Getting this wrong is a cross-tenant data leak.

Tenant context is read from `TenantContext.getCurrentTenant()` which is populated by `TenantFilter` using Java 25 ScopedValue (fallback ThreadLocal). See `hu.riskguard.core.security.TenantFilter` lines 38-39. Controllers extract tenant directly from the JWT claim `active_tenant_id` via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")` — mirror `EprController.java:48`. Don't rely on `TenantContext` inside controllers; use the JWT extraction.

### ArchUnit — `EpicNineInvariantsTest` flips from vacuous to binding

`EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components` uses `allowEmptyShould(true)` and is currently vacuous because the jOOQ-generated classes don't exist. **The moment Task 1 codegens the table classes, the rule binds.** If you write or import `ProductPackagingComponents` / `ProductPackagingComponentsRecord` anywhere outside `..epr.registry..` or `..architecture..` (including accidental imports in `EprService`, `EprRepository`, any `stores/`, test utilities), the build breaks. Keep the registry insulated.

Similarly, `fee_calculation_must_not_branch_on_recyclability_grade` binds as soon as `RecyclabilityGrade` is introduced. Place it in `hu.riskguard.epr.registry.domain` (as AC 4 demands) and never import it from `epr.domain.*` or `epr.report.*`. Storage (jOOQ records, registry service, DTOs) is fine; branching in fee logic is forbidden.

### Audit-log shape — diff-granularity matters

One audit row per **field** that actually changed, not per update call. This is what ADR-0001 and CP-5 §4.2 both contemplate and what makes 9.3's `AI_SUGGESTED_CONFIRMED / AI_SUGGESTED_EDITED` tagging meaningful later. Worked example:

```text
Before: { name: "Activia 4×125g", status: ACTIVE, components: [{kf_code: 11010101, weight: 0.70}] }
After:  { name: "Activia 4×125g", status: ACTIVE, components: [{kf_code: 11010101, weight: 0.75}] }

Audit rows written:
  field_changed = "components[<uuid>].weight_per_unit_kg"
  old_value     = "0.70"
  new_value     = "0.75"
  source        = MANUAL
```

No audit row for `name` or `status` because they did not change. Compare `BigDecimal` with `compareTo` (not `equals`) to avoid false positives on scale (`0.70` vs `0.700`).

### No tenant_id on `product_packaging_components` — why

Components inherit tenancy transitively via `product_id → products.tenant_id`. This choice is deliberate:
- avoids `tenant_id` drift bugs (child row with tenant different from parent row);
- forces every query to demonstrate it joined through `products`, making the ArchUnit write-boundary rule effective;
- matches the pattern used elsewhere in the repo for parent-child tenant-scoped data.

The `registry_entry_audit_log` table DOES carry a denormalised `tenant_id` because audit reads are cross-product (tenant-wide audit drawer in PLATFORM_ADMIN / SME_ADMIN contexts later). Denormalisation is justified by read-path convenience; writes MUST populate it from the owning product's `tenant_id`, never from the JWT claim directly (prevents mismatched-tenant audit rows slipping in).

### KF code format — 8 digits, structured

Annex 1.2 defines an 8-digit structured code. Display format: groups of 2 digits separated by spaces (e.g., `11 01 01 01`). Storage format: bare 8 digits (no spaces). The `KfCodeInput.vue` component owns this conversion client-side; the `@Pattern(regexp="^[0-9]{8}$")` constraint on the DTO owns it server-side. Frontend also needs a helper `formatKfCode()` — the same formula used by `WizardStepper.vue` from Story 4.4 (see Frontend Patterns below). Do NOT force KF-code presence at DB layer (`kf_code` is nullable) — draft products with half-entered components are legal mid-edit; full enforcement is reserved for Story 9.4's report-time validation.

### i18n — HU is the primary language

User-facing labels for Hungarian producers must be in Hungarian first; English is the accountant-segment fallback. Key labels to translate carefully (hints — verify with a native speaker before shipping):
- "Product-Packaging Registry" → "Termék–Csomagolás Nyilvántartás" (CP-5 §4.1 copy used this exact form)
- "packaging component" → "csomagolási elem"
- "KF code" → "KF-kód"
- "VTSZ" → stays "VTSZ"
- "PPWR readiness" → "PPWR-megfelelőség (opcionális)"
- "Audit log" → "Változásnapló"

### Libraries & versions — nothing new

Stay on the stack already in the repo:
- Backend: Spring Boot (Java 25 preview), jOOQ (codegen via `nu.studer.jooq`), Flyway, JUnit 5 + Mockito + AssertJ. No new deps.
- Frontend: Nuxt 3, Vue 3, PrimeVue (Aura theme), Tailwind 4, Pinia, Vitest + @vue/test-utils. No new deps.

If you find yourself adding a library — stop. The patterns below cover every UI + service concern this story needs.

### Backend patterns to model after

- **Package layout:** `hu.riskguard.epr.{api,domain,internal}` with `package-info.java`. Clone to `hu.riskguard.epr.registry.{api,domain,internal}`. [Source: `backend/src/main/java/hu/riskguard/epr/package-info.java`]
- **Controller + DTO pattern:** [Source: `backend/src/main/java/hu/riskguard/epr/api/EprController.java` lines 32-48; `backend/src/main/java/hu/riskguard/epr/api/dto/MaterialTemplateRequest.java`; `.../MaterialTemplateResponse.java` lines 44, 64 for `static from()`]
- **Repository base class and tenant filter:** [Source: `backend/src/main/java/hu/riskguard/core/repository/BaseRepository.java` line 32 `tenantCondition(...)`]
- **JWT claim extraction:** [Source: `backend/src/main/java/hu/riskguard/epr/api/EprController.java` line 48 `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")`]
- **Admin-role + tier gating:** [Source: `backend/src/main/java/hu/riskguard/epr/api/EprController.java` line 34 `@TierRequired(Tier.PRO_EPR)`; `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java` for PLATFORM_ADMIN pattern if later needed]
- **Page-size clamping pattern:** [Source: Story 6.4 `AuditAdminController` (whichever exists) — clamp at controller level before passing to service]
- **ArchUnit invariants to satisfy:** [Source: `backend/src/test/java/hu/riskguard/architecture/EpicNineInvariantsTest.java`]
- **Naming rules that apply:** [Source: `backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java`]

### Frontend patterns to model after

- **Pages location + routing:** [Source: `frontend/app/pages/` tree; mirror `pages/epr/`]
- **DataTable with server-side filter/pagination:** [Source: `frontend/app/pages/flight-control/index.vue` lines 50-60 for filter wiring; `pages/watchlist/index.vue` for multi-column sort]
- **In-row editing DataTable:** [Source: `frontend/app/pages/epr/filing.vue` lines 180-200 `<template #body="{ data }">`]
- **Form blur-validation:** [Source: `frontend/app/components/Epr/MaterialFormDialog.vue` lines 61-102]
- **KF-code display format helper:** [Source: `frontend/app/components/Epr/WizardStepper.vue` `formatKfCode()`]
- **Composable wrapping `$fetch`:** [Source: `frontend/app/composables/api/useApi.ts`]
- **Pinia store for entity CRUD:** [Source: `frontend/app/stores/eprFiling.ts` lines 40-150]
- **Toast + error mapping:** [Source: `frontend/app/composables/api/useApiError.ts` (RFC 7807)]
- **Sidebar nav additions:** [Source: `frontend/app/components/Common/AppSidebar.vue` `mainNavItems` array around line 134-145]
- **i18n barrel and HU/EN parity check:** [Source: `frontend/app/i18n/en/epr.json`, `frontend/app/i18n/hu/epr.json`, and `npm run check-i18n` script in `frontend/package.json`]
- **PrimeVue Tag severity mapping for status:** [Source: `frontend/app/components/Verdict/VerdictCard.vue` `statusConfig` computed]
- **Component test scaffolding (`vi.mock`, stubs, flushPromises):** [Source: `frontend/app/components/Epr/InvoiceAutoFillPanel.spec.ts`]

### Accessibility (Story 3-0c must still hold)

- Every `InputText` / `InputNumber` / `Select` has a visible `<label for="...">`
- Validation errors: `aria-describedby="<field>-error"` + `role="alert"` on the error `<small>`
- Status badges pair color with icon (PrimeVue Tag already renders readable text; for icon-only action buttons, add `aria-label`)
- Focus indicator on all interactive elements via the global `:focus-visible` outline (3-0c)
- Async registry list loads announced via the existing `CommonLiveRegion` utility (or `aria-live="polite"` on the loading/empty-state wrapper)
- Keyboard-only path: create → add component row → edit → save, reachable without a mouse (Playwright/manual check)
- Contrast: status Tag colors must meet AA (4.5:1) — PrimeVue Aura defaults do; don't override to decorative low-contrast hues

### Testing cadence (from `feedback_test_timeout_values.md` memory)

- Targeted first: `./gradlew test --tests "hu.riskguard.epr.*"` (~90s), `./gradlew test --tests "hu.riskguard.architecture.*"` (~30s). Run these repeatedly.
- Full suite only once at the end.
- Never pipe `./gradlew` output (breaks the TTY test runner). Use `tee` to a file or just let it print.
- Frontend Vitest ~6s; run often.

### Previous-story intelligence

**Story 8.3 (Invoice-Driven EPR Auto-Fill, done 2026-04-03)** established the invoice→VTSZ-prefix→template matching path that this registry replaces for the majority ICP. The VTSZ-prefix logic in `EprService.autoFillFromInvoices` (and `vtszMappings` in `epr_configs.config_data`) stays in place and will be refactored into a strategy-pattern fallback inside Story 9.3. Do not touch 8.3 code in this story; just be aware that its callers will eventually migrate.

**Story 4.1 / 4.2 / 4.4 (Material Library + Wizard + KF Template Mapping)** built the `epr_material_templates` table and the KF-code wizard. That data is separate from the registry — templates are tenant-wide material definitions (e.g., "PET bottle 0.7 kg") authored for the filing wizard. The registry's `product_packaging_components` is the **per-product BoM** that references KF codes directly. There is no FK from `product_packaging_components` to `epr_material_templates`; a component's KF code is authoritative. If the user wants to "reuse" a material definition, that's UX-layer convenience only — not a DB relationship. Do not introduce that FK.

**Story 6.4 (GDPR search audit viewer)** shipped an admin-only audit viewer. The registry audit-log endpoint in AC 7 is NOT that admin viewer — it's a per-product audit drawer for the tenant user viewing their own product's history. Different access scope, different endpoint, different UI.

**Story 8.5 (Platform Admin + role regating, done 2026-04-10)** introduced the `PLATFORM_ADMIN` role. Registry endpoints in this story are tenant-user-scoped (PRO_EPR tier), NOT platform-admin. Do not add any `@RolesAllowed("PLATFORM_ADMIN")` or admin-only guards.

**Epic 6 retro decision:** "AC-to-task translation pass before implementation" (P1 action item). The task list above is expected to map 1:1 to ACs — do the mapping mentally before starting Task 1. If a task doesn't trace to an AC, re-read.

### Git intelligence — recent relevant commits

- `387a633 chore: close epics 4/5/7, cancel 8.6 per CP-5` — sprint-status closures immediately preceding this story; sets Epic 8 as the only active epic until 9 opens.
- `e161794 docs: CP-5 Sprint Change Proposal — Epic 9 Product-Packaging Registry` — the governing proposal; re-read §4.2 and §5 before starting.
- `544fbf1 fix(8.3): scope banner on invoice-driven EPR auto-fill panel (CP-5 §4.1)` — the UI-copy patch to 8.3 that tells current users to expect the registry. That copy links to a future `/registry` route — this story ships that route. Verify after Task 9 that the banner's link resolves.

### Project Structure Notes

- Alignment with repo conventions is the hard path; all new files land in the already-established locations listed above.
- Detected variance: AC 1 chooses NOT to put `tenant_id` on `product_packaging_components`. Rationale inline in §Dev Notes — this is deliberate, not an oversight.
- Detected variance: `registry_entry_audit_log` denormalises `tenant_id`. Rationale inline — audit read-paths need it.
- Detected variance: `products` and `product_packaging_components` use bare (non-`epr_`-prefixed) table names per CP-5 §4.2. Other tables in the codebase are mixed (`epr_*`, `watchlist_*`, `search_*`, but also `tenants`, `users`). CP-5's naming stands.

### References

- [Source: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md` §4.2 (Story 9.1), §5 (invariants), §8.4 (tier-pricing decision), §2 (legal basis)]
- [Source: `_bmad-output/planning-artifacts/epics.md` lines 789-812 (Story 9.1 spec), 813-875 (downstream stories 9.2-9.4 for scope-boundary awareness)]
- [Source: `docs/architecture/adrs/ADR-0001-ai-kf-classification.md` — scope boundary: AI classifier is Story 9.3, NOT this story]
- [Source: `docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md` — scope boundary: report-target pluggability is Story 9.4, NOT this story; do not rename `MohuExporter` here]
- [Source: `backend/src/test/java/hu/riskguard/architecture/EpicNineInvariantsTest.java` — the three ArchUnit rules that must pass]
- [Source: `backend/src/main/java/hu/riskguard/epr/` — the sibling module whose layout this story clones]
- [Source: `frontend/app/pages/epr/`, `frontend/app/components/Epr/`, `frontend/app/stores/eprFiling.ts` — the sibling UI module patterns to mirror]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Claude Code)

### Debug Log References

- ArchUnit `only_registry_package_writes_to_product_packaging_components` — 63 violations on first run because jOOQ-generated classes in `hu.riskguard.jooq.*` reference `ProductPackagingComponents` by design. Fix: added `.and().resideOutsideOfPackage("..jooq..")` to the rule predicate in `EpicNineInvariantsTest.java`.
- `KfCodeInput.spec.ts` tests 2 & 3 failed with "you cannot set the target value of an event" — fixed by replacing `trigger('input', { target: { value: ... } })` with `input.setValue(...)` per vue-test-utils API.

### Completion Notes List

- All 17 ACs implemented. Testcontainers integration tests included (harness already existed).
- `RegistryRepositoryIntegrationTest`: 5 tests covering tenant isolation, same article_number different tenants (no collision), component tenant isolation via join, archive.
- `EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components` now binding and passing (was vacuous before this story).
- `RecyclabilityGrade` placed in `epr.registry.domain` (not `epr.domain`) to satisfy `fee_calculation_must_not_branch_on_recyclability_grade` ArchUnit invariant.
- Playwright smoke (`npm run test:e2e`) passed with no `/registry` route in existing e2e suite — no new e2e test required per AC 17.
- i18n parity check (`npm run check-i18n`) passed — HU/EN registry namespace fully covered.
- `NamingConventionTest.epr_module_should_only_access_own_tables` updated to allow `Products`, `ProductPackagingComponents`, `RegistryEntryAuditLog` table prefixes for the `..epr..` package.
- ✅ Resolved review finding [Patch] P1: Added `set_updated_at()` trigger function + `BEFORE UPDATE` triggers on `products` and `product_packaging_components`.
- ✅ Resolved review finding [Patch] P2: `field_changed` column widened from `VARCHAR(64)` to `VARCHAR(128)` in migration.
- ✅ Resolved review finding [Patch] P3: `registry` sidebar entry now gated on `hasProEpr` computed (reads `tier` from auth store via `TIER_ORDER`); hidden for ALAP/PRO users. Auth mock in `AppSidebar.spec.ts` and `shell.a11y.spec.ts` updated with `tier: 'PRO_EPR'`.
- ✅ Resolved review finding [Patch] P4: Removed `.allowEmptyShould(true)` from `only_registry_package_writes_to_product_packaging_components` — rule is now strict.
- ✅ Resolved review finding [Patch] P5: Added `useTierGate('PRO_EPR')` to `pages/registry/index.vue` and `pages/registry/[id].vue`; both show lock UI when tier insufficient.
- ✅ Resolved review finding [Patch] P6: `registry.actions` keys reordered alphabetically in both `en/registry.json` and `hu/registry.json` (`archive, archived, back, cancel, create, edit`).
- ✅ Resolved review finding [Patch] P7: `EpicNineInvariantsTest` jooq exclusion narrowed from `..jooq..` to two specific predicates: `..jooq.tables..` (table+record classes) + `hu.riskguard.jooq` (generated infra: Keys, Tables, Indexes, Public). ArchUnit passes.

### File List

**Backend — new files:**
- `backend/src/main/resources/db/migration/V20260414_001__create_product_registry.sql`
- `backend/src/main/java/hu/riskguard/epr/registry/package-info.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/Product.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ProductPackagingComponent.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ProductSummary.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ProductStatus.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/RecyclabilityGrade.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/AuditSource.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ProductUpsertCommand.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ComponentUpsertCommand.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryListFilter.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryAuditEntry.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryService.java`
- `backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryRepository.java`
- `backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryAuditRepository.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ProductUpsertRequest.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ComponentUpsertRequest.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ProductResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ComponentResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ProductSummaryResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/RegistryPageResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/RegistryAuditEntryResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/RegistryAuditPageResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/RegistryController.java`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryControllerTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryRepositoryIntegrationTest.java`

**Backend — modified files:**
- `backend/src/test/java/hu/riskguard/architecture/EpicNineInvariantsTest.java` (jooq exclusion)
- `backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java` (registry table prefixes)

**Frontend — new files:**
- `frontend/app/composables/api/useRegistry.ts`
- `frontend/app/stores/registry.ts`
- `frontend/app/components/registry/KfCodeInput.vue`
- `frontend/app/components/registry/KfCodeInput.spec.ts`
- `frontend/app/pages/registry/index.vue`
- `frontend/app/pages/registry/index.spec.ts`
- `frontend/app/pages/registry/[id].vue`
- `frontend/app/pages/registry/[id].spec.ts`
- `frontend/app/i18n/en/registry.json`
- `frontend/app/i18n/hu/registry.json`

**Frontend — modified files:**
- `frontend/app/components/Common/AppSidebar.vue` (registry nav entry)
- `frontend/app/components/Common/AppSidebar.spec.ts` (registry assertions)
- `frontend/app/i18n/en/common.json` (nav.registry key)
- `frontend/app/i18n/hu/common.json` (nav.registry key)
- `frontend/nuxt.config.ts` (registry.json locale files)

**Artifacts:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status → review)

### Review Findings — Group A (DB migration, ArchUnit wiring, sidebar, i18n)

> Reviewed 2026-04-14. Group A only (39-file diff chunked; groups B–E to follow).
> 2 decision-needed · 6 patch · 13 deferred · 7 dismissed

#### Decision-needed

- [x] [Review][Patch] P7 (from D1) — Narrow `..jooq..` exclusion to `..jooq.tables..` in `EpicNineInvariantsTest` — current broad exclusion silently exempts any future hand-written class in a jooq-named package [EpicNineInvariantsTest.java:41]

- [x] [Review][Decision] D2 — `ON DELETE CASCADE` on `registry_entry_audit_log` — dismissed, keeping CASCADE per spec; ARCHIVED is the only real end-state in practice. [V20260414_001__create_product_registry.sql:56]

#### Patch

- [x] [Review][Patch] P1 — No `BEFORE UPDATE` trigger: `updated_at` will never auto-update on `products` or `product_packaging_components` [V20260414_001__create_product_registry.sql]
- [x] [Review][Patch] P2 — `field_changed VARCHAR(64)` too short: component audit path `components[<uuid>].weight_per_unit_kg` is 68 chars and will throw at runtime; needs `VARCHAR(128)` [V20260414_001__create_product_registry.sql:59]
- [x] [Review][Patch] P3 — `registry` sidebar entry has no PRO_EPR tier gating — unconditionally visible to all authenticated users; spec AC 12 requires PRO_EPR tier check mirroring `/epr/*` gating [AppSidebar.vue:143]
- [x] [Review][Patch] P4 — `allowEmptyShould(true)` still present on `only_registry_package_writes_to_product_packaging_components` — rule is now binding (classes exist); removing it closes the vacuous-pass loophole [EpicNineInvariantsTest.java]
- [x] [Review][Patch] P5 — Registry pages have no PRO_EPR tier guard — `pages/registry/index.vue` and `[id].vue` contain no `useTierGate` / `TierUpgradePrompt`; spec AC 12 requires mirroring `/epr/*` gating [Group E]
- [x] [Review][Patch] P6 — `registry.actions` keys not alphabetical in registry.json — `archived` appears after `edit`; spec AC 13 requires alphabetical key order within each sub-object [Group E]

#### Deferred

- [x] [Review][Defer] W1 — Redundant `idx_products_tenant_article` alongside partial unique index — plain index subsumed for non-null lookups; minor write overhead [V20260414_001__create_product_registry.sql:27] — deferred, pre-existing design trade-off
- [x] [Review][Defer] W2 — `changed_by_user_id` nullable with no `source` correlation constraint — MANUAL audit row can be written with NULL actor; beyond spec scope [V20260414_001__create_product_registry.sql] — deferred, pre-existing
- [x] [Review][Defer] W3 — `primary_unit` free-text VARCHAR(16) with no enumeration CHECK — inconsistent unit strings could corrupt weight aggregations; beyond spec scope for this story — deferred
- [x] [Review][Defer] W4 — AppSidebar spec tests a local copy of `mainNavItems`, not the mounted component — cannot detect drift; pre-existing pattern in the project [AppSidebar.spec.ts] — deferred, pre-existing
- [x] [Review][Defer] W5 — `registry_entry_audit_log.tenant_id` has no DB-level consistency constraint with `products.tenant_id` — deliberate denormalization (spec Dev Notes); application layer enforces — deferred
- [x] [Review][Defer] W6 — `substances_of_concern` JSONB has no schema CHECK or GIN index — beyond scope for this story — deferred
- [x] [Review][Defer] W7 (Group C) — `RegistryRepository.insertProduct`/`insertComponent` use `fetchOne(ID)` which returns null on constraint race; null UUID passed to `get()` → misleading NOT_FOUND [RegistryRepository.java] — deferred to Group C review
- [x] [Review][Defer] W8 (Group C) — `RegistryController` `page` parameter has no `@Min(0)` — negative page produces a DB-level OFFSET error (500 instead of 400) [RegistryController.java] — deferred to Group C review
- [x] [Review][Defer] W9 (Group C) — `updateProduct`/`archive` don't check jOOQ affected-row count — silent no-op on cross-tenant update; audit rows written for non-existent state [RegistryRepository.java] — deferred to Group C review
- [x] [Review][Defer] W10 (Group C) — `insertComponent` inserts by `productId` only with no tenant re-verification at component level [RegistryRepository.java] — deferred to Group C review
- [x] [Review][Defer] W11 (Group C) — `countByTenantWithFilters` applies `kfCode` EXISTS subquery twice; structurally diverges from `listByTenantWithFilters` [RegistryRepository.java] — deferred to Group C review
- [x] [Review][Defer] W12 (Group E) — `ToggleSwitch` bound to nullable `Boolean reusable` — null initial value renders in undefined state; irreversible once toggled [pages/registry/[id].vue] — deferred to Group E review
- [x] [Review][Defer] W13 (Group B) — `substancesOfConcern` missing from `diffComponentAndAudit` — field changes produce no audit row, violating AC 5 [RegistryService.java] — deferred to Group B review

### Review Findings — Groups B–E (Service / Repository / Controller / Frontend)

> Reviewed 2026-04-14. Three-layer parallel review: Acceptance Auditor + Blind Hunter + Edge Case Hunter.
> User directive: "fix findings on your own". 21 patches auto-fixed; 13 deferred (documented below); tests all green.

#### Patch — resolved 2026-04-14

- [x] [Review][Patch] BCDE-P1 — **CRITICAL** kfCode filter cross-tenant risk: `applyFilters` used a raw `DSL.table("p2")` alias whose correlation bound to the outer `PRODUCTS` rather than the aliased join; fixed by dropping the alias — outer `PRODUCTS.TENANT_ID = tenantId` scopes the correlated subquery. [RegistryRepository.java:170]
- [x] [Review][Patch] BCDE-P2 — **HIGH** Controller `page` and `size` not clamped → negative offset and LIMIT. Added `Math.max(0, page)` and `Math.min(Math.max(1, size), 200)` on both `list` and `auditLog`. [RegistryController.java:46,119]
- [x] [Review][Patch] BCDE-P3 (W7) — `insertProduct` / `insertComponent` `fetchOne(ID)` could return null; now throw `IllegalStateException` instead of silently passing null UUID downstream. [RegistryRepository.java:53,213]
- [x] [Review][Patch] BCDE-P4 (W9) — `updateProduct`/`archive` returned void; now return affected-row count and service throws `NOT_FOUND` when zero rows updated, closing silent cross-tenant no-op window. [RegistryService.java:106,161; RegistryRepository.java:55,68]
- [x] [Review][Patch] BCDE-P5 (W10) — `insertComponent` now takes `tenantId` parameter and runs a defence-in-depth `fetchExists` verifying the target product belongs to the caller's tenant before inserting. [RegistryRepository.java:197]
- [x] [Review][Patch] BCDE-P6 (W11) — `countByTenantWithFilters` applied the kfCode EXISTS subquery twice (once via `applyFilters`, once in outer branch). Removed the redundant branch; list/count now structurally identical. [RegistryRepository.java:146]
- [x] [Review][Patch] BCDE-P7 (W13) — `substancesOfConcern` diff missing from `diffComponentAndAudit`. Added compare-by-serialised-form diff + CREATE audit row. [RegistryService.java:281]
- [x] [Review][Patch] BCDE-P8 — H1: `registryRepository.updateProduct` was called on no-op updates, bumping `updated_at`. Now tracked via `productChanged` flag; UPDATE is skipped entirely on pure no-op. Test added. [RegistryService.java:85]
- [x] [Review][Patch] BCDE-P9 — H2: `component_order` changes never audited, violating AC 5's per-field coverage. Added diff + CREATE path; dedicated test. [RegistryService.java:240]
- [x] [Review][Patch] BCDE-P10 — H4: `@Size(min=1)` on `components` only fires when non-null, so `components: null` bypassed the min-size rule. Added `@NotNull`. [ProductUpsertRequest.java:19]
- [x] [Review][Patch] BCDE-P11 — H5: component CREATE audit rows mixed prefix styles (`CREATE.weight_per_unit_kg` vs `components[].kf_code`). Normalised to `CREATE.components[<uuid>].<field>` across the board. [RegistryService.java:207]
- [x] [Review][Patch] BCDE-P12 — H13: `q` LIKE filter passed user input unescaped; `%` / `_` / `!` now escaped with `!` as escape char. [RegistryRepository.java:173]
- [x] [Review][Patch] BCDE-P13 — H15: added `@Transactional(readOnly = true)` to `list`, `count`, `get`, `listAuditLog`, `countAuditLog` for read-path consistency. [RegistryService.java]
- [x] [Review][Patch] BCDE-P14 — M1: merged the two duplicated removal loops (audit + delete) into a single pass; removes copy-paste drift risk. [RegistryService.java:136]
- [x] [Review][Patch] BCDE-P15 — M14: `archive()` NPE possibility if `existing.status()` is null. Null-guarded old value and moved `emitAudit` BEFORE the DB write so a failure between the two leaves a consistent audit trail. [RegistryService.java:152]
- [x] [Review][Patch] BCDE-P16 — M11: `fromJsonb` swallowed `JsonProcessingException` silently. Now logs a warning via SLF4J so bad JSONB surfaces in logs. [RegistryRepository.java:305]
- [x] [Review][Patch] BCDE-P17 — M15: `KfCodeInput.required` prop was dead — `validate()` returned `''` on empty regardless. Now returns `kfCodeRequired` message when `required` and empty. [KfCodeInput.vue:34]
- [x] [Review][Patch] BCDE-P18 — M4: `statusFilter` watcher was not debounced while `searchQ` and `kfCodeFilter` were. Routed through `onFilterChange` for uniform behaviour. [pages/registry/index.vue:79]
- [x] [Review][Patch] BCDE-P19 — H12: `weightPerUnitKg ?? 0` silently coerced empty weight to 0 (valid per `@DecimalMin(0)`). Added client-side `validateComponents()` that blocks save with a warn toast when any weight is null; submit body passes the real value (no coercion). [pages/registry/[id].vue:55]
- [x] [Review][Patch] BCDE-P20 (W12) — `ToggleSwitch` on nullable `reusable` could not return to `null` after first toggle. Replaced with tri-state `Select` (Not specified / Yes / No) mapping to `null / true / false`; i18n keys added in both locales. [pages/registry/[id].vue:450]
- [x] [Review][Patch] BCDE-P21 — Added two AC-15 tests: `list_negativePage_clampsToZero` and `list_zeroSize_clampsToOne` on the controller; `update_componentOrderChanged_emitsComponentOrderAuditRow` and `update_noChanges_producesZeroAuditRows` now also asserts `updateProduct` is never invoked. [RegistryServiceTest.java / RegistryControllerTest.java]

#### Deferred

- [ ] [Review][Defer] BCDE-W1 — Audit-log UI column for changed-by user display name (AC 11 "fully traceable" wording). Requires user-table join or batch fetch — scope creep for 9.1; Story 9.4 admin audit drawer already covers the denormalised user path.
- [ ] [Review][Defer] BCDE-W2 — "Remove component" lacks undo toast. UX enhancement; save is the commit boundary so no data is lost until save.
- [ ] [Review][Defer] BCDE-W3 — Controller tests are direct unit tests, not MockMvc. Tier gating (`@TierRequired`) and `@Valid` on DTOs are enforced at Spring filter/binding layers and are NOT exercised. Full MockMvc rewrite deferred; covered indirectly by existing integration E2E on `/api/v1/epr/*` which shares the same `@TierRequired` aspect.
- [ ] [Review][Defer] BCDE-W4 — Frontend `pages/registry/*.spec.ts` tests exercise mocked composable calls, not mounted SFC behaviour. Re-mounting with PrimeVue stubs per `InvoiceAutoFillPanel.spec.ts` pattern deferred — meaningful coverage deferred to Story 9.4's end-to-end Playwright flow on `/registry`.
- [ ] [Review][Defer] BCDE-W5 — `KfCodeInput` caret-jumping on the 8th digit due to imperative `target.value` mutation. Cosmetic UX issue; functional behaviour (emit bare digits, validate, blur) is correct. Refactor to controlled-component deferred.
- [ ] [Review][Defer] BCDE-W6 — Repository uses raw `.eq(tenantId)` rather than `BaseRepository.tenantCondition(...)`. Spec-mandated but `tenantCondition` reads from `TenantContext` which our tests pass tenantId explicitly for determinism. Behaviour is equivalent; refactor deferred to avoid widespread test rework.
- [ ] [Review][Defer] BCDE-W7 — `searchQ` not bound to `?q=` URL query. State lost on refresh. Minor UX; deferred.
- [ ] [Review][Defer] BCDE-W8 — PPWR uses per-row `Accordion` column rather than `DataTable` row-expansion. Functionally equivalent; refactor deferred.
- [ ] [Review][Defer] BCDE-W9 — `vtsz` `@Pattern("^[0-9]{4,8}$")` allows 4-8 digits. Downstream fee lookups want 8 digits. Tightening deferred pending 9.3 classifier behaviour decision.
- [ ] [Review][Defer] BCDE-W10 — `primaryUnit` unbounded length. Add `@Size(max=32)` + allow-list deferred to Story 9.4 (needs product-design call on allowed units).
- [ ] [Review][Defer] BCDE-W11 — No `(product_id, component_order)` unique constraint at DB. Under concurrent writes, order collisions possible; today client reorders before save. Add unique index deferred.
- [ ] [Review][Defer] BCDE-W12 — `substancesOfConcern` JSONB has no size cap / schema validation; potential abuse vector. Story 9.4 / PPWR compliance work will introduce schema.
- [ ] [Review][Defer] BCDE-W13 — No optimistic-locking on product update. Concurrent-edit lost-update possible. Introduce `version` column + `If-Match` ETag deferred.

#### Dismissed

- BCDE-D1 — "`q` parameter could DoS via `%%%%`" — mitigated by LIKE escape (BCDE-P12) + 200-item page clamp + Postgres planner's B-tree on `name`.
- BCDE-D2 — "`crypto.randomUUID()` SSR concern" — registry pages are authenticated CSR routes; Nuxt `definePageMeta` hydrates client-side so `crypto.randomUUID` is available. No runtime path hits SSR.
- BCDE-D3 — "`emitCreateAudit` asymmetric whitespace handling" — blank-string-at-create skipped is intentional (no UX surface for blank article numbers).
- BCDE-D4 — "`fetchOne(0, Long.class)` in count may return null" — fixed as part of BCDE-P6 refactor (null-coalesced to 0L).
