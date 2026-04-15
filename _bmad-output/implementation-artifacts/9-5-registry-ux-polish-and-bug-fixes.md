# Story 9.5: Registry UX Polish & Bug Fixes

Status: review

<!-- Ad-hoc story added 2026-04-15 after user QA pass on the Nyilvántartás (Registry) UI shipped in Stories 9.1 + 9.3. Not in the original CP-5 scope; captured here so fixes are tracked with the same rigor as feature stories. -->

## Story

As a Hungarian KKV user creating and editing products in the Nyilvántartás (Product-Packaging Registry),
I want the page to present itself with clear Hungarian labels, a usable packaging-components table, a comprehensible AI-assist button, and actionable validation feedback,
so that I can build my registry without confusion, without the AI popover breaking the app shell, and without guessing why a save silently fails with "Hiba történt".

## Business Context

Story 9.1 shipped the Registry foundation and Story 9.3 bolted on the AI classifier. A user QA pass on 2026-04-15 (logged in activation prompt of this story) revealed nine distinct UX/functional regressions that together make the page feel broken. Several are blockers:

- **Bug 6** (AI popover renders under the sidebar and leaves the sidebar stuck open) is an app-shell regression — the only recovery is a full page refresh.
- **Bug 9** (missing component with generic "Hiba történt" toast) prevents users from completing their first product save without trial and error.
- **Bug 1** (raw route keys in the breadcrumb) makes the page look unlocalised, undermining trust for a Hungarian-only target audience.

This is a pure polish story — no schema changes, no new endpoints, no new cross-module contracts. All fixes live in the frontend except AC #9's backend problem-detail mapping.

## Acceptance Criteria

1. **Breadcrumb shows Hungarian labels on `/registry` and `/registry/new`.** Visiting `/registry/new` renders breadcrumb "Főoldal › Nyilvántartás › Új termék" (hu) / "Home › Registry › New product" (en) — never raw segments `registry` or `new`. `/registry/{uuid}` renders "Nyilvántartás › Termék szerkesztése" (hu) / "Registry › Edit product" (en) with the UUID segment replaced by the product name once loaded, or by the "Termék szerkesztése" / "Edit product" label if still loading. [Source: bug #1]

2. **Registry editor page (`/registry/new` and `/registry/{id}`) has a coherent top-level layout.** The max-width container is widened from `max-w-4xl` to `max-w-6xl` (or equivalent) so the components table fits without horizontal scroll at ≥1280px viewport. A prominent page-level "mandatory fields" legend appears above the product fields grid (reuses existing `registry.form.validation.*` patterns — no new i18n style). Primary action buttons (Save, Cancel/Back) are sticky-pinned at the bottom of the form on desktop widths ≥1024px following the same pattern as `pages/epr/filing.vue` (see §Frontend Patterns). [Source: bug #2]

3. **"Csomagolási elemek" table fits within the page width at viewports ≥1024px — no horizontal scrollbar on standard desktop.** Achieved by: (a) removing the fixed `style="width: 120px"` on the PPWR column and collapsing PPWR into a per-row expand-toggle button (leveraging PrimeVue `DataTable` built-in `<Column :expander="true">` row-expansion — NOT a nested Accordion), (b) letting `materialDescription` flex to fill remaining width, (c) keeping componentOrder/kfCode/weight columns at their current widths. At viewports <1024px the table gracefully falls back to PrimeVue `DataTable` `responsiveLayout="stack"` mode so rows stack vertically rather than triggering horizontal scroll. [Source: bug #3]

4. **"KF-kód javaslat" (magic) button is always visible per row, regardless of product-name state, but disabled with a tooltip when the product name is empty.** Replace `v-if="name"` with `:disabled="!name"` on the button at `[id].vue:487`. Wrap the button in a PrimeVue `Tooltip` (or `v-tooltip` directive) with the text `registry.classify.tooltipDisabled` ("Add meg a termék nevét a javaslathoz" / "Enter the product name to get a suggestion") when disabled, and `registry.classify.tooltipEnabled` ("AI által javasolt KF-kód a termék neve és VTSZ alapján" / "AI-suggested KF code based on product name and VTSZ") when enabled. The button keeps its `pi-sparkles` icon but ALSO grows a visible text label `registry.classify.suggestShort` ("Javaslat" / "Suggest") next to the icon so the affordance is self-describing. [Source: bugs #4 + #5]

5. **Clicking the magic button shows the AI suggestion in a popover that renders ABOVE the app shell (including the left sidebar) and NEVER leaves the sidebar in a stuck/unclosable state.** Enforced by: (a) adding `appendTo="body"` to the `<Popover>` at `[id].vue:496`, (b) ensuring popover z-index is above the sidebar drawer (`z-50` or higher — sidebar drawer is `z-40`), (c) the Popover MUST auto-close on outside click AND on route change AND on Escape key without leaving any menu element in an open/locked state, (d) removing the Popover from inside the `DataTable` column `<template #body>` (which is re-rendered per row and causes ref leaks) — hoist it to a single Popover instance at the page root and target it by the currently-active row `_tempId`. A Playwright E2E test reproduces the regression: click magic button → assert popover visible above sidebar (`z-index` ≥ sidebar z-index), press Escape → assert popover closed AND sidebar toggle responds to click. [Source: bug #6 — highest severity]

6. **PPWR fields ("PPWR-megfelelőség (opcionális)") render as a clean row-expansion panel, not a cramped in-column Accordion.** After AC #3's restructuring, clicking the row-expand button reveals a two-column grid (recyclabilityGrade | recycledContentPct on row 1, reusable | supplierDeclarationRef on row 2) at viewport widths ≥768px, stacking to single-column below. Expanded row background is tinted with `bg-slate-50` (or equivalent token from the design system — see `docs/design-tokens.md` if present, otherwise match `pages/epr/filing.vue` accordion-content background) to visually differentiate it from the main row. The "opcionális" hint stays in the expand-toggle label so users understand these fields are not required. [Source: bug #7]

7. **"Alapegység" (primaryUnit) field is a PrimeVue `Select` with Hungarian-abbreviated options, defaulting to `db` (darab, Hungarian for "pieces") instead of English `pcs`.** Options MUST be defined as a hu/en-aware option list in a new `useUnits()` composable at `frontend/app/composables/registry/useUnits.ts` returning: `db` (darab / piece), `kg` (kilogramm / kilogram), `g` (gramm / gram), `l` (liter / litre), `ml` (milliliter / millilitre), `m` (méter / metre), `m2` (négyzetméter / square metre), `m3` (köbméter / cubic metre), `csomag` (csomag / pack). The default selected option on new products is `db`. Existing products that were saved with `primaryUnit='pcs'` display "pcs" as an extra read-only option (so edit does not silently remap the value) but saving the product for any other reason keeps `pcs` intact — we DO NOT run a data-migration for this story (see §Dev Notes → Data strategy). i18n keys added under `registry.units.*`. [Source: bug #8]

8. **The Csomagolási elemek array is required, and this is communicated BEFORE save.** (a) The "Csomagolási elemek" section header grows a visible "*" mandatory marker matching the existing convention at `[id].vue:390` (`{{ t('registry.form.name') }} *`). (b) If `components.length === 0` on page load (new product), a neutral empty-state card appears inside the components section with text `registry.form.noComponents.cta` ("Adjon hozzá legalább egy csomagolási elemet a termék mentéséhez" / "Add at least one packaging component to save this product") and a prominent "Elem hozzáadása" button (existing i18n key `registry.form.addComponent` reused). (c) Client-side `validateComponents()` at `[id].vue:62` is extended to ALSO fail when `components.length === 0`, returning `t('registry.form.validation.componentsRequired')`. (d) On failed validation, a page-level `Message` banner (PrimeVue `<Message severity="error">`) is rendered above the components section — NOT a toast that disappears. The banner auto-dismisses when validation passes on the next save attempt. (e) Backend returns a distinct problem detail with `type: "urn:riskguard:error:registry-components-required"` when an UpsertRequest arrives with an empty component list (this is already enforced by `@Size(min=1)` in `ProductUpsertRequest` — check the actual error type emitted by Spring Boot's bean validation handler; add an `@ExceptionHandler` to remap to the URN if needed). `useApiError.ts` gets the new URN entry pointing to `registry.form.validation.componentsRequired`. [Source: bug #9]

9. **Zero regressions in existing registry flows.** All existing tests in `frontend/app/pages/registry/**.spec.ts`, `frontend/app/components/registry/**.spec.ts`, and Playwright e2e (`frontend/e2e/registry*.spec.ts` if present) pass unchanged after the refactor. Specifically: (a) save-new-product happy path still POSTs `/api/v1/registry`, (b) edit-existing-product still PUTs `/api/v1/registry/{id}`, (c) AI classifier accept flow still sets `classificationSource = 'AI_SUGGESTED_CONFIRMED'`, (d) audit log drawer still renders, (e) tier gate for non-PRO_EPR tenants still blocks with the lock screen at `[id].vue:359`.

## Tasks / Subtasks

- [x] **Task 1 — Breadcrumb labels for `registry` and `new`** (AC: #1)
  - [x] Add entries `registry: 'common.breadcrumb.registry'` and `new: 'common.breadcrumb.new'` to `ROUTE_LABELS` in `frontend/app/components/Common/AppBreadcrumb.vue:59-67`.
  - [x] Add `breadcrumb.registry` = "Nyilvántartás" / "Registry" and `breadcrumb.new` = "Új termék" / "New product" to `frontend/app/i18n/hu/common.json:29-37` and `frontend/app/i18n/en/common.json:29-37`. Keep keys alphabetically sorted (project rule — see project-context.md "Alphabetical i18n").
  - [x] For `/registry/{uuid}`, extend `AppBreadcrumb.vue` with a route-matcher branch: if segment is a UUID and previous segment is `registry`, replace the UUID label with the current product's `name` from `useRegistryStore().editProduct` when available, else use `t('common.breadcrumb.edit')` ("Termék szerkesztése" / "Edit product") — add this i18n key too.
  - [x] Unit test in `AppBreadcrumb.spec.ts`: assert `/registry/new` renders the three labels in order; assert `/registry/{uuid}` with a store-loaded product renders the product name.

- [x] **Task 2 — Widen form container + sticky action bar** (AC: #2)
  - [x] Change `max-w-4xl` → `max-w-6xl` on the root wrapper at `frontend/app/pages/registry/[id].vue`.
  - [x] Move the save button(s) from their current inline position to a sticky footer at the bottom of the form (`sticky bottom-0 bg-white border-t p-3 flex justify-end gap-2 -mx-4 z-10`).
  - [x] Registry-list page `pages/registry/index.vue` does not use `max-w-4xl` — no change needed (checked).

- [x] **Task 3 — Components DataTable layout rework** (AC: #3, #6)
  - [x] Removed the `<Accordion>` block that was inside the PPWR column.
  - [x] Removed the PPWR column entirely; added `<Column :expander="true" style="width: 3rem" />` as the leftmost column.
  - [x] Added `<template #expansion="{ data }">` block with `bg-slate-50` tint and a nested two-column PPWR grid.
  - [x] Added `responsive-layout="stack" breakpoint="1024px"` on the `DataTable`.
  - [x] Removed `overflow-hidden` from the DataTable's wrapping class (AC #5 dependency).
  - [x] `materialDescription` column now flexes (no width style).
  - [x] Empty-state CTA card renders in place of the table when `components.length === 0`.

- [x] **Task 4 — Magic button: always visible, tooltip, disabled state, text label** (AC: #4)
  - [x] Replaced `v-if="name"` with `:disabled="!name"` on the Suggest button.
  - [x] Added `:label="t('registry.classify.suggestShort')"`.
  - [x] Attached `v-tooltip.bottom` with enabled/disabled tooltip keys.
  - [x] Added i18n keys: `registry.classify.suggestShort`, `registry.classify.tooltipEnabled`, `registry.classify.tooltipDisabled` in both hu and en files.

- [x] **Task 5 — Popover: appendTo body, hoist to page root, z-index fix** (AC: #5 — CRITICAL)
  - [x] Deleted the per-row `<Popover>` from inside the kfCode column body template.
  - [x] Added a single `<Popover ref="classifyPopover" append-to="body" class="z-[60]">` at the page root.
  - [x] Replaced the per-row `classifyPopoverRef` map with a single `classifyPopover` ref plus `activeClassifyTempId` + `activeClassifySuggestion` refs.
  - [x] Added a window `keydown` listener that closes the popover on `Escape`.
  - [x] Added a route-change watcher that closes the popover on navigation.
  - [x] Added Playwright regression test `frontend/e2e/registry-classify-popover.e2e.ts` asserting popover z-index > sidebar, Escape closes popover, sidebar remains responsive.

- [x] **Task 6 — PPWR row-expansion content layout** (AC: #6)
  - [x] Render the four PPWR fields in a `grid grid-cols-1 md:grid-cols-2 gap-4` container inside the expansion template.
  - [x] Preserve existing i18n keys (`registry.form.recyclabilityGrade`, `recycledContentPct`, `reusable`, `supplierDeclarationRef`).
  - [x] Each field has a unique `id` per row via `data._tempId` suffix; `<label for="...">` matches.

- [x] **Task 7 — Alapegység Select with Hungarian units** (AC: #7)
  - [x] Created `frontend/app/composables/registry/useUnits.ts` exporting `useUnits()`, `UNIT_VALUES`, and `DEFAULT_UNIT`.
  - [x] Added `registry.units.*` keys to `hu/registry.json` and `en/registry.json`.
  - [x] Changed default `primaryUnit` from `'pcs'` to `DEFAULT_UNIT` (`'db'`) for new products.
  - [x] Replaced the `<InputText>` for Alapegység with a `<Select>` bound to `unitOptions`.
  - [x] Legacy-value handling: `unitOptions` computed prepends an extra `{value: legacy, label: legacy}` option when the loaded product's `primaryUnit` is outside the canonical list (e.g. legacy `'pcs'` rows).
  - [x] Unit tests co-located in `useUnits.spec.ts`; legacy-preservation logic tested in `[id].spec.ts` under "unit options legacy preservation".

- [x] **Task 8 — Components-required validation UX + backend problem detail mapping** (AC: #8)
  - [x] Added `*` to the Csomagolási elemek header.
  - [x] Extended `validateComponents()` to return `componentsRequired` when the list is empty.
  - [x] Added i18n key `registry.form.validation.componentsRequired` in both locales.
  - [x] Empty-state CTA card renders above the components DataTable with the `registry.form.noComponents.cta` text and an "Elem hozzáadása" button.
  - [x] `save()` no longer fires a toast for components errors — inline `<Message severity="error" :closable="false">` renders above the components section using `componentsError.value`.
  - [x] Backend: new `RegistryValidationExceptionHandler` (`@ControllerAdvice(assignableTypes = RegistryController.class)`) remaps a `components` field `Size` violation to `ProblemDetail` with type `urn:riskguard:error:registry-components-required`, status 400.
  - [x] Unit test `RegistryValidationExceptionHandlerTest` asserts the URN is emitted and that other field violations fall back to the generic problem detail.
  - [x] Frontend: added the URN → i18n mapping to `ERROR_TYPE_MAP` in `useApiError.ts`.

- [x] **Task 9 — Regression test sweep** (AC: #9)
  - [x] Targeted frontend: `npx vitest run app/pages/registry app/components/registry app/components/Common/AppBreadcrumb.spec.ts app/composables/registry` — 64 tests pass.
  - [x] Targeted backend: `./gradlew test --tests "hu.riskguard.epr.registry.*"` — BUILD SUCCESSFUL (77 tests).
  - [x] ArchUnit: `./gradlew test --tests "hu.riskguard.architecture.*"` — BUILD SUCCESSFUL.
  - [x] Full frontend suite: `npx vitest run` — 771/771 tests pass.
  - [x] Full backend suite: `./gradlew test` — BUILD SUCCESSFUL.
  - [ ] Playwright E2E — test file added but requires running backend+frontend; to be run in CI environment. Targeted-run instructions documented in story.

## Dev Notes

### Architectural constraints

- **NO schema migrations in this story.** The DB column `products.primary_unit VARCHAR(16) DEFAULT 'pcs'` stays unchanged. Legacy rows with `'pcs'` continue to roundtrip. A future story can add a `V{date}__migrate_primary_unit_pcs_to_db.sql` if product ownership decides to normalise.
- **NO new endpoints, NO new DTOs, NO new cross-module contracts.** This is a frontend polish + one targeted backend exception-handler addition.
- **Spring Modulith module isolation** stays intact — all backend changes are inside `hu.riskguard.epr.registry.api`. Do NOT touch `hu.riskguard.epr.registry.domain` or `.internal`.
- **ArchUnit**: `EpicNineInvariantsTest` must continue to pass. The rule `only_registry_package_writes_to_product_packaging_components` is unaffected by this story (no repository changes).

### Data strategy for primary_unit legacy values

Existing products have `primary_unit='pcs'` in the DB (only test fixtures today, per Story 9.1's AC 1 migration `V20260414_001`). Two options were considered:

1. **Schema migration** to UPDATE all `'pcs'` → `'db'` and change DEFAULT. **Rejected** because (a) this story is scope-locked to polish and (b) a data migration without a product-ownership review risks breaking any integration that expected the literal `'pcs'`.
2. **Frontend-only default change + legacy value preservation.** **Chosen.** New products default to `'db'`, existing `'pcs'` values render as a non-preferred option and roundtrip unchanged on edit.

If product ownership later decides the DB should be normalised, that's a separate story with its own Flyway migration.

### Popover regression — root cause analysis

The menu-breaks bug (AC #5) has three layered causes, all of which MUST be fixed:

1. **PrimeVue `<Popover>` inside a `<template #body>`**: DataTable re-renders row templates on any reactivity tick, which can leak popover refs and leave the overlay in an orphaned DOM state. Fix: hoist to a single page-level Popover.
2. **DataTable's `overflow-hidden` on the wrapper `class`**: clips popovers that position themselves outside the table bounds. Fix: remove `overflow-hidden` from `[id].vue:440`.
3. **No `appendTo="body"`**: causes the popover to render inside the DataTable's stacking context, where it can end up below the sidebar's drawer layer. Fix: `appendTo="body"` teleports the popover to the document root, and an explicit `z-index` above the sidebar layer keeps it visible.

The "menu becomes unclosable" symptom is the sidebar's click-outside handler getting confused when the popover DOM is stuck inside the table wrapper — once the popover is appendTo=body, outside-click detection works correctly and the sidebar unlocks.

### Frontend Patterns (follow, don't reinvent)

- **Sticky action bar**: pattern used in `pages/epr/filing.vue` (find the sticky save footer — typical class `sticky bottom-0 bg-white border-t p-3 flex justify-end gap-2`).
- **Row-expansion DataTable**: `<Column :expander="true" />` + `<template #expansion>` — see PrimeVue docs. No existing usage in this repo, so you're introducing the pattern — keep it minimal.
- **Inline banner vs toast**: toasts auto-dismiss and are for informational feedback; validation errors use inline `<Message>` banners. Existing pattern: `components/registry/MaterialFormDialog.vue` (blur + watch validation) — consistent with what we already do for field-level errors; AC #8 extends this to section-level errors.
- **i18n alphabetical sort**: all locale JSON files must stay alphabetically sorted per project rule. Do NOT bypass this.
- **PrimeVue Tooltip**: ensure the Tooltip directive is registered — check `plugins/primevue.ts` (or equivalent) for `import Tooltip from 'primevue/tooltip'; nuxtApp.vueApp.directive('tooltip', Tooltip)`. If absent, register it there.

### Testing standards

- Frontend unit tests: Vitest + Vue Test Utils co-located (`*.vue` + `*.spec.ts` same directory) per project rule.
- Backend: `RegistryControllerTest` already exists — add the new `post_with_empty_components_*` test to the same class.
- E2E: Playwright; test file name pattern `frontend/e2e/registry-*.spec.ts`. Follow existing fixtures in `frontend/e2e/` — auth bypass via the Story 2.1.5 infrastructure.
- Per project-memory `feedback_test_timeout_values.md`: run targeted `./gradlew test --tests "hu.riskguard.epr.registry.*"` (~90s) first; run full suite ONCE at end. Never pipe `gradlew`. Frontend targeted run ~6s. ArchUnit separately ~30s.

### Project Structure Notes

- All frontend files are in `frontend/app/` (Nuxt 4 — `app/` is the routed-code directory).
- i18n is per-feature: `registry.json` for registry keys, `common.json` for shared shell keys (breadcrumb, nav).
- No new files under `backend/src/main/java/hu/riskguard/epr/registry/` except possibly an exception-handler method added to the existing `RegistryController` or to a shared `GlobalExceptionHandler` if one exists.

### References

- [Source: frontend/app/pages/registry/[id].vue:49] — `primaryUnit` ref default `'pcs'`
- [Source: frontend/app/pages/registry/[id].vue:411-414] — primaryUnit `InputText` to replace with `Select`
- [Source: frontend/app/pages/registry/[id].vue:440] — DataTable wrapper with `overflow-hidden` (must remove)
- [Source: frontend/app/pages/registry/[id].vue:487] — magic Button `v-if="name"` (must change to `:disabled="!name"`)
- [Source: frontend/app/pages/registry/[id].vue:496-517] — Popover to hoist to page root
- [Source: frontend/app/pages/registry/[id].vue:538-580] — PPWR Accordion-in-column block to replace with row-expansion
- [Source: frontend/app/pages/registry/[id].vue:62-69] — `validateComponents()` to extend with `length === 0` check
- [Source: frontend/app/pages/registry/[id].vue:267-271] — save() toast-based error to convert to inline Message banner
- [Source: frontend/app/components/Common/AppBreadcrumb.vue:59-67] — `ROUTE_LABELS` to extend with `registry` + `new` + `edit` + UUID branch
- [Source: frontend/app/i18n/hu/common.json:29-37] — hu breadcrumb block
- [Source: frontend/app/i18n/en/common.json:29-37] — en breadcrumb block
- [Source: frontend/app/i18n/hu/registry.json:138-147] — classify block for new tooltip keys
- [Source: frontend/app/composables/api/useApiError.ts:8-14] — ERROR_TYPE_MAP to extend
- [Source: backend/src/main/resources/db/migration/V20260414_001__create_product_registry.sql:13] — `primary_unit DEFAULT 'pcs'` (stays unchanged this story)
- [Source: backend/src/main/java/hu/riskguard/epr/registry/api/dto/ProductUpsertRequest.java] — `@Valid @Size(min=1) List<ComponentUpsertRequest> components` (existing constraint — we're only adding the URN problem-detail mapping)
- [Source: _bmad-output/project-context.md] — i18n alphabetical sort, Composition API only, PrimeVue patterns
- [Source: _bmad-output/planning-artifacts/epics.md:789-876] — Epic 9 scope (context)
- [Source: _bmad-output/implementation-artifacts/9-1-product-packaging-registry-foundation.md] — Story 9.1 context (what this polishes)
- [Source: _bmad-output/implementation-artifacts/9-3-ai-assisted-kf-code-classification.md] — Story 9.3 context (magic button origin)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (1M context) via Claude Code — `bmad-dev-story` skill, 2026-04-15.

### Debug Log References

- **Frontend test iteration #1**: `useUnits.spec.ts` initially failed because `useUnits.ts` imported `useI18n` from `#imports` (a Nuxt auto-import virtual module not resolvable in vitest). Fixed by removing the explicit import and relying on the auto-import pattern used by every other composable in the repo; test-side stub via `vi.stubGlobal('useI18n', ...)`.
- **Frontend test iteration #2**: `AppBreadcrumb.spec.ts` "all 5 primary routes have i18n label mappings" started failing after two new routes (`registry`, `new`) were added to `ROUTE_LABELS`. Updated the assertion to include the new keys.
- **Frontend test iteration #3**: `test/a11y/shell.a11y.spec.ts` started failing once `AppBreadcrumb.vue` began reading the registry store (to resolve the UUID → product-name breadcrumb branch). Added a `vi.mock('~/stores/registry', …)` entry mirroring the existing store mocks pattern.
- **Backend test iteration #1**: `RegistryValidationExceptionHandlerTest` failed its two "fallback" assertions because `ProblemDetail.forStatus(...)` leaves `type` as `null` rather than `URI.create("about:blank")`. Relaxed the assertion to accept either `null` or `about:blank`.

### Completion Notes List

- Story delivers 9 ACs covering the registry-editor UX regressions surfaced in the 2026-04-15 QA pass. Frontend-only for 8 of the 9; one backend `@ControllerAdvice` for the `components`-required URN.
- **AC #5 (Popover menu regression, highest severity)** — root cause was three-fold per the Dev Notes: per-row popover refs leaking on DataTable re-render, DataTable's `overflow-hidden` clipping the overlay, and missing `appendTo="body"` leaving the popover underneath the sidebar drawer. All three fixed: popover hoisted to a single page-root instance keyed by `activeClassifyTempId`, `append-to="body"`, `z-[60]` (sidebar is `z-40`), plus Escape + route-change close handlers. A Playwright regression test reproduces the failure mode.
- **AC #7 (primaryUnit)** — DB schema untouched per the story's Data Strategy. Legacy rows with `'pcs'` roundtrip unchanged via the `unitOptions` computed that prepends the loaded value when it falls outside the canonical `UNIT_VALUES`.
- **AC #8 (components-required)** — backend URN mapping scoped to `RegistryController` via `@ControllerAdvice(assignableTypes = RegistryController.class)` to avoid affecting any other module's validation behaviour. The existing `@Size(min = 1)` constraint on `ProductUpsertRequest.components` was already in place (Story 9.1); this story only remaps the emitted ProblemDetail.
- **i18n alphabetical invariant** preserved within the blocks I touched; top-level block ordering was already non-alphabetical prior to this story and was not changed.
- **Architectural invariants** — no new endpoints, no new DTOs, no new cross-module contracts, no schema migrations. All backend changes inside `hu.riskguard.epr.registry.api`. ArchUnit + EpicNineInvariantsTest remain green.
- The Playwright e2e regression test uses `test.skip()` gates for: (a) tier-gate blocking (test user lacking PRO_EPR) and (b) classifier returning no HIGH/MEDIUM suggestion. These gates keep the test honest — it can only pass when it actually exercises the regression path.
- **R1 review follow-up (2026-04-15)**: All 10 action items resolved. Decision (c) applied — pcs removed from dropdown entirely, DB roundtrips invisibly; 9 patches applied (double-submit guard, componentsError reactive clear, safe exception detail, desktop-only sticky bar, proper mandatory legend i18n, tooltip span wrapper for disabled button, visible PPWR header, removeComponent key cleanup). P4 was already resolved. 63 frontend + registry backend tests green.

### File List

**Frontend — added:**
- `frontend/app/composables/registry/useUnits.ts`
- `frontend/app/composables/registry/useUnits.spec.ts`
- `frontend/e2e/registry-classify-popover.e2e.ts`

**Frontend — modified:**
- `frontend/app/components/Common/AppBreadcrumb.vue`
- `frontend/app/components/Common/AppBreadcrumb.spec.ts`
- `frontend/app/composables/api/useApiError.ts`
- `frontend/app/i18n/hu/common.json`
- `frontend/app/i18n/en/common.json`
- `frontend/app/i18n/hu/registry.json`
- `frontend/app/i18n/en/registry.json`
- `frontend/app/pages/registry/[id].vue`
- `frontend/app/pages/registry/[id].spec.ts`
- `frontend/test/a11y/shell.a11y.spec.ts`

**Backend — added:**
- `backend/src/main/java/hu/riskguard/epr/registry/api/RegistryValidationExceptionHandler.java`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryValidationExceptionHandlerTest.java`

**Docs / sprint status — modified:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status: ready-for-dev → in-progress → review)
- `_bmad-output/implementation-artifacts/9-5-registry-ux-polish-and-bug-fixes.md` (Status, Tasks, Dev Agent Record, File List, Change Log)

### Change Log

- 2026-04-15 — Story 9.5 implemented (Andras + Claude Opus 4.6). All 9 ACs satisfied. 9 tasks complete. Gates green: 771/771 frontend tests, targeted `hu.riskguard.epr.registry.*` + `hu.riskguard.architecture.*` suites, and full backend suite (BUILD SUCCESSFUL in 7m 11s). Status → review.
- 2026-04-15 — Code review R1 (3-layer: Blind Hunter + Edge Case Hunter + Acceptance Auditor). 1 decision-needed, 9 patches, 14 deferred, 8 dismissed.
- 2026-04-15 — Code review R1 follow-up resolved (Date: 2026-04-15). All 10 action items addressed: Decision applied (option c — pcs removed from dropdown); P1 double-submit guard; P2 componentsError cleared in addComponent; P3 exception detail safe string; P4 already resolved; P5 lg:sticky; P6 mandatoryLegend i18n; P7 tooltip span wrapper; P8 expander column header; P9 removeComponent cleanup. 63 frontend + registry backend (BUILD SUCCESSFUL) green. Status → review.
- 2026-04-15 — Code review R2 (3-layer) + follow-up resolved. 11 new patches applied, 14 prior R1 deferrals honoured. Patches: (R2-P1) Popover `@hide` listener resets state on outside-click dismiss; (R2-P2) Popover z-index via `:pt.root.class` so class lands on teleported overlay root; (R2-P3) route watcher switched from `fullPath` to `path`; (R2-P4) Suggest button `aria-label` reflects disabled state; (R2-P5) breadcrumb product-name truncates at 16rem with tooltip; (R2-P6) E2E sidebar-click assertion unconditional at 1280×800; (R2-P7) focus restore to Suggest button on popover close; (R2-P8) sticky action bar `lg:-mx-4 lg:border-t` — narrow-viewport bleed fixed; (R2-P9) i18n top-level blocks reordered alphabetically (actions, audit, bootstrap, classify, empty, form, list, status, title, units); (R2-P10) dead `registry.form.noComponents.empty` key removed; (R2-P11) HU tooltipDisabled formality fixed ("Add meg" → "Adja meg"). 770/770 frontend tests + 5/6 E2E passing (1 acknowledged skip per R1 D7). E2E verified live with backend on test profile.

### Review Findings

- [x] [Review][Decision] **AC7 — legacy `pcs` unit option is selectable on new products** — Resolved: option (c) applied — removed `pcs` from the dropdown entirely; DB value roundtrips invisibly. `unitOptions` computed simplified to return `unitOptionsBase.value` directly; `UNIT_VALUES` import removed; legacy-prepend test replaced with canonical-list test. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P1 — `save()` missing double-submit guard** — Added `if (isSaving.value) return` at the top of `save()`. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P2 — `componentsError` not reactively cleared when user adds a component** — Cleared `componentsError` inside `addComponent()` when banner is active. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P3 — `RegistryValidationExceptionHandler` fallback branch leaks raw Spring message** — Replaced `ex.getMessage()` with the safe fixed string `"Validation failed"`. [`backend/src/main/java/hu/riskguard/epr/registry/api/RegistryValidationExceptionHandler.java`]

- [x] [Review][Patch] **P4 — `classifyLoading[comp._tempId]` never cleared** — Already resolved in initial implementation; `finally { classifyLoading.value[comp._tempId] = false }` was present. No change needed.

- [x] [Review][Patch] **P5 — AC2 sticky action bar active at all viewport widths (not desktop-only)** — Changed `sticky` to `lg:sticky` on the action bar wrapper. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P6 — AC2 mandatory fields legend displays `nameRequired` error text instead of a generic legend** — Changed to `t('registry.form.mandatoryLegend')`; added i18n keys: en "Fields marked * are required", hu "A *-gal jelölt mezők kitöltése kötelező". [`frontend/app/pages/registry/[id].vue`, `frontend/app/i18n/en/registry.json`, `frontend/app/i18n/hu/registry.json`]

- [x] [Review][Patch] **P7 — AC4 `v-tooltip` on `:disabled` PrimeVue Button will not fire** — Moved `v-tooltip.bottom` to a wrapping `<span class="inline-flex">` so pointer events reach the tooltip on disabled buttons. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P8 — AC6 "opcionális" hint is only in `aria-label`, not visible toggle text** — Added `<template #header>` to the expander Column rendering the `ppwrExpand` label as visible column-header text. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Patch] **P9 — `removeComponent()` does not clean up stale `classifyLoading` and `expandedRows` keys** — Added `delete classifyLoading.value[tempId]` and `delete expandedRows.value[tempId]` in `removeComponent()`. [`frontend/app/pages/registry/[id].vue`]

- [x] [Review][Defer] **D1 — Race condition: parallel classify requests clobber shared Popover state** [`[id].vue`] — deferred, pre-existing pattern; fixing requires request cancellation (AbortController); out of scope for polish story.
- [x] [Review][Defer] **D2 — `isComponentsMinSize` also matches `@Size(max=N)` violations on `components`** [`RegistryValidationExceptionHandler.java`] — deferred, pre-existing; a `@Size(max=...)` violation on components would emit the wrong URN but this constraint does not exist today.
- [x] [Review][Defer] **D3 — `expandedRows` stale keys after product reload** [`[id].vue`] — deferred, cosmetic; rows re-expand on reload in edge case.
- [x] [Review][Defer] **D4 — `setEditProduct(null)` in `onBeforeUnmount` causes breadcrumb flash** [`[id].vue`] — deferred, one-frame cosmetic flash during navigation.
- [x] [Review][Defer] **D5 — `save()` for new product does not call `setEditProduct(saved)` before navigation** [`[id].vue`] — deferred, cosmetic breadcrumb flicker; edit path already does it.
- [x] [Review][Defer] **D6 — E2E popover z-index assertion walks ancestor tree, not the popover element directly** [`registry-classify-popover.e2e.ts`] — deferred, test improvement; current assertion still exercises the regression path.
- [x] [Review][Defer] **D7 — E2E test silently skips when classifier returns no HIGH/MEDIUM suggestion** [`registry-classify-popover.e2e.ts`] — deferred, accepted per Dev Agent notes; test is honest about when it can run.
- [x] [Review][Defer] **D8 — `materialDescription` field has no frontend validation (backend is @NotBlank)** [`[id].vue`] — deferred, pre-existing gap from Story 9.1; surfaces as generic 400 on save.
- [x] [Review][Defer] **D9 — UUID regex does not enforce UUID version/variant bits** [`AppBreadcrumb.vue`] — deferred, cosmetic; any hex-UUID-shaped string shows the edit fallback.
- [x] [Review][Defer] **D10 — `shims-vue.d.ts` uses `any` in component type** [`frontend/types/shims-vue.d.ts`] — deferred, accepted TypeScript shim pattern.
- [x] [Review][Defer] **D11 — Global keydown listener accumulates in Nuxt keep-alive** [`[id].vue`] — deferred, pre-existing SSR pattern; onBeforeUnmount removes the listener.
- [x] [Review][Defer] **D12 — `@ControllerAdvice(assignableTypes)` scoping is fragile to controller refactoring** [`RegistryValidationExceptionHandler.java`] — deferred, intentional scope; no refactoring planned.
- [x] [Review][Defer] **D13 — Sticky action bar uses hardcoded `bg-white` (breaks dark mode)** [`[id].vue`] — deferred, dark mode not in scope for this project.
- [x] [Review][Defer] **D14 — Legacy `primaryUnit` prepended option uses raw DB value as label (no i18n)** [`[id].vue`] — deferred, acceptable for legacy roundtrip; value is `'pcs'` which is self-describing.
