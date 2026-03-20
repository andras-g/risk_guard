# Story 3.12: Demo Mode & Guest Rate Limiting
> Moved from Story 2.7 on 2026-03-16 — growth/conversion feature fits better in Epic 3.

Status: done

## Story

As a Guest (unauthenticated visitor),
I want to try the product with limited access before signing up,
So that I can evaluate its value with real data before committing to a paid plan.

## Acceptance Criteria

1. **Given** an unauthenticated visitor on the Landing Page, **When** I perform a partner search without logging in, **Then** the system creates a transient `guest_sessions` record with a session fingerprint (browser fingerprint or cookie-based identifier).

2. **Given** a guest session, **Then** I can search up to 10 unique companies and perform 3 instant checks per day (limits sourced from `risk-guard-tokens.json`: `guest.maxCompanies: 10`, `guest.maxDailyChecks: 3`).

3. **Given** a guest session, **Then** the UI displays a progress indicator showing remaining searches (e.g., "7 of 10 companies used").

4. **Given** the daily check limit is reached, **Then** the UI shows a clear, localized message: "Daily limit reached — sign up for unlimited checks" with a prominent registration CTA.

5. **Given** the company limit is reached, **Then** the UI shows: "Demo limit reached — sign up to monitor unlimited partners" with a registration CTA.

6. **Given** expired guest sessions, **Then** a scheduled cleanup job purges them daily.

7. **Given** guest data, **Then** it is stored with a synthetic `tenant_id` (format: `guest-{session_uuid}`) and is never accessible to authenticated users.

## Tasks / Subtasks

- [x] **Task 1: Backend — Guest Session Management** (AC: #1, #6, #7)
  - [x] 1.1 Create `GuestSessionService.java` in `hu.riskguard.identity.domain` — manages guest session lifecycle: create, find-by-fingerprint, check limits, increment counters (renamed from GuestSessionManager per ArchUnit naming convention)
  - [x] 1.2 Add guest session CRUD methods to `IdentityRepository` — findByFingerprint, create, incrementCounters, resetDailyChecks, deleteExpired
  - [x] 1.3 Create session: generate `UUID` as session ID, synthetic `tenant_id` as `UUID.nameUUIDFromBytes("guest-{sessionId}".getBytes())`, set `expires_at` to 24h from creation
  - [x] 1.4 Session fingerprint: accept browser fingerprint hash from frontend (crypto.randomUUID stored in localStorage) — stored in `session_fingerprint` column
  - [x] 1.5 Scheduled cleanup: `@Scheduled(cron = "0 0 3 * * *")` job to `DELETE FROM guest_sessions WHERE expires_at < NOW()` — runs at 3 AM daily
  - [x] 1.6 Daily check reset: when `created_at` is on a different day than the current request, reset `daily_checks` to 0 (compares LocalDate, no extra migration needed)

- [x] **Task 2: Backend — Guest Rate Limiting Endpoint** (AC: #2, #4, #5)
  - [x] 2.1 Create `POST /api/v1/public/guest/search` endpoint via `GuestSearchController` — accepts `taxNumber` + `sessionFingerprint`, returns verdict + guest usage stats
  - [x] 2.2 Load limits from `RiskGuardProperties.Guest` (bound from `risk-guard-tokens.json` / `application.yml`) — `guest.maxCompanies` (10) and `guest.maxDailyChecks` (3)
  - [x] 2.3 Check `companies_checked >= maxCompanies` → return 429 with `COMPANY_LIMIT_REACHED` error code
  - [x] 2.4 Check `daily_checks >= maxDailyChecks` → return 429 with `DAILY_LIMIT_REACHED` error code
  - [x] 2.5 On successful search: increment `companies_checked` (only if new unique tax number) and `daily_checks`, call `ScreeningService.search()` with the synthetic guest `tenant_id`
  - [x] 2.6 Return `GuestSearchResponse` record with verdict data + `companiesUsed`, `companiesLimit`, `dailyChecksUsed`, `dailyChecksLimit` fields
  - [x] 2.7 Verified `/api/v1/public/**` in SecurityConfig's `permitAll()` and `PUBLIC_PATH_PREFIXES` covers the guest endpoint — no changes needed

- [x] **Task 3: Backend — Guest Tenant Isolation** (AC: #7)
  - [x] 3.1 Synthetic `tenant_id` for guests: use `UUID.nameUUIDFromBytes(("guest-" + sessionId).getBytes())` — deterministic, unique per session
  - [x] 3.2 Verified `TenantFilter` gracefully handles guest requests (no JWT → no authentication → TenantContext stays null). Guest endpoint manually sets `TenantContext.setCurrentTenant(syntheticTenantId)` with try/finally clear.
  - [x] 3.3 Added integration test: authenticated user query NEVER returns guest session data (TenantJooqListener enforces tenant_id filtering)
  - [x] 3.4 Guest snapshots and verdicts use the synthetic tenant_id — they are only visible within the same guest session

- [x] **Task 4: Frontend — Guest Search Flow** (AC: #1, #3, #4, #5)
  - [x] 4.1 Create `useGuestSession` composable in `frontend/app/composables/auth/` — manages guest session fingerprint (stored in localStorage), calls guest search endpoint, tracks usage stats
  - [x] 4.2 Modified `SearchBar.vue` — when user is NOT authenticated (`!authStore.isAuthenticated`), use guest search endpoint instead of authenticated endpoint
  - [x] 4.3 Display progress indicator: PrimeVue `ProgressBar` showing "{used} of {limit} companies used" near the search bar (only for guests)
  - [x] 4.4 Handle 429 `COMPANY_LIMIT_REACHED`: show localized "Demo limit reached — sign up to monitor unlimited partners" + registration CTA (NuxtLink to /auth/register)
  - [x] 4.5 Handle 429 `DAILY_LIMIT_REACHED`: show localized "Daily limit reached — sign up for unlimited checks" + registration CTA
  - [x] 4.6 Guest fingerprint: `crypto.randomUUID()` stored in localStorage as key `rg_guest_token` — simple, privacy-preserving, no PII

- [x] **Task 5: Frontend — i18n for Guest Mode** (AC: #3, #4, #5)
  - [x] 5.1 Add `screening.guest.*` keys to `hu/screening.json`: `companyLimitReached`, `dailyLimitReached`, `progressIndicator`, `signUpCta`, `signUpForUnlimited`
  - [x] 5.2 Add matching English keys to `en/screening.json`
  - [x] 5.3 Keys alphabetically sorted per project convention (guest section inserted between "freshness" and existing sections)

- [x] **Task 6: Tests** (AC: all)
  - [x] 6.1 `GuestSessionServiceTest.java` — 10 unit tests: session creation, fingerprint lookup, limit checks (OK/company/daily), counter increment (new/existing), daily reset logic, synthetic tenant ID determinism
  - [x] 6.2 `GuestSearchEndpointTest.java` — 5 unit tests: successful guest search, 429 on company limit, 429 on daily limit, session creation on first search, existing company search
  - [x] 6.3 `TenantIsolationIntegrationTest.java` — added test: authenticated user query NEVER returns guest session data
  - [x] 6.4 `useGuestSession.spec.ts` — 6 composable tests: fingerprint persistence (new/reuse), usage tracking after search, limit detection (company/daily/network error)
  - [x] 6.5 `SearchBar.spec.ts` — 4 guest-mode tests: guest search routing, progress indicator calculation, company limit detection, daily limit detection

- [x] **Review Follow-ups (AI)** — Code Review 2026-03-20
  - [x] [AI-Review][HIGH] `GuestSearchController` imports `GuestSessionService.GuestSession` and `GuestSessionService.LimitStatus` directly — module boundary violation. Move these types to standalone classes in `identity.domain` or `identity.api.dto` and export through `IdentityService` facade only. [GuestSearchController.java:4-5]
  - [x] [AI-Review][HIGH] `IdentityRepository.guestHasSearchedTaxNumber()` queries `company_snapshots` table (owned by `screening` module) using raw `DSL.table()` — cross-module table access violation. Move this check to `GuestSearchController` (in `screening` module) or call `ScreeningService` facade. [IdentityRepository.java:262-270]
  - [x] [AI-Review][HIGH] `GuestSearchController.guestSearch()` has a TOCTOU race condition on limit checks — concurrent requests for the same fingerprint can exceed company/daily limits. Add `SELECT ... FOR UPDATE` or atomic DB-level limit enforcement. [GuestSearchController.java:66-99]
  - [x] [AI-Review][MEDIUM] `api.d.ts` manually defines `GuestSearchRequest`/`GuestSearchResponse` interfaces — violates "NEVER define interfaces for backend data manually" rule. Add TODO comment or generate from OpenAPI spec. [api.d.ts:40-66]
  - [x] [AI-Review][MEDIUM] `SearchBar.spec.ts` guest-mode tests (lines 98-135) test JavaScript operators, not component rendering. Rewrite as `@vue/test-utils` mount tests verifying DOM output (`data-testid="guest-progress"`, limit messages, CTA links). [SearchBar.spec.ts:98-135]
  - [x] [AI-Review][MEDIUM] `useGuestSession.spec.ts` mocks `ref()`/`computed()` as plain objects — reactivity doesn't work, assertions may be false positives. Use `@nuxt/test-utils` or proper Vue reactivity. [useGuestSession.spec.ts:15-17]
  - [x] [AI-Review][LOW] `GuestSessionService` uses `OffsetDateTime.now()` directly — inject `java.time.Clock` for testable time-dependent logic (daily reset edge cases). [GuestSessionService.java:81,164]
  - [x] [AI-Review][LOW] `GuestSearchController` 429 error responses use untyped `Map.of()` — create `GuestLimitResponse` record in `screening.api.dto` with `from()` factory per DTO conventions. [GuestSearchController.java:70-83]

- [x] **Review Follow-ups (AI)** — Code Review #2 2026-03-20
  - [x] [AI-Review][HIGH] `IdentityRepository` guest methods use `OffsetDateTime.now()` directly instead of injected `Clock` — bypasses the Clock injection added in GuestSessionService, making expiration checks non-deterministic in tests. Inject `Clock` into repository. [IdentityRepository.java:205,270]
  - [x] [AI-Review][HIGH] `IdentityService` guest facade methods lack `@Transactional` annotations — inconsistent with ALL other methods in the class. Added `@Transactional` / `@Transactional(readOnly = true)` for consistency. [IdentityService.java:161-177]
  - [x] [AI-Review][HIGH] `GuestSearchController.incrementGuestCounters()` called inside `TenantContext` try/finally block — counter updates don't need tenant context (use session ID). Moved outside block to prevent context leakage. [GuestSearchController.java:107]
  - [x] [AI-Review][MEDIUM] `GuestSessionServiceTest` creates test sessions with `OffsetDateTime.now()` (system clock) but service uses `FIXED_CLOCK` — potential midnight-boundary flake. Changed to `OffsetDateTime.now(FIXED_CLOCK)`. [GuestSessionServiceTest.java:86,111,131,262]
  - [x] [AI-Review][MEDIUM] `Clock.systemDefaultZone()` — timezone-dependent daily reset behavior. Changed to `Clock.system(ZoneId.of("Europe/Budapest"))` for consistent business logic across environments. [RiskGuardApplication.java:37]
  - [x] [AI-Review][MEDIUM] `useGuestSession` composable creates new reactive state per call — multiple callers get independent state. Extracted to module-level singleton refs. [useGuestSession.ts:35-44]
  - [x] [AI-Review][LOW] `GuestSearchEndpointTest` creates test data with `OffsetDateTime.now()` — non-deterministic. Changed to fixed timestamp. [GuestSearchEndpointTest.java:216-227]


## Dev Notes

### Critical Context — What This Story Builds

Story 2.7 implements the **guest rate limiting layer** that allows unauthenticated visitors to try the product before signing up. The `guest_sessions` table already exists (created in `V20260305_001__create_identity_tables.sql`) with columns: `id`, `tenant_id`, `session_fingerprint`, `companies_checked`, `daily_checks`, `created_at`, `expires_at`.

The limits are already defined in `risk-guard-tokens.json`:
```json
"guest": {
  "maxCompanies": 10,
  "maxDailyChecks": 3,
  "captchaAfterChecks": 3
}
```

**What is NOT in this story:**
- The public SEO page (Story 2.6 handles that)
- The Landing Page (Story 3.0b handles that)
- CAPTCHA integration (the `captchaAfterChecks` token is defined but CAPTCHA implementation is deferred)
- Feature flag tier gating (Story 3.3 handles that)

### Architecture Compliance — CRITICAL

**1. Guest Endpoint Security**

`SecurityConfig.java` already has `"/api/public/**"` in `permitAll()` and in `PUBLIC_PATH_PREFIXES`. The guest endpoint at `/api/v1/public/guest/search` will be covered by the `/api/public/**` pattern — BUT check the pattern match! The current config has `"/api/public/**"` (no `v1`), while Story 2.6 adds `"/api/v1/public/**"`. Ensure BOTH patterns are present, or standardize on one.

**Current SecurityConfig patterns:**
```java
.requestMatchers(
    "/api/public/**", "/actuator/**", "/v3/api-docs/**",
    "/swagger-ui/**", "/login/**", "/oauth2/**", "/error"
).permitAll()
```

If the guest endpoint uses `/api/v1/public/guest/search`, the `/api/public/**` pattern will NOT match it (it's `/api/v1/public/**`). Story 2.6 should add `/api/v1/public/**` — coordinate with that story or verify the final pattern.

**2. Guest TenantContext Setup**

For guest requests, there's no JWT → `TenantFilter` won't set `TenantContext`. The guest endpoint must MANUALLY set the synthetic tenant ID before calling `ScreeningService.search()`:

```java
UUID syntheticTenantId = UUID.nameUUIDFromBytes(("guest-" + sessionId).getBytes());
TenantContext.setCurrentTenant(syntheticTenantId);
try {
    // ... call screening service
} finally {
    TenantContext.clear();
}
```

**3. Module Boundary — Guest Sessions owned by `identity` module**

Per architecture, `guest_sessions` table is owned by the `identity` module. `GuestSessionManager` must live in `identity.domain`. The guest search endpoint should call `IdentityService` (facade) to manage sessions, then call `ScreeningService` (facade) to perform the search. Cross-module via facades only.

**4. No Bucket4j for Guest Rate Limiting**

The guest rate limiting in this story is APPLICATION-LEVEL (check `companies_checked` and `daily_checks` columns in the DB), NOT infrastructure-level (Bucket4j). Bucket4j rate limiting (per-IP request throttling) is a separate concern and is not part of this story. The distinction:
- **This story:** "You've searched 10 companies, sign up to continue" (business limit)
- **Future:** "Too many requests per second from your IP" (DDoS protection via Bucket4j)

### Existing Code Reference

| File | Path | Relevance |
|------|------|-----------|
| `IdentityService.java` | `backend/.../identity/domain/IdentityService.java` | **MODIFYING** — add guest session facade methods |
| `IdentityRepository.java` | `backend/.../identity/internal/IdentityRepository.java` | **MODIFYING** — add guest session CRUD |
| `SecurityConfig.java` | `backend/.../core/config/SecurityConfig.java` | **CHECK** — verify `/api/v1/public/**` covers guest endpoints |
| `TenantFilter.java` | `backend/.../core/security/TenantFilter.java` | **READ** — confirm null tenant gracefully handled for guests |
| `TenantContext.java` | `backend/.../core/security/TenantContext.java` | **READ** — understand how to manually set tenant for guest |
| `ScreeningService.java` | `backend/.../screening/domain/ScreeningService.java` | **READ** — guest search calls this via facade |
| `SearchBar.vue` | `frontend/app/components/Screening/SearchBar.vue` | **MODIFYING** — add guest mode detection |
| `risk-guard-tokens.json` | `frontend/app/risk-guard-tokens.json` | **READ** — guest limits already defined |
| `V20260305_001` | `backend/.../db/migration/V20260305_001__create_identity_tables.sql` | **READ** — `guest_sessions` table already exists |

### DANGER ZONES — Common LLM Mistakes to Avoid

1. **DO NOT create a separate `guest_sessions` migration.** The table already exists in `V20260305_001`. Only add a migration if you need to add columns (e.g., `daily_reset_at`).

2. **DO NOT use Bucket4j for the guest search limit.** This story implements APPLICATION-LEVEL rate limiting via DB counters, not per-IP throttling. Bucket4j is for DDoS protection.

3. **DO NOT hardcode limit numbers.** Read from `risk-guard-tokens.json` (frontend) and `RiskGuardProperties` (backend). The limits are: 10 companies, 3 checks/day.

4. **DO NOT let guest snapshots bleed into authenticated user queries.** The synthetic tenant UUID ensures isolation — but verify with a test that authenticated queries never return guest data.

5. **DO NOT store PII in `session_fingerprint`.** Store a hash (SHA-256 of User-Agent + IP or a random token), not the raw values. The fingerprint is for session continuity, not tracking.

6. **DO NOT forget to clear `TenantContext` in the finally block.** If you manually set it for guest requests, you MUST clear it after — otherwise the context leaks to subsequent requests on the same thread (virtual threads mitigate this, but be safe).

7. **DO NOT create a `GuestController` in the `identity` module.** The search endpoint belongs in `ScreeningController` (or a new `PublicScreeningController`). It CALLS the identity facade for session management and the screening facade for search.

8. **DO NOT use `HttpSession` for guest tracking.** The backend is stateless (JWT-based). Guest session state lives in the `guest_sessions` DB table + a fingerprint sent by the frontend.

9. **DO NOT add the guest progress indicator to authenticated pages.** The progress bar ("7 of 10 companies used") only shows for guests, never for logged-in users.

10. **DO NOT forget the daily check reset logic.** `daily_checks` must reset to 0 at midnight (or when the first request of a new day arrives). Consider adding a `daily_reset_at` column via migration to track when the counter was last reset.

### New Files to Create

```
backend/src/main/java/hu/riskguard/identity/domain/
  GuestSessionManager.java                          # NEW — guest session lifecycle management

backend/src/main/java/hu/riskguard/screening/api/dto/
  GuestSearchResponse.java                          # NEW — verdict + usage stats DTO

backend/src/test/java/hu/riskguard/identity/
  GuestSessionManagerTest.java                      # NEW — guest session unit tests

backend/src/test/java/hu/riskguard/screening/api/
  GuestSearchEndpointTest.java                      # NEW — guest endpoint integration tests

frontend/app/composables/auth/
  useGuestSession.ts                                # NEW — guest session composable
  useGuestSession.spec.ts                           # NEW — co-located test
```

### Modified Files

```
backend/src/main/java/hu/riskguard/identity/domain/
  IdentityService.java                   # Add guest session facade methods

backend/src/main/java/hu/riskguard/identity/internal/
  IdentityRepository.java                # Add guest session CRUD queries

backend/src/main/java/hu/riskguard/screening/api/
  ScreeningController.java               # Add POST /api/v1/public/guest/search

frontend/app/components/Screening/
  SearchBar.vue                          # Guest mode detection + progress indicator

frontend/app/i18n/hu/
  screening.json                         # Add screening.guest.* keys

frontend/app/i18n/en/
  screening.json                         # Add screening.guest.* keys

frontend/types/
  api.d.ts                               # Add GuestSearchResponse interface
```

### Database Notes

The `guest_sessions` table already exists with this schema:
```sql
guest_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,          -- synthetic tenant ID (guest-{session_uuid})
    session_fingerprint VARCHAR(255) NOT NULL,
    companies_checked INT NOT NULL DEFAULT 0,
    daily_checks INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
)
```

Consider adding a migration for `daily_reset_at` if the daily check reset logic requires it:
```sql
-- V20260316_001__add_daily_reset_at_to_guest_sessions.sql
ALTER TABLE guest_sessions ADD COLUMN IF NOT EXISTS daily_reset_at DATE NOT NULL DEFAULT CURRENT_DATE;
```

### Previous Story Intelligence (Story 2.6 — in parallel)

1. Story 2.6 adds `/api/v1/public/**` permit-all to SecurityConfig — coordinate patterns
2. Story 2.6 creates `public.vue` layout — guest search may reuse this layout for the demo page
3. Story 2.6 creates `PublicCompanyResponse` DTO — guest search returns `VerdictResponse` + usage stats (different DTO)
4. Both stories touch `SecurityConfig.java` and `ScreeningController.java` — be aware of merge conflicts

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.7] Story AC: guest sessions, 10 company / 3 check limits, progress indicator, limit CTAs, session cleanup, synthetic tenant isolation
- [Source: _bmad-output/planning-artifacts/architecture.md#identity-module] GuestSessionManager in identity.domain, guest_sessions table
- [Source: _bmad-output/planning-artifacts/architecture.md#Data-Boundaries] Guest sessions use synthetic tenant IDs (`guest-{session_uuid}`)
- [Source: frontend/app/risk-guard-tokens.json] Guest limits: maxCompanies: 10, maxDailyChecks: 3, captchaAfterChecks: 3
- [Source: V20260305_001__create_identity_tables.sql] guest_sessions table already exists with schema
- [Source: _bmad-output/project-context.md] Tokenized constants from risk-guard-tokens.json, no hardcoded limits

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- ArchUnit naming convention violation: `GuestSessionManager` renamed to `GuestSessionService` (all `@Service` classes must end with `Service` suffix per architecture rule)
- SecurityConfig verification: `/api/v1/public/**` already in both `PUBLIC_PATH_PREFIXES` and `permitAll()` — no changes needed
- TenantJooqListener: `guest_sessions` already in `isTenantAwareQuery()` check — synthetic tenant IDs provide automatic isolation
- Daily reset approach: compare `created_at.toLocalDate()` vs `LocalDate.now()` — no `daily_reset_at` migration needed
- `nuxi typecheck` has pre-existing Vite/Rollup type incompatibility (not caused by this story); `tsc --noEmit` passes clean
- Code review follow-ups: Moved `GuestSession`/`LimitStatus` from inner types to standalone classes `GuestSession.java`/`GuestLimitStatus.java` in identity.domain
- Removed `guestHasSearchedTaxNumber()` from `IdentityRepository` (was querying screening module's `company_snapshots` table via raw `DSL.table()`) — replaced with `ScreeningService.hasSnapshotForTenant()` → `ScreeningRepository.existsSnapshotByTenantAndTaxNumber()` using type-safe jOOQ
- TOCTOU race fix: `findGuestSessionByFingerprint` renamed to `findGuestSessionByFingerprintForUpdate` with `SELECT ... FOR UPDATE` row-level lock
- Injected `java.time.Clock` into `GuestSessionService` for deterministic time-dependent tests; added `Clock` bean to `RiskGuardApplication`
- Created `GuestLimitResponse` record in `screening.api.dto` with `from()` factory per ArchUnit DTO conventions — replaced untyped `Map.of()` in 429 responses
- ArchUnit `NamingConventionTest` caught missing `from()` factory on `GuestLimitResponse` — added `static from()` method
- Frontend: `useGuestSession.spec.ts` now uses real Vue `ref()`/`computed()`/`readonly()` from `vue` import instead of plain object stubs
- Frontend: `SearchBar.spec.ts` guest-mode tests rewritten as `@vue/test-utils` mount tests verifying actual DOM (`data-testid="guest-progress"`, limit messages, CTA links)
- `api.d.ts`: Added TODO comment to remove manual Guest DTOs once CI OpenAPI pipeline regenerates; added `GuestLimitResponse` interface

### Completion Notes List

- Implemented guest session lifecycle management via `GuestSessionService` (identity domain) with facade methods on `IdentityService`
- Created `GuestSearchController` in screening.api module — calls identity facade for sessions, screening facade for search
- Guest endpoint manually sets/clears `TenantContext` in try/finally block for tenant isolation
- Application-level rate limiting via DB counters (not Bucket4j) — checks `companies_checked` and `daily_checks` columns
- Frontend `useGuestSession` composable manages guest fingerprint (crypto.randomUUID in localStorage), API calls, and usage tracking
- SearchBar.vue detects guest mode via `!authStore.isAuthenticated` and switches between authenticated/guest search flows
- PrimeVue ProgressBar shows guest usage (only visible for unauthenticated users)
- 429 responses with `COMPANY_LIMIT_REACHED` / `DAILY_LIMIT_REACHED` error codes trigger localized CTA messages
- All 452+ backend tests pass (including ArchUnit, Modulith verification, integration tests)
- All 472 frontend tests pass (Vitest)
- `tsc --noEmit` passes clean
- ✅ Resolved review finding [HIGH]: Module boundary — GuestSession/LimitStatus extracted to standalone types
- ✅ Resolved review finding [HIGH]: Cross-module table access — moved company_snapshots query to ScreeningService facade
- ✅ Resolved review finding [HIGH]: TOCTOU race — added SELECT ... FOR UPDATE row-level lock
- ✅ Resolved review finding [MEDIUM]: api.d.ts — added TODO comment for OpenAPI generation + GuestLimitResponse interface
- ✅ Resolved review finding [MEDIUM]: SearchBar.spec.ts — rewritten as @vue/test-utils mount tests
- ✅ Resolved review finding [MEDIUM]: useGuestSession.spec.ts — uses real Vue reactivity (ref/computed/readonly)
- ✅ Resolved review finding [LOW]: Clock injection — java.time.Clock injected into GuestSessionService
- ✅ Resolved review finding [LOW]: GuestLimitResponse DTO — typed record with from() factory replaces Map.of()

### File List

**New files:**
- `backend/src/main/java/hu/riskguard/identity/domain/GuestSessionService.java`
- `backend/src/main/java/hu/riskguard/identity/domain/GuestSession.java` — standalone domain record (extracted from GuestSessionService inner type)
- `backend/src/main/java/hu/riskguard/identity/domain/GuestLimitStatus.java` — standalone enum (extracted from GuestSessionService inner type)
- `backend/src/main/java/hu/riskguard/screening/api/GuestSearchController.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/GuestSearchRequest.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/GuestSearchResponse.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/GuestLimitResponse.java` — typed 429 response DTO with from() factory
- `backend/src/test/java/hu/riskguard/identity/domain/GuestSessionServiceTest.java`
- `backend/src/test/java/hu/riskguard/screening/api/GuestSearchEndpointTest.java`
- `frontend/app/composables/auth/useGuestSession.ts`
- `frontend/app/composables/auth/useGuestSession.spec.ts`

**Modified files:**
- `backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java` — guest session facade methods; uses standalone GuestSession/GuestLimitStatus types
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java` — guest session CRUD; removed cross-module guestHasSearchedTaxNumber(); renamed findGuestSessionByFingerprint to findGuestSessionByFingerprintForUpdate with FOR UPDATE lock
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — added hasSnapshotForTenant() facade method
- `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java` — added existsSnapshotByTenantAndTaxNumber()
- `backend/src/main/java/hu/riskguard/RiskGuardApplication.java` — added Clock bean
- `backend/src/test/java/hu/riskguard/identity/domain/IdentityServiceTest.java` — added GuestSessionService mock
- `backend/src/test/java/hu/riskguard/identity/domain/GuestSessionServiceTest.java` — uses Clock injection, standalone types
- `backend/src/test/java/hu/riskguard/screening/api/GuestSearchEndpointTest.java` — uses standalone types, GuestLimitResponse, ScreeningService.hasSnapshotForTenant
- `backend/src/test/java/hu/riskguard/identity/TenantIsolationIntegrationTest.java` — guest data isolation test
- `frontend/app/components/Screening/SearchBar.vue` — guest mode detection, progress indicator, limit messages
- `frontend/app/components/Screening/SearchBar.spec.ts` — rewritten guest-mode tests as @vue/test-utils mount tests
- `frontend/app/composables/auth/useGuestSession.spec.ts` — fixed reactivity mocks to use real Vue ref/computed/readonly
- `frontend/app/i18n/hu/screening.json` — added screening.guest.* keys
- `frontend/app/i18n/en/screening.json` — added screening.guest.* keys
- `frontend/types/api.d.ts` — added Guest DTOs + GuestLimitResponse interface + TODO comment
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — status: ready-for-dev → in-progress → review

### Change Log

- 2026-03-20: Implemented guest session management (GuestSessionService), rate-limiting endpoint (GuestSearchController), tenant isolation, frontend guest search flow (useGuestSession composable + SearchBar.vue), i18n keys, and comprehensive test suite (25 backend + 10 frontend tests added). All tasks complete. Status → review.
- 2026-03-20: **AI Code Review** — 8 issues found (3 HIGH, 3 MEDIUM, 2 LOW). Key findings: module boundary violation (GuestSearchController imports identity domain types directly), cross-module table access (IdentityRepository queries company_snapshots), TOCTOU race condition on limit checks, weak frontend test coverage. 8 action items created. Status → in-progress.
- 2026-03-20: Addressed code review findings — 8/8 items resolved. Key changes: extracted GuestSession/GuestLimitStatus to standalone types, moved company_snapshots query to ScreeningService facade, added SELECT...FOR UPDATE for TOCTOU race prevention, injected java.time.Clock, created GuestLimitResponse DTO, rewrote frontend tests with real Vue reactivity and @vue/test-utils mount tests. All 452 backend + 472 frontend tests pass. Status → review.
- 2026-03-20: **AI Code Review #2** — 7 issues found (3 HIGH, 3 MEDIUM, 1 LOW). All 7 auto-fixed: Clock injected into IdentityRepository, @Transactional added to IdentityService guest facade, incrementGuestCounters moved outside TenantContext block, test timestamps fixed to use FIXED_CLOCK, Clock.system(Budapest) for consistent TZ, useGuestSession singleton state pattern, GuestSearchEndpointTest deterministic timestamps. All 452 backend + 472 frontend tests pass. Status → done.

