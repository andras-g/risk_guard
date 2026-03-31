# Story 6.2: Manual Adapter Quarantine

Status: done

## Story

As an Admin,
I want a "Force Quarantine" toggle for each data source adapter,
so that I can manually disable a failing API integration if the canary hasn't caught it yet.

## Acceptance Criteria

1. **Quarantine toggle endpoint**
   - Given an authenticated `SME_ADMIN` user
   - When they call `POST /api/v1/admin/datasources/{adapterName}/quarantine` with `{"quarantined": true}`
   - Then the backend calls `circuitBreaker.transitionToForcedOpenState()` on that adapter's named circuit breaker
   - And `adapter_health.quarantined` is set to `true` for that adapter
   - And the action is logged to `admin_action_log`
   - And the response is HTTP 200 with the updated `AdapterHealthResponse` (circuitBreakerState = `FORCED_OPEN`)
   - And non-`SME_ADMIN` callers receive HTTP 403

2. **Release quarantine**
   - Given an adapter is currently quarantined (`FORCED_OPEN`)
   - When an `SME_ADMIN` calls the same endpoint with `{"quarantined": false}`
   - Then the backend calls `circuitBreaker.transitionToClosedState()` on that circuit breaker
   - And `adapter_health.quarantined` is set to `false`
   - And the action is logged to `admin_action_log`
   - And the response is HTTP 200 with `circuitBreakerState = CLOSED`

3. **Adapter not found → 404; no circuit breaker registered → 422**
   - Given an unknown `{adapterName}` not in the registered `List<CompanyDataPort>`
   - When the quarantine endpoint is called
   - Then HTTP 404 is returned
   - Given a known adapter with no Resilience4j circuit breaker registered
   - When the quarantine endpoint is called
   - Then HTTP 422 is returned with message "No circuit breaker registered for adapter '{name}'"

4. **Verdicts marked unavailable while quarantined**
   - Given an adapter is in `FORCED_OPEN` state
   - When any screening request triggers `CompanyDataAggregator`
   - Then Resilience4j throws `CallNotPermittedException`, the fallback returns unavailable `ScrapedData`
   - And the verdict for that source is marked `SOURCE_UNAVAILABLE` (existing `CompanyDataAggregator` behavior — no new code)

5. **Global frontend health banner**
   - Given one or more adapters have `circuitBreakerState === 'FORCED_OPEN'`
   - When a `SME_ADMIN` user views any page in the authenticated layout
   - Then a yellow/amber dismissible banner appears at the top: "Data source '{name}' is Under Maintenance"
   - And the banner lists all quarantined adapter names
   - The banner is powered by `useHealthStore()` — the health store is fetched globally in the default layout when the user is `SME_ADMIN`

6. **Quarantine toggle in dashboard UI**
   - Given the user is on `/admin/datasources`
   - When they view an adapter card
   - Then an `InputSwitch` toggle labeled "Force Quarantine" is shown on each card
   - And the toggle is `true` (on) when `circuitBreakerState === 'FORCED_OPEN'`
   - And toggling calls `healthStore.quarantineAdapter(adapterName, newValue)` → `POST /quarantine`
   - And while the request is in-flight, the toggle is disabled (loading state)
   - And after the request completes, `healthStore.fetchHealth()` is called to refresh the dashboard

7. **Circuit breaker event listener handles FORCED_OPEN state**
   - When a circuit breaker transitions to `FORCED_OPEN`
   - Then `CircuitBreakerEventListener` writes status `QUARANTINED` to `adapter_health`

8. **Quarantine persisted across server restarts**
   - Given `adapter_health.quarantined = true` for an adapter in the DB
   - When the application starts up
   - Then `CircuitBreakerEventListener.init()` re-applies `transitionToForcedOpenState()` for that adapter's circuit breaker

9. **DB migration**
   - Given the Flyway migration `V20260331_002__add_quarantine_and_admin_action_log.sql` runs
   - Then `adapter_health` gains column: `quarantined BOOLEAN NOT NULL DEFAULT FALSE`
   - And `admin_action_log` table is created with: `id UUID PK DEFAULT gen_random_uuid()`, `actor_user_id UUID NOT NULL`, `action VARCHAR(50) NOT NULL`, `target VARCHAR(255) NOT NULL`, `details JSONB`, `performed_at TIMESTAMPTZ NOT NULL DEFAULT now()`

## Tasks / Subtasks

- [x] Task 1 — DB migration (AC: #9)
  - [x] Create `backend/src/main/resources/db/migration/V20260331_002__add_quarantine_and_admin_action_log.sql`
  - [x] `ALTER TABLE adapter_health ADD COLUMN quarantined BOOLEAN NOT NULL DEFAULT FALSE;`
  - [x] `CREATE TABLE admin_action_log (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_user_id UUID NOT NULL, action VARCHAR(50) NOT NULL, target VARCHAR(255) NOT NULL, details JSONB, performed_at TIMESTAMPTZ NOT NULL DEFAULT now());`
  - [x] `CREATE INDEX idx_admin_action_log_performed_at ON admin_action_log (performed_at DESC);`

- [x] Task 2 — Backend: `QuarantineRequest` DTO (AC: #1, #2)
  - [x] Create `backend/src/main/java/hu/riskguard/datasource/api/dto/QuarantineRequest.java`
  - [x] Java record: `(boolean quarantined)` — annotate with `@NotNull` via Jakarta Bean Validation if desired

- [x] Task 3 — Backend: `AdapterHealthRepository` additions (AC: #1, #2, #8, #9)
  - [x] Add `setQuarantined(String adapterName, boolean quarantined, Instant now)` — UPDATE `adapter_health` SET `quarantined = ?`, `updated_at = ?` WHERE `adapter_name = ?`
  - [x] Add `findQuarantinedAdapterNames()` → `List<String>` — SELECT `adapter_name` FROM `adapter_health` WHERE `quarantined = TRUE`
  - [x] Add `insertAdminAction(UUID actorUserId, String action, String target, String details, Instant now)` — INSERT INTO `admin_action_log`

- [x] Task 4 — Backend: Update `CircuitBreakerEventListener` (AC: #7, #8)
  - [x] In the `onStateTransition` switch, add: `case FORCED_OPEN -> "QUARANTINED"` before the `default` case
  - [x] In `init()`, after registering listeners, call `adapterHealthRepository.findQuarantinedAdapterNames()` and for each name, call `circuitBreakerRegistry.find(name).ifPresent(cb -> cb.transitionToForcedOpenState())` to restore quarantine on startup
  - [x] Log restored quarantines at INFO level: `"Restored quarantine for circuit breaker '{}'"`

- [x] Task 5 — Backend: Quarantine endpoint in `DataSourceAdminController` (AC: #1, #2, #3)
  - [x] Add `POST /{adapterName}/quarantine` handler method: `public AdapterHealthResponse quarantine(@PathVariable String adapterName, @RequestBody QuarantineRequest request, @AuthenticationPrincipal Jwt jwt)`
  - [x] Call `requireAdminRole(jwt)` first
  - [x] Find adapter: `adapters.stream().filter(a -> a.adapterName().equals(adapterName)).findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Adapter not found: " + adapterName))`
  - [x] Find CB: `circuitBreakerRegistry.find(adapterName).orElseThrow(() -> new ResponseStatusException(UNPROCESSABLE_ENTITY, "No circuit breaker registered for adapter '" + adapterName + "'"))`
  - [x] If `request.quarantined()`: `cb.transitionToForcedOpenState()`; else `cb.transitionToClosedState()`
  - [x] Call `adapterHealthRepository.setQuarantined(adapterName, request.quarantined(), Instant.now())`
  - [x] Get actor UUID: `UUID actorUserId = JwtUtil.requireUuidClaim(jwt, "user_id")`
  - [x] Call `adapterHealthRepository.insertAdminAction(actorUserId, request.quarantined() ? "QUARANTINE" : "RELEASE_QUARANTINE", adapterName, "{\"quarantined\": " + request.quarantined() + "}", Instant.now())`
  - [x] Return the updated health snapshot: call the private `buildResponse(adapter, dataSourceMode, adapterHealthRepository.findAll(), adapterHealthRepository.findAllCredentialStatuses(List.of(adapterName)))` — or extract a helper `buildSingleResponse(adapterName)` for cleanliness
  - [x] Import: `import org.springframework.web.bind.annotation.PostMapping; import org.springframework.web.bind.annotation.PathVariable; import org.springframework.web.bind.annotation.RequestBody;`

- [x] Task 6 — Frontend: Update `health.ts` store (AC: #6)
  - [x] Add `quarantining: Record<string, boolean>` to `HealthState` — tracks per-adapter in-flight state
  - [x] Add action `quarantineAdapter(adapterName: string, quarantined: boolean)`:
    - Set `this.quarantining[adapterName] = true`
    - `await $fetch('/api/v1/admin/datasources/' + adapterName + '/quarantine', { method: 'POST', body: { quarantined }, baseURL: config.public.apiBase, credentials: 'include' })`
    - Call `await this.fetchHealth()` to refresh
    - In `finally`: `this.quarantining[adapterName] = false`

- [x] Task 7 — Frontend: i18n keys (AC: #5, #6)
  - [x] Add to `frontend/app/i18n/en/admin.json` under `admin.datasources`:
    - `"quarantine"`, `"quarantine_release"`, `"quarantineLabel"` (= "Force Quarantine"), `"quarantineConfirm"` (= "Quarantine this adapter?"), `"releaseConfirm"` (= "Release quarantine?")
    - `"states.quarantined"` (= "Quarantined")
    - `"banner.underMaintenance"` (= "{adapter} is Under Maintenance"), `"banner.title"` (= "Data Source Maintenance")
  - [x] Add same keys in `frontend/app/i18n/hu/admin.json` in Hungarian

- [x] Task 8 — Frontend: Update `DataSourceHealthDashboard.vue` (AC: #6, AC: #7 badge)
  - [x] Import `InputSwitch from 'primevue/inputswitch'` and `useHealthStore`
  - [x] Accept new prop: `quarantining: Record<string, boolean>` (from parent page)
  - [x] Add `FORCED_OPEN` handling in `cbStateBadgeClass`: `case 'FORCED_OPEN': return 'bg-orange-100 text-orange-800'`
  - [x] Add `FORCED_OPEN` in `cbStateBadgeLabel`: `case 'FORCED_OPEN': return t('admin.datasources.states.quarantined')`
  - [x] Add quarantine toggle below circuit breaker badge in each adapter card
  - [x] Add `emit('quarantine', adapterName: string, quarantined: boolean)` in `defineEmits`
  - [x] ARIA: extend `ariaStatusMessage` to also announce `FORCED_OPEN` transitions (existing watcher already handles it via `cbStateBadgeLabel`)

- [x] Task 9 — Frontend: Update `pages/admin/datasources.vue` (AC: #6)
  - [x] Import and expose `quarantining` from health store: `const quarantining = computed(() => healthStore.quarantining)`
  - [x] Pass `:quarantining="quarantining"` prop to `<DataSourceHealthDashboard>`
  - [x] Handle `@quarantine` emit: `async function handleQuarantine(adapterName: string, quarantined: boolean) { await healthStore.quarantineAdapter(adapterName, quarantined) }`
  - [x] Add `@quarantine="handleQuarantine"` to the component

- [x] Task 10 — Frontend: Global health banner in `default.vue` (AC: #5)
  - [x] Import `useHealthStore` and `useIdentityStore`
  - [x] In `<script setup>`, after existing code: `const identityStore = useIdentityStore()`, `const healthStore = useHealthStore()`
  - [x] `const quarantinedAdapters = computed(() => healthStore.adapters.filter(a => a.circuitBreakerState === 'FORCED_OPEN'))`
  - [x] `onMounted(() => { if (identityStore.user?.role === 'SME_ADMIN') { healthStore.fetchHealth() } })` — supplements, not replaces, the existing admin page polling
  - [x] Added banner inside main content div before TopBar
  - [x] Added `useI18n()` to the layout script

- [x] Task 11 — Backend: Tests (AC: #1, #2, #3)
  - [x] Extend `backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java`
  - [x] Test: `quarantineAdapter_smeAdmin_returns200AndForcedOpen`
  - [x] Test: `releaseQuarantine_smeAdmin_returns200AndClosed`
  - [x] Test: `quarantineAdapter_nonAdmin_returns403`
  - [x] Test: `quarantineAdapter_unknownAdapter_returns404`
  - [x] Test: `quarantineAdapter_noCircuitBreaker_returns422`
  - [x] Follow `@MockitoSettings(strictness = Strictness.LENIENT)` pattern established in existing test

- [x] Task 12 — Frontend: Update `DataSourceHealthDashboard.spec.ts` (AC: #6)
  - [x] Add test: quarantine toggle is rendered per adapter card
  - [x] Add test: toggle is disabled when `quarantining[adapterName]` is true
  - [x] Add test: toggle emits `quarantine` event with correct args on click
  - [x] Add test: `FORCED_OPEN` state shows orange badge with "Quarantined" label
  - [x] Follow existing spec pattern (stubs for PrimeVue components)

- [x] Task 13 — Final verification
  - [x] Backend: `./gradlew test` — BUILD SUCCESSFUL, 682 tests, 0 failures
  - [x] Frontend: `vitest run` — 594 tests, all green
  - [x] NamingConventionTest: no violations (new DTO in `hu.riskguard.datasource.api.dto.*` — correct)

### Review Findings

- [x] [Review][Patch] P1 — Non-atomic CB/DB write: if `setQuarantined` throws after `transitionToForcedOpenState()`, CB is stuck FORCED_OPEN with `quarantined=false` in DB — split-brain on restart [DataSourceAdminController.java]
- [x] [Review][Patch] P2 — `setQuarantined` and `insertAdminAction` are non-transactional: if audit insert throws, quarantine is set but audit is missing and caller gets misleading 500 [DataSourceAdminController.java]
- [x] [Review][Patch] P3 (D1) — Release-quarantine must guard `cb.getState() == FORCED_OPEN` before calling `transitionToClosedState()`; if CB is not in `FORCED_OPEN`, return HTTP 409 ("Adapter is not quarantined"); also prevents `IllegalStateException` on already-CLOSED CB [DataSourceAdminController.java]
- [x] [Review][Patch] P4 — `quarantineAdapter` swallows `$fetch` errors with no catch; failed POST gives user zero feedback (no toast, no error state) [health.ts]
- [x] [Review][Patch] P5 — Global banner is not dismissible; AC#5 explicitly says "dismissible banner" [default.vue]
- [x] [Review][Patch] P6 — Banner i18n text missing "Data source" prefix and name quotes; AC#5 requires `"Data source '{name}' is Under Maintenance"`, current value is `"{adapter} is Under Maintenance"` [i18n/en/admin.json, i18n/hu/admin.json, default.vue]
- [x] [Review][Patch] P7 — Startup quarantine restore silently skips adapters with no registered CB; should log WARN [CircuitBreakerEventListener.java]
- [x] [Review][Patch] P8 — In DEMO mode `buildResponse` overrides `cbState → "CLOSED"` after quarantine, making the toggle appear to do nothing [DataSourceAdminController.java]
- [x] [Review][Defer] W1 — Hand-rolled JSON string for audit `details` field — safe now (boolean), fragile pattern for future [DataSourceAdminController.java] — deferred, pre-existing pattern
- [x] [Review][Defer] W2 — `adapterName` path variable has no length/pattern validation at API boundary [DataSourceAdminController.java] — deferred, adapter-exists check + SME_ADMIN gate limits risk
- [x] [Review][Defer] W3 — No confirmation dialog before quarantine toggle fires; i18n keys exist but AC#6 doesn't mandate it — deferred, future UX enhancement
- [x] [Review][Defer] W4 — Banner fetches health once on mount; stale on long sessions on non-datasources pages — deferred, acceptable given admin page polling
- [x] [Review][Defer] W5 — `setQuarantined` INSERT ON CONFLICT may fail if adapter row is missing other NOT NULL columns (fresh DB) — deferred, rows guaranteed by 6.1 startup listener
- [x] [Review][Defer] W6 — `recordStateTransition` + `setQuarantined` write to same row without coordination — deferred, update different columns, last-write on `updated_at` acceptable
- [x] [Review][Defer] W7 — `findAll()` / `AdapterHealthRow` doesn't expose `quarantined` flag; frontend infers from CB state — deferred, no current consumer needs it
- [x] [Review][Defer] W8 — Startup quarantine restore race with in-flight health checks — deferred, narrow window, hard to address without startup barrier
- [x] [Review][Defer] W9 — `buildResponse` does full O(n) reload for single-adapter quarantine — deferred, low-frequency admin operation
- [x] [Review][Defer] W10 — `actor_user_id` has no FK constraint on `admin_action_log` — deferred, intentional soft audit (no user table in this service)
- [x] [Review][Defer] W11 — `admin_action_log` missing indexes on `actor_user_id` and `action` — deferred, no audit viewer feature yet
- [x] [Review][Defer] W12 — `setQuarantined` phantom row: silently creates health row for never-registered adapter — deferred, requires SME_ADMIN access, acceptable defensive behavior
- [x] [Review][Defer] W13 — `FORCED_OPEN` conflated with quarantine in banner — deferred, only one code path creates FORCED_OPEN in current system

### Review Findings R2

- [x] [Review][Patch] R2-P1 — i18n key mismatch: `t('common.dismiss')` in `default.vue:27` resolves to raw key string; actual key is `common.actions.dismiss` [frontend/app/layouts/default.vue:27]
- [x] [Review][Patch] R2-P2 — Dead code: `setQuarantined()` and `insertAdminAction()` in `AdapterHealthRepository` have no callers after R1 P1+P2 fix; controller uses `setQuarantinedAndLogAction()` exclusively [backend/src/main/java/hu/riskguard/datasource/internal/AdapterHealthRepository.java]

## Dev Notes

### Backend: Circuit Breaker State Transitions

Resilience4j 2.2.0 (`resilience4j-spring-boot3:2.2.0`):
- `circuitBreaker.transitionToForcedOpenState()` — manually forces FORCED_OPEN; all calls throw `CallNotPermittedException` immediately; does NOT require reaching failure threshold
- `circuitBreaker.transitionToClosedState()` — manually resets to CLOSED with zeroed metrics; use this for release
- `FORCED_OPEN` is a distinct state from `OPEN` (auto-open by failures). Both block calls; only `FORCED_OPEN` is the manual quarantine state.

**Effect on verdicts (existing behavior, no new code):** `DemoCompanyDataAdapter.fetchFallback()` is invoked → returns `ScrapedData(unavailable=true)` → `CompanyDataAggregator` maps to `SOURCE_UNAVAILABLE` verdict field. This is already working from Story 6.1's `@CircuitBreaker(name = "demo", fallbackMethod = "fetchFallback")` annotation.

**Startup re-quarantine ordering:** `@PostConstruct init()` runs AFTER the Spring context is fully initialized, so all circuit breakers registered by `DataSourceModeConfig` will already be present in the registry. Call `findQuarantinedAdapterNames()` AFTER the `breakers.forEach(this::registerListeners)` loop so that the re-quarantine also fires state-transition events (which the listener is now subscribed to), correctly updating `adapter_health.status` to `QUARANTINED`.

### Backend: DB Migration Filename

Previous: `V20260331_001__create_adapter_health_tables.sql`
This story: `V20260331_002__add_quarantine_and_admin_action_log.sql`
The `002` suffix is correct — same date, second migration of the day.

### Backend: `buildResponse()` reuse for quarantine endpoint

The private `buildResponse()` in `DataSourceAdminController` needs to be called for the single adapter after quarantine. Options:
1. Call `adapterHealthRepository.findAll()` again (simple, minor extra DB round-trip)
2. Inline a single-adapter response build

**Recommended**: Call `findAll()` again — consistent with the GET endpoint behavior, no premature optimization. The admin quarantine is a low-frequency operation.

### Backend: `setQuarantined()` implementation

Use a raw SQL UPDATE (not INSERT ON CONFLICT) since the adapter_health row is guaranteed to exist once Story 6.1 has run (CircuitBreakerEventListener writes on startup). But be defensive — use INSERT ON CONFLICT DO UPDATE:

```sql
INSERT INTO adapter_health (adapter_name, quarantined, updated_at)
VALUES (?, ?, ?)
ON CONFLICT (adapter_name) DO UPDATE SET
    quarantined = EXCLUDED.quarantined,
    updated_at  = EXCLUDED.updated_at
```

### Backend: Role Check Pattern

Existing `requireAdminRole(Jwt jwt)` private method in `DataSourceAdminController` handles the 403 check. Reuse it in the new quarantine method — already defined in the class.

### Backend: JwtUtil for actor user ID

```java
UUID actorUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
```
Claim `"user_id"` is set in `TokenProvider.java`. Import: `import hu.riskguard.core.util.JwtUtil;`

### Frontend: `InputSwitch` component

Project uses `InputSwitch` from `primevue/inputswitch` (PrimeVue 4.5.4 — confirmed in `MaterialInventoryBlock.vue`). Do NOT use `ToggleSwitch` — that name is not verified as used in this project.

### Frontend: Health store `quarantining` state

Initialize as `quarantining: {} as Record<string, boolean>` in `HealthState`. The per-adapter loading flag prevents double-toggling while the request is in-flight.

### Frontend: Default layout global banner placement

Add the banner INSIDE the `<div class="flex flex-col min-h-screen...">` div, just before `<CommonAppTopBar />` so it appears at the top of the content area (below the sidebar area). This avoids z-index conflicts with the sidebar.

### Frontend: useI18n in default.vue

Check if `default.vue` already uses `useI18n` before adding — if not, add `const { t } = useI18n()` alongside the existing `storeToRefs` imports.

### Testing: Mock `CircuitBreaker` in tests

`CircuitBreaker` is an interface. Mock it with Mockito: `CircuitBreaker mockCb = mock(CircuitBreaker.class)`. Mock `circuitBreakerRegistry.find("demo")` to return `Optional.of(mockCb)`. Verify `verify(mockCb).transitionToForcedOpenState()` after quarantine call.

The `buildResponse()` method is called after the transition — the mocked CB's `getState()` must return a non-null State. Mock it: `when(mockCb.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN)` and `when(mockCb.getMetrics()).thenReturn(mock(CircuitBreaker.Metrics.class))`.

### Project Structure Notes

| Artifact | Path |
|----------|------|
| DB migration | `backend/src/main/resources/db/migration/V20260331_002__add_quarantine_and_admin_action_log.sql` |
| DTO | `backend/src/main/java/hu/riskguard/datasource/api/dto/QuarantineRequest.java` |
| Repository additions | `backend/src/main/java/hu/riskguard/datasource/internal/AdapterHealthRepository.java` (modified) |
| Event listener | `backend/src/main/java/hu/riskguard/datasource/internal/CircuitBreakerEventListener.java` (modified) |
| Controller | `backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java` (modified) |
| Controller test | `backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java` (modified) |
| Pinia store | `frontend/app/stores/health.ts` (modified) |
| Dashboard component | `frontend/app/components/Admin/DataSourceHealthDashboard.vue` (modified) |
| Dashboard spec | `frontend/app/components/Admin/DataSourceHealthDashboard.spec.ts` (modified) |
| Admin page | `frontend/app/pages/admin/datasources.vue` (modified) |
| Default layout | `frontend/app/layouts/default.vue` (modified) |
| EN i18n | `frontend/app/i18n/en/admin.json` (modified) |
| HU i18n | `frontend/app/i18n/hu/admin.json` (modified) |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 6.2] — acceptance criteria and epic goal
- [Source: _bmad-output/implementation-artifacts/6-1-data-source-health-dashboard-the-heartbeat.md] — previous story: all datasource module patterns, AdapterHealthRepository atomic SQL methods, CircuitBreakerEventListener registration, role check pattern, i18n admin.json structure, DataSourceHealthDashboard.vue badge helpers
- [Source: _bmad-output/planning-artifacts/architecture.md#ADR-4] — data source adapters, Resilience4j circuit breakers, CompanyDataPort
- [Source: backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java] — existing controller structure, requireAdminRole(), buildResponse()
- [Source: backend/src/main/java/hu/riskguard/datasource/internal/AdapterHealthRepository.java] — string-based jOOQ DSL pattern, AdapterHealthRow record, existing atomic methods
- [Source: backend/src/main/java/hu/riskguard/datasource/internal/CircuitBreakerEventListener.java] — onStateTransition switch, @PostConstruct init() pattern
- [Source: backend/src/main/java/hu/riskguard/core/util/JwtUtil.java] — requireUuidClaim(jwt, "user_id")
- [Source: frontend/app/stores/health.ts] — existing AdapterHealth interface, fetchHealth() action, $fetch pattern
- [Source: frontend/app/components/Admin/DataSourceHealthDashboard.vue] — existing badge helpers, InputSwitch import pattern
- [Source: frontend/app/components/Epr/MaterialInventoryBlock.vue] — InputSwitch usage pattern (primevue/inputswitch)
- [Source: frontend/app/layouts/default.vue] — layout structure for banner placement
- [Source: backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java] — @MockitoSettings(LENIENT) pattern, existing test structure

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None.

### Completion Notes List

- Implemented `POST /api/v1/admin/datasources/{adapterName}/quarantine` endpoint with 200/403/404/422 responses.
- `CircuitBreakerEventListener.init()` now restores quarantine state from DB on startup by calling `transitionToForcedOpenState()` for each persisted quarantined adapter.
- `FORCED_OPEN` circuit breaker state maps to `"QUARANTINED"` status in `adapter_health`.
- Frontend: `quarantining` per-adapter loading flag prevents double-toggling; `quarantineAdapter` action in health store handles POST + refresh.
- Global amber banner in `default.vue` renders for all `FORCED_OPEN` adapters when user is `SME_ADMIN`.
- InputSwitchStub in spec fixed to use plain JS `$event.target.checked` (no TypeScript cast in template).
- Backend: 682 tests, 0 failures. Frontend: 594 tests, all green.
- ✅ Resolved review finding [Patch] P1+P2: Added `setQuarantinedAndLogAction` with jOOQ `dsl.transaction` to `AdapterHealthRepository` — both DB writes atomic; CB transition moved AFTER DB commit so no split-brain on failure.
- ✅ Resolved review finding [Patch] P3: Added `cb.getState() == FORCED_OPEN` guard before `transitionToClosedState()`; returns HTTP 409 if adapter is not quarantined. New test `releaseQuarantine_notQuarantined_returns409` added.
- ✅ Resolved review finding [Patch] P4: `quarantineAdapter` now catches errors, sets `this.error`, and re-throws so callers see the failure.
- ✅ Resolved review finding [Patch] P5: Added `bannerDismissed` ref and a dismiss button (✕) to the global quarantine banner in `default.vue`.
- ✅ Resolved review finding [Patch] P6: Updated `underMaintenance` i18n key to `"Data source '{name}' is Under Maintenance"` (EN) and `"A '{name}' adatforrás karbantartás alatt áll"` (HU); template now passes `{ name: a.adapterName }`. Added `common.actions.dismiss` key to both locales.
- ✅ Resolved review finding [Patch] P7: `CircuitBreakerEventListener.init()` now logs WARN when a quarantined adapter has no registered CB instead of silently skipping.
- ✅ Resolved review finding [Patch] P8: `buildResponse` DEMO mode override now carves out `FORCED_OPEN` — quarantined adapters remain visible as `FORCED_OPEN` even in DEMO mode.
- Backend: 683 tests, 0 failures. Frontend: 594 tests, all green.

### File List

- `backend/src/main/resources/db/migration/V20260331_002__add_quarantine_and_admin_action_log.sql` (new)
- `backend/src/main/java/hu/riskguard/datasource/api/dto/QuarantineRequest.java` (new)
- `backend/src/main/java/hu/riskguard/datasource/internal/AdapterHealthRepository.java` (modified)
- `backend/src/main/java/hu/riskguard/datasource/internal/CircuitBreakerEventListener.java` (modified)
- `backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java` (modified)
- `backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java` (modified)
- `frontend/app/stores/health.ts` (modified)
- `frontend/app/components/Admin/DataSourceHealthDashboard.vue` (modified)
- `frontend/app/components/Admin/DataSourceHealthDashboard.spec.ts` (modified)
- `frontend/app/pages/admin/datasources.vue` (modified)
- `frontend/app/layouts/default.vue` (modified)
- `frontend/app/i18n/en/admin.json` (modified)
- `frontend/app/i18n/hu/admin.json` (modified)
- `frontend/app/i18n/en/common.json` (modified — added `common.actions.dismiss`)
- `frontend/app/i18n/hu/common.json` (modified — added `common.actions.dismiss`)

## Change Log

- 2026-03-31: Story created — Manual Adapter Quarantine with force-open/release CB endpoints, admin_action_log audit trail, quarantine persistence across restarts, global health banner in default layout, InputSwitch toggle in DataSourceHealthDashboard.
- 2026-03-31: Implementation complete — all 13 tasks done, 682 backend + 594 frontend tests green.
- 2026-03-31: Addressed code review findings — 8 patch items resolved (P1-P8): atomic DB transaction, 409 guard, error propagation in store, dismissible banner, i18n text fix, WARN log on missing CB, DEMO mode carve-out for FORCED_OPEN. 683 backend + 594 frontend tests green.
- 2026-03-31: Code review R2 — 2 patch items resolved: i18n key mismatch (`common.dismiss` → `common.actions.dismiss`), dead code removal (`setQuarantined` + `insertAdminAction` in AdapterHealthRepository). 683 backend + 594 frontend + 5 e2e all green. Status → done.
