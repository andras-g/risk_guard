# Epic 6 — QA Bugfix Session (2026-04-01)

**Date:** 2026-04-01
**Scope:** Epic 6 manual QA pass — admin navigation visibility
**Test results after all fixes:** Frontend 30/30 AppSidebar tests passing

---

## Bug 1: Admin sidebar link never visible to SME_ADMIN users

**Symptom:** The left-panel sidebar never showed the "Admin" navigation item (cog icon → `/admin`) for logged-in `SME_ADMIN` users.

**Root cause:** `AppSidebar.vue` computed `isAdmin` used the string `'ADMIN'` instead of the actual role name `'SME_ADMIN'`:

```ts
// Before (wrong)
const isAdmin = computed(() => role.value === 'ADMIN')

// After (correct)
const isAdmin = computed(() => role.value === 'SME_ADMIN')
```

No user in the system ever has role `'ADMIN'` — the correct admin role is `'SME_ADMIN'` (matches all backend guards: `DataSourceAdminController`, `EprAdminController`, `AuditAdminController`, and the frontend page guards in `datasources.vue`, `epr-config.vue`, `audit-search.vue`).

The spec's "admin section role gating" describe block also had wrong tests — they tested an inline `=== 'ADMIN'` expression detached from the component, and one test was explicitly named *"admin section is hidden when user role is SME_ADMIN"* (inverted from the correct expectation).

**Files changed:**

| File | Change |
|------|--------|
| `frontend/app/components/Common/AppSidebar.vue` | `role.value === 'ADMIN'` → `role.value === 'SME_ADMIN'` |
| `frontend/app/components/Common/AppSidebar.spec.ts` | Fixed "admin section role gating" describe block: removed wrong `'ADMIN'` test, corrected remaining tests to assert `=== 'SME_ADMIN'` |
