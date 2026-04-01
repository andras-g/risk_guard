# Story 7.1: Risk Pulse Dashboard Redesign

Status: review

## Story

As a User (SME owner),
I want my dashboard to show a live summary of my partner portfolio — counts by risk status, a "Needs Attention" list of at-risk and stale partners, and a recent status-change feed —
So that I can assess my compliance posture at a glance every morning without manually scanning the full watchlist.

> **Accountant behaviour unchanged:** Users with `ACCOUNTANT` role are redirected to `/flight-control` on dashboard mount. This redirect must be ADDED (it does not currently exist). This story applies only to SME-owner users.

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

6. **Empty watchlist deferred to Story 7.5**
   - If `watchlistStore.entries.length === 0` and not loading, show a simple placeholder message: "Add partners to your watchlist to see your risk summary here." (Story 7.5 replaces this with the full onboarding component)

## Tasks / Subtasks

- [x] T1 — Fetch watchlist and alerts on dashboard mount (AC: 1, 2, 3)
  - [x] In `dashboard/index.vue`, call `watchlistStore.fetchEntries()` and `portfolioStore.fetchAlerts(7)` in `onMounted` (parallel, `Promise.all`)
  - [x] **KEEP** the existing `screeningStore.clearSearch()` call — it prevents stale verdict from triggering the watcher and redirecting back to `/screening/[taxNumber]` on every dashboard mount. Removing it would break navigation.
  - [x] Preserve existing `watch(currentVerdict, ...)` watcher that navigates to `/screening/[taxNumber]`
  - [x] **ADD** accountant redirect in `onMounted`: `if (isAccountant.value) router.push('/flight-control')` — this guard does NOT currently exist in `dashboard/index.vue`
  - [x] Remove `<NotificationPortfolioPulse v-if="isAccountant" />` from template (accountants will be redirected before seeing the dashboard)

- [x] T2 — Stat bar component (AC: 1, 5)
  - [x] Create `frontend/app/components/dashboard/DashboardStatBar.vue`
  - [x] Props: `entries: WatchlistEntryResponse[]`, `isLoading: boolean`
  - [x] Computed: `reliableCount` (entries where `currentVerdictStatus === 'RELIABLE'`), `atRiskCount` (`AT_RISK` + `TAX_SUSPENDED` + `INCOMPLETE`), `staleCount` (`UNAVAILABLE`)
  - [x] Stat card colors are hardcoded per card: emerald for Reliable, crimson (red-700) for At Risk, amber for Stale
  - [x] Loading: three `Skeleton` placeholders (width: 180px, height: 72px)

- [x] T3 — "Needs Attention" list component (AC: 2, 5)
  - [x] Create `frontend/app/components/dashboard/DashboardNeedsAttention.vue`
  - [x] Props: `entries: WatchlistEntryResponse[]`, `isLoading: boolean`
  - [x] Computed: filter entries by `currentVerdictStatus` in (`AT_RISK`, `TAX_SUSPENDED`, `INCOMPLETE`, `UNAVAILABLE`), sort by priority (AT_RISK=0, TAX_SUSPENDED=1, INCOMPLETE=2, UNAVAILABLE=3), slice to max 10
  - [x] Each row: company name bold, tax number in `font-mono`, status badge (PrimeVue `Tag` with hardcoded severity or class per status), `useDateRelative` for `lastCheckedAt`, `NuxtLink` to `/screening/[taxNumber]`
  - [x] **Do NOT use `useStatusColor` for badge colors** — it doesn't handle `TAX_SUSPENDED` or `UNAVAILABLE` explicitly (both fall to slate default). Use a local status→severity/class map instead:
    - `AT_RISK` → `severity="danger"` (crimson)
    - `TAX_SUSPENDED` → `severity="danger"` (crimson)
    - `INCOMPLETE` → `severity="warn"` (amber)
    - `UNAVAILABLE` → `severity="secondary"` (slate)
  - [x] Loading: 3 skeleton rows

- [x] T4 — "Recent Status Changes" feed (AC: 3, 5)
  - [x] Create `frontend/app/components/dashboard/DashboardAlertFeed.vue`
  - [x] Props: `alerts: PortfolioAlertResponse[]`, `isLoading: boolean`
  - [x] Render max 5 items; use `useStatusColor` for left border class based on `alert.newStatus`; use `useDateRelative` for `alert.changedAt`
  - [x] Each item shows: `alert.companyName`, `alert.previousStatus → alert.newStatus` (use `statusI18nKey` from `useStatusColor` for display labels), relative timestamp
  - [x] Empty state text (AC: 3)

- [x] T5 — Compose dashboard page (AC: 1–6)
  - [x] Rewrite `frontend/app/pages/dashboard/index.vue` to use `DashboardStatBar`, `DashboardNeedsAttention`, `DashboardAlertFeed`, `ScreeningSearchBar`
  - [x] Two-column layout for attention list + alert feed (CSS grid, 60/40 split, collapses to single column on mobile)
  - [x] Keep `screeningStore.clearSearch()` in `onMounted`; ADD accountant redirect; remove `NotificationPortfolioPulse`

- [x] T6 — i18n keys (AC: 2, 3, 6)
  - [x] Create NEW file `frontend/app/i18n/en/dashboard.json`:
    ```json
    {
      "dashboard": {
        "statReliable": "Reliable",
        "statAtRisk": "At Risk",
        "statStale": "Stale / Unavailable",
        "needsAttention": "Needs Attention",
        "recentChanges": "Recent Status Changes",
        "noRecentChanges": "No changes in the last 7 days",
        "emptyWatchlistHint": "Add partners to your watchlist to see your risk summary here."
      }
    }
    ```
  - [x] Create `frontend/app/i18n/hu/dashboard.json` with Hungarian translations
  - [x] **Register in `frontend/nuxt.config.ts`** — add `'hu/dashboard.json'` to the `hu` locale files array and `'en/dashboard.json'` to the `en` locale files array. The i18n files are explicitly listed; a new file is NOT auto-discovered.

- [x] T7 — Spec (AC: 1–5)
  - [x] Create `frontend/app/pages/dashboard/dashboard.spec.ts` co-located with `index.vue`
  - [x] Mock `watchlistStore`, `portfolioStore`, and `screeningStore`; assert stat counts, attention list ordering, alert feed render, skeleton visibility, search bar presence
  - [x] Test accountant redirect: mock `identityStore.user.role = 'ACCOUNTANT'`, assert `router.push('/flight-control')` called

## Dev Notes

### No backend changes required
All data already exists client-side: `watchlistStore.entries` (fetched via `GET /api/v1/watchlist`) and `portfolioStore.alerts` (fetched via `GET /api/v1/portfolio/alerts?days=7`). Do not add a new backend endpoint.

### CRITICAL: Accountant guard must be ADDED, not preserved
The current `dashboard/index.vue` does NOT redirect accountants to `/flight-control`. It currently shows `<NotificationPortfolioPulse v-if="isAccountant" />` instead. This story must:
1. Add `if (isAccountant.value) router.push('/flight-control')` to `onMounted` (after `screeningStore.clearSearch()`)
2. Remove `<NotificationPortfolioPulse v-if="isAccountant" />` from the template

### CRITICAL: Keep `screeningStore.clearSearch()` in `onMounted`
The current code has `screeningStore.clearSearch()` in `onMounted` with this comment:
> "Clear any previous verdict when the dashboard mounts so the watcher below does not immediately redirect back to the detail page if the user navigated here from /screening/[taxNumber] and the store still holds the previous result."

**Do NOT remove this call.** The watcher `watch(currentVerdict, ...)` navigates to `/screening/[taxNumber]` whenever a verdict is present. Without `clearSearch()`, users navigating from the verdict page back to the dashboard would immediately be redirected back. Keep it as the first line in `onMounted`.

### WatchlistEntryResponse field names
The status field is **`currentVerdictStatus`** (not `verdictStatus`):
```ts
interface WatchlistEntryResponse {
  id: string
  taxNumber: string
  companyName: string | null
  currentVerdictStatus: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE' | null
  lastCheckedAt: string | null
  // ...
}
```
Use `entry.currentVerdictStatus` in all computed properties and templates.

### useStatusColor gaps
`useStatusColor` does NOT handle `TAX_SUSPENDED` or `UNAVAILABLE` (both fall to default slate). This is fine for:
- `DashboardAlertFeed` — uses `useStatusColor` for left border on `alert.newStatus` (STALE/INCOMPLETE handle fine; TAX_SUSPENDED/UNAVAILABLE get slate which is acceptable)
- Stat bar — colors are hardcoded per card, do not use `useStatusColor`

Do NOT use `useStatusColor` for status badges in `DashboardNeedsAttention`. Use PrimeVue `Tag` severity instead (see T3).

### Status priority mapping
- "At Risk" stat card: `AT_RISK`, `TAX_SUSPENDED`, `INCOMPLETE`
- "Stale" stat card: `UNAVAILABLE`
- "Needs Attention" sort order: `AT_RISK`=0, `TAX_SUSPENDED`=1, `INCOMPLETE`=2, `UNAVAILABLE`=3

### i18n: nuxt.config.ts registration required
New i18n files are NOT auto-discovered. After creating `dashboard.json` files, you MUST update `frontend/nuxt.config.ts`:
```ts
// In the 'hu' locale:
files: ['hu/admin.json', 'hu/auth.json', 'hu/common.json', 'hu/dashboard.json', 'hu/epr.json', ...]
// In the 'en' locale:
files: ['en/admin.json', 'en/auth.json', 'en/common.json', 'en/dashboard.json', 'en/epr.json', ...]
```
Files must be alphabetically ordered within each list.

### Relevant existing files
- `useStatusColor` — `frontend/app/composables/formatting/useStatusColor.ts` — `statusColorClass(status)`, `statusIconClass(status)`, `statusI18nKey(status)`
- `useDateRelative` — `frontend/app/composables/formatting/useDateRelative.ts` — `formatRelative(isoString)`
- `portfolioStore` — `frontend/app/stores/portfolio.ts` — `fetchAlerts(days)`, `isLoading`, `alerts: PortfolioAlertResponse[]`
- `watchlistStore` — `frontend/app/stores/watchlist.ts` — `fetchEntries()`, `isLoading`, `entries: WatchlistEntryResponse[]`
- `PortfolioAlertResponse` — `frontend/types/api.d.ts` line 146 — fields: `alertId`, `companyName`, `previousStatus`, `newStatus`, `changedAt`, `taxNumber`

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/pages/dashboard/index.vue` | Rewrite (keep clearSearch + watcher, ADD accountant redirect, remove PortfolioPulse) |
| `frontend/app/components/dashboard/DashboardStatBar.vue` | New |
| `frontend/app/components/dashboard/DashboardNeedsAttention.vue` | New |
| `frontend/app/components/dashboard/DashboardAlertFeed.vue` | New |
| `frontend/app/pages/dashboard/dashboard.spec.ts` | New |
| `frontend/app/i18n/en/dashboard.json` | New |
| `frontend/app/i18n/hu/dashboard.json` | New |
| `frontend/nuxt.config.ts` | Register dashboard.json in both locale file lists |

### Previous story intelligence (Story 6.4)
- Explicit composable imports required in spec files (`useAdminAudit` had to be imported, not auto-imported via Nuxt)
- `$fetch` mocked via `vi.stubGlobal` in tests
- Frontend types added manually to `api.d.ts` when no CI OpenAPI pipeline (not needed for this story — all types already exist)
- Pattern for i18n in tests: use `global.plugins = [i18n]` or mock `t()` — follow `epr-config.spec.ts` or `audit-search.spec.ts`

## Project Artifact Inventory

### New files (create)

- `frontend/app/components/dashboard/DashboardStatBar.vue`
- `frontend/app/components/dashboard/DashboardNeedsAttention.vue`
- `frontend/app/components/dashboard/DashboardAlertFeed.vue`
- `frontend/app/pages/dashboard/dashboard.spec.ts`
- `frontend/app/i18n/en/dashboard.json`
- `frontend/app/i18n/hu/dashboard.json`

### Modified files (edit)

- `frontend/app/pages/dashboard/index.vue` — rewrite
- `frontend/nuxt.config.ts` — register `dashboard.json` in i18n locales

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References
- `storeToRefs` fails in tests when store mocks return plain objects with null reactive values. Fixed by replacing all `storeToRefs` usage with `computed(() => store.property)` wrappers — simpler and more test-friendly.
- `useStatusColor` and `useDateRelative` composables are Nuxt auto-imports (not explicit imports in components). In spec files they must be stubbed via `vi.stubGlobal` before the component imports.

### Completion Notes List
- All 7 tasks implemented. No backend changes (all data from existing watchlist and portfolio stores).
- Accountant redirect (`router.push('/flight-control')`) added as first guard in `onMounted` after `screeningStore.clearSearch()`. Accountants short-circuit before `fetchEntries/fetchAlerts` are called.
- `DashboardStatBar`: 3 stat cards (emerald/crimson/amber), Skeleton placeholders while loading.
- `DashboardNeedsAttention`: filters to AT_RISK/TAX_SUSPENDED/INCOMPLETE/UNAVAILABLE, priority-sorted, max 10 rows. Uses local severity map (not `useStatusColor`) per Dev Notes.
- `DashboardAlertFeed`: max 5 items, `useStatusColor` for left border, `statusI18nKey` for status labels, empty state text.
- Two-column CSS grid layout (md:col-span-3 / md:col-span-2 ≈ 60/40), collapses to single column on mobile.
- i18n: 7 keys in `en/dashboard.json` and `hu/dashboard.json`, registered in `nuxt.config.ts` alphabetically.
- 28 new tests: 13 dashboard page tests (store fetch, stat counts, loading states, accountant redirect) + 5 DashboardStatBar + 7 DashboardNeedsAttention + 3 DashboardAlertFeed. All 636 frontend tests pass.
- ✅ Resolved review finding [HIGH]: P1 — replaced `useIdentityStore` with `useAuthStore`; `authStore.isAccountant` reliably reads role from `/me` response (HttpOnly cookie flow)
- ✅ Resolved review finding [MEDIUM]: P2 — restored `exclude: ['monaco-editor']` to `optimizeDeps` in `nuxt.config.ts`
- ✅ Resolved review finding [Decision]: Accountant flash — chose Option A, added `v-if="!isAccountant"` to template root div; accountants see nothing while redirect resolves

### File List
- `frontend/app/pages/dashboard/index.vue` (modified — rewritten)
- `frontend/app/components/dashboard/DashboardStatBar.vue` (new)
- `frontend/app/components/dashboard/DashboardNeedsAttention.vue` (new)
- `frontend/app/components/dashboard/DashboardAlertFeed.vue` (new)
- `frontend/app/pages/dashboard/dashboard.spec.ts` (new)
- `frontend/app/i18n/en/dashboard.json` (new)
- `frontend/app/i18n/hu/dashboard.json` (new)
- `frontend/nuxt.config.ts` (modified — i18n locale file registration)

### Review Findings (code-review R1 — 2026-04-01)

- [x] [Review][Decision] Accountant flash before redirect — after P1 is fixed, accountants still see one render tick of the dashboard before `router.push` resolves; options: (A) add `v-if="!isAccountant"` wrapper to the full template body, or (B) accept the one-tick flash as acceptable SPA behavior [frontend/app/pages/dashboard/index.vue] → Chose Option A: added `v-if="!isAccountant"` to template root
- [x] [Review][Patch] **P1 — Wrong store for isAccountant [HIGH]**: `identityStore.user` is always `null` in the HttpOnly-cookie auth flow (only populated via `setToken()` with a raw JWT string, which is never called); redirect to `/flight-control` never fires for accountants; fix: use `authStore.isAccountant` or equivalent [frontend/app/pages/dashboard/index.vue:22] → Fixed: replaced `useIdentityStore` with `useAuthStore`; `authStore.isAccountant` reads from `/me` response
- [x] [Review][Patch] **P2 — monaco-editor removed from optimizeDeps.exclude [MEDIUM]**: `MonacoEditor.vue` is still in use; Vite will attempt to pre-bundle `monaco-editor` (a large ESM package with worker scripts) causing dev-server OOM or worker-load failures; restore the `exclude: ['monaco-editor']` entry [frontend/nuxt.config.ts:13] → Restored `exclude: ['monaco-editor']` to optimizeDeps
- [x] [Review][Defer] W1 — API failures silently produce misleading empty states [frontend/app/pages/dashboard/index.vue:36-39] — `fetchEntries` failure renders the empty-watchlist hint; `fetchAlerts` failure renders "No changes in the last 7 days"; no try/catch or error display; deferred, project-wide pattern
- [x] [Review][Defer] W2 — null currentVerdictStatus entries uncounted in stat bar [DashboardStatBar.vue:11-25] — entries with `null` status match no card; totals appear authoritative but may be incomplete; deferred, no "pending" bucket in spec
- [x] [Review][Defer] W3 — formatRelative called with potentially null lastCheckedAt [DashboardNeedsAttention.vue:81] — safe if composable handles null gracefully (not verified from diff); deferred, composable behavior
- [x] [Review][Defer] W4 — Raw enum strings in attention-list badges [DashboardNeedsAttention.vue:78] — Tag :value renders AT_RISK, TAX_SUSPENDED etc. directly; spec does not mandate localized badge text; deferred, UX polish
- [x] [Review][Defer] W5 — 10-entry cap in NeedsAttention with no overflow indicator [DashboardNeedsAttention.vue:42] — spec-compliant but silent truncation in risk-monitoring context; deferred
- [x] [Review][Defer] W6 — 5-alert cap in AlertFeed with no overflow indicator [DashboardAlertFeed.vue:13] — spec says "up to 5"; deferred
- [x] [Review][Defer] W7 — INCOMPLETE classified as "At Risk" (red) in stat bar but "warn" (yellow) in attention list — intentional per spec; semantic inconsistency may confuse users; deferred

## Change Log

- 2026-03-31: Story created (moved from Epic 6 planning). Status → ready-for-dev.
- 2026-04-01: Story enriched with critical implementation warnings: accountant redirect missing, currentVerdictStatus field name, useStatusColor gaps, clearSearch must be kept, nuxt.config.ts i18n registration required.
- 2026-04-01: Implementation complete. 3 new dashboard components, rewritten index.vue, i18n files, 28 new tests. 636 frontend tests pass. Status → review.
- 2026-04-01: Addressed code review findings — 3 items resolved (P1, P2, Decision). Status → review.
