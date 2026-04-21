# Story 10.10: EPR Filing Navigation & Discoverability

Status: review

<!-- Epic 10 · Story 10.10 · added via CP-6 (2026-04-21) · depends on 10.1, 10.6, 10.9 (all done); parallel-safe with 10.7 -->
<!-- Frontend-only story: 2 Vue components, 1 page, 1 dialog, i18n keys, 4 spec files, 1 new E2E spec, 1 deferred-work cleanup. Zero DB / zero backend / zero ArchUnit rule changes. -->

## Story

As an **SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN user** on a PRO_EPR-tier tenant,
I want a **persistent "Negyedéves bejelentés" nav entry in the sidebar and mobile drawer, plus contextual CTAs on the Registry page header and after invoice bootstrap completes**,
so that **I can reach the EPR filing page in one click from anywhere in the application, instead of typing the URL**.

## Acceptance Criteria

### Frontend — Desktop Sidebar Nav Entry

1. `frontend/app/components/Common/AppSidebar.vue` — `mainNavItems` computed gains an `eprFiling` entry:
   ```ts
   { key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }
   ```
   Inserted **directly after** the `registry` entry, and only when `hasProEpr.value` is `true` (same spread-conditional pattern as `registry` at line 152). The entry does NOT carry an `accountantOnly` flag — it is visible to SME_ADMIN, ACCOUNTANT, and PLATFORM_ADMIN on PRO_EPR tier. Hidden for ALAP and PRO tiers.

2. The nav entry renders through the existing `<NuxtLink>` + `v-tooltip.right` machinery in the template (lines 27–59) — no template change is required. Active-route detection via the existing `isActive()` helper (line 177) works for `/epr/filing` and any future `/epr/filing/*` sub-route.

3. `data-testid` is `nav-item-eprFiling` (existing convention: `nav-item-${item.key}`). Icon `pi-file` (PrimeIcons) chosen to signal "document/filing"; consistent with the breadcrumb's document metaphor and distinguishable from `pi-box` (registry), `pi-search` (screening), `pi-eye` (watchlist).

### Frontend — Mobile Drawer Nav Entry

4. `frontend/app/components/Common/AppMobileDrawer.vue` — the `mainNavItems` constant array (currently a plain `const` at line 120, **not** a computed) is converted to a `computed` that mirrors `AppSidebar.vue`'s PRO_EPR tier gating. Add the `eprFiling` entry with identical key / to / icon values as AC #1.

5. Tier gating port: add a `hasProEpr` computed to `AppMobileDrawer.vue`'s `<script setup>` block using the same `useTierGate('PRO_EPR')` pattern as `pages/registry/index.vue:16` **or** the `TIER_ORDER` pattern from `AppSidebar.vue:137–141`. Use `useTierGate('PRO_EPR').hasAccess` to stay consistent with the rest of the codebase; import `useTierGate` from `~/composables/auth/useTierGate`.

6. The mobile drawer's `eprFiling` entry is rendered by the existing `<NuxtLink v-for>` loop (lines 22–38); no template change beyond the array extension is needed. `data-testid` is `drawer-nav-eprFiling` (existing convention).

7. **Scope note (non-goal):** the mobile drawer currently does **not** expose the `registry` entry either (see "Pre-existing gap" in Dev Notes). This story does NOT add `registry` to the drawer. It only adds `eprFiling`. Restoring `registry` to the drawer is deferred to Epic 11 UX polish — confirmed with PO per CP-6.

### Frontend — i18n Keys

8. `frontend/app/i18n/hu/common.json` — insert `eprFiling: "Negyedéves bejelentés"` in the `common.nav` object, **alphabetically positioned** between `dashboard` and `registry` (enforced by the `lint:i18n` pre-commit hook from Story 10.1 retro T6).

9. `frontend/app/i18n/en/common.json` — insert `eprFiling: "Quarterly filing"` in the `common.nav` object at the same alphabetical position.

10. `npm run -w frontend lint:i18n` must pass **22/22**; no key drift, no ordering violations. No other i18n files are touched by this story.

### Frontend — Registry Page Header CTA

11. `frontend/app/pages/registry/index.vue` — the page-header row (lines 157–164) gains a **secondary outlined** PrimeVue `<Button>` labelled via `t('registry.actions.openFiling')` with icon `pi pi-file`, `severity="secondary"`, `outlined`, positioned **to the left of** the existing "Új termék" primary button. Click handler: `router.push('/epr/filing')`. `data-testid="header-cta-filing"`.

12. The filing CTA is visible only when `registryCompleteness.productsWithComponents.value > 0`. When the registry is empty, or when `productsWithComponents === 0`, the CTA is hidden — the onboarding block / all-incomplete banner (already in the page at lines 220–234) owns that state. Use a `v-if` guard; `registryCompleteness` is already instantiated at line 23.

13. Add two i18n keys (alphabetically):
    - `registry.actions.openFiling`: `"Negyedéves bejelentés"` (HU) / `"Open filing"` (EN) in `registry.json` under the existing `actions` nested object.

### Frontend — InvoiceBootstrapDialog Completion CTA

14. `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` — in the `<template #footer>` block's **done-phase** sub-template (lines 369–377), add a **secondary outlined** `<Button>` labelled `t('registry.bootstrap.completion.openFiling')`, rendered **next to** (after) the existing "Megnyitás" (`openRegistry`) button. `data-testid="bootstrap-cta-filing"`. Click handler: a new `onOpenFiling()` function that calls `onClose()` then `router.push('/epr/filing')` (mirrors `onOpenRegistry()` at line 186 minus the query-param branching).

15. The filing CTA is visible **only** on the completion frame when:
    ```
    jobStatus.value?.status === 'COMPLETED'
      && jobStatus.value.createdProducts > 0
      && jobStatus.value.unresolvedPairs === 0
    ```
    Hidden on `FAILED`, `FAILED_PARTIAL`, `CANCELLED`, and on `COMPLETED` with `createdProducts === 0` (empty-period case, see `bootstrap-completion-empty` block at lines 294–298) OR any `unresolvedPairs > 0` (user should fix hiányos rows first). Use a single `v-if` expression with the three conjuncts.

16. Add two i18n keys (alphabetically):
    - `registry.bootstrap.completion.openFiling`: `"Bejelentés megnyitása"` (HU) / `"Open filing"` (EN) under the existing `bootstrap.completion` nested object.

### Frontend — Breadcrumb Comment Update

17. `frontend/app/components/Common/AppBreadcrumb.vue` — update the JSDoc comment above `OBSOLETE_PARENT_SEGMENTS` (currently lines 76–82) to reflect Story 10.10's reality:
    - `/epr` index page does not exist (Story 10.1 removed it).
    - `/epr/filing` is the **only** active route under `/epr/*`.
    - The sidebar entry added by Story 10.10 routes directly to `/epr/filing`, so breadcrumb users never need to click the "EPR" parent segment.
    - Keep the non-clickable parent treatment; no code change.

   **No change to `OBSOLETE_PARENT_SEGMENTS` itself.** The comment update is the only deliverable for AC #17.

### Cleanup — deferred-work.md D6

18. `_bmad-output/implementation-artifacts/deferred-work.md:88` — this note was already updated to `RESOLVED STALE (2026-04-21, CP-6)` during CP-6 authoring. **Verification-only AC:** the dev agent asserts the note currently reads `**RESOLVED STALE (2026-04-21, CP-6):**` and references Story 10.10. If the note has regressed to its pre-CP-6 form, re-apply the resolved-stale wording from `sprint-change-proposal-2026-04-21.md:232–244`.

### Tests — vitest

19. `AppSidebar.spec.ts` extends the existing `'AppSidebar — navigation items'` describe block + the `'AppSidebar — component mount smoke test'` describe block:
    - `mainNavItems` length changes from 4 → 5; new `it('includes eprFiling entry pointing to /epr/filing with pi-file icon', ...)` asserts key, to, icon.
    - Key ordering assertion updates from `['dashboard', 'screening', 'watchlist', 'registry']` → `['dashboard', 'screening', 'watchlist', 'registry', 'eprFiling']` in the `'includes Dashboard, Screening, Watchlist, and Registry items'` test (rename the test title accordingly).
    - Mount smoke test adds `expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(true)` after the existing `nav-item-registry` assertion.
    - Two new tests under `'AppSidebar — PRO_EPR tier gating for registry'` describe block (rename to `'AppSidebar — PRO_EPR tier gating for registry and eprFiling'`):
      - `eprFiling entry shown when tier is PRO_EPR`
      - `eprFiling entry hidden when tier is PRO` (and another for `ALAP`, `null`)

20. `AppMobileDrawer.spec.ts` extends:
    - `mainNavItems` length changes from 3 → 4 (pending PO sign-off on AC #7 scope — mobile drawer gets `eprFiling`, not `registry`).
    - `'each item uses i18n key pattern common.nav.{key}'` loop continues to pass for the new entry.
    - New test `it('includes eprFiling drawer entry pointing to /epr/filing', ...)`.
    - Mount smoke test asserts `drawer-nav-eprFiling` testid exists on PRO_EPR-tiered mount; hidden on ALAP mount.
    - The existing auth-store mock (`role: 'SME_ADMIN'`) needs a `tier: 'PRO_EPR'` field added so the tier gate returns `hasAccess=true`. Add a second `describe` block with a separate mock that sets `tier: 'ALAP'` and asserts the drawer entry is absent.

21. `registry/index.spec.ts` gains **two new tests**:
    - `'renders filing CTA when productsWithComponents > 0'` — mock `useRegistryCompleteness` to return `productsWithComponents=1`; assert `wrapper.find('[data-testid="header-cta-filing"]').exists()` is `true`; assert click pushes `/epr/filing`.
    - `'hides filing CTA when productsWithComponents === 0'` — mock to return `productsWithComponents=0`; assert testid does not exist.

22. `InvoiceBootstrapDialog.spec.ts` gains **three new tests**:
    - `'filing CTA visible on COMPLETED with createdProducts > 0 && unresolvedPairs === 0'`.
    - `'filing CTA hidden on COMPLETED with unresolvedPairs > 0'` (user must still fix hiányos rows).
    - `'filing CTA hidden on FAILED / FAILED_PARTIAL / CANCELLED / empty COMPLETED'` (parameterize via `it.each` over the four statuses).

### Tests — Playwright E2E

23. New E2E spec `frontend/e2e/nav-to-filing.e2e.ts`:
    - **Golden path:** authenticate as the deterministic `E2E_USER` (SME_ADMIN on the PRO_EPR-tiered E2E tenant via `loginAsTestUser` from `./auth.setup`). `page.goto('/dashboard')` → click `[data-testid="nav-item-eprFiling"]` → assert `page.url().endsWith('/epr/filing')` → assert the filing page heading is visible (`page.getByRole('heading', { name: /negyedéves bejelentés/i })`).
    - **Tier-gate exclusion:** the current E2E seed uses a PRO_EPR-tiered tenant for `E2E_USER`. Wrap an ALAP-tier check in a `test.skip` guard: if the e2e seed does NOT include an ALAP user, **skip** the exclusion test (follow the `empty-registry-onboarding.e2e.ts` skip pattern at lines 28–31, 46–50). If an ALAP user exists, authenticate as them and assert `page.getByTestId('nav-item-eprFiling')` is not visible.
    - **Mobile drawer path:** `page.setViewportSize({ width: 375, height: 812 })` → open drawer via `[data-testid="hamburger-button"]` → assert `[data-testid="drawer-nav-eprFiling"]` is visible and navigates to `/epr/filing` on click.
    - **MUST NOT** hard-fail on missing data: follow the skip-when-precondition-not-met pattern used throughout Epic 10 E2Es (`empty-registry-onboarding.e2e.ts` lines 35–39, `submission-history.e2e.ts` lines 11–14). Hard-assert only on the golden path.

24. Existing Playwright specs (`filing-workflow.e2e.ts`, `submission-history.e2e.ts`, `empty-registry-onboarding.e2e.ts`) continue to pass — they all use `page.goto('/epr/filing')` directly, so the new nav entry does not affect their flow.

### Process Gate

25. **AC-to-task walkthrough (retro T1)** filed in the Dev Agent Record's **Completion Notes List** under heading `### AC-to-Task Walkthrough (T1)` **before any production code is committed**. Map every AC above to the task number(s) that cover it; any uncovered AC triggers task addition before code. Pattern: see Stories 10.1, 10.8, 10.9 R1 reviews. This is a hard gate — the T1 retro exists because Story 9.4 skipped this step and paid 25+ patches.

## Tasks / Subtasks

> **Order matters.** Task 1 (AC-to-task walkthrough) is a **gate** — do not open any other task until the walkthrough is filed. Tasks 2 (i18n) + 3 (AppSidebar) can commit together; Task 4 (AppMobileDrawer) depends on Task 3 for the tier-gate pattern; Tasks 5 (Registry CTA), 6 (InvoiceBootstrapDialog CTA), 7 (breadcrumb comment) are independent after Task 2. Task 8 (E2E) and Task 10 (verification) come last.

- [x] **Task 1 — AC-to-task walkthrough gate (AC: #25).**
  - [x] Read each AC #1–#24 in order; under a `### AC-to-Task Walkthrough (T1)` heading in the Completion Notes, list each AC number with the matching Task number(s). File the walkthrough verbatim before touching any production file. Any uncovered AC adds a task here before proceeding.

- [x] **Task 2 — i18n keys (AC: #8, #9, #10, #13, #16).**
  - [x] Add `common.nav.eprFiling` to `frontend/app/i18n/hu/common.json` and `frontend/app/i18n/en/common.json` alphabetically between `dashboard` and `registry`.
  - [x] Add `registry.actions.openFiling` to `frontend/app/i18n/hu/registry.json` and `frontend/app/i18n/en/registry.json` alphabetically within the `actions` nested object.
  - [x] Add `registry.bootstrap.completion.openFiling` to the same two files alphabetically within the `bootstrap.completion` nested object.
  - [x] Run `npm run -w frontend lint:i18n` → 22/22 green.

- [x] **Task 3 — AppSidebar.vue + AppSidebar.spec.ts (AC: #1, #2, #3, #19).**
  - [x] Append `...(hasProEpr.value ? [{ key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }] : [])` to the `mainNavItems` computed **after** the `registry` conditional spread (line 152).
  - [x] Extend `AppSidebar.spec.ts`:
    - Update the `mainNavItems` constant and the 4→5 length assertion.
    - Update the key-order assertion.
    - Add registry + eprFiling tier-gate tests (PRO_EPR visible, PRO/ALAP/null hidden).
    - Add `nav-item-eprFiling` assertion in the mount smoke test.

- [x] **Task 4 — AppMobileDrawer.vue + AppMobileDrawer.spec.ts (AC: #4, #5, #6, #7, #20).**
  - [x] Import `useTierGate` from `~/composables/auth/useTierGate`.
  - [x] Instantiate `const { hasAccess: hasProEpr } = useTierGate('PRO_EPR')` in the `<script setup>` block.
  - [x] Convert the plain `mainNavItems` constant (line 120) to a `computed` that appends `{ key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }` only when `hasProEpr.value === true`. Do **not** add `registry` to the drawer — out of scope per AC #7.
  - [x] Extend `AppMobileDrawer.spec.ts`:
    - Update `mainNavItems` length assertion 3→4.
    - Add `tier: 'PRO_EPR'` to the auth-store mock at line 27.
    - Add `it('includes eprFiling drawer entry pointing to /epr/filing', ...)`.
    - Add a second `describe` or `it` with a separate mock that forces `tier: 'ALAP'` and asserts the drawer entry is absent.
    - Update the mount smoke test to assert `drawer-nav-eprFiling` testid exists.

- [x] **Task 5 — Registry page header CTA + spec (AC: #11, #12, #13, #21).**
  - [x] In `frontend/app/pages/registry/index.vue`, add a secondary outlined `<Button>` (label `t('registry.actions.openFiling')`, icon `pi pi-file`, `severity="secondary"`, `outlined`, `data-testid="header-cta-filing"`, click handler routes to `/epr/filing`) **to the left of** the existing "Új termék" button within the header flex container (lines 157–164). Use `v-if="registryCompleteness.productsWithComponents.value > 0"`.
  - [x] Extend `registry/index.spec.ts` with the two visibility tests — keep the existing `useRegistryCompleteness` mocking pattern. Mock the composable to surface `productsWithComponents` as a Vue ref so `.value > 0` evaluates as expected.

- [x] **Task 6 — InvoiceBootstrapDialog CTA + spec (AC: #14, #15, #16, #22).**
  - [x] In `InvoiceBootstrapDialog.vue`, define `function onOpenFiling()` mirroring `onOpenRegistry()` but routing unconditionally to `/epr/filing`. Add a secondary outlined `<Button>` in the done-phase footer sub-template (lines 369–377) with `data-testid="bootstrap-cta-filing"`, `v-if="jobStatus?.status === 'COMPLETED' && jobStatus.createdProducts > 0 && jobStatus.unresolvedPairs === 0"`.
  - [x] Extend `InvoiceBootstrapDialog.spec.ts` with the three visibility tests; parameterize the "hidden on non-success" test via `it.each` over the four status scenarios.

- [x] **Task 7 — Breadcrumb comment update (AC: #17).**
  - [x] Edit the JSDoc block above `OBSOLETE_PARENT_SEGMENTS` in `AppBreadcrumb.vue` (lines 76–82) to reflect post-Story-10.10 state. No code change. No test change — existing `AppBreadcrumb.spec.ts` assertions around the non-clickable parent continue to pass.

- [x] **Task 8 — Playwright E2E: `nav-to-filing.e2e.ts` (AC: #23, #24).**
  - [x] Create `frontend/e2e/nav-to-filing.e2e.ts` using the `loginAsTestUser` helper from `./auth.setup`.
  - [x] Three tests: sidebar → filing (golden, hard assertions); mobile drawer → filing (golden, hard assertions); ALAP-tier exclusion (skip when the e2e seed lacks an ALAP user — follow `empty-registry-onboarding.e2e.ts` skip pattern).
  - [x] Verify all existing E2Es pass unchanged. (Deferred to Task 10 full verification run.)

- [x] **Task 9 — deferred-work.md D6 verification (AC: #18).**
  - [x] `grep -n "D6:" _bmad-output/implementation-artifacts/deferred-work.md` → assert the match line contains `**RESOLVED STALE (2026-04-21, CP-6):**`. If regressed, restore from the CP-6 proposal (file lines 232–244).

- [x] **Task 10 — Full verification.**
  - [x] `npm run -w frontend test` — all vitest green (target ≥ previous baseline + 8–10 new tests). **Result: 862/862 (baseline 844 + 18 new tests).**
  - [x] `npx tsc --noEmit -p frontend/tsconfig.json` — clean. **Result: clean.**
  - [x] `npm run -w frontend lint` — 0 errors. **Result: 0 errors, 819 warnings (all pre-existing style warnings; no new warnings from Story 10.10).**
  - [x] `npm run -w frontend lint:i18n` — 22/22. **Result: 22/22 OK — keys alphabetical at every level.**
  - [x] `./gradlew test --tests "hu.riskguard.architecture.*"` — ArchUnit green (unchanged; sanity). **Result: BUILD SUCCESSFUL (UP-TO-DATE; backend untouched).**
  - [x] `npx playwright test` against live backend + Nuxt — `nav-to-filing.e2e.ts` green; all pre-existing E2Es still green. **Result: 6 passed / 12 skipped / 0 failed (baseline 5 passed / 10 skipped; +1 passed + 2 skipped from nav-to-filing.e2e.ts; E2E seed tenant is ALAP so both golden-path cases skip under the Epic 10 skip-when-precondition pattern, and the negative ALAP assertion is the passing case).**

## Dev Notes

### Architecture Compliance — MUST FOLLOW

- **Frontend-only story.** No DB migration, no backend code, no ArchUnit rule change. Any temptation to "just add a tier check on the backend" is scope creep — the existing `@TierRequired(PRO_EPR)` on `EprController` already 403s non-entitled tenants; the frontend nav entry is a discoverability fix, not a security control.
- **No new composable, no new store.** Reuse:
  - `~/composables/auth/useTierGate` — tier check (new import only for `AppMobileDrawer.vue`).
  - `~/composables/api/useRegistryCompleteness` — already instantiated in `registry/index.vue:23`; read `productsWithComponents.value` directly.
  - `useRouter()` — already in scope in all three target components; no new import.
- **No `<i18n>` edits outside `common.json` / `registry.json`.** Specifically, do not touch `epr.json` — Story 10.9's submission-history keys live there, and the lint:i18n hook will catch any accidental ordering drift.
- **Data-testid naming conventions (must follow):**
  - Sidebar nav items: `nav-item-${key}` — already enforced by `AppSidebar.vue:45`.
  - Mobile drawer nav items: `drawer-nav-${key}` — already enforced by `AppMobileDrawer.vue:33`.
  - Registry page header CTA: `header-cta-filing` (new; parity with existing page-scoped CTA naming).
  - Bootstrap dialog CTA: `bootstrap-cta-filing` (new; parity with `bootstrap-open-registry-btn` at `InvoiceBootstrapDialog.vue:374`).
- **T6 i18n alphabetical-ordering hook** from Story 10.1 is enforced by `npm run -w frontend lint:i18n` — this will fail the pre-commit hook if any key is out of order within its nested object. Always insert new keys at the correct alphabetical position; do NOT append-at-end.

### Pre-existing Gap (NOT in scope)

`AppMobileDrawer.vue` currently exposes only `dashboard`, `screening`, `watchlist` (lines 120–124) — it is **missing the `registry` entry** that `AppSidebar.vue` exposes for PRO_EPR tenants. This is a pre-existing parity defect, surfaced but **not fixed** by this story per CP-6 AC #7 / §4.1. Restoring `registry` to the mobile drawer is deferred to Epic 11 UX polish. If the dev agent notices this and is tempted to "fix both at once": **don't**. That expansion was explicitly rejected in CP-6 §3 (options 2 and 4) to keep the story scope tight.

### Exact File Locations

| File | Change | Reference |
|---|---|---|
| `frontend/app/components/Common/AppSidebar.vue` | +1 computed spread entry | line 152 (after the `registry` spread) |
| `frontend/app/components/Common/AppMobileDrawer.vue` | `useTierGate` import + `mainNavItems` → computed + `eprFiling` entry | lines 92–120 |
| `frontend/app/components/Common/AppBreadcrumb.vue` | JSDoc comment update only | lines 76–82 |
| `frontend/app/pages/registry/index.vue` | +1 secondary `<Button>` in header flex row | lines 157–164 |
| `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` | +1 `onOpenFiling()` fn + 1 secondary `<Button>` in done-phase footer | lines 186–198 (new fn) + 369–377 (new button) |
| `frontend/app/i18n/hu/common.json` | +1 key under `common.nav` | between lines 54 and 55 (`dashboard` → `eprFiling` → `registry`) |
| `frontend/app/i18n/en/common.json` | +1 key under `common.nav` | between lines 54 and 55 |
| `frontend/app/i18n/hu/registry.json` | +2 keys (`actions.openFiling`, `bootstrap.completion.openFiling`) | within existing nested objects |
| `frontend/app/i18n/en/registry.json` | +2 keys | within existing nested objects |
| `frontend/app/components/Common/AppSidebar.spec.ts` | test extensions | ~lines 47–93 + mount-smoke at line 244 |
| `frontend/app/components/Common/AppMobileDrawer.spec.ts` | test extensions + new tier-gate describe | ~lines 39–85 + 188–201 |
| `frontend/app/pages/registry/index.spec.ts` | +2 tests | end of file |
| `frontend/app/components/Registry/InvoiceBootstrapDialog.spec.ts` | +3 tests | end of file |
| `frontend/e2e/nav-to-filing.e2e.ts` | NEW file | 3 tests; follows `empty-registry-onboarding.e2e.ts` structure |
| `_bmad-output/implementation-artifacts/deferred-work.md` | verify-only (D6 already updated) | line 88 |

### Icon Choice

`pi-file` (PrimeIcons). Rationale:
- Semantically "document/filing" — matches the Hungarian "bejelentés" mental model.
- Visually distinct from existing sidebar icons: `pi-th-large` (dashboard grid), `pi-search` (screening magnifier), `pi-eye` (watchlist), `pi-box` (registry — packaging box), `pi-objects-column` (flight control Kanban).
- Available in the current PrimeIcons version (used elsewhere in the app; grep for `pi pi-file` confirms prior usage).

### Tier-Gate Composable Pattern (AppMobileDrawer port)

Currently `AppMobileDrawer.vue` does not tier-gate anything — all three existing entries are visible on any tier. The port from `AppSidebar.vue` is:

```ts
// Add to <script setup> block after the existing store imports:
import { useTierGate } from '~/composables/auth/useTierGate'

// After existing consts:
const { hasAccess: hasProEpr } = useTierGate('PRO_EPR')

// Convert mainNavItems from plain const to computed:
const mainNavItems = computed(() => [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  ...(hasProEpr.value ? [{ key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }] : []),
])
```

The `<template>` block's `v-for="item in mainNavItems"` at line 23 works identically against a computed ref — no template change needed.

### Tests — Mocking `useRegistryCompleteness` in registry/index.spec.ts

The existing `registry/index.spec.ts` does not currently mock `useRegistryCompleteness`. Add a module mock at the top (symmetric with the other `vi.mock` blocks) that returns refs with `.value` set to the test scenario's counts. Example:

```ts
const mockProductsWithComponents = ref(0)
vi.mock('~/composables/api/useRegistryCompleteness', () => ({
  useRegistryCompleteness: vi.fn(() => ({
    totalProducts: ref(0),
    productsWithComponents: mockProductsWithComponents,
    isLoading: ref(false),
    isEmpty: computed(() => mockProductsWithComponents.value === 0),
    refresh: vi.fn(),
  })),
}))
```

Then each test mutates `mockProductsWithComponents.value` before asserting visibility.

### Previous Story Intelligence

- **Story 10.1 (done):** removed `/epr` route and `Anyagkönyvtár` menu entry but **did not add a replacement nav for `/epr/filing`** — this is the root cause of the gap Story 10.10 closes. Story 10.1's `AppSidebar.spec.ts` asserted absence of `nav-item-epr` and `a[href="/epr"]`; Story 10.10 keeps those negative assertions and adds positive assertions for `nav-item-eprFiling`.
- **Story 10.7 (in-progress):** parallel-safe. 10.7 adds the `RegistryOnboardingBlock` on `/registry` and `/epr/filing` for the empty-registry state. The filing CTA from AC #11 complements 10.7 — when the registry has components, the onboarding block is hidden and the CTA is visible; transitions work via the existing `v-if` chain in `registry/index.vue:219–234`.
- **Story 10.9 (done):** added the Submission History panel to `/epr/filing`. A user whose flow Story 10.10 enables (click sidebar → land on filing) will also see 10.9's panel. No shared state, no interaction.
- **Retro T1 (HIGH):** AC-to-task walkthrough before code. Enforced by Task 1. Story 9.4 skipped this and paid 25+ patches.
- **Retro T6 (LOW):** i18n alphabetical ordering pre-commit hook. Task 2 must pass `lint:i18n` 22/22.
- **Story 10.6 R2-P10 lesson:** dead i18n keys under `epr.filing` were removed during a cleanup pass (exportTaxNumberLabel). Do not leave any temporary / unused i18n keys behind in this story — add exactly the six keys listed in ACs #8, #9, #13, #16 and no others.
- **Story 10.7 E2E skip pattern:** when a preconditional data state (empty registry, ALAP tier) cannot be guaranteed by the test seed, use `test.skip(true, '<reason>')`. Do NOT hard-fail. Reference: `empty-registry-onboarding.e2e.ts:28–31, 35–39, 46–50`.

### Testing Standards

- **vitest:** tests are co-located with the component (`<Component>.spec.ts` next to `<Component>.vue`). Pattern: `describe` per logical area; `it` per behavior. Mock stores with the `vi.mock('pinia', …)` pattern at file top (see `AppSidebar.spec.ts:18–34`).
- **Playwright:** specs live in `frontend/e2e/`. Use `loginAsTestUser(page)` from `./auth.setup` in `beforeEach`. Use `test.skip(true, reason)` for missing-precondition cases — never hard-fail. Viewport defaults to 1280×800; resize per test when covering mobile.
- **No new backend tests, no new ArchUnit tests.** (Task 10 still runs ArchUnit as a sanity check; it must stay green.)
- **Coverage target:** ~8–10 new vitest cases (specs across 4 files) + 3 new Playwright scenarios. Previous stories' baseline is ~844 vitest; this story should land at ~852–854.

### Anti-Pattern Prevention

- **DO NOT** add a `v-if="isAdmin"` or role-specific guard to the sidebar nav entry. Role restriction is covered by backend `@TierRequired(PRO_EPR)` + `@PreAuthorize`; the frontend shows/hides based on **tier** (`hasProEpr`), not role. GUEST users never reach an authenticated sidebar (middleware redirects).
- **DO NOT** introduce a new Pinia store for "nav items config" — the existing computed pattern in `AppSidebar.vue` is idiomatic and survives tree-shaking. A store would be over-engineering for a 1-line change.
- **DO NOT** write a `resolveNavLabel()` helper that centralizes the `common.nav.*` namespace — the existing `$t(\`common.nav.${item.key}\`)` template expression handles this directly in both components.
- **DO NOT** route the filing CTA through any "is feature-flagged" check — PRO_EPR is both the tier gate and the feature gate; there is no separate Growth-Book-style flag for filing.
- **DO NOT** add a "visited" state / badge / indicator ("new!") to the nav entry. The gap being closed is functional discoverability, not marketing. Epic 11 UX polish can revisit.
- **DO NOT** modify `AppBreadcrumb.vue`'s `OBSOLETE_PARENT_SEGMENTS` contents. The set still needs `'epr'` because `/epr` index page still does not exist. Only the comment needs updating (AC #17).
- **DO NOT** skip the `onOpenRegistry` existing query-param branching (lines 186–198) when copying the pattern for `onOpenFiling`. `onOpenFiling` ALWAYS routes to `/epr/filing` (no query params), because the filing page does not accept a filter query string from the bootstrap completion state.
- **DO NOT** fix the pre-existing mobile-drawer-missing-registry gap (AC #7 note). It is deferred to Epic 11 per CP-6.

### Project Structure Notes

- Component co-location is enforced: each `.vue` has its `.spec.ts` sibling. See `frontend/app/components/Common/` and `frontend/app/components/Registry/`.
- i18n files under `frontend/app/i18n/{hu,en}/` are flat per-namespace (`common.json`, `registry.json`, `epr.json`, etc.). Never mix namespaces — use the right file for the right key path.
- E2E specs under `frontend/e2e/` run against a live backend (via `SPRING_PROFILES_ACTIVE=test`) + a live Nuxt dev server. `playwright.config.ts` owns the orchestration; new specs are picked up automatically on file creation.
- PrimeVue component imports: named imports from `primevue/<component>` — see `registry/index.vue:1–7` and `InvoiceBootstrapDialog.vue:2–5` for the pattern. Do not import from the PrimeVue Nuxt module root.

### Acceptance Bar (what "done" looks like)

- Sign in as the demo accountant; sidebar "Negyedéves bejelentés" entry visible under Nyilvántartás; click → land on `/epr/filing`.
- Same path via mobile drawer on a 375-width viewport.
- On `/registry` page header, "Negyedéves bejelentés" outlined button visible when ≥1 product has components; click → `/epr/filing`.
- Run `POST /api/v1/registry/bootstrap-from-invoices` → wait for `COMPLETED` with ≥1 created product and 0 unresolved → "Bejelentés megnyitása" secondary CTA appears next to "Megnyitás"; click → `/epr/filing`.
- ALAP-tier user: sidebar and drawer do NOT show the eprFiling entry.
- All vitest + Playwright + lint:i18n + lint + tsc green.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-21.md — CP-6 AC draft §4.1 and §4.2]
- [Source: _bmad-output/planning-artifacts/epics.md#Story-10.10 (lines 1183–1209)]
- [Source: _bmad-output/implementation-artifacts/10-1-registry-schema-menu-restructure-and-tx-pool-refactor.md — Part C "Menu Restructure" (Tasks 7–10)]
- [Source: _bmad-output/implementation-artifacts/10-9-submission-history.md — pattern continuity: done-phase footer, i18n alphabetical, T6 hook, co-located specs]
- [Source: frontend/app/components/Common/AppSidebar.vue:143–155 — mainNavItems computed anchor]
- [Source: frontend/app/components/Common/AppMobileDrawer.vue:120–124 — mainNavItems const (plain) needing computed conversion]
- [Source: frontend/app/components/Common/AppBreadcrumb.vue:76–82 — OBSOLETE_PARENT_SEGMENTS JSDoc anchor]
- [Source: frontend/app/pages/registry/index.vue:157–164 — page header flex anchor; line 23 — registryCompleteness instantiation]
- [Source: frontend/app/components/Registry/InvoiceBootstrapDialog.vue:186–198 — onOpenRegistry pattern; 369–377 — done-phase footer anchor]
- [Source: frontend/app/composables/auth/useTierGate.ts — hasAccess reactive]
- [Source: frontend/e2e/auth.setup.ts — loginAsTestUser helper]
- [Source: frontend/e2e/empty-registry-onboarding.e2e.ts — skip-when-precondition-missing pattern]
- [Source: memory/project_epic_10_audit_architecture_decision.md — irrelevant here (no audit writes)]
- [Source: memory/feedback_test_timeout_values.md — frontend vitest ~6s; backend targeted ~90s; ArchUnit ~30s]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context)

### Debug Log References

### Completion Notes List

### AC-to-Task Walkthrough (T1)

Per Retro T1 (HIGH) — filed before any production code. Every AC #1–#24 mapped to task number(s):

| AC | Covered by Task | Notes |
|---|---|---|
| #1 AppSidebar mainNavItems gains eprFiling entry after registry | Task 3 | Uses existing spread-conditional pattern (hasProEpr). |
| #2 Template unchanged; NuxtLink + tooltip machinery reused | Task 3 | Verified: no template change needed. |
| #3 data-testid `nav-item-eprFiling`, icon `pi-file` | Task 3 | Existing convention. |
| #4 AppMobileDrawer mainNavItems gains eprFiling, mirrors sidebar tier-gating | Task 4 | const→computed conversion. |
| #5 Mobile drawer imports `useTierGate('PRO_EPR')` | Task 4 | Consistent with rest of codebase. |
| #6 data-testid `drawer-nav-eprFiling`, template loop unchanged | Task 4 | Existing convention. |
| #7 Scope note — do NOT add `registry` to drawer | Task 4 | Non-goal: pre-existing gap deferred to Epic 11. |
| #8 HU common.nav.eprFiling alphabetically between dashboard and registry | Task 2 | T6 hook enforces. |
| #9 EN common.nav.eprFiling at same position | Task 2 | T6 hook enforces. |
| #10 lint:i18n 22/22 green | Task 2 + Task 10 | Verification step. |
| #11 Registry header secondary outlined Button left of "Új termék" | Task 5 | data-testid `header-cta-filing`. |
| #12 CTA visible only when productsWithComponents > 0 | Task 5 | v-if guard. |
| #13 i18n `registry.actions.openFiling` (HU+EN) | Task 2 + Task 5 | Under existing `actions` nested object. |
| #14 InvoiceBootstrapDialog done-phase secondary outlined Button next to "Megnyitás" | Task 6 | data-testid `bootstrap-cta-filing`. |
| #15 CTA visible only on COMPLETED && createdProducts > 0 && unresolvedPairs === 0 | Task 6 | Single v-if with three conjuncts. |
| #16 i18n `registry.bootstrap.completion.openFiling` (HU+EN) | Task 2 + Task 6 | Under existing `bootstrap.completion` nested object. |
| #17 AppBreadcrumb JSDoc comment update (no code change) | Task 7 | Non-functional. |
| #18 deferred-work.md D6 verification-only | Task 9 | Already updated in CP-6 authoring. |
| #19 AppSidebar.spec.ts extensions (5 bullet points) | Task 3 | Length 4→5, ordering, mount smoke, tier-gate pair. |
| #20 AppMobileDrawer.spec.ts extensions (5 bullet points) | Task 4 | Length 3→4, i18n loop, new tests, tier-gate describe. |
| #21 registry/index.spec.ts +2 tests (productsWithComponents > 0 / === 0) | Task 5 | Mocks useRegistryCompleteness. |
| #22 InvoiceBootstrapDialog.spec.ts +3 tests (visible, unresolvedPairs > 0, non-success parameterized) | Task 6 | it.each for the four status scenarios. |
| #23 New Playwright spec nav-to-filing.e2e.ts (3 scenarios) | Task 8 | Sidebar golden, mobile drawer golden, ALAP tier skip-guarded. |
| #24 Existing Playwright specs continue to pass | Task 8 + Task 10 | Sanity: pre-existing specs use page.goto directly. |
| #25 Process gate (this walkthrough) | Task 1 | This table. |

**No uncovered ACs.** Proceeding to Task 2.

### Implementation Summary

- **Task 2 — i18n keys (6 keys):** Added `common.nav.eprFiling` (HU/EN), `registry.actions.openFiling` (HU/EN), `registry.bootstrap.completion.openFiling` (HU/EN). `lint:i18n` 22/22 green — T6 alphabetical-ordering hook satisfied.
- **Task 3 — AppSidebar.vue:** Appended `eprFiling` entry (PRO_EPR tier-gated) to `mainNavItems` computed after the existing `registry` spread. Extended `AppSidebar.spec.ts`: length assertion 4→5, key-ordering, tier-gate describe renamed to cover registry + eprFiling (4 new tier tests), mount smoke test asserts `nav-item-eprFiling`. 38/38 spec tests green.
- **Task 4 — AppMobileDrawer.vue:** Imported `useTierGate`, converted `mainNavItems` from plain const → `computed`, added `eprFiling` entry gated on `hasProEpr`. Pre-existing registry-missing-from-drawer gap explicitly left untouched (CP-6 §3). `AppMobileDrawer.spec.ts`: added hoisted `authState.tier` toggle so the same file can exercise PRO_EPR/PRO/ALAP/null paths; new tier-gate describe with 4 mount-based tests; length 3→4; mount smoke test asserts `drawer-nav-eprFiling`. Overrode the vitest.setup.ts `useAuthStore` global stub so the auto-imported `useTierGate` composable picks up the same mock. 27/27 green.
- **Task 5 — Registry page header CTA:** Wrapped the existing "Új termék" button + new secondary outlined "Negyedéves bejelentés" button in a flex container; `v-if` guards on `registryCompleteness.productsWithComponents.value > 0`. `registry/index.spec.ts`: added `useRegistryCompleteness` mock with mutable refs plus `useTierGate` mock, new describe mounts the page with minimal PrimeVue stubs that preserve `data-testid` for visibility assertions; 2 new tests (visible when > 0, hidden when === 0 + click routes to /epr/filing). 11/11 green.
- **Task 6 — InvoiceBootstrapDialog CTA:** Added `onOpenFiling()` (onClose + push `/epr/filing`, no query-param branching); new secondary outlined button in done-phase footer with `v-if` on the three-conjunct expression. Spec: pure-function visibility tests mirroring the v-if expression — consistent with the file's existing function-level style. `it.each` parameterizes the four non-success/empty-period scenarios. 15/15 green.
- **Task 7 — AppBreadcrumb JSDoc:** Refreshed the JSDoc above `OBSOLETE_PARENT_SEGMENTS` to reflect Story 10.10 reality (sidebar routes directly to `/epr/filing`, no `/epr` index page exists or is planned, non-clickable parent treatment retained). No code change; `OBSOLETE_PARENT_SEGMENTS` set unchanged. Breadcrumb spec 20/20 green.
- **Task 8 — Playwright E2E:** New `nav-to-filing.e2e.ts` with 3 tests (sidebar → filing, mobile drawer → filing, ALAP exclusion). Deviation from AC #23 literal wording: the E2E seed tenant is ALAP (verified in `R__e2e_test_data.sql:15–17`), not PRO_EPR as the story assumed. Following the Epic 10 skip-when-precondition pattern (filing-workflow.e2e.ts:23–26, empty-registry-onboarding.e2e.ts:28–31), both golden paths are skip-guarded when the tier gate hides the nav entry; the ALAP-exclusion test becomes the affirmative assertion that actually runs against the current seed. `vi`-mocked tier-gate paths in vitest cover the PRO_EPR visibility case (8 new tests across AppSidebar + AppMobileDrawer specs).
- **Task 9 — deferred-work.md D6:** Verified line 88 contains `**RESOLVED STALE (2026-04-21, CP-6):**` and references Story 10.10 — no restoration needed.

### Verification

- `npm run -w frontend test`: **862/862** green (baseline 844 + 18 new tests across 4 specs; exceeds the 8–10 estimate because tier-gate paths were expanded to 4 mount-based tests in the mobile drawer).
- `npx tsc --noEmit`: clean.
- `npm run -w frontend lint`: 0 errors, 819 warnings (all pre-existing — no new warnings from Story 10.10 files).
- `npm run -w frontend lint:i18n`: **22/22** OK — keys alphabetical at every level.
- `./gradlew test --tests "hu.riskguard.architecture.*"` (backend): BUILD SUCCESSFUL (UP-TO-DATE; backend untouched).
- `npx playwright test` (live backend + Nuxt): **6 passed / 12 skipped / 0 failed** (+1 passed, +2 skipped from `nav-to-filing.e2e.ts`; no pre-existing E2E regressions).

### Deviations from Story Spec

1. **AC #23 PRO_EPR assumption:** The story AC authored the golden paths assuming the E2E seed tenant is PRO_EPR. Actual seed (`R__e2e_test_data.sql:15–17`) provisions the tenant at **ALAP** tier. Resolution: skip-guard both golden paths when the nav entry is not visible (matches `filing-workflow.e2e.ts:23–26` pattern), and flip the ALAP-exclusion test from skip-always to a live negative assertion. Net effect: visibility under PRO_EPR is validated by vitest tier-gate mount tests; visibility hidden under ALAP is validated by the live E2E. An optional follow-up (Epic 11) would be to flip the E2E seed tenant to PRO_EPR or seed an additional PRO_EPR user so the golden paths can be run end-to-end.

### File List

**Modified:**
- `frontend/app/components/Common/AppSidebar.vue` — +2 lines: `eprFiling` entry appended to `mainNavItems` computed under `hasProEpr` gate.
- `frontend/app/components/Common/AppSidebar.spec.ts` — extended `mainNavItems` fixture, renamed key-ordering test, renamed tier-gate describe, added eprFiling tier-gate tests, added mount-smoke assertion.
- `frontend/app/components/Common/AppMobileDrawer.vue` — imported `useTierGate`, converted `mainNavItems` const → computed, added `eprFiling` entry under `hasProEpr` gate.
- `frontend/app/components/Common/AppMobileDrawer.spec.ts` — added `authState` hoisted toggle, overrode `useAuthStore` global stub, extended fixture, added tier-gate describe with 4 mount-based tests, added mount-smoke assertion.
- `frontend/app/components/Common/AppBreadcrumb.vue` — refreshed JSDoc above `OBSOLETE_PARENT_SEGMENTS` to reflect Story 10.10 state.
- `frontend/app/pages/registry/index.vue` — wrapped header buttons in flex container; added secondary outlined "Negyedéves bejelentés" Button with `v-if` on `productsWithComponents > 0`.
- `frontend/app/pages/registry/index.spec.ts` — added `useRegistryCompleteness` and `useTierGate` mocks, added filing-CTA visibility describe with 2 mount-based tests.
- `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` — added `onOpenFiling()`, added secondary outlined "Bejelentés megnyitása" Button to done-phase footer with three-conjunct `v-if`.
- `frontend/app/components/Registry/InvoiceBootstrapDialog.spec.ts` — added filing-CTA visibility describe with 3 tests (visible, unresolvedPairs > 0, `it.each` over 4 non-success/empty-period scenarios).
- `frontend/app/i18n/hu/common.json` — added `common.nav.eprFiling`.
- `frontend/app/i18n/en/common.json` — added `common.nav.eprFiling`.
- `frontend/app/i18n/hu/registry.json` — added `registry.actions.openFiling` and `registry.bootstrap.completion.openFiling`.
- `frontend/app/i18n/en/registry.json` — added `registry.actions.openFiling` and `registry.bootstrap.completion.openFiling`.
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story 10-10 status ready-for-dev → in-progress → review.
- `_bmad-output/implementation-artifacts/10-10-epr-filing-navigation-and-discoverability.md` — story file: Status, task checkboxes, Completion Notes, File List, Change Log.

**New:**
- `frontend/e2e/nav-to-filing.e2e.ts` — 3 E2E tests (sidebar → filing golden, mobile drawer → filing golden, ALAP-tier exclusion).

**Verified (no change):**
- `_bmad-output/implementation-artifacts/deferred-work.md:88` — D6 note already contains `**RESOLVED STALE (2026-04-21, CP-6):**` and references Story 10.10 (Task 9 verification-only AC).

### Change Log

- **2026-04-21 (Story 10.10 initial implementation):** Added PRO_EPR-gated "Negyedéves bejelentés" entry to desktop sidebar and mobile drawer, secondary outlined CTAs on the Registry page header (guarded by `productsWithComponents > 0`) and in the InvoiceBootstrapDialog done-phase footer (guarded by `COMPLETED && createdProducts > 0 && unresolvedPairs === 0`), 6 new i18n keys across common.nav and registry.{actions,bootstrap.completion}, AppBreadcrumb JSDoc refresh, and a new `nav-to-filing.e2e.ts` Playwright spec. AppMobileDrawer `mainNavItems` converted from plain const to computed; `useTierGate('PRO_EPR')` imported and read as `hasProEpr`. Vitest baseline 844 → 862 (+18 tests across AppSidebar.spec, AppMobileDrawer.spec, registry/index.spec, InvoiceBootstrapDialog.spec). Playwright baseline 5 passed/10 skipped → 6 passed/12 skipped. Retro T1 AC-to-task walkthrough filed before production code. Retro T6 alphabetical i18n ordering satisfied (lint:i18n 22/22). No backend, DB migration, or ArchUnit rule changes — architectural compliance confirmed (ArchUnit BUILD SUCCESSFUL UP-TO-DATE).
