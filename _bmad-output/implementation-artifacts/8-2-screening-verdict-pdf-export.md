# Story 8.2: Screening Verdict PDF Export

Status: review

## Story

As a User who has run a screening,
I want to export a one-page audit-proof PDF of the verdict directly from the screening page,
so that I can provide legally-traceable evidence of my due diligence to banks, auditors, or partners instantly.

## Acceptance Criteria

### AC 1: Export PDF button enabled on VerdictCard

**Given** a verdict is displayed on `/screening/[taxNumber]`
**When** the verdict data has loaded (not in loading state)
**Then** the "PDF exportálás" / "Export PDF" button in the VerdictCard action row is **enabled** and clickable
**And** the button shows a spinner while PDF generation is in progress
**And** the button is disabled again while generating (no double-click)

### AC 2: PDF content

**Given** the user clicks "Export PDF" on a loaded verdict
**When** the PDF is generated
**Then** it contains all of the following:
- Header: "RiskGuard — Átvilágítási Jelentés" (HU) / "RiskGuard — Screening Report" (EN), generated date
- Company name (or "—" if null) + Tax Number
- Verdict status (localized label: Megbízható / Kockázatos / Felfüggesztett adószám / Hiányos / Nem elérhető)
- Risk signals list (localized, or "Nem találtunk kockázati jelet" / "No risk signals found" if empty)
- Data sources checked: list from provenance (source name, available/unavailable, checked timestamp)
- SHA-256 audit hash (64-char hex), or "N/A" if `sha256Hash` is null or `"HASH_UNAVAILABLE"`
- Footer: liability disclaimer text matching `screening.disclaimer.text` i18n key
**And** the PDF filename is `riskguard-atvilágítás-{taxNumber}-{YYYY-MM-DD}.pdf`

### AC 3: Mobile share / Desktop download

**Given** the PDF has been generated
**When** the device supports `navigator.share` with file support (`navigator.canShare({ files: [...] })`)
**Then** `navigator.share({ files: [pdfFile], title: '...', text: '...' })` is invoked
**When** `navigator.share` is unavailable or does not support files (desktop browsers)
**Then** the PDF is downloaded via a programmatic `<a download>` click

### AC 4: Error handling

**Given** PDF generation throws (e.g., jsPDF internal error)
**When** the error is caught
**Then** a Crimson Toast is shown: `t('screening.actions.exportPdfError')` with the error message
**And** the button returns to its enabled, non-loading state

### AC 5: Tests

**Given** all changes are implemented
**Then** `npm run test` (Vitest) passes — minimum 6 new unit tests:
- `useVerdictPdf`: generates correct filename, includes sha256 hash, falls back to "N/A" for null hash, includes disclaimer
- `VerdictCard.vue`: button enabled when verdict loaded, button disabled during generation

---

## Tasks / Subtasks

- [x] Task 1: Create `useVerdictPdf` composable (AC: #1, #2, #3, #4)
  - [x] 1.1 Create `frontend/app/composables/api/useVerdictPdf.ts`
  - [x] 1.2 Import `jsPDF` from `jspdf` (already installed — Story 5.1 dependency)
  - [x] 1.3 Function signature: `generateVerdictPdf(verdict: VerdictResponse, provenance: SnapshotProvenanceResponse | null, t: (key: string) => string): Promise<File>`
  - [x] 1.4 Build PDF layout: header, company block, verdict/risk signals block, data sources block, SHA-256 block, footer disclaimer
  - [x] 1.5 Implement mobile-share vs download dispatch (same pattern as `AuditDispatcher.vue`)
  - [x] 1.6 Expose `isGenerating: Ref<boolean>` and `exportVerdict(...)` function

- [x] Task 2: Update `VerdictCard.vue` (AC: #1, #4)
  - [x] 2.1 Add `provenance: SnapshotProvenanceResponse | null` prop (optional, default null)
  - [x] 2.2 Import and call `useVerdictPdf` composable
  - [x] 2.3 Remove hardcoded `disabled` from the PDF export Button
  - [x] 2.4 Add `:disabled="isGenerating"`, `:loading="isGenerating"`, `@click="exportVerdict(verdict, provenance, t)"`

- [x] Task 3: Update `[taxNumber].vue` to pass provenance to VerdictCard (AC: #2)
  - [x] 3.1 Pass `:provenance="currentProvenance"` to `<ScreeningVerdictCard>` in both desktop and mobile layouts

- [x] Task 4: Add i18n keys (AC: #4)
  - [x] 4.1 `hu/screening.json`: add `screening.actions.exportPdfError: "PDF generálása sikertelen: {message}"`
  - [x] 4.2 `en/screening.json`: add `screening.actions.exportPdfError: "PDF generation failed: {message}"`

- [x] Task 5: Unit tests (AC: #5)
  - [x] 5.1 Create `frontend/app/composables/api/useVerdictPdf.spec.ts`
  - [x] 5.2 Mock `jsPDF`, assert correct filename format
  - [x] 5.3 Assert sha256 fallback: null → "N/A", "HASH_UNAVAILABLE" → "N/A", valid hash → displayed
  - [x] 5.4 Assert disclaimer text included
  - [x] 5.5 Update `VerdictCard.spec.ts` (or create): button enabled when verdict loaded, disabled during generation

---

## Dev Notes

### Previous Story Intelligence (8.1)

**Learnings from Story 8.1 that apply here:**
- **PrimeVue `useToast` in Vitest**: Must use `vi.mock('primevue/usetoast', ...)` — `vi.stubGlobal` doesn't work for module imports. Use this pattern in VerdictCard tests.
- **`useRuntimeConfig` in components**: Story 8.1 R2 found that hardcoded `/api/v1/...` URLs break in deployments with separate API origin. Always use `useRuntimeConfig().public.apiBase`. However, this story is **client-side only** (no API calls), so this doesn't apply directly.
- **Health store mock reactivity**: Use JS getter (`get adapters() { return mockAdapters }`) in `vi.mock` factory for reactive test data.

### Why this wasn't in Story 5.1

Story 5.1 (`5-1-watchlist-bulk-pdf-export-and-mobile-dispatcher`) implemented **bulk watchlist PDF** — multi-row tabular export. The screening verdict page button was stubbed as `disabled` with tooltip "Story 5.1", but the watchlist export scope never included the single-verdict report. This story closes that gap.

### Key files to touch

| File | Change |
|------|--------|
| `frontend/app/composables/api/useVerdictPdf.ts` | **CREATE** — PDF generation composable |
| `frontend/app/composables/api/useVerdictPdf.spec.ts` | **CREATE** — unit tests |
| `frontend/app/components/Screening/VerdictCard.vue` | Enable button, add provenance prop, call composable |
| `frontend/app/pages/screening/[taxNumber].vue` | Pass `currentProvenance` to VerdictCard |
| `frontend/app/i18n/hu/screening.json` | Add `exportPdfError` key |
| `frontend/app/i18n/en/screening.json` | Add `exportPdfError` key |

### Exact jsPDF Pattern (from `useWatchlistPdfExport.ts`)

Follow this proven pattern — do NOT deviate:
```typescript
// Dynamic import to avoid bundle bloat
const { jsPDF } = await import('jspdf')
const doc = new jsPDF({ orientation: 'portrait' })  // portrait for single-verdict (watchlist used landscape)

// Font sizing convention:
doc.setFontSize(14)  // Title
doc.setFontSize(10)  // Metadata / section headers
doc.setFontSize(8)   // Body text, footer

// Footer disclaimer on every page:
const pageCount = doc.getNumberOfPages()
for (let i = 1; i <= pageCount; i++) {
  doc.setPage(i)
  doc.setFontSize(8)
  doc.text(disclaimerText, 14, doc.internal.pageSize.height - 10)
}

// File output:
const blob = doc.output('blob')
return new File([blob], filename, { type: 'application/pdf' })
```

### Exact Share/Download Dispatch Pattern (from `useWatchlistPdfExport.ts`)

```typescript
const file = new File([blob], filename, { type: 'application/pdf' })

// Mobile share if supported
if (typeof navigator !== 'undefined' && navigator.canShare?.({ files: [file] })) {
  try {
    await navigator.share({ files: [file], title, text })
    return
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') return  // user dismissed
  }
}

// Fallback: programmatic download
const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
const a = document.createElement('a')
a.href = url
a.download = filename
document.body.appendChild(a)
a.click()
document.body.removeChild(a)
setTimeout(() => URL.revokeObjectURL(url), 10_000)
```

### Exact VerdictCard Button Code to Modify

Current code at `VerdictCard.vue:324-332`:
```vue
<Button
  :label="t('screening.actions.exportPdf')"
  icon="pi pi-file-pdf"
  severity="primary"
  disabled
  :title="t('screening.actions.exportPdfTooltip')"
  data-testid="export-pdf-button"
/>
```
Replace with:
```vue
<Button
  :label="t('screening.actions.exportPdf')"
  icon="pi pi-file-pdf"
  severity="primary"
  :disabled="isGenerating"
  :loading="isGenerating"
  :title="t('screening.actions.exportPdfTooltip')"
  data-testid="export-pdf-button"
  @click="exportVerdict(props.verdict, props.provenance ?? null, t)"
/>
```

### Exact `[taxNumber].vue` Change

Add `:provenance="currentProvenance"` prop in **both** desktop and mobile layouts:
```vue
<!-- Desktop (line ~79) -->
<ScreeningVerdictCard :verdict="currentVerdict" :provenance="currentProvenance" />

<!-- Mobile (line ~94) -->
<ScreeningVerdictCard :verdict="currentVerdict" :provenance="currentProvenance" />
```
`currentProvenance` is already destructured from `storeToRefs(screeningStore)` at the top of the component — no new import needed.

### Existing VerdictCard Test Pattern to Follow

`VerdictCard.spec.ts` already has an "action buttons" describe block (lines 312-331) with tests for the disabled button. Update these tests:
- Change `expect(pdfBtn.attributes('disabled')).toBeDefined()` → test that button is NOT disabled when verdict is loaded
- Add test: button IS disabled when `isGenerating` is true (mock the composable)
- Keep the `data-testid="export-pdf-button"` selector

### Existing i18n Keys (keep alphabetical order)

`hu/screening.json` actions section currently contains: `addToWatchlist`, `addToWatchlistTooltip`, `backToDashboard`, `copyHash`, `exportPdf`, `exportPdfTooltip`, `searchNow`. Insert `exportPdfError` between `exportPdf` and `exportPdfTooltip`.

`en/screening.json` same structure — insert `exportPdfError` in same position.

### Verdict Status i18n Mapping

Use existing keys for localized verdict labels in the PDF. The verdict status labels are already in i18n under `screening.verdict.*` keys. Map:
- `RELIABLE` → `t('screening.verdict.reliable')`
- `AT_RISK` → `t('screening.verdict.atRisk')`
- `TAX_SUSPENDED` → `t('screening.verdict.taxSuspended')`
- `INCOMPLETE` → `t('screening.verdict.incomplete')`
- `UNAVAILABLE` → `t('screening.verdict.unavailable')`

### Risk Signal i18n Mapping

Risk signals in `VerdictResponse.riskSignals` are string keys like `"TAX_DEBT"`, `"NEGATIVE_RATING"`, etc. Map them via `t('screening.riskSignals.{key}')` — the same pattern VerdictCard.vue already uses to render them in the UI.

### Technical constraints

- **jsPDF already installed** — `import { jsPDF } from 'jspdf'` — no new dependency needed. See Story 5.1 (`AuditDispatcher.vue`) for precedent.
- **VerdictResponse.sha256Hash** — already in the API type (`frontend/types/api.d.ts:24`). Field: `sha256Hash: string | null`. Handle `"HASH_UNAVAILABLE"` sentinel as well as `null`.
- **Provenance type** — `SnapshotProvenanceResponse` (see `frontend/types/api.d.ts`). Pass as nullable prop; skip data sources section if null.
- **Composable location** — `composables/api/` per project-context.md organization rules.
- **i18n** — All user-facing strings must use `$t()`. Keys must be added to BOTH `hu/` and `en/` and kept alphabetically sorted.
- **No backend changes** — all required data is already in `VerdictResponse` + `SnapshotProvenanceResponse` from existing screening store.
- **AuditDispatcher pattern reference** — `frontend/app/components/Watchlist/AuditDispatcher.vue` for the `navigator.share` + fallback `<a download>` pattern. Do NOT import AuditDispatcher; replicate the ~10-line dispatch logic inline in the composable.
- **TypeScript** — Use `<script setup lang="ts">`. `useVerdictPdf` must be a proper Nuxt composable (no import of Vue needed in Nuxt 4 auto-import context).

### References

- [Source: frontend/app/components/Screening/VerdictCard.vue#329] — disabled button to enable
- [Source: frontend/app/components/Watchlist/AuditDispatcher.vue] — mobile share dispatch pattern
- [Source: frontend/types/api.d.ts#13-25] — VerdictResponse with sha256Hash
- [Source: frontend/app/i18n/hu/screening.json#48-56] — existing screening action keys
- [Source: _bmad-output/implementation-artifacts/5-1-watchlist-bulk-pdf-export-and-mobile-dispatcher.md] — jsPDF pattern and AuditDispatcher precedent

### Git Intelligence

Recent commits show Story 8.1 (NAV Online Számla) is the most recent work. Key patterns:
- Commit convention: `feat(8.1): description` — use `feat(8.2): Screening verdict PDF export`
- Frontend test count at ~684 — ensure no regressions
- No backend changes in this story — backend test suite unchanged

### Architecture Compliance

- **Module isolation**: This story is frontend-only. No cross-module concerns.
- **Frontend spec co-location**: `useVerdictPdf.spec.ts` goes in `composables/api/` next to the composable.
- **i18n**: All PDF text MUST use `t()` translations. Never hardcode Hungarian or English strings in the composable.
- **Legal proof integrity**: The SHA-256 hash in the PDF is for display/audit trail only — the legal truth remains in `search_audit_log`. The PDF is a convenience export.
- **PII**: Tax numbers appear in the PDF by design (it's the user's own screening result), but never log them.

### Deferred / Out of Scope

- PDF template customization (logo, branding) — future story
- Server-side PDF generation — not needed; client-side jsPDF is sufficient
- PDF/A compliance for long-term archival — not required yet
- Multi-page PDF for multiple screenings — this is single-verdict only

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No blockers encountered.

### Completion Notes List

- Created `useVerdictPdf` composable with `exportVerdict(verdict, provenance, t)` and `isGenerating` ref. Follows dynamic-import jsPDF pattern from `useWatchlistPdfExport.ts`. Portrait orientation, full layout: header, company block, verdict status (localized), risk signals (localized), data sources (from provenance), SHA-256 (null/"HASH_UNAVAILABLE" → "N/A"), footer disclaimer via `t('screening.disclaimer.text')`.
- Mobile share / desktop download dispatch identical to `useWatchlistPdfExport.ts` pattern.
- Added `provenance?: SnapshotProvenanceResponse | null` optional prop to `VerdictCard.vue`. Button now enabled and wired to `exportVerdict`. `useVerdictPdf` auto-imported as Nuxt composable.
- Both desktop and mobile `<ScreeningVerdictCard>` in `[taxNumber].vue` now receive `:provenance="currentProvenance"` (already in storeToRefs).
- Added `exportPdfError` key to both `hu/` and `en/` screening.json. Also added `pdf.reportTitle` and `pdf.generated` keys required by the PDF header text.
- 8 new tests in `useVerdictPdf.spec.ts`: filename format, sha256 (real/null/sentinel), disclaimer, isGenerating state, provenance data sources (included/skipped).
- Updated `VerdictCard.spec.ts`: replaced "button disabled" test with "button enabled when verdict loaded" and added "button disabled when generation in progress". Added `useVerdictPdf` global stub.
- Fixed `test/a11y/screening.a11y.spec.ts`: added `useVerdictPdf` global stub to prevent ReferenceError.
- Full suite: 693 tests / 66 files — all green.
- ✅ Resolved review finding [HIGH]: P1 — Added `useToast` to composable; catch block in `exportVerdict` shows Crimson Toast with `t('screening.actions.exportPdfError', { message })`. Added 2 new tests (error toast shown, isGenerating resets).
- ✅ Resolved review finding [MED]: P2 — Added `checkPageBreak(y)` helper inside `buildPdf`; called before every text write in risk-signals and data-sources loops, and before the SHA-256 block. `addPage: vi.fn()` added to test mockDoc.
- ✅ Resolved review finding [LOW]: P3 — Added `typeof navigator.share === 'function'` guard before `canShare` check in `dispatch`.
- ✅ Resolved review finding [LOW]: P4 — Updated `exportPdfTooltip` in `en/screening.json` → "Export verdict as PDF for audit documentation" and `hu/screening.json` → "Verdikt exportálása PDF-be audit dokumentációhoz".
- ✅ Resolved review finding [LOW]: P5 — Moved `pdf` block after `guest` in both `en/screening.json` and `hu/screening.json`.
- Full suite post-review: 695 tests / 66 files — all green.

### File List

- `frontend/app/composables/api/useVerdictPdf.ts` — CREATED
- `frontend/app/composables/api/useVerdictPdf.spec.ts` — CREATED
- `frontend/app/components/Screening/VerdictCard.vue` — MODIFIED
- `frontend/app/components/Screening/VerdictCard.spec.ts` — MODIFIED
- `frontend/app/pages/screening/[taxNumber].vue` — MODIFIED
- `frontend/app/i18n/hu/screening.json` — MODIFIED
- `frontend/app/i18n/en/screening.json` — MODIFIED
- `frontend/test/a11y/screening.a11y.spec.ts` — MODIFIED

### Review Findings

- [x] [Review][Patch] P1 (HIGH): Missing error handling — no Crimson Toast shown on PDF generation failure [`useVerdictPdf.ts:8-21`, `VerdictCard.vue`]
- [x] [Review][Patch] P2 (MED): PDF page overflow with many risk signals or data sources — no page-break logic [`useVerdictPdf.ts:58-85`]
- [x] [Review][Patch] P3 (LOW): `navigator.canShare` checked without `navigator.share` existence guard [`useVerdictPdf.ts:142-146`]
- [x] [Review][Patch] P4 (LOW): Stale `exportPdfTooltip` text "Coming in a future release" — feature is now live [`en/screening.json`, `hu/screening.json`]
- [x] [Review][Patch] P5 (LOW): `pdf` i18n block placed before `guest` — violates alphabetical ordering constraint [`en/screening.json:72`, `hu/screening.json:72`]
- [x] [Review][Defer] D1: `URL.revokeObjectURL` 10s `setTimeout` — pre-existing pattern from Story 5.1 [`useVerdictPdf.ts:169`] — deferred, pre-existing
- [x] [Review][Defer] D2: `SOURCE_UNAVAILABLE` signal split on `:` truncates if source name contains colon — hypothetical format, not current [`useVerdictPdf.ts:131`] — deferred, pre-existing
- [x] [Review][Defer] D3: `dispatch` download fallback calls `document.createElement` without SSR guard — client-side-only page [`useVerdictPdf.ts:161`] — deferred, pre-existing
- [x] [Review][Defer] D4: Empty `provenance.sources` array produces no "no sources" message in PDF — spec does not require it [`useVerdictPdf.ts:72`] — deferred, pre-existing
- [x] [Review][Defer] D5: Race condition — null provenance exported if provenance fetch fails before user clicks export [`[taxNumber].vue`] — deferred, pre-existing
- [x] [Review][Defer] D6: `riskSignalLabel` returns raw i18n key for unknown signals — consistent with existing UI pattern [`useVerdictPdf.ts:134`] — deferred, pre-existing

### R2 Review Findings (2026-04-03)

- [x] [Review][Patch] P1 (LOW): `pdf` i18n block placed between `guest` and `freshness` — alphabetically `p > f`, so `pdf` must follow `freshness` [`en/screening.json`, `hu/screening.json`]
- [x] [Review][Patch] P2 (LOW): `screening.a11y.spec.ts` mock used plain `{ value: false }` for `isGenerating` (truthy object → button appeared disabled in a11y renders) [`test/a11y/screening.a11y.spec.ts:40`]
- [x] [Review][Patch] P3 (HIGH): `useVerdictPdf` used in `VerdictCard.vue` without explicit import — Nuxt dev server auto-import cache miss caused `ReferenceError: useVerdictPdf is not defined` in E2E / fresh environments; added explicit `import { useVerdictPdf } from '~/composables/api/useVerdictPdf'` [`VerdictCard.vue:7`]
- [x] [Review][Patch] P4 (MED): After P3 fix, `vi.stubGlobal('useVerdictPdf')` mocks in `VerdictCard.spec.ts` and `screening.a11y.spec.ts` became ineffective (explicit import bypasses global stub); migrated to `vi.mock('~/composables/api/useVerdictPdf', ...)` [`VerdictCard.spec.ts`, `screening.a11y.spec.ts`]
- [x] [Review][Defer] D7: `(doc as any).internal.getNumberOfPages()` bypasses type — pre-existing pattern from `useWatchlistPdfExport.ts` [`useVerdictPdf.ts:120`]

## Change Log

- 2026-04-03: R2 code review — 4 patch items resolved: i18n block ordering, a11y mock ref fix, explicit composable import (E2E ReferenceError fix), vi.mock migration; 695 frontend + 5 e2e all green (Date: 2026-04-03)
- 2026-04-03: Addressed code review findings — 5 items resolved (Date: 2026-04-03)
- 2026-04-03: Implemented Story 8.2 — `useVerdictPdf` composable (jsPDF portrait, full layout, mobile share/desktop download), VerdictCard PDF button enabled, provenance prop wired through, i18n keys added, 8 new composable tests + 2 updated VerdictCard tests. 693 frontend tests green.
