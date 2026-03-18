# Story 3.12: Demo Mode & Guest Rate Limiting
> Moved from Story 2.7 on 2026-03-16 — growth/conversion feature fits better in Epic 3.

Status: ready-for-dev

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

- [ ] **Task 1: Backend — Guest Session Management** (AC: #1, #6, #7)
  - [ ] 1.1 Create `GuestSessionManager.java` in `hu.riskguard.identity.domain` — manages guest session lifecycle: create, find-by-fingerprint, check limits, increment counters
  - [ ] 1.2 Add `GuestSessionRepository` methods to `IdentityRepository` or create a dedicated repository — CRUD for `guest_sessions` table (already exists in DB schema)
  - [ ] 1.3 Create session: generate `UUID` as session ID, synthetic `tenant_id` as `UUID.nameUUIDFromBytes("guest-{sessionId}".getBytes())`, set `expires_at` to 24h from creation
  - [ ] 1.4 Session fingerprint: accept browser fingerprint hash from frontend (IP + User-Agent hash as fallback) — stored in `session_fingerprint` column
  - [ ] 1.5 Scheduled cleanup: `@Scheduled(cron = "0 0 3 * * *")` job to `DELETE FROM guest_sessions WHERE expires_at < NOW()` — runs at 3 AM daily
  - [ ] 1.6 Daily check reset: when `created_at` is on a different day than the current request, reset `daily_checks` to 0 (or use a separate `daily_reset_at` column via migration)

- [ ] **Task 2: Backend — Guest Rate Limiting Endpoint** (AC: #2, #4, #5)
  - [ ] 2.1 Create `POST /api/v1/public/guest/search` endpoint — accepts `taxNumber` + `sessionFingerprint`, returns verdict + guest usage stats
  - [ ] 2.2 Load limits from `risk-guard-tokens.json` or `RiskGuardProperties` — `guest.maxCompanies` (10) and `guest.maxDailyChecks` (3)
  - [ ] 2.3 Check `companies_checked >= maxCompanies` → return 429 with `COMPANY_LIMIT_REACHED` error code
  - [ ] 2.4 Check `daily_checks >= maxDailyChecks` → return 429 with `DAILY_LIMIT_REACHED` error code
  - [ ] 2.5 On successful search: increment `companies_checked` (only if new unique tax number) and `daily_checks`, call `ScreeningService.search()` with the synthetic guest `tenant_id`
  - [ ] 2.6 Return `GuestSearchResponse` record with verdict data + `companiesUsed`, `companiesLimit`, `dailyChecksUsed`, `dailyChecksLimit` fields
  - [ ] 2.7 Add `/api/v1/public/guest/**` to the `permitAll()` list in SecurityConfig (already partially covered by `/api/public/**` pattern — verify match)

- [ ] **Task 3: Backend — Guest Tenant Isolation** (AC: #7)
  - [ ] 3.1 Synthetic `tenant_id` for guests: use `UUID.nameUUIDFromBytes(("guest-" + sessionId).getBytes())` — deterministic, unique per session
  - [ ] 3.2 Verify `TenantFilter` gracefully handles guest requests (no JWT → no tenant context set → guest endpoint sets context manually via `TenantContext.setCurrentTenant(syntheticTenantId)`)
  - [ ] 3.3 Ensure authenticated user queries NEVER return guest tenant data — the `requireTenantId()` in repositories already prevents this since authenticated users always have a real tenant UUID
  - [ ] 3.4 Guest snapshots and verdicts use the synthetic tenant_id — they are only visible within the same guest session

- [ ] **Task 4: Frontend — Guest Search Flow** (AC: #1, #3, #4, #5)
  - [ ] 4.1 Create `useGuestSession` composable in `frontend/app/composables/auth/` — manages guest session fingerprint (stored in localStorage), calls guest search endpoint, tracks usage stats
  - [ ] 4.2 Modify `SearchBar.vue` or create `GuestSearchBar.vue` — when user is NOT authenticated, use guest search endpoint instead of authenticated endpoint
  - [ ] 4.3 Display progress indicator: "X of 10 companies used" — PrimeVue `ProgressBar` or simple text indicator near the search bar
  - [ ] 4.4 Handle 429 `COMPANY_LIMIT_REACHED`: show localized "Demo limit reached — sign up to monitor unlimited partners" + registration CTA
  - [ ] 4.5 Handle 429 `DAILY_LIMIT_REACHED`: show localized "Daily limit reached — sign up for unlimited checks" + registration CTA
  - [ ] 4.6 Guest fingerprint: use a combination of `navigator.userAgent` + screen resolution + timezone hash (or a simple `crypto.randomUUID()` stored in localStorage as a "guest token")

- [ ] **Task 5: Frontend — i18n for Guest Mode** (AC: #3, #4, #5)
  - [ ] 5.1 Add `screening.guest.*` keys to `hu/screening.json`: `progressIndicator`, `dailyLimitReached`, `companyLimitReached`, `signUpCta`, `signUpForUnlimited`
  - [ ] 5.2 Add matching English keys to `en/screening.json`
  - [ ] 5.3 Keys must be alphabetically sorted per project convention

- [ ] **Task 6: Tests** (AC: all)
  - [ ] 6.1 `GuestSessionManagerTest.java` — unit tests: session creation, fingerprint lookup, limit checks (company limit, daily limit), counter increment, daily reset logic
  - [ ] 6.2 `GuestSearchEndpointTest.java` — integration test: successful guest search, 429 on company limit, 429 on daily limit, session creation on first search
  - [ ] 6.3 `TenantIsolationIntegrationTest.java` — add test: authenticated user query NEVER returns guest session data
  - [ ] 6.4 `useGuestSession.spec.ts` — composable test: fingerprint persistence, usage tracking, limit detection
  - [ ] 6.5 Frontend component test: progress indicator renders, limit messages render, CTA links to registration


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

gitlab/duo-chat-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

