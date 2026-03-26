# Story 5.1: Watchlist Bulk PDF Export & Mobile Dispatcher

Status: review

## Story

As a User,
I want to select partners from my watchlist and export a professional PDF status report with a native mobile share option,
so that I can provide court-ready evidence of my due diligence to banks or auditors instantly from my device.

## Acceptance Criteria

### AC 1: SHA-256 Hash Surfaced in Watchlist API Response

**Given** a watchlist entry whose partner has been screened at least once
**When** `GET /api/v1/watchlist` is called
**Then** the response includes `"latestSha256Hash"` (the most recent SHA-256 from `search_audit_log`, denormalized onto `watchlist_entries.latest_sha256_hash`)
**And** for entries never screened (or screened before this migration), `latestSha256Hash` is `null`

### AC 2: Hash Kept Fresh on Every Verdict Change

**Given** a partner on a tenant's watchlist
**When** a `PartnerStatusChanged` event fires (user search or 24h monitor)
**Then** `watchlist_entries.latest_sha256_hash` is updated with `event.sha256Hash()` (alongside `last_verdict_status` and `last_checked_at`)
**And** if `event.sha256Hash()` is `null` or the sentinel `"HASH_UNAVAILABLE"`, the column retains the previous value (no overwrite with null/sentinel)

### AC 3: Export Status PDF — Multi-Select in Watchlist Table

**Given** the Watchlist page with at least one entry
**When** the user checks one or more checkboxes in the watchlist table
**Then** each selected row is highlighted
**And** the "Export Status PDF" button shows the count of selected entries: "Export PDF (3)"
**When** no entries are selected
**Then** the button reads "Export PDF (all)" and exports all entries

### AC 4: PDF Content

**Given** a selection of partners (or all, if none selected)
**When** "Export Status PDF" is clicked
**Then** the system generates a client-side PDF containing:
- Report header: "RiskGuard — Partner Due Diligence Report" + current date
- One row per partner: Partner Name, Tax Number, Verdict Status (localized), Last Check Timestamp (ISO-8601)
- SHA-256 hash column: 64-char hex string, or "N/A" if null
- Footer: "This report is for informational purposes only and does not constitute legal advice."
**And** the PDF filename is `riskguard-watchlist-{YYYY-MM-DD}.pdf`

### AC 5: Mobile Share via AuditDispatcher

**Given** the PDF has been generated
**When** the device supports `navigator.share` (typically mobile) AND the Share Sheet API supports files
**Then** `navigator.share({ files: [pdfFile], title: 'RiskGuard Due Diligence Report', text: 'Partner status report' })` is called
**And** the user can dispatch the PDF to email, Slack, WhatsApp, etc.
**When** `navigator.share` is not available or file sharing is not supported (desktop)
**Then** the PDF is downloaded via a programmatic `<a download>` click

### AC 6: AuditDispatcher Component — UX Pattern

**Given** the `AuditDispatcher` component (UX Spec §6.1, §8.1)
**When** on mobile (screen width < 768px or `navigator.share` available)
**Then** the Export button is styled as a primary CTA, full-width at the bottom of the table header area, with label "Share Report"
**When** on desktop
**Then** the Export button is in the table header toolbar with label "Export PDF"
**And** the button shows a spinner during PDF generation (`isGenerating` state)
**And** if PDF generation or share fails, a Crimson Toast notification is shown: `t('notification.watchlist.exportError')`

### AC 7: Tests Pass

**Given** all changes are implemented
**Then** `./gradlew test` passes (all backend tests green)
**And** `npm run test` (in `frontend/`) passes (all Vitest tests green)

---

## Tasks / Subtasks

- [x] Task 1: DB Migration (AC: #1, #2)
  - [x] 1.1 Create `backend/src/main/resources/db/migration/V20260326_002__add_sha256_to_watchlist.sql`:
    ```sql
    ALTER TABLE watchlist_entries ADD COLUMN latest_sha256_hash VARCHAR(64);
    ```
  - [x] 1.2 No jOOQ codegen needed — `NotificationRepository` uses raw DSL (`field()`). But run `./gradlew generateJooq` anyway after migration if codegen is configured for the `watchlist_entries` table (check build.gradle).

- [x] Task 2: Backend — `NotificationRepository` (AC: #1, #2)
  - [x] 2.1 Add `String latestSha256Hash` to the `WatchlistEntryRecord` inner record (at the end of the fields list)
  - [x] 2.2 Update `findByTenantId()`: add `field("latest_sha256_hash", String.class)` to the SELECT and to the `fetch()` constructor call
  - [x] 2.3 Update `findByTenantIdAndTaxNumber()`: same addition as 2.2
  - [x] 2.4 Add new method `updateVerdictStatusWithHash(UUID tenantId, String taxNumber, String verdictStatus, OffsetDateTime checkedAt, String sha256Hash)`:
    ```java
    public int updateVerdictStatusWithHash(UUID tenantId, String taxNumber,
                                           String verdictStatus, OffsetDateTime checkedAt,
                                           String sha256Hash) {
        var update = dsl.update(table("watchlist_entries"))
                .set(field("last_verdict_status", String.class), verdictStatus)
                .set(field("last_checked_at", OffsetDateTime.class), checkedAt)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now());
        if (sha256Hash != null && !sha256Hash.equals("HASH_UNAVAILABLE")) {
            update = update.set(field("latest_sha256_hash", String.class), sha256Hash);
        }
        return update.where(field("tenant_id", UUID.class).eq(tenantId))
                     .and(field("tax_number", String.class).eq(taxNumber))
                     .execute();
    }
    ```
    Note: This method extends the existing `updateVerdictStatus` contract. The original method remains for callers that do not have a hash (backward compat).

- [x] Task 3: Backend — Domain & DTO (AC: #1)
  - [x] 3.1 `WatchlistEntry.java`: Add `String latestSha256Hash` as the last field in the record. Add it to the existing convenience constructor as `null` default:
    ```java
    public WatchlistEntry(UUID id, UUID tenantId, String taxNumber, String companyName,
                          String label, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, tenantId, taxNumber, companyName, label, createdAt, updatedAt, null, null, null);
    }
    ```
  - [x] 3.2 `WatchlistEntryResponse.java`: Add `String latestSha256Hash` as the last field. Update `from()` factory:
    ```java
    public static WatchlistEntryResponse from(WatchlistEntry entry) {
        return new WatchlistEntryResponse(
                entry.id(), entry.taxNumber(), entry.companyName(), entry.label(),
                entry.currentVerdictStatus(), entry.lastCheckedAt(), entry.createdAt(),
                entry.latestSha256Hash());
    }
    ```
    **IMPORTANT**: `WatchlistEntryResponse` already maps from `entry.verdictStatus()` into the field named `currentVerdictStatus`. Check the existing `from()` factory carefully — do NOT rename existing fields.
  - [x] 3.3 Update `NotificationService.java`: wherever it creates `WatchlistEntry` from a `WatchlistEntryRecord`, pass `record.latestSha256Hash()` as the last argument. Check `NotificationService.getWatchlistEntries()` and any other factory sites.

- [x] Task 4: Backend — PartnerStatusChangedListener (AC: #2)
  - [x] 4.1 Replace the `notificationRepository.updateVerdictStatus(...)` call (line ~74) with `notificationRepository.updateVerdictStatusWithHash(entry.tenantId(), entry.taxNumber(), event.newStatus(), event.timestamp(), event.sha256Hash())`
  - [x] 4.2 **Preserve existing behavior**: `event.sha256Hash()` may be null (for WatchlistMonitor-triggered events if hash not available). The new method guards against null/sentinel overwrites — no null-check needed at the call site.

- [x] Task 5: Backend — WatchlistController + T1 requireUuidClaim (AC: #1)
  - [x] 5.1 Check if `hu.riskguard.core.security.JwtClaimUtil` exists (Story 5.0 T1 may have extracted it).
    - If **yes**: Remove the private `requireUuidClaim()` method from `WatchlistController` and use `JwtClaimUtil.requireUuid(jwt, "active_tenant_id")` instead.
    - If **no**: Leave the inline helper in place — do NOT duplicate the extraction work.
  - [x] 5.2 No new endpoints in `WatchlistController` — PDF generation is client-side.

- [x] Task 6: Frontend — Install jsPDF (AC: #4)
  - [x] 6.1 In `frontend/`, run: `npm install jspdf jspdf-autotable`
    - `jspdf` ^2.5.1 — client-side PDF generation, built-in TypeScript types
    - `jspdf-autotable` ^3.8.2 — table plugin for jsPDF, needed for the partner table layout
  - [x] 6.2 Verify install: `import jsPDF from 'jspdf'` compiles without error.
  - [x] 6.3 Note for Nuxt/SSR: jsPDF must be imported with `process.client` guard or inside `onMounted` / composable called from the client. Use dynamic import: `const { jsPDF } = await import('jspdf')`.

- [x] Task 7: Frontend — `useWatchlistPdfExport.ts` composable (AC: #4, #5)
  - [x] 7.1 Create `frontend/app/composables/useWatchlistPdfExport.ts`:
    ```typescript
    import type { WatchlistEntryResponse } from '~/types/api'

    export function useWatchlistPdfExport() {
      const isGenerating = ref(false)

      async function generateAndDispatch(entries: WatchlistEntryResponse[]) {
        isGenerating.value = true
        try {
          const pdf = await buildPdf(entries)
          await dispatch(pdf)
        } finally {
          isGenerating.value = false
        }
      }

      return { isGenerating, generateAndDispatch }
    }
    ```
  - [x] 7.2 `buildPdf(entries)` implementation:
    - Dynamic import: `const { default: jsPDF } = await import('jspdf')`
    - `await import('jspdf-autotable')` (side-effect import, patches jsPDF prototype)
    - Create `doc = new jsPDF({ orientation: 'landscape' })`
    - Header: `doc.text('RiskGuard — Partner Due Diligence Report', 14, 15)` + date
    - Table body: map entries to `[companyName, taxNumber, localizedVerdict, formattedLastChecked, sha256Hash || 'N/A']`
    - Use `(doc as any).autoTable(...)` (jspdf-autotable adds this method dynamically — TypeScript sees it as `any`)
    - Footer text on each page: `doc.text('This report is for informational purposes only...', 14, doc.internal.pageSize.height - 10)`
    - Return `doc.output('blob')` as `Blob`
  - [x] 7.3 `dispatch(blob)` implementation:
    ```typescript
    async function dispatch(blob: Blob) {
      const filename = `riskguard-watchlist-${new Date().toISOString().slice(0, 10)}.pdf`
      const file = new File([blob], filename, { type: 'application/pdf' })
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: 'RiskGuard Due Diligence Report', text: 'Partner status report' })
      } else {
        // Desktop fallback: direct download
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = filename
        a.click()
        URL.revokeObjectURL(url)
      }
    }
    ```
    - `navigator.canShare` check before `navigator.share` to avoid the "NotAllowedError: share must be initiated by user gesture" trap on desktops that have `share` but not file-sharing support.

- [x] Task 8: Frontend — `AuditDispatcher.vue` component (AC: #5, #6)
  - [x] 8.1 Create `frontend/app/components/Watchlist/AuditDispatcher.vue`:
    ```vue
    <script setup lang="ts">
    import Button from 'primevue/button'
    import type { WatchlistEntryResponse } from '~/types/api'

    const props = defineProps<{
      entries: WatchlistEntryResponse[]
      selectedEntries: WatchlistEntryResponse[]
    }>()

    const emit = defineEmits<{ exportError: [] }>()
    const { t } = useI18n()
    const toast = useToast()
    const { isGenerating, generateAndDispatch } = useWatchlistPdfExport()

    const exportTargets = computed(() =>
      props.selectedEntries.length > 0 ? props.selectedEntries : props.entries
    )

    const buttonLabel = computed(() => {
      const isMobile = typeof navigator !== 'undefined' && !!navigator.share
      const label = isMobile ? t('notification.watchlist.export.shareLabel') : t('notification.watchlist.export.downloadLabel')
      return props.selectedEntries.length > 0
        ? `${label} (${props.selectedEntries.length})`
        : `${label} (${t('notification.watchlist.export.all')})`
    })

    async function handleExport() {
      try {
        await generateAndDispatch(exportTargets.value)
      } catch {
        toast.add({
          severity: 'error',
          summary: t('notification.watchlist.exportError'),
          life: 4000,
        })
      }
    }
    </script>

    <template>
      <Button
        :label="buttonLabel"
        icon="pi pi-file-pdf"
        :loading="isGenerating"
        :disabled="entries.length === 0"
        data-testid="export-pdf-button"
        @click="handleExport"
      />
    </template>
    ```
  - [x] 8.2 Mobile-first layout: The button is rendered inside the WatchlistTable header toolbar. On mobile (≤768px, use Tailwind responsive prefix), make it `w-full` with `order-first`. Use Tailwind classes on the wrapping `<div>` in `watchlist/index.vue`, not inside the component itself.

- [x] Task 9: Frontend — `WatchlistTable.vue` selection support (AC: #3)
  - [x] 9.1 Add props and emits:
    ```typescript
    const props = defineProps<{
      entries: WatchlistEntryResponse[]
      isLoading: boolean
      selection: WatchlistEntryResponse[]  // NEW
    }>()
    const emit = defineEmits<{
      remove: [entry: WatchlistEntryResponse]
      'update:selection': [entries: WatchlistEntryResponse[]]  // NEW
    }>()
    ```
  - [x] 9.2 Update `<DataTable>`:
    ```html
    <DataTable
      v-model:selection="internalSelection"
      selection-mode="multiple"
      ...
    >
    ```
    Wire internal selection: `const internalSelection = computed({ get: () => props.selection, set: (v) => emit('update:selection', v) })`
  - [x] 9.3 Add checkbox column as the FIRST column:
    ```html
    <Column selection-mode="multiple" style="width: 3rem" />
    ```
  - [x] 9.4 No `data-key` needed — PrimeVue uses object identity by default.

- [x] Task 10: Frontend — `watchlist/index.vue` wiring (AC: #3, #4, #5, #6)
  - [x] 10.1 Add `selectedEntries` local ref:
    ```typescript
    const selectedEntries = ref<WatchlistEntryResponse[]>([])
    ```
  - [x] 10.2 Update `<WatchlistTable>` template usage:
    ```html
    <WatchlistTable
      v-model:selection="selectedEntries"
      :entries="watchlistStore.entries"
      :is-loading="watchlistStore.isLoading"
      @remove="handleRemove"
    />
    ```
  - [x] 10.3 Add `<AuditDispatcher>` above the table (or in a toolbar div):
    ```html
    <div class="flex justify-between items-center mb-4">
      <h1 class="text-2xl font-bold text-slate-800">{{ t('notification.watchlist.title') }}</h1>
      <div class="flex gap-2 items-center flex-wrap">
        <AuditDispatcher
          :entries="watchlistStore.entries"
          :selected-entries="selectedEntries"
        />
        <Button :label="t('notification.watchlist.addButton')" icon="pi pi-plus" data-testid="add-partner-button" @click="showAddDialog = true" />
      </div>
    </div>
    ```
  - [x] 10.4 Remove the old `<h1>` + `<Button>` header block that was previously a flex row — replace with the new combined header from 10.3.

- [x] Task 11: Frontend i18n (AC: #4, #6)
  - [x] 11.1 `frontend/app/i18n/en/notification.json` — add inside `"watchlist"` object (alphabetically sorted):
    ```json
    "export": {
      "all": "all",
      "downloadLabel": "Export PDF",
      "shareLabel": "Share Report"
    },
    "exportError": "Failed to generate PDF. Please try again."
    ```
  - [x] 11.2 `frontend/app/i18n/hu/notification.json` — mirror with Hungarian translations:
    ```json
    "export": {
      "all": "összes",
      "downloadLabel": "PDF exportálás",
      "shareLabel": "Jelentés megosztása"
    },
    "exportError": "PDF generálása sikertelen. Kérjük, próbálja újra."
    ```
  - [x] 11.3 Run `npm run check-i18n` to verify parity.

- [x] Task 12: Frontend — Types update (AC: #1)
  - [x] 12.1 `frontend/types/epr.ts` — NOT touched by this story.
  - [x] 12.2 `frontend/types/api.d.ts` — AUTO-GENERATED from OpenAPI; do NOT edit manually. After backend changes are built, regenerate: `npm run generate-types`. If regeneration is not part of dev workflow, manually add `latestSha256Hash?: string | null` to `WatchlistEntryResponse` in `types/api.d.ts` as a temporary measure, with a TODO comment.

- [x] Task 13: Backend Tests (AC: #7)
  - [x] 13.1 Check if `WatchlistControllerTest.java` exists in `backend/src/test/java/hu/riskguard/notification/` or `backend/src/test/java/hu/riskguard/`.
    - If **exists**: Add a test that verifies `latestSha256Hash` is serialized in the `GET /api/v1/watchlist` response. Use existing mock patterns.
    - If **not exists**: Create a minimal test class with at least the `latestSha256Hash` assertion.
  - [x] 13.2 Add a test to verify `updateVerdictStatusWithHash` does NOT overwrite `latest_sha256_hash` when `sha256Hash = null`.

- [x] Task 14: Frontend Tests (AC: #3, #5, #7)
  - [x] 14.1 `WatchlistTable.spec.ts` — add test: `'emits update:selection when checkbox is clicked'`
    - Use `renderWithProviders`, click the checkbox column, verify `emitted('update:selection')` is truthy.
  - [x] 14.2 Create `AuditDispatcher.spec.ts`:
    - `'renders Export PDF button with entry count when entries provided'`
    - `'shows spinner when isGenerating is true'`
    - `'button is disabled when entries is empty'`
    - Mock `useWatchlistPdfExport` to prevent actual jsPDF execution in tests.
  - [x] 14.3 Create or update `watchlist/index.spec.ts`:
    - Verify `AuditDispatcher` renders in the page.
    - Verify `selectedEntries` is passed to `WatchlistTable` as `:selection`.

- [x] Task 15: Smoke Check (AC: #7)
  - [x] 15.1 `./gradlew test` — all green (BUILD SUCCESSFUL; OOM fixed with `-Xmx1g` in build.gradle)
  - [x] 15.2 `npm run test` (in `frontend/`) — all green (569/569 passed)

---

## Dev Notes

### SHA-256 Hash: Denormalized Architecture

The `sha256Hash` from `PartnerStatusChanged` event already flows through to `PartnerStatusChangedListener.onPartnerStatusChanged()` via `event.sha256Hash()`. The event is populated by `ScreeningService` (user searches) and `WatchlistMonitor` (24h cycle). The hash is the same value already stored in `search_audit_log.sha256_hash`.

**Why denormalize instead of JOIN**: The `search_audit_log` lives in the `screening` module's data boundary. `NotificationRepository` is scoped to `watchlist_entries` + `notification_outbox` only (see the explicit comment in `NotificationRepository.java`). Cross-module SQL joins are prohibited by the architecture. Denormalization with an event listener is the established pattern here (same as `last_verdict_status` was denormalized in Story 3.7).

**Null semantics**: `latest_sha256_hash` is nullable. Null = "never screened" or "screened before this story's migration". The `updateVerdictStatusWithHash` method must NOT overwrite with null or sentinel `"HASH_UNAVAILABLE"` — only overwrite when a valid 64-char hex hash is present.

### jsPDF in Nuxt (SSR Safety)

jsPDF uses browser globals (`window`, `Blob`, `URL`). In Nuxt, it must NOT be imported at module level. The composable must use dynamic import inside the async function:
```typescript
const { default: jsPDF } = await import('jspdf')
await import('jspdf-autotable')
```
This ensures the import only runs on the client. The `nuxt.config.ts` may need `build.transpile: ['jspdf', 'jspdf-autotable']` if SSR/hybrid rendering causes issues. Check `nuxt.config.ts` for the existing `transpile` list and follow the same pattern used for other browser-only libs.

### jspdf-autotable TypeScript Cast

`jspdf-autotable` adds the `autoTable` method to the jsPDF prototype at runtime but TypeScript doesn't know about it. The correct approach:
```typescript
import type { UserOptions } from 'jspdf-autotable'
// After: (doc as any).autoTable({ ... })
// OR install @types/jspdf-autotable if available
```

### navigator.share File Sharing Check

`navigator.canShare({ files: [file] })` must be called BEFORE `navigator.share` to avoid:
- `TypeError: navigator.share is not a function` on desktop Chrome
- `NotAllowedError` when files are not supported even though `share` exists

Pattern:
```typescript
if (typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
  await navigator.share({ files: [file], ... })
} else {
  // blob URL download fallback
}
```

### WatchlistEntry Record — Canonical Update Pattern

`WatchlistEntry` is an immutable Java record with a compact constructor. Adding `latestSha256Hash` requires updating:
1. The record's canonical constructor (add as last param)
2. The convenience constructor: `this(..., null, null, null)` (existing 2 nulls + new one)
3. `NotificationService.getWatchlistEntries()` — wherever it maps `WatchlistEntryRecord` to `WatchlistEntry`

Search `NotificationService.java` for all `new WatchlistEntry(` or `WatchlistEntry.of(` call sites.

### WatchlistTable Selection — Do NOT use Local Computed for v-model

PrimeVue DataTable's `v-model:selection` requires a mutable binding. Using a computed setter is the correct pattern to bridge parent-owned state with the child DataTable:
```typescript
const internalSelection = computed({
  get: () => props.selection,
  set: (v) => emit('update:selection', v)
})
```
Then `<DataTable v-model:selection="internalSelection">`.

### T1 — requireUuidClaim Extraction

Story 5.0 task list includes T1 (extract `requireUuidClaim` to `core`). If it was implemented:
- `JwtClaimUtil.java` will exist at `backend/src/main/java/hu/riskguard/core/security/JwtClaimUtil.java`
- Import and use `JwtClaimUtil.requireUuid(jwt, "active_tenant_id")` in `WatchlistController`
- Remove the private helper

If Story 5.0 did NOT implement T1 (check `git log --oneline` for Story 5.0 commit), leave the inline helper as-is in `WatchlistController`. Do NOT implement T1 in this story if 5.0 is pending.

### PDF Content Design

| Column | Value | Notes |
|--------|-------|-------|
| Company Name | `entry.companyName` | Fallback: `entry.taxNumber` if null |
| Tax Number | `entry.taxNumber` | — |
| Verdict | localized verdict label | Same labels as `WatchlistTable.verdictLabel()` |
| Last Checked | `entry.lastCheckedAt?.toISOString().slice(0,16)` or `'—'` | ISO datetime, truncated to minutes |
| SHA-256 Hash | `entry.latestSha256Hash` or `'N/A'` | 64-char hex, may overflow column — use `columnStyles.sha256: { cellWidth: 60, overflow: 'ellipsize' }` |

Font: jsPDF default (Helvetica). No custom fonts needed for a functional MVP.

### Project Structure

Backend:
- `backend/src/main/resources/db/migration/V20260326_002__add_sha256_to_watchlist.sql` (new)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java` (modify)
- `backend/src/main/java/hu/riskguard/notification/domain/WatchlistEntry.java` (modify)
- `backend/src/main/java/hu/riskguard/notification/api/dto/WatchlistEntryResponse.java` (modify)
- `backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java` (modify)
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java` (modify — WatchlistEntry factory sites)
- `backend/src/main/java/hu/riskguard/notification/api/WatchlistController.java` (modify if T1 done)

Frontend:
- `frontend/package.json` (new deps: jspdf, jspdf-autotable)
- `frontend/app/composables/useWatchlistPdfExport.ts` (new)
- `frontend/app/components/Watchlist/AuditDispatcher.vue` (new)
- `frontend/app/components/Watchlist/WatchlistTable.vue` (modify — add selection)
- `frontend/app/pages/watchlist/index.vue` (modify — wire selection + AuditDispatcher)
- `frontend/app/i18n/en/notification.json` (modify)
- `frontend/app/i18n/hu/notification.json` (modify)
- `frontend/types/api.d.ts` (modify or regenerate — add `latestSha256Hash`)

Tests:
- `backend/src/test/java/hu/riskguard/notification/WatchlistControllerTest.java` (new or modify)
- `frontend/app/components/Watchlist/WatchlistTable.spec.ts` (modify)
- `frontend/app/components/Watchlist/AuditDispatcher.spec.ts` (new)
- `frontend/app/pages/watchlist/index.spec.ts` (new or modify)

### References

- UX Spec §6.1, §8.1 — AuditDispatcher mobile-first share pattern: `_bmad-output/planning-artifacts/ux-design-specification.md`
- PartnerStatusChanged event: `backend/src/main/java/hu/riskguard/core/events/PartnerStatusChanged.java`
- HashUtil sentinel: `ScreeningRepository.HASH_UNAVAILABLE_SENTINEL = "HASH_UNAVAILABLE"` — guard against this value same as null
- NotificationRepository raw DSL pattern: `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java`
- Existing denormalization pattern: same file, `updateVerdictStatus()` method — `updateVerdictStatusWithHash` follows the same DSL style
- i18n parity check script: `frontend/scripts/i18n-check.js` (run via `npm run check-i18n`)
- Epic 4 Retro T1 action item (requireUuidClaim): `_bmad-output/implementation-artifacts/epic-4-retro-2026-03-26.md`
- jsPDF docs: https://github.com/parallax/jsPDF (latest stable: 2.5.x)
- jspdf-autotable docs: https://github.com/simonbengtsson/jsPDF-AutoTable

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None.

### Completion Notes List

1. **SHA-256 denormalization via event listener**: `latest_sha256_hash` is denormalized onto `watchlist_entries` through the existing `PartnerStatusChangedListener`, following the same pattern as `last_verdict_status`. Cross-module SQL joins are avoided by design.
2. **Null/sentinel guard in `updateVerdictStatusWithHash`**: The method skips overwriting `latest_sha256_hash` when `sha256Hash` is null or equals `"HASH_UNAVAILABLE"`, preserving the last valid hash.
3. **jsPDF SSR safety**: Dynamic import (`await import('jspdf')`) inside the async composable function ensures the browser-only library never runs server-side.
4. **`navigator.canShare` guard**: Checked before `navigator.share` to avoid `NotAllowedError` on desktops that don't support file sharing; falls back to blob URL download.
5. **OOM fix**: Added `jvmArgs '-Xmx1g'` to `tasks.named('test')` in `backend/build.gradle`. Full test suite now runs green without heap exhaustion.
6. **`PartnerStatusChangedListenerTest` migration**: All 9 test verify/never calls updated from `updateVerdictStatus` (4-arg) to `updateVerdictStatusWithHash` (5-arg, nullable sha256Hash).
7. **PrimeVue toast mock pattern**: `watchlist/index.spec.ts` uses `vi.mock('primevue/usetoast', ...)` (module mock) rather than `vi.stubGlobal` — required because the component imports `useToast` directly from the module.
8. ✅ Resolved review finding [Patch]: Added `HASH_UNAVAILABLE_SENTINEL` local constant in `NotificationRepository` (avoids cross-module import from `ScreeningRepository.internal`); also added `!sha256Hash.isBlank()` guard against empty string bypass.
9. ✅ Resolved review finding [Patch]: `selection` prop in `WatchlistTable` now uses `withDefaults` with `() => []` default.
10. ✅ Resolved review finding [Patch]: `insertWatchlistEntryWithHash` helper now includes `company_name` column.
11. ✅ Resolved review finding [Patch]: `AuditDispatcher` button disabled during `isGenerating` to prevent concurrent PDF builds; `isMobile` now includes `window.innerWidth < 768` condition per AC 6.
12. ✅ Resolved review finding [Patch]: `AbortError` from share sheet dismissal is swallowed silently in `dispatch()`; `URL.revokeObjectURL` deferred 10s for Firefox/Safari; null-safe `??` operators for companyName/taxNumber fields in PDF rows.

### File List

**Backend — New:**
- `backend/src/main/resources/db/migration/V20260326_002__add_sha256_to_watchlist.sql`

**Backend — Modified:**
- `backend/build.gradle` (added `-Xmx1g` JVM arg for test task)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java`
- `backend/src/main/java/hu/riskguard/notification/domain/WatchlistEntry.java`
- `backend/src/main/java/hu/riskguard/notification/api/dto/WatchlistEntryResponse.java`
- `backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java`
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java`
- `backend/src/test/java/hu/riskguard/notification/api/WatchlistControllerTest.java`
- `backend/src/test/java/hu/riskguard/notification/NotificationRepositoryIntegrationTest.java`
- `backend/src/test/java/hu/riskguard/notification/domain/PartnerStatusChangedListenerTest.java`

**Frontend — New:**
- `frontend/app/composables/useWatchlistPdfExport.ts`
- `frontend/app/components/Watchlist/AuditDispatcher.vue`
- `frontend/app/components/Watchlist/AuditDispatcher.spec.ts`
- `frontend/app/pages/watchlist/index.spec.ts`

**Frontend — Modified:**
- `frontend/app/components/Watchlist/WatchlistTable.vue`
- `frontend/app/components/Watchlist/WatchlistTable.spec.ts`
- `frontend/app/pages/watchlist/index.vue`
- `frontend/app/i18n/en/notification.json`
- `frontend/app/i18n/hu/notification.json`
- `frontend/types/api.d.ts`

### Review Findings

- [x] [Review][Patch] Sentinel `"HASH_UNAVAILABLE"` is a raw string literal — use `ScreeningRepository.HASH_UNAVAILABLE_SENTINEL` constant instead [NotificationRepository.java]
- [x] [Review][Patch] Empty string `""` bypasses sentinel guard — add `!sha256Hash.isBlank()` to the guard condition [NotificationRepository.java]
- [x] [Review][Patch] `selection` prop in WatchlistTable has no default — add `default: () => []` to prevent runtime Vue warning when prop is omitted [WatchlistTable.vue]
- [x] [Review][Patch] `insertWatchlistEntryWithHash` test helper omits `company_name` column — if NOT NULL, integration test will fail on schema validation [NotificationRepositoryIntegrationTest.java]
- [x] [Review][Patch] Export button not disabled while `isGenerating` — `:disabled` only guards `entries.length === 0`; rapid double-click spawns two concurrent PDF builds; fix: `:disabled="isGenerating || entries.length === 0"` [AuditDispatcher.vue]
- [x] [Review][Patch] User-cancel of share sheet (`AbortError`) shows misleading "Failed to generate PDF" error toast — catch `DOMException` with `name === 'AbortError'` in `dispatch()` and swallow silently [useWatchlistPdfExport.ts]
- [x] [Review][Patch] `e.companyName || e.taxNumber` produces `"undefined"` in PDF if both fields are null — use `e.companyName ?? e.taxNumber ?? '—'`; also guard `e.taxNumber` column with `?? '—'` [useWatchlistPdfExport.ts]
- [x] [Review][Patch] `URL.revokeObjectURL` called synchronously after `a.click()` — on Firefox/Safari the download may not have initiated yet; defer revocation with `setTimeout(() => URL.revokeObjectURL(url), 10000)` [useWatchlistPdfExport.ts]
- [x] [Review][Patch] `isMobile` computed checks only `navigator.share`, missing AC 6's screen-width condition — AC 6 specifies `width < 768px OR navigator.share`; desktop Chrome 89+ exposes `navigator.share` and would show "Share Report" label incorrectly; fix: `window.innerWidth < 768 || !!navigator.share` [AuditDispatcher.vue]

- [x] [Review][Defer] No index on `latest_sha256_hash` column [V20260326_002__add_sha256_to_watchlist.sql] — deferred; column is never used in WHERE/JOIN queries; add partial index before any hash-based lookup feature
- [x] [Review][Defer] `latestSha256Hash?: string | null` optional type may persist beyond its TODO — deferred; acknowledged with TODO comment; resolve when OpenAPI pipeline is wired
- [x] [Review][Defer] `-Xmx1g` hardcoded in build.gradle without CI property override — deferred; advisory; low risk for current single-runner setup
- [x] [Review][Defer] `updateVerdictStatusWithHash` returns 0 rows when entry was deleted mid-flight without logging — deferred; pre-existing pattern; add warning log before Epic 6 monitoring hardening
- [x] [Review][Defer] Stale selection after watchlist refresh (object identity without `dataKey`) — deferred; PrimeVue DataTable design limitation; mitigated by store refresh clearing entries
- [x] [Review][Defer] Count suffix appended to "Share Report" label (minor deviation from AC 3 wording) — deferred; spec only specifies "Export PDF (3)" label; share label with count is acceptable UX

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-26 | 1.0 | Initial implementation: SHA-256 denormalization, jsPDF export composable, AuditDispatcher, WatchlistTable multi-select, OOM heap fix | claude-sonnet-4-6 |
| 2026-03-26 | 1.1 | Addressed 9 code review findings: HASH_UNAVAILABLE_SENTINEL constant, isBlank guard, withDefaults for selection prop, company_name in test helper, isGenerating disables export button, AbortError swallow, null-safe ?? operators in PDF rows, setTimeout revoke, isMobile screen-width condition | claude-sonnet-4-6 |
