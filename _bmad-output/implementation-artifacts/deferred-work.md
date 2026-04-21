# Deferred Work

## Deferred from: code review of 10-7-empty-registry-block-guided-onboarding (2026-04-21)

- W1: Stale cache after bootstrap/create/archive — `summaryCache` in RegistryService has 10s TTL only; `onBootstrapCompleted → refresh()` may return cached empty state. Fix: call `summaryCache.invalidate(tenantId)` in `RegistryService.create/archive` and in the bootstrap completion hook (Story 10.4 scope).
- W2: `RegistrySummaryResponse` imports `RegistryRepository.RegistrySummary` from `internal` package — mild upward layering; ArchUnit passes currently. Refactor `from()` factory to accept primitive ints instead.
- W3: Double `fetchProducts` call on `registry/index.vue` mount when `onlyIncomplete` auto-activates and the watcher fires simultaneously; `fetchProducts` is debounced so 1 effective call, but emits 2 debounced invocations. Minor perf.
- W4: 1-tick initial flash of onboarding block before skeleton shows — `isLoading` initializes as `false`; fix by initializing `ref(true)` and updating the spec test expectation `[false,true,true,false] → [true,true,true,false]`.

## Deferred from: code review of 10-6-epr-filing-ui-rebuild-two-tier-display (2026-04-21)

- R2-D1: AbortController for `fetchAggregation` races — concurrent fetches can land out of order and overwrite newer state; in-flight fetch not cancelled on unmount. Cross-cutting refactor; follow-up.
- R2-D2: `onMounted` + period watcher both schedule a fetch on first render — low impact (same default period = same response); resolved once R2-D1 cancels the earlier request.
- R2-D3: Description normalization (trim/case) between `soldProducts` and `unresolved` for status-badge matching — reaffirmed R1 D3, belongs server-side.

## Deferred from: code review of 9-6-multi-layer-packaging-ratio-and-ai-weight-suggestions (2026-04-16)

- D1: Static ObjectMapper in BootstrapRepository — thread-safe for current usage but fragile if JavaTimeModule/Jdk8Module needed later. Pre-existing pattern.
- D2: candidatesTokenCount Vertex AI field naming — may vary across API versions; token counts could silently be 0. Not blocking.
- D3: useApiError leaks raw backend validation messages to toast summaries — pre-existing XSS/info-disclosure risk via Spring BindingResult interpolation.
- D4: weightPerUnitKg nullable in OkirkapuXmlExporter — pre-existing NPE risk for components with null weight.
- D5: useClassifier.spec.ts never existed — pre-existing test coverage gap for classifier composable.
- D6: No component-level tests for multi-layer popover UX — popover logic tested via function-level tests only.

## Deferred from: code review of 10-4-tenant-onboarding-feltoltes-szamlak-alapjan (2026-04-20)

- D1: No max period window validation for bootstrap trigger — spec doesn't require; potential future enhancement to cap NAV fetch range and classifier cost.
- D2: `taskExecutor` rejection policy (CallerRunsPolicy) not verified — pre-existing AsyncConfig concern; if queue is full, HTTP thread could block.
- D3: `writeCounters` NPE if a new `AuditSource` enum value is added without wiring it in AuditService — pre-existing EnumMap initialization gap.
- D4: `@Lazy @Autowired self` self-proxy anti-pattern — works reliably in production but fragile in test setup; consider `ObjectProvider<>` refactor in a future Epic 10 cleanup.
- D5: AC#23 overwrite check calls `listProducts({size:1})` not `GET /api/v1/registry/summary` — Story 10.7 doesn't exist yet; update the dialog when 10.7 lands.
- D6: NBSP and Unicode whitespace not stripped by Java `.trim()` in dedup key — NAV invoice descriptions could differ by U+00A0 and produce duplicates; fix with `replaceAll("\\p{Z}", "")` if reports surface.
- D7: AC#6 `BootstrapJobWorker` named as a separate class in spec but merged into `@Async processJob()` — functionally equivalent; ArchUnit `allowEmptyShould(true)` permits this.
- D8: `findInflightByTenant` uses `fetchOptional` — silent masking if partial-unique-index is ever dropped and two in-flight rows exist; add log.error if count > 1.
- D9: Progress bar shows indeterminate spinner when `totalPairs === 0` — minor UX confusion for tenants with no invoices; acceptable for now.
- D10: Per-pair crash loses in-memory `totalFailed` counter — outer catch-all calls `failJob()`, so job doesn't stay RUNNING; exact partial-failure count is lost on JVM crash only.
- D7: classify() with empty productName produces false-positive VTSZ_FALLBACK matches — pre-existing.

## Deferred from: code review of 9-5-registry-ux-polish-and-bug-fixes (2026-04-15)

- D1: Race condition — parallel classify requests clobber shared Popover state (`[id].vue` suggestKfCode). Fixing requires AbortController cancellation; out of scope for polish story.
- D2: `isComponentsMinSize` also matches `@Size(max=N)` violations on `components` (`RegistryValidationExceptionHandler.java`). No `@Size(max=...)` exists on components today; safe to defer.
- D3: `expandedRows` stale keys after product reload (`[id].vue`). Cosmetic; rows re-expand unexpectedly on reload in edge case.
- D4: `setEditProduct(null)` in `onBeforeUnmount` causes one-frame breadcrumb flash during navigation (`[id].vue`). Cosmetic.
- D5: `save()` for new product does not call `setEditProduct(saved)` before navigation (`[id].vue`). Cosmetic breadcrumb flicker; edit path already calls it.
- D6: E2E popover z-index assertion walks ancestor tree, not the popover element directly (`registry-classify-popover.e2e.ts`). Test improvement; regression is still caught.
- D7: E2E test silently skips when classifier returns no HIGH/MEDIUM suggestion. Accepted per dev notes; test is honest.
- D8: `materialDescription` has no frontend validation (backend is `@NotBlank`) — surfaces as generic 400 on save. Pre-existing gap from Story 9.1.
- D9: UUID regex does not enforce UUID version/variant bits in `AppBreadcrumb.vue`. Cosmetic; hex-UUID-shaped segments show the edit fallback.
- D10: `shims-vue.d.ts` uses `any` in component type. Accepted TypeScript shim pattern.
- D11: Global keydown listener could accumulate in Nuxt keep-alive. `onBeforeUnmount` removes it correctly; no real leak in current app.
- D12: `@ControllerAdvice(assignableTypes = RegistryController.class)` scoping fragile to controller refactoring. Intentional scope; no refactoring planned.
- D13: Sticky action bar uses hardcoded `bg-white` (breaks dark mode). Dark mode not in project scope.
- D14: Legacy `primaryUnit` prepended option uses raw DB value as label (no i18n). Acceptable for legacy roundtrip; `'pcs'` is self-describing.

## Deferred from: code review of 8-5-platform-admin-role-and-admin-re-gating (2026-04-09)

- D1: PLATFORM_ADMIN placed in demo SME tenant (`b000-000000000001`) — cross-tenant authorization model undefined; demo-mode acceptable for now; revisit when platform operator multi-tenant scope is designed.
- D2: `DataSourceHealthDashboard` defaults `canQuarantine: true` — insecure opt-out default; parent always passes explicit `:can-quarantine` prop correctly, but the default should be `false`; pre-existing from Story 8.4.
- D3: PLATFORM_ADMIN has no post-login landing page — global middleware sends to SME dashboard (watchlist/screening) which is irrelevant; pre-existing middleware design gap.
- D4: `admin/index.vue` has no test file — new `onMounted` redirect (GUEST → /dashboard) and `v-if="isPlatformAdmin"` card visibility logic are entirely untested; AC11 only required updating existing specs, not creating new ones.
- D5: `requirePlatformAdminRole` / `requireAnyAdminRole` duplicated identically across 3 controllers — no shared utility; latent consistency risk if a new role is added; pre-existing pattern from Story 8.4.
- D6: `AppSidebar` shows PLATFORM_ADMIN SME-oriented nav items (Watchlist, Screening, EPR Filing) — these features are irrelevant to a platform operator; pre-existing nav structure; address in UX polish sprint.

## Deferred from: code review of 8-3-invoice-driven-epr-auto-fill (2026-04-03)

- W1: `invoiceNumber` logged plain in `DataSourceService.queryInvoiceDetails` warn path — inconsistent with masked `taxNumber`; invoice numbers are not PII; low risk.
- W2: `toInvoiceSummary` hardcodes `invoiceNetAmount=BigDecimal.ZERO` — demo mode only; EPR auto-fill never reads summary net amounts, so functionally harmless.
- W3: `toInvoiceDetail` passes `li.netAmount()` twice for `lineNetAmount` and `lineNetAmountHUF` — demo mode only; EPR reads vtszCode/quantity not HUF amounts; live path correctly distinguishes them.
- W4: `DemoInvoiceFixtures.getForTaxNumber` truncates to 8 chars without validating content is numeric — demo mode only; short/non-numeric strings silently miss in fixture map (safe).
- W5: `emptyDetail` hardcodes `InvoiceDirection.OUTBOUND` — error-path fallback only; direction field not consumed by any EPR auto-fill logic.
- W6: N+1 sequential NAV calls in `EprService.autoFillFromInvoices` — one `queryInvoiceDetails` call per invoice summary, blocking; batch/parallel fetch needed for large invoice counts; MVP scope.
- W7: SQL migration `V20260403_001` UPDATE silently affects 0 rows if no `version=1` config exists — pre-existing system assumption that EPR wizard has been run before auto-fill is used.
- W8: `InvoiceAutoFillPanel.vue` `handleFetch`: DatePicker can be cleared to null; silent no-op with no user feedback when dates missing.
- W9: `EprService.autoFillFromInvoices` template match uses `findFirst()` — non-deterministic if two templates share the same `materialName_hu` (case-insensitively); low-probability data quality edge case.
- W10: No explicit unit test that `EprConfigValidator.validate()` accepts a config containing `vtszMappings` — validator ignores unknown top-level keys by design; regression guard would be useful.

## Deferred from: code review of 8-2-screening-verdict-pdf-export (2026-04-03)

- D1: `URL.revokeObjectURL` 10s `setTimeout` fire-and-forget in `dispatch` — pre-existing pattern from Story 5.1 `useWatchlistPdfExport.ts`; minor memory hold for up to 10s after unmount.
- D2: `SOURCE_UNAVAILABLE:name` signal split on first `:` only — if source name ever contains a colon, the label is truncated; current backend uses short alphanumeric names so no impact today.
- D3: `dispatch` download fallback calls `document.createElement`/`body.appendChild` without `typeof document !== 'undefined'` guard — screening page is client-side only, so SSR path is unreachable.
- D4: Empty `provenance.sources` array (`[]`) produces no "no sources" message in the PDF — spec does not require this; PDF silently skips the section, consistent with spec intent.
- D5: If provenance fetch fails, `currentProvenance` is null and a user who immediately clicks Export PDF gets a PDF without data sources — null provenance is gracefully handled (section omitted), no crash; acceptable silent omission.
- D6: `riskSignalLabel` falls through to `t(\`screening.riskSignals.\${signal}\`)` for unknown signals — returns raw key in PDF for future unknown enum values; consistent with the existing VerdictCard UI rendering pattern.

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

## Deferred from: code review of 8-1-nav-online-szamla-client-implementation (2026-04-03)

- D1: New `HttpClient` created per HTTP call — no connection pooling [`NavOnlineSzamlaClient.java:236`, `AuthService.java:121`]; inject shared instance for performance under load.
- D2: PBKDF2 uses hardcoded salt `"nav-cred-salt"` [`AesFieldEncryptor.java:32`]; acceptable for app-level encryption but per-record salt would be stronger.
- D3: `NavCredentialManager.vue` uses raw `fetch()` instead of project `useApi` composable; works with cookie auth but bypasses centralized interceptors.
- D4: No page cap on `queryInvoiceDigest` pagination loop [`NavOnlineSzamlaClient.java:167`]; NAV responses bounded in practice.
- D5: No FK constraint `nav_tenant_credentials.tenant_id → tenants(id)` [`V20260402_001__add_nav_tenant_credentials.sql`]; consistent with existing patterns.
- D6: `XmlMarshaller.unmarshal` not type-directed — JAXB root element mapping used [`XmlMarshaller.java:76`]; works for all current response types.
- D7: GZIP decompression `readAllBytes()` with no size cap [`NavOnlineSzamlaClient.java:503`]; NAV invoices bounded in practice.
- D8: `NavOnlineSzamlaAdapter` hardcodes live production URL as provenance metadata [`NavOnlineSzamlaAdapter.java:47`]; cosmetic audit log issue.
- D9: `AuthService.verifyCredentials` treats NAV `WARN` funcCode as failure [`AuthService.java:133`]; conservative; loosen if real users hit WARN.

## Deferred from: code review R2 of 8-1-nav-online-szamla-client-implementation (2026-04-03)

- D10: `switchTenant` removed `window.location.reload()` — Pinia stores may retain stale data from previous tenant. `app.vue` pageKey mitigates via NuxtPage re-mount; needs targeted cross-tenant testing.
- D11: Credential status is binary VALID/NOT_CONFIGURED — no background validation. Expired NAV credentials still show VALID. Needs background verification job.
- D12: `TenantJooqListener` AUTH_LOOKUP_PATTERN regex uses greedy `.*` — could match non-auth queries. Tight enough for current codebase.
- D13: `queryInvoiceData` hardcodes OUTBOUND direction — add direction parameter when inbound queries needed.
- D14: `nav_tenant_credentials` not in `TenantJooqListener.isTenantAwareQuery()` table list — add when new query patterns emerge.
- D15: `EprService.copyTemplates` name dedup (non-story bugfix) has no test coverage for the new filter path.

## Deferred from: code review of 8-4-accountant-nav-credential-access-and-demo-mode (2026-04-08)

- W1: No delete confirmation dialog on NavCredentialManager — single click permanently deletes credentials with no undo; pre-existing UX gap; add ConfirmDialog when accountant access matures.
- W2: `requireAdminOrAccountantRole` null-role behavior undocumented — null JWT claim correctly triggers 403 but intent never stated; same pattern as pre-existing `requireAdminRole`; document when refactoring auth helpers.
- W3: Class-level Javadoc on DataSourceAdminController still says "Restricted to SME_ADMIN only" — update when next touching the class.
- W4: `adapters[0]` hardcoded in `datasources.vue` — dataSourceMode from first adapter passed to NavCredentialManager; wrong in multi-adapter setups where NAV isn't at index 0; revisit when multi-adapter health view is built.
- W5: `getHealth` computes `existsByTenantId` DB query on every 30s poll call with no server-side caching — low impact at current scale; add caching if health endpoint becomes a performance hotspot.

## Deferred from: code review of 9-1-product-packaging-registry-foundation (2026-04-14)

- W1: Redundant `idx_products_tenant_article` alongside partial unique index `uq_products_tenant_article` — minor write overhead; subsumed for non-null lookups.
- W2: `changed_by_user_id` nullable in `registry_entry_audit_log` with no CHECK(source='MANUAL' → NOT NULL actor) — MANUAL audit row can carry NULL actor; beyond spec scope.
- W3: `primary_unit` free-text VARCHAR(16) with no enumeration CHECK — inconsistent unit strings could corrupt weight aggregations; beyond Story 9.1 scope.
- W4: AppSidebar spec tests a local `mainNavItems` copy, not the mounted component — cannot detect drift; pre-existing test pattern.
- W5: `registry_entry_audit_log.tenant_id` has no DB-level FK consistency constraint with `products.tenant_id` — deliberate denormalization per spec Dev Notes; app layer enforces.
- W6: `substances_of_concern` JSONB has no schema CHECK or GIN index — beyond scope for this story.
- W7 (Group C): `RegistryRepository.insertProduct`/`insertComponent` use `fetchOne(ID)` which can return null on constraint race; null UUID produces misleading NOT_FOUND.
- W8 (Group C): `RegistryController` `page` parameter has no `@Min(0)` — negative page produces DB-level OFFSET error (500 instead of 400).
- W9 (Group C): `updateProduct`/`archive` don't check jOOQ affected-row count — silent no-op on cross-tenant update; audit rows written for non-existent state.
- W10 (Group C): `insertComponent` inserts by `productId` only with no tenant re-verification at component level.
- W11 (Group C): `countByTenantWithFilters` applies `kfCode` EXISTS subquery twice, structurally diverging from `listByTenantWithFilters`.
- W12 (Group E): `ToggleSwitch` bound to nullable `Boolean reusable` — null initial value renders in undefined state; irreversible once toggled.
- W13 (Group B): `substancesOfConcern` missing from `diffComponentAndAudit` in RegistryService — field changes produce no audit row, violating AC 5.

## Deferred from: code review of 9-2-nav-invoice-driven-registry-bootstrap (2026-04-14)

- D1: `triggerBootstrap` holds open a DB transaction across N serial NAV HTTP calls — connection pool exhaustion risk under load. Pre-existing pattern also in EprService. Needs async or chunked approach.
- D2: A single `queryInvoiceDetails` exception rolls back the entire trigger batch — no per-invoice error isolation. Requires architectural redesign (e.g., per-invoice try-catch with partial-result persistence).
- D3: `APPROVED` candidate with `resulting_product_id = NULL` after linked product is deleted (FK ON DELETE SET NULL) — no guard in service or UI for this inconsistent state.
- D4: Keyboard shortcut hint rendered as `<p>` above DataTable rather than inside DataTable header slot — minor UX deviation from AC 7 spec wording.
- D5: No `from <= to` validation in trigger request; inverted date range forwarded to NAV silently. Simple cross-field constraint, low urgency.
- D6: `normalize()` private static method duplicated in `RegistryBootstrapService` and `BootstrapRepository` — divergence risk if normalization logic is ever changed.


## Deferred from: code review of 9-3-ai-assisted-kf-code-classification (2026-04-14)

- W1: `RegistryAuditEntry` record and `listAuditByProduct()` fetch do not expose `strategy`/`modelVersion` columns — written to DB but never returned in audit log API; not specified in AC 9 for read path; address when audit log UI needs to show AI provenance.
- W2: `[id].spec.ts` tests 7–9 test mock call behavior rather than actual component rendering (proxy tests); low-value coverage pattern; pre-existing test quality issue in this file.
- W3: Confidence ordinal comparison in `ClassifierRouter` is fragile if `ClassificationConfidence` enum is ever reordered; existing comment in `ClassificationConfidence.java` warns; not introduced here.
- W4: `VtszPrefixFallbackClassifier` returns `VTSZ_PREFIX` strategy for no-match case before `ClassifierRouter` overrides to `NONE`; correct end-to-end behavior, minor spec deviation from AC 3 wording.

## Deferred from: code review of 9-4-registry-driven-epr-autofill-and-okirkapu-export (2026-04-15)

- W1: Redundant `idx_producer_profiles_tenant` index alongside UNIQUE constraint — UNIQUE already creates an index in PostgreSQL; clean up when convenient.
- W2: Empty `MohuExportRequest` record compiled into production JAR — tombstone stub; delete in merge commit.
- W3: `exportMohuGone` tombstone endpoint is unauthenticated — discloses replacement API path `/api/v1/epr/filing/okirkapu-export` to anonymous callers; low risk for a 410 tombstone.
- W4: `generateReport` silently swallows `getActiveConfigVersion()` exceptions and logs `configVersion=0` — defensive fallback; audit log integrity is weakly compromised; address if configVersion is ever used for compliance reporting.
- W5: `EprReportArtifact.bytes()` is a mutable `byte[]` — no defensive copy in the record; not currently exploitable since artifacts are not shared between calls; harden if caching is ever introduced.
- W6: `ZipEntry` creation timestamps not set — will show epoch/1980-01-01 in archive tools; purely cosmetic.
- W7: `KgKgyfNeMarshaller`/`KgKgyfNeAggregator` `@PostConstruct` always runs at startup regardless of `OkirkapuXmlExporter` `@ConditionalOnProperty`; only matters if a second `EprReportTarget` strategy is activated.
- W8: Playwright e2e `epr-filing.spec.ts` not updated for new export button label and `.zip` extension; update before next release cycle.

## Deferred from: code review of 9-6-multi-layer-packaging-ratio-and-ai-weight-suggestions (2026-04-16)

- D1: Static ObjectMapper in BootstrapRepository [BootstrapRepository.java:35] — thread-safe for current usage but fragile if JavaTimeModule or Jdk8Module are needed later. Pre-existing pattern across repo.
- D2: candidatesTokenCount field naming in Vertex AI response [VertexAiGeminiClassifier.java:196] — field name may vary across API versions; token counts could silently be 0. Not blocking current functionality.

## Deferred from: code review of 10-1-task-0-audit-architecture (2026-04-17)

- W1: Audit row timestamp relies on DB `DEFAULT now()` rather than being stamped at write time [RegistryAuditRepository.java insertAuditRow] — pre-existing Epic 9 pattern inherited by the relocated repository; harden if future migration loses the default or deterministic Clock-backed tests are needed.
- W2: `AuditSource.valueOf(cmd.classificationSource())` in `RegistryService` swallow/fallback path [RegistryService.java:~296] — pre-existing caller-side parsing; revisit when introducing `FieldChangeEvent`-based call sites (Story 10.3 / 10.8) so strategy/modelVersion mapping lives inside the facade.
- W3: Perf sanity-check deferred to story-exit journal — no regression signal yet; no baseline from Epic 9 to compare against. Revisit if Story 10.4's 3000-invoice load test surfaces audit-write overhead (ADR-0003 revisit trigger §"Audit write volume outgrows the transactional path").

## Deferred from: code review of 10-1-registry-schema-menu-restructure-and-tx-pool-refactor Group A (2026-04-18)

- A-D1: `items_per_parent` CREATE audit null-guard in `RegistryService.emitComponentCreateAudit` is dead code on the DTO path (toCommand() substitutes DEFAULT_ITEMS_PER_PARENT before the command reaches service); guard remains relevant only for direct-construction paths (e.g., bootstrap). Revisit when Story 10.4 bootstrap path constructs ComponentUpsertCommand directly.
- A-D2: `emitAudit(…null, value…)` called directly for `items_per_parent` CREATE vs `emitCreateAuditRaw` for all other CREATE fields. Cosmetic inconsistency; both ultimately call AuditService.recordRegistryFieldChange. Standardize in a future cleanup pass.
- A-D3: `RegistryRepository.toComponent()` null-guards `getItemsPerParent()` despite DB NOT NULL constraint. Consistent with pre-existing jOOQ codegen behavior (codegen types NOT NULL columns as nullable). Revisit with a jOOQ codegen non-null config if adopted.
- A-D4: No zero-divisor guard in `OkirkapuXmlExporter.processLineItem()` on `itemsPerParent`. Only reachable via direct-SQL legacy rows; DTO @DecimalMin("0.0001") and migration DEFAULT 1 prevent zero from API path. Add guard before Epic 10.5 aggregation lands.
- A-D5: No packaging-hierarchy constraint on `wrappingLevel`: level-3 component accepted without a level-1/2 parent in the same product. Story 10.5 aggregation must handle degenerate hierarchies or add a domain-level validation in RegistryService.

## Deferred from: code review of 10-1-registry-schema-menu-restructure-and-tx-pool-refactor Group B (2026-04-18)

- B-D1: `BootstrapResult.skipped()` conflates two distinct scenarios: "row existed at pre-check time" and "lost concurrent-insert race after spending AI classifier tokens on the candidate". Add a richer return type (e.g., `BootstrapResult(created, skippedKnown, skippedRace)`) before Story 10.4 scales to 3000 invoices where the distinction matters for diagnostics.
- B-D2: `BootstrapServiceTest` uses a no-op `PlatformTransactionManager` with `SimpleTransactionStatus(isNewTransaction=false)`, so `TransactionTemplate` never calls `commit()` on the manager. Rollback semantics (e.g., DataAccessException inside the batch tx) are untested at unit level. Add a dedicated test with a mock manager that verifies rollback is called on exception.
- B-D3: `NavHttpOutsideTransactionTest.invokeForbiddenHttpClient()` inspects only one-level-deep call sites. A `@Transactional` method that delegates to a private helper calling `DataSourceService` would evade the rule. Upgrade to recursive call analysis or add a documentation note about the limitation. Revisit before Story 10.4 adds new `@Transactional` methods in scope.
- B-D4: `RegistryBootstrapServiceLoadTest` runs a single bootstrap invocation. The original bug manifested under concurrent load (two tenants, both holding a connection). Add a concurrent variant (two parallel `CompletableFuture.supplyAsync(bootstrap)` calls against `spring.datasource.hikari.maximum-pool-size=2`) to directly prove the fix.
- B-D5: `poller.awaitTermination(1, TimeUnit.SECONDS)` in load test is not asserted for `true`. A stuck poller thread leaks into the shared Spring test context. Add `assertThat(poller.awaitTermination(...)).isTrue()`.
- B-D6: `RegistryBootstrapService.triggerBootstrap` passes raw `g.vtsz()` to `existsByTenantAndDedupeKey` while the insert path normalizes separately. The double-normalize is idempotent today but fragile. Extract a `normalizeVtsz(String)` method and apply it consistently at both call sites.

## Deferred from: code review of 10-1-registry-schema-menu-restructure-and-tx-pool-refactor Group C (2026-04-18)

- C-D1: `EprExceptionHandler` is scoped to `EprController`. `RegistryController` INSERT/UPDATE with an invalid `materialTemplateId` reaches Spring's global 500 handler instead of returning a clean 409. Widen the scope or add a `RegistryExceptionHandler` when Story 10.2+ adds picker-driven writes that may produce this FK violation. Partially mitigated by A-P1 tenant-isolation fix (invalid cross-tenant UUIDs caught before DB).
- C-D2: `EprExceptionHandler.handleDataIntegrity()` uses `contains("product_packaging_components") && contains("material_template_id")` to route. This cannot distinguish a parent-side DELETE RESTRICT from a child-side INSERT FK violation — both message strings contain those tokens. If the handler scope widens, it will emit `type = template-still-referenced` for the wrong case. Fix: also check for `"update or delete"` vs `"insert or update"` in the message, or match the full constraint name.
- C-D3: `getMostSpecificCause().getMessage()` can return null (e.g., a JDBC driver bug or an NPE-wrapped exception). Current code assigns `rootMessage = null`, the template branch is skipped, and `problem.setDetail(null)` produces `"detail": null` in the JSON body. Add `rootMessage != null ? rootMessage : "Data integrity violation"` in the fallback.
- C-D4: `OkirkapuXmlExporterTest.component()` helper parameter is still named `unitsPerProduct` after the field rename. Rename to `itemsPerParent` for clarity.
- C-D5: `EprExceptionHandlerTest` has no test for the null-message path (both `getMostSpecificCause()` and `getMessage()` return null). Add a `@Test` that passes a bare `new DataIntegrityViolationException(null)` and asserts a non-null safe 409 body.

## Deferred from: code review of 10-1-registry-schema-menu-restructure-and-tx-pool-refactor Group E (2026-04-18)

- E-D1: `no-restricted-imports` with `~/composables/...` path relies on literal string matching; Nuxt auto-imports (no `import` statement) bypass the rule entirely. Documented ESLint limitation for Nuxt apps; grep-based CI check is the alternative per Dev Notes.
- E-D2: `no-restricted-syntax: 'off'` in the ESLint override for `components/registry/**` and `composables/registry/**` silences all current and future `no-restricted-syntax` rules in those files. Narrow the override to specific rule IDs when new syntax rules are added.
- E-D3: `app/composables/api/**` allow-list exempts all API composables from the URL-literal guardrail; a future composable there referencing `/api/v1/epr/materials` would pass lint silently.
- E-D4: `useMaterialTemplatePicker.list()` returns an empty slice for out-of-range pages with no error or sentinel. Consistent with pagination convention.
- E-D5: `[id].spec.ts` picker tests are stub/state-machine only; picker Dialog + Listbox + search UI untested at component level. Pre-existing `[id].spec.ts` pattern; manual verification or Playwright e2e covers picker UX.
- E-D6: `createDraft` hardcodes `recurring: true` in POST body with no override path. Intentional draft-flow design; revisit if non-recurring draft templates are ever needed.

## Deferred from: code review of 10-1-registry-schema-menu-restructure-and-tx-pool-refactor Group D (2026-04-18)

- D-D1: `AppSidebar.spec.ts` `hasProEpr` test helper uses `TIER_ORDER['PRO_EPR'] ?? 0` as the right operand. If `PRO_EPR` is removed from `TIER_ORDER`, the comparison becomes `>= 0` (grants access to every tier). Change the fallback to `?? Infinity` to deny access on key-not-found, matching the real component's behavior.
- D-D2: `filing.vue` back-button route change (`/epr` → `/registry`) lacks a spec assertion. Add `expect(wrapper.find('[data-testid="back-to-library-button"]').trigger('click'))` → `expect(mockRouter.push).toHaveBeenCalledWith('/registry')` in `filing.spec.ts` when Stories 10.6/10.7 rebuild the filing page.
- D-D3: Pre-commit i18n hook requires manual install (`ln -s ../../scripts/pre-commit.sh .git/hooks/pre-commit`). Add a `postinstall` or `prepare` script in the root `package.json` to auto-install the hook, or adopt Husky in a future tooling story.
- D-D4: `AppBreadcrumb.spec.ts` has a live test case for the `/epr` breadcrumb path which no longer has a UI entry point. Remove or update in a future cleanup pass; replace with a test for `/epr/filing` breadcrumb rendering.

## Deferred from: code review of 10-3-ai-batch-classifier-full-packaging-stack-endpoint (2026-04-20)

- D1: TOCTOU cap race — two concurrent batches can both pass the pre-check when remaining ≥ max(N1, N2) but N1+N2 > remaining. AC #9 explicitly accepts best-effort; bounded by per-tenant concurrency gate (max 3) + circuit breaker.
- D2: `BatchPackagingConcurrencyGate` Semaphore map grows indefinitely (one entry per tenant UUID, never evicted). Documented in Javadoc. Acceptable for bounded tenant count.
- D3: `TenantContext.setCurrentTenant/clear` in virtual threads relies on ThreadLocal compatibility wrapper — correct for current implementation, follows `CompanyDataAggregator` pattern.
- D4: `scope.join()` InterruptedException + `StructuredTaskScope.close()` interaction — with interrupt status set, close() cancels tasks and re-throws; low probability in practice; Java 25 STS contract handles cleanup.
- D5: Double DB roundtrip `usedBefore` + `usedAfter` in controller — post-batch snapshot is best-effort; concurrent batches from same tenant may cause slight inaccuracy. By design.
- D6: DST edge case in `secondsUntilNextBudapestMonth()` — Budapest CET/CEST never has midnight DST gap; `toLocalDate().atStartOfDay(BUDAPEST)` would be strictly more correct.
- D7: `isCapExceeded` refactored to `getCallCountForMonth() >= cap` changes `cap=0` semantics (old: false when row absent; new: `0 >= 0 = true`). Only affects unrealistic `monthly-cap: 0` config.
- D8: AC #16 taxonomy excerpt not appended to `packaging-stack-v1.txt` — prompt is a scaffold; taxonomy append deferred to Story 10.5+ when prompt is routed through VertexAiGeminiClassifier.
- D9: AC #13 load-test timing assertion — concurrency test verifies in-flight count ≤ limit (correct) but does not assert wall-clock duration between serial and unbounded. Timing assertions are flaky in CI.
- D10: Post-batch `usedAfter` may reflect other concurrent tenants' increments — informational only; best-effort.
- D11: `BatchPackagingResult.from()` all-layers-dropped counter shows UNRESOLVED even when Gemini returned content (vs "Gemini returned nothing"). Log gap addressed by R1-P7; counter attribution improvement deferred.
- D12: `BatchPackagingConcurrencyGate.release()` has no upper-bound guard on Semaphore permits — R2-P5 closed the auto-create-on-release gap; the remaining `Semaphore.release()`-above-max-permits concern survives as a structural follow-up for future callers.
- D13 (R2): No overall deadline on `scope.join()` in `BatchPackagingClassifierService.classify(...)` — a batch of 100 pairs can block a Tomcat worker indefinitely if Vertex degrades past circuit-breaker timeouts. Requires an architectural decision (adopt `scope.joinUntil(...)` or rely on upstream request timeout); not in 10.3 scope.
- D14 (R2): Golden fixture `batch-packaging-v1.json` has 10 pairs covering the required packaging families, but only 3 descriptions verbatim match `DemoInvoiceFixtures.java` line items; the other 7 are synthesised. Categories covered; text fidelity deferred.
- D15 (R2): `request_cappedExceeded_retrySeconds_present_and_bounded` asserts `retrySeconds > 0 && retrySeconds <= 32*24*3600` but does not compute the exact delta to the next Europe/Budapest month boundary. Exact match is brittle across boundary days.
- D16 (R2): Counter increment in `BatchPackagingClassifierService.classify` can be missed if a forked task is interrupted between `results.set(idx, outcome)` and `incrementStrategyCounter(...)` — observability drift only; at most one tick per interrupted pair. Attribution improvement lives alongside D11 follow-up.

## Deferred from: code review of 10-5-product-first-aggregation-service (2026-04-20)

- W1: `orElse("")` for missing tax number — silent empty result when tenant has no NAV credentials; `InvoiceDrivenFilingAggregator.java:99`. DataSourceService behaviour; scope for a follow-up story.
- W2: `resolvedLineCount` semantics — VTSZ_FALLBACK lines counted in both `resolvedLineCount` and `unresolved.size()`; null-VTSZ lines inflate `invoiceLineCount` without appearing in either counter. `InvoiceDrivenFilingAggregator.java:122–183`. Story 10.8 audit panel should document field semantics.
- W3: `OffsetDateTime` in `AggregationCacheKey` — timezone-sensitive `equals()` may cause spurious cache misses if PostgreSQL returns non-UTC timestamps. Use `Instant`. `AggregationCacheKey.java:16`.
- W4: Audit fires only on cache miss — `recordAggregationRun` not emitted for cached responses; AuditService is log-only until Story 10.8 adds DB table. `InvoiceDrivenFilingAggregator.java:90`.
- W5: Orphaned chain unit test — to be added alongside P1 (orphaned chain formula fix). `InvoiceDrivenFilingAggregatorTest.java`.
- W6: Integration test uses demo data, not purpose-built 10-line/4-product seed — AC #18 is partially met; specific value assertions deferred to hardening pass. `InvoiceDrivenFilingAggregatorIntegrationTest.java`.
- W7: Load test registry mock empty — produces zero contributions, not 15,000 (AC #22). `InvoiceDrivenFilingAggregatorLoadTest.java`.
- W8: Zero-quantity / negative-quantity credit-note lines silently dropped with no unresolved entry — reduces EPR obligation for returns without visibility; business logic gap for a future story.
- W9: Same `wrapping_level` twice in one product — `buildCumulByLevel` HashMap.put silent overwrite (non-deterministic). Requires DB-level unique constraint on (product_id, wrapping_level). `InvoiceDrivenFilingAggregator.java:266–269`.

## Deferred from: code review of 10-2-kf-wizard-browse-button-on-registry (2026-04-19)

- D1: Double `close()` on PrimeVue Dialog dismiss (`@hide` + `@update:visible` both fire) — spec-prescribed; `cancelWizard()`/`$reset()` are idempotent. [KfCodeWizardDialog.vue:73-74]
- D2: `startResolveOnly()` in-flight race: rapid open→close→re-open could leave `isResolveOnlyMode=false` after stale fetch resolves — pre-existing pattern from `startWizard()` (no `isLoading` guard either); low probability. [stores/eprWizard.ts]
- D3: `startResolveOnly()` does not defensively reset `lastResolvedKfCode` — watcher fires on value change only; pre-existing session cleanup via `cancelWizard()` is reliable. [stores/eprWizard.ts]
- D4: Stale override state (`isOverrideActive`) if `cancelWizard()` is skipped between sessions — pre-existing pattern; `cancelWizard()`→`$reset()` cleans state on every normal close. [stores/eprWizard.ts]
- D5: Override falsy check for `overrideKfCode`/`overrideClassification` (empty string falls back to resolved value) — pre-existing pattern from `confirmAndLink()`. [stores/eprWizard.ts:505-513]
- D6: `startResolveOnly()` error path leaves blank stepper — no error banner in `KfCodeWizardDialog.vue`; pre-existing behavior matching `startWizard()` failure. [KfCodeWizardDialog.vue]
- D7: Duplicate `data-testid="wizard-cancel-button"` across both Step 4 footer branches — non-colliding; no test exercises cancel at Step 4. [WizardStepper.vue:275 and 301]

## Deferred from: code review of 10-6-epr-filing-ui-rebuild-two-tier-display (2026-04-21)
- D1: Race condition — concurrent fetchAggregation calls not cancelled; earlier request can resolve after a later one, showing stale data for the wrong period. AbortController integration needed. [eprFiling.ts]
- D2: AND-filter with both 'only missing' + 'only uncertain' chips active always produces an empty table silently (status is mutually exclusive). Consider empty-state message or chip exclusivity. [EprSoldProductsTable.vue]
- D3: getRowStatus matches description by strict equality — whitespace/case divergence between soldProducts and unresolved (from backend) could produce wrong status badge. Normalise server-side. [EprSoldProductsTable.vue]
