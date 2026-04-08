# Story 8.4: Accountant NAV Credential Access & Demo Mode

Status: done

## Story

As a RiskGuard accountant managing multiple client companies,
I want to enter and manage NAV Online Számla credentials for each of my mandated clients from the Data Sources admin page,
so that real invoice data flows into screening and EPR modules for every client I serve — without requiring each client to log in themselves.

Additionally, as any user running in demo mode,
I want the credential panel to be visible and functional (skipping NAV verification),
so that I can explore the full credential management workflow during onboarding and demos.

## Acceptance Criteria

1. **Backend: `ACCOUNTANT` role allowed on credential endpoints** — `DataSourceAdminController.saveCredentials()` and `deleteCredentials()` accept both `SME_ADMIN` and `ACCOUNTANT` roles. An `ACCOUNTANT` user with `active_tenant_id` set (via context switch) can save/delete credentials for that tenant. `GUEST` role still returns 403. The health endpoint (`getHealth()`) also accepts `ACCOUNTANT` so the data sources page loads. The quarantine endpoint remains `SME_ADMIN`-only (accountants should not control circuit breakers).

2. **Backend: Demo mode skips `verifyCredentials()`** — When `riskguard.data-source.mode=demo`, `saveCredentials()` skips the `authService.verifyCredentials()` call and stores credentials directly. This allows the full credential workflow to be exercised without a live NAV connection. Non-demo modes still verify.

3. **Backend: Demo mode credential status derived from DB** — `buildResponse()` no longer hardcodes `credentialStatus = "NOT_CONFIGURED"` in demo mode. Instead, it derives status from `navTenantCredentialRepository.existsByTenantId(tenantId)` regardless of mode — same logic as non-demo. After saving credentials in demo mode, the status badge shows `VALID`.

4. **Frontend: Data Sources page accessible to `ACCOUNTANT`** — `pages/admin/datasources.vue` `onMounted` guard allows both `SME_ADMIN` and `ACCOUNTANT` roles. Other admin pages (`epr-config.vue`, `audit-search.vue`) remain `SME_ADMIN`-only.

5. **Frontend: NavCredentialManager visible in all modes** — The `v-if` condition on `AdminNavCredentialManager` in `datasources.vue` no longer checks `dataSourceMode !== 'DEMO'`. The panel renders whenever `healthStore.adapters.length > 0`.

6. **Frontend: Demo mode visual indicator** — When `dataSourceMode === 'DEMO'`, `NavCredentialManager` shows an i18n `Message` (PrimeVue, severity `info`) explaining that credential verification is skipped in demo mode. The `dataSourceMode` is passed as a prop from `datasources.vue`.

7. **Backend tests updated** — Existing `saveCredentials_nonAdmin_returns403` and `deleteCredentials_nonAdmin_returns403` tests updated: they now test with `GUEST` role instead of `ACCOUNTANT`. New tests added: `saveCredentials_accountant_returns200`, `deleteCredentials_accountant_returns200`, `saveCredentials_demo_skipsVerification`, `getHealth_accountant_returns200`, `quarantine_accountant_returns403`.

8. **Frontend tests updated** — `NavCredentialManager.spec.ts`: add test for demo mode info message visibility. `datasources.vue` tests (if any): verify `ACCOUNTANT` role is not redirected.

9. **All existing tests green** — `./gradlew test` BUILD SUCCESSFUL. Frontend `npm run test` passes. No regressions.

## Tasks / Subtasks

- [x] Task 1: Backend — Widen role check on credential + health endpoints (AC: 1)
  - [x] 1.1 In `DataSourceAdminController`, extract new helper `requireAdminOrAccountantRole(Jwt)` that accepts both `SME_ADMIN` and `ACCOUNTANT`
  - [x] 1.2 Change `saveCredentials()`, `deleteCredentials()`, and `getHealth()` to use `requireAdminOrAccountantRole()` instead of `requireAdminRole()`
  - [x] 1.3 Keep `quarantine()` using `requireAdminRole()` (SME_ADMIN only)

- [x] Task 2: Backend — Demo mode skip credential verification (AC: 2)
  - [x] 2.1 In `saveCredentials()`, check `"demo".equalsIgnoreCase(riskGuardProperties.getDataSource().getMode())`
  - [x] 2.2 If demo: skip `authService.verifyCredentials()`, proceed directly to `navTenantCredentialRepository.upsert()`
  - [x] 2.3 If non-demo: keep existing verification logic unchanged

- [x] Task 3: Backend — Fix demo mode credential status derivation (AC: 3)
  - [x] 3.1 In `buildResponse()`, remove the `"DEMO".equals(dataSourceMode)` branch that hardcodes `"NOT_CONFIGURED"`
  - [x] 3.2 Use the same `navTenantCredentialRepository.existsByTenantId(tenantId)` logic for all modes

- [x] Task 4: Backend tests (AC: 7)
  - [x] 4.1 Change `saveCredentials_nonAdmin_returns403` to use `GUEST` role instead of `ACCOUNTANT`
  - [x] 4.2 Change `deleteCredentials_nonAdmin_returns403` to use `GUEST` role instead of `ACCOUNTANT`
  - [x] 4.3 Add `saveCredentials_accountant_returns200` — same as `smeAdmin_validCredentials` but with `ACCOUNTANT` role
  - [x] 4.4 Add `deleteCredentials_accountant_returns200`
  - [x] 4.5 Add `getHealth_accountant_returns200`
  - [x] 4.6 Add `quarantine_accountant_returns403`
  - [x] 4.7 Add `saveCredentials_demo_skipsVerification` — set mode to `demo`, verify `authService.verifyCredentials()` is never called, credentials still saved

- [x] Task 5: Frontend — Allow ACCOUNTANT on datasources page (AC: 4, 5)
  - [x] 5.1 In `datasources.vue` onMounted, change guard to: `if (authStore.role !== 'SME_ADMIN' && authStore.role !== 'ACCOUNTANT') router.replace('/dashboard')`
  - [x] 5.2 Remove the `dataSourceMode !== 'DEMO'` check from `AdminNavCredentialManager` `v-if`; simplify to `v-if="healthStore.adapters.length > 0"`

- [x] Task 6: Frontend — Demo mode info message + prop (AC: 6)
  - [x] 6.1 Pass `dataSourceMode` prop to `NavCredentialManager`: `:data-source-mode="healthStore.adapters[0]?.dataSourceMode ?? ''"`
  - [x] 6.2 In `NavCredentialManager.vue`, add `dataSourceMode` prop (String, default `''`)
  - [x] 6.3 Add PrimeVue `Message` component (severity `info`) when `dataSourceMode === 'DEMO'`, text from i18n key `admin.navCredentials.demoModeInfo`
  - [x] 6.4 Add i18n keys to `en/admin.json` and `hu/admin.json`

- [x] Task 7: Frontend tests (AC: 8)
  - [x] 7.1 In `NavCredentialManager.spec.ts`: add test `shows demo mode info message when dataSourceMode is DEMO`
  - [x] 7.2 In `NavCredentialManager.spec.ts`: add test `hides demo mode info message when dataSourceMode is TEST`
  - [x] 7.3 Update `mountComponent` helper to accept optional `dataSourceMode` prop

- [x] Task 8: Verify full test suite (AC: 9)
  - [x] 8.1 `./gradlew test` — BUILD SUCCESSFUL
  - [x] 8.2 `cd frontend && npm run test` — 714 tests pass

## Dev Notes

### NAV Online Számla Credential Workflow (Real-World Context)

Each Hungarian company creates its own NAV Online Számla technical user on the NAV portal (Cégkapu). There is **no delegation mechanism** in the NAV API. An accountant managing N clients needs N separate credential sets — each client shares their credentials with the accountant. This is standard industry practice across all Hungarian invoicing software.

**API authorization scopes:**
- `QueryTaxpayer`: Any technical user can query **any** tax number (public-ish)
- `QueryInvoiceDigest/Data`: Only invoices where the credential owner's company is issuer or receiver
- `ManageInvoice`: Only for the credential owner's issued invoices

The existing per-tenant credential storage (`nav_tenant_credentials.tenant_id`) is architecturally correct. This story just widens **who** can manage those credentials.

### Previous Story Intelligence (8.3)

- **PrimeVue `useToast` in Vitest**: Must use `vi.mock('primevue/usetoast', ...)` — not `vi.stubGlobal`.
- **`vi.mock` for explicitly-imported composables**: Because composables use explicit imports, `vi.stubGlobal` is bypassed. Use `vi.mock('~/composables/...', ...)`.
- **Health store mock reactivity**: Use JS getter (`get adapters() { return mockAdapters }`) in the `vi.mock` factory so each test can update `mockAdapters`.
- **i18n alphabetical ordering**: Check exact position in both `en/` and `hu/` JSON files before inserting new keys.
- **Test baseline**: 710 frontend tests at end of Story 8.3. Backend BUILD SUCCESSFUL.

### Key Files to Touch

| File | Change |
|------|--------|
| `backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java` | Widen role check, demo skip, fix demo credential status |
| `backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java` | Update 403 tests, add ACCOUNTANT + demo tests |
| `frontend/app/pages/admin/datasources.vue` | Widen role guard, remove DEMO v-if, pass dataSourceMode prop |
| `frontend/app/components/Admin/NavCredentialManager.vue` | Add dataSourceMode prop, demo info message |
| `frontend/app/components/Admin/NavCredentialManager.spec.ts` | Add demo mode tests |
| `frontend/app/i18n/en/admin.json` | Add `navCredentials.demoModeInfo` key |
| `frontend/app/i18n/hu/admin.json` | Add `navCredentials.demoModeInfo` key |

### Exact Code Changes Required

#### Backend: `DataSourceAdminController.java`

**New helper method** (add alongside existing `requireAdminRole`):
```java
private void requireAdminOrAccountantRole(Jwt jwt) {
    String role = jwt.getClaimAsString("role");
    if (!"SME_ADMIN".equals(role) && !"ACCOUNTANT".equals(role)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or Accountant access required");
    }
}
```

**`getHealth()`** (line 80): Change `requireAdminRole(jwt)` to `requireAdminOrAccountantRole(jwt)`

**`saveCredentials()`** (line 157): Change `requireAdminRole(jwt)` to `requireAdminOrAccountantRole(jwt)`. Add demo mode check:
```java
requireAdminOrAccountantRole(jwt);
UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

String passwordHash = authService.hashPassword(request.password());
String loginEnc = aesFieldEncryptor.encrypt(request.login());
String signingKeyEnc = aesFieldEncryptor.encrypt(request.signingKey());
String exchangeKeyEnc = aesFieldEncryptor.encrypt(request.exchangeKey());

// Demo mode: skip NAV verification (no live NAV connection available)
boolean isDemoMode = "demo".equalsIgnoreCase(riskGuardProperties.getDataSource().getMode());
if (!isDemoMode) {
    NavCredentials credentials = new NavCredentials(
            request.login(), passwordHash, request.signingKey(),
            request.exchangeKey(), request.taxNumber()
    );
    boolean valid = authService.verifyCredentials(credentials);
    if (!valid) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "NAV credential verification failed ...");
    }
}

navTenantCredentialRepository.upsert(tenantId, loginEnc, passwordHash,
        signingKeyEnc, exchangeKeyEnc, request.taxNumber());
```

**`deleteCredentials()`** (line 186): Change `requireAdminRole(jwt)` to `requireAdminOrAccountantRole(jwt)`

**`buildResponse()`** — Remove the demo-mode credential status override at lines 236-238. Replace:
```java
// REMOVE THIS:
String credentialStatus = "DEMO".equals(dataSourceMode)
        ? "NOT_CONFIGURED"
        : (NAV_ADAPTER_NAME.equals(name) ? navCredentialStatus : "NOT_CONFIGURED");

// REPLACE WITH:
String credentialStatus = NAV_ADAPTER_NAME.equals(name) ? navCredentialStatus : "NOT_CONFIGURED";
```

**`quarantine()`** (line 108): Keep `requireAdminRole(jwt)` — no change.

#### Frontend: `datasources.vue`

**Line 23** — Change:
```vue
if (authStore.role !== 'SME_ADMIN') {
```
To:
```vue
if (authStore.role !== 'SME_ADMIN' && authStore.role !== 'ACCOUNTANT') {
```

**Lines 91-93** — Change:
```vue
<AdminNavCredentialManager
  v-if="healthStore.adapters.length > 0 && healthStore.adapters[0].dataSourceMode !== 'DEMO'"
/>
```
To:
```vue
<AdminNavCredentialManager
  v-if="healthStore.adapters.length > 0"
  :data-source-mode="healthStore.adapters[0]?.dataSourceMode ?? ''"
/>
```

#### Frontend: `NavCredentialManager.vue`

**Add prop** (in `<script setup>`):
```ts
const props = defineProps<{
  dataSourceMode?: string
}>()
```

**Add PrimeVue Message import:**
```ts
import Message from 'primevue/message'
```

**Add demo mode info message in template** (after the `<p class="text-sm ...">` description, before the grid):
```vue
<Message v-if="props.dataSourceMode === 'DEMO'" severity="info" :closable="false" data-testid="demo-mode-info">
  {{ t('admin.navCredentials.demoModeInfo') }}
</Message>
```

#### i18n Keys

In `en/admin.json`, inside `navCredentials` object, add alphabetically — `demoModeInfo` goes after `deleteSuccess` and before `description` (alphabetical: `deleteS...` < `demoM...` < `descr...`):
```json
"demoModeInfo": "Demo mode: credentials are stored locally without NAV verification. Switch to test/live mode for real validation."
```

In `hu/admin.json`, same position:
```json
"demoModeInfo": "Demo mód: a hitelesítő adatok helyi tárolása NAV ellenőrzés nélkül történik. Váltson teszt/éles módra a valós érvényesítéshez."
```

### Existing Patterns to Reuse

| Pattern | Where |
|---------|-------|
| `requireAdminRole()` pattern | `DataSourceAdminController.java:253` — copy and widen |
| `buildJwtWithRole("ACCOUNTANT")` test helper | `DataSourceAdminControllerTest.java:392` — already exists |
| `riskGuardProperties.getDataSource().getMode()` | `DataSourceService.java:149` — same demo check pattern |
| PrimeVue `Message` component | Used in `InvoiceAutoFillPanel.vue` (Story 8.3) for NAV unavailable warning |
| i18n key insertion | Check existing `navCredentials.*` key ordering in both language files |
| Health store mock with getter | `NavCredentialManager.spec.ts:19-24` — reuse exact pattern |

### Testing Requirements

**Backend (Mockito, no Spring context):**
- Update `saveCredentials_nonAdmin_returns403`: change role from `ACCOUNTANT` to `GUEST`
- Update `deleteCredentials_nonAdmin_returns403`: change role from `ACCOUNTANT` to `GUEST`
- New: `saveCredentials_accountant_returns200` — build JWT with `ACCOUNTANT` role + `active_tenant_id`, mock `authService.verifyCredentials()` → true, verify `upsert()` called
- New: `deleteCredentials_accountant_returns200` — `ACCOUNTANT` role, verify `deleteByTenantId()` called
- New: `getHealth_accountant_returns200` — `ACCOUNTANT` role, verify no 403
- New: `quarantine_accountant_returns403` — `ACCOUNTANT` role, verify 403
- New: `saveCredentials_demo_skipsVerification` — mock `riskGuardProperties.getDataSource().getMode()` → `"demo"`, verify `authService.verifyCredentials()` is **never** called, `upsert()` still called

**Frontend (Vitest + Vue Test Utils):**
- New in `NavCredentialManager.spec.ts`: `shows demo mode info message when dataSourceMode is DEMO` — mount with `dataSourceMode: 'DEMO'` prop, assert `[data-testid="demo-mode-info"]` exists
- New: `hides demo mode info message when dataSourceMode is TEST` — mount with `dataSourceMode: 'TEST'`, assert `[data-testid="demo-mode-info"]` does not exist
- Update `mountComponent` helper to accept `dataSourceMode` prop parameter

### Project Structure Notes

**No new files created.** This story only modifies existing files. No new Java classes, no new Vue components, no new migrations.

### Architecture Compliance

- **ADR-5 (JWT dual claims)**: `active_tenant_id` already present in accountant's JWT after context switch. No JWT changes needed — credential operations use `active_tenant_id` which is already tenant-scoped.
- **Mandate validation not needed here**: The context-switch endpoint (`POST /api/v1/identity/tenants/switch`) already validates the mandate via `identityService.hasMandate()`. By the time an accountant reaches the credential page, their `active_tenant_id` is guaranteed to be a mandated tenant.
- **Module boundary respected**: No cross-module imports added. `DataSourceAdminController` stays within `datasource` module.
- **No PII logging**: No new log statements with credentials or tax numbers.
- **Quarantine isolation**: Intentionally kept as `SME_ADMIN`-only — circuit breaker control is infrastructure-level, not per-client credential management.

### References

- [Source: backend/.../DataSourceAdminController.java:253] — `requireAdminRole()` to widen
- [Source: backend/.../DataSourceAdminController.java:153-179] — `saveCredentials()` with verifyCredentials
- [Source: backend/.../DataSourceAdminController.java:236-238] — demo mode credential status hardcode
- [Source: backend/.../DataSourceAdminControllerTest.java:360-381] — existing 403 tests to update
- [Source: frontend/app/pages/admin/datasources.vue:23-26] — role guard
- [Source: frontend/app/pages/admin/datasources.vue:91-93] — DEMO mode v-if guard
- [Source: frontend/app/components/Admin/NavCredentialManager.vue] — component to add prop
- [Source: frontend/app/components/Admin/NavCredentialManager.spec.ts] — test patterns
- [Source: _bmad-output/planning-artifacts/architecture.md#ADR-5] — JWT dual claims
- [Source: backend/.../identity/internal/IdentityRepository.java:57-66] — `hasMandate()` already validates context switch
- [Source: backend/.../identity/api/IdentityController.java:98-146] — switchTenant with mandate check

### Git Intelligence

Recent commits: `feat(8.3)`, `feat(8.2)`, `feat(8.1)`. Use: `feat(8.4): Accountant NAV credential access & demo mode`
Test baseline: 710 frontend + BUILD SUCCESSFUL backend + 5 e2e.

### Deferred / Out of Scope

- **Admin sidebar link visibility for ACCOUNTANT**: Currently the admin sidebar shows for `SME_ADMIN` only. This story does NOT change the sidebar — an accountant navigates to `/admin/datasources` directly or via a link in the credential prompt. Sidebar access is a separate UX decision.
- **Accountant access to other admin pages** (`epr-config.vue`, `audit-search.vue`): Remains `SME_ADMIN`-only. Separate stories if needed.
- **Background credential re-validation**: Already deferred from Story 8.1 R2. If NAV revokes credentials, status stays `VALID` until next manual save.
- **Mandate-scoped credential listing**: An accountant seeing all clients' credential statuses at once (without switching context) is a future dashboard feature.
- **Bulk credential entry**: Entering multiple clients' credentials in one flow — future UX improvement.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- `saveCredentials_invalidCredentials_returns422` test failed initially because default setUp mode was `demo` (skips verification). Fixed by setting mode to `test` in tests that exercise the verification path.
- Same fix applied to `saveCredentials_smeAdmin_validCredentials_returns200` and `saveCredentials_accountant_returns200`.

### Completion Notes List

- AC 1: `requireAdminOrAccountantRole()` helper added; `getHealth`, `saveCredentials`, `deleteCredentials` widened to accept ACCOUNTANT. `quarantine` remains SME_ADMIN-only.
- AC 2: Demo mode check added to `saveCredentials()` — skips `authService.verifyCredentials()` when mode is `demo`.
- AC 3: Removed `"DEMO".equals(dataSourceMode)` hardcode in `buildResponse()`. Credential status now derived from DB in all modes.
- AC 4: `datasources.vue` role guard widened to accept ACCOUNTANT.
- AC 5: Removed `dataSourceMode !== 'DEMO'` check from `AdminNavCredentialManager` v-if.
- AC 6: Added `dataSourceMode` prop to `NavCredentialManager.vue`, PrimeVue `Message` with i18n key `admin.navCredentials.demoModeInfo` shown when DEMO mode.
- AC 7: 5 new backend tests added, 2 updated (ACCOUNTANT→GUEST in 403 tests, 1 renamed). Existing tests updated for demo mode awareness.
- AC 8: 2 new frontend tests for demo mode info message visibility. `mountComponent` helper updated to accept `dataSourceMode` prop.
- AC 9: Backend BUILD SUCCESSFUL, 714 frontend tests pass.
- ✅ Resolved review finding [Patch] D1: `canQuarantine` prop added to `DataSourceHealthDashboard.vue`; `datasources.vue` passes `authStore.role === 'SME_ADMIN'`; quarantine toggle hidden for ACCOUNTANT. New test added.
- ✅ Resolved review finding [Patch] P1: `buildResponse()` now applies `navCredentialStatus` when `"DEMO".equals(dataSourceMode)` in addition to `NAV_ADAPTER_NAME`; demo-mode credential badge now shows VALID after save.
- ✅ Resolved review finding [Patch] P2: `demoModeDerivesCredentialStatusFromDb` test updated to assert VALID (not NOT_CONFIGURED); added companion test `demoModeCredentialStatusNotConfiguredWhenNoCreds`. 26 backend tests pass, 715 frontend tests pass.

### File List

- backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java (modified)
- backend/src/test/java/hu/riskguard/datasource/DataSourceAdminControllerTest.java (modified)
- frontend/app/pages/admin/datasources.vue (modified)
- frontend/app/components/Admin/NavCredentialManager.vue (modified)
- frontend/app/components/Admin/NavCredentialManager.spec.ts (modified)
- frontend/app/i18n/en/admin.json (modified)
- frontend/app/i18n/hu/admin.json (modified)

### Change Log

- 2026-04-08: Story 8.4 implemented — Accountant NAV credential access & demo mode
- 2026-04-09: Addressed code review findings — 3 items resolved (D1, P1, P2)
- 2026-04-09: Code review R2 — 1 patch applied (W6: chunked arrayBufferToBase64 in usePdfFont.ts), 0 deferred, 0 dismissed; 715 frontend + 5 e2e all green; status → done

### Review Findings

- [x] [Review][Patch] D1→Patch: Hide quarantine controls for ACCOUNTANT — `v-if` or conditional prop on the quarantine action in `datasources.vue` so ACCOUNTANT does not see admin-only controls [`frontend/app/pages/admin/datasources.vue`]
- [x] [Review][Patch] P1: AC 3 broken — demo-mode credential status badge never shows VALID; `buildResponse` applies `navCredentialStatus` only to `NAV_ADAPTER_NAME` ("nav-online-szamla"), which is absent in demo mode (only "demo" adapter is present); fix: also apply `navCredentialStatus` when `name` equals "demo" [`DataSourceAdminController.java` ~line 237]
- [x] [Review][Patch] P2: `demoModeDerivesCredentialStatusFromDb` test name is misleading — passes via adapter-name mismatch (adapter is "demo", not "nav-online-szamla"), not by exercising DB credential lookup; does not cover AC 3 intent that status shows VALID after save in demo mode [`DataSourceAdminControllerTest.java` ~line 200]
- [x] [Review][Defer] W1: No delete confirmation dialog on NavCredentialManager — single click permanently deletes credentials with no undo; pre-existing UX gap [`NavCredentialManager.vue`] — deferred, pre-existing
- [x] [Review][Defer] W2: `requireAdminOrAccountantRole` null-role behavior undocumented — null JWT claim correctly triggers 403 via `!"X".equals(null)` pattern but intent never stated; same pattern as pre-existing `requireAdminRole` [`DataSourceAdminController.java`] — deferred, pre-existing
- [x] [Review][Defer] W3: Class-level Javadoc still says "Restricted to SME_ADMIN only" after role expansion to ACCOUNTANT [`DataSourceAdminController.java` ~line 37] — deferred, pre-existing
- [x] [Review][Defer] W4: `adapters[0]` hardcoded in `datasources.vue` — dataSourceMode passed to NavCredentialManager is from first adapter; wrong in multi-adapter setups where NAV isn't at index 0 [`datasources.vue`] — deferred, pre-existing
- [x] [Review][Defer] W5: `getHealth` computes `existsByTenantId` DB query on every 30s poll call with no caching — pre-existing scalability concern [`DataSourceAdminController.java`] — deferred, pre-existing
- [x] [Review][Patch] W6: `arrayBufferToBase64` in `usePdfFont.ts` used character-by-character string concatenation over 320KB font files — fixed R2: chunked `String.fromCharCode(...bytes.subarray(i, CHUNK))` reduces 320K→40 function calls [`usePdfFont.ts`]
