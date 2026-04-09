# Story 8.5: PLATFORM_ADMIN Role & Admin Re-Gating

Status: review

## Story

As a RiskGuard platform operator,
I want platform-level admin features (EPR config, GDPR audit, adapter quarantine) restricted to the PLATFORM_ADMIN role,
so that SME company owners and accountants cannot accidentally modify system-wide configuration or view cross-tenant data.

## Acceptance Criteria

1. **PLATFORM_ADMIN role accepted in backend** — No DB migration needed (`users.role` is VARCHAR(50)). The value `PLATFORM_ADMIN` is simply used as a role string in the JWT `role` claim. Add a `PLATFORM_ADMIN` test user to dev seed data or test fixtures.

2. **EPR Config endpoints restricted to PLATFORM_ADMIN** — `EprAdminController` (3 methods: `getConfig`, `validate`, `publish`) reject SME_ADMIN and ACCOUNTANT with 403. Only PLATFORM_ADMIN passes. File: `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java` (lines 41, 54, 67 call `requireAdminRole`; lines 72-77 define it).

3. **GDPR Audit Search restricted to PLATFORM_ADMIN** — `AuditAdminController.getAuditLog()` rejects SME_ADMIN and ACCOUNTANT with 403. Only PLATFORM_ADMIN passes. File: `backend/src/main/java/hu/riskguard/screening/api/AuditAdminController.java` (line 46 calls `requireAdminRole`; lines 57-61 define it).

4. **Data Sources quarantine restricted to PLATFORM_ADMIN** — `DataSourceAdminController.quarantine()` rejects SME_ADMIN and ACCOUNTANT with 403. Only PLATFORM_ADMIN passes. Note: Story 8.4 already widens `getHealth()`, `saveCredentials()`, `deleteCredentials()` to accept ACCOUNTANT. This story additionally makes those 3 methods accept PLATFORM_ADMIN too (add to the role check). Keep quarantine PLATFORM_ADMIN-only.

5. **Frontend: EPR Config page restricted to PLATFORM_ADMIN** — `pages/admin/epr-config.vue` (line 24) guard changed from `SME_ADMIN` to `PLATFORM_ADMIN`.

6. **Frontend: Audit Search page restricted to PLATFORM_ADMIN** — `pages/admin/audit-search.vue` (line 30) guard changed from `SME_ADMIN` to `PLATFORM_ADMIN`.

7. **Frontend: Data Sources page accepts PLATFORM_ADMIN** — `pages/admin/datasources.vue` guard (already accepts SME_ADMIN + ACCOUNTANT from Story 8.4) also accepts PLATFORM_ADMIN. PLATFORM_ADMIN sees the quarantine controls; SME_ADMIN/ACCOUNTANT do not see quarantine section (only credentials + health).

8. **Frontend: Admin hub page role-based cards** — `pages/admin/index.vue` shows different admin feature cards based on role:
   - PLATFORM_ADMIN: All admin features (data sources, EPR config, GDPR audit)
   - SME_ADMIN / ACCOUNTANT: Only data sources (credential management)
   - Add a role guard to the page itself: redirect GUEST to dashboard.

9. **Frontend: Admin sidebar for all admin roles** — Both `AppSidebar.vue` (line 131) and `AppMobileDrawer.vue` (line 112) `isAdmin` computed should check for all admin-capable roles: `['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(role.value)`. This also FIXES the existing bug where `AppMobileDrawer.vue:112` checks `'ADMIN'` instead of `'SME_ADMIN'` — mobile users currently never see admin nav.

10. **Backend tests updated** — Each re-gated controller needs: (a) PLATFORM_ADMIN success test, (b) SME_ADMIN 403 test for platform-only endpoints, (c) GUEST 403 test. Update existing tests that assumed SME_ADMIN could access everything.

11. **Frontend tests updated** — Update sidebar/drawer specs for multi-role admin check. Update any admin page specs that test role guards.

12. **All existing tests green** — `./gradlew test` BUILD SUCCESSFUL. Frontend `npm run test` passes. No regressions.

## Tasks / Subtasks

- [x] Task 1: Backend — Create shared role check helpers (AC: 2, 3, 4)
  - [x] 1.1 In each of the 3 admin controllers, add `requirePlatformAdminRole(Jwt)` that only accepts PLATFORM_ADMIN
  - [x] 1.2 In `DataSourceAdminController`, update `requireAdminOrAccountantRole` (from Story 8.4) to also accept PLATFORM_ADMIN — rename to `requireAnyAdminRole` for clarity
  - [x] 1.3 Change `EprAdminController.requireAdminRole()` calls to `requirePlatformAdminRole()`
  - [x] 1.4 Change `AuditAdminController.requireAdminRole()` to `requirePlatformAdminRole()`
  - [x] 1.5 Change `DataSourceAdminController.quarantine()` to use `requirePlatformAdminRole()`
  - [x] 1.6 Ensure `getHealth()`, `saveCredentials()`, `deleteCredentials()` use `requireAnyAdminRole()` (SME_ADMIN + ACCOUNTANT + PLATFORM_ADMIN)

- [x] Task 2: Backend tests (AC: 10)
  - [x] 2.1 `EprAdminControllerTest`: add `platformAdmin_getConfig_returns200`, `smeAdmin_getConfig_returns403`, `accountant_getConfig_returns403`
  - [x] 2.2 `AuditAdminControllerTest`: add `platformAdmin_getAuditLog_returns200`, `smeAdmin_getAuditLog_returns403`
  - [x] 2.3 `DataSourceAdminControllerTest`: add `platformAdmin_quarantine_returns200`, update existing tests to reflect new role gates, add `platformAdmin_saveCredentials_returns200`
  - [x] 2.4 Add PLATFORM_ADMIN to test JWT builders where needed

- [x] Task 3: Frontend — Re-gate admin pages (AC: 5, 6, 7, 8)
  - [x] 3.1 `epr-config.vue`: change guard to `role !== 'PLATFORM_ADMIN'`
  - [x] 3.2 `audit-search.vue`: change guard to `role !== 'PLATFORM_ADMIN'`
  - [x] 3.3 `datasources.vue`: add PLATFORM_ADMIN to the role guard (alongside SME_ADMIN + ACCOUNTANT from 8.4)
  - [x] 3.4 `admin/index.vue`: add role guard (redirect GUEST), show role-based feature cards
  - [x] 3.5 `datasources.vue`: conditionally hide quarantine section from non-PLATFORM_ADMIN users (pass role info to health dashboard component or use v-if)

- [x] Task 4: Frontend — Fix sidebar + mobile drawer (AC: 9)
  - [x] 4.1 `AppSidebar.vue:131`: change `isAdmin` to `['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(role.value)`
  - [x] 4.2 `AppMobileDrawer.vue:112`: fix `'ADMIN'` to same multi-role check (this fixes the existing bug)

- [x] Task 5: Frontend tests (AC: 11)
  - [x] 5.1 Update `AppSidebar.spec.ts` role-gating tests for multi-role
  - [x] 5.2 Add or update `AppMobileDrawer` tests for admin visibility
  - [x] 5.3 Update admin page specs if they test role guards

- [x] Task 6: Seed data (AC: 1)
  - [x] 6.1 Add a PLATFORM_ADMIN user to test fixtures or dev seed migration
  - [x] 6.2 Ensure auth store / mock fixtures support PLATFORM_ADMIN role

- [x] Task 7: Verify full test suite (AC: 12)
  - [x] 7.1 `./gradlew test` — BUILD SUCCESSFUL (757 tests)
  - [x] 7.2 `cd frontend && npm run test` — all tests pass (718 tests)
  - [x] 7.3 No regressions in existing admin, EPR, screening, or datasource flows

## Dev Notes

### Previous Story Intelligence (8.4)
- Story 8.4 widened `DataSourceAdminController` to accept ACCOUNTANT on credential + health endpoints. This story builds on that: add PLATFORM_ADMIN to those checks AND restrict quarantine to PLATFORM_ADMIN-only.
- PrimeVue `useToast` in Vitest: Must use `vi.mock('primevue/usetoast', ...)`.
- Health store mock reactivity: Use JS getter pattern.
- Test baseline: 710 frontend + BUILD SUCCESSFUL backend.
- Commit convention: `feat(8.5): PLATFORM_ADMIN role & admin re-gating`

### Key Files to Touch

| File | Change |
|------|--------|
| `backend/.../datasource/api/DataSourceAdminController.java` | Add PLATFORM_ADMIN to role checks, quarantine becomes PLATFORM_ADMIN-only |
| `backend/.../epr/api/EprAdminController.java` | Change requireAdminRole to requirePlatformAdminRole |
| `backend/.../screening/api/AuditAdminController.java` | Change requireAdminRole to requirePlatformAdminRole |
| `backend/.../datasource/DataSourceAdminControllerTest.java` | Add PLATFORM_ADMIN tests, update existing role tests |
| `backend/.../epr/EprAdminControllerTest.java` | Add PLATFORM_ADMIN + SME_ADMIN 403 tests |
| `backend/.../screening/AuditAdminControllerTest.java` | Add PLATFORM_ADMIN + SME_ADMIN 403 tests |
| `frontend/app/pages/admin/epr-config.vue` | Guard: PLATFORM_ADMIN only |
| `frontend/app/pages/admin/audit-search.vue` | Guard: PLATFORM_ADMIN only |
| `frontend/app/pages/admin/datasources.vue` | Add PLATFORM_ADMIN to guard; hide quarantine section for non-platform admins |
| `frontend/app/pages/admin/index.vue` | Role guard + role-based feature cards |
| `frontend/app/components/Common/AppSidebar.vue` | Multi-role isAdmin check |
| `frontend/app/components/Common/AppMobileDrawer.vue` | Fix 'ADMIN' bug + multi-role check |

### Architecture Compliance
- No DB migration needed — `users.role` is VARCHAR(50), not a DB ENUM
- JWT `role` claim is already a string — PLATFORM_ADMIN works as-is
- No cross-module imports added
- Quarantine isolation: PLATFORM_ADMIN-only — infrastructure-level control
- EPR config isolation: PLATFORM_ADMIN-only — system-wide fee tables
- GDPR audit isolation: PLATFORM_ADMIN-only — cross-tenant data

### Existing Patterns to Reuse

| Pattern | Where |
|---------|-------|
| `requireAdminRole()` | All 3 admin controllers — copy and specialize |
| `buildJwtWithRole(String)` | All admin controller tests — already exists |
| `authStore.role` frontend check | All admin pages — same pattern |
| `AppSidebar.spec.ts` role tests | Epic 6 bugfix added these |

### Deferred / Out of Scope
- URL-pattern-based role restrictions in SecurityConfig.java — not needed, controller-level checks are sufficient
- Role hierarchy (PLATFORM_ADMIN inherits SME_ADMIN) — not implementing; each endpoint explicitly lists allowed roles
- PLATFORM_ADMIN user management UI — future story
- Architecture doc / PRD / epics.md updates for new role — SM handles separately

### References
- [Source: sprint-change-proposal-2026-04-08.md] — CP-4 approval
- [Source: DataSourceAdminController.java:253] — requireAdminRole to widen
- [Source: EprAdminController.java:72-77] — requireAdminRole to restrict
- [Source: AuditAdminController.java:57-61] — requireAdminRole to restrict
- [Source: AppMobileDrawer.vue:112] — bug: 'ADMIN' instead of 'SME_ADMIN'
- [Source: AppSidebar.vue:131] — isAdmin computed to widen
- [Source: architecture.md:1225] — users table role definition

## Dev Agent Record

### Completion Notes

Story 8.5 implemented 2026-04-09 by Claude Sonnet 4.6.

**Backend changes:**
- `EprAdminController`: `requireAdminRole()` → `requirePlatformAdminRole()` accepting only PLATFORM_ADMIN. SME_ADMIN and ACCOUNTANT now get 403 on all 3 endpoints (getConfig, validate, publish).
- `AuditAdminController`: same pattern — `requirePlatformAdminRole()` only.
- `DataSourceAdminController`: `requireAdminRole()` → `requirePlatformAdminRole()` for quarantine; `requireAdminOrAccountantRole()` renamed to `requireAnyAdminRole()` and extended to also accept PLATFORM_ADMIN for health/credentials endpoints.

**Backend tests:**
- `EprAdminControllerTest`: renamed all `smeAdmin` success tests to `platformAdmin`; added `smeAdmin_*_returns403` and `accountant_*_returns403` for all 3 endpoints.
- `AuditAdminControllerTest`: same — `smeAdmin_getAuditLog_returns403` added; all success tests use PLATFORM_ADMIN JWT.
- `DataSourceAdminControllerTest`: quarantine tests renamed from `smeAdmin` to `platformAdmin`; `quarantineAdapter_smeAdmin_returns403` added; `getHealth/saveCredentials/deleteCredentials_platformAdmin_returns200` added.

**Frontend changes:**
- `epr-config.vue`, `audit-search.vue`: guard changed from `SME_ADMIN` to `PLATFORM_ADMIN`.
- `datasources.vue`: guard widened to include PLATFORM_ADMIN; `can-quarantine` prop now `role === 'PLATFORM_ADMIN'`.
- `admin/index.vue`: full rewrite — added role guard (GUEST → redirect `/dashboard`); role-based cards: data sources visible to all 3 admin roles, EPR config and GDPR audit visible to PLATFORM_ADMIN only.
- `AppSidebar.vue`: `isAdmin` changed to multi-role includes check.
- `AppMobileDrawer.vue`: fixed long-standing bug — `'ADMIN'` changed to multi-role includes check.

**Frontend tests:**
- `AppSidebar.spec.ts`: admin role gating tests updated to multi-role function; removed unused `ref` import.
- `AppMobileDrawer.spec.ts`: tests rewritten to test correct multi-role logic (was testing the buggy `'ADMIN'` check).
- `epr-config.spec.ts`, `audit-search.spec.ts`: `mockUserRole` changed from `'SME_ADMIN'` to `'PLATFORM_ADMIN'`; redirect tests updated.

**Seed data:**
- `R__e2e_test_data.sql`: added `e2e-platform-admin@riskguard.hu` (UUID `a000-000000000003`), PLATFORM_ADMIN role.
- `R__demo_data.sql`: added `platform-admin@riskguard.hu` (UUID `b000-000000000009`), PLATFORM_ADMIN role, password `Admin1234!`.

**Bonus fix — notebook crash prevention:**
- Created `backend/gradle.properties` with `org.gradle.jvmargs=-Xmx2g` to cap Gradle daemon heap. Without this, Spring Boot AOT compilation inside the daemon JVM had no memory cap. Combined with 11 stale daemons from prior sessions, this caused system OOM and hard freezes.

**Test results:** 757 backend + 718 frontend tests, all green. BUILD SUCCESSFUL.

### File List

- `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java`
- `backend/src/main/java/hu/riskguard/screening/api/AuditAdminController.java`
- `backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java`
- `backend/src/test/java/hu/riskguard/epr/EprAdminControllerTest.java`
- `backend/src/test/java/hu/riskguard/screening/api/AuditAdminControllerTest.java`
- `backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java`
- `backend/src/main/resources/db/test-seed/R__e2e_test_data.sql`
- `backend/src/main/resources/db/test-seed/R__demo_data.sql`
- `backend/gradle.properties`
- `frontend/app/pages/admin/epr-config.vue`
- `frontend/app/pages/admin/audit-search.vue`
- `frontend/app/pages/admin/datasources.vue`
- `frontend/app/pages/admin/index.vue`
- `frontend/app/components/Common/AppSidebar.vue`
- `frontend/app/components/Common/AppMobileDrawer.vue`
- `frontend/app/components/Common/AppSidebar.spec.ts`
- `frontend/app/components/Common/AppMobileDrawer.spec.ts`
- `frontend/app/pages/admin/epr-config.spec.ts`
- `frontend/app/pages/admin/audit-search.spec.ts`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Review Findings

**Code Review Round 1 (2026-04-09):**
- [x] [Review][Patch] P1: `requireAnyAdminRole` stale error message — says "Admin or Accountant access required" but PLATFORM_ADMIN is also accepted [`DataSourceAdminController.java` `requireAnyAdminRole`]
- [x] [Review][Patch] P2: epr-config.vue and audit-search.vue redirect to `/` for non-PLATFORM_ADMIN; should redirect to `/dashboard` to match datasources.vue and admin/index.vue [`epr-config.vue:onMounted`, `audit-search.vue:onMounted`]
- [x] [Review][Patch] P3: `AuditAdminControllerTest.getAuditLog_nonAdmin_returns403` builds JWT with role `PRO_EPR` instead of `GUEST` — AC10 requires a GUEST 403 test [`AuditAdminControllerTest.java` `buildNonAdminJwt`]
- [x] [Review][Defer] D1: PLATFORM_ADMIN placed in demo SME tenant (`b000-000000000001`) — cross-tenant authorization model undefined; demo-mode acceptable for now [`R__demo_data.sql`] — deferred, architectural, out of scope
- [x] [Review][Defer] D2: `DataSourceHealthDashboard` defaults `canQuarantine: true` — insecure opt-out; parent always passes explicit prop correctly but default is wrong [`DataSourceHealthDashboard.vue`] — deferred, pre-existing from Story 8.4
- [x] [Review][Defer] D3: PLATFORM_ADMIN has no post-login landing page — global middleware sends to SME dashboard — deferred, pre-existing, not in scope
- [x] [Review][Defer] D4: `admin/index.vue` has no test file — new `onMounted` redirect and `v-if="isPlatformAdmin"` card logic are entirely untested — deferred, AC11 only required updating existing specs
- [x] [Review][Defer] D5: `requirePlatformAdminRole` / `requireAnyAdminRole` duplicated identically across 3 controllers — no shared utility; latent consistency risk for future role additions — deferred, pre-existing pattern
- [x] [Review][Defer] D6: `AppSidebar` shows PLATFORM_ADMIN SME-oriented nav items (Watchlist, Screening, EPR) — pre-existing nav structure, irrelevant to platform operator role — deferred, UX polish scope

**Code Review Round 2 (2026-04-10):**
- [x] [Review][Patch] R2-P1: `EprAdminControllerTest` missing GUEST 403 tests — AC10(c) requires a GUEST 403 test per re-gated controller; `AuditAdminControllerTest` was fixed to use GUEST (P3 in R1) but `EprAdminControllerTest` only had `smeAdmin_*` and `accountant_*` 403 tests; added `guest_getConfig_returns403`, `guest_validate_returns403`, `guest_publish_returns403` + `buildGuestJwt()` helper [`EprAdminControllerTest.java`]

### Change Log

- feat(8.5): PLATFORM_ADMIN role & admin re-gating — EPR config, GDPR audit, quarantine restricted to PLATFORM_ADMIN; health/credentials widened to SME_ADMIN+ACCOUNTANT+PLATFORM_ADMIN; admin hub shows role-based feature cards; mobile drawer admin bug fixed; Gradle daemon heap capped (2026-04-09)
- fix(8.5): review patch items resolved — P1: requireAnyAdminRole error message updated to "Admin access required"; P2: epr-config.vue and audit-search.vue redirect to /dashboard (not /); P3: AuditAdminControllerTest buildNonAdminJwt uses GUEST role; spec redirect assertions updated (2026-04-09)
- fix(8.5): R2 review — added GUEST 403 tests to EprAdminControllerTest (3 new: guest_getConfig/validate/publish_returns403); 760 backend + 718 frontend + 5 e2e all green (2026-04-10)
