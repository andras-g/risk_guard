# Story 5.4: Export Locale Enforcement & UX Notice

Status: done

## Story

As a User,
I want to be notified that my government exports are being generated in Hungarian, even if my UI is set to English,
So that I am not confused by the language change in the final file.

## Acceptance Criteria

1. **Given** a user with UI language set to English,
   **When** they trigger "Export for MOHU" (clicking the export button on `/epr/filing`),
   **Then** the UI displays an Indigo Toast notification (PrimeVue `severity: 'info'`): "Export generated in Hungarian (required by MOHU portal)."

2. **And** the generated CSV content already uses Hungarian material category names — this is enforced via `@ExportLocale("hu")` on `MohuExporter.java` (column headers hardcoded in Hungarian per MOHU spec). No new backend changes are needed.

---

## Tasks / Subtasks

- [x] Task 1 — Frontend: Add `exportLocaleNotice` i18n keys (AC: 1)
  - [x] 1.1 Add to `frontend/app/i18n/en/epr.json` under `epr.filing`:
    ```json
    "exportLocaleNotice": "Export generated in Hungarian (required by MOHU portal)."
    ```
  - [x] 1.2 Add to `frontend/app/i18n/hu/epr.json` under `epr.filing`:
    ```json
    "exportLocaleNotice": "Az export magyar nyelven készült (a MOHU portál követelménye)."
    ```
  - [x] 1.3 Run `npm run check-i18n` to verify parity — must pass

- [x] Task 2 — Frontend: Update `handleExport()` in `filing.vue` to show info toast on success (AC: 1)
  - [x] 2.1 In `frontend/app/pages/epr/filing.vue`, update `handleExport()`:
    - After `await filingStore.exportMohu()` completes **without throwing**, add an info toast:
      ```typescript
      toast.add({
        severity: 'info',
        summary: t('epr.filing.exportLocaleNotice'),
        life: 5000,
      })
      ```
    - The error path (`catch`) remains unchanged — it still shows `severity: 'error'`

- [x] Task 3 — Frontend: Update `filing.spec.ts` — add info toast test (AC: 1)
  - [x] 3.1 Add test: "Export success shows info toast with locale notice"
    - Set `mockServerResult` to a populated result, `mockExportMohu` resolves normally
    - Mount page, click export button, `await nextTick()`
    - Assert `mockToastAdd` was called with `expect.objectContaining({ severity: 'info' })`
  - [x] 3.2 Confirm existing error toast test still passes (no regression)

---

## Dev Notes

### What Was Already Done in Story 5.3 (Do NOT Re-implement)

- `@ExportLocale("hu")` is already on `MohuExporter.java` — it is a **marker annotation** only (documentation; no runtime locale-switching). The locale is enforced by hardcoded Hungarian column headers in the `HEADER` constant.
- Backend messages files already have the `export.locale.notice` key:
  - `messages_hu.properties`: `export.locale.notice=Az export magyar nyelven készült (MOHU előírás)`
  - `messages_en.properties`: `export.locale.notice=Export generated in Hungarian (required by MOHU)`
  - `messages.properties` (fallback): same as EN
- `I18nConfigTest.java` already tests `export.locale.notice` resolution in both HU and EN locales — this test passes and requires NO changes.
- CSV column headers (`KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)`) are hardcoded in Hungarian in `MohuExporter.java` — AC2 is fully satisfied.

### What Story 5.4 Adds (New Work)

This is a **pure frontend story** — three small changes:
1. Two i18n keys (one per locale JSON file)
2. One extra `toast.add()` call on the success path of `handleExport()`
3. One new test case in `filing.spec.ts`

### Filing Page Architecture (from Story 5.3)

`filing.vue` already has:
- `useToast()` imported and initialized as `const toast = useToast()`
- `handleExport()` async function with error toast on `catch`
- Export button visible when `filingStore.serverResult !== null`
- `mockToastAdd` mock and `primevue/usetoast` mock already set up in `filing.spec.ts`

The ONLY change to `handleExport()` is adding the `toast.add({ severity: 'info', ... })` on the success path, **before** the `catch` block:

```typescript
// BEFORE (Story 5.3):
async function handleExport() {
  try {
    await filingStore.exportMohu()
  }
  catch (e: unknown) {
    const message = (e as { data?: { detail?: string } })?.data?.detail
      ?? (e instanceof Error ? e.message : 'Unknown error')
    toast.add({
      severity: 'error',
      summary: t('epr.filing.exportError', { message }),
      life: 5000,
    })
  }
}

// AFTER (Story 5.4):
async function handleExport() {
  try {
    await filingStore.exportMohu()
    toast.add({
      severity: 'info',
      summary: t('epr.filing.exportLocaleNotice'),
      life: 5000,
    })
  }
  catch (e: unknown) {
    const message = (e as { data?: { detail?: string } })?.data?.detail
      ?? (e instanceof Error ? e.message : 'Unknown error')
    toast.add({
      severity: 'error',
      summary: t('epr.filing.exportError', { message }),
      life: 5000,
    })
  }
}
```

### "Indigo Toast" = PrimeVue `severity: 'info'`

The UX spec refers to "Indigo Toast" (UX §6.5 info pattern: "Info (Indigo left border)"). In this codebase `severity: 'info'` is the correct PrimeVue mapping — already used in `frontend/app/pages/epr/index.vue:105` and `frontend/app/components/Common/AppSidebar.vue:55`.

### i18n Key Placement

Add the new key in alphabetical order under `epr.filing` in both JSON files. Currently the filing section ends with:
```json
"totalLines": "Materials included",
"validation": { ... }
```
Insert `"exportLocaleNotice"` after `"exportGenerating"` and before `"grandTotalFee"`:
- `exportButton` → `exportError` → `exportGenerating` → **`exportLocaleNotice`** ← new → `grandTotalFee`

### Test Pattern for `filing.spec.ts`

The existing "Export error shows toast when exportMohu() throws" test pattern at line 262 is the model. The new test is the success-path mirror:

```typescript
it('Export success shows info toast with locale notice', async () => {
  mockFilingLines = [
    {
      templateId: '1', name: 'Box A', kfCode: '11010101',
      baseWeightGrams: 120, feeRateHufPerKg: 215,
      quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120,
      feeAmountHuf: 25800, isValid: true, validationMessage: null,
    },
  ]
  mockHasValidLines = true
  mockServerResult = {
    lines: [{ templateId: '1', name: 'Box A', kfCode: '11010101', quantityPcs: 1000, baseWeightGrams: 120, totalWeightGrams: 120000, totalWeightKg: 120, feeRateHufPerKg: 215, feeAmountHuf: 25800 }],
    grandTotalWeightKg: 120,
    grandTotalFeeHuf: 25800,
    configVersion: 1,
  }
  // mockExportMohu resolves by default (vi.fn().mockResolvedValue(undefined))

  const wrapper = mountPage()
  const exportBtn = wrapper.find('[data-testid="export-mohu-button"]')
  expect(exportBtn.exists()).toBe(true)
  await exportBtn.trigger('click')
  await wrapper.vm.$nextTick()

  expect(mockToastAdd).toHaveBeenCalledWith(
    expect.objectContaining({ severity: 'info' }),
  )
})
```

Note: The `mockExportMohu` is declared at line 56 as `vi.fn().mockResolvedValue(undefined)` — it resolves successfully by default. No override needed.

### Architecture Compliance Checklist

- [ ] No new backend files — backend locale enforcement already complete
- [ ] No Flyway migration — no DB changes
- [ ] `exportLocaleNotice` key added to BOTH `en/epr.json` AND `hu/epr.json` (i18n parity required)
- [ ] ESLint `@intlify/vue-i18n/no-raw-text` — no hardcoded strings; the message uses `t('epr.filing.exportLocaleNotice')`
- [ ] Toast appears on SUCCESS path only — error path unchanged (`severity: 'error'`)
- [ ] `life: 5000` consistent with all other toasts in `filing.vue`

### Project Structure Notes

Files to modify (all small changes):
- `frontend/app/i18n/en/epr.json` — add 1 key
- `frontend/app/i18n/hu/epr.json` — add 1 key
- `frontend/app/pages/epr/filing.vue` — add 4 lines to `handleExport()`
- `frontend/app/pages/epr/filing.spec.ts` — add 1 test (~25 lines)

Files to verify (no changes needed):
- `backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java` — already `@ExportLocale("hu")`
- `backend/src/main/resources/messages_hu.properties` — already has `export.locale.notice`
- `backend/src/main/resources/messages_en.properties` — already has `export.locale.notice`
- `backend/src/test/java/hu/riskguard/core/config/I18nConfigTest.java` — already passes

### References

- `filing.vue` (current implementation): `frontend/app/pages/epr/filing.vue`
- `filing.spec.ts` (test file): `frontend/app/pages/epr/filing.spec.ts`
- `en/epr.json` (EN i18n): `frontend/app/i18n/en/epr.json`
- `hu/epr.json` (HU i18n): `frontend/app/i18n/hu/epr.json`
- `MohuExporter.java` (@ExportLocale): `backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java`
- `messages_hu.properties` (backend export keys): `backend/src/main/resources/messages_hu.properties`
- `I18nConfigTest.java` (backend locale test): `backend/src/test/java/hu/riskguard/core/config/I18nConfigTest.java`
- Architecture — i18n/l10n patterns, @ExportLocale, display vs. export locale: [Source: architecture.md §i18n & l10n Patterns]
- Architecture — Toast conventions: [Source: architecture.md §Process Patterns]
- UX Spec — Indigo Toast (info) pattern: [Source: ux-design-specification.md §6.5, User Journey step 8]
- Story 5.3 (export implementation): `_bmad-output/implementation-artifacts/5-3-mohu-ready-csv-export.md`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — straightforward implementation, no issues encountered.

### Completion Notes List

- Added `exportLocaleNotice` key to both `en/epr.json` and `hu/epr.json` under `epr.filing`, inserted alphabetically between `exportGenerating` and `grandTotalFee`.
- Updated `handleExport()` in `filing.vue` to call `toast.add({ severity: 'info', summary: t('epr.filing.exportLocaleNotice'), life: 5000 })` on the success path (inside try, after `await filingStore.exportMohu()`).
- Added test "Export success shows info toast with locale notice" to `filing.spec.ts` — passes.
- `npm run check-i18n` passes (all modules in parity).
- Full suite: 580 tests pass, 0 regressions.

### File List

- `frontend/app/i18n/en/epr.json`
- `frontend/app/i18n/hu/epr.json`
- `frontend/app/pages/epr/filing.vue`
- `frontend/app/pages/epr/filing.spec.ts`

### Change Log

- 2026-03-29: Story 5.4 implemented — added exportLocaleNotice i18n keys (EN+HU), info toast on export success, new test case. 580 frontend tests pass.

### Review Findings

- [x] [Review][Patch] Toast assertion under-specified — test only checks `{ severity: 'info' }`, not `summary`; a misspelled key or wrong toast would still pass [`frontend/app/pages/epr/filing.spec.ts` — new test]
- [x] [Review][Defer] Single `$nextTick()` may not flush all promise microtasks in async test — deferred, pre-existing pattern throughout the test file
- [x] [Review][Defer] Info toast fires even when blob is null/empty and no file was downloaded — deferred, pre-existing store behavior (Story 5.3 scope)
- [x] [Review][Defer] No test for `isExporting` state consistency when toast fires — deferred, pre-existing store concern, out of scope
