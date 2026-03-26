# Epic 4 — QA Bugfix Session (2026-03-25)

**Date:** 2026-03-25
**Scope:** EPR Module (Stories 4.0–4.4) + cross-cutting auth/routing fixes
**Test results after all fixes:** Frontend 56 files / 553 tests passing, Backend all EPR + identity tests passing

---

## Bug 1: `/me` endpoint missing `tier` field — EPR page inaccessible

**Symptom:** All users saw "Magasabb előfizetési szint szükséges" on the EPR page regardless of their actual subscription tier.

**Root cause:** The `UserResponse` record did not include the `tier` field. The JWT contained the tier claim, but the `/me` endpoint never passed it to the frontend. The `useTierGate` composable received `tier: null` and fail-closed.

**Files changed:**
| File | Change |
|------|--------|
| `UserResponse.java` | Added `String tier` field + updated `from()` factory method |
| `IdentityController.java` | `/me` now reads `tier` from JWT claim |
| `AuthController.java` | Both `register` and `login` pass tier to `UserResponse.from()` |
| `IdentityControllerTest.java` | +2 tests: tier from JWT, null tier for legacy tokens |

---

## Bug 2: Post-login landing page flash (~200ms)

**Symptom:** After login, users briefly saw the public landing page before being redirected to the dashboard.

**Root cause:** All three login flows (`login.vue`, `callback.vue`, `register.vue`) navigated to `/` (the landing page) instead of `/dashboard`. The redirect to `/dashboard` happened only in `onMounted` — after the landing page had already rendered and painted.

**Files changed:**
| File | Change |
|------|--------|
| `login.vue` | `navigateTo('/')` → `navigateTo('/dashboard')` (2 locations) |
| `register.vue` | `navigateTo('/')` → `navigateTo('/dashboard')` |
| `callback.vue` | `router.push('/')` → `router.push('/dashboard')` |
| `auth.global.ts` | Middleware now redirects authenticated users from `/` and `/auth/login` to `/dashboard` |
| `index.vue` | Removed redundant `onMounted` auth redirect (middleware handles it) |
| `login.spec.ts` | Updated assertion |
| `register.spec.ts` | Updated assertion |
| `index.spec.ts` | Updated test to verify component no longer redirects |

---

## Bug 3: CopyQuarterDialog — multiple UI issues

**Symptom:** (a) Quarter dropdown rendered behind modal (z-index). (b) Cancel button showed raw key `commons.button.cancel`. (c) Dialog header X button was text-selectable with blinking cursor.

**Root cause:** (a) PrimeVue `Select` appends its overlay to `<body>` by default, which renders behind the modal mask. (b) Wrong i18n key path: `common.buttons.*` doesn't exist, should be `common.actions.*`. (c) No `user-select: none` on the dialog header.

**Files changed:**
| File | Change |
|------|--------|
| `CopyQuarterDialog.vue` | Added `append-to="self"` on Select, fixed i18n key, added `pt` header styles |
| `MaterialFormDialog.vue` | Fixed i18n keys `common.buttons.cancel/save` → `common.actions.cancel/save` |
| `epr/index.vue` | Fixed i18n keys `common.buttons.delete/cancel` → `common.actions.delete/cancel` |
| `CopyQuarterDialog.spec.ts` | Fixed DialogStub to render header prop, updated quarter label test |
| `MaterialFormDialog.spec.ts` | Fixed DialogStub to render header prop |

---

## Bug 4: Watchers not firing on mount with `visible: true`

**Symptom:** CopyQuarterDialog and MaterialFormDialog tests failed because form state wasn't initialized when components were mounted with `visible: true`.

**Root cause:** Vue 3 `watch()` only fires on *change*, not on initial value. Components mounted with `visible: true` in tests never triggered the watcher, so `selectedQuarter` stayed `null` and form fields weren't populated.

**Files changed:**
| File | Change |
|------|--------|
| `CopyQuarterDialog.vue` | Added `{ immediate: true }` to the `watch(() => props.visible)` watcher |
| `MaterialFormDialog.vue` | Added `{ immediate: true }` to the `watch(() => props.visible)` watcher |

---

## Bug 5: Material Library table too wide — horizontal scrolling required

**Symptom:** The DataTable with 7 columns (including 3 text-label action buttons) overflowed the container on standard screens.

**Root cause:** `max-w-7xl` (1280px) container minus the 288px side panel left only ~968px for the table. Three action buttons with text labels consumed too much space.

**Files changed:**
| File | Change |
|------|--------|
| `epr/index.vue` | Removed `max-w-7xl` constraint — table uses full available width |
| `MaterialInventoryBlock.vue` | Action buttons changed from text+icon to icon-only with `v-tooltip.top`. Added `rounded`, reduced gap. Added `table-style="width: 100%"` to DataTable |

---

## Bug 6: Classification wizard broken at step 3 (subgroup selection)

**Symptom:** After selecting a group, the wizard showed "01" as the category and couldn't save. The subgroup selection threw a backend error.

**Root cause:** The frontend sent `level: 'subgroup'` to `/wizard/step`, but the backend's `processStep()` only handles `product_stream`, `material_stream`, and `group`. The `subgroup` case hit the `default` branch and threw `IllegalArgumentException`.

**Files changed:**
| File | Change |
|------|--------|
| `eprWizard.ts` | When `selection.level === 'subgroup'`, skip the `/wizard/step` API call. Add selection to `traversalPath` client-side and call `resolveResult()` directly |

---

## Bug 7: No back button in classification wizard

**Symptom:** If a user misclicked on the wrong option, there was no way to go back to the previous step.

**Files changed:**
| File | Change |
|------|--------|
| `eprWizard.ts` | Added `goBack()` action — pops last traversal entry, re-fetches options via `/wizard/start` (step 1) or `/wizard/step` replay (steps 2-3) |
| `WizardStepper.vue` | Added "Vissza" / "Back" button with `pi-arrow-left` icon, visible on steps 2-3 when `traversalPath.length > 0` |
| `WizardStepper.spec.ts` | +4 tests for back button visibility and click behavior |
| `hu/epr.json` | Added `epr.wizard.back: "Vissza"` |
| `en/epr.json` | Added `epr.wizard.back: "Back"` |

---

## Bug 8: Seasonal toggle blinks the entire table

**Symptom:** Toggling the seasonal checkbox on a material row caused the whole DataTable to flash (skeleton → table → loaded).

**Root cause:** `toggleSeasonal()` called `fetchMaterials()` after the PATCH, which set `isLoading = true` and replaced the table with skeletons for a network round-trip duration.

**Files changed:**
| File | Change |
|------|--------|
| `epr.ts` (store) | `toggleSeasonal()` now updates the single item in-place (`this.materials[index] = data`) instead of re-fetching the entire list |

---

## Bug 9: Wizard option cards don't update on language switch

**Symptom:** Switching language mid-wizard updated the table headers and buttons but the classification option cards kept the old language.

**Root cause:** Option card labels are plain strings from the API response stored in Pinia state (`availableOptions`). They're not reactive to locale changes — only `t()` calls are.

**Files changed:**
| File | Change |
|------|--------|
| `eprWizard.ts` | Added `refreshOptions()` action — replays the entire traversal from `/wizard/start` through each step to get fresh localized labels for breadcrumb and current options |
| `WizardStepper.vue` | Added `watch(locale, ...)` that triggers `refreshOptions()` when locale changes during an active wizard session |
| `WizardStepper.spec.ts` | Updated mock store with `refreshOptions` |

---

## Bug 10: `epr.wizard.back` i18n key placed at wrong nesting level

**Symptom:** Back button showed the raw key `epr.wizard.back` instead of translated text.

**Root cause:** The `"back"` and `"cancel"` keys were accidentally inserted inside the `epr.wizard.override` object instead of at the `epr.wizard` level (in both `hu` and `en` JSON files). The `edit` tool matched the wrong `"cancel"` occurrence.

**Files changed:**
| File | Change |
|------|--------|
| `hu/epr.json` | Moved `back` key to correct `epr.wizard` level, removed duplicate `cancel` from inside `override` |
| `en/epr.json` | Same fix |

---

## Bug 11: Override dialog shows `[object Object]` after selecting a KF code

**Symptom:** Picking a KF code entry from the AutoComplete dropdown showed `[object Object]` in the input field.

**Root cause:** PrimeVue 4 AutoComplete uses `optionLabel` prop (not `field` which was PrimeVue 3 API). Without `optionLabel`, the selected object was coerced to string via `toString()`.

**Files changed:**
| File | Change |
|------|--------|
| `OverrideDialog.vue` | `field="classification"` → `option-label="classification"` |

---

## Bug 12: KF codes indistinguishable in override dialog

**Symptom:** Codes like `11 01 01 01`, `11 01 02 01`, `11 01 03 01` all showed the same name ("Papír és karton") and fee rate (20.44 Ft/kg) with no visible difference.

**Root cause:** The `classification` field came from the fee entry (keyed by `feeCode = productStream + materialStream`), which doesn't include the group. All three codes share fee code `1101`.

**Files changed:**
| File | Change |
|------|--------|
| `DagEngine.java` | `resolveKfCode()` now appends the group label when multiple groups exist. Added `resolveGroupLabel()` helper. E.g., "Papír és karton — Fogyasztói csomagolás" vs "Papír és karton — Szállítási csomagolás" |
| `OverrideDialog.vue` | Redesigned `#option` template to two-line layout: KF code + fee rate on top, classification below |

---

## Bug 13: Mixed-language breadcrumb after mid-wizard locale switch

**Symptom:** Selecting step 1 in Hungarian, switching to English, then continuing — the breadcrumb showed mixed languages (step 1 in HU, steps 2-3 in EN).

**Root cause:** `traversalPath` entries stored the `label` as a plain string at the time of selection. The earlier `refreshOptions()` only re-fetched the current step's options, not the breadcrumb labels of prior selections.

**Files changed:**
| File | Change |
|------|--------|
| `eprWizard.ts` | Rewrote `refreshOptions()` to replay the **entire traversal** from start. Each step re-fetches from the API, getting localized labels for every breadcrumb entry. Handles subgroup level client-side (no `/step` call needed) |

---

## Enhancement: Contextual wizard step hints

**Motivation:** The KF code hierarchy has orthogonal dimensions that can be counterintuitive. E.g., selecting "Fa" (Wood) as material then seeing "PET palack / Üveg palack / Fém doboz" as groups is confusing — but correct per 80/2023 Korm. rendelet.

**Files changed:**
| File | Change |
|------|--------|
| `WizardStepper.vue` | Added `stepHint` computed that returns context-specific explanation text. Rendered as subtle info-circle + text above option cards on steps 1-3. Special hint for deposit packaging (product stream 12) explaining the container-type vs material-type distinction |
| `hu/epr.json` | Added `epr.wizard.hints.*` (5 keys) |
| `en/epr.json` | Added `epr.wizard.hints.*` (5 keys) |

---

## Summary

| Category | Count |
|----------|-------|
| Bugs fixed | 13 |
| Enhancements | 1 (wizard hints) |
| Frontend files changed | ~20 |
| Backend files changed | 4 |
| New tests added | 8 (2 backend + 6 frontend) |
| Pre-existing test failures fixed | 4 |
| Final test count | 553 frontend + all backend passing |
