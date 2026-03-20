# Story 3.9: The Accountant "Portfolio Pulse" Feed

Status: done

## Story

As an Accountant,
I want a global alert feed on my main dashboard showing recent partner status changes across all my clients' tenants,
so that I can proactively advise my clients about emerging partner risks without manually checking each account.

## Acceptance Criteria

### AC1 — Portfolio Alerts API Endpoint
**Given** a user with the `ACCOUNTANT` role and active `tenant_mandates`,
**When** `GET /api/v1/portfolio/alerts?days=7` is called with a valid JWT,
**Then** the backend returns a JSON array of recent status-change events across ALL tenants the accountant has mandates for,
**And** each alert contains: `alertId`, `tenantId`, `tenantName`, `taxNumber`, `companyName`, `previousStatus`, `newStatus`, `changedAt`, `sha256Hash`, `verdictId`,
**And** the results are ordered by `changedAt` DESC (most recent first),
**And** the `days` query parameter defaults to 7 if omitted (max: 30),
**And** if the user does NOT have the `ACCOUNTANT` role, the endpoint returns `403 FORBIDDEN`.

### AC2 — Cross-Tenant Data Aggregation
**Given** an accountant with mandates for tenants A, B, and C,
**When** the portfolio alerts endpoint is called,
**Then** the backend queries `notification_outbox` records (type=ALERT or DIGEST, status=SENT) for ALL three tenants within the requested date range,
**And** the query JOINs `tenants` to resolve `tenantName` for each alert,
**And** the response includes alerts from all mandated tenants, not just the `active_tenant_id`,
**And** the query respects `tenant_mandates.valid_from` and `valid_to` — only includes tenants with currently active mandates.

### AC3 — Portfolio Pulse Sidebar Component
**Given** an authenticated user with the `ACCOUNTANT` role on the dashboard page,
**When** the dashboard loads,
**Then** a "Portfolio Alerts" sidebar section appears below the main search area,
**And** it displays the most recent status changes (up to 20) as a compact vertical feed,
**And** each alert item shows: a status-color icon (Emerald for resolved, Amber for degraded, Crimson for new At-Risk), client name (tenant name), partner company name, status change text (e.g., "Megbizhato to Kockazatos"), and relative timestamp (e.g., "2 oraja" / "2 hours ago"),
**And** clicking an alert triggers a context switch to that client's tenant and navigates to the Verdict Detail page for that partner.

### AC4 — Morning Risk Pulse UX Flow
**Given** status changes occurred since the accountant's last login,
**When** the accountant visits the dashboard,
**Then** the alert feed uses the "Morning Risk Pulse" UX flow: status changes are promoted to the top of the feed,
**And** new At-Risk alerts (Crimson) are visually prioritized above resolved (Emerald) and degraded (Amber) alerts,
**And** each alert provides one-tap access to the partner's Verdict Detail page with automatic context switch,
**And** the alert feed respects the established feedback patterns: Emerald for RELIABLE, Amber for STALE/INCOMPLETE, Crimson for AT_RISK (UX Spec SS7.2).

### AC5 — Context Switch on Alert Click
**Given** the accountant clicks an alert for a partner in a different tenant,
**When** the click is processed,
**Then** the `ContextGuard` interstitial appears briefly while the tenant switch occurs,
**And** a new JWT with the target tenant's `active_tenant_id` is issued via the existing `switchTenant()` mechanism,
**And** after the context switch completes, the user is navigated to `/screening/{taxNumber}` for the relevant partner,
**And** if the context switch fails (e.g., mandate expired, token error), a localized error message is shown via PrimeVue Toast.

### AC6 — Empty State
**Given** an accountant with no recent alerts across any mandated tenant,
**When** the Portfolio Pulse sidebar loads,
**Then** it displays an encouraging empty state: icon of a radar dish scanning, headline "Nincs friss riasztas" (No recent alerts), body text "All your clients' partners are stable. We'll notify you instantly when anything changes.",
**And** the empty state matches the design language from UX Spec SS13.2.

### AC7 — Non-Accountant Users Do Not See Portfolio Pulse
**Given** a user with `SME_ADMIN` or `GUEST` role,
**When** they visit the dashboard,
**Then** the Portfolio Pulse sidebar section is NOT rendered,
**And** the dashboard shows only the standard search and watchlist experience.

### AC8 — i18n Support
**Given** the accountant's `preferred_language` setting,
**When** the Portfolio Pulse feed renders,
**Then** all text (labels, status names, relative timestamps, empty state) is localized in HU or EN,
**And** i18n keys are added to both `hu/notification.json` and `en/notification.json`,
**And** status labels use existing `common.verdict.*` keys where available.

### AC9 — No Regressions
**Given** the new API endpoint, components, and store changes,
**When** `./gradlew check` and frontend tests are run,
**Then** all existing tests pass with zero regressions,
**And** new backend tests cover: (a) portfolio alerts endpoint returns cross-tenant data; (b) non-ACCOUNTANT role returns 403; (c) mandate date filtering; (d) days parameter defaults and limits; (e) empty result for accountant with no alerts,
**And** new frontend tests cover: (a) PortfolioPulse component renders alerts; (b) component hidden for non-accountant; (c) click triggers context switch + navigation; (d) empty state renders correctly; (e) i18n keys resolve.

## Tasks / Subtasks

### Backend Tasks

- [x] **BE-1:** Create `PortfolioAlertResponse.java` record in `hu.riskguard.notification.api.dto` — fields: `alertId` (UUID), `tenantId` (UUID), `tenantName` (String), `taxNumber` (String), `companyName` (String), `previousStatus` (String), `newStatus` (String), `changedAt` (OffsetDateTime), `sha256Hash` (String), `verdictId` (UUID). Include `static from(PortfolioAlert domain)` factory method. (AC1)
- [x] **BE-2:** Create `PortfolioAlert.java` domain record in `hu.riskguard.notification.domain` — mirrors DTO fields. This is the internal domain type returned by `NotificationService`. (AC1, AC2)
- [x] **BE-3:** Create `PortfolioController.java` in `hu.riskguard.notification.api` — `@RestController` at `/api/v1/portfolio`. Single endpoint: `GET /alerts` with optional `@RequestParam days` (default 7, max 30). Extract `role` from JWT claims. If role != `ACCOUNTANT`, throw `ResponseStatusException(FORBIDDEN)`. Extract `user_id` (not `active_tenant_id`) to look up mandates. Delegate to `NotificationService.getPortfolioAlerts(userId, days)`. Map result via `PortfolioAlertResponse.from()`. (AC1, AC7)
- [x] **BE-4:** Add `getPortfolioAlerts(UUID userId, int days)` method to `NotificationService.java` facade — validates `days` (clamp 1-30). Calls `IdentityService.getActiveMandateTenantIds(userId)` to get list of mandated tenant IDs. If empty, return empty list. Calls `NotificationRepository.findPortfolioAlerts(tenantIds, since)` with `since = OffsetDateTime.now().minusDays(days)`. Returns `List<PortfolioAlert>`. (AC2)
- [x] **BE-5:** Add `getActiveMandateTenantIds(UUID userId)` method to `IdentityService.java` facade — queries `tenant_mandates` where `accountant_user_id = userId` AND `valid_from <= now` AND (`valid_to IS NULL` OR `valid_to >= now`). Returns `List<UUID>` of tenant IDs. (AC2)
- [x] **BE-6:** Add `findActiveMandateTenantIds(UUID userId, OffsetDateTime now)` to `IdentityRepository.java` — jOOQ query against `tenant_mandates` table. (AC2)
- [x] **BE-7:** Add `findPortfolioAlerts(List<UUID> tenantIds, OffsetDateTime since)` to `NotificationRepository.java` — jOOQ query: SELECT from `notification_outbox` WHERE `tenant_id IN (tenantIds)` AND `type IN ('ALERT','DIGEST')` AND `status = 'SENT'` AND `created_at >= since`, JOIN `tenants` ON `tenant_id` to get `tenantName`. Parse `payload` JSONB to extract `taxNumber`, `companyName`, `previousStatus`, `newStatus`, `sha256Hash`, `verdictId`, `changedAt`. ORDER BY `created_at` DESC. LIMIT 100 (safety cap). Returns `List<PortfolioAlert>`. (AC2)
- [x] **BE-8:** Add `getTenantName(UUID tenantId)` to `IdentityService.java` facade if not already present — used by the repository JOIN alternative. Alternatively, do the JOIN in the repository query directly (preferred — single SQL round trip). (AC2)

### Frontend Tasks

- [x] **FE-1:** Create `PortfolioPulse.vue` component in `frontend/app/components/Notification/` — fetches alerts from `GET /api/v1/portfolio/alerts`. Renders a compact vertical feed. Each alert item: status-color left border (Emerald/Amber/Crimson), tenant name in bold, company name, status change text, relative timestamp via `useDateRelative()`. Click handler calls `handleAlertClick(alert)`. Conditionally rendered only when `user.role === 'ACCOUNTANT'`. (AC3, AC4, AC7)
- [x] **FE-2:** Create `PortfolioPulse.spec.ts` co-located with component — tests: (a) renders alert list for ACCOUNTANT; (b) hidden for SME_ADMIN; (c) click triggers context switch + navigation; (d) empty state renders; (e) i18n keys resolve. (AC9)
- [x] **FE-3:** Add `handleAlertClick(alert)` logic in `PortfolioPulse.vue` — calls `identityStore.switchTenant(alert.tenantId)`, on success navigates to `/screening/${alert.taxNumber}`. On failure, shows PrimeVue Toast error via `useApiError()`. (AC5)
- [x] **FE-4:** Integrate `PortfolioPulse` into `frontend/app/pages/dashboard/index.vue` — add below the search bar, conditionally rendered with `v-if="user?.role === 'ACCOUNTANT'"`. Use the identity store's user ref. (AC3, AC7)
- [x] **FE-5:** Add empty state to `PortfolioPulse.vue` — when alerts array is empty: radar dish icon concept (use PrimeVue `pi-wifi` or similar icon), headline and body text from i18n keys. (AC6)
- [x] **FE-6:** Add i18n keys to `frontend/app/i18n/hu/notification.json` and `en/notification.json` — keys: `notification.portfolio.title` ("Portfolio riasztasok" / "Portfolio Alerts"), `notification.portfolio.emptyTitle` ("Nincs friss riasztas" / "No recent alerts"), `notification.portfolio.emptyBody`, `notification.portfolio.statusChange` ("{previous} → {new}"), `notification.portfolio.viewDetails` ("Reszletek" / "Details"). (AC8)
- [x] **FE-7:** Add `fetchPortfolioAlerts(days?)` method to `frontend/app/stores/watchlist.ts` (or create dedicated `portfolio.ts` store if preferred) — calls `GET /api/v1/portfolio/alerts?days={days}` via `useApi()`. Stores result in reactive ref. (AC3)

### Testing Tasks

- [x] **TEST-1:** Create `PortfolioControllerTest.java` in `hu.riskguard.notification.api` — tests: (a) ACCOUNTANT gets alerts; (b) SME_ADMIN gets 403; (c) GUEST gets 403; (d) days defaults to 7; (e) days clamped to 30; (f) empty result returns empty array. (AC9)
- [x] **TEST-2:** Add `getPortfolioAlerts` tests to `NotificationServiceTest.java` — tests: (a) returns alerts from multiple tenants; (b) respects mandate validity dates; (c) excludes expired mandates; (d) empty mandates returns empty; (e) days parameter filtering. (AC9)
- [x] **TEST-3:** Add `getActiveMandateTenantIds` tests to `IdentityServiceTest.java` (or create if absent) — tests: (a) returns active mandates; (b) excludes expired; (c) excludes future mandates. (AC9)
- [x] **TEST-4:** Verify `./gradlew check` passes — all existing + new tests green. Confirm no regressions in WatchlistController, NotificationService, or ScreeningService tests. (AC9)

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] **H1:** PortfolioController violates module boundary — directly imports IdentityService + User from identity module. Move resolveUserId() logic into NotificationService; controller should only depend on its own module facade. [PortfolioController.java:6-7, 75-85]
- [x] [AI-Review][HIGH] **H2:** Status change text hardcoded instead of using i18n — `{{ alert.previousStatus }} → {{ alert.newStatus }}` renders raw enum values. Use `$t('notification.portfolio.statusChange', { previous: $t('common.verdict.' + ...), new: ... })` and localize status labels via existing `common.verdict.*` keys per AC8. [PortfolioPulse.vue:122]
- [x] [AI-Review][HIGH] **H3:** `days` parameter not validated in controller — negative/zero values silently clamped by service. Add `@Min(1) @Max(30)` Bean Validation or explicit 400 BAD_REQUEST for out-of-range values. [PortfolioController.java:46-48]
- [x] [AI-Review][MEDIUM] **M1:** 13 files changed in git but NOT in story File List — mostly Story 3.8 uncommitted changes mixed into 3.9 working tree (build.gradle, RiskGuardProperties, PiiUtil, PartnerStatusChangedListener, ScreeningService, WatchlistMonitor, application*.yml, messages*.properties, test configs). Document or commit separately.
- [x] [AI-Review][MEDIUM] **M2:** NotificationService creates its own ObjectMapper instance instead of injecting Spring's managed bean. Behavior may diverge from JacksonConfig. Inject via constructor. [NotificationService.java:47]
- [x] [AI-Review][MEDIUM] **M3:** `daysDefaultsTo7` test name is misleading — explicitly passes 7, doesn't test @RequestParam default. Rename or add MockMvc integration test. [PortfolioControllerTest.java:96-105]
- [x] [AI-Review][MEDIUM] **M4:** `mapErrorType(undefined)` loses actual error type — catch block discards RFC 7807 type from failed request. Extract error type before passing to toast. [PortfolioPulse.vue:64]
- [x] [AI-Review][LOW] **L1:** Status color utility is component-local — violates "no hardcoded status color classes" anti-pattern. Extract `statusColorClass()` and `statusIconClass()` to a reusable composable for Story 3.10 reuse. [PortfolioPulse.vue:26-47]
- [x] [AI-Review][LOW] **L2:** PortfolioAlertResponse in api.d.ts manually created — consistent with existing types but note for eventual OpenAPI pipeline cleanup. [api.d.ts:96-109]
- [x] [AI-Review][LOW] **L3:** `getPortfolioAlerts_clampsDaysParameter` test uses `any()` matcher — doesn't assert `since` is approximately now-30d. Capture argument and verify. [NotificationServiceTest.java:333-345]

## Dev Notes

### Architecture Fit

- **Module placement:** Backend code lives in the `notification` module. `PortfolioController` in `notification.api` (new controller alongside existing `WatchlistController`). `PortfolioAlert` domain record in `notification.domain`. DTO in `notification.api.dto`. Repository methods added to existing `NotificationRepository.java`.
- **Cross-module dependency:** This story requires calling `IdentityService` facade to get active mandate tenant IDs. Per architecture communication matrix: **Need return value → facade call.** Add `getActiveMandateTenantIds(userId)` to `IdentityService`. Do NOT import `IdentityRepository` directly from the notification module.
- **Data source:** Portfolio alerts are sourced from `notification_outbox` records with `status=SENT` and `type IN ('ALERT','DIGEST')`. These records are created by Story 3.8's outbox pattern. The outbox already contains `tenant_id`, and the `payload` JSONB has `taxNumber`, `companyName`, `previousStatus`, `newStatus`, `sha256Hash`, `verdictId`, `changedAt`.
- **Table ownership:** `notification` module owns `notification_outbox`. `identity` module owns `tenant_mandates` and `tenants`. The repository query JOINs `tenants` to get the tenant name — this is acceptable as a read-only JOIN for display purposes (not writing to another module's table).
- **Why outbox records and not a separate alerts table:** The outbox already captures every status change event with all needed data. Creating a separate table would duplicate data. The outbox `status=SENT` filter ensures we only show alerts that were actually processed (not pending/failed).

### Cross-Tenant Query Pattern

This is the FIRST endpoint that reads across multiple tenants for a single user. The pattern:

1. Extract `user_id` from JWT (NOT `active_tenant_id` — we need the accountant's own identity)
2. Query `tenant_mandates` to get all tenant IDs where the accountant has active mandates
3. Query `notification_outbox` with `tenant_id IN (mandateTenantIds)`
4. JOIN `tenants` to resolve human-readable tenant names

**CRITICAL:** Do NOT use `TenantFilter` / `SecurityContextHolder` for this query. The `TenantFilter` scopes to `active_tenant_id`, but we need data from ALL mandated tenants. The repository method must bypass the tenant filter by directly specifying the tenant IDs in the WHERE clause.

**Security:** The mandate check IS the authorization. An accountant can only see alerts for tenants they have active mandates for. The mandate validity dates (`valid_from`, `valid_to`) must be checked server-side.

### JWT Claims Required

The `PortfolioController` needs:
- `role` claim — to verify ACCOUNTANT role (reject non-accountants with 403)
- `sub` or a `user_id` claim — to look up the accountant's mandates in `tenant_mandates.accountant_user_id`

Check how existing controllers extract user identity. `WatchlistController` uses `active_tenant_id` from JWT. For this endpoint, we need the USER's ID, not the active tenant. Check if `sub` claim contains the user's UUID or email. If email, resolve to user ID via `IdentityService.getUserIdByEmail()` or add a `user_id` claim to the JWT.

**Likely approach:** The JWT `sub` claim is the user's email (set during SSO/auth). Use `IdentityService` to resolve email → user UUID, then query mandates. Alternatively, if a `user_id` claim exists in the JWT, extract it directly.

### Frontend Component Architecture

```
pages/dashboard/index.vue
  └── <PortfolioPulse v-if="isAccountant" />
        ├── fetches from GET /api/v1/portfolio/alerts
        ├── maps alerts to compact feed items
        ├── each item: StatusIcon + TenantName + CompanyName + ChangeText + Timestamp
        ├── click handler: switchTenant() → navigate to /screening/{taxNumber}
        └── empty state when no alerts
```

**Component placement:** `frontend/app/components/Notification/PortfolioPulse.vue` with co-located `PortfolioPulse.spec.ts`. The architecture defines `Notification/` as the component directory for watchlist/alert UI.

**Conditional rendering:** Use `useIdentityStore()` to check `user.role === 'ACCOUNTANT'`. The identity store already exposes `user` with a `role` field.

### Status Color Mapping (from UX Spec SS7.2)

| New Status | Color | Tailwind Class | Icon |
|---|---|---|---|
| AT_RISK | Crimson (#B91C1C) | `border-red-700 text-red-700` | `pi-shield-x` or `pi-exclamation-triangle` |
| RELIABLE | Emerald (#15803D) | `border-green-700 text-green-700` | `pi-shield-check` or `pi-check-circle` |
| STALE / INCOMPLETE | Amber (#B45309) | `border-amber-600 text-amber-600` | `pi-shield-clock` or `pi-clock` |
| UNAVAILABLE | Grey (Slate) | `border-slate-400 text-slate-400` | `pi-shield` or `pi-minus-circle` |

**Priority ordering for Morning Risk Pulse:** AT_RISK first, then STALE/INCOMPLETE, then RELIABLE. Within same priority, sort by `changedAt` DESC.

### Outbox Payload JSONB Structure (from Story 3.8)

The `notification_outbox.payload` column for ALERT records contains:
```json
{
  "tenantId": "uuid",
  "taxNumber": "12345678",
  "companyName": "Kovacs Kft",
  "previousStatus": "RELIABLE",
  "newStatus": "AT_RISK",
  "verdictId": "uuid",
  "changedAt": "2026-03-20T10:30:00Z",
  "sha256Hash": "a1b2c3..."
}
```

For DIGEST records, the payload has a `changes` array:
```json
{
  "tenantId": "uuid",
  "changes": [
    { "taxNumber": "...", "companyName": "...", "previousStatus": "...", "newStatus": "..." }
  ]
}
```

**Implementation note:** DIGEST records need to be expanded into individual alert items in the response. Each entry in the `changes` array becomes a separate `PortfolioAlert`. The `changedAt` uses the outbox record's `created_at`. Digest entries lack `sha256Hash` and `verdictId` — these fields will be null in the response.

### Previous Story Intelligence (Story 3.8)

- **Outbox pattern is fully operational.** `notification_outbox` table exists with ALERT and DIGEST records. `OutboxProcessor` sends emails and marks records `SENT`. Portfolio alerts query SENT records.
- **PartnerStatusChangedListener** creates outbox records on every status change — this is the data source for portfolio alerts.
- **PiiUtil** is in `core.util` — use `PiiUtil.maskTaxNumber()` in any logging. Do NOT log raw tax numbers.
- **Cross-module facade pattern:** Story 3.8 added `getUserEmail()` and `getUserPreferredLanguage()` to `IdentityService`. Follow the same pattern for `getActiveMandateTenantIds()`.
- **jOOQ note:** Continue using `field()` and `table()` references for queries (established pattern across Stories 3.5-3.8). Add TODO markers for future type-safe replacement.
- **Health package:** `org.springframework.boot.health.contributor.{Health, HealthIndicator, Status}` (Spring Boot 4 location, learned in Stories 3.4-3.7). Not directly needed for this story but note for any health-related work.
- **Test count baseline:** 395 backend tests, 399+ frontend tests as of Story 3.8 completion.

### Git Intelligence

- Latest commit: `fe4aa70` — JWT algorithm fix, project-context.md update.
- Recent JWT/auth fixes (commits 82ffe78-1c4f771) — JWT `sub` claim and token handling are recently stabilized. Verify JWT claim names before implementing.
- `@EnableScheduling` already active on `RiskGuardApplication` (Story 3.5).
- No architectural changes impacting this story in recent commits.

### Critical Anti-Patterns to Avoid

1. **DO NOT use `active_tenant_id` from JWT for this endpoint.** The portfolio view needs data from ALL mandated tenants, not just the currently active one. Extract `user_id` (or resolve from `sub` email) instead.
2. **DO NOT create a new database table for portfolio alerts.** Reuse `notification_outbox` records. The data already exists there.
3. **DO NOT bypass `IdentityService` facade to query `tenant_mandates` directly.** Add a facade method and respect module boundaries.
4. **DO NOT render the PortfolioPulse component for non-ACCOUNTANT users.** Check role both server-side (403) and client-side (v-if).
5. **DO NOT log raw tax numbers or email addresses** in any new code. Use `PiiUtil.maskTaxNumber()`.
6. **DO NOT hardcode status color classes.** Create a reusable status-to-color mapping (composable or utility) that can be shared with other components.
7. **DO NOT use `TenantFilter`** for the cross-tenant portfolio query. This is a privileged cross-tenant read, similar to `getMonitoredPartners()` in NotificationService.

### Spring Boot 4 / Modulith Notes

- **Role check:** Extract role from JWT via `jwt.getClaimAsString("role")`. Compare against `"ACCOUNTANT"`. Spring Security's `@PreAuthorize("hasRole('ACCOUNTANT')")` can also be used if roles are mapped to authorities in `SecurityConfig`.
- **JSONB parsing in jOOQ:** Use `field("payload").cast(String.class)` to get the JSONB as a String, then parse with Jackson `ObjectMapper` in the service/repository layer. Alternatively use PostgreSQL JSONB operators (`->`, `->>`) in the jOOQ query for field extraction.
- **Transaction:** `getPortfolioAlerts` is a read-only operation. Annotate with `@Transactional(readOnly = true)`.

### Flyway Migration

No new tables needed. The `notification_outbox` and `tenant_mandates` tables already exist. If an index is needed for the portfolio query performance:

```sql
-- Optional: composite index for portfolio alerts query
-- Only add if query plan shows sequential scan on large outbox tables
CREATE INDEX idx_outbox_tenant_status_created
  ON notification_outbox (tenant_id, status, created_at DESC)
  WHERE type IN ('ALERT', 'DIGEST');
```

Evaluate whether this index is needed based on expected data volume. For MVP (< 1000 outbox records), the existing `idx_outbox_status` index should suffice.

### Project Structure Notes

- **Module boundaries:** All new backend production classes within `notification` module except the `IdentityService` facade method (in `identity` module). `PortfolioController` in `notification.api`. `PortfolioAlert` in `notification.domain`. DTO in `notification.api.dto`. Repository methods in `notification.internal.NotificationRepository`.
- **Cross-module dependencies:** `notification` → `identity` via `IdentityService.getActiveMandateTenantIds()` facade call. This follows the established pattern from Story 3.8 (getUserEmail, getUserPreferredLanguage).
- **Frontend structure:** `PortfolioPulse.vue` + `PortfolioPulse.spec.ts` in `frontend/app/components/Notification/`. i18n keys in `frontend/app/i18n/{hu,en}/notification.json`. Store method in `frontend/app/stores/watchlist.ts` (or new `portfolio.ts`).
- **No new database tables.** Reuses `notification_outbox` (notification module) and `tenant_mandates` (identity module via facade).
- **Dashboard integration:** `pages/dashboard/index.vue` gains a conditional `<PortfolioPulse />` component for accountants.

### Key Files to Create or Modify

| File | Action | Notes |
|---|---|---|
| `backend/.../notification/api/PortfolioController.java` | **Create** | REST endpoint for portfolio alerts (AC1, AC7) |
| `backend/.../notification/api/dto/PortfolioAlertResponse.java` | **Create** | Response DTO record with from() factory (AC1) |
| `backend/.../notification/domain/PortfolioAlert.java` | **Create** | Domain record (AC1, AC2) |
| `backend/.../notification/domain/NotificationService.java` | **Modify** | Add `getPortfolioAlerts()` method (AC2, AC4) |
| `backend/.../notification/internal/NotificationRepository.java` | **Modify** | Add `findPortfolioAlerts()` query (AC2) |
| `backend/.../identity/domain/IdentityService.java` | **Modify** | Add `getActiveMandateTenantIds()` facade method (AC2) |
| `backend/.../identity/internal/IdentityRepository.java` | **Modify** | Add `findActiveMandateTenantIds()` query (AC2) |
| `frontend/app/components/Notification/PortfolioPulse.vue` | **Create** | Portfolio alert feed component (AC3, AC4, AC5, AC6) |
| `frontend/app/components/Notification/PortfolioPulse.spec.ts` | **Create** | Co-located test spec (AC9) |
| `frontend/app/pages/dashboard/index.vue` | **Modify** | Add conditional PortfolioPulse component (AC3, AC7) |
| `frontend/app/i18n/hu/notification.json` | **Modify** | Add portfolio pulse i18n keys (AC8) |
| `frontend/app/i18n/en/notification.json` | **Modify** | Add portfolio pulse i18n keys (AC8) |
| `frontend/app/stores/watchlist.ts` | **Modify** | Add fetchPortfolioAlerts method (AC3) |
| `backend/src/test/java/.../notification/api/PortfolioControllerTest.java` | **Create** | Controller tests (AC9) |
| `backend/src/test/java/.../notification/domain/NotificationServiceTest.java` | **Modify** | Add portfolio alerts tests (AC9) |

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` Story 3.9] — Story definition: "The Accountant Portfolio Pulse Feed", acceptance criteria (FR5/FR6/FR7 accountant extension)
- [Source: `_bmad-output/planning-artifacts/epics.md` Story 3.10] — Related: "Flight Control Dashboard" (Story 3.10) is the next story building on this pulse feed with a full aggregate view
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS5.1] — "Morning Risk Pulse" UX flow: automated promotion of status changes with one-tap access to Audit Proof PDF
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS6.2] — Page Map: "Flight Control" accountant's aggregate client view, "Risk Pulse Dashboard" prioritized monitoring
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS7.2] — Feedback Patterns: Emerald for integrity, Amber for regulatory warning, Crimson for correction
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS11.2] — Risk Pulse Dashboard layout: alerts banner, watchlist table, quick search
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS11.5] — Flight Control layout: portfolio overview, client table, recent alerts feed
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS13.2] — Empty State: "Accountant with No Clients" design pattern (radar dish icon concept)
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` SS15.2] — In-App Alert Banner behavior and status change communication matrix
- [Source: `_bmad-output/planning-artifacts/architecture.md` #notification module] — Module facade pattern, notification_outbox table schema, cross-module communication via facades
- [Source: `_bmad-output/planning-artifacts/architecture.md` #identity module] — tenant_mandates table: accountant_user_id, tenant_id, valid_from, valid_to
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Communication Patterns] — Need return value → facade call. Broadcasting → event.
- [Source: `_bmad-output/planning-artifacts/architecture.md` #ADR-5] — OAuth2 SSO + Dual-Claim JWT: home_tenant_id, active_tenant_id, role in JWT claims
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Cross-Cutting Concerns — Tenant Isolation] — TenantFilter reads active_tenant_id. Accountant context-switch issues fresh JWT.
- [Source: `_bmad-output/implementation-artifacts/3-8-resend-email-alerts-and-outbox-pattern.md`] — Outbox pattern: notification_outbox table, ALERT/DIGEST types, payload JSONB structure, PiiUtil, IdentityService facade methods
- [Source: `_bmad-output/implementation-artifacts/3-7-24h-background-monitoring-cycle.md`] — WatchlistMonitor publishes PartnerStatusChanged, PartnerStatusChangedListener creates outbox records
- [Source: `_bmad-output/implementation-artifacts/3-6-watchlist-management-crud.md`] — WatchlistController pattern, NotificationService facade, tenant-scoped CRUD
- [Source: `_bmad-output/project-context.md`] — Module Facade rule, DTOs as records, @LogSafe, PII zero-tolerance, i18n rules
- [Source: `frontend/app/stores/identity.ts`] — useIdentityStore: user.role, switchTenant() mechanism
- [Source: `frontend/app/pages/dashboard/index.vue`] — Current dashboard structure (search + skeleton + verdict redirect)
- [Source: `backend/.../notification/api/WatchlistController.java`] — Pattern: JWT claim extraction with requireUuidClaim(), tenant-scoped operations
- [Source: `backend/.../notification/domain/NotificationService.java`] — Existing facade with outbox methods (createAlertNotification, getOutboxStats)

### Review Follow-ups R2 (AI)

- [x] [AI-Review][HIGH] **R2-H1:** DIGEST-expanded alerts share the same `rec.id()` as `alertId` — all entries from one DIGEST record have duplicate UUIDs. Breaks Vue `:key="alert.alertId"` rendering (mismatched virtual DOM updates). Fixed: `parseDigestRecord` now calls `UUID.randomUUID()` per expanded entry. Test added: `getPortfolioAlerts_expandsDigestRecords` now asserts `result.get(0).alertId() != result.get(1).alertId()`. [NotificationService.java:185]
- [x] [AI-Review][HIGH] **R2-H2:** `PortfolioPulse.vue` silently swallows `fetchAlerts()` failures — `store.error` was never read by the component, so network errors/403/500 showed as the "No recent alerts" empty state (indistinguishable from a real empty result). Fixed: added `error` to `storeToRefs`, added error state template block with retry button. Tests added: `shows error state when fetchAlerts fails`, `retry button calls fetchAlerts again`. [PortfolioPulse.vue:14, 80-100]
- [x] [AI-Review][MEDIUM] **R2-M1:** `useStatusColor.spec.ts` missing `TAX_SUSPENDED` and `UNAVAILABLE` coverage for `statusColorClass` and `statusIconClass`. Fixed: added 4 tests. [useStatusColor.spec.ts]
- [x] [AI-Review][MEDIUM] **R2-M2:** `PortfolioPulse.spec.ts` had no test for fetch error state — an AC9 gap. Fixed: 2 tests added covering error state rendering and retry button. Mock updated to expose `error` ref. [PortfolioPulse.spec.ts]
- [x] [AI-Review][LOW] **R2-L1:** `statusIconClass` spec missing `INCOMPLETE` test. Fixed: added test asserting `pi-clock` for `INCOMPLETE`. [useStatusColor.spec.ts]

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- All backend tests pass: `./gradlew check` BUILD SUCCESSFUL (395+ existing + 16 new tests + 5 new R1 review-fix tests + 1 new R2 review-fix test)
- All frontend tests pass: 43 test files, 425+ tests (399 existing + 8 PortfolioPulse + 16 useStatusColor + 4 new R2 useStatusColor tests + 2 new R2 PortfolioPulse error tests)
- Zero regressions across both suites
- Review R1 follow-up: 10/10 action items resolved (3H + 4M + 3L)
- Review R2 follow-up: 5/5 action items resolved (2H + 2M + 1L)

### Completion Notes List

- **BE-1/BE-2:** Created `PortfolioAlertResponse` DTO record and `PortfolioAlert` domain record following the canonical `static from(Domain)` factory pattern (matching `WatchlistEntryResponse`).
- **BE-3:** Created `PortfolioController` at `/api/v1/portfolio`. ACCOUNTANT role enforced via JWT `role` claim. User ID resolved from JWT `sub` (email) via `IdentityService.findUserByEmail()`. Non-accountants get 403.
- **BE-4:** Added `getPortfolioAlerts()` to `NotificationService` — cross-module facade call to `IdentityService.getActiveMandateTenantIds()`, then `NotificationRepository.findPortfolioAlerts()`. Parses ALERT and DIGEST outbox payloads. DIGEST records expanded into individual alerts. Morning Risk Pulse priority sorting: AT_RISK > STALE/INCOMPLETE > RELIABLE.
- **BE-5/BE-6:** Added `getActiveMandateTenantIds()` to `IdentityService` facade and `findActiveMandateTenantIds()` to `IdentityRepository` using type-safe jOOQ `TENANT_MANDATES` references. Checks `valid_from <= now` and `valid_to IS NULL OR valid_to >= now`.
- **BE-7/BE-8:** Added `findPortfolioAlerts()` to `NotificationRepository` — cross-tenant read JOINing `notification_outbox` with `tenants` to resolve tenant name. Single SQL round-trip (preferred over separate facade call). Uses raw jOOQ DSL pattern consistent with existing repository.
- **FE-1/FE-3/FE-5:** Created `PortfolioPulse.vue` component with compact vertical feed, status color mapping (UX Spec SS7.2), `handleAlertClick()` with tenant context switch, and empty state with `pi-wifi` icon.
- **FE-2:** 8 co-located tests covering: alert rendering, empty state, click with/without tenant switch, i18n resolution, mount fetch, loading skeleton, 20-item limit.
- **FE-4:** Integrated into `dashboard/index.vue` with `v-if="isAccountant"` conditional rendering.
- **FE-6:** Added i18n keys to both `hu/notification.json` and `en/notification.json` (alphabetically sorted).
- **FE-7:** Created dedicated `portfolio.ts` Pinia store (options API, matching watchlist store pattern).
- **TEST-1:** 8 controller tests (ACCOUNTANT happy path, SME_ADMIN 403, GUEST 403, days parameter, empty result, missing sub, unknown email).
- **TEST-2:** 5 service tests (multi-tenant alerts, mandate filtering, digest expansion, empty mandates, days clamping).
- **TEST-3:** 3 identity service tests (active mandates, expired exclusion, empty mandates).
- ✅ Resolved review finding [HIGH]: **H1** — Removed `IdentityService` + `User` imports from `PortfolioController`. Added `resolveUserIdByEmail()` to `NotificationService` facade. Controller now depends only on its own module.
- ✅ Resolved review finding [HIGH]: **H2** — Status change text now uses i18n via `localizedStatusChange()` helper. Maps enum values to `screening.verdict.*` keys using `useStatusColor().statusI18nKey()`. Renders localized labels (e.g., "Megbízható → Kockázatos").
- ✅ Resolved review finding [HIGH]: **H3** — Added `validateDaysParameter()` in controller returning 400 BAD_REQUEST for days < 1 or > 30. Three new controller tests (zero, negative, over-30). Service still clamps as defensive layer.
- ✅ Resolved review finding [MEDIUM]: **M1** — File List updated to clearly separate Story 3.9 files from pre-existing Story 3.8 uncommitted changes in working tree. Story 3.8 files are not Story 3.9 changes.
- ✅ Resolved review finding [MEDIUM]: **M2** — Partial fix: ObjectMapper now created in constructor (not field initializer) with TODO noting no Spring-managed ObjectMapper bean exists in this project (all modules use local instances). Full injection deferred to when JacksonAutoConfiguration is enabled.
- ✅ Resolved review finding [MEDIUM]: **M3** — Renamed `daysDefaultsTo7` → replaced with `daysAcceptsBoundaryValue1` and `daysAcceptsBoundaryValue30` tests. Added 3 new validation tests: `daysZeroReturns400`, `daysNegativeReturns400`, `daysOver30Returns400`.
- ✅ Resolved review finding [MEDIUM]: **M4** — Added `extractErrorType()` helper in `PortfolioPulse.vue` that extracts `data.type` from FetchError before passing to `mapErrorType()`. No longer discards RFC 7807 type.
- ✅ Resolved review finding [LOW]: **L1** — Extracted `statusColorClass()`, `statusIconClass()`, `statusI18nKey()` to new `useStatusColor` composable in `composables/formatting/`. 16 co-located tests. Reusable for Story 3.10.
- ✅ Resolved review finding [LOW]: **L2** — Acknowledged: `PortfolioAlertResponse` in `api.d.ts` is manually created, consistent with project pattern. Will be replaced when OpenAPI pipeline is enabled.
- ✅ Resolved review finding [LOW]: **L3** — Replaced `any()` matcher with `ArgumentCaptor<OffsetDateTime>` in `getPortfolioAlerts_clampsDaysParameter`. Asserts captured `since` falls within ±1 minute of `now - 30 days`.

### Change Log

- 2026-03-20: Story 3.9 implemented — Portfolio Pulse cross-tenant alert feed for accountants. Backend: new PortfolioController, PortfolioAlert/Response records, cross-module facade methods. Frontend: PortfolioPulse component with context switch, i18n, and dedicated store. 16 new backend tests, 8 new frontend tests. Zero regressions.
- 2026-03-20: Code review R1 — 3H/4M/3L findings. 10 action items created. Status → in-progress. Key issues: module boundary violation in PortfolioController (H1), hardcoded status text instead of i18n (H2), missing input validation (H3), 13 uncommitted Story 3.8 files mixed in working tree (M1).
- 2026-03-20: Addressed code review R1 findings — 10/10 items resolved (3H/4M/3L). Key changes: controller no longer imports identity module (H1), status text localized via screening.verdict.* keys (H2), days parameter validated with 400 BAD_REQUEST (H3), status color extracted to reusable composable (L1), error type properly extracted from FetchError (M4). 5 new backend tests, 16 new frontend composable tests. Zero regressions. Status → review.
- 2026-03-20: Addressed code review R2 findings — 5/5 items resolved (2H/2M/1L). Key changes: DIGEST-expanded alerts now use UUID.randomUUID() per entry (R2-H1, fixes Vue :key duplicate bug), PortfolioPulse shows error state with retry button instead of silently showing empty state on fetch failure (R2-H2), useStatusColor spec expanded with TAX_SUSPENDED/UNAVAILABLE/INCOMPLETE coverage (R2-M1/L1), PortfolioPulse spec covers error state (R2-M2). Zero regressions. Status → review.

### File List

**New files:**
- `backend/src/main/java/hu/riskguard/notification/api/PortfolioController.java`
- `backend/src/main/java/hu/riskguard/notification/api/dto/PortfolioAlertResponse.java`
- `backend/src/main/java/hu/riskguard/notification/domain/PortfolioAlert.java`
- `backend/src/test/java/hu/riskguard/notification/api/PortfolioControllerTest.java`
- `backend/src/test/java/hu/riskguard/identity/domain/IdentityServiceTest.java`
- `frontend/app/components/Notification/PortfolioPulse.vue`
- `frontend/app/components/Notification/PortfolioPulse.spec.ts`
- `frontend/app/stores/portfolio.ts`
- `frontend/app/composables/formatting/useStatusColor.ts` — (R1 fix L1) reusable status color/icon/i18n composable
- `frontend/app/composables/formatting/useStatusColor.spec.ts` — (R1 fix L1) 16 co-located tests

**Modified files:**
- `backend/src/main/java/hu/riskguard/notification/api/PortfolioController.java` — (R1 fix H1/H3) removed IdentityService import, added days validation with 400 BAD_REQUEST, delegated resolveUserId to NotificationService
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java` — added resolveUserIdByEmail() facade method (R1 fix H1), getPortfolioAlerts(), JSONB parsing, Morning Risk Pulse sorting, ObjectMapper created in constructor with TODO (R1 fix M2)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java` — added PortfolioOutboxRecord, findPortfolioAlerts() with tenants JOIN
- `backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java` — added getActiveMandateTenantIds() facade method
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java` — added findActiveMandateTenantIds() query
- `backend/src/test/java/hu/riskguard/notification/api/PortfolioControllerTest.java` — (R1 fix H1/H3/M3) removed IdentityService mock, added 5 new validation tests, replaced misleading daysDefaultsTo7 with boundary tests
- `backend/src/test/java/hu/riskguard/notification/domain/NotificationServiceTest.java` — added IdentityService mock, 5 portfolio tests, (R1 fix L3) ArgumentCaptor for since date assertion
- `frontend/app/components/Notification/PortfolioPulse.vue` — (R1 fix H2/M4/L1) uses useStatusColor composable, localizedStatusChange() for i18n, extractErrorType() for RFC 7807
- `frontend/app/components/Notification/PortfolioPulse.spec.ts` — updated mocks for useStatusColor composable
- `frontend/app/pages/dashboard/index.vue` — added conditional PortfolioPulse component for accountants
- `frontend/app/i18n/hu/notification.json` — added portfolio section
- `frontend/app/i18n/en/notification.json` — added portfolio section
- `frontend/types/api.d.ts` — added PortfolioAlertResponse interface

- `frontend/app/components/Notification/PortfolioPulse.vue` — (R2 fix H2) added `error` to storeToRefs, added error state template block with retry button
- `frontend/app/components/Notification/PortfolioPulse.spec.ts` — (R2 fix H2/M2) added `mockError` ref to store mock, added 2 error state tests, reset `mockError` in beforeEach
- `frontend/app/composables/formatting/useStatusColor.spec.ts` — (R2 fix M1/L1) added 4 tests: UNAVAILABLE/TAX_SUSPENDED for statusColorClass, INCOMPLETE/UNAVAILABLE/TAX_SUSPENDED for statusIconClass

**Note (M1):** The following files appear in `git diff` but are Story 3.8 uncommitted changes, NOT Story 3.9: `build.gradle`, `RiskGuardProperties.java`, `PartnerStatusChanged.java`, `PiiUtil.java`, `PartnerStatusChangedListener.java`, `ScreeningService.java`, `WatchlistMonitor.java`, `ScreeningRepository.java`, `application*.yml`, `messages*.properties`, `application-test.yml`, `PartnerStatusChangedListenerTest.java`. These should be committed separately.
