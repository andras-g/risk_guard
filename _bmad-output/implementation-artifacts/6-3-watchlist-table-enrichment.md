# Story 6.3: Watchlist Table Enrichment

Status: ready-for-dev

## Story

As a User,
I want my watchlist table to show when each partner was last screened and whether their risk status has improved, worsened, or stayed the same since the previous check —
So that I can spot deteriorating partners at a glance without having to open each one individually.

## Acceptance Criteria

1. **"Last Screened" column**
   - Given the watchlist table renders
   - Then a "Last Screened" column is visible showing `lastCheckedAt` as a relative time (e.g. "2h ago", "Yesterday", "3 days ago") via `useDateRelative`
   - And if `lastCheckedAt` is null, the cell shows "—"
   - Note: `lastCheckedAt` already exists in `WatchlistEntryResponse` — no backend change needed for this column

2. **"Trend" column**
   - Given a watchlist entry with both `currentVerdictStatus` and `previousVerdictStatus` populated
   - Then the Trend column shows:
     - ↑ (green, `pi-arrow-up`) — status improved (e.g. AT_RISK → RELIABLE)
     - ↓ (red, `pi-arrow-down`) — status worsened (e.g. RELIABLE → AT_RISK)
     - → (grey, `pi-minus`) — status unchanged
   - And if `previousVerdictStatus` is null (first check or no previous data), the cell shows "—"

3. **Status severity ordering for trend**
   - "Improved" means: new status has lower severity than previous
   - "Worsened" means: new status has higher severity than previous
   - Severity order (lowest to highest): `RELIABLE` < `INCOMPLETE` < `UNAVAILABLE` < `TAX_SUSPENDED` < `AT_RISK`

4. **Backend: store previous verdict on monitoring update**
   - Given the nightly `WatchlistMonitor` updates a partner's verdict
   - When the new verdict differs from the current one
   - Then `previous_verdict_status` in `watchlist_entries` is set to the old `current_verdict_status` before updating
   - And `previousVerdictStatus` is returned in `WatchlistEntryResponse`

5. **DB migration**
   - Given the Flyway migration runs
   - Then `watchlist_entries` gains column `previous_verdict_status VARCHAR(30) NULL DEFAULT NULL`
   - And all existing rows have `previous_verdict_status = NULL` (backfill not required)

6. **Existing behaviour preserved**
   - All existing columns (Company, Tax Number, Status, Actions) are unchanged
   - Bulk selection, PDF export, add/remove partner flows are unaffected
   - Story 6.2 drawer still works (row-click behaviour unchanged)

## Tasks / Subtasks

- [ ] T1 — DB migration (AC: 5)
  - [ ] Create `V20260331_001__add_previous_verdict_status_to_watchlist.sql` (use next available date/sequence)
  - [ ] `ALTER TABLE watchlist_entries ADD COLUMN previous_verdict_status VARCHAR(30) NULL DEFAULT NULL`

- [ ] T2 — Backend: `WatchlistMonitor` stores previous verdict (AC: 4)
  - [ ] In `hu.riskguard.notification.domain.WatchlistMonitor` (or equivalent monitoring service), before updating `current_verdict_status`:
    - Read current `current_verdict_status`
    - Write it to `previous_verdict_status` in the same UPDATE statement
  - [ ] Update `NotificationRepository` (jOOQ): add `previous_verdict_status` to the UPDATE DSL
  - [ ] Only update `previous_verdict_status` when `current_verdict_status` actually changes (no-op if same)

- [ ] T3 — Backend: expose `previousVerdictStatus` in DTO (AC: 4)
  - [ ] Add `previousVerdictStatus` field to `WatchlistEntryResponse.java` record (nullable String)
  - [ ] Update `WatchlistEntryResponse.from(domain)` factory method to map the new field
  - [ ] Update jOOQ query in `NotificationRepository` to SELECT `previous_verdict_status`
  - [ ] Run `tsc --noEmit` after OpenAPI pipeline regenerates `api.d.ts` to confirm type is available; until CI regenerates, add manual TODO comment to `api.d.ts` following existing pattern

- [ ] T4 — Frontend: add "Last Screened" and "Trend" columns to `WatchlistTable.vue` (AC: 1, 2, 3, 6)
  - [ ] Add PrimeVue `Column` for "Last Screened": `field="lastCheckedAt"`, body slot using `useDateRelative`, show "—" if null
  - [ ] Add PrimeVue `Column` for "Trend": computed from `currentVerdictStatus` vs `previousVerdictStatus` using severity map; render `<i>` icon with appropriate color class
  - [ ] Severity map (constant in composable or component): `{ RELIABLE: 0, INCOMPLETE: 1, UNAVAILABLE: 2, TAX_SUSPENDED: 3, AT_RISK: 4 }`

- [ ] T5 — i18n keys (AC: 1, 2)
  - [ ] Add to `en/` and `hu/`: `notification.watchlist.columnLastScreened`, `notification.watchlist.columnTrend`, `notification.watchlist.trendImproved`, `notification.watchlist.trendWorsened`, `notification.watchlist.trendStable`
  - [ ] Keep files alphabetically sorted

- [ ] T6 — Tests (AC: 1–6)
  - [ ] Backend: update `WatchlistMonitorTest` (or integration test) to assert `previous_verdict_status` is written correctly on status change; assert it is NOT written when status is unchanged
  - [ ] Frontend: update watchlist spec to assert "Last Screened" and "Trend" columns render correctly for sample data

## Dev Notes

### `lastCheckedAt` already in response
`WatchlistEntryResponse` already has `lastCheckedAt: string | null` (Story 3.7/3.10 implementation). T4 "Last Screened" column needs no backend change.

### `previousVerdictStatus` — update-only, no backfill
New entries will have `previous_verdict_status = NULL` until the first monitoring cycle that causes a status change. The trend arrow shows "—" for null. This is correct behaviour.

### WatchlistMonitor update pattern
The monitoring service updates `current_verdict_status` in `watchlist_entries`. Extend the existing UPDATE to also write `previous_verdict_status`:
```sql
UPDATE watchlist_entries
SET previous_verdict_status = current_verdict_status,  -- capture old before overwrite
    current_verdict_status = :newStatus,
    last_checked_at = NOW()
WHERE id = :id AND current_verdict_status != :newStatus  -- only on actual change
```
If the monitoring service uses jOOQ DSL, extend the `.set()` chain accordingly.

### Key files to touch
| File | Change |
|------|--------|
| `V20260331_001__...sql` | New migration |
| `hu.riskguard.notification.domain.WatchlistMonitor` (or service) | Store previous verdict |
| `hu.riskguard.notification.internal.NotificationRepository` | Extend UPDATE + SELECT |
| `hu.riskguard.notification.api.dto.WatchlistEntryResponse` | Add `previousVerdictStatus` |
| `frontend/app/components/watchlist/WatchlistTable.vue` | Add 2 columns |
| `frontend/types/api.d.ts` | Manual TODO until CI regenerates |
| i18n files | New keys |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
