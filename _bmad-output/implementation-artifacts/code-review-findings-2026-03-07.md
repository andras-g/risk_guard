# Consolidated Code Review Findings — Stories 1.1–1.4

**Reviewer:** Adversarial Senior Dev Review (AI)
**Date:** 2026-03-07
**Stories Covered:** 1.1, 1.2, 1.3, 1.4
**Total Issues:** 34 (2 CRITICAL, 7 HIGH, 14 MEDIUM, 11 LOW)

---

## 🔴 CRITICAL (2)

- [ ] [CRITICAL] Story 1.3 | **Java deserialization RCE via OAuth2 authorization request cookie** | `backend/src/main/java/hu/riskguard/core/util/CookieUtils.java:53` | `SerializationUtils.deserialize()` on user-supplied cookie value allows arbitrary object instantiation (classic deserialization gadget attack). Attacker crafts malicious serialized object in `oauth2_auth_request` cookie. Replace with JSON-based serialization or Spring's `Jackson2JsonRedisSerializer`.

- [ ] [CRITICAL] Story 1.3 | **JWT token leaked in URL query parameter** | `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:39` | Token appended to redirect URL `?token=...` — logged in browser history, server access logs, Referer headers. Token is ALSO set as HttpOnly cookie, making the query param redundant and purely a leak vector. Remove the query param entirely; frontend should read from cookie/`/me` endpoint.

---

## 🟠 HIGH (7)

- [ ] [HIGH] Story 1.2 | **Tenant isolation bypass — update/delete not filtered** | `backend/src/main/java/hu/riskguard/core/security/TenantAwareDSLContext.java:28-39` | `update()` and `deleteFrom()` override methods are NO-OPs — they just call `super` without adding `tenant_id` conditions. Only `selectFrom` and `fetchCount` are filtered. Any raw `dsl.update(TABLE)` or `dsl.deleteFrom(TABLE)` bypasses tenant isolation.

- [ ] [HIGH] Story 1.2 | **VisitListener tenant verification is a no-op** | `backend/src/main/java/hu/riskguard/core/security/TenantJooqListener.java:57-61` | `isTenantAwareQuery()` always returns `false`, so `visitEnd()` never actually verifies anything. The "secondary guard" comment is misleading — there is no secondary guard.

- [ ] [HIGH] Story 1.2 | **select() not overridden in TenantAwareDSLContext** | `backend/src/main/java/hu/riskguard/core/security/TenantAwareDSLContext.java` | Only `selectFrom()` is overridden. Any query using `dsl.select(...).from(TABLE).where(...)` bypasses tenant filtering entirely. `IdentityRepository.java:31` uses exactly this pattern.

- [ ] [HIGH] Story 1.2 | **IdentityRepository queries bypass tenant isolation** | `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java:31-34` | `findUserByEmail` uses `dsl.select().from()` (not `dsl.selectFrom()`), so TenantAwareDSLContext filtering does not apply. Same issue on lines 37-43 (`hasMandate`) and 46-51 (`findMandatedTenants`).

- [ ] [HIGH] Story 1.1 | **Hardcoded JWT secret in default config** | `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java:66` | Default secret `"default_secret_must_be_overridden_in_production_32_chars_min"` is committed in source. `TokenProvider.validateSecret()` only checks length >= 32, not that it was actually overridden. `application.yml:52` has the same weak default.

- [ ] [HIGH] Story 1.2 | **CSRF token repository set to httpOnly=false** | `backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java:43` | `CookieCsrfTokenRepository.withHttpOnlyFalse()` exposes CSRF token to JS. Combined with stateless JWT session, this is potentially inconsistent — CSRF protection with stateless JWT auth is unusual and may cause double-submit issues.

- [ ] [HIGH] Story 1.3 | **CookieUtils.addCookie missing Secure and SameSite flags** | `backend/src/main/java/hu/riskguard/core/util/CookieUtils.java:25-31` | `addCookie()` uses old `Cookie` API without setting Secure or SameSite. Used for `oauth2_auth_request` and `redirect_uri` cookies — these get sent over HTTP in dev and have no SameSite protection against CSRF.

---

## 🟡 MEDIUM (14)

- [ ] [MEDIUM] Story 1.4 | **TenantSwitchRequest missing @Valid/@NotNull** | `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:52` | `@RequestBody TenantSwitchRequest request` has no `@Valid` annotation, and `TenantSwitchRequest.java:5` record field `tenantId` has no `@NotNull`. Null `tenantId` causes NPE at line 61.

- [ ] [MEDIUM] Story 1.2 | **TenantFilter allows requests without tenant context** | `backend/src/main/java/hu/riskguard/core/security/TenantFilter.java:29-36` | If JWT has no `active_tenant_id` claim, filter silently proceeds without setting tenant context. Downstream tenant-aware queries then throw `IllegalStateException` (fail-closed), but non-tenant-aware endpoints run without context.

- [ ] [MEDIUM] Story 1.2 | **AsyncConfig does not propagate TenantContext** | `backend/src/main/java/hu/riskguard/core/config/AsyncConfig.java:31-46` | `MdcTaskDecorator` propagates only MDC, not `TenantContext` (ThreadLocal). Any async operation loses tenant isolation entirely.

- [ ] [MEDIUM] Story 1.4 | **Frontend auth store references missing endpoint** | `frontend/app/stores/auth.ts:72` | `authConfig.endpoints.mandates` does not exist in `risk-guard-tokens.json` (only `me` and `switchTenant` are defined). This will be `undefined`, causing `$fetch(undefined)`.

- [ ] [MEDIUM] Story 1.3 | **Frontend $fetch calls lack baseURL** | `frontend/app/stores/auth.ts:54,72,105` | `$fetch(authConfig.endpoints.me)` uses relative path `/api/v1/identity/me` without `baseURL` config. In SSR context this will fail; `runtimeConfig.public.apiBase` is not used.

- [ ] [MEDIUM] Story 1.3 | **auth.global.ts imports from non-existent path** | `frontend/app/middleware/auth.global.ts:1` | `import authConfig from '../risk-guard-tokens.json'` — `frontend/app/risk-guard-tokens.json` does not exist. File is at project root `risk-guard-tokens.json`.

- [ ] [MEDIUM] Story 1.3 | **Same broken import in login.vue and auth.ts** | `frontend/app/pages/auth/login.vue:2`, `frontend/app/stores/auth.ts:3` | Both import `risk-guard-tokens.json` from relative paths that resolve to `frontend/app/risk-guard-tokens.json` which does not exist.

- [ ] [MEDIUM] Story 1.4 | **i18n key mismatch: common.retry vs common.actions.retry** | `frontend/app/components/Identity/ContextGuard.vue:55` | `$t('common.retry')` but in `common.json` the key is nested at `actions.retry`. Correct key would be `common.actions.retry`.

- [ ] [MEDIUM] Story 1.4 | **Duplicate i18n directories committed** | `frontend/i18n/i18n/` and `frontend/app/i18n/i18n/` | 12 duplicate JSON files exist in nested `i18n/i18n/` subdirectories. Likely accidental double-nesting from a copy. These are NOT referenced by `nuxt.config.ts` but pollute the repo.

- [ ] [MEDIUM] Story 1.1 | **i18n-check.js scans wrong directory** | `frontend/scripts/i18n-check.js:4` | `const i18nRoot = './i18n'` — runs from `frontend/` working directory, so checks `frontend/i18n/` not `frontend/app/i18n/` where the actual files live. CI check is scanning the wrong directory.

- [ ] [MEDIUM] Story 1.3 | **TenantSwitchResponse leaks token in JSON response body** | `backend/src/main/java/hu/riskguard/identity/api/dto/TenantSwitchResponse.java:3` | Token is already set as HttpOnly cookie but also returned in JSON response body. Frontend `auth.ts:110` reads it from body. Contradicts HttpOnly cookie security model.

- [ ] [MEDIUM] Story 1.1 | **build.gradle includes both JPA and jOOQ** | `backend/build.gradle:43-44` | `spring-boot-starter-data-jpa` AND `spring-boot-starter-jooq` both present. `spring-modulith-starter-jpa` also pulled in. Hibernate configured with `ddl-auto: validate`. Architecture mandates jOOQ-only persistence — JPA on classpath is an architecture violation.

- [ ] [MEDIUM] Story 1.1 | **Hibernate ORM plugin applied** | `backend/build.gradle:5` | `org.hibernate.orm` Gradle plugin applied with enhancement config (lines 128-131) despite architecture mandating jOOQ-only persistence.

- [ ] [MEDIUM] Story 1.1 | **Hardcoded DB credentials in build.gradle** | `backend/build.gradle:98-100` | `user = 'riskguard'`, `password = 'localdev'` hardcoded in jOOQ codegen config; also in `application.yml:6-7`, `docker-compose.yml:8`, and `ci.yml:19`. Should use environment variables or Gradle properties.

---

## 🟢 LOW (11)

- [ ] [LOW] Story 1.3 | **Cookie secure flag depends on request.isSecure()** | `backend/src/main/java/hu/riskguard/core/security/OAuth2AuthenticationSuccessHandler.java:33` | `secure(request.isSecure())` means cookie is NOT secure behind a reverse proxy that terminates TLS. Should use a config flag or `X-Forwarded-Proto` awareness.

- [ ] [LOW] Story 1.4 | **IdentityController directly imports from internal package** | `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java:10` | Controller directly imports `IdentityRepository` from `internal` package, violating Modulith boundary convention (`api` → `internal` direct dependency). Should go through `domain` service facade.

- [ ] [LOW] Story 1.2 | **User/Tenant domain objects have mutable timestamp defaults** | `backend/src/main/java/hu/riskguard/identity/domain/User.java:20` | `createdAt = OffsetDateTime.now()` set at construction time, not DB insert time. If object is cached or reused, timestamp is wrong. Same in `Tenant.java:15` and `TenantMandate.java:14`.

- [ ] [LOW] Story 1.1 | **RiskGuardProperties.init() silently swallows exceptions** | `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java:31-33` | Empty catch block when loading `risk-guard-tokens.json`. If file is missing or malformed, no logging, falls back silently.

- [ ] [LOW] Story 1.1 | **application.yml security.default-user-role orphaned** | `backend/src/main/resources/application.yml:55` | `default-user-role: SME_ADMIN` is under `risk-guard.security` but `RiskGuardProperties` has it under `Identity` inner class, not `Security`. YAML key is orphaned/ignored.

- [ ] [LOW] Story 1.4 | **ContextGuard.vue logout redirects to wrong path** | `frontend/app/components/Identity/ContextGuard.vue:27` | `navigateTo('/login')` but the login page is at `/auth/login`. Will 404 or hit auth middleware loop.

- [ ] [LOW] Story 1.4 | **ContextGuard error message is hardcoded English** | `frontend/app/components/Identity/ContextGuard.vue:18` | `error.value = 'Switch failed. Please try again or log in.'` should use `$t()` i18n key.

- [ ] [LOW] Story 1.3 | **Duplicate Toast component rendering** | `frontend/app/pages/auth/login.vue:48` | `<Toast />` rendered here AND in `app.vue:6`. Double Toast rendering causes duplicate notifications.

- [ ] [LOW] Story 1.1 | **CI backend test exclusion syntax may not work** | `.github/workflows/ci.yml:64` | `--exclude-task "*IntegrationTest"` is not valid Gradle syntax for excluding test classes. Gradle `--exclude-task` works on task names, not test class patterns.

- [ ] [LOW] Story 1.4 | **default.vue hardcoded English nav labels** | `frontend/app/layouts/default.vue:11-14` | Navigation links "Dashboard", "Screening", "Watchlist", "EPR" are hardcoded English strings, not using `$t()`.

- [ ] [LOW] Story 1.2 | **guest_sessions FK constraint will break guest creation** | `backend/src/main/resources/db/migration/V20260306_001__add_guest_sessions_fk.sql:7-9` | Guest sessions use synthetic tenant IDs (`guest-{session_uuid}`) that don't exist in `tenants` table, but FK is enforced. Guest session creation will fail with FK violation.

---

## Cross-Story Structural Issues

- [ ] [MEDIUM] **Duplicate `risk-guard-tokens.json` in multiple locations** | Root `risk-guard-tokens.json` is the canonical source, but `frontend/risk-guard-tokens.json` and `frontend/app/risk-guard-tokens.json` may also exist as copies. Frontend imports reference relative paths that may not resolve correctly.

- [ ] [MEDIUM] **JPA entities exist alongside jOOQ** | `User.java`, `Tenant.java`, `TenantMandate.java` with JPA annotations exist in `identity/domain/` alongside Spring Data repositories (`UserRepository`, `TenantRepository`, `TenantMandateRepository`). Architecture mandates jOOQ-only. Decide: remove JPA or formalize the hybrid approach.

- [ ] [MEDIUM] **Triple-nested i18n directories** | Git history shows files committed to `frontend/i18n/`, `frontend/app/i18n/`, `frontend/app/i18n/i18n/`, and `frontend/i18n/i18n/`. Only one location should be canonical — clean up the rest.

---

## Summary by Story

| Story | CRITICAL | HIGH | MEDIUM | LOW | Total |
|-------|----------|------|--------|-----|-------|
| 1.1   | 0        | 1    | 4      | 4   | 9     |
| 1.2   | 0        | 4    | 2      | 2   | 8     |
| 1.3   | 2        | 1    | 4      | 2   | 9     |
| 1.4   | 0        | 0    | 2      | 3   | 5     |
| Cross | 0        | 1    | 2      | 0   | 3     |
| **Total** | **2** | **7** | **14** | **11** | **34** |

---

_This file is intended to be used as a checklist in a future dev session. Check off items as they are resolved._
