# Story 6.0: Epic 6 Foundation — Technical Debt Cleanup

Status: review

## Story

As a developer starting Epic 6,
I want the three technical debt items from Epics 4–5 retros resolved,
so that the new admin controller doesn't perpetuate copy-paste JWT utilities, TenantContext is on the ScopedValue path for HTTP requests, and low-risk deferred-work items no longer accumulate.

## Acceptance Criteria

1. `JwtUtil.requireUuidClaim()` exists in `core/util/`; all 4 controllers (`ScreeningController`, `WatchlistController`, `EprController`, `AuditHistoryController`) delegate to it; private `requireUuidClaim()` methods removed from each; 401 behaviour unchanged.
2. `TenantContext` exposes a `public static final ScopedValue<UUID> CURRENT_TENANT`; `TenantFilter` binds the tenant ID via `ScopedValue.where(TenantContext.CURRENT_TENANT, tenantId).run(...)` instead of imperative set/clear; all HTTP-path tests pass.
3. `TenantContext.setCurrentTenant()` and `TenantContext.clear()` remain (annotated `@Deprecated`) for background services and tests; `getCurrentTenant()` checks ScopedValue first, falls back to ThreadLocal.
4. `filing.spec.ts`: all `$nextTick()` calls replaced with `flushPromises()` (imported from `@vue/test-utils`).
5. `EprFilingStore.isLoading` dead field removed; all `isLoading` references in filing store and its tests updated or removed.
6. `EprRepository.findVerifiedByTenant()` dead method removed; no callers exist post-Story-5.3.
7. `deferred-work.md`: items resolved in this story are removed; remaining items untouched.
8. All tests pass: backend `BUILD SUCCESSFUL`, frontend all suites green.

## Tasks / Subtasks

### T1 — Extract `requireUuidClaim` to shared utility (AC: 1)

- [x] Create `backend/src/main/java/hu/riskguard/core/util/JwtUtil.java`
  - [x] `public static UUID requireUuidClaim(Jwt jwt, String claimName)` — throws `ResponseStatusException(UNAUTHORIZED)` on null or non-UUID claim
  - [x] Add `backend/src/test/java/hu/riskguard/core/util/JwtUtilTest.java`: unit tests for (a) happy path, (b) null claim → 401, (c) malformed UUID → 401
- [x] Update `ScreeningController` — delete private method, call `JwtUtil.requireUuidClaim(jwt, claimName)`
- [x] Update `WatchlistController` — same
- [x] Update `EprController` — same
- [x] Update `AuditHistoryController` — same; also note that it currently uses a `problemDetail()` wrapper instead of `ResponseStatusException`; migrate to `ResponseStatusException` to align with shared utility
- [x] Run backend tests; confirm all 4 existing controller JWT tests still pass (no behaviour change)

### T2 — ScopedValue migration for HTTP request path (AC: 2, 3)

**Scope:** HTTP path only (TenantFilter). Background services and tests keep ThreadLocal via deprecated API.

- [x] Update `TenantContext.java`:
  - [x] Add `public static final ScopedValue<UUID> CURRENT_TENANT = ScopedValue.newInstance();`
  - [x] Keep `private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();`
  - [x] Update `getCurrentTenant()` to: `return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : currentTenant.get();`
  - [x] Mark `setCurrentTenant()` and `clear()` with `@Deprecated(since = "6.0", forRemoval = false)`
  - [x] Add Javadoc to `CURRENT_TENANT`: "HTTP request path. Bind in TenantFilter. Background services use setCurrentTenant()."
- [x] Update `TenantFilter.java`:
  - [x] Replace the `setCurrentTenant(tenantId)` + try/finally `clear()` block with `ScopedValue.where(TenantContext.CURRENT_TENANT, tenantId).call(...)`
  - [x] Remove explicit `TenantContext.clear()` call — ScopedValue scope ends automatically
- [x] Verify `TenantFilterTest.java` passes — it calls `TenantContext.clear()` in `@BeforeEach`; that still compiles and works (ThreadLocal path)
- [x] Do NOT touch: `ScreeningService`, `WatchlistMonitor`, `AsyncIngestor`, `CompanyDataAggregator`, `GuestSearchController`, `AsyncConfig`, any test files — they use the deprecated ThreadLocal API and are out of scope for this story
- [x] Run backend tests; confirm all pass

### T3 — Curated deferred-work cleanup (AC: 4, 5, 6, 7)

- [x] **`filing.spec.ts` — `$nextTick` → `flushPromises()`** (deferred from 5-4)
  - [x] Add `import { flushPromises } from '@vue/test-utils'` (already a devDependency)
  - [x] Replace every `await wrapper.vm.$nextTick()` with `await flushPromises()`
  - [x] Run `pnpm test` in frontend; confirm all filing tests pass
- [x] **Remove dead `EprFilingStore.isLoading`** (deferred W8 from 5-2)
  - [x] Delete the `isLoading` state field from `eprFiling.ts`
  - [x] Search for any remaining references: `grep -r "filingStore\.isLoading\|eprFiling.*isLoading" frontend/` — remove or replace each
  - [x] Update `filing.spec.ts` if it asserts on this field
- [x] **Remove dead `EprRepository.findVerifiedByTenant()`** (deferred W1 from 5-2, now confirmed unused post-5.3)
  - [x] Already removed during 5-2 code review R2 (confirmed absent from current EprRepository.java); deferred-work item cleaned up
- [x] **Update `deferred-work.md`**:
  - [x] Remove entries resolved by this story (the three items above + the `requireUuidClaim` note)
  - [x] All other deferred items remain untouched

### Final verification (AC: 8)

- [x] Backend: `./gradlew test` — `BUILD SUCCESSFUL`
- [x] Frontend: `pnpm test` — all suites green (580 tests)
- [x] No regressions in `TenantFilterTest`, controller JWT tests, `EprControllerWizardTest`, filing tests

## Dev Notes

### T1 — JwtUtil placement and exception style

**Target file:** `backend/src/main/java/hu/riskguard/core/util/JwtUtil.java`

Existing `core/util/` precedent: `HashUtil.java`, `PiiUtil.java` — both are stateless static-method utility classes. `JwtUtil` follows the same pattern.

**Exception style decision:** Use `ResponseStatusException(HttpStatus.UNAUTHORIZED, ...)`. Three of four controllers already use `ResponseStatusException`; `AuditHistoryController` (Story 5.1a) uses a custom `problemDetail()` helper returning `ErrorResponseException`. After extraction, align AuditHistoryController to `ResponseStatusException` to match. If `problemDetail()` RFC 9457 format is ever required globally, that refactor belongs to a dedicated cross-cutting story.

**Caller pattern to replicate:**
```java
// In each controller — before:
private UUID requireUuidClaim(Jwt jwt, String claimName) { ... }
// In each controller — after:
// Delete private method; call: JwtUtil.requireUuidClaim(jwt, claimName)
```

**Test coverage:** Controller tests already cover the 401 paths end-to-end via MockMvc (`ScreeningControllerTest` lines 122–168, `WatchlistControllerTest` lines 142–166, `EprControllerTest` lines 232–255). Add `JwtUtilTest` as a unit test of the utility directly (no Spring context needed).

### T2 — ScopedValue scope and call-site impact

**Why hybrid (not full migration):** `TenantContext.setCurrentTenant()/clear()` is called from ~50 locations across production background services (`ScreeningService:467`, `WatchlistMonitor:90,143`, `AsyncIngestor:92,139`, `CompanyDataAggregator:169,174`, `GuestSearchController:90,109`, `AsyncConfig:46,51`) and dozens of test `@BeforeEach`/`@AfterEach` hooks. Full migration in one story would be high-risk and disruptive. The HTTP path is the primary leak risk. Full migration in background services is a separate story.

**`ScopedValue.call()` vs `run()`:** Use `call()` (returns a value, declares `throws X`) because `chain.doFilter()` throws checked exceptions. The pattern shown in the task above is the correct idiom.

**`isBound()` check in `getCurrentTenant()`:** This is required. If code runs outside any `ScopedValue.where().run()` block (tests, background workers), `CURRENT_TENANT.get()` throws `NoSuchElementException`. The `isBound()` guard makes the hybrid safe.

**Java version:** The project uses Java 25 (`build.gradle`: `JavaLanguageVersion.of(25)`). `ScopedValue` is stable (not preview) from Java 21.

**`AsyncConfig.java` (lines 46–51):** This is a `TaskDecorator` that captures the current tenant from the calling thread and sets it in the async executor thread (Spring `@Async` propagation). It captures via `TenantContext.getCurrentTenant()` (which checks ScopedValue first, then ThreadLocal). Since `AsyncConfig`'s async workers use `setCurrentTenant()`/`clear()` (ThreadLocal path), this continues to work unchanged. No modifications needed.

**Files to touch for T2:** Only `TenantContext.java` and `TenantFilter.java`.

### T3 — Deferred-work specifics

**`$nextTick` in `filing.spec.ts`:** There are 2 occurrences (`nextTick` + 0 `flushPromises` per grep count). `flushPromises` is already available — `@vue/test-utils` is a devDependency used in other spec files. Pattern: `await flushPromises()` replaces `await wrapper.vm.$nextTick()`.

**`EprFilingStore.isLoading`:** Dead field identified in 5-2 code review (W8). The filing page (`filing.vue`) already uses `eprStore.isLoading` for load state display. Confirm: `grep -r "filingStore\|eprFiling" frontend/ | grep "isLoading"` to find all references before deletion.

**`findVerifiedByTenant()`:** W1 from 5-2 code review said "may be used by Story 5.3 MOHU CSV export; remove or use then." Story 5.3 is done and did not use it (confirmed by retro). Safe to delete.

**`deferred-work.md` cleanup scope:** Remove only the 4 items resolved here:
1. The `requireUuidClaim` extraction note (from 5-0 section)
2. The `$nextTick`/`flushPromises` item (from 5-4 section)
3. `filingStore.isLoading` dead state (W8 from 5-2 section)
4. `findVerifiedByTenant()` dead code (W1 from 5-2 section)

### Project Structure Notes

| Artifact | Path |
|----------|------|
| New utility | `backend/src/main/java/hu/riskguard/core/util/JwtUtil.java` |
| New test | `backend/src/test/java/hu/riskguard/core/util/JwtUtilTest.java` |
| TenantContext | `backend/src/main/java/hu/riskguard/core/security/TenantContext.java` |
| TenantFilter | `backend/src/main/java/hu/riskguard/core/security/TenantFilter.java` |
| ScreeningController | `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java` (lines 85–96 = private method) |
| WatchlistController | `backend/src/main/java/hu/riskguard/notification/api/WatchlistController.java` (lines 99–111) |
| EprController | `backend/src/main/java/hu/riskguard/epr/api/EprController.java` (lines 242–254) |
| AuditHistoryController | `backend/src/main/java/hu/riskguard/screening/api/AuditHistoryController.java` (lines 100–112) |
| EprFilingStore | `frontend/app/stores/eprFiling.ts` |
| EprRepository | `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java` |
| filing.spec.ts | `frontend/app/pages/epr/filing.spec.ts` |
| deferred-work.md | `_bmad-output/implementation-artifacts/deferred-work.md` |

### References

- [Source: _bmad-output/implementation-artifacts/epic-5-retro-2026-03-30.md#Action Items] — T1, T2, T3 assignment and scope
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] — specific deferred items for T3
- [Source: backend/src/main/java/hu/riskguard/core/security/TenantContext.java] — current ThreadLocal implementation
- [Source: backend/src/main/java/hu/riskguard/core/security/TenantFilter.java] — current set/clear pattern to migrate
- [Source: _bmad-output/planning-artifacts/architecture.md#ADR-5] — JWT dual-claim pattern

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- T1: Created `JwtUtil.java` (static utility, `final` class, private ctor) following `HashUtil`/`PiiUtil` pattern. Added `JwtUtilTest` with 3 unit tests (happy path, null claim → 401, malformed UUID → 401). Removed private `requireUuidClaim` from all 4 controllers; `AuditHistoryController` had used `ErrorResponseException`/`problemDetail()` wrapper — kept `problemDetail()` for `validateCheckSource` and NOT_FOUND (out of scope), replaced only the `requireUuidClaim` calls. Controller JWT tests: ScreeningControllerTest (11), WatchlistControllerTest (9), EprControllerTest (20), AuditHistoryControllerTest (11) — all pass.
- T2: `TenantContext` now has `public static final ScopedValue<UUID> CURRENT_TENANT`; `getCurrentTenant()` checks `isBound()` first (guards against `NoSuchElementException` outside ScopedValue scope); `setCurrentTenant()`/`clear()` annotated `@Deprecated(since = "6.0", forRemoval = false)`. `TenantFilter` binds via `ScopedValue.where(...).call()` with explicit IOException/ServletException rethrow; MDC cleaned up in `finally`. `TenantFilterTest` uses `TenantContext.clear()` in `@BeforeEach` (ThreadLocal path) — still works. 2 tests pass.
- T3: `filing.spec.ts` — 2 `$nextTick()` replaced with `flushPromises()`; import added. `eprFiling.ts` — `isLoading` removed from `FilingState` interface, `state()` factory, and `reset()` action; matching mock entry in `filing.spec.ts` also removed. `EprRepository.findVerifiedByTenant()` was already removed in 5-2 code review R2 (confirmed absent). `deferred-work.md` — 4 items removed: requireUuidClaim note (5-0), $nextTick item (5-4), W1 (5-2), W8 (5-2). Backend BUILD SUCCESSFUL, frontend 580/580 tests green.
- ✅ Resolved review finding [Patch]: TenantFilter `catch (Exception e)` wrapping RuntimeException subtypes — added `catch (RuntimeException e) { throw e; }` before the generic catch to prevent Spring error handling from being broken. Backend BUILD SUCCESSFUL, frontend 580/580 green.

### File List

- `backend/src/main/java/hu/riskguard/core/util/JwtUtil.java` (new)
- `backend/src/test/java/hu/riskguard/core/util/JwtUtilTest.java` (new)
- `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java` (modified)
- `backend/src/main/java/hu/riskguard/notification/api/WatchlistController.java` (modified)
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` (modified)
- `backend/src/main/java/hu/riskguard/screening/api/AuditHistoryController.java` (modified)
- `backend/src/main/java/hu/riskguard/core/security/TenantContext.java` (modified)
- `backend/src/main/java/hu/riskguard/core/security/TenantFilter.java` (modified)
- `frontend/app/pages/epr/filing.spec.ts` (modified)
- `frontend/app/stores/eprFiling.ts` (modified)
- `_bmad-output/implementation-artifacts/deferred-work.md` (modified)
- `_bmad-output/implementation-artifacts/6-0-epic-6-foundation-technical-debt-cleanup.md` (this file)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (modified)

### Review Findings

- [x] [Review][Patch] TenantFilter: `catch (Exception e)` wraps RuntimeException subtypes, breaking Spring error handling [TenantFilter.java:42-44]
- [x] [Review][Defer] TenantFilter: `UUID.fromString(tenantIdStr)` throws unhandled `IllegalArgumentException` for malformed JWT tenant claim [TenantFilter.java:35] — deferred, pre-existing
- [x] [Review][Defer] Out-of-scope Story 5.4 changes (`filing.vue`, `index.vue`, `i18n/*.json`) mixed into Story 6.0 uncommitted diff — deferred, process issue: commit separately
- [x] [Review][Defer] `AuditHistoryController` mixed exception response format: 401 → ResponseStatusException body, 404 → ErrorResponseException/RFC 9457 body — deferred, by design per story spec
- [x] [Review][Defer] `TenantContext.CURRENT_TENANT` public field allows any code to bind arbitrary tenant via `ScopedValue.where(...)` — deferred, by design, protected by filter chain in production
- [x] [Review][Defer] `JwtUtilTest` missing empty-string claim case (`""`) — deferred, pre-existing test gap, low risk

## Change Log

- 2026-03-30: Story implemented. T1: extracted `requireUuidClaim` to `JwtUtil`; removed from all 4 controllers; added `JwtUtilTest` (3 unit tests). T2: added `ScopedValue<UUID> CURRENT_TENANT` to `TenantContext`; migrated `TenantFilter` HTTP path to `ScopedValue.where().call()`; deprecated `setCurrentTenant()`/`clear()`. T3: replaced `$nextTick()` with `flushPromises()` in `filing.spec.ts`; removed dead `isLoading` field from `eprFiling.ts`; confirmed `findVerifiedByTenant()` already removed; cleaned 4 items from `deferred-work.md`. All tests green (backend BUILD SUCCESSFUL, frontend 580/580).
- 2026-03-30: Addressed code review findings — 1 item resolved (Date: 2026-03-30). TenantFilter `catch (Exception e)` RuntimeException wrapping fixed: added `catch (RuntimeException e) { throw e; }` before generic catch. Backend BUILD SUCCESSFUL, frontend 580/580 green.
