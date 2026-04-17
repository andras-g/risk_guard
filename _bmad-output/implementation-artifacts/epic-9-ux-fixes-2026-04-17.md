# Epic 9 — EPR + Registry + Admin UX Fixes (2026-04-17)

**Date:** 2026-04-17
**Scope:** Menu-wide UX polish pass covering EPR filing, EPR material library, Registry, and Admin data sources
**Test results after all fixes:** Frontend 101/101 EPR specs passing; Backend `./gradlew test --tests "hu.riskguard.epr.*"` BUILD SUCCESSFUL

---

## Fix 1: Search-icon overlap on invoice-products panel (filing page)

**Symptom:** On `/epr/filing` in the "Termékek az előző negyedév számlái alapján" (renamed, see Fix 2) panel, the search `InputText`'s placeholder rendered on top of the search icon. Root cause: the panel used a manual `<span class="relative">` + absolute-positioned `<i class="pi pi-search">` with Tailwind `pl-10` on the `InputText` — PrimeVue 4's theme input padding overrode `pl-10`, so the placeholder sat on top of the icon.

**Fix:** Replaced the manual composition with PrimeVue 4's dedicated `IconField` + `InputIcon` components (the officially-supported pattern for this exact case).

**Files changed:**
- `frontend/app/pages/epr/filing.vue` — `<IconField><InputIcon class="pi pi-search" /><InputText/></IconField>` block; added imports for `IconField` and `InputIcon`
- `frontend/app/pages/epr/filing.spec.ts` — stubbed `IconField` and `InputIcon` to keep test output clean (15 tests passing)

---

## Fix 2: Hungarian copy pass on filing-page labels

**Symptoms:**
- `"Számlatermékek az előző negyedévből"` — `Számlatermékek` isn't a natural Hungarian compound; feels like a neologism
- `"Előzetes jelentés"` — vague ("preliminary report"); the user submits an XML, not a "report"
- `"Nyilvántartási egyezések (XML-be kerül)"` — siblings use pattern `<label> (<fate>)` (`"VTSZ visszaesési sorok (…, XML-ből kizárva)"`, `"Nem illeszkedett sorok (XML-ből kizárva)"`) — this one broke the pattern and stacked nominalized nouns

**Fixes:**
| Key | HU: before → after | EN: before → after |
|---|---|---|
| `epr.autofill.panelTitle` | "Számlatermékek az előző negyedévből" → **"Termékek az előző negyedév számlái alapján"** | "Invoice Products — Previous Quarter" → **"Products from Previous Quarter's Invoices"** |
| `epr.okirkapu.preview.title` | "Előzetes jelentés" → **"Bejelentés előnézete"** | "Report Preview" → **"Filing Preview"** |
| `epr.okirkapu.preview.registryMatch` | "Nyilvántartási egyezések (XML-be kerül)" → **"Azonosított tételek (bekerülnek az XML-be)"** | "Registry Matches (included in XML)" → **"Identified Items (included in XML)"** |

**Files changed:**
- `frontend/app/i18n/hu/epr.json` — three keys
- `frontend/app/i18n/en/epr.json` — three keys

---

## Fix 3: Data sources dashboard gated away from customer admins

**Symptom:** `/admin/datasources` showed operator-level telemetry (circuit-breaker state, success rate, MTBF, last-success timestamp, credential status, data-source mode) to `SME_ADMIN` and `ACCOUNTANT` roles. These values are not meaningful to customer admins — they're observability for the platform operator.

**Fix:** Gated the dashboard + refresh chrome + 30-second poll on `PLATFORM_ADMIN`. Non-platform admins still land on the page (it's linked from `epr/filing.vue` when NAV is unreachable) but see only the `AdminNavCredentialManager` section. One-shot fetch on mount remains so `NavCredentialManager` still receives the `dataSourceMode` prop for all roles.

Customer-facing subtitle added: `admin.datasources.subtitleCustomer` = "A NAV Online Számla hozzáférés beállítása és kezelése." / "Configure and manage your NAV Online Számla access." — swapped in for non-platform admins so the "adapter health" subtitle doesn't mislead.

**Files changed:**
- `frontend/app/pages/admin/datasources.vue` — `isPlatformAdmin` computed; `v-if` on dashboard + chrome; conditional subtitle; poll-gating
- `frontend/app/i18n/hu/admin.json` — new `admin.datasources.subtitleCustomer`
- `frontend/app/i18n/en/admin.json` — same

---

## Fix 4: "EPR Anyagkönyvtár" — menu-to-title context carry-over

**Symptom:** Clicking the `EPR` menu item landed on a page titled just `Anyagkönyvtár`, with no EPR context. Users asked what "Anyagkönyvtár" meant in isolation.

**Fix:** Renamed the page `h1` to carry the module context — `epr.materialLibrary.title`: "Anyagkönyvtár" → **"EPR Anyagkönyvtár"** (EN: "Material Library" → "EPR Material Library").

**Files changed:**
- `frontend/app/i18n/hu/epr.json` — `materialLibrary.title`
- `frontend/app/i18n/en/epr.json` — same

---

## Fix 5: EPR material table — horizontal scroll eliminated + always-on paging

**Symptom:** On `/epr` (Material Library), the material table had 7 columns (Name, Base Weight, KF Code, Verified, Recurring, Created, Actions). With the right-side `EprSidePanel` consuming ~320px on desktop, the main column shrank to ~700–800px — the content-heavy `Verified` column (filing-ready badge + confidence badge + override indicator) forced the table wider than its container and the browser added horizontal scroll. Separately, paging only activated at `>20` rows, so typical users (with <20 templates) never saw pagination controls.

**Fix:**
- **Dropped the `Created` column** (low signal — users classify once and reuse; `Recurring` already conveys reusability). Saves ~120px; eliminates the scroll at typical viewport widths.
- **Always-on paginator:** `:rows="10"` + `rows-per-page-options="[10, 25, 50]"` (was conditional paginator at >20 rows).
- Adjusted skeleton loading rows from 7 to 6 columns; updated widths so they still sum to 100%.
- Removed now-unused `useDateRelative` import.

**Files changed:**
- `frontend/app/components/Epr/MaterialInventoryBlock.vue` — dropped `Created` column, paginator config, skeleton adjust, unused import removed
- `frontend/app/i18n/hu/epr.json`, `frontend/app/i18n/en/epr.json` — removed orphan `materialLibrary.columns.created` key

---

## Fix 6: Classification path visible in the material table

**Symptom:** After classifying a material via the wizard, only the 8-digit KF code was visible in the table (e.g., `11010101`). The human-readable classification path ("Nem kötelezően visszaváltási díjas, egyszer használatos csomagolás → Alumínium → Fogyasztói csomagolás") was lost — users couldn't audit their own classifications without looking up the code externally.

**Fix (backend + frontend):**

**Backend:** Added `materialClassification` (13th field) to `MaterialTemplateResponse`. Extended `EprRepository.findAllByTenantWithOverride` — the existing LEFT JOIN that pulls `confidence` and `feeRate` from the latest linked calculation now also pulls `material_classification`. `EprService.listTemplatesWithOverride` maps the new field through. Test fixture arity bumped to 13.

**Frontend:** `MaterialInventoryBlock.vue` renders the classification path under the KF code in small muted text (`data-testid="classification-path"`). Mobile card (`pages/epr/index.vue`) mirrors the same visibility. Type `MaterialTemplateResponse.materialClassification: string | null` added.

**Files changed:**
- Backend: `MaterialTemplateResponse.java`, `EprRepository.java`, `EprService.java`, `EprControllerTest.java`
- Frontend: `types/epr.ts`, `components/Epr/MaterialInventoryBlock.vue`, `pages/epr/index.vue`, spec fixture updates (`MaterialInventoryBlock.spec.ts`, `MaterialFormDialog.spec.ts`)

---

## Fix 7: Wizard auto-scrolls into view on activation

**Symptom:** On `/epr`, clicking `Újrasorolás` (or `Besorolás`) on a material-table row opened the `EprWizardStepper` **below** the DataTable on desktop (per UX Spec §8.2). With a tall table the wizard rendered below the fold — user clicked the button and thought nothing happened.

**Fix:** Added `ref="wizardContainerRef"` to the desktop wizard container. Extended the existing `watch(() => wizardStore.isActive, …)` — when `isActive` flips true, `nextTick()` + `scrollIntoView({ behavior: 'smooth', block: 'start' })`. Non-invasive, preserves the current layout.

Side effect: combined with Fix 5's always-on paging (10 rows), the table is now short enough that the wizard is usually already near the fold even without the scroll.

**Files changed:**
- `frontend/app/pages/epr/index.vue` — `wizardContainerRef` ref; extended `isActive` watcher with scroll branch; ref attached to wizard container

---

## Verification

- **Frontend:** `npx vitest run app/components/Epr app/pages/epr` → 11 test files, 101 tests passing
- **Backend:** `./gradlew test --tests "hu.riskguard.epr.*"` → BUILD SUCCESSFUL in 3m 6s (exit 0)
