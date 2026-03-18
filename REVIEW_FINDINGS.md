# Code Review Findings

**Date:** 2026-03-16
**Scope:** All uncommitted changes

---

## HIGH Severity

### 1. Wrong CSS selector in E2E test

- **File:** `frontend/e2e/search-flow.e2e.ts`, line 83
- **Issue:** The test uses `.text-red-500` to locate validation error elements, but the actual components (`SearchBar.vue` line 56, `LandingSearchBar.vue` line 59) use the class `.text-at-risk`. The class `.text-red-500` only exists in the unrelated `ContextGuard.vue`.
- **Impact:** The test provides zero coverage for validation error behavior — it will either time out or pass vacuously.
- **Fix:** Change the selector to `.text-at-risk`, or better, add a `data-testid="validation-error"` attribute to the `<small>` element in `SearchBar.vue` and select by that.

### 2. Partial adapter data discarded for unavailable adapters

- **File:** `backend/src/main/java/hu/riskguard/datasource/internal/CompanyDataAggregator.java`, lines 108-134
- **Issue:** When an adapter returns `available=false`, `mergeResults` discards the adapter's actual `result.data()` and replaces it with a synthetic `Map.of("status", "SOURCE_UNAVAILABLE", "reason", ...)`. This violates the architecture's design principle: "positive evidence is actionable even from degraded sources."
- **Impact:** If a future adapter (e.g., NAV) returns `available=false` but includes partial risk-relevant data (like `hasPublicDebt=true`), that data is silently lost. Not broken today with the demo adapter (always available), but a latent data contract violation.
- **Fix:** When `available=false`, preserve the adapter's actual `result.data()` (merging in the status/reason metadata) rather than replacing it entirely with the generic map.

---

## MEDIUM Severity

### 3. Inconsistent timestamps across audit trail

- **File:** `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java`, lines 115, 157, 189
- **Issue:** Three separate `OffsetDateTime.now()` calls are used:
  - Line 115: `now` — used for snapshot `CREATED_AT` and audit log `SEARCHED_AT`
  - Line 157: `checkedAt` — used for snapshot `CHECKED_AT` and verdict `CREATED_AT`
  - Line 189: third `now()` — used as `evaluationTime` in `VerdictEngine.evaluate()`
- **Impact:** With data source I/O between calls, `searched_at` can precede `verdict.created_at` by several seconds, creating a potentially confusing audit trail for a legal proof system. Not a correctness bug, but worth standardizing.
- **Fix:** Capture a single `now` at method entry and a single `checkedAt` after I/O. Pass `checkedAt` consistently to all downstream uses instead of calling `now()` a third time.

### 4. Duplicate onMounted hooks with navigation race

- **File:** `frontend/app/pages/index.vue`, lines 8-14 and 18-29
- **Issue:** Two separate `onMounted` async hooks run concurrently. The first may call `navigateTo('/dashboard')` while the second is still performing a health check `$fetch`. If navigation triggers, the health check response arrives for an unmounting component, potentially briefly flashing a `serviceUnavailable` warning.
- **Impact:** Not a crash, but wasteful and creates a minor UX race. 
- **Fix:** Combine both hooks into a single `onMounted`. After `navigateTo`, return early to skip the health check.

### 5. Stale package-info documentation

- **File:** `backend/src/main/java/hu/riskguard/testing/package-info.java`
- **Issue:** Documentation states beans use `@Profile("test")`, but `TestAuthController.java` (line 44) and `TestSecurityConfig.java` (line 19) both use `@Profile({"test", "e2e"})`.
- **Fix:** Update `package-info.java` to document both profiles: `@Profile({"test", "e2e"})`.

---

## LOW Severity

### 6. Unused import

- **File:** `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java`, line 27
- **Issue:** `import java.util.HashMap;` is unused.
- **Fix:** Remove the import.

### 7. Redundant profile activation in CI

- **File:** `.github/workflows/ci.yml`, lines ~180-195
- **Issue:** `spring.profiles.active=test` is set via both `-Dspring.profiles.active=test` (JVM arg to Gradle) and `SPRING_PROFILES_ACTIVE=test` (environment variable). The `-D` flag may not be forwarded to the Spring Boot application JVM by Gradle's `bootRun` task; the env var is the reliable mechanism.
- **Fix:** Remove the `-Dspring.profiles.active=test` flag and rely solely on the `SPRING_PROFILES_ACTIVE` environment variable.
