# Story 7.5: Dashboard Empty & Onboarding State

Status: ready-for-dev

## Story

As a new User (SME owner) with no partners yet on my watchlist,
I want the dashboard to guide me through adding my first partner and explain how monitoring works —
So that I understand what to do next instead of seeing a blank or confusing page.

## Acceptance Criteria

1. **Empty state detection**
   - Given an authenticated SME-owner user navigates to `/dashboard`
   - When `watchlistStore.entries.length === 0` and `watchlistStore.isLoading === false`
   - Then the `WatchlistOnboardingHero` component is rendered instead of the stat bar, attention list, and alert feed
   - And the `ScreeningSearchBar` remains visible below the hero (unchanged)

2. **Onboarding hero — zero-count stat bar**
   - The hero shows the same three stat cards as `DashboardStatBar` but all counts are `0` and visually muted (lower opacity or greyed-out)
   - This provides visual continuity: the user sees what the dashboard will look like once populated

3. **Primary CTA — "Add Your First Partner"**
   - A prominent primary button "Add Your First Partner" is displayed in the hero
   - Clicking it opens the existing `WatchlistAddDialog` (same dialog used from the watchlist page)
   - After a partner is successfully added, `watchlistStore.fetchEntries()` re-runs and the hero is replaced by the live dashboard (Story 6.1 content)

4. **Secondary CTA — "Search by Tax Number"**
   - A secondary outlined button "Search by Tax Number" is displayed alongside the primary CTA
   - Clicking it scrolls down to / focuses the `ScreeningSearchBar` input field

5. **How-it-works strip**
   - Below the CTAs, a 3-step strip explains the workflow:
     - Step 1: "Add Partners" — add companies you work with to your watchlist
     - Step 2: "Automatic Monitoring" — RiskGuard checks their NAV status nightly
     - Step 3: "Stay Informed" — get alerted when a partner's risk status changes
   - Each step has an icon (PrimeVue icon: `pi-plus-circle`, `pi-sync`, `pi-bell` respectively) and short description

6. **Accountant users unaffected**
   - The accountant redirect (`isAccountant` → `/flight-control`) fires before any watchlist fetch
   - This component is never rendered for accountant-role users

7. **Loading state**
   - While `watchlistStore.isLoading` is true, the existing skeleton placeholders from Story 6.1 are shown
   - The hero is only rendered after loading completes with zero entries

## Tasks / Subtasks

- [ ] T1 — Create `WatchlistOnboardingHero.vue` component (AC: 2, 3, 4, 5)
  - [ ] File: `frontend/app/components/dashboard/WatchlistOnboardingHero.vue`
  - [ ] Props: none (reads from `watchlistStore` internally for add-partner success detection)
  - [ ] Emits: none
  - [ ] Muted stat bar: render `DashboardStatBar` with `entries=[]` and `isLoading=false` wrapped in a low-opacity container (`opacity-40`)
  - [ ] Hero heading: `dashboard.onboardingTitle` (e.g. "Your risk monitor is ready")
  - [ ] Sub-heading: `dashboard.onboardingSubtitle` (e.g. "Add your first partner to start monitoring their NAV compliance status.")
  - [ ] Primary button: `WatchlistAddDialog` trigger — use the same `visible` ref pattern as `watchlist/index.vue`; import and embed `WatchlistAddDialog` within this component
  - [ ] Secondary button: emits a `focus-search` event (caught by `dashboard/index.vue` to focus the `ScreeningSearchBar`)
  - [ ] 3-step strip: three cards in a horizontal flex row (wraps on mobile); icon + step title + description

- [ ] T2 — Wire hero into `dashboard/index.vue` (AC: 1, 6, 7)
  - [ ] Replace the Story 6.1 placeholder text (`dashboard.emptyWatchlistHint`) with conditional rendering:
    - `v-if="!watchlistStore.isLoading && watchlistStore.entries.length === 0"` → `<WatchlistOnboardingHero @focus-search="focusSearchBar" />`
    - `v-else-if="!watchlistStore.isLoading"` → stat bar + attention list + alert feed (Story 6.1 content)
  - [ ] Add `focusSearchBar()` method: calls `$refs.searchBar.focus()` (or use `provide/inject` with `ScreeningSearchBar`)
  - [ ] Import and register `WatchlistOnboardingHero`

- [ ] T3 — i18n keys (AC: 2–5)
  - [ ] Add to `en/` and `hu/`:
    - `dashboard.onboardingTitle`
    - `dashboard.onboardingSubtitle`
    - `dashboard.addFirstPartner`
    - `dashboard.searchByTaxNumber`
    - `dashboard.howItWorksStep1Title`, `dashboard.howItWorksStep1Body`
    - `dashboard.howItWorksStep2Title`, `dashboard.howItWorksStep2Body`
    - `dashboard.howItWorksStep3Title`, `dashboard.howItWorksStep3Body`
  - [ ] Remove `dashboard.emptyWatchlistHint` added in Story 6.1 (superseded by this component)
  - [ ] Keep files alphabetically sorted

- [ ] T4 — Spec (AC: 1–5, 7)
  - [ ] Update `frontend/app/pages/dashboard/dashboard.spec.ts` (created in Story 6.1)
  - [ ] Add test: when `watchlistStore.entries = []` and not loading → hero renders, stat bar + attention list hidden
  - [ ] Add test: when `watchlistStore.entries` has items → hero hidden, stat bar renders
  - [ ] Add test: "Add Your First Partner" button opens `WatchlistAddDialog`
  - [ ] Add test: "Search by Tax Number" button emits `focus-search` (or calls focus on search bar)
  - [ ] Add test: 3-step strip renders all three steps with correct icons

## Dev Notes

### Relationship to Story 6.1
Story 6.1 T5 sets up the conditional rendering scaffold with a simple placeholder string (`dashboard.emptyWatchlistHint`). Story 6.5 replaces that placeholder with `WatchlistOnboardingHero`. The condition logic (`entries.length === 0 && !isLoading`) should already be in place from Story 6.1 — T2 here just swaps the placeholder for the real component.

### WatchlistAddDialog reuse
`WatchlistAddDialog` is currently used in `watchlist/index.vue`. It is a self-contained dialog component that emits a success event after adding a partner. Embed it in `WatchlistOnboardingHero` using the same open/close `visible` ref pattern. After success, call `watchlistStore.fetchEntries()` — the updated non-empty entries list will trigger the `v-else-if` branch in `dashboard/index.vue`, replacing the hero automatically.

### Focusing the ScreeningSearchBar
`ScreeningSearchBar` is a child component of `dashboard/index.vue`. Use a template ref (`ref="searchBarRef"`) on the component and expose a `focus()` method from within `ScreeningSearchBar` via `defineExpose`. The `focusSearchBar()` handler in `dashboard/index.vue` calls `searchBarRef.value?.focus()`. Alternatively, emit a custom event and handle it at the page level — either pattern is acceptable.

### Muted stat bar
Wrap `<DashboardStatBar :entries="[]" :is-loading="false" />` in a `<div class="opacity-40 pointer-events-none select-none">` so the user can see the future layout but cannot interact with the zero-count cards.

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/components/dashboard/WatchlistOnboardingHero.vue` | New |
| `frontend/app/pages/dashboard/index.vue` | Replace placeholder with hero component |
| `frontend/app/pages/dashboard/dashboard.spec.ts` | Extend with hero tests |
| `frontend/app/i18n/en/screening.json` (or dashboard namespace) | New keys, remove `emptyWatchlistHint` |
| `frontend/app/i18n/hu/screening.json` | Same |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
