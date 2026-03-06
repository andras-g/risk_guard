# Story 1.4: Accountant Context-Switcher UI

Status: in-progress

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an Accountant,
I want a searchable dropdown in the top-bar to switch between client accounts,
so that I can check different companies' status without logging out.

## Acceptance Criteria

1. **Accountant Role Requirement:** Given a user with the `ACCOUNTANT` role and multiple `tenant_mandates`, the Context Switcher must be visible in the top-bar.
2. **Searchable Dropdown:** When I click the Context Switcher and search for a client name, the UI must display matching clients.
3. **Switch Action:** Selecting a client triggers an API call that validates the mandate and issues a new short-lived JWT with the updated `active_tenant_id`.
4. **Dashboard Reload:** Upon successful switch, the entire dashboard must reload with the new client's context, ensuring all data fetched is for the new `active_tenant_id`.
5. **Safety Interstitial (ContextGuard):** If the tenant switch fails (e.g., token expired or unauthorized), a safety interstitial (ContextGuard) must block access and redirect back to the home tenant or login.
6. **Localization:** The Context Switcher and all status/error messages must be available in both Hungarian (primary) and English (fallback).

## Tasks / Subtasks

- [ ] Implement `IdentityController.switchTenant` (if not already fully functional) to return a new JWT cookie (AC: 3)
- [ ] Create `ContextSwitcher.vue` component in `frontend/components/Identity/` (AC: 2)
- [ ] Integrate `ContextSwitcher.vue` into the main layout top-bar (AC: 1, 2)
- [ ] Create `ContextGuard.vue` safety interstitial for transition states and error handling (AC: 5)
- [ ] Update Pinia `auth.ts` store to handle the tenant switch and state reload (AC: 4)
- [ ] Add localized strings to `hu/identity.json` and `en/identity.json` (AC: 6)
- [ ] Verify the switch flow with a Playwright E2E test simulating an accountant switching between two tenants (AC: 3, 4, 5)

## Dev Notes

- **Context Switching:** The backend must verify that the user has a valid entry in the `tenant_mandates` table for the target `tenant_id` before issuing the new JWT.
- **JWT Update:** The new JWT should have the same `home_tenant_id` but a different `active_tenant_id`. It should be delivered via the same secure HttpOnly cookie mechanism established in Story 1.3.
- **Frontend Reload:** Use `window.location.reload()` or Nuxt's `refresh` patterns to ensure no stale data remains in other Pinia stores after a switch.
- **Component Styling:** Use PrimeVue `Dropdown` or `AutoComplete` with a custom template for the searchable switcher, following the "Deep Navy" authority theme.

## Dev Agent Contextual Intelligence

### Previous Story Intelligence (Story 1.3)

- **Lessons Learned:** JWT delivery should use HttpOnly cookies for security. The `/me` endpoint is the primary source of truth for the frontend auth store. Ensure the new JWT is correctly set in the cookie after a switch.
- **Established Patterns:** Use `hu.riskguard.identity.internal.IdentityRepository` for all database interactions. The `TenantMandate` table is already created and populated during SSO.
- **Architecture Compliance:** Follow ADR-5 (Dual-Claim JWT) and ADR-1 (Spring Modulith). Ensure the `identity` module maintains strict boundaries.

### Git Intelligence Summary

- **Recent Work:** Story 1.3 implemented SSO integration and user/tenant/mandate auto-provisioning. The identity API and repository were refactored to use jOOQ and standard RFC 7807 error patterns.
- **Pattern:** Backend uses `hu.riskguard.core.security.TokenProvider` to generate JWTs. Frontend uses `frontend/stores/auth.ts` for identity state management.

### Project Structure Notes

- **Backend:** Context switch logic belongs in `hu.riskguard.identity.api.IdentityController`.
- **Frontend:** Components go in `frontend/components/Identity/`.
- **i18n:** Namespace-per-file: `frontend/i18n/hu/identity.json`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.4]
- [Source: _bmad-output/planning-artifacts/architecture.md#ADR-5]
- [Source: _bmad-output/implementation-artifacts/1-3-google-and-microsoft-sso-integration.md]
- [Source: backend/src/main/java/hu/riskguard/identity/api/IdentityController.java]
- [Source: frontend/stores/auth.ts]

## Dev Agent Record

### Agent Model Used

google-vertex/gemini-3-flash-preview

### Debug Log References

### Completion Notes List

- [x] Ultimate context engine analysis completed - comprehensive developer guide created

### File List

- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java`
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java`
- `frontend/components/Identity/ContextSwitcher.vue`
- `frontend/components/Identity/ContextGuard.vue`
- `frontend/stores/auth.ts`
- `frontend/i18n/hu/identity.json`
- `frontend/i18n/en/identity.json`

## Story Completion Status

Status: in-progress
Completion Note: Ultimate context engine analysis completed - comprehensive developer guide created
