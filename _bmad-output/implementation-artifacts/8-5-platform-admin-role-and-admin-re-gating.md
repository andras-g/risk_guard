# Story 8.5: PLATFORM_ADMIN Role & Admin Re-Gating

Status: ready-for-dev

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

- [ ] Task 1: Backend — Create shared role check helpers (AC: 2, 3, 4)
  - [ ] 1.1 In each of the 3 admin controllers, add `requirePlatformAdminRole(Jwt)` that only accepts PLATFORM_ADMIN
  - [ ] 1.2 In `DataSourceAdminController`, update `requireAdminOrAccountantRole` (from Story 8.4) to also accept PLATFORM_ADMIN — rename to `requireAnyAdminRole` for clarity
  - [ ] 1.3 Change `EprAdminController.requireAdminRole()` calls to `requirePlatformAdminRole()`
  - [ ] 1.4 Change `AuditAdminController.requireAdminRole()` to `requirePlatformAdminRole()`
  - [ ] 1.5 Change `DataSourceAdminController.quarantine()` to use `requirePlatformAdminRole()`
  - [ ] 1.6 Ensure `getHealth()`, `saveCredentials()`, `deleteCredentials()` use `requireAnyAdminRole()` (SME_ADMIN + ACCOUNTANT + PLATFORM_ADMIN)

- [ ] Task 2: Backend tests (AC: 10)
  - [ ] 2.1 `EprAdminControllerTest`: add `platformAdmin_getConfig_returns200`, `smeAdmin_getConfig_returns403`, `accountant_getConfig_returns403`
  - [ ] 2.2 `AuditAdminControllerTest`: add `platformAdmin_getAuditLog_returns200`, `smeAdmin_getAuditLog_returns403`
  - [ ] 2.3 `DataSourceAdminControllerTest`: add `platformAdmin_quarantine_returns200`, update existing tests to reflect new role gates, add `platformAdmin_saveCredentials_returns200`
  - [ ] 2.4 Add PLATFORM_ADMIN to test JWT builders where needed

- [ ] Task 3: Frontend — Re-gate admin pages (AC: 5, 6, 7, 8)
  - [ ] 3.1 `epr-config.vue`: change guard to `role !== 'PLATFORM_ADMIN'`
  - [ ] 3.2 `audit-search.vue`: change guard to `role !== 'PLATFORM_ADMIN'`
  - [ ] 3.3 `datasources.vue`: add PLATFORM_ADMIN to the role guard (alongside SME_ADMIN + ACCOUNTANT from 8.4)
  - [ ] 3.4 `admin/index.vue`: add role guard (redirect GUEST), show role-based feature cards
  - [ ] 3.5 `datasources.vue`: conditionally hide quarantine section from non-PLATFORM_ADMIN users (pass role info to health dashboard component or use v-if)

- [ ] Task 4: Frontend — Fix sidebar + mobile drawer (AC: 9)
  - [ ] 4.1 `AppSidebar.vue:131`: change `isAdmin` to `['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(role.value)`
  - [ ] 4.2 `AppMobileDrawer.vue:112`: fix `'ADMIN'` to same multi-role check (this fixes the existing bug)

- [ ] Task 5: Frontend tests (AC: 11)
  - [ ] 5.1 Update `AppSidebar.spec.ts` role-gating tests for multi-role
  - [ ] 5.2 Add or update `AppMobileDrawer` tests for admin visibility
  - [ ] 5.3 Update admin page specs if they test role guards

- [ ] Task 6: Seed data (AC: 1)
  - [ ] 6.1 Add a PLATFORM_ADMIN user to test fixtures or dev seed migration
  - [ ] 6.2 Ensure auth store / mock fixtures support PLATFORM_ADMIN role

- [ ] Task 7: Verify full test suite (AC: 12)
  - [ ] 7.1 `./gradlew test` — BUILD SUCCESSFUL
  - [ ] 7.2 `cd frontend && npm run test` — all tests pass
  - [ ] 7.3 No regressions in existing admin, EPR, screening, or datasource flows

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
