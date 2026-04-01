# Story 7.2: Partner Detail Slide-Over Drawer

Status: done

## Story

As a User,
I want to click on a partner row in my watchlist to see their full details in a side panel — without leaving the watchlist page —
So that I can quickly check a partner's status, last check time, and take actions (view screening, export PDF, audit history, remove) without navigating away.

## Acceptance Criteria

1. **Drawer opens on row click**
   - Given the watchlist table is rendered
   - When the user clicks anywhere on a partner row (excluding the checkbox and the remove icon)
   - Then a right-side slide-over drawer opens (~480px wide) showing that partner's details
   - And the watchlist table behind the drawer is dimmed (semi-transparent overlay)

2. **Drawer content — cached data only**
   - The drawer displays data already present in the `WatchlistEntryResponse`:
     - Company name (bold, large)
     - Tax number in monospace font (`font-mono`)
     - Current verdict status badge (color-coded, using existing `useStatusColor`)
     - "Watchlist since" date (formatted from `createdAt`)
     - "Last screened" relative time (from `lastCheckedAt`; shows "Never" if null)
   - No additional API calls are made when the drawer opens

3. **Drawer action buttons**
   - [View Full Screening →] — navigates to `/screening/[taxNumber]` (closes drawer); primary navy button
   - [Export Audit PDF] — triggers single-entry PDF export using existing `AuditDispatcher` logic for that entry; outlined button
   - [View Audit History] — navigates to `/audit-history` pre-filtered by `taxNumber` (passes query param); outlined button
   - [Remove from Watchlist] — red text link; triggers the existing `handleRemove` confirmation flow

4. **Drawer close**
   - The drawer closes when: X button clicked, Escape key pressed, or overlay background clicked
   - Closing the drawer does not affect multi-row checkbox selection state

5. **Checkbox selection unaffected**
   - Clicking a row's checkbox still toggles selection (for bulk PDF export) without opening the drawer
   - The bulk PDF export button (existing `AuditDispatcher`) continues to work on `selectedEntries`

6. **Loading / empty guard**
   - If the watchlist is still loading (`watchlistStore.isLoading`), row clicks are ignored (drawer does not open)

## Tasks / Subtasks

- [x] T1 — Create `WatchlistPartnerDrawer.vue` component (AC: 2, 3, 4)
  - [x] File: `frontend/app/components/watchlist/WatchlistPartnerDrawer.vue`
  - [x] Props: `entry: WatchlistEntryResponse | null`, `visible: boolean`
  - [x] Emits: `update:visible`, `remove`
  - [x] Use PrimeVue `Drawer` (or `Sidebar`) component with `position="right"`, `style="width: 480px"`
  - [x] Identity section: company name, tax number (`font-mono`), status `Tag` badge, "Watchlist since" date, "Last screened" relative time
  - [x] Action buttons: [View Full Screening →] (NuxtLink to `/screening/[taxNumber]`), [Export Audit PDF] (call existing per-entry export), [View Audit History] (NuxtLink to `/audit-history?taxNumber=[taxNumber]`), [Remove] (emit `remove` event)
  - [x] Close on Escape and overlay click (PrimeVue Drawer handles this natively)

- [x] T2 — Update `WatchlistTable.vue` to emit row-click (AC: 1, 5)
  - [x] File: `frontend/app/components/watchlist/WatchlistTable.vue`
  - [x] Add emit: `row-select(entry: WatchlistEntryResponse)`
  - [x] In PrimeVue DataTable, add `@row-click` handler: if `$event.originalEvent.target` is NOT the checkbox or remove button, emit `row-select`
  - [x] Ensure checkbox `@click.stop` prevents row-click from firing on checkbox click

- [x] T3 — Wire drawer into `watchlist/index.vue` (AC: 1, 4, 5, 6)
  - [x] Add `selectedPartner: WatchlistEntryResponse | null = null` and `drawerVisible = false` refs
  - [x] Handle `@row-select` from `WatchlistTable`: set `selectedPartner` and `drawerVisible = true` (guard: skip if `isLoading`)
  - [x] Pass `selectedPartner` and `drawerVisible` to `WatchlistPartnerDrawer`
  - [x] Handle `@remove` from drawer: call existing `handleRemove(selectedPartner)` then close drawer

- [x] T4 — i18n keys (AC: 2, 3)
  - [x] Add to `en/` and `hu/` JSON: `notification.watchlist.drawerTitle`, `notification.watchlist.watchlistSince`, `notification.watchlist.lastScreened`, `notification.watchlist.neverScreened`, `notification.watchlist.viewScreening`, `notification.watchlist.viewAuditHistory`
  - [x] Keep files alphabetically sorted

- [x] T5 — Spec (AC: 1–5)
  - [x] Update or create `watchlist.spec.ts` co-located with `watchlist/index.vue`
  - [x] Assert: row click opens drawer with correct entry data; checkbox click does NOT open drawer; Escape closes drawer; remove button triggers confirmation

## Dev Notes

### No backend changes
All drawer data comes from `WatchlistEntryResponse` already in the store. Do not add new API endpoints.

### WatchlistEntryResponse exact fields
```ts
interface WatchlistEntryResponse {
  id: string
  taxNumber: string
  companyName: string | null
  label: string | null                       // ← user-assigned label, may display in drawer header
  currentVerdictStatus: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE' | null
  lastCheckedAt: string | null
  createdAt: string
  latestSha256Hash?: string | null
}
```
Status field is **`currentVerdictStatus`** (not `verdictStatus`). [Source: frontend/types/api.d.ts]

### PrimeVue Drawer — confirmed v4.5.4
PrimeVue 4 uses `Drawer` (auto-imported via `@primevue/nuxt-module`). Confirmed in `package.json`. Reference implementation: `frontend/app/components/Common/AppMobileDrawer.vue`.
```vue
<Drawer
  v-model:visible="drawerVisible"
  position="right"
  style="width: 480px"
  data-testid="partner-drawer"
  @hide="onDrawerHide()"
>
  <template #header>...</template>
  <!-- content -->
</Drawer>
```
- Use `v-model:visible` (NOT `:visible` + `@update:visible`)
- `@hide` fires on close (Escape, overlay click, X button) — use it to reset `selectedPartner`
- Overlay dim and Escape close are handled natively by PrimeVue Drawer

### Distinguishing row click from checkbox click
PrimeVue DataTable's `@row-click` fires even when clicking the checkbox cell. Guard:
```ts
function onRowClick(event: DataTableRowClickEvent) {
  const target = event.originalEvent.target as HTMLElement
  if (target.closest('.p-checkbox') || target.closest('[data-testid="remove-btn"]')) return
  emit('row-select', event.data)
}
```

### handleRemove — existing signature
```ts
function handleRemove(entry: WatchlistEntryResponse) {
  confirm.require({   // useConfirm() from PrimeVue
    message: t('notification.watchlist.confirmRemove', { companyName: entry.companyName || entry.taxNumber }),
    header: t('notification.watchlist.removeButton'),
    acceptClass: 'p-button-danger',
    accept: async () => { await watchlistStore.removeEntry(entry.id) /* + toast */ },
  })
}
```
Call `handleRemove(selectedPartner.value)` from `watchlist/index.vue` when drawer emits `remove`. The confirmation dialog is managed by `ConfirmDialog` (already mounted globally). [Source: frontend/app/pages/watchlist/index.vue]

### Audit History navigation with pre-filter
Route to `/audit-history?taxNumber=12345678-2-41`. Confirmed: `useAuditHistory.ts` reads `route.query.taxNumber` to pre-populate the filter. Query param name is exactly `taxNumber`. [Source: frontend/app/composables/useAuditHistory.ts:50]

### Single-entry PDF export — AuditDispatcher props
```vue
<AuditDispatcher
  :entries="watchlistStore.entries"
  :selected-entries="selectedEntries"
/>
```
For single-entry drawer export: pass `[selectedPartner]` as both `entries` and `selectedEntries`. This is the documented pattern. Do NOT call store actions directly — use the component. [Source: frontend/app/components/Watchlist/AuditDispatcher.vue]

### i18n — CRITICAL: use `notification.json`, NOT `screening.json`
The watchlist i18n lives in **`notification.json`** under `notification.watchlist.*` namespace:
- `frontend/app/i18n/en/notification.json`
- `frontend/app/i18n/hu/notification.json`

`notification.json` already exists and is already registered in `nuxt.config.ts` — **no registration change needed**. Add new keys under the existing `watchlist` object. Keep keys alphabetically ordered.

Suggested new keys:
```json
{
  "notification": {
    "watchlist": {
      "drawerTitle": "Partner Details",
      "lastScreened": "Last screened",
      "neverScreened": "Never",
      "viewAuditHistory": "View Audit History",
      "viewScreening": "View Full Screening",
      "watchlistSince": "Watchlist since"
    }
  }
}
```

### useStatusColor for drawer badge
`useStatusColor` composable handles `RELIABLE`, `AT_RISK`, `INCOMPLETE` well. `TAX_SUSPENDED` and `UNAVAILABLE` fall to slate default. For the drawer's single status badge, use PrimeVue `Tag` with a local severity map (same pattern as `DashboardNeedsAttention` in Story 7.1):
- `AT_RISK` / `TAX_SUSPENDED` → `severity="danger"`
- `INCOMPLETE` → `severity="warn"`
- `UNAVAILABLE` → `severity="secondary"`
- `RELIABLE` → `severity="success"`

### Previous story intelligence (Story 7.1 — completed 2026-04-01)
- **`storeToRefs` fails in tests** when store mocks return plain objects with null reactive values. Use `computed(() => store.property)` wrappers instead — simpler and more test-friendly.
- **Nuxt auto-imports are NOT available in spec files.** Composables like `useStatusColor`, `useDateRelative` must be stubbed via `vi.stubGlobal('useStatusColor', mockFn)` before component imports.
- **`$fetch` mocking:** use `vi.stubGlobal('$fetch', vi.fn())` in spec files.
- **i18n in tests:** use `global.plugins = [i18n]` or mock `t()` — follow pattern in `audit-search.spec.ts`.
- **`useIdentityStore` is unreliable** in the HttpOnly-cookie auth flow (`user` always null). Use `useAuthStore().isAccountant` for role checks instead.

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/components/watchlist/WatchlistPartnerDrawer.vue` | New |
| `frontend/app/components/watchlist/WatchlistTable.vue` | Add `row-click` emit + `onRowClick` guard |
| `frontend/app/pages/watchlist/index.vue` | Wire drawer (selectedPartner, drawerVisible, @remove) |
| `frontend/app/i18n/en/notification.json` | New keys under `notification.watchlist.*` |
| `frontend/app/i18n/hu/notification.json` | Hungarian translations for same keys |

### References
- [Source: frontend/app/components/Common/AppMobileDrawer.vue] — Drawer usage pattern
- [Source: frontend/app/components/Watchlist/WatchlistTable.vue] — existing emits (`remove`, `update:selection`)
- [Source: frontend/app/pages/watchlist/index.vue] — handleRemove, AuditDispatcher wiring
- [Source: frontend/app/components/Watchlist/AuditDispatcher.vue] — props: `entries`, `selectedEntries`
- [Source: frontend/types/api.d.ts] — WatchlistEntryResponse fields
- [Source: frontend/app/i18n/en/notification.json] — existing watchlist i18n keys
- [Source: frontend/app/composables/useAuditHistory.ts:50] — `taxNumber` query param confirmed
- [Source: Story 7.1 Dev Agent Record] — storeToRefs test failure, auto-import stubbing, $fetch mock

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References
- Fixed `vi.mock` inside test body causing hoisting issue with `confirmRequireMock`. Moved to module-level constant.
- ✅ Resolved review finding [Patch] P1: Added optional `onAccept` callback to `handleRemove`; `handleDrawerRemove` passes `() => { drawerVisible.value = false }` — drawer now closes only after user confirms removal.
- ✅ Resolved review finding [Patch] P2: Replaced `new Date(createdAt).toLocaleDateString()` with `useDateShort().formatShort(createdAt)` — uses active i18n locale via `Intl.DateTimeFormat`.

### Completion Notes List
- Created `WatchlistPartnerDrawer.vue` with PrimeVue Drawer (position=right, 480px), identity section, Tag severity badge (same map as DashboardNeedsAttention), AuditDispatcher for single-entry PDF export, navigateTo() for screening link, NuxtLink for audit history.
- Updated `WatchlistTable.vue`: added `DataTableRowClickEvent` import, `row-select` emit, `onRowClick` guard (skips .p-checkbox and [data-testid="remove-entry-button"] clicks), `@row-click` on DataTable.
- Wired `watchlist/index.vue`: `selectedPartner`/`drawerVisible` refs, `handleRowSelect` (isLoading guard), `handleDrawerRemove`, `@row-select` on WatchlistTable, `WatchlistPartnerDrawer` in template.
- Added 6 i18n keys to `en/notification.json` and `hu/notification.json` (alphabetically sorted): drawerTitle, lastScreened, neverScreened, viewAuditHistory, viewScreening, watchlistSince.
- Updated `index.spec.ts`: 14 tests (5 new drawer tests), all pass. Full suite 642/642 green.

### File List
- `frontend/app/components/watchlist/WatchlistPartnerDrawer.vue` (new)
- `frontend/app/components/Watchlist/WatchlistTable.vue` (modified)
- `frontend/app/pages/watchlist/index.vue` (modified)
- `frontend/app/pages/watchlist/index.spec.ts` (modified)
- `frontend/app/i18n/en/notification.json` (modified)
- `frontend/app/i18n/hu/notification.json` (modified)

### Review Findings

- [x] [Review][Patch] P1: `handleDrawerRemove` closes drawer before confirm dialog resolves [frontend/app/pages/watchlist/index.vue]
- [x] [Review][Patch] P2: `watchlistSince` uses browser locale (`toLocaleDateString()`) instead of active i18n locale [frontend/app/components/watchlist/WatchlistPartnerDrawer.vue]
- [x] [Review][Defer] D1: `taxNumber` not encoded in URL paths/query strings (Hungarian tax numbers are numeric+hyphen, safe in practice) [WatchlistPartnerDrawer.vue] — deferred, pre-existing
- [x] [Review][Defer] D2: `watchlistSince` shows "Invalid Date" for malformed `createdAt` — `createdAt` is non-nullable and comes from controlled backend [WatchlistPartnerDrawer.vue] — deferred, pre-existing
- [x] [Review][Defer] D3: `onRowClick` guard is a static allow-list — future interactive cells will fall through [WatchlistTable.vue] — deferred, pre-existing
- [x] [Review][Defer] D4: `STATUS_SEVERITY` map duplicated from `DashboardNeedsAttention` — divergence risk on new statuses [WatchlistPartnerDrawer.vue] — deferred, pre-existing
- [x] [Review][Defer] D5: `AuditDispatcher` inline `[entry]` arrays create new references on every render [WatchlistPartnerDrawer.vue] — deferred, pre-existing
- [x] [Review][Defer] D6: No drawer close after PDF export in `AuditDispatcher` — spec doesn't require it; UX stays on watchlist — deferred, pre-existing
- [x] [Review][Defer] D7: Row lacks `cursor: pointer` visual affordance — UX polish — deferred, pre-existing
- [x] [Review][Defer] D8: `selectedPartner` not cleared when drawer closes via X/Escape/overlay — stale ref never shown to user (drawer hidden + overwritten on next row click) [index.vue] — deferred, pre-existing

### Review Findings R2 (2026-04-01)

- [x] [Review][Patch] P3: `onAccept` called before `await removeEntry` — drawer closes even on removal failure [frontend/app/pages/watchlist/index.vue] — fixed: moved `onAccept?.()` into success path after `await removeEntry`
- [x] [Review][Patch] P4: `selectedPartner` never cleared on native drawer close (X/Escape/overlay) — stale ref if drawer reopened after failed remove [WatchlistPartnerDrawer.vue, index.vue] — fixed: `onDrawerHide` now emits `hide`; parent clears `selectedPartner` via `@hide` handler; double-emit of `update:visible` eliminated

## Change Log

- 2026-03-31: Story created (moved from Epic 6 planning). Status → ready-for-dev.
- 2026-04-01: Story enriched with code analysis: Drawer usage pattern (AppMobileDrawer reference), WatchlistEntryResponse exact fields (label added), i18n namespace corrected to notification.json (not screening.json), handleRemove signature, AuditDispatcher single-entry pattern, storeToRefs test failure workaround, auto-import stubbing from Story 7.1 learnings.
- 2026-04-01: Implementation complete. All 5 tasks done. 642 frontend tests pass. Status → review.
- 2026-04-01: Code review R1 — 2 patch items, 8 deferred, 7 dismissed. Status → in-progress.
- 2026-04-01: Review follow-ups resolved — P1 (drawer closes after confirm accept, not immediately), P2 (watchlistSince uses useDateShort/i18n locale). 642 frontend tests green. Status → review.
- 2026-04-01: Code review R2 — 2 patch items resolved (P3: onAccept moved to success path; P4: hide emit added to WatchlistPartnerDrawer, selectedPartner cleared on @hide). 643 frontend tests + 5 E2E green. Status → done.
