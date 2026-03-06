# Story 1.3: Google & Microsoft SSO Integration

Status: review

## Story


As a User,
I want to log in using my existing Google or Microsoft account,
so that I don't have to manage another password.

## Acceptance Criteria

1. **OAuth2 Redirect:** Given a Nuxt 3 frontend and Spring Security 6 backend, when I click the "Login with Google" or "Login with Microsoft" button, I am redirected to the respective provider's OAuth2 consent screen.
2. **User/Tenant Creation:** Upon successful return from the SSO provider, the system must check if a user with that email exists. If not, a new `User` and a corresponding `Tenant` record must be created.
3. **Dual-Claim JWT:** After authentication/registration, the backend must issue a stateless JWT containing both `home_tenant_id` and `active_tenant_id` claims (initially identical for new users).
4. **Token Storage:** The frontend must receive the JWT and store it securely (e.g., in a secure cookie or local storage) for subsequent authenticated requests.
5. **Error Handling:** Failed login attempts (e.g., user cancels consent, provider error) must be handled gracefully, showing a clear, localized error message in the UI.
6. **Localization:** The login UI and error messages must be available in both Hungarian (primary) and English (fallback).

## Tasks / Subtasks

- [x] Configure Spring Security OAuth2 Client for Google and Microsoft Entra ID (AC: 1)
- [x] Implement `CustomOAuth2UserService` to handle user/tenant auto-provisioning (AC: 2)
- [x] Create `JwtTokenProvider` to generate dual-claim JWTs upon successful authentication (AC: 3)
- [x] Implement a custom `AuthenticationSuccessHandler` to redirect back to the frontend with the JWT (AC: 3, 4)
- [x] Build the Login page in Nuxt 3 with SSO buttons (AC: 1, 6)
- [x] Implement frontend auth store (Pinia) to manage JWT and user state (AC: 4)
- [x] Add localized error handling for OAuth2 failures (AC: 5, 6)
- [x] Verify SSO flow with integration tests using mock OAuth2 provider (AC: 1, 2, 3)

### Review Follow-ups (AI)

- [x] [AI-Review][CRITICAL] Stateful OAuth2 Flow in Stateless App (missing AuthorizationRequestRepository) [backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java:45]
- [x] [AI-Review][CRITICAL] Security Vulnerability: Weak/Unvalidated JWT Secret [backend/src/main/java/hu/riskguard/core/security/TokenProvider.java:21]
- [x] [AI-Review][MEDIUM] Hardcoded Success Redirect hop to /auth/login [backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:39]
- [x] [AI-Review][MEDIUM] Missing CSRF protection strategy for cookie-based auth [backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java:39]
- [x] [AI-Review][LOW] Redundant UUID.randomUUID() for User/Tenant (prefer DB default or factory) [backend/src/main/java/hu/riskguard/identity/domain/CustomOAuth2UserService.java:48]
- [x] [AI-Review][CRITICAL] Untracked file: HttpCookieOAuth2AuthorizationRequestRepository.java (required for stateless OAuth2)
- [x] [AI-Review][HIGH] Hardcoded targetUrl points to /auth/login after SUCCESS [backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:39]
- [x] [AI-Review][MEDIUM] Refactoring desync: Story file list paths don't match frontend/app/ structure
- [x] [AI-Review][MEDIUM] Untracked database migration: V20260306_001__add_guest_sessions_fk.sql
...
### Completion Notes List

- [x] Configured Google and Microsoft Entra ID OAuth2 registration in `application.yml`.
- [x] Implemented `HttpCookieOAuth2AuthorizationRequestRepository` for stateless OAuth2 flow.
- [x] Enabled CSRF protection with `CookieCsrfTokenRepository` for secure cookie-based auth.
- [x] Hardened `TokenProvider` with `@PostConstruct` JWT secret length validation.
- [x] Verified `CustomOAuth2UserService` correctly handles auto-provisioning with improved idiomatic logic.
- [x] Verified `TokenProvider` includes `home_tenant_id` and `active_tenant_id` in dual-claim JWT.
- [x] Implemented `OAuth2AuthenticationSuccessHandler` with configurable frontend redirect and JWT delivery via HttpOnly cookie.
- [x] Implemented `OAuth2AuthenticationFailureHandler` for graceful error redirection to frontend with RFC 7807 error keys.
- [x] Created Nuxt 3 Login page with PrimeVue buttons and localized i18n support.
- [x] Implemented Pinia auth store for JWT management, supporting HttpOnly cookies and /me endpoint.
- [x] Added global auth middleware to handle session hydration from HttpOnly cookies.
- [x] Resolved all code review findings including missing files, hardcoded redirects, and frontend path desync.
- [x] Verified both backend and frontend compilation and type safety.

### File List

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java`
- `backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java`
- `backend/src/main/java/hu/riskguard/core/security/TokenProvider.java`
- `backend/src/main/java/hu/riskguard/core/security/HttpCookieOAuth2AuthorizationRequestRepository.java` (Stateless OAuth2)
- `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java`
- `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationFailureHandler.java`
- `backend/src/main/java/hu/riskguard/core/security/CustomOAuth2User.java`
- `backend/src/main/java/hu/riskguard/core/util/CookieUtils.java`
- `backend/src/main/java/hu/riskguard/identity/domain/CustomOAuth2UserService.java`
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java`
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java`
- `backend/src/main/java/hu/riskguard/identity/api/dto/UserResponse.java`
- `backend/src/main/resources/db/migration/V20260306_001__add_guest_sessions_fk.sql`
- `backend/src/test/java/hu/riskguard/identity/domain/CustomOAuth2UserServiceTest.java`
- `backend/src/test/java/hu/riskguard/identity/SsoIntegrationTest.java`
- `backend/src/test/resources/application-test.yml`
- `frontend/nuxt.config.ts`
- `frontend/app/middleware/auth.global.ts`
- `frontend/app/stores/auth.ts`
- `frontend/app/pages/auth/login.vue`
- `frontend/app/pages/login/callback.vue`
- `frontend/app/risk-guard-tokens.json`
- `frontend/app/i18n/hu/auth.json`
- `frontend/app/i18n/hu/common.json`
- `frontend/app/i18n/hu/identity.json`
- `frontend/app/i18n/en/auth.json`
- `frontend/app/i18n/en/common.json`
- `frontend/app/i18n/en/identity.json`

## Story Completion Status

Status: review
Completion Note: SSO implementation complete. All code review findings addressed: untracked files added, hardcoded redirects fixed, and file list updated for frontend refactoring. Both backend and frontend builds are successful.

