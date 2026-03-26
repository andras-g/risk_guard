# Story 5.0: Rename `seasonal` → `recurring` for UX Clarity

Status: done

## Story

As a PRO_EPR user,
I want the "Seasonal" toggle on material templates renamed to "Recurring" with clear labelling,
so that I immediately understand which materials auto-copy each quarter and which are one-time or campaign-specific.

## Background / Decision Record

The original `seasonal` flag was confusing — it implies a time-of-year concept rather than a recurrence concept.
Decision: flip to the positive default `recurring = true` (Option A), so the flag explicitly answers "is this material used every quarter?"

**Logic inversion:**
- Old: `seasonal = false` (default) → material copied every quarter
- New: `recurring = true` (default) → material copied every quarter
- `recurring = NOT seasonal` — every existing value must be flipped in the DB migration

---

## Acceptance Criteria

### AC 1: DB — Column Renamed and Values Inverted

**Given** a live Flyway-managed PostgreSQL database
**When** migration `V20260326_001__rename_seasonal_to_recurring.sql` is applied
**Then** `epr_material_templates.seasonal` column is renamed to `recurring`
**And** all existing row values are bitwise-flipped (`recurring = NOT seasonal`)
**And** the column default is changed from `false` to `true`
**And** jOOQ codegen regenerates so `EPR_MATERIAL_TEMPLATES.RECURRING` compiles correctly

### AC 2: Backend API — `seasonal` Field Replaced by `recurring` in All DTOs

**Given** a `POST /api/v1/epr/materials` request body
**When** the client sends `{ "name": "Box", "baseWeightGrams": 100 }` without `recurring`
**Then** the backend defaults `recurring = true` (the field is optional, nullable Boolean, defaults to true)
**And** `GET /api/v1/epr/materials` response contains `"recurring": true` (not `"seasonal"`)
**And** `PUT /api/v1/epr/materials/{id}` accepts `recurring` field
**And** `PATCH /api/v1/epr/materials/{id}/recurring` replaces the old `/seasonal` endpoint
**And** `POST /api/v1/epr/materials/copy-from-quarter` request uses `includeNonRecurring` (replaces `includeSeasonal`)

### AC 3: Copy-from-Quarter Logic — Behavior Preserved, Names Updated

**Given** a tenant with recurring and non-recurring templates from a previous quarter
**When** "Copy from Previous Quarter" is triggered without `includeNonRecurring`
**Then** only `recurring = true` templates are copied (same behavior as old non-seasonal copy)
**When** `includeNonRecurring = true` is sent
**Then** all templates including `recurring = false` are copied (same behavior as old `includeSeasonal = true`)

### AC 4: Frontend Toggle UX — "Recurring" Toggle, Default ON

**Given** the Material Library DataTable
**When** a new material is created
**Then** the "Recurring" toggle defaults to ON (checked)
**When** the toggle is switched OFF on an existing template
**Then** `PATCH /api/v1/epr/materials/{id}/recurring` is called with `{ "recurring": false }`
**And** the row shows an Amber **"One-time"** badge (replaces old "Seasonal" badge)
**And** the toggle column header reads "Recurring" (was "Seasonal")

### AC 5: Filter Options — Labels Updated

**Given** the filter toggle above the Material Library DataTable
**When** the user opens the filter
**Then** the options are: "All", "Recurring only", "One-time only" (replaces "Seasonal only" / "Non-seasonal only")
**And** "Recurring only" shows templates where `recurring = true`
**And** "One-time only" shows templates where `recurring = false`

### AC 6: Material Form Dialog — "Recurring" Checkbox, Default Checked

**Given** the Add/Edit Material dialog
**When** the dialog opens for a new material
**Then** the "Recurring material" checkbox is checked by default
**And** the tooltip on the label reads: "Recurring materials are automatically included when copying to a new quarter. Uncheck for one-time or campaign-specific packaging."
**When** the dialog opens to edit an existing template
**Then** the checkbox reflects the template's current `recurring` value

### AC 7: Copy-Quarter Dialog — "Include one-time templates" Checkbox

**Given** the Copy from Previous Quarter dialog
**When** the dialog renders
**Then** the checkbox label reads: "Include one-time templates" (was "Include seasonal templates")
**And** it controls the `includeNonRecurring` field in the request body

### AC 8: Side Panel — "One-time" Count Replaces "Seasonal" Count

**Given** the EPR side panel summary
**When** the materials list is loaded
**Then** the stat row reads "One-time" with the count of templates where `recurring = false`
**And** `data-testid="one-time-count"` replaces `data-testid="seasonal-count"`
**And** the store getter is `oneTimeCount` (replaces `seasonalCount`)

### AC 9: i18n — All Keys Updated in EN and HU, Sorted Alphabetically

**Given** `en/epr.json` and `hu/epr.json`
**When** all seasonal-related keys are replaced
**Then** old keys removed: `seasonal`, `seasonalLabel`, `seasonalOnly`, `nonSeasonalOnly`, `seasonalBadge`, `includeSeasonal`
**And** new keys added (alphabetically sorted, parity across both files):
  - `columns.recurring` — column header
  - `dialog.recurringLabel` — form field label
  - `dialog.recurringTooltip` — tooltip text
  - `filter.recurringOnly` — filter option
  - `filter.oneTimeOnly` — filter option
  - `copyQuarter.includeNonRecurring` — checkbox label
  - `oneTimeBadge` — amber badge text
  - `sidePanel.oneTime` — side panel stat label

### AC 10: No `seasonal` References Remain in Source Code

**Given** the rename is complete
**Then** `./gradlew test` passes (all backend tests green)
**And** `npm run test` passes (all frontend Vitest tests green)
**And** `grep -r "seasonal" backend/src frontend/` returns zero matches in `.java`, `.ts`, `.vue`, `.json` files
(The SQL migration file itself may reference the old column name in comments — that is acceptable)

---

## Tasks / Subtasks

- [x] Task 1: DB Migration (AC: #1)
  - [x] 1.1 Create `backend/src/main/resources/db/migration/V20260326_001__rename_seasonal_to_recurring.sql`
  - [x] 1.2 Run jOOQ codegen so `EPR_MATERIAL_TEMPLATES.RECURRING` is available before editing Java source

- [x] Task 2: Backend DTOs (AC: #2)
  - [x] 2.1 `MaterialTemplateRequest.java`: `Boolean seasonal` → `Boolean recurring`
  - [x] 2.2 `MaterialTemplateResponse.java`: `boolean seasonal` → `boolean recurring`; `from()` factory maps `record.getRecurring()`
  - [x] 2.3 Rename file `SeasonalToggleRequest.java` → `RecurringToggleRequest.java`
  - [x] 2.4 `CopyQuarterRequest.java`: `boolean includeSeasonal` → `boolean includeNonRecurring`

- [x] Task 3: Backend Repository (AC: #1, #2, #3)
  - [x] 3.1 `EprRepository.java`: rename all `seasonal` parameters → `recurring`
  - [x] 3.2 Rename `updateSeasonal` → `updateRecurring`
  - [x] 3.3 `TemplateCopyData` record: `boolean seasonal` → `boolean recurring`
  - [x] 3.4 Copy-quarter filter logic inversion: `!t.seasonal()` → `t.recurring()`

- [x] Task 4: Backend Service (AC: #2, #3)
  - [x] 4.1 Rename `toggleSeasonal` → `toggleRecurring`
  - [x] 4.2 `createTemplate` default: `boolean isRecurring = recurring == null || recurring`
  - [x] 4.3 `updateTemplate`: same default resolution as 4.2
  - [x] 4.4 `copyFromQuarter`: param `includeSeasonal` → `includeNonRecurring`

- [x] Task 5: Backend Controller (AC: #2)
  - [x] 5.1 PATCH mapping: `/materials/{id}/seasonal` → `/materials/{id}/recurring`
  - [x] 5.2 Rename `toggleSeasonal` → `toggleRecurring`; `SeasonalToggleRequest` → `RecurringToggleRequest`
  - [x] 5.3 Copy-quarter handler: `includeSeasonal` → `includeNonRecurring`
  - [x] 5.4 Update all Javadoc comments referencing "seasonal"

- [x] Task 6: Backend Tests (AC: #10)
  - [x] 6.1 `EprControllerTest.java`: inverted assertions, updated PATCH URL
  - [x] 6.2 `EprServiceTest.java`: updated record helpers and assertions
  - [x] 6.3 `EprRepositoryTest.java`: `getSeasonal()` → `getRecurring()`; `updateSeasonal` → `updateRecurring`

- [x] Task 7: Frontend Types (AC: #2)
  - [x] 7.1 `frontend/types/epr.ts`: `seasonal` → `recurring` throughout

- [x] Task 8: Frontend Store (AC: #4, #8)
  - [x] 8.1 `frontend/app/stores/epr.ts`: `seasonalCount` → `oneTimeCount`; `toggleSeasonal` → `toggleRecurring`; URL and body updated

- [x] Task 9: i18n (AC: #9)
  - [x] 9.1 `frontend/app/i18n/en/epr.json`: removed seasonal keys; added recurring/one-time keys
  - [x] 9.2 `frontend/app/i18n/hu/epr.json`: mirror with Hungarian translations

- [x] Task 10: Frontend Components (AC: #4, #5, #6, #7, #8)
  - [x] 10.1 `MaterialInventoryBlock.vue`: emits, refs, filter logic, badges, testids updated
  - [x] 10.2 `MaterialFormDialog.vue`: default `recurring = ref(true)`; label, tooltip, testid updated
  - [x] 10.3 `CopyQuarterDialog.vue`: `includeNonRecurring`, updated testid and label
  - [x] 10.4 `EprSidePanel.vue`: `oneTimeCount` prop, `one-time-count` testid

- [x] Task 11: Frontend Page (AC: #4, #8)
  - [x] 11.1 `frontend/app/pages/epr/index.vue`: `handleToggleRecurring`, badge, event, prop all updated

- [x] Task 12: Frontend Tests (AC: #10)
  - [x] 12.1 `MaterialFormDialog.spec.ts`: testid and fixture updated
  - [x] 12.2 `CopyQuarterDialog.spec.ts`: testid updated
  - [x] 12.3 `MaterialInventoryBlock.spec.ts`: testids and fixture updated
  - [x] 12.4 `EprSidePanel.spec.ts`: prop, testid, i18n key updated
  - [x] 12.5 `epr/index.spec.ts`: store mock and stub prop updated

- [x] Task 13: Smoke Check (AC: #10)
  - [x] 13.1 `grep -r "seasonal" ...` — zero matches ✅
  - [x] 13.2 `./gradlew test` — all test suites green (102 XML results, 0 failures, 0 errors) ✅
  - [x] 13.3 `npm run test` — 553 tests across 56 files, all passed ✅

---

## Dev Notes

### Critical: Logic Inversion Table

| Old expression | New expression |
|---|---|
| `m.seasonal == true` | `m.recurring == false` |
| `m.seasonal == false` | `m.recurring == true` |
| `seasonal = false` (default) | `recurring = true` (default) |
| `includeSeasonal = true` → also copy seasonal | `includeNonRecurring = true` → also copy one-time |

### jOOQ Codegen — Run Before Editing Java

After `V20260326_001` migration is written, regenerate jOOQ before touching any Java source. `EprMaterialTemplatesRecord` will expose `getRecurring()`/`setRecurring()`. Check `build.gradle` for the codegen task name.

### Service Default Resolution

Old:
```java
boolean isSeasonal = seasonal != null && seasonal;
```
New (null → recurring by default):
```java
boolean isRecurring = recurring == null || recurring;
```

### PATCH Endpoint URL Change

`/api/v1/epr/materials/{id}/seasonal` → `/api/v1/epr/materials/{id}/recurring`

Search `epr.ts` store for the hardcoded string `/seasonal` — there is exactly one occurrence.

### MaterialFormDialog Default Flip

Old: `const seasonal = ref(false)` — starts unchecked.
New: `const recurring = ref(true)` — starts ON. Also update the reset on dialog close: `recurring.value = true`.

### Side Panel Stat Semantics

"One-time" count = `!m.recurring` — still counting the exceptions (non-default items), just named correctly.

### Project Structure

Backend EPR: `backend/src/main/java/hu/riskguard/epr/`
- `api/dto/` — rename `SeasonalToggleRequest.java` → `RecurringToggleRequest.java`
- `api/EprController.java`
- `domain/EprService.java`
- `internal/EprRepository.java`

Frontend EPR:
- `frontend/types/epr.ts`
- `frontend/app/stores/epr.ts`
- `frontend/app/i18n/en/epr.json` + `hu/epr.json`
- `frontend/app/components/Epr/` (4 components + 4 spec files)
- `frontend/app/pages/epr/index.vue` + `index.spec.ts`

### References

- Original seasonal implementation: `_bmad-output/implementation-artifacts/4-1-epr-material-library-and-seasonal-templates.md`
- Most recent migration pattern: `backend/src/main/resources/db/migration/V20260324_001__add_confidence_and_override_columns.sql`

---

### Review Findings

- [x] [Review][Patch] `index.spec.ts` SidePanelStub prop name mismatch: `verifiedCount` → `filingReadyCount` [frontend/app/pages/epr/index.spec.ts:17] — **fixed**
- [x] [Review][Patch] `index.spec.ts` wizard store mock missing `lastCloseReason: null` [frontend/app/pages/epr/index.spec.ts:85] — **fixed**
- [x] [Review][Defer] `updateTemplate` null recurring defaults to true — pre-existing design decision; frontend always sends explicit value — deferred, pre-existing
- [x] [Review][Defer] DB migration not idempotent if run twice — Flyway checksum prevents double-runs; standard pattern — deferred, pre-existing
- [x] [Review][Defer] `insertTemplate` fetchOne can return null — pre-existing jOOQ pattern throughout codebase — deferred, pre-existing
- [x] [Review][Defer] `copyFromQuarter` no deduplication guard — pre-existing behavior, not this story's scope — deferred, pre-existing
- [x] [Review][Defer] toggleRecurring TOCTOU race condition — pre-existing store pattern; InputSwitch is fully controlled — deferred, pre-existing
- [x] [Review][Defer] `CopyQuarterDialog` closes before server confirms success — UX design choice; parent handles errors — deferred, pre-existing
- [x] [Review][Defer] `findByTenantAndQuarter` uses UTC CREATED_AT for quarter boundary — pre-existing; no timezone column exists — deferred, pre-existing
- [x] [Review][Defer] No upper-bound validation on `sourceYear` / `baseWeightGrams` — pre-existing API concerns — deferred, pre-existing
- [x] [Review][Defer] `requireUuidClaim` not extracted to shared util (Epic 4 retro T1) — not in story spec; epic 4 retro item — deferred, pre-existing

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Logic inversion applied correctly throughout: `seasonal=false` (default) ↔ `recurring=true` (default)
- `EprRepositoryTest.java` was not in the original story file list but also required updating (integration test referenced `getSeasonal()`/`updateSeasonal()`)
- jOOQ codegen ran cleanly after `V20260326_001` migration; `EPR_MATERIAL_TEMPLATES.RECURRING` field generated
- Backend: 102 test result XML files, 0 failures, 0 errors
- Frontend: 553 tests across 56 files, all passed
- Zero `seasonal` references remain in `.java`, `.ts`, `.vue`, `.json` source files

### File List

- `backend/src/main/resources/db/migration/V20260326_001__rename_seasonal_to_recurring.sql` (new)
- `backend/src/main/java/hu/riskguard/epr/api/dto/MaterialTemplateRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/MaterialTemplateResponse.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/SeasonalToggleRequest.java` → `RecurringToggleRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/CopyQuarterRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java`
- `backend/src/main/java/hu/riskguard/epr/domain/EprService.java`
- `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java`
- `backend/src/test/java/hu/riskguard/epr/EprControllerTest.java`
- `backend/src/test/java/hu/riskguard/epr/EprServiceTest.java`
- `backend/src/test/java/hu/riskguard/epr/EprRepositoryTest.java`
- `frontend/types/epr.ts`
- `frontend/app/stores/epr.ts`
- `frontend/app/i18n/en/epr.json`
- `frontend/app/i18n/hu/epr.json`
- `frontend/app/components/Epr/MaterialInventoryBlock.vue`
- `frontend/app/components/Epr/MaterialFormDialog.vue`
- `frontend/app/components/Epr/CopyQuarterDialog.vue`
- `frontend/app/components/Epr/EprSidePanel.vue`
- `frontend/app/pages/epr/index.vue`
- `frontend/app/components/Epr/MaterialInventoryBlock.spec.ts`
- `frontend/app/components/Epr/MaterialFormDialog.spec.ts`
- `frontend/app/components/Epr/CopyQuarterDialog.spec.ts`
- `frontend/app/components/Epr/EprSidePanel.spec.ts`
- `frontend/app/pages/epr/index.spec.ts`
