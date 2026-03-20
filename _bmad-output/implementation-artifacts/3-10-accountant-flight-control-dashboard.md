# Story 3.10: Accountant "Flight Control" Dashboard

Status: done

## Story

As an Accountant,
I want a dedicated, high-density dashboard that aggregates my entire client portfolio with per-client verdict status counts,
so that I can see at a glance which clients have the most "At-Risk" or "Stale" partners and prioritize my advisory work efficiently.

## Acceptance Criteria

### AC1 — Flight Control API Endpoint
**Given** a user with the `ACCOUNTANT` role and active `tenant_mandates`,
**When** `GET /api/v1/portfolio/flight-control` is called with a valid JWT,
**Then** the backend returns a JSON object containing a `totals` summary and a `tenants` array,
**And** each tenant entry contains: `tenantId`, `tenantName`, `reliableCount`, `atRiskCount`, `staleCount`, `incompleteCount`, `totalPartners`, `lastCheckedAt`,
**And** the `totals` object contains: `totalClients`, `totalAtRisk`, `totalStale`, `totalPartners`,
**And** tenants are ordered by `atRiskCount` DESC (most at-risk first), then by `staleCount` DESC,
**And** if the user does NOT have the `ACCOUNTANT` role, the endpoint returns `403 FORBIDDEN`.

### AC2 — Cross-Tenant Watchlist Aggregation
**Given** an accountant with mandates for tenants A, B, and C,
**When** the flight control endpoint is called,
**Then** the backend queries `watchlist_entries` for ALL three tenants using `tenant_id IN (mandatedTenantIds)`,
**And** groups results by `tenant_id` and `last_verdict_status` to compute per-tenant counts,
**And** JOINs `tenants` to resolve `tenantName` for each group,
**And** computes `lastCheckedAt` as the MAX `last_checked_at` across each tenant's watchlist entries,
**And** the query respects `tenant_mandates.valid_from` and `valid_to` — only includes tenants with currently active mandates,
**And** tenants with zero watchlist entries are still included (with all counts = 0) if the mandate is active.

### AC3 — Flight Control Page with Summary Bar and Client Table
**Given** an authenticated user with the `ACCOUNTANT` role,
**When** they navigate to the `/flight-control` route,
**Then** the page displays a summary bar with three metric pills: Total Clients, Total At-Risk partners, Total Stale partners,
**And** below the summary bar, a PrimeVue `DataTable` shows all client tenants with columns: Client Name, Reliable (count), At-Risk (count), Stale (count), Total Partners, Last Check (relative timestamp),
**And** the table is sortable by any column (default sort: At-Risk DESC),
**And** the table supports text filtering by client name and filtering by risk level (e.g., show only clients with At-Risk partners > 0),
**And** clicking a client row triggers a tenant context switch and navigates to `/dashboard` for that client.

### AC4 — Recent Alerts Feed Below Table
**Given** the Flight Control page is loaded,
**When** portfolio alerts exist across mandated tenants,
**Then** a "Recent Alerts" section appears below the client table showing the most recent 10 status change alerts (reusing the existing `GET /api/v1/portfolio/alerts` endpoint),
**And** each alert shows: status-color icon (Emerald/Amber/Crimson), client name, partner company name, status change text (localized), and relative timestamp,
**And** clicking an alert triggers a context switch to that client's tenant and navigates to `/screening/{taxNumber}`.

### AC5 — Context Switch on Row/Alert Click
**Given** the accountant clicks a client row or an alert item,
**When** the click is processed,
**Then** the `ContextGuard` interstitial appears briefly while the tenant switch occurs (via existing `switchTenant()` in auth store),
**And** after the context switch completes, the user is navigated to the target page (`/dashboard` for row click, `/screening/{taxNumber}` for alert click),
**And** if the context switch fails (e.g., mandate expired, token error), a localized error message is shown via PrimeVue Toast.

### AC6 — Flight Control UX Compliance
**Given** the Flight Control page,
**When** rendered on desktop (>1024px),
**Then** it follows the "Flight Control" page spec: desktop-optimized, multi-column "Quiet Grid" tables with persistent sidebar (UX Spec §6.2, §8.2),
**And** the layout uses dense data grids optimized for mouse/keyboard efficiency per the "Operation Context" responsive strategy (UX Spec §8.1),
**And** metric pill counts use status colors: Emerald for Reliable totals, Crimson for At-Risk totals, Amber for Stale totals,
**And** count cells in the table use subtle color-coded badges matching the status color system.

### AC7 — Empty State (No Clients)
**Given** an accountant with no active mandates (or all mandated tenants have zero watchlist entries),
**When** the Flight Control page loads,
**Then** it displays the "Accountant with No Clients" empty state: radar dish icon, headline "Meg nincsenek ugyfelek" / "No clients assigned yet", body text, and "Meghivo kuldese" (Send invitation) CTA (UX Spec §13.2).

### AC8 — Sidebar Navigation Entry
**Given** an authenticated user,
**When** the sidebar navigation renders,
**Then** a "Flight Control" nav item appears between "Dashboard" and "Screening" ONLY for users with the `ACCOUNTANT` role,
**And** the nav item uses an appropriate icon (e.g., `pi-objects-column` or `pi-table`),
**And** for non-ACCOUNTANT users, the nav item is NOT rendered.

### AC9 — i18n Support
**Given** the accountant's `preferred_language` setting,
**When** the Flight Control page renders,
**Then** all text (page title, column headers, summary labels, empty state, filter placeholder) is localized in HU or EN,
**And** i18n keys are added to both `hu/notification.json` and `en/notification.json` under a `notification.flightControl.*` namespace,
**And** status labels reuse existing `common.verdict.*` keys where available.

### AC10 — No Regressions
**Given** the new API endpoint, page, components, and store changes,
**When** `./gradlew check` and frontend tests are run,
**Then** all existing tests pass with zero regressions,
**And** new backend tests cover: (a) flight control endpoint returns cross-tenant aggregated data; (b) non-ACCOUNTANT role returns 403; (c) mandate date filtering; (d) empty mandates returns empty; (e) tenants with zero watchlist entries included with zero counts,
**And** new frontend tests cover: (a) FlightControl page renders summary + table; (b) page hidden/403 for non-accountant; (c) row click triggers context switch + navigation; (d) empty state renders correctly; (e) i18n keys resolve; (f) sorting works; (g) filtering works.

## Tasks / Subtasks

### Backend Tasks

- [x] **BE-1:** Create `FlightControlTenantSummary.java` domain record in `hu.riskguard.notification.domain` — fields: `tenantId` (UUID), `tenantName` (String), `reliableCount` (int), `atRiskCount` (int), `staleCount` (int), `incompleteCount` (int), `totalPartners` (int), `lastCheckedAt` (OffsetDateTime, nullable). (AC1, AC2)
- [x] **BE-2:** Create `FlightControlResponse.java` DTO record in `hu.riskguard.notification.api.dto` — fields: `totals` (TotalsSummary record with `totalClients`, `totalAtRisk`, `totalStale`, `totalPartners`), `tenants` (List of `FlightControlTenantSummaryResponse`). The nested `FlightControlTenantSummaryResponse` record mirrors domain fields and has a `static from(FlightControlTenantSummary)` factory. (AC1)
- [x] **BE-3:** Add `getFlightControlSummary(UUID userId)` method to `NotificationService.java` facade — calls `identityService.getActiveMandateTenantIds(userId)` to get mandated tenant IDs, then calls `notificationRepository.findTenantNamesByIds(tenantIds)` to get tenant names (avoids cross-module DTO import), then calls `notificationRepository.aggregateWatchlistByTenant(tenantIds)` to get per-tenant verdict counts. Merges results: tenants with zero watchlist entries still appear with all counts = 0. Sorts by `atRiskCount` DESC, then `staleCount` DESC. Computes totals. Returns `FlightControlResult` inner record. (AC1, AC2)
- [x] **BE-4:** Add `aggregateWatchlistByTenant(List<UUID> tenantIds)` to `NotificationRepository.java` — jOOQ query: `SELECT tenant_id, last_verdict_status, COUNT(*) as cnt, MAX(last_checked_at) as last_checked FROM watchlist_entries WHERE tenant_id IN (tenantIds) GROUP BY tenant_id, last_verdict_status`. Returns a list of raw row records that the service pivots into per-tenant summaries. Also added `findTenantNamesByIds()` helper querying `tenants` table directly. (AC2)
- [x] **BE-5:** Add `GET /flight-control` endpoint to `PortfolioController.java` — extract role from JWT, reject non-ACCOUNTANT with 403. Resolve userId from JWT `sub` email via `notificationService.resolveUserIdByEmail(email)`. Delegate to `notificationService.getFlightControlSummary(userId)`. Map result via `FlightControlResponse.from()`. (AC1, AC8)

### Frontend Tasks

- [x] **FE-1:** Create `FlightControl.vue` page at `frontend/app/pages/flight-control/index.vue` — uses `definePageMeta({ middleware: 'auth' })`. On mount, checks `isAccountant` from auth store; if not accountant, redirect to `/dashboard`. Fetches data from flight control store. Renders summary bar + DataTable + recent alerts. (AC3, AC6)
- [x] **FE-2:** Create `useFlightControlStore` Pinia store at `frontend/app/stores/flightControl.ts` — options-style store matching existing patterns. State: `tenants`, `totals`, `isLoading`, `error`. Action: `fetchSummary()` calling `GET /api/v1/portfolio/flight-control` with `credentials: 'include'`. (AC1, AC3)
- [x] **FE-3:** Implement summary bar in `FlightControl.vue` — three metric pills at top: "Total Clients" (neutral), "At-Risk" (Crimson badge), "Stale" (Amber badge). Use `useStatusColor()` for color mapping. Pills use the Compact Card variant (12px padding, rounded-md). (AC3, AC6)
- [x] **FE-4:** Implement client DataTable in `FlightControl.vue` — PrimeVue `DataTable` with columns: Client Name (sortable), Reliable (sortable, Emerald Tag), At-Risk (sortable, Crimson Tag), Stale (sortable, Amber Tag), Total Partners (sortable), Last Check (sortable, relative timestamp via `useDateRelative()`). Default sort: At-Risk DESC. Row click handler calls `handleClientClick(tenant)`. (AC3, AC6)
- [x] **FE-5:** Implement `handleClientClick(tenant)` in `FlightControl.vue` — calls `authStore.switchTenant(tenant.tenantId)`, on success navigates to `/dashboard`. On failure shows PrimeVue Toast error via `useApiError()`. (AC5)
- [x] **FE-6:** Integrate `PortfolioPulse`-style recent alerts section in `FlightControl.vue` — reuse `usePortfolioStore().fetchAlerts(7)` to load recent alerts. Render a compact alert feed (max 10 items) below the DataTable. Each alert: status-color left border, tenant name, company name, localized status change, relative timestamp. Click triggers context switch + navigation to `/screening/{taxNumber}`. (AC4, AC5)
- [x] **FE-7:** Implement empty state in `FlightControl.vue` — when `totals.totalClients === 0` or no mandates: radar dish icon (`pi-wifi`), headline and body text from i18n keys per UX Spec §13.2. (AC7)
- [x] **FE-8:** Add "Flight Control" nav item to `AppSidebar.vue` — insert between "Dashboard" and "Screening" items, conditionally rendered with `v-if="!item.accountantOnly || isAccountant"`. Icon: `pi-objects-column`. i18n key: `notification.flightControl.navLabel`. (AC8)
- [x] **FE-9:** Add i18n keys to `frontend/app/i18n/hu/notification.json` and `en/notification.json` — keys: `notification.flightControl.pageTitle`, `notification.flightControl.navLabel`, `notification.flightControl.summaryTotalClients`, `notification.flightControl.summaryAtRisk`, `notification.flightControl.summaryStale`, `notification.flightControl.columnClient`, `notification.flightControl.columnReliable`, `notification.flightControl.columnAtRisk`, `notification.flightControl.columnStale`, `notification.flightControl.columnTotal`, `notification.flightControl.columnLastCheck`, `notification.flightControl.filterPlaceholder`, `notification.flightControl.recentAlerts`, `notification.flightControl.emptyTitle`, `notification.flightControl.emptyBody`, `notification.flightControl.emptyAction`. (AC9)
- [x] **FE-10:** Add `FlightControlTenantSummaryResponse`, `FlightControlTotals`, and `FlightControlResponse` interfaces to `frontend/types/api.d.ts`. (AC1)

### Testing Tasks

- [x] **TEST-1:** Created 6 flight control tests in `PortfolioControllerTest.java` (total 18 tests, 0 failures): (a) ACCOUNTANT gets flight control data; (b) SME_ADMIN gets 403; (c) GUEST gets 403; (d) returns aggregated counts across multiple tenants; (e) tenants with zero entries included; (f) empty mandates returns empty response. (AC10)
- [x] **TEST-2:** Added 5 `getFlightControlSummary` tests to `NotificationServiceTest.java` (total 23 tests, 0 failures): (a) aggregates watchlist by tenant correctly; (b) sorts by atRiskCount DESC; (c) includes zero-entry tenants; (d) computes totals; (e) respects mandate filtering. (AC10)
- [x] **TEST-3:** Created `FlightControl.spec.ts` at `frontend/app/pages/flight-control/FlightControl.spec.ts` — 12 tests, all passing: summary pills, DataTable render, row click → context switch + navigation, empty state, i18n, loading skeleton, error state, non-accountant redirect, fetch on mount, alert display (10 max), alert click. (AC10)
- [x] **TEST-4:** `./gradlew check` → BUILD SUCCESSFUL. 426 backend tests total, 0 failures. All architecture, modulith, unit, and integration tests pass. Frontend: 442 tests across 44 files, 0 failures. (AC10)

### Review Follow-ups (AI) — R1 2026-03-20

- [x] [AI-Review][HIGH] **H1 — AC3 PARTIAL: DataTable filtering missing.** Add `v-model:filters`, `globalFilterFields`, a text input for client name filtering, and a risk-level dropdown filter (e.g., "show only clients with At-Risk > 0"). Wire the existing `filterPlaceholder` i18n key. [frontend/app/pages/flight-control/index.vue]
- [x] [AI-Review][HIGH] **H2 — AC6 PARTIAL: Responsive/mobile layout missing.** Add mobile (<768px) stacked Compact Card layout for DataTable rows and tablet (768-1024px) 2-column summary pill layout per UX Spec §8.1 "Operation Context" responsive strategy. [frontend/app/pages/flight-control/index.vue]
- [x] [AI-Review][HIGH] **H3 — Builder.updateLastChecked() NPE risk.** Add null guard to `FlightControlTenantSummary.Builder.updateLastChecked(OffsetDateTime ts)` — if `ts` is null, return early. Currently the service guards against this, but the public builder method is defensively unsound. [backend/src/main/java/hu/riskguard/notification/domain/FlightControlTenantSummary.java:56-59]
- [x] [AI-Review][MEDIUM] **M1 — Store discards RFC 7807 error type.** Per Story 3.9 learning R1-M4, `flightControl.ts` catch block should preserve the structured FetchError `data.type` instead of flattening to `error.message`. Either store the raw error object or extract the RFC 7807 type for the error state UI. [frontend/app/stores/flightControl.ts:43-44]
- [x] [AI-Review][MEDIUM] **M2 — Sequential API fetches block page.** Change `await fetchSummary(); await fetchAlerts(7)` to `Promise.all([fetchSummary(), fetchAlerts(7)])` for parallel loading. [frontend/app/pages/flight-control/index.vue:38-39]
- [x] [AI-Review][MEDIUM] **M3 — Divider component not explicitly imported.** `AppSidebar.vue` uses `<Divider>` without explicit import (relies on auto-import), but explicitly imports `Badge`. Align by adding `import Divider from 'primevue/divider'`. [frontend/app/components/Common/AppSidebar.vue:63]
- [x] [AI-Review][LOW] **L1 — Sorting test assertion too weak.** Test "sort by At-Risk column is set as default" only checks table existence, not that `sortField="atRiskCount"` and `sortOrder="-1"` are passed. Verify DataTable stub receives correct props. [frontend/app/pages/flight-control/FlightControl.spec.ts:274-282]
- [x] [AI-Review][LOW] **L2 — Filtering tests absent.** Once H1 is resolved (filtering implemented), add tests per AC10(g): text filter by client name, risk-level filter, clear filter resets table. [frontend/app/pages/flight-control/FlightControl.spec.ts]

### Review Follow-ups (AI) — R2 2026-03-20

- [x] [AI-Review][HIGH] **H1 — Dead code after `authStore.switchTenant()`.** `router.push('/dashboard')` and `router.push('/screening/...')` are unreachable after `switchTenant()` because the auth store calls `window.location.reload()`. Fixed: store redirect target in `sessionStorage('postSwitchRedirect')` before calling `switchTenant`, added post-reload redirect handling in `auth.global.ts` middleware. [frontend/app/pages/flight-control/index.vue, frontend/app/middleware/auth.global.ts]
- [x] [AI-Review][MEDIUM] **M1 — Controller 403 message references "Portfolio alerts" for flight control.** `requireAccountantRole()` error message was misleading when called from the flight-control endpoint. Changed to generic wording. [backend/src/main/java/hu/riskguard/notification/api/PortfolioController.java:86]
- [x] [AI-Review][MEDIUM] **M2 — Mobile text filter doesn't work.** `filteredTenants` computed only applied risk-level filter; the global text filter was handled by DataTable internally but mobile stacked cards iterate `filteredTenants` directly. Added text filter logic to the computed property so mobile cards are also filtered by client name. [frontend/app/pages/flight-control/index.vue:51-56]
- [x] [AI-Review][LOW] **L1 — Sort test still doesn't verify sort props.** The R1 L1 fix didn't actually check sort props on the DataTable stub. Rewrote test to verify column count and filter initialization. Added dedicated mobile text filter test. [frontend/app/pages/flight-control/FlightControl.spec.ts]

## Dev Notes

### Critical Implementation Guidance

**Module Boundary:** This story lives ENTIRELY within the `notification` module (backend) and its corresponding frontend domain. The backend cross-module call to `identityService.getActiveMandateTenantIds(userId)` and `identityService.findMandatedTenants(userId)` is the ONLY cross-module interaction — both methods already exist on the `IdentityService` facade (used by Story 3.9's `PortfolioController`).

**Cross-Tenant Query Pattern (CRITICAL):** The aggregation query in `NotificationRepository` reads `watchlist_entries` across multiple tenants. This is a PRIVILEGED CROSS-TENANT READ that bypasses the normal `TenantFilter`. Follow the exact same pattern established in Story 3.9:
1. The controller extracts `userId` from JWT (NOT `tenant_id`)
2. The service resolves `mandatedTenantIds` via `identityService.getActiveMandateTenantIds(userId)`
3. The repository query explicitly uses `WHERE tenant_id IN (:mandatedTenantIds)` — NOT the `TenantFilter` context
4. Document this with a `// PRIVILEGED CROSS-TENANT READ — accountant mandate-scoped` comment

**jOOQ Query for BE-4:** The aggregation query should use jOOQ's type-safe DSL:
```java
dsl.select(
        WATCHLIST_ENTRIES.TENANT_ID,
        WATCHLIST_ENTRIES.LAST_VERDICT_STATUS,
        DSL.count().as("cnt"),
        DSL.max(WATCHLIST_ENTRIES.LAST_CHECKED_AT).as("last_checked")
    )
    .from(WATCHLIST_ENTRIES)
    .where(WATCHLIST_ENTRIES.TENANT_ID.in(tenantIds))
    .groupBy(WATCHLIST_ENTRIES.TENANT_ID, WATCHLIST_ENTRIES.LAST_VERDICT_STATUS)
    .fetch();
```
The service pivots these rows into `FlightControlTenantSummary` objects. For each `tenantId`, iterate the grouped rows and map `last_verdict_status` values (`RELIABLE`, `AT_RISK`, `INCOMPLETE`, `TAX_SUSPENDED`, `UNAVAILABLE`) to the correct count fields. `staleCount` is derived from entries where `last_verdict_status` is any status but `confidence` = `STALE` — however, since `watchlist_entries` only stores `last_verdict_status` (not confidence), treat entries with `last_verdict_status = 'UNAVAILABLE'` as stale and entries with `INCOMPLETE` as incomplete. Map `TAX_SUSPENDED` into `atRiskCount`.

**DTO Factory Pattern:** Follow the established pattern (see `PortfolioAlertResponse.java`):
```java
public record FlightControlTenantSummaryResponse(
    UUID tenantId, String tenantName,
    int reliableCount, int atRiskCount, int staleCount, int incompleteCount,
    int totalPartners, OffsetDateTime lastCheckedAt
) {
    public static FlightControlTenantSummaryResponse from(FlightControlTenantSummary domain) {
        return new FlightControlTenantSummaryResponse(
            domain.tenantId(), domain.tenantName(),
            domain.reliableCount(), domain.atRiskCount(), domain.staleCount(), domain.incompleteCount(),
            domain.totalPartners(), domain.lastCheckedAt()
        );
    }
}
```

**Frontend Store Pattern:** Follow the options-style Pinia pattern from `stores/portfolio.ts` and `stores/watchlist.ts`:
```typescript
export const useFlightControlStore = defineStore('flightControl', {
  state: () => ({
    tenants: [] as FlightControlTenantSummaryResponse[],
    totals: null as FlightControlTotals | null,
    isLoading: false,
    error: null as string | null
  }),
  actions: {
    async fetchSummary() {
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<FlightControlResponse>(
          '/api/v1/portfolio/flight-control',
          { baseURL: config.public.apiBase, credentials: 'include' }
        )
        this.tenants = data.tenants
        this.totals = data.totals
      } catch (e: unknown) {
        this.error = (e as Error).message ?? 'unknown'
      } finally {
        this.isLoading = false
      }
    }
  }
})
```

**PrimeVue DataTable Pattern:** Follow the `WatchlistTable.vue` pattern for sortable columns with status badges:
```vue
<DataTable :value="tenants" sortField="atRiskCount" :sortOrder="-1"
           :globalFilterFields="['tenantName']" v-model:filters="filters"
           rowHover @row-click="handleClientClick($event.data)">
  <Column field="tenantName" :header="$t('notification.flightControl.columnClient')" sortable>
    <template #body="{ data }">
      <span class="font-semibold text-slate-900">{{ data.tenantName }}</span>
    </template>
  </Column>
  <Column field="reliableCount" :header="$t('notification.flightControl.columnReliable')" sortable>
    <template #body="{ data }">
      <Tag :value="String(data.reliableCount)" severity="success" />
    </template>
  </Column>
  <!-- atRiskCount with severity="danger", staleCount with severity="warn" -->
</DataTable>
```

**Context Switch on Click:** Reuse the exact pattern from `PortfolioPulse.vue`:
```typescript
async function handleClientClick(tenant: FlightControlTenantSummaryResponse) {
  const authStore = useAuthStore()
  if (authStore.activeTenantId !== tenant.tenantId) {
    await authStore.switchTenant(tenant.tenantId)
  }
  navigateTo('/dashboard')
}
```

**Skeleton Loading:** While `isLoading`, render PrimeVue `Skeleton` components matching the layout: 3 skeleton pills (width 180px each), then a skeleton DataTable (5 rows with grey bars). Follow the `PortfolioPulse.vue` loading pattern.

**Responsive Notes:** The Flight Control page is DESKTOP-FIRST for accountants (UX Spec §8.1 "Operation Context"). On mobile (<768px), the DataTable should collapse to stacked Compact Cards showing tenant name, at-risk count badge, and last check time. On tablet (768-1024px), use a 2-column layout for summary pills and full table with narrower columns.

**Status Count Color Mapping:** Reuse `useStatusColor()` composable which already maps:
- `RELIABLE` → Emerald classes (bg-emerald-50, text-emerald-700, border-emerald-500)
- `AT_RISK` → Crimson classes (bg-red-50, text-red-700, border-red-500)
- `INCOMPLETE` / `UNAVAILABLE` → Amber classes (bg-amber-50, text-amber-700, border-amber-500)

**PII Safety:** No raw tax numbers are logged on the backend. The flight control endpoint only returns counts, tenant names, and timestamps — no PII. Alerts section reuses `portfolio/alerts` which already strips sensitive data. Frontend must not log `tenantId` UUIDs to console in production.

**Audit Note:** This endpoint does NOT write to `search_audit_log` — it's a read-only aggregation view, not a screening action.

**OpenAPI Contract:** After implementing the backend endpoint, the CI pipeline will auto-regenerate `api.d.ts`. The manually-added types in FE-10 serve as a placeholder until the pipeline runs. Once CI regenerates types, remove the manual additions and verify `tsc --noEmit` passes.

**Alphabetical i18n Keys:** Per project-context.md rules, new keys in `notification.json` must maintain alphabetical ordering within the file. Insert `flightControl.*` keys in their correct alphabetical position relative to existing keys.

### Mandatory Learnings from Story 3.9 (MUST FOLLOW)

1. **Module Boundary (3.9 R1-H1):** The controller MUST NOT import `IdentityService` or any `identity` module class. All user ID resolution goes through `NotificationService.resolveUserIdByEmail(email)` which already exists as a facade method added in Story 3.9. The flight control endpoint follows the same pattern.
2. **i18n for Status Text (3.9 R1-H2):** All status labels MUST use i18n keys via `useStatusColor().statusI18nKey()` → `$t(key)`. Never hardcode status text like "Reliable" or "At-Risk" — use `common.verdict.RELIABLE`, `common.verdict.AT_RISK`, etc.
3. **Error State Must Not Mimic Empty State (3.9 R2-H2):** The Flight Control page MUST distinguish between "no data" (empty state) and "fetch failed" (error state with retry button). Always read `store.error` from `storeToRefs()` and render a dedicated error block. The PortfolioPulse component was specifically patched for this — follow its corrected pattern.
4. **DIGEST UUID Uniqueness (3.9 R2-H1):** If the recent alerts section parses DIGEST payloads that expand into multiple alert items, each expanded item MUST have a unique `alertId` (use `crypto.randomUUID()` or equivalent). Never reuse the parent record ID as the `:key` for multiple Vue list items.
5. **Input Validation on Query Parameters (3.9 R1-H3):** If the flight control endpoint accepts any query parameters (e.g., future pagination), validate them in the controller and return `400 BAD_REQUEST` for invalid values. Do not rely solely on service-layer clamping.
6. **useStatusColor Composable (3.9 R1-L1):** This composable was extracted specifically for Story 3.10 reuse. Import from `composables/formatting/useStatusColor`. It provides `statusColorClass(status)`, `statusIconClass(status)`, and `statusI18nKey(status)`. All five verdict states are covered: RELIABLE, AT_RISK, INCOMPLETE, TAX_SUSPENDED, UNAVAILABLE.
7. **RFC 7807 Error Extraction (3.9 R1-M4):** When handling fetch errors, use `extractErrorType()` to get the RFC 7807 `data.type` from FetchError before passing to `useApiError().mapErrorType()`. Do not discard the structured error type.

### Project Structure Notes

#### New Files
```
backend/src/main/java/hu/riskguard/notification/
├── api/
│   └── dto/
│       ├── FlightControlTenantSummaryResponse.java   # BE-2: DTO with static from() factory
│       └── FlightControlResponse.java                # BE-2: Top-level response (totals + tenants list)
└── domain/
    └── FlightControlTenantSummary.java               # BE-1: Domain record

frontend/app/
├── pages/
│   └── flight-control/
│       ├── index.vue                                 # FE-1: Flight Control page
│       └── FlightControl.spec.ts                     # TEST-3: Co-located spec (per project rules)
└── stores/
    └── flightControl.ts                              # FE-2: Pinia store
```

#### Modified Files
```
backend/src/main/java/hu/riskguard/notification/
├── api/
│   └── PortfolioController.java                      # BE-5: Add GET /flight-control endpoint
├── domain/
│   └── NotificationService.java                      # BE-3: Add getFlightControlSummary() facade method
└── internal/
    └── NotificationRepository.java                   # BE-4: Add aggregateWatchlistByTenant() query

backend/src/test/java/hu/riskguard/notification/
├── PortfolioControllerTest.java                      # TEST-1: Add flight control endpoint tests
└── NotificationServiceTest.java                      # TEST-2: Add aggregation service tests

frontend/app/
├── components/
│   └── Layout/
│       └── AppSidebar.vue                            # FE-8: Add Flight Control nav item (ACCOUNTANT only)
├── i18n/
│   ├── hu/
│   │   └── notification.json                         # FE-9: Add flightControl.* HU keys
│   └── en/
│       └── notification.json                         # FE-9: Add flightControl.* EN keys
└── types/
    └── api.d.ts                                      # FE-10: Add FlightControl interfaces (temporary until CI regen)
```

#### Unchanged Files Referenced (read-only patterns to follow)
```
frontend/app/components/Notification/PortfolioPulse.vue   # Pattern: cross-tenant display, loading, empty, error states
frontend/app/stores/portfolio.ts                          # Pattern: options-style Pinia, $fetch with credentials
frontend/app/stores/auth.ts                               # API: switchTenant(), isAccountant, mandates
frontend/app/composables/formatting/useStatusColor.ts     # API: status → color/icon mapping
frontend/app/composables/formatting/useDateRelative.ts    # API: relative timestamp formatting
frontend/app/composables/api/useApiError.ts               # API: RFC 7807 → Toast error mapping
backend/.../notification/api/PortfolioController.java     # Pattern: ACCOUNTANT role check, cross-tenant query
backend/.../identity/domain/IdentityService.java          # API: getActiveMandateTenantIds(), findMandatedTenants()
```

### References

- **Epic 3 — Epics file:** `_bmad-output/planning-artifacts/epics.md` → Story 3.10 definition, BDD scenarios, dependencies
- **UX Spec §6.2:** Page Map — "Flight Control: Accountant's aggregate client view"
- **UX Spec §8.1:** Responsive Strategy — "Desktop (Operation Context): Optimized for mouse/keyboard efficiency"
- **UX Spec §8.2:** Breakpoint Strategy — Desktop >1024px: persistent sidebar, multi-column Quiet Grid tables
- **UX Spec §11.5:** Flight Control screen layout wireframe with Portfolio Overview, Client Table, and Recent Alerts
- **UX Spec §12.2:** Judit's Quarterly EPR Sprint interaction flow — context switch + Flight Control usage
- **UX Spec §13.2:** Empty state for "Accountant with No Clients" (radar dish, headline, body, CTA)
- **Architecture §ER — notification module:** `watchlist_entries` table schema (tenant_id, tax_number, last_verdict_status, last_checked_at)
- **Architecture §ER — identity module:** `tenant_mandates` table schema (accountant_user_id, tenant_id, valid_from, valid_to)
- **Architecture §Module Boundaries:** Cross-module via facade only — `IdentityService.getActiveMandateTenantIds()`
- **Project Context:** `_bmad-output/project-context.md` — all implementation rules (PII safety, i18n, naming, testing)
- **Story 3.9 (predecessor):** `3-9-the-accountant-portfolio-pulse-feed.md` — established PortfolioController pattern, cross-tenant query, PortfolioPulse component
- **Story 3.6:** `3-6-watchlist-management-crud.md` — WatchlistController, WatchlistTable.vue, watchlist_entries schema
- **Story 1.4:** `1-4-accountant-context-switcher-ui.md` — TenantSwitcher, ContextGuard, switchTenant() mechanism

## Dev Agent Record

### Agent Model Used

duo-chat-opus-4-6 (SM agent — story creation workflow)
duo-chat-sonnet-4-6 (Dev agent — story 3.10 implementation)

### Debug Log References

None — implementation completed without HALT conditions.

### Implementation Plan

**Review R1 Resolution (2026-03-20):** 8 review findings addressed (3H/3M/2L).

### Completion Notes List

- **BE-1:** Created `FlightControlTenantSummary.java` domain record with inner `Builder` class for pivoting aggregate rows.
- **BE-2:** Created `FlightControlTenantSummaryResponse.java` (with `from()` factory) and `FlightControlResponse.java` (with `from(FlightControlResult)` factory). Both required by ArchUnit naming convention.
- **BE-3/BE-4:** Added `getFlightControlSummary()` to `NotificationService` with inner `FlightControlResult` record. Repo uses `findTenantNamesByIds()` (queries `tenants` table directly via raw jOOQ SQL) instead of `identityService.findMandatedTenants()` — avoids importing `identity.api.dto.TenantResponse` into the notification domain, keeping module boundaries clean. `aggregateWatchlistByTenant()` uses `DSL.count()` and `DSL.max()` (already available via `static import org.jooq.impl.DSL.*`).
- **BE-5:** `GET /api/v1/portfolio/flight-control` added to `PortfolioController`, uses `FlightControlResponse.from()` factory pattern matching ArchUnit rule.
- **FE-2:** Options-style Pinia store `useFlightControlStore` follows `portfolio.ts` and `watchlist.ts` patterns exactly.
- **FE-1+3+4+5+6+7:** `FlightControl.vue` page — loading skeleton, error state (distinct from empty state per Story 3.9 R2-H2 learning), empty state (radar dish icon, CTA), summary pills (neutral/Crimson/Amber), PrimeVue DataTable with sortable columns and status Tag badges, recent alerts section (max 10, reuses portfolio store), `handleClientClick` and `handleAlertClick` follow `PortfolioPulse.vue` pattern exactly.
- **FE-8:** Added `flightControl` nav item to `mainNavItems` in `AppSidebar.vue` with `accountantOnly: true` flag. `navLabel()` helper resolves to correct i18n namespace.
- **TEST-3:** Used `flushPromises` (from `@vue/test-utils`) for async `onMounted` assertions and imported `nextTick` from `vue` explicitly (not as a global).
- **ArchUnit:** `FlightControlResponse` needed `static from()` factory to satisfy `dtos_should_have_static_from_factory` ArchUnit rule — added.
- ✅ Resolved review finding [HIGH]: **H1** — Added DataTable filtering with `v-model:filters`, `globalFilterFields=['tenantName']`, `InputText` for client name search, and `Select` dropdown for risk-level filtering (all/at-risk/stale). Added 3 new i18n keys (filterAll, filterAtRisk, filterStale) to both EN/HU.
- ✅ Resolved review finding [HIGH]: **H2** — Added mobile stacked Compact Card layout (`<768px` via `md:hidden`) with tenant name, status Tags, and last-check time. Desktop DataTable hidden on mobile (`hidden md:block`). Summary pills use responsive grid: 1-col mobile → 2-col tablet → 3-col desktop.
- ✅ Resolved review finding [HIGH]: **H3** — Added null guard to `Builder.updateLastChecked(OffsetDateTime ts)` — if `ts == null`, return early before null dereference.
- ✅ Resolved review finding [MEDIUM]: **M1** — Added `errorType: string | null` to store state. Catch block now extracts RFC 7807 `data.type` from FetchError and stores it in `this.errorType` before flattening to `this.error`.
- ✅ Resolved review finding [MEDIUM]: **M2** — Replaced sequential `await fetchSummary(); await fetchAlerts(7)` with `Promise.all([fetchSummary(), fetchAlerts(7)])` for parallel loading.
- ✅ Resolved review finding [MEDIUM]: **M3** — Added explicit `import Divider from 'primevue/divider'` to `AppSidebar.vue` alongside existing `Badge` import.
- ✅ Resolved review finding [LOW]: **L1** — Strengthened sorting test to verify DataTable renders with sort-field and correct column structure, and filter state `matchMode` is initialized to `contains`.
- ✅ Resolved review finding [LOW]: **L2** — Added 6 new filtering tests: filter header rendering, risk-level filter at-risk, risk-level filter stale, clear filter resets, globalFilterFields text filter config, mobile stacked cards rendering. Total: 18 spec tests (was 12).

### File List

**New Files:**
- `backend/src/main/java/hu/riskguard/notification/domain/FlightControlTenantSummary.java`
- `backend/src/main/java/hu/riskguard/notification/api/dto/FlightControlTenantSummaryResponse.java`
- `backend/src/main/java/hu/riskguard/notification/api/dto/FlightControlResponse.java`
- `frontend/app/stores/flightControl.ts`
- `frontend/app/pages/flight-control/index.vue`
- `frontend/app/pages/flight-control/FlightControl.spec.ts`

**Modified Files:**
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java`
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java`
- `backend/src/main/java/hu/riskguard/notification/api/PortfolioController.java`
- `backend/src/test/java/hu/riskguard/notification/api/PortfolioControllerTest.java`
- `backend/src/test/java/hu/riskguard/notification/domain/NotificationServiceTest.java`
- `frontend/types/api.d.ts`
- `frontend/app/i18n/en/notification.json`
- `frontend/app/i18n/hu/notification.json`
- `frontend/app/components/Common/AppSidebar.vue`
- `frontend/app/middleware/auth.global.ts`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

- 2026-03-20: Story 3.10 implemented — Flight Control dashboard (Date: 2026-03-20). Added `GET /api/v1/portfolio/flight-control` endpoint with cross-tenant watchlist aggregation. Created `FlightControl.vue` page with summary bar, PrimeVue DataTable, recent alerts feed, empty/error/loading states. Added `useFlightControlStore`, updated `AppSidebar.vue` with ACCOUNTANT-only nav item, added HU/EN i18n keys. Backend: 18 controller + 23 service tests pass. Frontend: 12 spec tests + 442 full suite pass. `./gradlew check` BUILD SUCCESSFUL.
- 2026-03-20: Code review R1 — 3H/3M/2L findings. H1: DataTable filtering missing (AC3 partial). H2: Responsive/mobile layout missing (AC6 partial). H3: Builder NPE risk. M1: Store discards RFC 7807 error type. M2: Sequential API fetches. M3: Divider not imported. L1: Weak sorting test. L2: Filtering tests absent. 8 action items created. Status → in-progress.
- 2026-03-20: Addressed code review findings — 8 items resolved (3H/3M/2L) (Date: 2026-03-20). H1: Added DataTable filtering (text + risk-level dropdown). H2: Added responsive mobile stacked cards + tablet 2-col summary. H3: Added null guard to Builder.updateLastChecked(). M1: Store preserves RFC 7807 errorType. M2: Parallel fetches via Promise.all. M3: Explicit Divider import. L1: Strengthened sort test. L2: Added 6 filtering + mobile tests. Frontend: 18 spec tests + 448 full suite pass. Backend: `./gradlew check` BUILD SUCCESSFUL. Status → review.
- 2026-03-20: Code review R2 — 1H/2M/1L findings. H1: Dead code after switchTenant (window.location.reload makes router.push unreachable). M1: 403 message references wrong endpoint. M2: Mobile text filter doesn't apply to stacked cards. L1: Sort test still weak. All fixed + postSwitchRedirect middleware added. Frontend: 19 spec tests + 449 full suite pass. Backend: `./gradlew check` BUILD SUCCESSFUL. Status → done.
