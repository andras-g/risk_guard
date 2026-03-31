# Story 7.1: Risk Pulse Dashboard Redesign

Status: ready-for-dev

## Story

As a User (SME owner),
I want my dashboard to show a live summary of my partner portfolio — counts by risk status, a "Needs Attention" list of at-risk and stale partners, and a recent status-change feed —
So that I can assess my compliance posture at a glance every morning without manually scanning the full watchlist.

> **Accountant behaviour unchanged:** Users with `ACCOUNTANT` role continue to be redirected to `/flight-control` on dashboard mount. This story applies only to SME-owner users.

## Acceptance Criteria

1. **Stat bar — portfolio health counts**
   - Given an authenticated SME-owner user navigates to `/dashboard`
   - When the watchlist data finishes loading
   - Then three stat cards are displayed: "Reliable" (emerald, count of `RELIABLE` entries), "At Risk" (crimson, count of `AT_RISK` + `TAX_SUSPENDED` + `INCOMPLETE` entries), "Stale / Unavailable" (amber, count of `UNAVAILABLE` entries)
   - And counts are computed client-side from `watchlistStore.entries` (no new backend endpoint)

2. **"Needs Attention" list**
   - Given the stat bar is rendered
   - When there are entries with status `AT_RISK`, `TAX_SUSPENDED`, `INCOMPLETE`, or `UNAVAILABLE`
   - Then they are displayed in a prioritised list (AT_RISK first, INCOMPLETE second, UNAVAILABLE third), max 10 entries
   - And each row shows: company name (bold), tax number (JetBrains Mono), status badge (colored), `lastCheckedAt` relative timestamp, and a right-arrow link to `/screening/[taxNumber]`
   - And clicking the row arrow navigates to `/screening/[taxNumber]` (existing screening detail page)

3. **"Recent Status Changes" feed**
   - Given the portfolio alerts endpoint returns data (existing `portfolioStore.fetchAlerts(7)`)
   - Then up to 5 most recent alerts are displayed in a right-column feed
   - And each item shows: company name, previous→new status change (e.g. "Reliable → At Risk"), relative timestamp, colored left border matching new status
   - And if no alerts exist, the feed shows "No changes in the last 7 days"

4. **Tax number search bar preserved**
   - The existing `ScreeningSearchBar` component remains on the dashboard below the two-column section
   - Searching still navigates to `/screening/[taxNumber]` via the existing watcher (no change to existing logic)

5. **Loading states**
   - While `watchlistStore.isLoading` is true, stat cards and attention list show PrimeVue `Skeleton` placeholders
   - While `portfolioStore.isLoading` is true, the alert feed shows skeleton rows
   - The search bar is visible and functional immediately (not blocked by loading state)

6. **Empty watchlist deferred to Story 6.5**
   - If `watchlistStore.entries.length === 0` and not loading, show a simple placeholder message: "Add partners to your watchlist to see your risk summary here." (Story 6.5 replaces this with the full onboarding component)

## Tasks / Subtasks

- [ ] T1 — Fetch watchlist and alerts on dashboard mount (AC: 1, 2, 3)
  - [ ] In `dashboard/index.vue`, call `watchlistStore.fetchEntries()` and `portfolioStore.fetchAlerts(7)` in `onMounted` (parallel, `Promise.all`)
  - [ ] Remove the `screeningStore.clearSearch()` call only after verifying no other page depends on it running at mount (it is safe to remove — the watcher below handles navigation)
  - [ ] Preserve existing `watch(currentVerdict, ...)` watcher that navigates to `/screening/[taxNumber]`

- [ ] T2 — Stat bar component (AC: 1, 5)
  - [ ] Create `frontend/app/components/dashboard/DashboardStatBar.vue`
  - [ ] Props: `entries: WatchlistEntryResponse[]`, `isLoading: boolean`
  - [ ] Computed: `reliableCount`, `atRiskCount` (AT_RISK + TAX_SUSPENDED + INCOMPLETE), `staleCount` (UNAVAILABLE)
  - [ ] Loading: three `Skeleton` placeholders (width: 180px, height: 72px)
  - [ ] Use existing status color conventions from `useStatusColor` composable

- [ ] T3 — "Needs Attention" list component (AC: 2, 5)
  - [ ] Create `frontend/app/components/dashboard/DashboardNeedsAttention.vue`
  - [ ] Props: `entries: WatchlistEntryResponse[]`, `isLoading: boolean`
  - [ ] Computed: filter + sort entries by priority (AT_RISK/TAX_SUSPENDED → INCOMPLETE → UNAVAILABLE), slice to max 10
  - [ ] Each row: company name, tax number in `font-mono`, status badge (PrimeVue `Tag`), `useDateRelative` for `lastCheckedAt`, `NuxtLink` to `/screening/[taxNumber]`
  - [ ] Loading: 3 skeleton rows

- [ ] T4 — "Recent Status Changes" feed (AC: 3, 5)
  - [ ] Create `frontend/app/components/dashboard/DashboardAlertFeed.vue`
  - [ ] Props: `alerts: PortfolioAlertResponse[]`, `isLoading: boolean`
  - [ ] Render max 5 items; reuse `useStatusColor` for border class; use `useDateRelative` for timestamp
  - [ ] Empty state text (AC: 3)

- [ ] T5 — Compose dashboard page (AC: 1–6)
  - [ ] Rewrite `frontend/app/pages/dashboard/index.vue` to use `DashboardStatBar`, `DashboardNeedsAttention`, `DashboardAlertFeed`, `ScreeningSearchBar`
  - [ ] Two-column layout for attention list + alert feed (CSS grid, 60/40 split, collapses to single column on mobile)
  - [ ] Accountant redirect (`isAccountant` → `/flight-control`) unchanged

- [ ] T6 — i18n keys (AC: 2, 3, 6)
  - [ ] Add to `frontend/app/i18n/en/screening.json` (or new `dashboard.json` namespace — match existing namespace convention):
    `dashboard.statReliable`, `dashboard.statAtRisk`, `dashboard.statStale`, `dashboard.needsAttention`, `dashboard.recentChanges`, `dashboard.noRecentChanges`, `dashboard.emptyWatchlistHint`
  - [ ] Mirror keys in `hu/` file; keep both files alphabetically sorted

- [ ] T7 — Spec (AC: 1–5)
  - [ ] Create `frontend/app/pages/dashboard/dashboard.spec.ts` co-located with `index.vue`
  - [ ] Mock `watchlistStore` and `portfolioStore`; assert stat counts, attention list ordering, alert feed render, skeleton visibility, search bar presence

## Dev Notes

### No backend changes required
All data for this story already exists client-side: `watchlistStore.entries` (fetched via existing `GET /api/v1/watchlist`) and `portfolioStore.alerts` (fetched via existing `GET /api/v1/portfolio/alerts?days=7`). Do not add a new backend endpoint.

### Status priority mapping
- "At Risk" stat card: `AT_RISK`, `TAX_SUSPENDED`, `INCOMPLETE`
- "Stale" stat card: `UNAVAILABLE`
- "Needs Attention" sort order: `AT_RISK` = priority 0, `TAX_SUSPENDED` = 1, `INCOMPLETE` = 2, `UNAVAILABLE` = 3

### Accountant guard (unchanged)
The existing guard in `dashboard/index.vue`:
```ts
const isAccountant = computed(() => identityStore.user?.role === 'ACCOUNTANT')
onMounted(() => { if (isAccountant.value) router.push('/flight-control') })
```
Keep this intact. The new components should only render for non-accountant paths.

### Relevant existing composables
- `useStatusColor` — `frontend/app/composables/formatting/useStatusColor.ts` — provides `statusColorClass(status)` and `statusIconClass(status)`
- `useDateRelative` — `frontend/app/composables/formatting/useDateRelative.ts` — provides `formatRelative(isoString)`
- `portfolioStore` — `frontend/app/stores/portfolio.ts` — already used in `flight-control/index.vue` (Story 3.9/3.10); reuse the same store

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/pages/dashboard/index.vue` | Rewrite (preserve watcher + accountant redirect) |
| `frontend/app/components/dashboard/DashboardStatBar.vue` | New |
| `frontend/app/components/dashboard/DashboardNeedsAttention.vue` | New |
| `frontend/app/components/dashboard/DashboardAlertFeed.vue` | New |
| `frontend/app/pages/dashboard/dashboard.spec.ts` | New |
| `frontend/app/i18n/en/*.json` | New keys |
| `frontend/app/i18n/hu/*.json` | New keys |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
