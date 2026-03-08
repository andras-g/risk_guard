# Story 1.3: Google & Microsoft SSO Integration

Status: done

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
- [x] [AI-Review][HIGH] JWT missing `role` claim — frontend relies on /me for role, breaks stateless architecture (ADR-5) [backend/src/main/java/hu/riskguard/core/security/TokenProvider.java:38]
- [x] [AI-Review][HIGH] Mandate `validTo` expiry never checked — expired mandates still grant tenant access [backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java:47]
- [x] [AI-Review][HIGH] No audit logging for tenant context switches — architecture requires TenantContextSwitched event [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:49]
- [x] [AI-Review][MEDIUM] OAuth2AuthorizationRequest cookie serialization uses plain Jackson without Spring Security mixins — fragile across upgrades [backend/src/main/java/hu/riskguard/core/util/CookieUtils.java:72]
- [x] [AI-Review][MEDIUM] IdentityRepository.findMandatedTenants() returns api.dto.TenantResponse directly — violates module layering [backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java:60]
- [x] [AI-Review][MEDIUM] Cookie maxAge hardcoded to 3600 in SuccessHandler but uses properties in IdentityController — inconsistent [backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:31]
- [x] [AI-Review][LOW] SsoIntegrationTest mocks OAuth2UserRequest despite being @SpringBootTest — misleading test name [backend/src/test/java/hu/riskguard/identity/SsoIntegrationTest.java:47]
- [x] [AI-Review][LOW] processOAuth2User is public but should be package-private [backend/src/main/java/hu/riskguard/identity/domain/CustomOAuth2UserService.java:32]
- [x] [AI-Review-R3][HIGH] DecodedToken interface missing `role` field — frontend never extracts role from JWT on client side [frontend/app/stores/auth.ts:16-20]
- [x] [AI-Review-R3][HIGH] TenantContextSwitchedEvent in api/event/ package — violates architecture module layering (should be domain/events/) [backend/src/main/java/hu/riskguard/identity/api/event/TenantContextSwitchedEvent.java]
- [x] [AI-Review-R3][HIGH] IdentityController.switchTenant() publishes audit event AFTER response commit — event failure silently loses audit trail [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:81-87]
- [x] [AI-Review-R3][MEDIUM] RiskGuardProperties.validateSecurityConfig() duplicates TokenProvider.validateSecret() with different thresholds (char vs byte length) [backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java:44-55]
- [x] [AI-Review-R3][MEDIUM] OAuth2AuthenticationFailureHandler passes full URN as error query param but frontend tries to use it as i18n key — format mismatch, always falls back to generic [backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationFailureHandler.java:22]
- [x] [AI-Review-R3][MEDIUM] HttpCookieOAuth2AuthorizationRequestRepository uses addCookie() overload that defaults secure=false — OAuth2 state cookie sent over plain HTTP even in production [backend/src/main/java/hu/riskguard/core/security/HttpCookieOAuth2AuthorizationRequestRepository.java:40]
- [x] [AI-Review-R3][MEDIUM] auth.ts isAuthenticated getter uses OR (email || token) — creates zombie auth state when JWT decoding fails but token is set [frontend/app/stores/auth.ts:34]
- [x] [AI-Review-R3][LOW] CustomOAuth2UserService doesn't validate OAuth2 name attribute length/content before using in tenant name [backend/src/main/java/hu/riskguard/identity/domain/CustomOAuth2UserService.java:52]
- [x] [AI-Review-R3][LOW] No test for OAuth2AuthenticationFailureHandler error redirect behavior — security-critical path untested
- [x] [AI-Review-R4][HIGH] SecretKey re-derived on every createToken() call — cache as @PostConstruct field in TokenProvider; JwtDecoder in SecurityConfig uses independent derivation, no shared source of truth [backend/src/main/java/hu/riskguard/core/security/TokenProvider.java:33]
- [x] [AI-Review-R4][HIGH] Re-login always resets active_tenant_id to home_tenant_id — behaviour is undocumented and untested; AC-3 says "initially identical for new users" but says nothing about returning users [backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:27]
- [x] [AI-Review-R4][HIGH] TenantContextSwitchedEvent has zero @EventListener handlers — the "audit trail" is a published-and-forgotten no-op; event record and publisher exist but no consumer was implemented [backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEvent.java]
- [x] [AI-Review-R4][MEDIUM] CookieUtils.deleteCookie() missing Secure and SameSite attributes — deletion cookie differs from creation cookie; Chromium may silently fail to delete the OAuth2 state cookie in production [backend/src/main/java/hu/riskguard/core/util/CookieUtils.java:71-78]
- [x] [AI-Review-R4][MEDIUM] auth.global.ts fires authStore.initializeAuth() (and therefore a /me HTTP request) on every unauthenticated navigation including public routes — should short-circuit on public routes before calling initializeAuth() [frontend/app/middleware/auth.global.ts:7-9]
- [x] [AI-Review-R4][MEDIUM] switchTenant() returns 204 via imperative response.setStatus() — should use @ResponseStatus(NO_CONTENT) or ResponseEntity<Void>; the 204 status is untested in IdentityControllerTest [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:88]
- [x] [AI-Review-R4][MEDIUM] TokenProviderTest.shouldRejectWeakSecret() manually calls validateSecret() without Spring lifecycle — @PostConstruct is not invoked by plain new; test is misleading about startup validation behaviour [backend/src/test/java/hu/riskguard/core/security/TokenProviderTest.java:73-83]
- [x] [AI-Review-R4][LOW] auth.ts setToken() leaves this.token set when jwtDecode() throws — should call clearAuth() or set this.token = null in catch block to prevent stale token state [frontend/app/stores/auth.ts:49-51]
- [x] [AI-Review-R4][LOW] MandateExpiryIntegrationTest declares its own @Container PostgreSQLContainer while SsoUserProvisioningTest uses TC JDBC URL — inconsistent Testcontainer strategies may spin up two DB instances per test run [backend/src/test/java/hu/riskguard/identity/MandateExpiryIntegrationTest.java:29-31]
- [x] [AI-Review-R4][LOW] clearAuth() cannot clear the HttpOnly auth_token cookie — useCookie().value = null has no effect on HttpOnly cookies; no backend logout endpoint exists to issue a Max-Age=0 deletion [frontend/app/stores/auth.ts:87-98]

### Review Follow-ups (AI) — Round 6

- [x] [AI-Review-R6][CRITICAL] `@ApplicationModuleListener` runs listener in a separate transaction — "event failure aborts switch" guarantee was broken; replaced with `@TransactionalEventListener(phase = BEFORE_COMMIT)` so listener fires within the calling TX [backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEventListener.java:25]
- [x] [AI-Review-R6][CRITICAL] `getMandates` still had raw SpEL `@PreAuthorize("authentication.token.claims['role'] == 'ACCOUNTANT'")` while `switchTenant` was fixed in R5 — inconsistent RBAC; replaced with explicit in-method role check consistent with `switchTenant` pattern [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:48]

### Review Follow-ups (AI) — Round 5

- [x] [AI-Review-R5][HIGH] `RiskGuardProperties.loadTokensConfig()` reads `risk-guard-tokens.json` via `ClassPathResource` but the file is NOT in `backend/src/main/resources/` — always fails silently, `cookieName` always falls back to hardcoded default, defeating single-source-of-truth architecture mandate [backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java:35]
- [x] [AI-Review-R5][HIGH] `@PreAuthorize("authentication.token.claims['role'] == 'ACCOUNTANT'")` on `/tenants/switch` gates the endpoint entirely to ACCOUNTANTs, making the self-switch path (`user.getTenantId().equals(request.tenantId())`) dead code for SME_ADMIN users; use Spring Security `hasRole()` or restructure the business logic [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:47,72]
- [x] [AI-Review-R5][HIGH] `TenantContextSwitchedEvent` is a plain Java record — does NOT extend `ApplicationEvent` or use `@DomainEvent`; Spring Modulith Event Publication Registry will NOT track it, no guaranteed delivery or replay on listener failure; listener uses `@EventListener` not `@ApplicationModuleListener` [backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEvent.java]
- [x] [AI-Review-R5][MEDIUM] `IdentityController.switchTenant()` lacks `@Transactional` — audit event fires and then token creation could fail, leaving a logged audit event for a switch that was never committed [backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:71]
- [x] [AI-Review-R5][MEDIUM] `RiskGuardProperties` uses both `@Component` and `@ConfigurationProperties` — legacy pattern in Spring Boot 4; use `@ConfigurationPropertiesScan` + `@ConfigurationProperties` only; `@PostConstruct` may fire before binding is complete [backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java:5-6]
- [x] [AI-Review-R5][MEDIUM] `IdentityService` facade is missing `@Transactional` on all read methods; `IdentityController.switchTenant()` calls `findUserByEmail()` then `hasMandate()` as two separate non-transactional reads — user could be deleted between the two calls [backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java]
- [x] [AI-Review-R5][LOW] `TenantContextSwitchedEvent` carries `email` as a record field — PII in-memory with no `@LogSafe` / `@PiiField` marker; any future `@EventListener` could accidentally log it; consider removing email from the event or adding an explicit PII annotation [backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEvent.java:18]
- [x] [AI-Review-R5][LOW] Story 1-3 File List documents `frontend/app/risk-guard-tokens.json` (wrong path) — actual changed file is the monorepo root `risk-guard-tokens.json` [_bmad-output/implementation-artifacts/1-3-google-and-microsoft-sso-integration.md:148]

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
- [x] ✅ Resolved review finding [HIGH]: Added `role` claim to JWT via TokenProvider.createToken() — stateless architecture restored.
- [x] ✅ Resolved review finding [HIGH]: Mandate `validTo` expiry now enforced in hasMandate() and findMandatedTenants() — expired mandates blocked.
- [x] ✅ Resolved review finding [HIGH]: TenantContextSwitchedEvent published on tenant switch — audit trail complete.
- [x] ✅ Resolved review finding [MEDIUM]: CookieUtils now uses Spring Security Jackson mixins (CoreJackson2Module + OAuth2ClientJackson2Module).
- [x] ✅ Resolved review finding [MEDIUM]: IdentityRepository.findMandatedTenants() returns domain Tenant, conversion to TenantResponse in IdentityService facade.
- [x] ✅ Resolved review finding [MEDIUM]: Cookie maxAge in SuccessHandler now uses properties.getSecurity().getJwtExpirationMs() / 1000 — consistent with IdentityController.
- [x] ✅ Resolved review finding [LOW]: Renamed SsoIntegrationTest → SsoUserProvisioningTest with Javadoc explaining mock rationale.
- [x] ✅ Resolved review finding [LOW]: processOAuth2User changed from public to package-private, test moved to same package.
- [x] ✅ Resolved review finding R3 [HIGH]: DecodedToken interface now includes `role` field, setToken() extracts role from JWT on client side.
- [x] ✅ Resolved review finding R3 [HIGH]: Moved TenantContextSwitchedEvent from api/event/ to domain/events/ — module layering corrected.
- [x] ✅ Resolved review finding R3 [HIGH]: switchTenant() now publishes audit event BEFORE response commit — event failure aborts the switch, no silent audit loss.
- [x] ✅ Resolved review finding R3 [MEDIUM]: Removed duplicate secret length validation from RiskGuardProperties; only TokenProvider validates (byte-based, 256-bit minimum). Properties retains default-value warning.
- [x] ✅ Resolved review finding R3 [MEDIUM]: OAuth2AuthenticationFailureHandler now sends plain i18n key `auth-failed` (not URN), added matching i18n keys in hu/en auth.json.
- [x] ✅ Resolved review finding R3 [MEDIUM]: HttpCookieOAuth2AuthorizationRequestRepository now injects RiskGuardProperties and passes cookieSecure flag to addCookie().
- [x] ✅ Resolved review finding R3 [MEDIUM]: auth.ts isAuthenticated changed from OR to AND logic — prevents zombie auth state when JWT decode fails.
- [x] ✅ Resolved review finding R3 [LOW]: Added sanitizeOAuth2Name() to CustomOAuth2UserService — strips control chars, trims, truncates to 100 chars, returns null for blank input.
- [x] ✅ Resolved review finding R3 [LOW]: Added OAuth2AuthenticationFailureHandlerTest with 2 tests using Spring MockHttpServlet* — verifies i18n key format and configurable base URL.
- [x] ✅ Fixed pre-existing bug: RiskGuardApplicationTests missing @ActiveProfiles("test") — was hitting local dev DB instead of Testcontainers, causing Flyway baseline error.
- [x] ✅ Resolved review finding R4 [HIGH]: Cached SecretKey as @PostConstruct field in TokenProvider; SecurityConfig.jwtDecoder() now uses tokenProvider.getSigningKey() — single source of truth.
- [x] ✅ Resolved review finding R4 [HIGH]: Documented intentional active_tenant_id reset behavior on re-login; added OAuth2AuthenticationSuccessHandlerTest with 2 tests verifying reset and no-token-in-URL redirect.
- [x] ✅ Resolved review finding R4 [HIGH]: Created TenantContextSwitchedEventListener with @EventListener — audit events now logged (PII-safe: no email in logs). 2 tests added.
- [x] ✅ Resolved review finding R4 [MEDIUM]: CookieUtils.deleteCookie() now accepts forceSecure param with Secure and SameSite=Lax attributes; HttpCookieOAuth2AuthorizationRequestRepository callers updated. 2 tests added.
- [x] ✅ Resolved review finding R4 [MEDIUM]: auth.global.ts short-circuits on public routes BEFORE calling initializeAuth() — no unnecessary /me requests.
- [x] ✅ Resolved review finding R4 [MEDIUM]: switchTenant() now returns ResponseEntity<Void> (204 No Content) via ResponseEntity.noContent().build(); removed HttpServletRequest param; tests verify 204 status.
- [x] ✅ Resolved review finding R4 [MEDIUM]: TokenProviderTest now calls init() (simulating @PostConstruct) in setUp(); renamed shouldRejectWeakSecret → shouldRejectWeakSecretDuringInit; added shouldCacheSigningKeyAfterInit test.
- [x] ✅ Resolved review finding R4 [LOW]: setToken() catch block now calls clearAuth() to prevent zombie auth state with stale token.
- [x] ✅ Resolved review finding R4 [LOW]: Removed @Container/@ServiceConnection from MandateExpiryIntegrationTest — now uses TC JDBC URL from application-test.yml, consistent with SsoUserProvisioningTest.
- [x] ✅ Resolved review finding R4 [LOW]: Added POST /api/v1/identity/logout endpoint to clear HttpOnly cookie (Max-Age=0); frontend clearAuth() now calls backend logout; added logout test in IdentityControllerTest.
- [x] ✅ Resolved review finding R5 [HIGH]: Copied `risk-guard-tokens.json` to `backend/src/main/resources/` — file now on classpath for IDE, tests, and runtime without requiring a Gradle build step. `processResources` task updated with `DuplicatesStrategy.INCLUDE` to keep in sync with monorepo root.
- [x] ✅ Resolved review finding R5 [HIGH]: Removed `@PreAuthorize` from `switchTenant()` and restructured business logic — SME_ADMIN can self-switch to home tenant; ACCOUNTANT can switch to any mandated tenant; non-ACCOUNTANT attempting external switch throws FORBIDDEN. Added `switchTenantShouldForbidSmeAdminSwitchingToExternalTenant()` test; updated existing tests to use ACCOUNTANT role for external switch scenarios.
- [x] ✅ Resolved review finding R5 [HIGH]: Changed `@EventListener` to `@ApplicationModuleListener` in `TenantContextSwitchedEventListener` — events now tracked by Spring Modulith Event Publication Registry with guaranteed at-least-once delivery and replay on listener failure.
- [x] ✅ Resolved review finding R5 [MEDIUM]: Added `@Transactional` to `IdentityController.switchTenant()` — audit event, mandate check, and token creation now execute in a single transaction boundary.
- [x] ✅ Resolved review finding R5 [MEDIUM]: Removed `@Component` from `RiskGuardProperties`; registered via `@EnableConfigurationProperties(RiskGuardProperties.class)` on `RiskGuardApplication` — eliminates anti-pattern where `@PostConstruct` could fire before property binding completes.
- [x] ✅ Resolved review finding R5 [MEDIUM]: Added `@Transactional(readOnly = true)` to all `IdentityService` read methods (`findUserByEmail`, `hasMandate`, `findMandatedTenants`) — prevents TOCTOU issues and enables consistent snapshot reads.
- [x] ✅ Resolved review finding R5 [LOW]: Removed `email` field from `TenantContextSwitchedEvent` record per PII zero-tolerance policy — only `@LogSafe` types (UUIDs, OffsetDateTime) remain; listener already omitted email from logs; `userId` uniquely identifies the actor. Updated event listener tests and controller tests accordingly.
- [x] ✅ Resolved review finding R5 [LOW]: Fixed story File List path for `risk-guard-tokens.json` — was incorrectly documented as `frontend/app/risk-guard-tokens.json`; correct path is `risk-guard-tokens.json` (monorepo root).
- [x] ✅ Resolved review finding R6 [CRITICAL]: Replaced `@ApplicationModuleListener` with `@TransactionalEventListener(phase = BEFORE_COMMIT)` in `TenantContextSwitchedEventListener` — listener now fires within the calling transaction before commit; listener failure rolls back the entire switch operation, restoring the abort-on-failure guarantee. Updated Javadoc.
- [x] ✅ Resolved review finding R6 [CRITICAL]: Removed `@PreAuthorize` from `getMandates()`; replaced with explicit `jwt.getClaimAsString("role")` check throwing FORBIDDEN for non-ACCOUNTANT — consistent with `switchTenant` RBAC pattern. Removed unused `@PreAuthorize` import. Added 2 tests (`getMandatesShouldForbidNonAccountant`, `getMandatesShouldReturnMandatesForAccountant`).

### File List

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java` (MODIFIED R3 — removed duplicate secret length validation)
- `backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java` (MODIFIED R4 — jwtDecoder uses TokenProvider.getSigningKey())
- `backend/src/main/java/hu/riskguard/core/security/TokenProvider.java` (MODIFIED R4 — cached signingKey, renamed validateSecret→init, added getSigningKey())
- `backend/src/main/java/hu/riskguard/core/security/HttpCookieOAuth2AuthorizationRequestRepository.java` (MODIFIED R3+R4 — deleteCookie now passes forceSecure)
- `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java` (MODIFIED R4 — documented intentional active_tenant_id reset)
- `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationFailureHandler.java` (MODIFIED R3 — sends plain i18n key instead of URN)
- `backend/src/main/java/hu/riskguard/core/security/CustomOAuth2User.java`
- `backend/src/main/java/hu/riskguard/core/util/CookieUtils.java` (MODIFIED R4 — deleteCookie with forceSecure, Secure and SameSite attrs)
- `backend/src/main/java/hu/riskguard/identity/domain/CustomOAuth2UserService.java` (MODIFIED R3 — added sanitizeOAuth2Name() validation)
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java`
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java` (MODIFIED R3+R4 — switchTenant returns ResponseEntity, added logout endpoint)
- `backend/src/main/java/hu/riskguard/identity/api/dto/UserResponse.java`
- `backend/src/main/resources/db/migration/V20260306_001__add_guest_sessions_fk.sql`
- `backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java`
- `backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEvent.java` (MOVED R3 — from api/event/ to domain/events/)
- ~~`backend/src/main/java/hu/riskguard/identity/api/event/TenantContextSwitchedEvent.java`~~ (DELETED R3 — moved to domain/events/)
- `backend/src/test/java/hu/riskguard/identity/domain/CustomOAuth2UserServiceTest.java` (MODIFIED R3 — added 7 sanitizeOAuth2Name tests)
- `backend/src/test/java/hu/riskguard/identity/domain/SsoUserProvisioningTest.java` (RENAMED from SsoIntegrationTest)
- `backend/src/test/java/hu/riskguard/identity/MandateExpiryIntegrationTest.java` (NEW, MODIFIED R4 — removed @Container, uses TC JDBC URL)
- `backend/src/test/java/hu/riskguard/identity/api/IdentityControllerTest.java` (MODIFIED R3+R4 — updated switchTenant signature, added logout test, 204 assertions)
- `backend/src/test/java/hu/riskguard/core/security/TokenProviderTest.java` (NEW, MODIFIED R4 — calls init(), added caching test, renamed weak secret test)
- `backend/src/test/java/hu/riskguard/core/security/OAuth2AuthenticationFailureHandlerTest.java` (NEW R3 — 2 tests for error redirect behavior)
- `backend/src/test/java/hu/riskguard/core/util/CookieUtilsTest.java` (NEW, MODIFIED R4 — added 2 deleteCookie tests with Secure/SameSite assertions)
- `backend/src/test/java/hu/riskguard/RiskGuardApplicationTests.java` (MODIFIED R3 — added @ActiveProfiles("test") to fix Flyway baseline error)
- `backend/src/test/resources/application-test.yml`
- `frontend/nuxt.config.ts`
- `frontend/app/middleware/auth.global.ts` (MODIFIED R4 — short-circuit on public routes before initializeAuth)
- `frontend/app/stores/auth.ts` (MODIFIED R3+R4 — clearAuth calls backend logout, setToken calls clearAuth on decode failure)
- `frontend/app/pages/auth/login.vue`
- `frontend/app/pages/login/callback.vue`
- `risk-guard-tokens.json` (MODIFIED R4 — added logout endpoint; canonical monorepo root copy)
- `frontend/app/i18n/hu/auth.json` (MODIFIED R3 — added auth-failed i18n key, alphabetized)
- `frontend/app/i18n/hu/common.json`
- `frontend/app/i18n/hu/identity.json`
- `frontend/app/i18n/en/auth.json` (MODIFIED R3 — added auth-failed i18n key, alphabetized)
- `frontend/app/i18n/en/common.json`
- `frontend/app/i18n/en/identity.json`
- `backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEventListener.java` (NEW R4 — @EventListener for audit logging)
- `backend/src/test/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEventListenerTest.java` (NEW R4 — 2 tests)
- `backend/src/test/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandlerTest.java` (NEW R4 — 2 tests for reset behavior and redirect)
- `backend/src/main/resources/risk-guard-tokens.json` (NEW R5 — copied from monorepo root for classpath availability)
- `backend/build.gradle` (MODIFIED R5 — added DuplicatesStrategy.INCLUDE to processResources; added documentation comment)
- `backend/src/main/java/hu/riskguard/RiskGuardApplication.java` (MODIFIED R5 — added @EnableConfigurationProperties(RiskGuardProperties.class))
- `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java` (MODIFIED R5 — removed @Component anti-pattern; added Javadoc)
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java` (MODIFIED R5 — removed @PreAuthorize from switchTenant; restructured role-based authorization; added @Transactional; removed email from event factory call)
- `backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java` (MODIFIED R5 — added @Transactional(readOnly=true) to all read methods)
- `backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEvent.java` (MODIFIED R5 — removed email field; updated factory method signature)
- `backend/src/main/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEventListener.java` (MODIFIED R5 — @EventListener → @ApplicationModuleListener for Spring Modulith Event Publication Registry tracking)
- `backend/src/test/java/hu/riskguard/identity/api/IdentityControllerTest.java` (MODIFIED R5 — updated roles in switchTenant tests; removed event.email() assertion; added switchTenantShouldForbidSmeAdminSwitchingToExternalTenant() test)
- `backend/src/test/java/hu/riskguard/identity/domain/events/TenantContextSwitchedEventListenerTest.java` (MODIFIED R5 — updated event factory calls to remove email arg)

## Change Log

- 2026-03-08: Addressed code review round 6 findings — 2 CRITICAL items resolved. Replaced @ApplicationModuleListener with @TransactionalEventListener(BEFORE_COMMIT) to restore abort-on-failure guarantee for tenant switch audit; removed raw SpEL @PreAuthorize from getMandates and replaced with explicit role check consistent with switchTenant. 47/47 backend tests pass.
- 2026-03-08: Addressed code review round 5 findings — all 8 items resolved (3 HIGH, 3 MEDIUM, 2 LOW). Copied risk-guard-tokens.json to classpath (backend/src/main/resources/); fixed @PreAuthorize dead-code with role-based business logic in switchTenant (SME_ADMIN can self-switch, ACCOUNTANT can switch to mandated tenants); upgraded TenantContextSwitchedEventListener to @ApplicationModuleListener for Spring Modulith Event Publication Registry tracking; added @Transactional to switchTenant and all IdentityService read methods; removed @Component anti-pattern from RiskGuardProperties (now @EnableConfigurationProperties); removed PII email field from TenantContextSwitchedEvent; fixed File List path. All 45/45 tests pass.
- 2026-03-08: Code review round 5 — 8 new findings (3 HIGH, 3 MEDIUM, 2 LOW). Key issues: risk-guard-tokens.json not on classpath so cookieName always falls back to hardcoded default; @PreAuthorize raw-claim expression makes self-switch dead code for SME_ADMIN; TenantContextSwitchedEvent is a plain record not tracked by Spring Modulith Event Publication Registry; missing @Transactional on switchTenant; @Component+@ConfigurationProperties anti-pattern; missing @Transactional on IdentityService facade; PII email field in event record lacks marker; wrong token file path in File List.
- 2026-03-08: Addressed code review round 4 findings — all 10 items resolved (3 HIGH, 4 MEDIUM, 3 LOW). Cached SecretKey in TokenProvider with single source of truth for SecurityConfig, documented and tested active_tenant_id reset on re-login, created TenantContextSwitchedEventListener for audit logging, added Secure/SameSite to deleteCookie, short-circuited auth middleware on public routes, switched to ResponseEntity<Void> for switchTenant, fixed test lifecycle for @PostConstruct, added clearAuth on decode failure, unified Testcontainer strategy, added backend logout endpoint. All 44/44 tests pass.
- 2026-03-07: Addressed code review round 3 findings — all 9 items resolved (3 HIGH, 4 MEDIUM, 2 LOW). Fixed frontend DecodedToken role extraction, moved TenantContextSwitchedEvent to domain/events/, reordered audit event before response commit, consolidated duplicate secret validation, fixed error key format for i18n, added secure cookie flag to OAuth2 auth repo, fixed isAuthenticated AND logic, added name sanitization, added failure handler tests. Also fixed pre-existing RiskGuardApplicationTests Flyway failure (missing @ActiveProfiles). All 36/36 tests pass.
- 2026-03-07: Code review round 3 — 9 new findings (3 HIGH, 4 MEDIUM, 2 LOW). Key issues: frontend DecodedToken missing role field, TenantContextSwitchedEvent in wrong package, audit event after response commit, duplicate secret validation, error key format mismatch, OAuth2 state cookie not Secure, fragile isAuthenticated getter, missing name validation, missing failure handler test.
- 2026-03-07: Addressed code review round 2 findings — 8 items resolved (3 HIGH, 3 MEDIUM, 2 LOW). Added role claim to JWT, enforced mandate expiry, added TenantContextSwitchedEvent audit, fixed cookie serialization, corrected module layering, unified cookie maxAge, renamed misleading test, restricted processOAuth2User visibility.

## Story Completion Status

Status: done
Completion Note: All original tasks and rounds 1-6 review follow-ups complete. 47/47 backend tests pass. Frontend builds successfully. Backend starts cleanly. Marked done after round 6 critical fixes: @TransactionalEventListener BEFORE_COMMIT restores abort-on-failure guarantee; getMandates @PreAuthorize removed and replaced with consistent role check; nuxt.config.ts i18n langDir pre-existing build error fixed.
