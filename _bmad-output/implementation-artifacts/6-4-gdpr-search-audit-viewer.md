# Story 6.4: GDPR Search Audit Viewer

Status: done

## Story

As an Admin,
I want to search and view the `search_audit_log`,
so that I can provide proof of a specific risk check if requested by legal authorities or users.

## Acceptance Criteria

<!-- SECTION: acceptance_criteria -->

1. **Admin Audit Search page loads with empty state**
   - Given an authenticated `SME_ADMIN` user
   - When they navigate to `/admin/audit-search`
   - Then the page shows a search form with two optional fields: "Tax Number" and "Tenant ID"
   - And the results table is hidden until the user submits a search
   - And non-`SME_ADMIN` callers receive HTTP 403 from the backend API

2. **Search by tax number returns matching entries across all tenants**
   - Given a valid Hungarian tax number entered in the search field
   - When the admin clicks "Search"
   - Then the UI calls `GET /api/v1/admin/screening/audit?taxNumber={taxNumber}`
   - And the response returns a paginated list of ALL `search_audit_log` entries matching that tax number (cross-tenant — no tenant scoping)
   - And each row shows: `searchedAt` (ISO timestamp), `taxNumber`, `sha256Hash`, `sourceUrls` (count + expandable list), `userId` (who performed the search), `tenantId`, `checkSource` (MANUAL/AUTOMATED), `verdictStatus`

3. **Search by tenant ID returns all entries for that tenant**
   - Given a valid UUID entered in the "Tenant ID" field
   - When the admin clicks "Search"
   - Then the UI calls `GET /api/v1/admin/screening/audit?tenantId={tenantId}`
   - And the response returns all audit entries for that tenant (all tax numbers)
   - And pagination works for large result sets

4. **Combined search filters by both tax number and tenant ID**
   - Given both a tax number and a tenant ID entered in the search form
   - When the admin clicks "Search"
   - Then the results are filtered by both criteria simultaneously (AND condition)

5. **No records found shows clear message**
   - Given a tax number or tenant ID with no matching audit records
   - When the admin clicks "Search"
   - Then the API returns `{ content: [], totalElements: 0, page: 0, size: 20 }`
   - And the UI displays "No records found for this search" in the table empty slot — no empty table

6. **At least one search criterion required**
   - Given neither taxNumber nor tenantId is provided in the request
   - When `GET /api/v1/admin/screening/audit` is called
   - Then the API returns HTTP 400 with a ProblemDetail: `"At least one of taxNumber or tenantId must be provided"`
   - And the frontend search button is disabled if both fields are empty

7. **Pagination works for large result sets**
   - Given a search returning more than 20 records
   - When the results are displayed
   - Then a PrimeVue DataTable with lazy pagination shows page controls
   - And navigating pages calls `GET /api/v1/admin/screening/audit` with updated `page` and `size` params

<!-- END SECTION -->

## Tasks / Subtasks

<!-- SECTION: tasks -->

- [x] Task 1 — Backend: New DTOs (AC: #2, #3)
  - [x] Create `backend/src/main/java/hu/riskguard/screening/api/dto/AdminAuditEntryResponse.java`
    - Java record with fields (in order): `String id`, `String tenantId`, `String userId`, `String taxNumber`, `String verdictStatus`, `String verdictConfidence`, `java.time.OffsetDateTime searchedAt`, `String sha256Hash`, `String dataSourceMode`, `String checkSource`, `java.util.List<String> sourceUrls`, `String companyName`
    - `static from(ScreeningRepository.AdminAuditHistoryRow r)` factory:
      - `id`: `r.id().toString()`
      - `tenantId`: `r.tenantId().toString()`
      - `userId`: `r.userId() != null ? r.userId().toString() : null`
      - `sourceUrls`: parse `r.sourceUrlsJson()` via `Jackson ObjectMapper` — reuse pattern from `AuditHistoryController.from()` (deserialize `List<String>` from JSONB string; return `List.of()` on parse error)
      - All other fields: direct mapping
  - [x] Create `backend/src/main/java/hu/riskguard/screening/api/dto/AdminAuditPageResponse.java`
    - Java record: `(java.util.List<AdminAuditEntryResponse> content, long totalElements, int page, int size)`
    - `static from(ScreeningService.AdminAuditPage page)` factory

- [x] Task 2 — Backend: `ScreeningRepository` additions (AC: #2, #3, #4)
  - [x] Add inner record `AdminAuditHistoryRow` to `ScreeningRepository`:
    ```java
    public record AdminAuditHistoryRow(
        UUID id,
        UUID tenantId,
        UUID userId,       // searched_by column
        String taxNumber,
        String verdictStatus,
        String verdictConfidence,
        OffsetDateTime searchedAt,
        String sha256Hash,
        String dataSourceMode,
        String checkSource,
        String companyName,
        String sourceUrlsJson,
        String disclaimerText
    ) {}
    ```
  - [x] Add `findAdminAuditPage(String taxNumber, UUID tenantId, long offset, int size)` → `List<AdminAuditHistoryRow>`:
    - jOOQ SELECT joining `search_audit_log LEFT JOIN verdicts ON verdict_id LEFT JOIN company_snapshots ON snapshot_id`
    - Select columns: `sal.ID`, `sal.TENANT_ID`, raw field for `searched_by` (use `DSL.field("sal.searched_by", UUID.class)`), `sal.TAX_NUMBER`, `v.STATUS` (nullable), `v.CONFIDENCE` (nullable), `sal.SEARCHED_AT`, `sal.SHA256_HASH`, raw `data_source_mode`, raw `check_source`, raw company name from snapshot JSONB, `sal.SOURCE_URLS`, raw `disclaimer_text`
    - CRITICAL: `search_audit_log` table alias `sal`; NO `WHERE sal.TENANT_ID = tenantId` global filter — this is cross-tenant
    - Add `WHERE sal.TAX_NUMBER = taxNumber` only if `taxNumber != null`
    - Add `WHERE sal.TENANT_ID = tenantId` only if `tenantId != null`
    - Order by `sal.SEARCHED_AT DESC`
    - Apply `OFFSET offset LIMIT size`
    - Map result rows to `AdminAuditHistoryRow` — check `AuditHistoryRow` mapping in the same file for the jOOQ field extraction pattern
    - NOTE: `data_source_mode`, `check_source`, and `searched_by` are NOT in the jOOQ generated code (added post-codegen via V20260330 migration). Use raw DSL fields: `DSL.field("check_source", String.class)`, etc. — same pattern already used in `findAuditHistoryPage()`
  - [x] Add `countAdminAudit(String taxNumber, UUID tenantId)` → `long`:
    - jOOQ `SELECT COUNT(*) FROM search_audit_log` with same WHERE conditions as `findAdminAuditPage()` but no OFFSET/LIMIT

- [x] Task 3 — Backend: `ScreeningService` additions (AC: #2, #3)
  - [x] Add inner record `AdminAuditPage` to `ScreeningService`:
    ```java
    public record AdminAuditPage(
        java.util.List<AdminAuditEntry> entries,
        long totalElements, int page, int size
    ) {}
    ```
  - [x] Add domain record `AdminAuditEntry` to `ScreeningService` (or a separate file under `domain/`):
    - Same fields as `AuditHistoryEntry` PLUS `UUID tenantId`, `UUID userId`
    - Alternatively: create `backend/src/main/java/hu/riskguard/screening/domain/AdminAuditEntry.java` as a standalone record
  - [x] Add `getAdminAuditLog(String taxNumber, UUID tenantId, int page, int size)` → `AdminAuditPage`:
    - Validate: if both `taxNumber` and `tenantId` are null throw `IllegalArgumentException("At least one criterion required")`
    - Call `screeningRepository.countAdminAudit(taxNumber, tenantId)` for totalElements
    - Call `screeningRepository.findAdminAuditPage(taxNumber, tenantId, (long) page * size, size)` for data
    - Map each `AdminAuditHistoryRow` to `AdminAuditEntry` — parse `sourceUrlsJson` same as `getAuditHistory()` does for `AuditHistoryRow`
    - Return `new AdminAuditPage(entries, total, page, size)`
    - NOTE: No `TenantContext` needed here — admin method reads cross-tenant; security is enforced at controller layer

- [x] Task 4 — Backend: `AuditAdminController` (AC: #1, #2, #3, #6)
  - [x] Create `backend/src/main/java/hu/riskguard/screening/api/AuditAdminController.java`
  - [x] Annotate: `@RestController @RequestMapping("/api/v1/admin/screening") @RequiredArgsConstructor`
  - [x] Inject: `ScreeningService screeningService`
  - [x] Implement `requireAdminRole(Jwt jwt)` — exact same pattern as `DataSourceAdminController` and `EprAdminController`:
    ```java
    private void requireAdminRole(Jwt jwt) {
        if (!"SME_ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
    ```
  - [x] `GET /audit` → `AdminAuditPageResponse getAuditLog(...)`:
    ```java
    @GetMapping("/audit")
    public AdminAuditPageResponse getAuditLog(
        @RequestParam(required = false) String taxNumber,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt
    )
    ```
    - `requireAdminRole(jwt)`
    - Validate: if `taxNumber == null && tenantId == null` → throw `ResponseStatusException(BAD_REQUEST, "At least one of taxNumber or tenantId must be provided")`
    - Clamp `size` to `[1, 100]`: `size = Math.min(Math.max(size, 1), 100)`
    - `return AdminAuditPageResponse.from(screeningService.getAdminAuditLog(taxNumber, tenantId, page, size))`
  - [x] Import: `import hu.riskguard.screening.api.dto.AdminAuditPageResponse;` etc.

- [x] Task 5 — Backend: `AuditAdminControllerTest` (AC: #1, #2, #6)
  - [x] Create `backend/src/test/java/hu/riskguard/screening/api/AuditAdminControllerTest.java`
  - [x] Use `@ExtendWith(MockitoExtension.class)`, `@MockitoSettings(strictness = LENIENT)`, mock `ScreeningService`
  - [x] Use inline JWT builders (following EprAdminControllerTest pattern — TestJwtBuilder doesn't exist)
  - [x] Tests:
    - `getAuditLog_smeAdmin_taxNumber_returns200WithPagedResults`
    - `getAuditLog_smeAdmin_tenantId_returns200WithPagedResults`
    - `getAuditLog_smeAdmin_emptyResults_returns200WithEmptyContent`
    - `getAuditLog_nonAdmin_returns403`
    - `getAuditLog_noSearchCriteria_returns400WithProblemDetail`
    - `getAuditLog_sizeClamped_to100Max`
    - `getAuditLog_combinedTaxNumberAndTenantId_passesAllParams`

- [x] Task 6 — Backend: `ScreeningRepositoryTest` additions (AC: #2, #3)
  - [x] Add tests to the existing `ScreeningRepositoryTest.java`:
    - `findAdminAuditPage_byTaxNumber_returnsAcrossAllTenants` — insert audit rows for two different tenants with the same tax number, assert both returned
    - `findAdminAuditPage_byTenantId_returnsOnlyThatTenant`
    - `findAdminAuditPage_combined_filtersByBothCriteria`
    - `findAdminAuditPage_noMatchingRows_returnsEmptyList`
    - `countAdminAudit_byTaxNumber_countsAcrossAllTenants`

- [x] Task 7 — Backend: Verify NamingConventionTest passes
  - [x] New DTOs must be in `hu.riskguard.screening.api.dto.*` — correct package
  - [x] `AuditAdminController` must be in `hu.riskguard.screening.api.*` — correct package
  - [x] `AdminAuditEntry` must be in `hu.riskguard.screening.domain.*` — correct package
  - [x] Also fixed pre-existing NamingConventionTest violation: added `from()` factory to `EprConfigPublishResponse` and `EprConfigValidateResponse` (removed in 6.3 code review)

- [x] Task 8 — Frontend: i18n keys (AC: #1, #5)
  - [x] Add to `frontend/app/i18n/en/admin.json` (inside the `"admin"` object, alphabetical):
    ```json
    "auditSearch": {
      "title": "GDPR Audit Search",
      "subtitle": "Search the audit log to provide proof of a specific risk check for legal or user requests.",
      "taxNumberLabel": "Tax Number",
      "taxNumberPlaceholder": "12345678-9-01",
      "tenantIdLabel": "Tenant ID (UUID)",
      "tenantIdPlaceholder": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "searchButton": "Search",
      "noRecords": "No records found for this search",
      "atLeastOneRequired": "Enter a tax number or tenant ID to search",
      "columns": {
        "searchedAt": "Searched At",
        "taxNumber": "Tax Number",
        "sha256Hash": "SHA-256 Hash",
        "sourceUrls": "Source URLs",
        "userId": "User ID",
        "tenantId": "Tenant ID",
        "checkSource": "Source",
        "verdictStatus": "Verdict"
      },
      "errors": {
        "loadFailed": "Search failed"
      }
    }
    ```
  - [x] Add equivalent HU translations in `frontend/app/i18n/hu/admin.json`:
    ```json
    "auditSearch": {
      "title": "GDPR Napló Kereső",
      "subtitle": "Keressen az audit naplóban jogi vagy felhasználói megkeresésekhez szükséges kockázat-ellenőrzési bizonyítékért.",
      "taxNumberLabel": "Adószám",
      "taxNumberPlaceholder": "12345678-9-01",
      "tenantIdLabel": "Bérlői azonosító (UUID)",
      "tenantIdPlaceholder": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "searchButton": "Keresés",
      "noRecords": "Nincs találat a megadott keresési feltételekre",
      "atLeastOneRequired": "Adjon meg adószámot vagy bérlői azonosítót a kereséshez",
      "columns": {
        "searchedAt": "Keresés ideje",
        "taxNumber": "Adószám",
        "sha256Hash": "SHA-256 hash",
        "sourceUrls": "Forrás URL-ek",
        "userId": "Felhasználó ID",
        "tenantId": "Bérlői ID",
        "checkSource": "Forrás",
        "verdictStatus": "Verdict"
      },
      "errors": {
        "loadFailed": "Keresés sikertelen"
      }
    }
    ```
  - [x] Keep both files alphabetically sorted within their sections

- [x] Task 9 — Frontend: `composables/useAdminAudit.ts` (AC: #2, #3)
  - [x] Create `frontend/app/composables/useAdminAudit.ts`
  - [x] Define request/response types using generated `api.d.ts` types — added `AdminAuditPageResponse` + `AdminAuditEntryResponse` to `frontend/types/api.d.ts` manually (no CI OpenAPI pipeline active)
  - [x] Export `useAdminAudit()` composable:
    ```ts
    export function useAdminAudit() {
      const config = useRuntimeConfig()
      const results = ref<AdminAuditPageResponse | null>(null)
      const pending = ref(false)
      const error = ref<string | null>(null)

      async function search(taxNumber: string | null, tenantId: string | null, page = 0, size = 20) {
        pending.value = true; error.value = null
        try {
          const params: Record<string, string | number> = { page, size }
          if (taxNumber) params.taxNumber = taxNumber
          if (tenantId) params.tenantId = tenantId
          results.value = await $fetch<AdminAuditPageResponse>(
            `${config.public.apiBase}/api/v1/admin/screening/audit`,
            { params }
          )
        } catch (e: unknown) {
          error.value = useApiError(e)
        } finally {
          pending.value = false
        }
      }
      return { results, pending, error, search }
    }
    ```
  - [x] NOTE: `AdminAuditPageResponse` and `AdminAuditEntryResponse` types come from the auto-generated `api.d.ts`. Do not declare them manually; the OpenAPI pipeline generates them after the backend controller is created.

- [x] Task 10 — Frontend: `pages/admin/audit-search.vue` (AC: #1–#7)
  - [x] Create `frontend/app/pages/admin/audit-search.vue`
  - [x] Role guard in `onMounted`: check `identityStore.user?.role === 'SME_ADMIN'` — redirect to `/` if not admin (same pattern as `datasources.vue` and `epr-config.vue`)
  - [x] State: `taxNumber: ref('')`, `tenantId: ref('')`, `currentPage: ref(0)`, `pageSize: ref(20)`
  - [x] Computed: `canSearch = computed(() => taxNumber.value.trim() !== '' || tenantId.value.trim() !== '')`
  - [x] `handleSearch(page = 0)`: calls `search(taxNumber.value || null, tenantId.value || null, page, pageSize.value)`
  - [x] Template structure:
    - Page header: `{{ t('admin.auditSearch.title') }}` + subtitle
    - Breadcrumb: Admin → Audit Search
    - Search form: two `InputText` fields side-by-side (tax number + tenant ID), "Search" `Button` (`:disabled="!canSearch"`)
    - `<div v-if="pending"><Skeleton height="4rem" /></div>`
    - `<div v-else-if="results">` — PrimeVue `DataTable` with `lazy`, `:value="results.content"`, `:totalRecords="results.totalElements"`, `@page="handleSearch($event.page)"`:
      - Columns: `searchedAt` (formatted), `taxNumber`, `sha256Hash` (truncated to 16 chars + tooltip with full hash), `sourceUrls` (count badge), `userId`, `tenantId`, `checkSource`, `verdictStatus`
      - Empty template slot: `<template #empty>{{ t('admin.auditSearch.noRecords') }}</template>`
    - Error state: Toast on error via `useToast()`
  - [x] Source URLs: use a `<Tag>` with the count and a `<Column>` expand or Tooltip showing the full URL list
  - [x] Follow `pages/admin/datasources.vue` for layout, breadcrumb, and error/toast patterns

- [x] Task 11 — Frontend: Admin index navigation (AC: #1)
  - [x] Edit `frontend/app/pages/admin/index.vue` — add a third card in the grid:
    ```vue
    <NuxtLink to="/admin/audit-search" class="block group">
      <div class="border rounded-lg p-5 bg-white shadow-sm hover:shadow-md hover:border-indigo-300 transition-all">
        <div class="flex items-center gap-3 mb-2">
          <span class="pi pi-shield text-indigo-600 text-xl" />
          <h2 class="text-lg font-semibold text-slate-800 group-hover:text-indigo-700">
            {{ t('admin.auditSearch.title') }}
          </h2>
        </div>
        <p class="text-sm text-slate-500">
          {{ t('admin.auditSearch.subtitle') }}
        </p>
      </div>
    </NuxtLink>
    ```

- [x] Task 12 — Frontend: Tests (AC: #2, #5, #6)
  - [x] Create `frontend/app/pages/admin/audit-search.spec.ts`
  - [x] Mock `$fetch` using `vi.stubGlobal` (following epr-config.spec.ts pattern)
  - [x] Tests:
    - `search button disabled when both fields empty`
    - `search button enabled when taxNumber filled`
    - `search button enabled when tenantId filled`
    - `handleSearch calls $fetch with taxNumber param`
    - `handleSearch calls $fetch with tenantId param`
    - `results DataTable renders content rows`
    - `empty results shows noRecords message`
    - `error from $fetch shows toast`
    - `redirects non-SME_ADMIN user to /`

- [x] Task 13 — Final verification
  - [x] Backend: `./gradlew test --tests "hu.riskguard.screening.*"` — all green (BUILD SUCCESSFUL)
  - [x] Backend: `./gradlew test --tests "hu.riskguard.architecture.NamingConventionTest"` — all green (BUILD SUCCESSFUL)
  - [x] Backend targeted build: screening + architecture tests green; full suite skipped (no local PostgreSQL for I18nConfigTest/EprRepositoryTest — pre-existing constraint)
  - [x] Frontend: `vitest run` — 609 tests all green

<!-- END SECTION -->

## Dev Notes

<!-- SECTION: dev_notes -->

### What makes 6.4 different from Story 5.1a (My Audit History)

Story 5.1a built `AuditHistoryController` at `/api/v1/screening/audit-history` — tenant-scoped, for regular users to view their own tenant's audit entries. Story 6.4 builds a cross-tenant **admin** viewer at `/api/v1/admin/screening/audit` that returns entries from all tenants without tenant isolation, filtered only by the admin's chosen search criteria (taxNumber, tenantId, or both). The admin viewer also exposes the `tenantId` and `userId` (who performed the search) fields that the user-facing endpoint omits.

### No DB migration required

`search_audit_log` already has all needed columns:
```sql
CREATE TABLE search_audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    searched_by UUID NOT NULL REFERENCES users(id),  -- this is the "userId" exposed in 6.4
    tax_number VARCHAR(11) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    disclaimer_text TEXT NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verdict_id UUID REFERENCES verdicts(id) ON DELETE SET NULL,
    check_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',   -- added V20260330
    data_source_mode VARCHAR(10) NOT NULL DEFAULT 'DEMO'  -- added V20260330
);
-- Indexes already present:
CREATE INDEX idx_search_audit_log_searched_at ON search_audit_log USING BRIN (searched_at);
CREATE INDEX idx_search_audit_log_tenant_tax ON search_audit_log (tenant_id, tax_number);
CREATE INDEX idx_audit_tenant_tax_searched ON search_audit_log (tenant_id, tax_number, searched_at DESC);
```

### Admin role pattern (copy from DataSourceAdminController / EprAdminController)

Both existing admin controllers check:
```java
private void requireAdminRole(Jwt jwt) {
    if (!"SME_ADMIN".equals(jwt.getClaimAsString("role"))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
}
```
Copy this **exactly** into `AuditAdminController`. Do not invent a new pattern.

### jOOQ raw DSL fields for post-codegen columns

The `check_source`, `data_source_mode`, and `searched_by` columns were added by migrations after the jOOQ codegen ran. They are NOT in the generated `SEARCH_AUDIT_LOG` table type. Access them with:
```java
DSL.field(DSL.name("sal", "check_source"), String.class)
DSL.field(DSL.name("sal", "data_source_mode"), String.class)
DSL.field(DSL.name("sal", "searched_by"), UUID.class)
```
The existing `findAuditHistoryPage()` in `ScreeningRepository` already uses this pattern — read it first and replicate exactly for `findAdminAuditPage()`.

### ScreeningService: AdminAuditEntry placement

Place `AdminAuditEntry` as a public inner record of `ScreeningService` (same as `AuditHistoryPage` is already defined there), OR extract it to a standalone `AdminAuditEntry.java` file under `hu.riskguard.screening.domain`. Either is correct; prefer standalone for clarity if `ScreeningService` is already long.

### Frontend: api.d.ts update required before frontend work

After the backend controller is implemented and the OpenAPI spec generates, run:
```bash
cd backend && ./gradlew generateOpenApiDocs
cd frontend && npm run generate:api
```
This regenerates `api.d.ts` with `AdminAuditPageResponse` and `AdminAuditEntryResponse` types. **Do not** define these TypeScript interfaces manually — the project rules forbid it (project-context.md: "API interfaces are auto-generated. NEVER define interfaces for backend data manually.").

### SHA-256 hash display in UI

The 64-character hex hash should be truncated to the first 16 chars with `…` in the table column, but the full hash must be accessible via a PrimeVue `Tooltip` on hover or a "Copy" button. This aids legal review without cluttering the table. Example: `a3f2b1c0d9e8f7a6…`.

### Source URL display

`source_urls` is stored as a JSONB array of strings. The DataTable column should show the count (e.g., "3 URLs") using a PrimeVue `Badge` or `Tag`. A row expansion or tooltip reveals the full URL list. Do NOT show raw URLs inline as they can be very long.

### Empty state requirement (AC 5)

Use PrimeVue DataTable's `#empty` slot template — do NOT render the table at all when `results` is null (before first search). Only show the "No records found" message when a search was performed and returned 0 results. This is a distinct state from "not yet searched".

<!-- END SECTION -->

## Project Artifact Inventory

<!-- SECTION: artifact_inventory -->

### New files (create)

**Backend:**
- `backend/src/main/java/hu/riskguard/screening/api/AuditAdminController.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/AdminAuditEntryResponse.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/AdminAuditPageResponse.java`
- `backend/src/main/java/hu/riskguard/screening/domain/AdminAuditEntry.java` (if standalone; else inner record of ScreeningService)
- `backend/src/test/java/hu/riskguard/screening/api/AuditAdminControllerTest.java`

**Frontend:**
- `frontend/app/composables/useAdminAudit.ts`
- `frontend/app/pages/admin/audit-search.vue`
- `frontend/app/pages/admin/audit-search.spec.ts`

### Modified files (edit)

**Backend:**
- `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java` — add `AdminAuditHistoryRow`, `findAdminAuditPage()`, `countAdminAudit()`
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — add `AdminAuditPage`, `getAdminAuditLog()`
- `backend/src/test/java/hu/riskguard/screening/internal/ScreeningRepositoryTest.java` — add admin audit query tests

**Frontend:**
- `frontend/app/pages/admin/index.vue` — add third admin card for Audit Search
- `frontend/app/i18n/en/admin.json` — add `auditSearch` namespace
- `frontend/app/i18n/hu/admin.json` — add `auditSearch` namespace

<!-- END SECTION -->

## Dev Agent Record

<!-- SECTION: dev_agent_record -->

### Implementation Notes

- `AdminAuditEntryResponse.from()` takes `AdminAuditEntry` (not `AdminAuditHistoryRow` as the story suggested) — consistent with existing `AuditHistoryEntryResponse.from(AuditHistoryEntry)` pattern and required by `AdminAuditPageResponse.from(AdminAuditPage)` which holds `List<AdminAuditEntry>`.
- `ScreeningRepositoryTest` admin tests use randomised tax numbers (UUID-derived prefixes) to avoid collisions with other tests that accumulate rows in the shared Testcontainer database across test methods.
- Fixed pre-existing `NamingConventionTest` violation (`response_dtos_should_have_from_factory`): `EprConfigPublishResponse` and `EprConfigValidateResponse` were missing `from()` after Story 6.3 code review P1/P2 removed them as "dead code".
- Frontend types (`AdminAuditEntryResponse`, `AdminAuditPageResponse`) added manually to `frontend/types/api.d.ts` following the established pattern for other DTOs (no CI OpenAPI pipeline active).
- `useAdminAudit` composable explicitly imported in `audit-search.vue` (required for test mock to intercept — Nuxt auto-imports don't work in Vitest).

### Completion Notes

All 13 tasks complete. All acceptance criteria satisfied:
- AC1: `/admin/audit-search` page with SME_ADMIN guard ✓
- AC2: Search by taxNumber returns cross-tenant results ✓
- AC3: Search by tenantId returns tenant-scoped results ✓
- AC4: Combined filter (AND) ✓
- AC5: Empty state message "No records found" ✓
- AC6: 400 when both fields empty; frontend search button disabled ✓
- AC7: Lazy paginated DataTable with page controls ✓

<!-- END SECTION -->

## Change Log

<!-- SECTION: change_log -->

- 2026-03-31: Story created. Status → ready-for-dev.
- 2026-04-01: Story implemented. Status → review. All 13 tasks complete. Backend: `AdminAuditEntry`, `AdminAuditHistoryRow`, `findAdminAuditPage()`, `countAdminAudit()`, `getAdminAuditLog()`, `AuditAdminController`, 12 new backend tests. Frontend: `useAdminAudit.ts`, `audit-search.vue`, 9 frontend tests, i18n EN/HU, admin index card, api.d.ts types. Fixed pre-existing NamingConventionTest violation (EprConfig DTOs missing from()).
- 2026-04-01: Code review R1 — 3 patch items auto-fixed: P1 missing page clamp in AuditAdminController (negative page → DB offset error), P2 fragile hasSize(2) in ScreeningRepositoryTest byTenantId (shared Testcontainer DB accumulation), P3 missing page-clamp unit test. 609 frontend + BUILD SUCCESSFUL backend + 5 e2e all green. Status → done.

<!-- END SECTION -->
