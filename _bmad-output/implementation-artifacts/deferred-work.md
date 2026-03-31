# Deferred Work

## Deferred from: code review of 6-2-manual-adapter-quarantine (2026-03-31)

- W1: Hand-rolled JSON string for audit `details` field — safe now (boolean primitive), but fragile pattern to copy; replace with `ObjectMapper` before any user-controlled string lands in that field.
- W2: `adapterName` path variable has no length/pattern validation at API boundary — SME_ADMIN gate + adapter-exists check limits risk; add `@Size`/`@Pattern` before public API exposure.
- W3: No confirmation dialog before quarantine toggle fires — i18n keys `quarantineConfirm`/`releaseConfirm` exist; wire up a confirm dialog when UX polish sprint runs.
- W4: Global banner fetches health once on mount — non-datasources pages show stale quarantine state on long sessions; admin page polling updates the shared store, so banner auto-refreshes when user visits datasources page; acceptable tradeoff.
- W5: `setQuarantined` INSERT ON CONFLICT may fail if adapter row is missing other NOT NULL columns (fresh DB before 6.1 startup listener runs) — rows guaranteed by CircuitBreakerEventListener startup; defend if adapter registration order ever changes.
- W6: `recordStateTransition` and `setQuarantined` write to the same `adapter_health` row without coordination — different columns updated; last-write on `updated_at` is acceptable; revisit if `updated_at` becomes load-bearing for analytics.
- W7: `findAll()` / `AdapterHealthRow` does not expose the `quarantined` DB flag — frontend infers quarantine from `circuitBreakerState === 'FORCED_OPEN'`; sufficient now, but expose the flag if DB/CB divergence detection is ever needed.
- W8: Startup quarantine restore race with in-flight health checks — `@PostConstruct` window is narrow; requires a startup barrier or ordered initialization to close fully.
- W9: `buildResponse` does full O(n) adapter reload for a single-adapter quarantine toggle — low-frequency admin operation; acceptable for MVP; optimize if adapter count grows large.
- W10: `actor_user_id` has no FK constraint on `admin_action_log` — intentional soft audit (no user table in this service); document as design decision in architecture notes.
- W11: `admin_action_log` missing indexes on `actor_user_id` and `action` — no audit viewer feature yet; add before Story 6.4 GDPR Search Audit Viewer.
- W12: `setQuarantined` phantom row: silently creates `adapter_health` row for a never-registered adapter — requires SME_ADMIN access; acceptable defensive behavior; add a health-row existence check if ghost rows become a monitoring concern.
- W13: `FORCED_OPEN` state conflated with quarantine in banner — only one code path creates `FORCED_OPEN` in the current system; add a `quarantined` flag to `AdapterHealthResponse` if disambiguation is ever needed.

## Deferred from: code review of 6-1-data-source-health-dashboard-the-heartbeat (2026-03-31)

- `CircuitBreakerEventListener.init()` registers only on CBs present at startup — `getAllCircuitBreakers()` misses lazily-created instances; use `registry.getEventPublisher().onEntryAdded()` for dynamic registration when new adapters are added.
- `dataSourceMode` is a global value applied to all adapters — multi-adapter deployments (e.g., demo + live NAV) will show incorrect per-adapter mode; requires per-adapter mode resolution in controller.
- No test for missing/null JWT `role` claim in `DataSourceAdminControllerTest` — edge case produces correct 403 but is untested; add a test with a JWT that has no role claim.
- Skeleton loading grid hardcoded to 3 placeholders — layout shift if actual adapter count differs; make count dynamic once adapter registry size is known at render time.
- ARIA announcement uses raw CB state enum value (`OPEN`) instead of past-tense verb phrase (`opened`) — i18n enhancement; add state → verb mapping in `en/hu admin.json`.
- Initial page paint has a brief empty grid before `onMounted` fires — `loading` starts as `false` so skeleton does not show until after the first fetch is triggered; initialise `loading: true` in store state.
- `$fetch` error handler in `useHealthStore.fetchHealth()` swallows HTTP status codes — stores only a string message; pre-existing pattern across other stores; improve to distinguish auth failures from server errors.
- `AdapterHealthResponse.from()` factory method is redundant — pure delegation to the canonical record constructor; cosmetic noise.
- `adapter_health.updated_at` has no `NOT NULL` constraint — minor DDL omission; low risk since upsert always supplies the value.
- `lastUpdated` relative timestamp in `datasources.vue` does not tick between 30 s polls — `formatRelative` is not time-reactive; shows stale "just now" until next reactive update; add a reactive interval or use a ticking composable.

## Deferred from: code review of 6-0-epic-6-foundation-technical-debt-cleanup (2026-03-30)

- `TenantFilter`: `UUID.fromString(tenantIdStr)` is called before any try/catch; a malformed `active_tenant_id` JWT claim propagates as unhandled `IllegalArgumentException` (500); pre-existing behaviour not introduced by this story.
- `AuditHistoryController` mixed exception response format: 401 now throws `ResponseStatusException` (via `JwtUtil`), while 404/400 paths still throw `ErrorResponseException` (RFC 9457 body); inconsistent JSON bodies on same endpoint; acknowledged by story spec as cross-cutting refactor for a future story.
- `TenantContext.CURRENT_TENANT` is `public static final` — any code can call `ScopedValue.where(CURRENT_TENANT, arbitrary).run(...)` to bind a spoofed tenant; protected by filter chain in production; design decision, not changeable without making `CURRENT_TENANT` package-private.
- `JwtUtilTest` missing empty-string claim value case (`""`) — handled by the `catch (IllegalArgumentException)` branch but not explicitly tested.
- Story 5.4 files (`filing.vue`, `index.vue`, `i18n/en/epr.json`, `i18n/hu/epr.json`) were implemented but not committed as part of Story 5.4; they appear in the Story 6.0 diff and should be committed together in the Story 6.0 commit.

## Deferred from: code review of 5-1a-my-audit-history-due-diligence-timeline (2026-03-30)

- TenantContext self-cleanup in `auditIngestorRefresh` — ingestor loop's `finally` handles it; risk only if method is ever called outside the ingestor loop context by future code.
- Multiple active mandates fan-out in `NotificationRepository` JOIN on `tenant_mandates` — no `DISTINCT ON` / `LIMIT 1` guard; if a tenant has two concurrent active mandates each watchlist entry produces duplicate partners; pre-existing issue.
- TOCTOU between `findAuditHistoryPage` and `countAuditHistory` — two separate queries; concurrent insert can cause `totalElements` to not match actual rows returned; common pagination tradeoff.
- Migration `DEFAULT 'DEMO'` mislabels all pre-migration rows as DEMO data source — MVP is demo-first, no live-mode searches existed before this migration; acceptable for current MVP context.
- `startDate > endDate` produces silent empty result set with 200 — no validation or 400 error; common REST tradeoff, acceptable.
- `sortDir` parameter accepted but silently ignored — only DESC is supported per spec; dead API parameter by design.
- `auditIngestorRefresh` uses a second `OffsetDateTime.now()` for verdict re-evaluation — a few milliseconds apart from the ingestor's own timestamp; negligible timing difference in practice.
- Test hash in `ScreeningRepositoryTest.insertAuditRow` is two concatenated UUIDs, not a real SHA-256 — hash round-trip not tested end-to-end; minor test quality gap.

## Deferred from: code review of 5-4-export-locale-enforcement-and-ux-notice (2026-03-29)

- Info toast fires even when `exportMohu()` resolves but the blob was null/empty and no file was actually downloaded — pre-existing store behaviour from Story 5.3; consider adding a non-null/non-empty blob guard in the store before the download step.
- No test for `isExporting` state consistency when the success toast fires — pre-existing store concern; low risk for current single-user store pattern.

## Deferred from: code review of 5-0-rename-seasonal-to-recurring (2026-03-26)

- `updateTemplate` null recurring silently defaults to `true` — by design; frontend always sends explicit value; may surprise raw API clients. Consider `@NotNull` + reject null on PUT if API is ever exposed externally.
- DB migration not idempotent if re-run (double-flip of boolean values) — Flyway checksum prevents this in practice; acceptable for now.
- `EprRepository.insertTemplate` fetchOne can return null without null-check — pre-existing jOOQ pattern; fail-fast acceptable given controller maps empty Optional to 500.
- `copyFromQuarter` has no deduplication guard — double-submission creates duplicate records; consider idempotency key or unique constraint on (tenant_id, name, quarter).
- `toggleRecurring` store has TOCTOU race for rapid toggles — last-write-wins on in-place update; debounce or request cancellation would improve UX.
- `CopyQuarterDialog` closes optimistically before server confirms copy success — UX design choice; acceptable since parent error toast covers the failure case.
- `findByTenantAndQuarter` uses UTC `CREATED_AT` for quarter membership — tenants not in UTC may see templates in wrong quarter; requires dedicated `quarter`/`year` columns to fix correctly.
- No upper-bound validation on `sourceYear` or `baseWeightGrams` — API hardening; low risk for internal tool.

## Deferred from: code review of 5-1-watchlist-bulk-pdf-export-and-mobile-dispatcher (2026-03-26)

- No index on `latest_sha256_hash` column — column not used in WHERE/JOIN now; add partial index before any hash-based lookup feature.
- `latestSha256Hash?: string | null` optional type in `api.d.ts` may persist beyond its TODO — resolve when OpenAPI pipeline is wired.
- `-Xmx1g` hardcoded in `build.gradle` without CI property override — advisory; low risk for current setup.
- `updateVerdictStatusWithHash` returns 0 rows when entry deleted mid-flight with no log warning — add warning log before Epic 6 monitoring hardening.
- Stale selection after watchlist refresh (object identity without `dataKey` on DataTable) — mitigated by store refresh; revisit if multi-source refresh becomes concurrent.
- Count suffix on "Share Report" label is minor deviation from AC 3 wording ("Export PDF (3)") — acceptable UX; revisit if share label UX is refined.

## Deferred from: code review of 5-2-quarterly-epr-filing-workflow (2026-03-26)

- W2: Template UUID leaked in error responses — "Template not found: `<uuid>`" exposes internal UUIDs enabling tenant enumeration oracle. Pre-existing pattern; consider generic "Resource not found" messages before public API exposure.
- W3: `Collectors.toMap` no-merge-function in `calculateFiling()` — throws `IllegalStateException` if `findAllByTenantWithOverride()` ever returns duplicate template records. Add merge function or verify uniqueness guarantee in repository.
- W4: `@Valid` does not cascade to null list elements in `FilingCalculationRequest` — malformed JSON `[null, {...}]` may bypass validation; add `@NotNull` annotations at list element level.
- W5: `NamingConventionTest` jOOQ record package path assumption — `extractRecordName()` assumes `.records` sub-package; verify against actual jOOQ codegen output path before adding more module rules.
- W6: Concurrent template delete causes 404 mid-calculation — optimistic-read pattern is acceptable for MVP; add transactional SELECT FOR SHARE if regulatory audit requirements emerge.
- W7: `BigDecimal` → JS `number` precision loss in `FilingCalculationResponse` TypeScript types — not practically reachable for EPR amounts; revisit if large industrial datasets are onboarded.
- W9: No `AbortController` for in-flight `calculate()` on navigation away — no crash risk; add cancellation if filing page gains a back-navigation confirmation guard.

## Deferred from: code review of 5-3-mohu-ready-csv-export (2026-03-28)

- `feeAmountHuf.intValue()` truncates without rounding — design contract (FeeCalculator always produces integers); add `@DecimalMax` / assertion if fractional fees ever become possible.
- Duplicate POST creates duplicate `epr_exports` rows (no unique constraint on tenant_id+file_hash) — add `UNIQUE(tenant_id, file_hash)` partial index or unique constraint in a future migration.
- `Content-Disposition` filename uses `LocalDate.now()` without explicit timezone — cosmetic only; if server timezone ever diverges from user timezone the date in the filename will be wrong.
