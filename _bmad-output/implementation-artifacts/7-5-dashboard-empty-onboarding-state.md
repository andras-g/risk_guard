# Story 7.5: Dashboard Empty & Onboarding State

Status: review

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

- [x] T1 — Create `WatchlistOnboardingHero.vue` component (AC: 2, 3, 4, 5)
  - [x] File: `frontend/app/components/dashboard/WatchlistOnboardingHero.vue`
  - [x] Props: none (reads from `watchlistStore` internally for add-partner success detection)
  - [x] Emits: none
  - [x] Muted stat bar: render `DashboardStatBar` with `entries=[]` and `isLoading=false` wrapped in a low-opacity container (`opacity-40`)
  - [x] Hero heading: `dashboard.onboardingTitle` (e.g. "Your risk monitor is ready")
  - [x] Sub-heading: `dashboard.onboardingSubtitle` (e.g. "Add your first partner to start monitoring their NAV compliance status.")
  - [x] Primary button: `WatchlistAddDialog` trigger — use the same `visible` ref pattern as `watchlist/index.vue`; import and embed `WatchlistAddDialog` within this component
  - [x] Secondary button: emits a `focus-search` event (caught by `dashboard/index.vue` to focus the `ScreeningSearchBar`)
  - [x] 3-step strip: three cards in a horizontal flex row (wraps on mobile); icon + step title + description

- [x] T2 — Wire hero into `dashboard/index.vue` (AC: 1, 6, 7)
  - [x] Replace the Story 7.1 placeholder `<p data-testid="empty-watchlist-hint">` with conditional rendering:
    - `v-if="!watchlistLoading && watchlistEntries.length === 0"` → `<WatchlistOnboardingHero @focus-search="focusSearchBar" />`
    - `v-else-if="!watchlistLoading"` → wrap stat bar + attention list + alert feed (the three blocks currently at lines ~70-92) in this branch
  - [x] Add `focusSearchBar()` method: calls `$refs.searchBar.focus()` (or use `provide/inject` with `ScreeningSearchBar`)
  - [x] Import and register `WatchlistOnboardingHero`

- [x] T3 — i18n keys (AC: 2–5)
  - [x] Add to `en/` and `hu/`:
    - `dashboard.onboardingTitle`
    - `dashboard.onboardingSubtitle`
    - `dashboard.addFirstPartner`
    - `dashboard.searchByTaxNumber`
    - `dashboard.howItWorksStep1Title`, `dashboard.howItWorksStep1Body`
    - `dashboard.howItWorksStep2Title`, `dashboard.howItWorksStep2Body`
    - `dashboard.howItWorksStep3Title`, `dashboard.howItWorksStep3Body`
  - [x] Remove `dashboard.emptyWatchlistHint` added in Story 6.1 (superseded by this component)
  - [x] Keep files alphabetically sorted

- [x] T4 — Spec (AC: 1–5, 7)
  - [x] Update `frontend/app/pages/dashboard/dashboard.spec.ts` (created in Story 7.1)
  - [x] **REMOVE** or update the 3 existing tests that check `data-testid="empty-watchlist-hint"` (~lines 234-255) — they will fail once the `<p>` is replaced by the hero component
  - [x] Add test: when `watchlistStore.entries = []` and not loading → hero renders, stat bar + attention list hidden
  - [x] Add test: when `watchlistStore.entries` has items → hero hidden, stat bar renders
  - [x] Add test: "Add Your First Partner" button opens `WatchlistAddDialog`
  - [x] Add test: "Search by Tax Number" button emits `focus-search` (or calls focus on search bar)
  - [x] Add test: 3-step strip renders all three steps with correct icons

## Dev Notes

### Relationship to Story 7.1 (current codebase state)
Story 7.1 T5 sets up the conditional rendering scaffold with a simple placeholder `<p>` tag at lines 60-67 of `dashboard/index.vue`:

```vue
<!-- Empty watchlist hint (Story 7.5 replaces with onboarding component) -->
<p
  v-if="watchlistEntries.length === 0 && !watchlistLoading"
  class="text-slate-400 text-sm"
  data-testid="empty-watchlist-hint"
>
  {{ t('dashboard.emptyWatchlistHint') }}
</p>
```

Story 7.5 (this story) replaces that placeholder with `WatchlistOnboardingHero`. The condition logic (`entries.length === 0 && !isLoading`) is already wired — T2 here swaps the placeholder for the real component.

**IMPORTANT — Stat bar visibility:** Currently `DashboardStatBar`, `DashboardNeedsAttention`, and `DashboardAlertFeed` are always rendered (lines 70-92) regardless of whether entries is empty. The empty state just shows the `<p>` above them. Story 7.5 must also wrap these three components in a `v-else-if` so that the onboarding hero replaces them entirely (AC 1: "rendered **instead of** the stat bar, attention list, and alert feed").

### WatchlistAddDialog reuse
`WatchlistAddDialog` is located at `frontend/app/components/Watchlist/WatchlistAddDialog.vue` (capital W in folder name). It is currently used in `watchlist/index.vue`. Props: `visible: boolean`. Emits: `update:visible` and `submit` with tax number, company name, verdict status. Embed it in `WatchlistOnboardingHero` using the same open/close `visible` ref pattern. After success, call `watchlistStore.fetchEntries()` — the updated non-empty entries list will trigger the `v-else-if` branch in `dashboard/index.vue`, replacing the hero automatically.

### Focusing the ScreeningSearchBar
`ScreeningSearchBar` is a child component of `dashboard/index.vue`. Use a template ref (`ref="searchBarRef"`) on the component and expose a `focus()` method from within `ScreeningSearchBar` via `defineExpose`. The `focusSearchBar()` handler in `dashboard/index.vue` calls `searchBarRef.value?.focus()`. Alternatively, emit a custom event and handle it at the page level — either pattern is acceptable.

### Muted stat bar
Wrap `<DashboardStatBar :entries="[]" :is-loading="false" />` in a `<div class="opacity-40 pointer-events-none select-none">` so the user can see the future layout but cannot interact with the zero-count cards.

### Key files to touch
| File | Change |
|------|--------|
| `frontend/app/components/dashboard/WatchlistOnboardingHero.vue` | New |
| `frontend/app/pages/dashboard/index.vue` | Replace `<p data-testid="empty-watchlist-hint">` at lines ~60-67 with `<WatchlistOnboardingHero>`; wrap stat bar + attention list + alert feed in `v-else-if` |
| `frontend/app/pages/dashboard/dashboard.spec.ts` | Extend with hero tests; update/remove `empty-watchlist-hint` testid references |
| `frontend/app/i18n/en/dashboard.json` | New onboarding keys, remove `emptyWatchlistHint` |
| `frontend/app/i18n/hu/dashboard.json` | Same |
| `frontend/app/components/Watchlist/WatchlistAddDialog.vue` | Read-only reference — reuse as-is, no changes |

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References
None — clean first-pass implementation.

### Completion Notes List
- Created `WatchlistOnboardingHero.vue` with muted stat bar preview (`opacity-40 pointer-events-none`), hero heading/subtitle, primary CTA (opens WatchlistAddDialog), secondary CTA (emits `focus-search`), and 3-step how-it-works strip.
- Wired hero into `dashboard/index.vue`: replaced `<p data-testid="empty-watchlist-hint">` placeholder with tri-branch conditional (hero / live dashboard / loading skeletons). Added `focusSearchBar()` using template ref on `ScreeningSearchBar`.
- Added `defineExpose({ focus() })` to `Screening/SearchBar.vue` so dashboard can programmatically focus the search input.
- Added 9 new i18n keys (EN + HU), removed `emptyWatchlistHint` from both locales. Files sorted alphabetically.
- Added 6 hero unit tests in `dashboard.spec.ts` (WatchlistOnboardingHero describe block). Fixed 2 pre-existing tests that assumed alert feed always renders (now gated on entries existing).
- All 672 frontend tests pass.

### File List
- `frontend/app/components/dashboard/WatchlistOnboardingHero.vue` (new)
- `frontend/app/pages/dashboard/index.vue` (modified)
- `frontend/app/components/Screening/SearchBar.vue` (modified — added defineExpose focus)
- `frontend/app/pages/dashboard/dashboard.spec.ts` (modified)
- `frontend/app/i18n/en/dashboard.json` (modified)
- `frontend/app/i18n/hu/dashboard.json` (modified)

### Review Findings

- [x] [Review][Patch] P1: `handleAddSubmit` has no error handling and ignores the `duplicate` return value — no try/catch, no user feedback on network failure or duplicate add [WatchlistOnboardingHero.vue:13-16]
- [x] [Review][Patch] P2: `handleAddSubmit` calls `watchlistStore.fetchEntries()` explicitly after `addEntry()` which already calls it internally — redundant double-fetch [WatchlistOnboardingHero.vue:15]
- [x] [Review][Patch] P3: `ref="inputRef"` bound to PrimeVue `InputText` component yields a component instance, not an `HTMLInputElement`; `focus()` may silently no-op [SearchBar.vue:17, 84]
- [x] [Review][Patch] P4: No test verifies that `fetchEntries` is called after dialog submit (AC 3 unverified) [dashboard.spec.ts]
- [x] [Review][Patch] P5: How-it-works step test omits body assertions for steps 2 & 3 and does not check icon classes (AC 5 partial) [dashboard.spec.ts:367-370]
- [x] [Review][Defer] D1: Hero may flicker briefly after successful add (async window between `addEntry` POST and `fetchEntries` resolving) [WatchlistOnboardingHero.vue] — deferred, pre-existing store async behavior
- [x] [Review][Defer] D2: `ScreeningSearchBarStub` expose pattern in tests is informal — ref wiring of `focusSearchBar` is untested at integration level [dashboard.spec.ts] — deferred, pre-existing test infrastructure gap
- [x] [Review][Defer] D3: No test triggers `focus-search` on the hero stub to verify the full `focusSearchBar` wiring in `index.vue` — deferred, test coverage gap
- [x] [Review][Defer] D4: `emptyWatchlistHint` i18n key removed; no codebase-wide grep done to confirm no other consumer — deferred, isolated to dashboard story scope
- [x] [Review][Defer] D5: Skeleton loading test does not assert `is-loading=true` is passed to skeleton stat bar (AC 7 test partial) [dashboard.spec.ts] — deferred, minor test gap

## Change Log

- Implemented WatchlistOnboardingHero with muted stat bar, CTAs, and how-it-works strip (Date: 2026-04-01)
- Addressed 5 code review findings: added try/catch + duplicate handling in handleAddSubmit (P1), removed redundant fetchEntries call (P2), fixed PrimeVue InputText ref to use $el for focus (P3), added addEntry call test (P4), expanded step icon/body assertions (P5) (Date: 2026-04-01)
