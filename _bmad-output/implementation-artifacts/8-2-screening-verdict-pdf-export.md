# Story 8.2: Screening Verdict PDF Export

Status: ready-for-dev

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

- [ ] Task 1: Create `useVerdictPdf` composable (AC: #1, #2, #3, #4)
  - [ ] 1.1 Create `frontend/app/composables/api/useVerdictPdf.ts`
  - [ ] 1.2 Import `jsPDF` from `jspdf` (already installed — Story 5.1 dependency)
  - [ ] 1.3 Function signature: `generateVerdictPdf(verdict: VerdictResponse, provenance: SnapshotProvenanceResponse | null, t: (key: string) => string): Promise<File>`
  - [ ] 1.4 Build PDF layout: header, company block, verdict/risk signals block, data sources block, SHA-256 block, footer disclaimer
  - [ ] 1.5 Implement mobile-share vs download dispatch (same pattern as `AuditDispatcher.vue`)
  - [ ] 1.6 Expose `isGenerating: Ref<boolean>` and `exportVerdict(...)` function

- [ ] Task 2: Update `VerdictCard.vue` (AC: #1, #4)
  - [ ] 2.1 Add `provenance: SnapshotProvenanceResponse | null` prop (optional, default null)
  - [ ] 2.2 Import and call `useVerdictPdf` composable
  - [ ] 2.3 Remove hardcoded `disabled` from the PDF export Button
  - [ ] 2.4 Add `:disabled="isGenerating"`, `:loading="isGenerating"`, `@click="exportVerdict(verdict, provenance, t)"`

- [ ] Task 3: Update `[taxNumber].vue` to pass provenance to VerdictCard (AC: #2)
  - [ ] 3.1 Pass `:provenance="currentProvenance"` to `<ScreeningVerdictCard>` in both desktop and mobile layouts

- [ ] Task 4: Add i18n keys (AC: #4)
  - [ ] 4.1 `hu/screening.json`: add `screening.actions.exportPdfError: "PDF generálása sikertelen: {message}"`
  - [ ] 4.2 `en/screening.json`: add `screening.actions.exportPdfError: "PDF generation failed: {message}"`

- [ ] Task 5: Unit tests (AC: #5)
  - [ ] 5.1 Create `frontend/app/composables/api/useVerdictPdf.spec.ts`
  - [ ] 5.2 Mock `jsPDF`, assert correct filename format
  - [ ] 5.3 Assert sha256 fallback: null → "N/A", "HASH_UNAVAILABLE" → "N/A", valid hash → displayed
  - [ ] 5.4 Assert disclaimer text included
  - [ ] 5.5 Update `VerdictCard.spec.ts` (or create): button enabled when verdict loaded, disabled during generation

---

## Dev Notes

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

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
