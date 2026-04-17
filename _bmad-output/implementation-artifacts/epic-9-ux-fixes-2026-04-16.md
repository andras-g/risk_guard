# Epic 9 — EPR Filing UX Fixes (2026-04-16)

**Date:** 2026-04-16
**Scope:** EPR filing page (`/epr/filing`) — usability fixes reported during manual QA
**Test results after all fixes:** Frontend 780/780 tests passing (15/15 filing.spec.ts)

---

## Fix 1: "Előtöltés számlákból" panel replaced with auto-loading invoice product table

**Symptom:** The "Előtöltés számlákból" collapsible panel (PrimeVue `Panel` with `collapsed: true`) was confusing — the plus-sign toggle didn't clearly communicate its purpose, and expanding it still required a manual "Számlák lekérdezése" click. Meanwhile, the "Előzetes jelentés" section already showed invoice provenance data, making the panel feel redundant.

**Fix:** Replaced the collapsed Panel + InvoiceAutoFillPanel sub-component with an always-visible section that:
- Auto-fetches grouped invoice products for the previous quarter on page mount (using the registered tax number from producer profile)
- Displays results in a PrimeVue DataTable with pagination (10/25/50 rows), global search (description, VTSZ code, KF code), and sortable columns
- Default sort: aggregated quantity descending (largest volumes first)
- Keeps the existing select-and-apply-to-filing flow via multi-select + "Alkalmazás a bejelentésre" button
- Shows contextual empty states: NAV unavailable, no results, no tax number configured

**Files changed:**
- `frontend/app/pages/epr/filing.vue` — replaced Panel with inline DataTable, added auto-fetch in `onMounted`
- `frontend/app/pages/epr/filing.spec.ts` — updated tests for new component structure (15 tests)
- `frontend/app/i18n/hu/epr.json` — new keys: `searchPlaceholder`, `noTaxNumber`; updated `panelTitle`
- `frontend/app/i18n/en/epr.json` — same keys (English)

---

## Fix 2: Hungarian date labels corrected from "Tól"/"Ig" to "-tól"/"-ig"

**Symptom:** The date range labels under "Negyedéves EPR bevallás – OKIRkapu beküldéshez" showed capitalized "Tól" and "Ig", which is incorrect Hungarian. These are suffixes (postpositions) and should be lowercase with a hyphen prefix: "-tól" and "-ig".

**Fix:** Updated `epr.autofill.fromLabel` and `epr.autofill.toLabel` in `hu/epr.json`.

**Files changed:**
- `frontend/app/i18n/hu/epr.json` — `"Tól"` → `"-tól"`, `"Ig"` → `"-ig"`

---

## Fix 3: "Saját adószám" field made read-only

**Symptom:** The tax number field in the OKIRkapu export section was editable, but it's auto-populated from the producer profile on page mount. Users should not be able to modify it inline — the canonical value comes from the producer profile settings.

**Fix:** Changed the input from `v-model` to `:value` with `readonly` attribute and visual styling (`bg-slate-50 cursor-not-allowed`) to clearly communicate it's not editable.

**Files changed:**
- `frontend/app/pages/epr/filing.vue` — `export-tax-number-input` now readonly
