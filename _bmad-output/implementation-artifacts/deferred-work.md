# Deferred Work

## Deferred from: code review of 7-5-dashboard-empty-onboarding-state (2026-04-01)

- D1: Hero may flicker briefly after successful add — async window between `addEntry` POST and `fetchEntries` resolving; `!watchlistLoading && entries.length === 0` can momentarily be true again; pre-existing store async behavior.
- D2: `ScreeningSearchBarStub` expose pattern in tests is informal — `setup()` returns `{ focus: vi.fn() }` without `defineExpose`; Vue 3 test-utils ref exposure is not formally tested; tests pass but `focusSearchBar` integration wiring is unverified.
- D3: No test triggers `focus-search` on `WatchlistOnboardingHeroStub` to verify that `focusSearchBar` is called in `dashboard/index.vue` — integration path untested.
- D4: `emptyWatchlistHint` i18n key removed from both locale files without a codebase-wide search; low risk as the key was introduced specifically for this story's placeholder and has no other known consumers.
- D5: Skeleton loading test (`hides onboarding hero while loading`) does not assert `:is-loading="true"` is passed to the DashboardStatBar skeleton — minor AC 7 test gap.

## Deferred from: code review R1 of 7-4-flight-control-client-partner-view (2026-04-01)

- W1: Rate limiting / audit log absent on `GET /clients/{clientTenantId}/partners` — authenticated accountant can enumerate mandated tenant UUIDs via brute-force; mandate check is correct but no throttling or audit trail; pre-existing concern, out of scope for this story.
- W2: `getActiveMandateTenantIds` null-return NPE in `getClientPartners` — `mandatedTenantIds.contains(clientTenantId)` would throw NullPointerException if service ever returns null; same unguarded pattern as pre-existing `getFlightControlSummary`; guard when the mandate service contract is hardened.
- W3: `confirmViewPartner`/Escape key race — Dialog emits `update:visible` on Escape while `await switchTenant` is in flight; `pendingTaxNumber` set to null mid-await could cause `/screening/null` redirect; very narrow timing window; address if confirmed by QA.
- W4: No integration test for `findByTenantId` with `previousVerdictStatus` — `NotificationRepositoryIntegrationTest` exercises `findAllWatchlistEntries` and `findByTenantIdAndTaxNumber` but not `findByTenantId`; typo in jOOQ field mapping would be invisible until runtime; add test in next integration test pass.
- W5: `trendDirection` returns `'stable'` for two unknown status strings — unknown values both map to severity 99; `c === p` → `'stable'` arrow instead of `—`; only manifests for future enum values not in `STATUS_SEVERITY`; add unknown-status guard when new verdict states are introduced.

## Deferred from: code review R1 of 7-2-partner-detail-slide-over-drawer (2026-04-01)

- D1: `taxNumber` not URL-encoded in route paths (`/screening/${taxNumber}`) and query strings (`?taxNumber=${taxNumber}`) — Hungarian tax numbers are numeric+hyphen (safe in practice); add `encodeURIComponent` if foreign entities with special chars are ever supported.
- D2: `watchlistSince` shows "Invalid Date" for malformed `createdAt` — field is non-nullable and backend-controlled; add a guard if data provenance ever becomes untrusted.
- D3: `onRowClick` guard is a static allow-list (`.p-checkbox`, `[data-testid="remove-entry-button"]`) — any future interactive cell added to the table will fall through and open the drawer unintentionally; consider a generic `a, button, input` ancestor check.
- D4: `STATUS_SEVERITY` map duplicated from `DashboardNeedsAttention.vue` — divergence risk when a new verdict status is added; extract to a shared composable when a third consumer appears.
- D5: `AuditDispatcher` receives inline `[entry]` arrays (new reference on every render) — no observable impact currently; compute a stable ref if AuditDispatcher is ever observed to re-trigger unnecessarily.
- D6: Drawer does not close after PDF export via `AuditDispatcher` — spec doesn't require it; acceptable UX (user stays on watchlist); revisit if UX feedback requests close-on-export.
- D7: Row lacks `cursor: pointer` visual affordance — no functional impact; add in UX polish pass.
- D8: `selectedPartner` not cleared when drawer closes via X/Escape/overlay — stale ref never displayed (drawer is hidden and ref is overwritten on next row click); add a `watch(drawerVisible)` cleanup if stale data flash ever manifests.

## Deferred from: code review R1 of 6-3-hot-swappable-epr-json-manager (2026-03-31)

- W1: Concurrent publish race — `getMaxConfigVersion()` + `insertConfig()` not locked; spec accepts negligible risk (SME_ADMIN only, UNIQUE constraint as hard stop); revisit if publish is ever opened to more roles.
- W2: First-ever publish when no active config → `IllegalStateException` in `publishNewConfig` — impossible in practice (v1 seeded by migration); add guard if zero-config bootstrap scenario ever added.
- W3: `details` JSON in publish audit log built by string concatenation — safe with int-only values; same as 6-2 W1 pattern; replace with `ObjectMapper` before any string field is added.
- W4: `activatedAt` displayed raw as ISO-8601 string in `epr-config.vue` — no locale date formatting; acceptable for admin-only tool.
- W5: Frontend role guard false-redirect when `identityStore.user` is null (store not hydrated on hard refresh) — pre-existing pattern across all admin pages; fix in UX polish sprint.
- W6: `admin.eprConfig.publishConfirm` i18n key is dead (spec prohibits dialog) — remove key in i18n cleanup pass.

## Deferred from: code review R2 of 6-3-hot-swappable-epr-json-manager (2026-03-31)

- D1: Concurrent publish version TOCTOU — two separate queries in READ_COMMITTED isolation (`getMaxConfigVersion` + `insertConfig`); UNIQUE constraint on version is the safety net; acceptable for admin-only low-frequency operation.
- D2: No config payload size limit on POST /publish — large JSON could cause resource exhaustion; add request size cap (e.g., `@Size(max=...)` or Spring `max-http-request-header-size`) in a hardening pass.
- D3: `EprConfigResponse.from()` nullable activatedAt vs non-nullable record type — currently unreachable via `findActiveConfig()` which filters `activated_at IS NOT NULL`; add explicit `requireNonNull` guard if the factory is ever reused elsewhere.
- D4: `getActiveConfigFull()` throws `IllegalStateException` → HTTP 500 when no active config; better as `ResponseStatusException(404)`; spec-prescribed, admin-only edge case.
- W7: `getActiveConfigFull()` / `getActiveConfigVersion()` missing `@Transactional(readOnly=true)` — pre-existing pattern, no correctness impact currently.
- W8: `MonacoEditor.MonacoEnvironment` overwritten on every mount; only `editor.worker` registered — revisit if JSON language features show issues in production.
- W9: Monaco `setValue` in `watch` resets cursor/undo history on external model updates — acceptable tradeoff for v-model sync; revisit if UX complaints arise.

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

## Deferred from: code review of 7-1-risk-pulse-dashboard-redesign (2026-04-01)

- W1: API failures silently produce misleading empty states — `fetchEntries` failure renders the "empty watchlist hint"; `fetchAlerts` failure renders "No changes in the last 7 days"; no error display or try/catch; project-wide pattern, fix holistically.
- W2: `null` `currentVerdictStatus` entries not counted in any stat card — totals appear authoritative but may undercount when entries have no verdict yet; no "pending" bucket in spec; revisit if null-status entries become common.
- W3: `formatRelative` called with potentially `null` `lastCheckedAt` in `DashboardNeedsAttention` — safe if composable guards null (likely); verify before expanding null-status entry handling.
- W4: Raw enum strings displayed in attention-list badges (`AT_RISK`, `TAX_SUSPENDED`, etc.) — spec does not mandate localized badge text; UX polish pass.
- W5: 10-entry cap in `DashboardNeedsAttention` with no overflow indicator — silent truncation in a risk-monitoring context; add "and N more..." affordance if stakeholder feedback requests it.
- W6: 5-alert cap in `DashboardAlertFeed` with no overflow indicator — spec says "up to 5"; add view-all link if needed.
- W7: `INCOMPLETE` classified as "At Risk" (red) in stat bar but "warn" (yellow) in attention list — intentional per spec; may confuse users who see the same entity coloured differently; consolidate in UX pass.
