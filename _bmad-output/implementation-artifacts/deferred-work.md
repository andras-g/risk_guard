# Deferred Work

## Deferred from: code review of 5-0-rename-seasonal-to-recurring (2026-03-26)

- `updateTemplate` null recurring silently defaults to `true` — by design; frontend always sends explicit value; may surprise raw API clients. Consider `@NotNull` + reject null on PUT if API is ever exposed externally.
- DB migration not idempotent if re-run (double-flip of boolean values) — Flyway checksum prevents this in practice; acceptable for now.
- `EprRepository.insertTemplate` fetchOne can return null without null-check — pre-existing jOOQ pattern; fail-fast acceptable given controller maps empty Optional to 500.
- `copyFromQuarter` has no deduplication guard — double-submission creates duplicate records; consider idempotency key or unique constraint on (tenant_id, name, quarter).
- `toggleRecurring` store has TOCTOU race for rapid toggles — last-write-wins on in-place update; debounce or request cancellation would improve UX.
- `CopyQuarterDialog` closes optimistically before server confirms copy success — UX design choice; acceptable since parent error toast covers the failure case.
- `findByTenantAndQuarter` uses UTC `CREATED_AT` for quarter membership — tenants not in UTC may see templates in wrong quarter; requires dedicated `quarter`/`year` columns to fix correctly.
- No upper-bound validation on `sourceYear` or `baseWeightGrams` — API hardening; low risk for internal tool.
- `requireUuidClaim` should be extracted to a shared utility (Epic 4 retro T1) — still private method in `EprController`; extract before Epic 6.

## Deferred from: code review of 5-1-watchlist-bulk-pdf-export-and-mobile-dispatcher (2026-03-26)

- No index on `latest_sha256_hash` column — column not used in WHERE/JOIN now; add partial index before any hash-based lookup feature.
- `latestSha256Hash?: string | null` optional type in `api.d.ts` may persist beyond its TODO — resolve when OpenAPI pipeline is wired.
- `-Xmx1g` hardcoded in `build.gradle` without CI property override — advisory; low risk for current setup.
- `updateVerdictStatusWithHash` returns 0 rows when entry deleted mid-flight with no log warning — add warning log before Epic 6 monitoring hardening.
- Stale selection after watchlist refresh (object identity without `dataKey` on DataTable) — mitigated by store refresh; revisit if multi-source refresh becomes concurrent.
- Count suffix on "Share Report" label is minor deviation from AC 3 wording ("Export PDF (3)") — acceptable UX; revisit if share label UX is refined.

## Deferred from: code review of 5-2-quarterly-epr-filing-workflow (2026-03-26)

- W1: `EprRepository.findVerifiedByTenant()` added but unused — `calculateFiling()` uses `findAllByTenantWithOverride()` instead; dead code. May be used by Story 5.3 MOHU CSV export; remove or use then.
- W2: Template UUID leaked in error responses — "Template not found: `<uuid>`" exposes internal UUIDs enabling tenant enumeration oracle. Pre-existing pattern; consider generic "Resource not found" messages before public API exposure.
- W3: `Collectors.toMap` no-merge-function in `calculateFiling()` — throws `IllegalStateException` if `findAllByTenantWithOverride()` ever returns duplicate template records. Add merge function or verify uniqueness guarantee in repository.
- W4: `@Valid` does not cascade to null list elements in `FilingCalculationRequest` — malformed JSON `[null, {...}]` may bypass validation; add `@NotNull` annotations at list element level.
- W5: `NamingConventionTest` jOOQ record package path assumption — `extractRecordName()` assumes `.records` sub-package; verify against actual jOOQ codegen output path before adding more module rules.
- W6: Concurrent template delete causes 404 mid-calculation — optimistic-read pattern is acceptable for MVP; add transactional SELECT FOR SHARE if regulatory audit requirements emerge.
- W7: `BigDecimal` → JS `number` precision loss in `FilingCalculationResponse` TypeScript types — not practically reachable for EPR amounts; revisit if large industrial datasets are onboarded.
- W8: `filingStore.isLoading` is dead state — never set to `true`; filing loading display relies on `eprStore.isLoading`. Clean up unused field.
- W9: No `AbortController` for in-flight `calculate()` on navigation away — no crash risk; add cancellation if filing page gains a back-navigation confirmation guard.
