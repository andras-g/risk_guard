# Story 7.3: Watchlist Table Enrichment

Status: done

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
   - Given the nightly `WatchlistMonitor` updates a partner's verdict (via `PartnerStatusChangedListener`)
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
   - Story 7.2 drawer still works (row-click behaviour unchanged)

## Senior Developer Review (AI)

**Review Outcome:** Changes Requested
**Review Date:** 2026-04-01
**Reviewer:** claude-sonnet-4-6 (code-review pass)

### Action Items

- [x] **[Med] P1:** `WatchlistControllerTest` missing test for `previousVerdictStatus` in response. `latestSha256Hash` has a dedicated test but `previousVerdictStatus` does not — adds `listEntriesShouldIncludePreviousVerdictStatusInResponse()`. (File: `WatchlistControllerTest.java`)
- [~] **[Low] P2:** Template edge: if `currentVerdictStatus` is null while `previousVerdictStatus` is non-null, `v-else` renders `pi-minus` (stable) instead of `—`. Unreachable in practice (a partner can't have a previous verdict without a current one). **Deferred.**
- [~] **[Low] P3:** `trendDirection()` called twice per row in v-else-if chain (for `improved` and `worsened` checks). Minor redundancy; no functional impact. **Deferred.**

## Tasks / Subtasks

- [x] T1 — DB migration (AC: 5)
  - [x] Create `backend/src/main/resources/db/migration/V20260401_001__add_previous_verdict_status_to_watchlist.sql`
  - [x] Content: `ALTER TABLE watchlist_entries ADD COLUMN previous_verdict_status VARCHAR(30) NULL DEFAULT NULL;`
  - [x] **IMPORTANT:** `V20260331_001__create_adapter_health_tables.sql` is already taken — the next available date prefix is `V20260401_001__`

- [x] T2 — Backend: capture previous verdict in `NotificationRepository.updateVerdictStatusWithHash()` (AC: 4)
  - [x] **CRITICAL: Do NOT modify `WatchlistMonitor.java`** — the monitor publishes a `PartnerStatusChanged` event; the actual DB write happens in `PartnerStatusChangedListener` → `NotificationRepository.updateVerdictStatusWithHash()`
  - [x] In `NotificationRepository.updateVerdictStatusWithHash()`, add a self-referential SET clause BEFORE overwriting `last_verdict_status`:
    ```java
    var update = dsl.update(table("watchlist_entries"))
            .set(field("previous_verdict_status", String.class), field("last_verdict_status", String.class))  // capture OLD before overwrite
            .set(field("last_verdict_status", String.class), verdictStatus)
            .set(field("last_checked_at", OffsetDateTime.class), checkedAt)
            .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now());
    ```
  - [x] PostgreSQL evaluates the right-hand side of all SET clauses from the pre-UPDATE row, so `field("last_verdict_status")` on the right still holds the old value — this is atomic and safe
  - [x] **Do NOT touch `updateVerdictStatus()`** (used by WatchlistMonitor for the "no change" path — previous verdict must not be overwritten on no-change updates)

- [x] T3 — Backend: propagate `previousVerdictStatus` through domain layer (AC: 4)
  - [x] **`NotificationRepository.WatchlistEntryRecord`** (internal record): add `String previousVerdictStatus` field
  - [x] **`NotificationRepository.findByTenantId()`**: add `field("previous_verdict_status", String.class)` to the SELECT and mapper
  - [x] **`NotificationRepository.findByTenantIdAndTaxNumber()`**: same — add field to SELECT and mapper
  - [x] **`WatchlistEntry.java`** (`hu.riskguard.notification.domain`): add `String previousVerdictStatus` parameter to the record; update the convenience constructor to pass `null` for it
  - [x] **`NotificationService.toDomain(WatchlistEntryRecord rec)`** (line ~531, `private static WatchlistEntry toDomain(...)`): add `rec.previousVerdictStatus()` to the `new WatchlistEntry(...)` call — this is the only place internal records are mapped to domain objects for watchlist reads
  - [x] **`WatchlistEntryResponse.java`** (`hu.riskguard.notification.api.dto`): add `String previousVerdictStatus` field; update `from(WatchlistEntry)` factory to map `entry.previousVerdictStatus()`
  - [x] **`frontend/types/api.d.ts`**: add `previousVerdictStatus?: string | null` to `WatchlistEntryResponse` interface, with a TODO comment following the existing `latestSha256Hash` pattern

- [x] T4 — Frontend: handle "Last Screened" and add "Trend" column to `WatchlistTable.vue` (AC: 1, 2, 3, 6)
  - [x] **File:** `frontend/app/components/Watchlist/WatchlistTable.vue` (note capital W in path!)
  - [x] **AC 1 ("Last Screened"):** The table already has a "Last Checked" column showing `lastCheckedAt` via `formatRelative`. AC 1 is satisfied by renaming this column header. Update the Column's `:header` binding from `t('notification.watchlist.columns.lastChecked')` to `t('notification.watchlist.columns.lastScreened')`. Do NOT add a second `lastCheckedAt` column.
  - [x] Add PrimeVue `Column` for "Trend": computed from `data.currentVerdictStatus` vs `data.previousVerdictStatus`; render `<i class="pi pi-arrow-up text-emerald-600">` (improved), `<i class="pi pi-arrow-down text-rose-600">` (worsened), `<i class="pi pi-minus text-slate-400">` (stable), or text `"—"` if `previousVerdictStatus` is null
  - [x] Severity map constant (inline in component or imported composable)
  - [x] Trend helper added
  - [x] Existing columns (Company, Tax Number, Status, Actions) and `row-click` handler are UNCHANGED — added by Stories 3.6/7.2

- [x] T5 — i18n keys (AC: 1, 2)
  - [x] In `frontend/app/i18n/en/notification.json` under `notification.watchlist.columns`:
    - Rename `"lastChecked": "Last Checked"` → `"lastScreened": "Last Screened"` (AC 1 renames the column)
    - Add `"trend": "Trend"` (AC 2 new column)
  - [x] Same changes in `frontend/app/i18n/hu/notification.json`
  - [x] Keep keys alphabetically sorted within objects
  - [x] `notification.json` is already registered in `nuxt.config.ts` — no registration change needed
  - [x] **Search the codebase** for any other usage of `notification.watchlist.columns.lastChecked` before renaming — grep confirms it's only used in `WatchlistTable.vue` header binding

- [x] **[AI-Review] R1 — Add `listEntriesShouldIncludePreviousVerdictStatusInResponse` to `WatchlistControllerTest`** (P1 Medium)

- [x] T6 — Tests (AC: 1–6)
  - [x] **Backend integration:** Added `updateVerdictStatusWithHash_capturesPreviousVerdictStatus()` to `NotificationRepositoryIntegrationTest` — asserts self-referential SQL works end-to-end
  - [x] **Do NOT** update `WatchlistMonitorTest` — that test covers event publishing, not `previous_verdict_status` persistence
  - [x] **Frontend:** Updated `WatchlistTable.spec.ts` to assert:
    - "Last Screened" column renders `formatRelative(lastCheckedAt)` when non-null
    - "Last Screened" shows "—" when `lastCheckedAt` is null
    - "Trend" shows `pi-arrow-up` icon when `previousVerdictStatus` has higher severity than `currentVerdictStatus`
    - "Trend" shows `pi-arrow-down` icon when `previousVerdictStatus` has lower severity
    - "Trend" shows `pi-minus` when statuses are equal
    - "Trend" shows "—" when `previousVerdictStatus` is null

## Dev Notes

### CRITICAL: WatchlistMonitor is in `screening.domain`, NOT `notification.domain`
`WatchlistMonitor.java` lives at `hu.riskguard.screening.domain.WatchlistMonitor` to avoid a circular module dependency (notification ↔ screening). Do NOT modify it. It calls `notificationService.getMonitoredPartnersWithVerdicts()`, detects verdict changes by comparing `MonitoredPartner.lastVerdictStatus()` vs the new verdict, then publishes a `PartnerStatusChanged` event. It does NOT directly write `previous_verdict_status`.

### Where `previous_verdict_status` must be written
The update chain for a status change is:
1. `WatchlistMonitor` detects change → publishes `PartnerStatusChanged` event
2. `PartnerStatusChangedListener.onPartnerStatusChanged()` handles the event
3. Calls `notificationRepository.updateVerdictStatusWithHash(tenantId, taxNumber, newStatus, timestamp, sha256Hash)`
4. **This is where `previous_verdict_status` must be captured** using a self-referential SET

For the "no change" path (WatchlistMonitor → `notificationService.updateVerdictStatus()`):
- This path does NOT capture `previous_verdict_status` — correct, since there's no actual change
- The `updateVerdictStatus()` method must NOT be modified for this story

### Self-referential PostgreSQL UPDATE pattern
```sql
UPDATE watchlist_entries
SET previous_verdict_status = last_verdict_status,  -- reads OLD value on right-hand side
    last_verdict_status = 'AT_RISK',                -- overwrites with new value
    last_checked_at = NOW(),
    updated_at = NOW()
WHERE tenant_id = ? AND tax_number = ?;
```
PostgreSQL evaluates all RHS expressions from the pre-UPDATE row, making this atomic and safe. jOOQ DSL equivalent:
```java
.set(field("previous_verdict_status", String.class), field("last_verdict_status", String.class))
.set(field("last_verdict_status", String.class), verdictStatus)
```

### `NotificationRepository` uses raw jOOQ DSL
The repository uses raw `table()` / `field()` references (not type-safe codegen) — per the TODO comment in the class header. Continue this pattern consistently when adding `previous_verdict_status`.

### WatchlistEntryRecord internal record — full field list after this story
The existing `WatchlistEntryRecord` has: `id`, `tenantId`, `taxNumber`, `companyName`, `label`, `createdAt`, `updatedAt`, `lastVerdictStatus` (maps to `last_verdict_status`), `lastCheckedAt`, `latestSha256Hash`. Add: `previousVerdictStatus`.

### WatchlistEntry domain record — convenience constructor must be updated
The short constructor (`WatchlistEntry(id, tenantId, taxNumber, companyName, label, createdAt, updatedAt)`) delegates to the full constructor with nulls. When adding `previousVerdictStatus` to the canonical constructor, update the convenience constructor to pass `null` for the new parameter as well.

### `lastCheckedAt` already in response — no backend change for AC 1
`WatchlistEntryResponse` already has `lastCheckedAt` (mapped from `WatchlistEntry.lastCheckedAt()`). The "Last Screened" column is purely a frontend change. The column header key goes under `notification.watchlist.columns.*` (not top-level `notification.watchlist.*`).

### WatchlistTable.vue — exact file path
Path is `frontend/app/components/Watchlist/WatchlistTable.vue` (capital "W" in "Watchlist" directory). The file already has:
- `row-click` emit guard (Story 7.2): skips `.p-checkbox` and `[data-testid="remove-entry-button"]`
- `formatRelative` imported via `useDateRelative` composable
- `verdictBadgeClass()` and `verdictLabel()` helpers
- Existing columns: checkbox, Company, Tax Number, Status, Last Checked, Actions

The "Last Screened" column in this story replaces or supplements the existing "Last Checked" column (`lastCheckedAt`). **Check whether the existing "Last Checked" column already shows `lastCheckedAt`** — if it does, this story may just be adding the "Trend" column. Read the current file carefully before adding.

> **ACTUAL STATE (verified):** WatchlistTable already has a "Last Checked" column (`field="lastCheckedAt"`, using `formatRelative`). AC 1 calls for "Last Screened" — this is the same data. The column already exists under key `notification.watchlist.columns.lastChecked`. The story likely wants to rename it to "Last Screened" or add it as a separate display. Given AC 1 says "a 'Last Screened' column is visible", and the existing column already shows this, the dev should check if the header label change is all that's needed for AC 1 (update i18n key `columns.lastChecked` → `columns.lastScreened`) rather than adding a new column.

### STATUS_SEVERITY map — potential duplication
`DashboardNeedsAttention.vue` and `WatchlistPartnerDrawer.vue` (Story 7.2) both have local STATUS_SEVERITY/status-severity maps. This story introduces a third. Deferred D4 in Story 7.2 noted this divergence risk. For now, keep it local to `WatchlistTable.vue` per the existing pattern.

### api.d.ts manual type addition — follow Story 5.1 pattern
`WatchlistEntryResponse` in `frontend/types/api.d.ts` is partially manually maintained. Current state:
```ts
export interface WatchlistEntryResponse {
  id: string
  taxNumber: string
  companyName: string | null
  label: string | null
  currentVerdictStatus: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE' | null
  lastCheckedAt: string | null
  createdAt: string
  // TODO: Remove manual addition once CI OpenAPI pipeline regenerates types from backend (Story 5.1)
  latestSha256Hash?: string | null
}
```
Add `previousVerdictStatus?: string | null` with the same TODO comment pattern.

### Key files to touch
| File | Change |
|------|--------|
| `backend/src/main/resources/db/migration/V20260401_001__add_previous_verdict_status_to_watchlist.sql` | New migration |
| `hu.riskguard.notification.internal.NotificationRepository` | `updateVerdictStatusWithHash()` self-referential SET; `WatchlistEntryRecord` + `findByTenantId()` + `findByTenantIdAndTaxNumber()` |
| `hu.riskguard.notification.domain.WatchlistEntry` | Add `previousVerdictStatus` field |
| `hu.riskguard.notification.domain.NotificationService` | Pass `previousVerdictStatus` when constructing `WatchlistEntry` |
| `hu.riskguard.notification.api.dto.WatchlistEntryResponse` | Add `previousVerdictStatus` field + `from()` mapping |
| `frontend/app/components/Watchlist/WatchlistTable.vue` | Add Trend column (and verify Last Screened AC) |
| `frontend/types/api.d.ts` | Add `previousVerdictStatus?` to `WatchlistEntryResponse` |
| `frontend/app/i18n/en/notification.json` | Add `columns.lastScreened`, `columns.trend` |
| `frontend/app/i18n/hu/notification.json` | Same keys in Hungarian |

### Previous Story Intelligence (Stories 7.1 + 7.2 — completed 2026-04-01)

- **`storeToRefs` fails in tests** when store mocks return plain objects with null reactive values. Use `computed(() => store.property)` wrappers — simpler and more test-friendly. [Source: Story 7.1 debug]
- **Nuxt auto-imports NOT available in spec files.** `useDateRelative`, `useStatusColor` must be stubbed via `vi.stubGlobal('useDateRelative', mockFn)` before component imports. [Source: Story 7.1 debug]
- **`$fetch` mocking:** use `vi.stubGlobal('$fetch', vi.fn())` in spec files.
- **`useIdentityStore` is unreliable** in HttpOnly-cookie auth flow. Use `useAuthStore().isAccountant` for role checks.
- **`STATUS_SEVERITY` map pattern confirmed:** `DashboardNeedsAttention.vue` and `WatchlistPartnerDrawer.vue` both define local maps — Story 7.1/7.2 decided not to extract to a shared composable. Repeat the same pattern here.
- **WatchlistTable `row-click` guard already in place** (Story 7.2): skips `.p-checkbox` and `[data-testid="remove-entry-button"]`; do not duplicate or re-add.
- **i18n: `notification.json` is the correct file** for all watchlist keys (NOT `screening.json`). Already registered in `nuxt.config.ts`.

### Git Intelligence (recent commits)
- `feat(7.2): Partner Detail Slide-Over Drawer` — added `WatchlistPartnerDrawer.vue`, updated `WatchlistTable.vue` with `row-click`, wired drawer into `watchlist/index.vue`
- `feat(7.1): Risk Pulse Dashboard redesign` — added `DashboardStatBar`, `DashboardNeedsAttention`, `DashboardAlertFeed`; introduced local STATUS_SEVERITY map pattern

### Architecture Compliance Checklist
- [ ] Flyway migration name: `V{YYYYMMDD}_{NNN}__{description}.sql` — `V20260401_001__add_previous_verdict_status_to_watchlist.sql` ✓
- [ ] DTO: Java record in `api.dto` package, `static from()` factory, no MapStruct ✓
- [ ] Repository: scoped to `notification` module tables only (`watchlist_entries`) ✓
- [ ] No cross-module table access in `NotificationRepository` (no `verdicts`, `snapshots`, etc.) ✓
- [ ] ArchUnit: `DtoConventionTest` verifies all response DTOs have `from()` factory — ensure the new field is mapped ✓
- [ ] `NamingConventionTest` enforces `snake_case` DB columns — `previous_verdict_status` ✓
- [ ] Frontend: `tsc --noEmit` must pass after updating `api.d.ts`

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References

### Completion Notes List
- T1: Created `V20260401_001__add_previous_verdict_status_to_watchlist.sql` — adds `previous_verdict_status VARCHAR(30) NULL DEFAULT NULL` to `watchlist_entries`
- T2: Added self-referential SET in `NotificationRepository.updateVerdictStatusWithHash()` — `previous_verdict_status = last_verdict_status` before overwrite; `updateVerdictStatus()` intentionally unchanged
- T3: Propagated `previousVerdictStatus` through full domain cascade: `WatchlistEntryRecord` → `findByTenantId()` / `findByTenantIdAndTaxNumber()` SELECT+mapper → `WatchlistEntry` domain record → `NotificationService.toDomain()` → `WatchlistEntryResponse.from()` → `frontend/types/api.d.ts`
- T4: Renamed "Last Checked" → "Last Screened" header in `WatchlistTable.vue`; added Trend `Column` with `trendDirection()` helper and `STATUS_SEVERITY` map; `row-click` guard unchanged
- T5: Renamed `columns.lastChecked` → `columns.lastScreened` in both en/hu `notification.json`; added `columns.trend` key; keys kept alphabetically sorted
- T6: Added `updateVerdictStatusWithHash_capturesPreviousVerdictStatus()` to `NotificationRepositoryIntegrationTest`; updated `WatchlistTable.spec.ts` with 6 new test cases (Last Screened + Trend)
- Test results: 704 backend tests pass, 649 frontend tests pass (63 files), 0 failures
- ✅ Resolved review finding [Med]: P1 — added `listEntriesShouldIncludePreviousVerdictStatusInResponse` to `WatchlistControllerTest`; 10 tests pass (was 9). 2 Low findings deferred (P2 unreachable edge case, P3 redundant function calls advisory).

### File List
- `backend/src/main/resources/db/migration/V20260401_001__add_previous_verdict_status_to_watchlist.sql` (new)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java` (modified)
- `backend/src/main/java/hu/riskguard/notification/domain/WatchlistEntry.java` (modified)
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java` (modified)
- `backend/src/main/java/hu/riskguard/notification/api/dto/WatchlistEntryResponse.java` (modified)
- `frontend/types/api.d.ts` (modified)
- `frontend/app/components/Watchlist/WatchlistTable.vue` (modified)
- `frontend/app/i18n/en/notification.json` (modified)
- `frontend/app/i18n/hu/notification.json` (modified)
- `backend/src/test/java/hu/riskguard/notification/NotificationRepositoryIntegrationTest.java` (modified)
- `backend/src/test/java/hu/riskguard/notification/api/WatchlistControllerTest.java` (modified)
- `frontend/app/components/Watchlist/WatchlistTable.spec.ts` (modified)

## Change Log

- 2026-03-31: Story created (moved from Epic 6 planning). Status → ready-for-dev.
- 2026-04-01: Story implemented by claude-sonnet-4-6. Code review R1 (2026-04-01) — 1 Med finding resolved (P1: missing `previousVerdictStatus` controller test), 2 Low findings deferred. 10 backend unit tests pass. Status → done. DB migration added, previousVerdictStatus propagated through backend domain cascade, Trend column and Last Screened rename added to frontend, i18n keys updated, integration and frontend tests added. 704 backend + 649 frontend tests green. Status → review.: corrected `WatchlistMonitor` package (screening.domain, not notification.domain), corrected migration filename (V20260401_001__, not V20260331_001__ which is taken), corrected T2 approach (self-referential SQL in `updateVerdictStatusWithHash()` via PartnerStatusChangedListener, not direct WatchlistMonitor modification), added full domain cascade (WatchlistEntryRecord, WatchlistEntry, NotificationService, WatchlistEntryResponse, api.d.ts), corrected test targets (PartnerStatusChangedListenerTest not WatchlistMonitorTest), noted existing Last Checked column ambiguity for AC 1, added 7.1/7.2 dev learnings.
