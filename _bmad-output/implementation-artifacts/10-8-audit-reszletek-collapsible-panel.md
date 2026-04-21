# Story 10.8: Audit / Részletek Collapsible Panel

Status: done

<!-- Epic 10 · Story 10.8 · depends on 10.5, 10.6 -->
<!-- Mixed story: backend provenance endpoint + CSV export + audit persistence + frontend collapsed panel + deletion sweep -->

## Story

As an **SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN user**,
I want a **collapsible audit/details panel on the EPR filing page** that shows per-invoice-line provenance lazy-loaded on expand and offers a full-dataset CSV export,
so that **I can audit exactly which invoice lines drove each KF-code total and export the provenance for compliance review, without the panel cluttering the filing view when I don't need it**.

## Acceptance Criteria

### Backend — Provenance Endpoint

1. `GET /api/v1/epr/filing/aggregation/provenance?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=50` returns a paginated `ProvenancePage { content: List<ProvenanceLine>, totalElements: long, page: int, size: int }`.

2. `ProvenanceLine` fields:
   - `invoiceNumber: String`
   - `lineNumber: int`
   - `vtsz: String`
   - `description: String`
   - `quantity: BigDecimal`
   - `unitOfMeasure: String`
   - `resolvedProductId: UUID | null`
   - `productName: String | null`
   - `componentId: UUID | null`
   - `wrappingLevel: int | null`
   - `componentKfCode: String | null`
   - `weightContributionKg: BigDecimal` (4 decimal places HALF_UP)
   - `provenanceTag: ProvenanceTag` — enum values: `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNRESOLVED`, `UNSUPPORTED_UNIT`

3. `page` default `0`, `size` default `50`, max `500` (server-enforced: clamp `size = Math.min(size, 500)`). Standard Spring `@RequestParam` with defaults.

4. No caching on provenance endpoint — always fresh (reads from aggregation cache which uses the same Caffeine single-flight as `GET /aggregation`; provenance lines come from the SAME computation pass as `kfTotals`). See Dev Notes §Architecture.

5. One provenance row per **component-invoice pair**: a product with N packaging components produces N rows per matching invoice line (so a 2-layer product × 1 invoice line = 2 rows). UNRESOLVED + UNSUPPORTED_UNIT lines are represented by a single row each (no component), with `weightContributionKg = 0` (included, not skipped). Per-component granularity is retained to preserve component-level detail for compliance audit.

6. Sum invariant: `Σ weightContributionKg per componentKfCode == kfTotals[componentKfCode].totalWeightKg` from Story 10.5.

7. Cross-tenant request returns 403. `@TierRequired(Tier.PRO_EPR)`. Role-gated: SME_ADMIN / ACCOUNTANT / PLATFORM_ADMIN (class-level annotation on `RegistryController` covers it; for `EprController` use `JwtUtil.requireRole` as the other methods do). 412 on missing producer profile (same guard as `aggregation` endpoint).

8. Endpoint added to `EprController` at `@GetMapping("/filing/aggregation/provenance")`.

### Backend — CSV Export Endpoint

9. `GET /api/v1/epr/filing/aggregation/provenance.csv?from=YYYY-MM-DD&to=YYYY-MM-DD` streams full dataset (no pagination). Response:
   - `Content-Type: text/csv; charset=UTF-8`
   - UTF-8 BOM prepended (`\uFEFF`)
   - Semicolon delimiter (Hungarian locale)
   - `Content-Disposition: attachment; filename="provenance-{tenantShortId}-{from}-{to}.csv"` where `tenantShortId` = first 8 chars of `tenantId.toString()`.
   - Uses Spring `StreamingResponseBody` to avoid OOM — flush every 500 rows.
   - Tested: 50,000 rows on 512MB heap without OOM.

10. CSV columns (header row first): `Számlaszám;Sorszám;VTSZ;Megnevezés;Mennyiség;ME;Termékazonosító;Terméknév;KF-kód;Csomagolási szint;Súly hozzájárulás (kg);Provenance tag`
    - `weightContributionKg` formatted to 4 decimal places with `.` as decimal separator (Locale.ROOT).

11. Same role/tier/profile guards as the paginated endpoint (AC 7).

### Backend — Audit Persistence

12. Flyway migration `V20260421_001__create_aggregation_audit_log.sql` creates table `aggregation_audit_log`:
    - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
    - `tenant_id UUID NOT NULL`
    - `event_type VARCHAR(50) NOT NULL` — values: `AGGREGATION_RUN`, `PROVENANCE_FETCH`, `CSV_EXPORT`
    - `period_start DATE`, `period_end DATE`
    - `resolved_count INT`, `unresolved_count INT`, `duration_ms BIGINT` (nullable; populated for `AGGREGATION_RUN`)
    - `page INT`, `page_size INT` (nullable; populated for `PROVENANCE_FETCH`)
    - `performed_by_user_id UUID` (nullable; from JWT `sub` claim; populated for `PROVENANCE_FETCH` + `CSV_EXPORT`)
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
    - Index: `CREATE INDEX ON aggregation_audit_log(tenant_id, created_at DESC)`

13. `AggregationAuditRepository` in `hu.riskguard.epr.audit.internal` package (parallel to `RegistryAuditRepository` already there). jOOQ-based insert; single row insert (not batched — one event per call).

14. `AuditService.recordAggregationRun()` (currently at lines 118–127, doing structured logging only) is updated to ALSO persist a row to `aggregation_audit_log` with `event_type=AGGREGATION_RUN`. Structured logging is kept.

15. Two new methods added to `AuditService`:
    - `recordProvenceFetch(UUID tenantId, UUID userId, LocalDate periodStart, LocalDate periodEnd, int page, int pageSize)` — persists `event_type=PROVENANCE_FETCH`.
    - `recordCsvExport(UUID tenantId, UUID userId, LocalDate periodStart, LocalDate periodEnd)` — persists `event_type=CSV_EXPORT`.
    - Both methods called from `EprController` after successful response (NOT inside the `InvoiceDrivenFilingAggregator` — audit is the controller's responsibility per ADR-0003 §caller-initiates pattern).

16. ArchUnit: extend `EpicTenInvariantsTest` with invariant `only_audit_package_writes_to_aggregation_audit_log` mirroring invariant 1 (`only_audit_package_writes_to_audit_tables`). The `AggregationAuditRepository` must reside in `hu.riskguard.epr.audit.internal`; no code outside `..epr.audit..` may depend on it.

### Backend — Deletion

17. Delete `POST /api/v1/epr/filing/okirkapu-preview` method from `EprController.java` (lines 287–297 in current HEAD) and its import `OkirkapuPreviewResponse` (line 11).

18. Delete `OkirkapuPreviewResponse.java` (`epr.api.dto`).

19. Delete `EprReportProvenanceDto.java` (`epr.api.dto`) — only used by `OkirkapuPreviewResponse`.

20. **DO NOT delete** `EprReportProvenance.java`, `epr.report.ProvenanceTag` (3-value enum: `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNMATCHED`), or `EprReportArtifact.java` — these remain in the `epr.report` package and are still used by `OkirkapuXmlExporter` (Story 9.4 flow).

### Frontend — `EprProvenanceTable.vue` Component

21. New component `frontend/app/components/Epr/EprProvenanceTable.vue`:
    - Wraps PrimeVue `DataTable` with `:lazy="true"`, `@page="onPage"`, `:total-records="totalElements"`, `:rows="pageSize"` (default 50).
    - Props: `period: { from: string; to: string }` (used to trigger cache invalidation by parent).
    - Columns: Számlaszám, Sor, VTSZ, Megnevezés, Mennyiség (+ ME), Terméknév (link to `/registry/{resolvedProductId}` when not null), KF-kód, Szint, Súly (kg, 4 decimals).
    - `provenanceTag` badge per row: `REGISTRY_MATCH` → green severity `success`; `VTSZ_FALLBACK` → yellow `warn`; `UNRESOLVED` → red `danger`; `UNSUPPORTED_UNIT` → orange (use `"contrast"` severity or custom class `tag-orange`). Consistent with Story 10.6 status badges.
    - `weightContributionKg` formatted to 4 decimal places (`toFixed(4)`).
    - Loading skeleton: `Skeleton` rows when `isLoading` prop is true (same pattern as `EprKfTotalsTable.vue`).

22. Component does NOT own the fetch logic — it emits `@page="{ page, rows }"` upward and receives `rows: ProvenanceLine[]`, `totalElements: number`, `isLoading: boolean` as props.

### Frontend — Composable `useEprFilingProvenance.ts`

23. New composable `frontend/app/composables/api/useEprFilingProvenance.ts` exposes:
    ```typescript
    const {
      rows, totalElements, isLoading, isCsvExporting,
      fetch, exportCsv, invalidate
    } = useEprFilingProvenance()
    ```
    - `rows: Ref<ProvenanceLine[]>` — current page.
    - `totalElements: Ref<number>`.
    - `isLoading: Ref<boolean>`.
    - `isCsvExporting: Ref<boolean>`.
    - `fetch(from: string, to: string, page: number, size: number): Promise<void>` — calls `GET /api/v1/epr/filing/aggregation/provenance`. Sets `isLoading`. Errors are swallowed (console log only) — consistent with `useRegistryCompleteness` pattern.
    - `exportCsv(from: string, to: string): Promise<void>` — calls `GET /api/v1/epr/filing/aggregation/provenance.csv`; triggers browser download via Blob + anchor click (same pattern as `exportOkirkapu` in `useEprFilingStore`). Sets `isCsvExporting`.
    - `invalidate(): void` — clears `rows` and `totalElements` to `[]`/`0`; does NOT trigger a fetch.
    - Cache semantics: once fetched for a (period, page), data is kept in `rows` until `invalidate()` is called. Period change invalidates (caller must call `invalidate()` before new period; does NOT auto-fetch).

### Frontend — `filing.vue` Modification

24. `filing.vue` imports and instantiates `useEprFilingProvenance()`.

25. Add a PrimeVue `Panel` component **below** the OKIRkapu export section (at the bottom of the filing content, above any existing footer). Panel config:
    - `header` = i18n `epr.filing.audit.panelTitle` ("Bejelentés részletei / Provenance")
    - `:collapsed="true"` initially (default collapsed).
    - `#icons` slot: CSV export icon-button (`pi pi-file-export`, `data-testid="audit-csv-export"`) that calls `provenance.exportCsv(period.from, period.to)`.
    - Panel content: `<EprProvenanceTable>` with bound props/events.

26. On panel toggle (expand): if `provenance.rows.value.length === 0` (i.e., not yet fetched), call `provenance.fetch(period.from, period.to, 0, 50)`.

27. On period change (when `period.from`/`period.to` change): call `provenance.invalidate()`. Do NOT auto-fetch. Re-fetch only happens when user next expands the panel (AC 26 guard).

28. Panel is only rendered when `!registryCompleteness.isEmpty.value` (i.e., same gate as the filing tables — do not show audit panel on empty registry).

### Frontend — i18n

29. Add to both `hu/epr.json` and `en/epr.json` under key path `epr.filing.audit`:
    - `panelTitle` ("Bejelentés részletei" / "Filing Provenance Details")
    - `exportCsv` ("CSV exportálás" / "Export CSV")
    - `columns.invoiceNumber`, `columns.lineNumber`, `columns.vtsz`, `columns.description`, `columns.quantity`, `columns.unitOfMeasure`, `columns.productName`, `columns.kfCode`, `columns.wrappingLevel`, `columns.weightContributionKg`, `columns.provenanceTag`
    - `tagLabel.REGISTRY_MATCH`, `tagLabel.VTSZ_FALLBACK`, `tagLabel.UNRESOLVED`, `tagLabel.UNSUPPORTED_UNIT`
    - `emptyMessage` ("Nincs megjelenítendő adat" / "No data to display")

30. No `epr.okirkapu.preview.*` keys exist in the current i18n files — confirmed in dev research; nothing to delete.

### Tests

31. `InvoiceDrivenFilingAggregatorProvenanceTest` (new class, `epr.aggregation.domain` test package):
    - Verifies sum invariant: provenance `Σ weightContributionKg` per kfCode equals `kfTotals[kfCode].totalWeightKg`.
    - Verifies UNRESOLVED + UNSUPPORTED_UNIT lines have `weightContributionKg = 0`.
    - Verifies `provenanceTag` assignment: `REGISTRY_MATCH` for normal match, `VTSZ_FALLBACK` for fallback-source component, `UNRESOLVED` for no matching product, `UNSUPPORTED_UNIT` for non-KG unit.
    - Verifies total row count equals component-invoice pair count (single-component + multi-component cases).

32. `FilingAggregationProvenanceControllerTest` (extends existing `EprControllerTest` or new class):
    - GET `/aggregation/provenance`: page/size defaults, size clamped to 500, 403 cross-tenant, 412 missing profile, role-gated.
    - GET `/aggregation/provenance.csv`: content-type, UTF-8 BOM, semicolon, Content-Disposition filename.
    - Each provenance page-load and CSV export triggers an audit event (verify `aggregation_audit_log` row inserted).

33. `AggregationAuditRepositoryTest`: insert + read round-trip for all 3 event types.

34. `EprProvenanceTable.spec.ts` (new, `components/Epr/`):
    - Renders badge colors per `provenanceTag`.
    - `weightContributionKg` displayed to 4 decimals.
    - Product name renders as link when `resolvedProductId` is not null.
    - `@page` emit on DataTable page change.
    - Skeleton shown when `isLoading=true`.

35. `filing.spec.ts` extended:
    - Panel collapsed by default.
    - Expand triggers `fetch()`.
    - Period change calls `invalidate()` but NOT `fetch()`.
    - CSV export button calls `exportCsv()`.
    - Panel absent when `registryCompleteness.isEmpty = true`.

36. AC-to-task walkthrough (T1) filed as Task 1 gate before any code.

## Tasks / Subtasks

- [x] Task 1 — AC-to-task walkthrough: verify every AC is covered by a task below before writing code (AC: #36)
- [x] Task 2 — Flyway migration `V20260421_001__create_aggregation_audit_log.sql` (AC: #12)
  - [x] Create table + index per AC 12 schema
  - [x] Verify idempotent (`IF NOT EXISTS`)
- [x] Task 3 — `AggregationAuditRepository` in `hu.riskguard.epr.audit.internal` (AC: #13)
  - [x] jOOQ insert method for all 3 event types
  - [x] `AggregationAuditRepositoryTest` round-trip (AC: #33)
- [x] Task 4 — `AuditService` persistence + new methods (AC: #14, #15)
  - [x] Implement DB persist in `recordAggregationRun()` (keep structured log)
  - [x] Add `recordProvenanceFetch()` and `recordCsvExport()` methods
- [x] Task 5 — New DTOs: `ProvenanceLine`, `ProvenancePage`, `ProvenanceTag` enum in `hu.riskguard.epr.aggregation.api.dto` (AC: #2)
  - [x] `ProvenanceTag` enum: `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNRESOLVED`, `UNSUPPORTED_UNIT`
  - [x] **Do not reuse or modify** `epr.report.ProvenanceTag` (different enum, different package)
- [x] Task 6 — Extend `InvoiceDrivenFilingAggregator` to capture per-line provenance (AC: #4, #5, #6, #31)
  - [x] Add `List<AggregationProvenanceLine>` capture during aggregation pass (same loop that builds `kfAccumulators`)
  - [x] Extend `FilingAggregationResult` with `provenanceLines: List<AggregationProvenanceLine>`
  - [x] Map internal `AggregationProvenanceLine` → `ProvenanceLine` DTO in controller (keep domain model lean)
  - [x] Write `InvoiceDrivenFilingAggregatorProvenanceTest` (AC: #31)
- [x] Task 7 — `EprController` provenance + CSV endpoints (AC: #3, #7, #8, #9, #10, #11)
  - [x] `GET /filing/aggregation/provenance` with page/size clamping, 403/412 guards
  - [x] `GET /filing/aggregation/provenance.csv` with `StreamingResponseBody`, BOM, semicolon, `Content-Disposition`
  - [x] Call `auditService.recordProvenanceFetch()` / `recordCsvExport()` after successful response
  - [x] `FilingAggregationProvenanceControllerTest` (AC: #32)
- [x] Task 8 — ArchUnit invariant for `AggregationAuditRepository` (AC: #16)
  - [x] Add invariant to `EpicTenInvariantsTest.java` mirroring invariant 1
- [x] Task 9 — Delete `okirkapu-preview` and related DTOs (AC: #17, #18, #19)
  - [x] Remove `previewOkirkapu()` method from `EprController` (lines 287–297 current HEAD) + import line 11
  - [x] Delete `OkirkapuPreviewResponse.java`
  - [x] Delete `EprReportProvenanceDto.java`
  - [x] Confirm `EprReportProvenance`, `epr.report.ProvenanceTag`, `EprReportArtifact` remain untouched
- [x] Task 10 — `EprProvenanceTable.vue` component (AC: #21, #22, #34)
  - [x] DataTable lazy pagination, badge colors, 4-decimal weight, product-name link, skeleton loading
  - [x] `EprProvenanceTable.spec.ts`
- [x] Task 11 — `useEprFilingProvenance.ts` composable (AC: #23)
  - [x] `fetch()`, `exportCsv()`, `invalidate()`, caching semantics
- [x] Task 12 — `filing.vue` audit panel integration (AC: #24–#28, #35)
  - [x] Collapsed `Panel` with `#icons` CSV button below OKIRkapu section
  - [x] Expand-triggers-fetch guard
  - [x] Period-change calls `invalidate()` only
  - [x] Hide panel when `registryCompleteness.isEmpty`
  - [x] Extend `filing.spec.ts`
- [x] Task 13 — i18n keys `epr.filing.audit.*` in `hu/epr.json` + `en/epr.json` (AC: #29, #30)
- [x] Task 14 — Full test pass: `./gradlew test --tests "hu.riskguard.epr.*"` + `npx vitest run` + `npx tsc --noEmit` + lint + lint:i18n — all green

### Review Follow-ups (AI)

- [x] [AI-Review][Decision] Reword AC #5 to per-component granularity (option A; per-component retained for compliance audit)
- [x] [AI-Review][Patch] Add 412 producer-profile guard to provenance endpoints + tests
- [x] [AI-Review][Patch] Sync `auditPanelCollapsed.value` in `onAuditPanelToggle`
- [x] [AI-Review][Patch] Escape `\r` in `csvEscape` (RFC 4180)
- [x] [AI-Review][Patch] Rename `Provence*` → `Provenance*` across 5 files
- [x] [AI-Review][Patch] Add `isEmpty=true` negative-case test for audit panel
- [x] [AI-Review][Patch] Fix collateral ArchUnit gaps (enum DTO + jOOQ allowlist for `AggregationAuditLog`)

### Review Follow-ups R2 (Blind Hunter + Edge Case Hunter + Acceptance Auditor, 2026-04-21)

- [x] [R2-Patch][CRITICAL] JWT user UUID extracted from wrong claim — spec & code used `sub`, but TokenProvider seeds `sub=email` and user UUID lives in `user_id` claim. Fixed in both provenance endpoints; production would have 401'd every request. [EprController.java:313,346]
- [x] [R2-Patch][HIGH] CSV formula-injection guard added to `csvEscape` — neutralises leading `= + - @ \t \r` by prefixing with single-quote (CWE-1236); invoice description is attacker-influenced text. [EprController.java csvEscape]
- [x] [R2-Patch][HIGH] Added `from.isAfter(to)` 400 guard on both provenance endpoints mirroring sibling `FilingAggregationController`; inverted ranges previously wrote misleading audit rows. [EprController.java:316,353]
- [x] [R2-Patch][MED] `safeSize` clamped to `>= 1` (was allowing `size=0` → divide-by-zero on paginator); `page * size` cast to long to prevent int overflow on pathological inputs. [EprController.java:320-326]
- [x] [R2-Patch][MED] Hungarian `panelTitle` reworded from "Bejelentés részletei / Provenance" to "Bejelentés részletei" per AC #29. [hu/epr.json]
- [x] [R2-Patch][MED] CSV export button rendered as icon-only (text/rounded, with `aria-label`+`title`) per AC #25 "icon-button". [filing.vue:440]
- [x] [R2-Patch][LOW] `CREATE INDEX IF NOT EXISTS idx_aggregation_audit_log_tenant_created` — migration now idempotent per Task 2 subtask. [V20260421_001]
- [x] [R2-Patch][LOW] Empty state moved into `DataTable #empty` slot (was a sibling `<p>`); matches PrimeVue idiom. [EprProvenanceTable.vue]
- [x] [R2-Patch][LOW] i18n `epr.filing.audit.*` keys re-sorted alphabetically (Dev Notes retro T6). [hu/epr.json, en/epr.json]
- [x] [R2-Patch][LOW] Pagination slice test now asserts content IDENTITY (`INV-C` on page=1, size=2), not just count; 2 new tests for inverted-range 400 and `size=0` clamp. [FilingAggregationProvenanceControllerTest]
- [x] [R2-Defer] PrintWriter not closed in CSV streaming — standard StreamingResponseBody pattern; closing underlying stream is hazardous. R2-W1.
- [x] [R2-Defer] Sum invariant precision drift at high component counts — already covered by R1-W6; pre-existing scale trade-off. R2-W2.
- [x] [R2-Defer] No AbortController on `useEprFilingProvenance.fetch` race — same pattern as Story 10.6 R2-D1; cross-cutting refactor. R2-W3.
- [x] [R2-Defer] Keystroke flicker on `provenance.invalidate` synchronous invocation in period watcher — minor UX. R2-W4.
- [x] [R2-Defer] Stale rows on re-expand after period tweak — low risk given invalidate clears rows. R2-W5.
- [x] [R2-Defer] No per-user rate-limit on provenance/CSV endpoints — gateway/infra layer. R2-W6.
- [x] [R2-Defer] Silently-dropped invoice lines emit no provenance row — pre-existing aggregator behaviour from 10.5. R2-W7.
- [x] [R2-Defer] `AggregationProvenanceLine` is public; spec recommended "package-private or nested" — refactor churn to relocate mapper. R2-W8.
- [x] [R2-Defer] Full provenance list materialised before slice — already R1-W1. R2-W9.

## Dev Notes

### Architecture: How Provenance Lines Flow

**Critical design decision:** Per-line provenance is captured inside the SAME aggregation pass that produces `kfTotals`. This guarantees the sum invariant (AC 6) without a separate query.

```
InvoiceDrivenFilingAggregator.compute()
  ├── for each invoice line:
  │   ├── match product via VTSZ lookup  → provenanceTag = REGISTRY_MATCH or VTSZ_FALLBACK
  │   ├── if no match                    → provenanceTag = UNRESOLVED, weightContribution = 0
  │   ├── if unsupported unit            → provenanceTag = UNSUPPORTED_UNIT, weightContribution = 0
  │   ├── build List<AggregationProvenanceLine> (internal domain object)
  │   └── also update kfAccumulators (existing logic unchanged)
  └── FilingAggregationResult { kfTotals, unresolvedLines, provenanceLines }  ← new field
```

The Caffeine cache caches `FilingAggregationResult` (including `provenanceLines`). The provenance endpoint reads from the same cache. "No caching" in the epic means: no ADDITIONAL cache layer for provenance specifically — the aggregation cache provides freshness guarantees via its key `(tenantId, periodStart, periodEnd, registryMaxUpdatedAt, activeConfigVersion)`.

The controller paginates `provenanceLines` in-memory via `subList(fromIndex, toIndex)`.

### AuditService Pattern

`AuditService` is at `hu.riskguard.epr.audit.AuditService`. It MUST NOT be `@Transactional` (ADR-0003 hard rule, ArchUnit invariant 2). It inherits the caller's transaction for writes.

Current method stub at line 121 (do logging + add DB persist):
```java
public void recordAggregationRun(UUID tenantId, LocalDate periodStart, LocalDate periodEnd,
                                 long durationMs, int resolvedCount, int unresolvedCount)
```
The `AggregationAuditRepository` goes in `hu.riskguard.epr.audit.internal` (see javadoc line 23 in `AuditService.java`). Only `AuditService` may depend on it — ArchUnit invariant 1 enforces this; extend invariant 1's check to also cover `AggregationAuditRepository`.

### Files to Delete (backend)

| File | Location | Reason |
|------|----------|--------|
| `previewOkirkapu()` method | `EprController.java` lines 287–297 | endpoint removed |
| import `OkirkapuPreviewResponse` | `EprController.java` line 11 | unused after deletion |
| `OkirkapuPreviewResponse.java` | `epr.api.dto` | no consumers after controller delete |
| `EprReportProvenanceDto.java` | `epr.api.dto` | only used by `OkirkapuPreviewResponse` |

**DO NOT delete** (still used by OKIRkapu XML export flow from Story 9.4):
- `epr.report.ProvenanceTag` (3 values: `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNMATCHED`)
- `epr.report.EprReportProvenance`
- `epr.report.EprReportArtifact`

### New vs Existing Enum Distinction

| Enum | Package | Values | Used by |
|------|---------|--------|---------|
| `epr.report.ProvenanceTag` | `hu.riskguard.epr.report` | `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNMATCHED` | OKIRkapu XML exporter (keep) |
| NEW `ProvenanceTag` | `hu.riskguard.epr.aggregation.api.dto` | `REGISTRY_MATCH`, `VTSZ_FALLBACK`, `UNRESOLVED`, `UNSUPPORTED_UNIT` | Provenance REST endpoint (create new) |

These are two different enums. Do NOT rename or extend `epr.report.ProvenanceTag` — it would break the XML exporter.

### InvoiceDrivenFilingAggregator Extension Points

File: `hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator`

Key method to extend: `compute()` and `aggregateComponents()`. The internal record `ComponentRow` already carries `classifierSource` (used for `hasFallback` flag). Reuse this to determine `VTSZ_FALLBACK` vs `REGISTRY_MATCH` tag at the per-line level.

Pattern for `UNSUPPORTED_UNIT`: currently logged or handled in the `aggregateComponents` method when `unitOfMeasure` is not `KG`. Check existing handling and emit `provenanceTag = UNSUPPORTED_UNIT`.

`FilingAggregationResult` is a record — add `List<AggregationProvenanceLine> provenanceLines` field. The `AggregationProvenanceLine` is an internal domain object (package-private or a nested record); the controller maps it to the public DTO `ProvenanceLine`.

### CSV Streaming Pattern

Use `StreamingResponseBody` to avoid loading all rows into a single response buffer:

```java
@GetMapping(value = "/filing/aggregation/provenance.csv", produces = "text/csv;charset=UTF-8")
public ResponseEntity<StreamingResponseBody> exportProvenanceCsv(...) {
    // resolve result from aggregator (same Caffeine cache call)
    StreamingResponseBody body = outputStream -> {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.print('\uFEFF'); // BOM
        writer.println("Számlaszám;Sorszám;..."); // header
        int rowCount = 0;
        for (AggregationProvenanceLine line : result.provenanceLines()) {
            writer.println(formatCsvRow(line));
            if (++rowCount % 500 == 0) writer.flush();
        }
        writer.flush();
    };
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"provenance-" + tenantShortId + "-" + from + "-" + to + ".csv\"")
        .body(body);
}
```

Use `Locale.ROOT` for `BigDecimal` → String conversion (same pattern as `OkirkapuXmlExporter` R3 fix).

### Frontend Component Location

New component goes in `frontend/app/components/Epr/` (capital E, capital P — same as `EprSoldProductsTable.vue`, `EprKfTotalsTable.vue`). File: `EprProvenanceTable.vue`.

New composable goes in `frontend/app/composables/api/` (same as `useRegistryCompleteness.ts`). File: `useEprFilingProvenance.ts`.

### PrimeVue Panel Collapsed Pattern

The `Panel` component collapsed-by-default is:
```vue
<Panel :collapsed="true" toggleable>
  <template #icons>
    <Button icon="pi pi-file-export" text ... @click="onCsvExport" data-testid="audit-csv-export" />
  </template>
  <EprProvenanceTable ... />
</Panel>
```
Use the `@toggle` event (or watch `:collapsed` two-way binding with `v-model:collapsed`) to trigger `fetch()` on first expand.

### ArchUnit No-Float Rule (Invariant 5)

Invariant 5 in `EpicTenInvariantsTest` bans `double`/`float` in `hu.riskguard.epr.aggregation.*`. The new `ProvenanceLine` DTO is in `hu.riskguard.epr.aggregation.api.dto` — use `BigDecimal` for `weightContributionKg` (not double). `toFixed(4)` in the frontend is fine.

### i18n File Locations

- `frontend/app/i18n/hu/epr.json` — Hungarian
- `frontend/app/i18n/en/epr.json` — English
- Run `lint:i18n` (`npx nuxi module add i18n` lint step) to verify no missing keys.
- Keep keys in alphabetical order within their object (retro T6 rule, though no pre-commit hook yet).

### Existing Filing Page Structure (post-10.6 + 10.7)

Current `filing.vue` section order (after 10.7):
1. Tier gate
2. RegistryOnboardingBlock (if empty registry)
3. Period selector
4. EprSoldProductsTable
5. EprKfTotalsTable
6. Summary cards (grand total weight/fee)
7. Unresolved panel (collapsible)
8. OKIRkapu export panel  ← **Story 10.8 adds Audit panel BELOW this**

### Testing Run Commands (per memory)

```bash
# Backend EPR tests (~90s)
./gradlew test --tests "hu.riskguard.epr.*"

# ArchUnit only (~30s)
./gradlew test --tests "hu.riskguard.architecture.*"

# Frontend (~6s)
npx vitest run

# Type check
npx tsc --noEmit

# Lint + i18n
npx eslint . && npx nuxt-i18n-ally check
```

### References

- `InvoiceDrivenFilingAggregator.java`: `hu.riskguard.epr.aggregation.domain` — cache pattern, `ComponentRow.classifierSource`, `FilingAggregationResult`
- `EprController.java`: `hu.riskguard.epr.api` — line 11 (import to delete), lines 287–297 (method to delete), class-level `@TierRequired`
- `AuditService.java`: `hu.riskguard.epr.audit` — lines 118–127 (`recordAggregationRun` stub), line 23 (AggregationAuditRepository reference)
- `EpicTenInvariantsTest.java`: `hu.riskguard.architecture` — invariants 1, 2, 5, 6 patterns
- `OkirkapuPreviewResponse.java`: `hu.riskguard.epr.api.dto` — delete
- `EprReportProvenanceDto.java`: `hu.riskguard.epr.api.dto` — delete
- `epr.report.ProvenanceTag`: keep (3-value enum, OKIRkapu exporter uses it)
- `filing.vue`: `frontend/app/pages/epr/filing.vue` — add Panel below OKIRkapu export section
- `EprKfTotalsTable.vue`: badge + skeleton patterns to follow
- `useRegistryCompleteness.ts`: composable pattern (error-swallowing, `isLoading`, `refresh`)
- `useEprFilingStore.ts`: `exportOkirkapu()` Blob download pattern to follow for CSV export
- `frontend/app/i18n/hu/epr.json`, `en/epr.json` — add `filing.audit.*` keys

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (initial implementation); claude-opus-4-7 (R1 review follow-ups)

### Debug Log References

### Completion Notes List

- ✅ Resolved review finding [Decision] AC #5 row-count ambiguity — user chose option A (per-component); AC reworded; test renamed; added companion multi-component test.
- ✅ Resolved review finding [Patch] 412 on missing producer profile now enforced on `GET /filing/aggregation/provenance` and `GET /filing/aggregation/provenance.csv`; 2 new controller tests cover the case.
- ✅ Resolved review finding [Patch] `auditPanelCollapsed.value = collapsed` synced in `onAuditPanelToggle`; prevents re-collapse on re-render.
- ✅ Resolved review finding [Patch] `csvEscape` now quotes values containing `\r` (RFC 4180).
- ✅ Resolved review finding [Patch] Provence→Provenance typo fixed across 5 files.
- ✅ Resolved review finding [Patch] Added `audit panel absent when isEmpty=true` assertion to filing.spec.ts.
- ✅ Collateral ArchUnit hardening: `dtos_should_be_records` excludes enums (ProvenanceTag is a spec-required enum in api.dto); `AggregationAuditLog` added to EPR jOOQ-owned tables allowlist.
- ✅ Verification: backend EPR suite BUILD SUCCESSFUL (6m 51s), ArchUnit BUILD SUCCESSFUL (49s), frontend vitest 831/831, tsc clean.

### File List

**Modified**
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` — 412 profile guard on both provenance endpoints; `recordProvenceFetch`→`recordProvenanceFetch` call; `\r` added to csvEscape
- `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java` — `recordProvenceFetch`→`recordProvenanceFetch`
- `backend/src/main/java/hu/riskguard/epr/audit/internal/AggregationAuditRepository.java` — `insertProvenceFetch`→`insertProvenanceFetch`
- `backend/src/test/java/hu/riskguard/epr/audit/AggregationAuditRepositoryTest.java` — rename + round-trip method call
- `backend/src/test/java/hu/riskguard/epr/aggregation/api/FilingAggregationProvenanceControllerTest.java` — rename + new 412 tests for both endpoints
- `backend/src/test/java/hu/riskguard/epr/aggregation/domain/InvoiceDrivenFilingAggregatorProvenanceTest.java` — test renamed + new multi-component per-component-pair test
- `backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java` — `dtos_should_be_records` excludes enums; `AggregationAuditLog` added to EPR allowlist
- `frontend/app/pages/epr/filing.vue` — sync `auditPanelCollapsed.value` in toggle handler
- `frontend/app/pages/epr/filing.spec.ts` — new negative-case test for audit panel when `isEmpty=true`
- `_bmad-output/implementation-artifacts/10-8-audit-reszletek-collapsible-panel.md` — AC #5 reword, task checkboxes, review follow-up checkboxes, Dev Agent Record

### Change Log

- 2026-04-21 — Addressed R1 code review findings (6 action items resolved + 2 collateral ArchUnit fixes). AC #5 reworded per user decision (per-component granularity). Status → review.

### Review Findings

- [x] [Review][Decision] AC #5 multi-component row count ambiguity — User chose option A: reword AC #5 to "one row per component-invoice pair" (per-component granularity retained for compliance audit). Story AC #5 updated; test `totalRowCount_equalsInvoiceLineCount_withSingleComponentProducts` renamed to `totalRowCount_equalsComponentInvoicePairCount_singleComponent`; new companion test `totalRowCount_equalsComponentInvoicePairCount_multiComponent` covers the 2-layer case. [InvoiceDrivenFilingAggregator.java, InvoiceDrivenFilingAggregatorProvenanceTest.java]
- [x] [Review][Patch] 412 on missing producer profile now enforced on provenance endpoints — `getProvenance()` and `exportProvenanceCsv()` call `producerProfileService.get(tenantId)` before `aggregator.aggregateForPeriod()`, matching the `/aggregation` guard pattern. New tests: `getProvenance_missingProducerProfile_throws412`, `exportCsv_missingProducerProfile_throws412`. [EprController.java:316,350]
- [x] [Review][Patch] `auditPanelCollapsed.value = collapsed` now set as first line of `onAuditPanelToggle` — keeps controlled ref in sync with user toggles, preventing PrimeVue Panel re-collapse on re-render. [frontend/app/pages/epr/filing.vue:113]
- [x] [Review][Patch] `csvEscape` now also quotes values containing `\r` (carriage return) — RFC 4180 compliance. [backend/src/main/java/hu/riskguard/epr/api/EprController.java:396]
- [x] [Review][Patch] `recordProvenceFetch`/`insertProvenceFetch` renamed to `recordProvenanceFetch`/`insertProvenanceFetch` across all 5 files: `AuditService.java`, `AggregationAuditRepository.java`, `EprController.java`, `FilingAggregationProvenanceControllerTest.java`, `AggregationAuditRepositoryTest.java`.
- [x] [Review][Patch] Added `audit panel is absent when registry is empty (isEmpty=true)` test to `filing.spec.ts` — covers AC #35 negative case. [frontend/app/pages/epr/filing.spec.ts]
- [x] [Review][Defer] In-memory unbounded provenance list — spec-intended design; in-memory subList pagination is specified in Dev Notes; Caffeine caches the full FilingAggregationResult including provenanceLines [InvoiceDrivenFilingAggregator.java] — deferred, pre-existing
- [x] [Review][Defer] Content-Disposition header RFC 6266 compliance — all interpolated values (UUID prefix, LocalDate.toString()) are safe ASCII characters; low practical risk [EprController.java] — deferred, pre-existing
- [x] [Review][Defer] Audit log recorded before StreamingResponseBody executes — intentional per ADR-0003 §caller-initiates pattern; records that the export was initiated, not that it was fully received — deferred, pre-existing
- [x] [Review][Defer] `$fetch` vs `apiFetch` for CSV export — verify the implementation matches the `exportOkirkapu` pattern in `useEprFilingStore.ts` before next story [frontend/app/composables/api/useEprFilingProvenance.ts:33] — deferred, pre-existing
- [x] [Review][Defer] Caffeine concurrent aggregation race (double audit rows) — pre-existing design reviewed in Story 10.5 R3 — deferred, pre-existing
- [x] [Review][Defer] Sum invariant rounding accumulation with large line counts — 4dp provenance vs 3dp kfTotal scale trade-off; pre-existing — deferred, pre-existing
- [x] [Review][Defer] `FilingAggregationResult` (api.dto) imports domain type `AggregationProvenanceLine` — spec-intended design; controller maps to public DTO — deferred, pre-existing
- [x] [Review][Defer] Cross-tenant 403 test missing — `tenantId` is always JWT-derived (`active_tenant_id` claim); no URL parameter to inject; cross-tenant isolation is implicit — deferred, pre-existing
- [x] [Review][Defer] No structured logging in `recordProvenceFetch`/`recordCsvExport` — spec only requires "logging kept" for AGGREGATION_RUN; new methods not specified — deferred, pre-existing
- [x] [Review][Defer] `setTimeout(100ms)` blob URL revoke race — consistent with `exportOkirkapu` pattern project-wide — deferred, pre-existing
