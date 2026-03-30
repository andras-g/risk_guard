# Story 6.2: Partner Detail Slide-Over Drawer

Status: ready-for-dev

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

- [ ] T1 — Create `WatchlistPartnerDrawer.vue` component (AC: 2, 3, 4)
  - [ ] File: `frontend/app/components/watchlist/WatchlistPartnerDrawer.vue`
  - [ ] Props: `entry: WatchlistEntryResponse | null`, `visible: boolean`
  - [ ] Emits: `update:visible`, `remove`
  - [ ] Use PrimeVue `Drawer` (or `Sidebar`) component with `position="right"`, `style="width: 480px"`
  - [ ] Identity section: company name, tax number (`font-mono`), status `Tag` badge, "Watchlist since" date, "Last screened" relative time
  - [ ] Action buttons: [View Full Screening →] (NuxtLink to `/screening/[taxNumber]`), [Export Audit PDF] (call existing per-entry export), [View Audit History] (NuxtLink to `/audit-history?taxNumber=[taxNumber]`), [Remove] (emit `remove` event)
  - [ ] Close on Escape and overlay click (PrimeVue Drawer handles this natively)

- [ ] T2 — Update `WatchlistTable.vue` to emit row-click (AC: 1, 5)
  - [ ] File: `frontend/app/components/watchlist/WatchlistTable.vue`
  - [ ] Add emit: `row-select(entry: WatchlistEntryResponse)`
  - [ ] In PrimeVue DataTable, add `@row-click` handler: if `$event.originalEvent.target` is NOT the checkbox or remove button, emit `row-select`
  - [ ] Ensure checkbox `@click.stop` prevents row-click from firing on checkbox click

- [ ] T3 — Wire drawer into `watchlist/index.vue` (AC: 1, 4, 5, 6)
  - [ ] Add `selectedPartner: WatchlistEntryResponse | null = null` and `drawerVisible = false` refs
  - [ ] Handle `@row-select` from `WatchlistTable`: set `selectedPartner` and `drawerVisible = true` (guard: skip if `isLoading`)
  - [ ] Pass `selectedPartner` and `drawerVisible` to `WatchlistPartnerDrawer`
  - [ ] Handle `@remove` from drawer: call existing `handleRemove(selectedPartner)` then close drawer

- [ ] T4 — i18n keys (AC: 2, 3)
  - [ ] Add to `en/` and `hu/` JSON: `notification.watchlist.drawerTitle`, `notification.watchlist.watchlistSince`, `notification.watchlist.lastScreened`, `notification.watchlist.neverScreened`, `notification.watchlist.viewScreening`, `notification.watchlist.viewAuditHistory`
  - [ ] Keep files alphabetically sorted

- [ ] T5 — Spec (AC: 1–5)
  - [ ] Update or create `watchlist.spec.ts` co-located with `watchlist/index.vue`
  - [ ] Assert: row click opens drawer with correct entry data; checkbox click does NOT open drawer; Escape closes drawer; remove button triggers confirmation

## Dev Notes

### No backend changes
All drawer data comes from `WatchlistEntryResponse` already in the store. Do not add new API endpoints.

### PrimeVue Drawer / Sidebar
PrimeVue 4 uses `Drawer` (renamed from `Sidebar` in v4). Check current PrimeVue version and use the correct component name. Props: `v-model:visible`, `position="right"`, `style="width:480px"`.

### Distinguishing row click from checkbox click
PrimeVue DataTable's `@row-click` fires even when clicking the checkbox cell. Guard:
```ts
function onRowClick(event: DataTableRowClickEvent) {
  const target = event.originalEvent.target as HTMLElement
  if (target.closest('.p-checkbox') || target.closest('[data-testid="remove-btn"]')) return
  emit('row-select', event.data)
}
```

### Audit History navigation with pre-filter
Route to `/audit-history?taxNumber=12345678-2-41`. The existing `audit-history/index.vue` already reads query params for filtering (Story 5.1a). Verify the query param name matches (`taxNumber`).

### Single-entry PDF export
`AuditDispatcher` currently receives the full `entries` array and `selectedEntries` for bulk export. For the drawer's single-entry export, pass `[entry]` as both `entries` and `selectedEntries` props — or trigger the existing store action directly. Prefer the store action to avoid prop misuse.

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/components/watchlist/WatchlistPartnerDrawer.vue` | New |
| `frontend/app/components/watchlist/WatchlistTable.vue` | Add `row-click` emit |
| `frontend/app/pages/watchlist/index.vue` | Wire drawer |
| `frontend/app/i18n/en/screening.json` (or relevant namespace) | New keys |
| `frontend/app/i18n/hu/screening.json` | New keys |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
