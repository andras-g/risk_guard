# Sprint Change Proposal — CP-4: PLATFORM_ADMIN Role Separation

**Date:** 2026-04-08
**Trigger:** Manual testing of Epic 8 admin features
**Proposed by:** Andras (product owner) + Bob (SM)
**Scope:** Moderate — backlog reorganization within Epic 8

---

## 1. Issue Summary

During manual testing of Epic 8 (NAV credential management), it became clear that the current role model conflates two distinct admin personas:

- **SME_ADMIN** (company owner / SME user) — manages their own company's data: NAV credentials, watchlist, EPR filings
- **Platform operator** — manages system-wide configuration: EPR fee tables, circuit breakers, cross-tenant GDPR audit

Today, `SME_ADMIN` is the only admin role. It has unrestricted access to all admin pages, including platform-level operations that an SME owner should never touch (and could cause system-wide damage if misconfigured).

**Evidence:**
- EPR Config Manager (Story 6.3): Monaco editor for fee table JSON — affects all tenants' EPR calculations
- GDPR Audit Search (Story 6.4): Cross-tenant audit viewer — exposes other tenants' search history
- Data Sources quarantine (Story 6.2): Force-opens circuit breakers — infrastructure-level control

**Bonus bug discovered:** `AppMobileDrawer.vue:112` checks `userRole.value === 'ADMIN'` instead of `'SME_ADMIN'` — mobile users never see admin nav. This was missed in the Epic 6 bugfix that only fixed `AppSidebar.vue`.

## 2. Impact Analysis

### Epic Impact

**Epic 8 (current):** Add 2 new stories (8.5 + bugfix). Epic 8 scope grows but remains coherent — all stories relate to admin access and data integration.

**Epics 1-7 (completed):** No rollback needed. The admin features built in Epics 1, 3, 6, 7 continue to work — we're just re-gating who can access them.

**Future epics:** Any future admin features must use the new role model. Architecture docs need updating.

### Artifact Conflicts

| Artifact | Impact | Action Needed |
|----------|--------|---------------|
| **PRD** (prd.md) | Roles section lists GUEST/SME_ADMIN/ACCOUNTANT — no platform admin | Add PLATFORM_ADMIN to role definitions |
| **Architecture** (architecture.md) | Users table ENUM, ADR-5 JWT claims, role checks | Update users ENUM, JWT role claim docs |
| **Epics** (epics.md) | Epic 6 stories say "Admin" without specifying which admin type | Clarify: Stories 6.1-6.4 require PLATFORM_ADMIN |
| **DB Schema** | `users.role` VARCHAR(50) — no ENUM constraint in migration | No migration needed — VARCHAR accepts new value |
| **SecurityConfig.java** | No URL-pattern role restrictions — all done in controllers | No change needed |
| **project-context.md** | No role hierarchy documented | Update after implementation |

### Admin Feature Re-Gating Matrix

| Feature | Current Gate | New Gate | Rationale |
|---------|-------------|----------|-----------|
| **NAV Credential Mgmt** | SME_ADMIN | SME_ADMIN + ACCOUNTANT | Story 8.4 (already created) |
| **Data Sources health view** | SME_ADMIN | SME_ADMIN + ACCOUNTANT + PLATFORM_ADMIN | Everyone with admin access needs health visibility |
| **Data Sources quarantine** | SME_ADMIN | PLATFORM_ADMIN only | Infrastructure-level circuit breaker control |
| **EPR Config Manager** | SME_ADMIN | PLATFORM_ADMIN only | System-wide fee table config |
| **GDPR Audit Search** | SME_ADMIN | PLATFORM_ADMIN only | Cross-tenant data exposure |
| **Admin sidebar link** | SME_ADMIN | SME_ADMIN + ACCOUNTANT + PLATFORM_ADMIN | All admin-capable roles |

### Technical Scope Assessment

- **DB migration:** NOT needed — `users.role` is `VARCHAR(50)`, not a DB ENUM. Just insert `'PLATFORM_ADMIN'` as a role value.
- **JWT changes:** None — `role` claim already carries a string. `PLATFORM_ADMIN` works as-is.
- **Backend:** 3 controllers with `requireAdminRole()` need re-gating. New helper methods.
- **Frontend:** 3 admin pages + sidebar + mobile drawer need role guard updates.
- **Seed data:** Need at least one PLATFORM_ADMIN user in dev/test seed data.

## 3. Recommended Approach

**Option 1: Direct Adjustment (RECOMMENDED)**

Add 2 stories to Epic 8. No rollback, no MVP change. The admin features already work — we're just tightening who can access them.

- **Effort:** Medium (2 stories, ~1 day each)
- **Risk:** Low — additive changes, no breaking modifications to existing features
- **Timeline impact:** +2 stories to Epic 8, delays Epic 8 close by ~2 days

**Option 2: Rollback** — Not viable. Nothing to roll back. The admin features are correct; only the access gates are too loose.

**Option 3: MVP Review** — Not needed. This doesn't change MVP scope — it refines access control within existing features.

### Proposed Story Sequence

1. **Story 8.4** (already created, ready-for-dev): Accountant NAV credential access + demo mode
2. **Story 8.5** (NEW): Introduce `PLATFORM_ADMIN` role and re-gate admin features
3. **Story 8.6** (NEW): Bugfix — `AppMobileDrawer.vue` admin role check + admin sidebar for all admin roles

Stories 8.5 and 8.6 could be a single story, but separating them keeps the role infrastructure change (8.5) clean from the UI bugfix (8.6). Alternatively, 8.6 can be folded into 8.5 as a task — your call.

## 4. Detailed Change Proposals

### CP-4a: Story 8.5 — PLATFORM_ADMIN Role & Admin Re-Gating

**Scope:** Backend role infrastructure + frontend page access + seed data

**Backend changes:**

| Controller | Method | Current | New |
|-----------|--------|---------|-----|
| `DataSourceAdminController` | `getHealth()` | `requireAdminRole` (SME_ADMIN) | `requireAnyAdminRole` (SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN) |
| `DataSourceAdminController` | `quarantine()` | `requireAdminRole` (SME_ADMIN) | `requirePlatformAdminRole` (PLATFORM_ADMIN only) |
| `DataSourceAdminController` | `saveCredentials()` | Per Story 8.4: SME_ADMIN + ACCOUNTANT | No further change |
| `DataSourceAdminController` | `deleteCredentials()` | Per Story 8.4: SME_ADMIN + ACCOUNTANT | No further change |
| `EprAdminController` | All 3 methods | `requireAdminRole` (SME_ADMIN) | `requirePlatformAdminRole` (PLATFORM_ADMIN only) |
| `AuditAdminController` | `getAuditLog()` | `requireAdminRole` (SME_ADMIN) | `requirePlatformAdminRole` (PLATFORM_ADMIN only) |

**Suggested shared utility:** Extract role-check methods to a shared utility or base class (e.g., `AdminRoleUtil`) since 3 controllers currently each have their own private `requireAdminRole()`. Or keep them private per controller — dev agent's call based on project patterns.

**Frontend changes:**

| Page | Current Guard | New Guard |
|------|--------------|-----------|
| `datasources.vue` | Per Story 8.4: SME_ADMIN + ACCOUNTANT | Add PLATFORM_ADMIN too |
| `epr-config.vue` | `role !== 'SME_ADMIN'` | `role !== 'PLATFORM_ADMIN'` |
| `audit-search.vue` | `role !== 'SME_ADMIN'` | `role !== 'PLATFORM_ADMIN'` |
| `admin/index.vue` (hub) | No guard | Add guard: allow SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN |

**Admin hub page differentiation:** The admin index page should show different cards/links based on role:
- `PLATFORM_ADMIN`: All admin features (data sources, EPR config, GDPR audit)
- `SME_ADMIN` / `ACCOUNTANT`: Only data sources (credential management)

**Seed data:**
- Add a `PLATFORM_ADMIN` user to dev seed migration or test fixtures
- Existing test JWT builders need a `PLATFORM_ADMIN` variant

**Tests:**
- Backend: Update existing 403 tests, add PLATFORM_ADMIN success tests, add SME_ADMIN 403 tests for platform-only endpoints
- Frontend: Test role-based page guards and admin hub card visibility

---

### CP-4b: Story 8.6 (or Task in 8.5) — Mobile Drawer Bugfix + Admin Sidebar for All Admin Roles

**Bug:** `AppMobileDrawer.vue:112` — `userRole.value === 'ADMIN'` should be `userRole.value === 'SME_ADMIN'`

**Enhancement:** Both `AppSidebar.vue` and `AppMobileDrawer.vue` `isAdmin` computed should check for all admin-capable roles:
```ts
const isAdmin = computed(() => 
  ['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(role.value)
)
```

**Tests:** Update `AppSidebar.spec.ts` role-gating tests (the bugfix in epic-6-bugfixes added these). Add mobile drawer test if missing.

---

### CP-4c: Architecture Doc Updates (Non-Code)

**architecture.md changes:**

Section: Users table schema (line 1225)
```
OLD: role (ENUM: GUEST/SME_ADMIN/ACCOUNTANT)
NEW: role (ENUM: GUEST/SME_ADMIN/ACCOUNTANT/PLATFORM_ADMIN)
```

Add note to ADR-5:
```
PLATFORM_ADMIN role has access to system-wide configuration endpoints 
(EPR config, GDPR audit, adapter quarantine). SME_ADMIN and ACCOUNTANT 
have access to tenant-scoped admin features (NAV credentials, health view).
```

**epics.md changes:**

Epic 6 stories 6.1-6.4: Add note that these features require PLATFORM_ADMIN access (post CP-4).

**PRD changes:**

Access Control section: Add PLATFORM_ADMIN to role list with description:
```
PLATFORM_ADMIN: System-wide configuration — EPR fee tables, cross-tenant audit, 
infrastructure controls. Not tenant-scoped.
```

## 5. Implementation Handoff

**Scope classification:** Moderate — backlog reorganization within current epic.

**Approved:** 2026-04-08 — Stories 8.5 (role separation + mobile bugfix, merged) and 8.6 (invoice traceability in EPR auto-fill) added to Epic 8.

**Sequence:**
1. **SM (Bob):** Create Stories 8.5 and 8.6 via `bmad-create-story`
2. **Dev agent:** Implement Story 8.4 first (no dependency on 8.5)
3. **Dev agent:** Implement Story 8.5 (PLATFORM_ADMIN role + re-gating + mobile drawer bugfix)
4. **Dev agent:** Implement Story 8.6 (invoice-to-material traceability in EPR auto-fill)
5. **SM (Bob):** Update architecture.md, epics.md, PRD role sections (non-code doc updates)
6. **QA:** Manual test all admin pages with each role (GUEST, SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN)

**Success criteria:**
- SME_ADMIN can access: Data Sources (credential management + health view only)
- ACCOUNTANT (context-switched) can access: Data Sources (credential management + health view only)
- PLATFORM_ADMIN can access: All admin features (data sources full, EPR config, GDPR audit)
- GUEST cannot access any admin features
- Mobile drawer shows admin link for all admin-capable roles
- All existing tests pass + new role-specific tests added

## 6. Checklist Status

| ID | Item | Status |
|----|------|--------|
| 1.1 | Triggering story identified | [x] Done — Story 8.4 (NAV credential access) revealed role gap |
| 1.2 | Core problem defined | [x] Done — Misunderstanding of original requirements (single admin role) |
| 1.3 | Evidence gathered | [x] Done — 3 platform-level features exposed to tenant admins + mobile drawer bug |
| 2.1 | Current epic impact | [x] Done — Epic 8 gains 1-2 stories |
| 2.2 | Epic-level changes | [x] Done — Modify Epic 8 scope, no new epic needed |
| 2.3 | Remaining epics reviewed | [x] Done — No impact on completed epics; future epics must use new role model |
| 2.4 | Future epic invalidation | [x] Done — None invalidated, none new needed |
| 2.5 | Epic ordering | [x] Done — No resequencing needed |
| 3.1 | PRD conflicts | [x] Done — Role list needs PLATFORM_ADMIN addition |
| 3.2 | Architecture conflicts | [x] Done — Users ENUM + ADR-5 need update |
| 3.3 | UI/UX conflicts | [x] Done — Admin hub page needs role-based card visibility |
| 3.4 | Other artifacts | [x] Done — project-context.md update after implementation |
| 4.1 | Direct Adjustment viable | [x] Viable — Recommended |
| 4.2 | Rollback viable | [x] Not viable — Nothing to roll back |
| 4.3 | MVP Review viable | [x] Not viable — MVP unchanged |
| 4.4 | Path selected | [x] Done — Option 1: Direct Adjustment |
| 5.1-5.5 | Proposal components | [x] Done |
