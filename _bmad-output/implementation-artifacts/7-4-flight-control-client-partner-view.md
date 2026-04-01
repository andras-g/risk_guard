# Story 7.4: Flight Control — Client Partner View

Status: done

## Story

As an Accountant,
I want to click on a client in my Flight Control list and see all of their watchlisted partners and risk statuses — in a read-only view without switching my tenant context —
So that I can assess a client's partner portfolio before deciding to switch into their account.

## Acceptance Criteria

1. **"View Partners →" link in Flight Control table**
   - Given the accountant is on `/flight-control`
   - When the client DataTable renders
   - Then each row has a "View Partners →" text link in the last column
   - And the client name in the first column is also clickable (underlined on hover) with the same action
   - And clicking either navigates to `/flight-control/[tenantId]` (no tenant switch)

2. **Client Partner View page loads correctly**
   - Given the accountant navigates to `/flight-control/[tenantId]`
   - When the page loads
   - Then a breadcrumb "Flight Control / [Client Name]" is shown (Flight Control link navigates back)
   - And a read-only amber info banner is displayed: "Read-only view. To manage partners or trigger screenings, switch to client context."
   - And a summary stat bar shows the client's Reliable / At Risk / Stale counts
   - And a partner table lists all the client's watchlisted partners

3. **Partner table (read-only)**
   - Columns: Company Name + tax number (monospace below), Status badge, Trend arrow, Last Screened, View →
   - "View →" icon navigates to `/screening/[taxNumber]` (after tenant context switch — see AC 5)
   - No add/remove/export actions are available (read-only)
   - Filtering by status (All / Reliable / At Risk / Stale) and name search work client-side

4. **Authorization**
   - Given the accountant's JWT user ID does NOT have an active mandate over the requested `tenantId`
   - Then the backend returns HTTP 403
   - And the frontend shows an error state with a "Back to Flight Control" link

5. **"Switch to Client →" button**
   - Given the Client Partner View is displayed
   - When the accountant clicks "Switch to Client →"
   - Then the existing `authStore.switchTenant(tenantId)` flow is triggered (identical to the current Flight Control row click behaviour)
   - And on success the app reloads and navigates to `/dashboard` in the client's context

6. **"View →" partner row action**
   - Given the accountant clicks "View →" on a partner row in read-only mode
   - Then a confirmation modal appears: "Switch to [Client Name]'s context to view this screening?"
   - And confirming triggers `authStore.switchTenant(tenantId)` with `postSwitchRedirect = /screening/[taxNumber]`

7. **Mobile: stacked card layout**
   - On screens < 768px, the partner table collapses to stacked cards (consistent with existing Flight Control mobile pattern)

## Tasks / Subtasks

- [x] T1 — Backend: new endpoint `GET /api/v1/portfolio/clients/{clientTenantId}/partners` (AC: 2, 4)
  - [x] Add to **`PortfolioController.java`** (NOT a new FlightControlController — none exists; all flight control is in `PortfolioController` at `/api/v1/portfolio/*`)
  - [x] New method: `@GetMapping("/clients/{clientTenantId}/partners")`
  - [x] Follow the existing controller pattern: `requireAccountantRole(jwt)` → `resolveUserId(jwt)` → delegate to `NotificationService`
  - [x] Delegate to a new `notificationService.getClientPartners(userId, clientTenantId)` method (see T2)
  - [x] Return `List<WatchlistEntryResponse>` mapped via `WatchlistEntryResponse::from`

- [x] T2 — Backend: `NotificationService.getClientPartners()` (AC: 4)
  - [x] Add `@Transactional(readOnly = true) public List<WatchlistEntry> getClientPartners(UUID userId, UUID clientTenantId)` to `NotificationService`
  - [x] **Mandate check**: call `identityService.getActiveMandateTenantIds(userId)` — if result does NOT contain `clientTenantId`, throw `new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate over requested tenant")`
  - [x] **Do NOT create `TenantMandateRepository.hasMandateOver()`** — there is no such class; mandate checking MUST go through `IdentityService.getActiveMandateTenantIds()` (already used by `getFlightControlSummary()`)
  - [x] Delegate to existing `notificationRepository.findByTenantId(clientTenantId)` (already exists and maps `previousVerdictStatus` after Story 7.3)
  - [x] Map records via existing `toDomain()` static helper (line ~531 in `NotificationService.java`)

- [x] T3 — Frontend: `useClientPartners` composable (AC: 2, 3, 4)
  - [x] File: `frontend/app/composables/api/useClientPartners.ts`
  - [x] `fetchClientPartners(clientTenantId: string): Promise<WatchlistEntryResponse[]>`
  - [x] Calls `GET /api/v1/portfolio/clients/{clientTenantId}/partners` (NOT `/api/v1/flight-control/...`)
  - [x] Handles 403 (sets `error` ref to `'forbidden'`)

- [x] T4 — Frontend: new page `flight-control/[clientId].vue` (AC: 2–7)
  - [x] File: `frontend/app/pages/flight-control/[clientId].vue`
  - [x] `definePageMeta({ middleware: 'auth' })` at top
  - [x] Accountant guard: `const { isAccountant } = storeToRefs(authStore)` + redirect non-accountants to `/dashboard` in `onMounted` (same pattern as `index.vue` lines 69–79)
  - [x] Client name for breadcrumb: read from `flightControlStore.tenants` by `route.params.clientId` (already loaded); do NOT use query params
  - [x] Breadcrumb, read-only amber banner, stat bar (computed from partners array: count by `currentVerdictStatus`)
  - [x] Partner table with columns: Company Name, Status badge, Trend arrow (reuse `trendDirection()` + `STATUS_SEVERITY` map from 7.3), Last Screened (`useDateRelative`), View →
  - [x] Filter bar: name search (`InputText`) + status dropdown (`Select`)
  - [x] "Switch to Client →" button: `sessionStorage.setItem('postSwitchRedirect', '/dashboard')` + `authStore.switchTenant(route.params.clientId)` (same pattern as `handleClientClick` in `index.vue` lines 88–110)
  - [x] "View →" per row: opens confirmation modal → on confirm, `sessionStorage.setItem('postSwitchRedirect', '/screening/' + taxNumber)` + `authStore.switchTenant(clientId)`
  - [x] Mobile stacked cards (consistent with `index.vue` pattern)
  - [x] Error state for 403 with "Back to Flight Control" `NuxtLink`

- [x] T5 — Frontend: update `flight-control/index.vue` — "View Partners →" link + refactor row-click (AC: 1)
  - [x] **Remove `@row-click="handleClientClick($event.data)"`** from the `<DataTable>` — row-click fires on ANY cell click, which ambiguity AC 1 is designed to fix
  - [x] Add last column to DataTable: `NuxtLink` to `/flight-control/[tenantId]` reading `data.tenantId` — label "View Partners →" with `t('notification.flightControl.viewPartners')`
  - [x] Make client name in first column a `NuxtLink` to `/flight-control/[data.tenantId]` (same target)
  - [x] Add an explicit "Switch →" icon button in the Actions column that calls existing `handleClientClick(data)` — keep `handleClientClick` function unchanged
  - [x] Mobile cards (`data-testid="mobile-tenant-card"`): add "View Partners →" `NuxtLink`; keep existing click on tenant name as the switch action

- [x] T6 — i18n keys (AC: 2, 4, 5, 6)
  - [x] Add to `en/notification.json` and `hu/notification.json`:
    - `notification.flightControl.viewPartners` — "View Partners →"
    - `notification.flightControl.readOnlyBanner` — "Read-only view. To manage partners or trigger screenings, switch to client context."
    - `notification.flightControl.switchToClient` — "Switch to Client →"
    - `notification.flightControl.switchConfirmTitle` — "Switch to client context?"
    - `notification.flightControl.switchConfirmBody` — "Switch to [Client Name]'s context to view this screening?"
    - `notification.flightControl.forbiddenError` — "You do not have access to this client."
  - [x] Keep files alphabetically sorted within objects

- [x] T7 — Tests (AC: 1–6)
  - [x] **Backend integration test**: added 4 tests to `PortfolioControllerTest` — 200 with partner list for mandated tenant, 403 propagated from service, 403 for non-accountant, empty list
  - [x] **Frontend**: 13-test spec `ClientPartnerView.spec.ts` — partner table, breadcrumb, 403 error state, switch-to-client, view-partner modal, filters, mobile cards

## Dev Notes

### CRITICAL: Controller location — `PortfolioController.java`, NOT `FlightControlController.java`

**No `FlightControlController.java` exists.** All Flight Control endpoints live in `PortfolioController.java` (`/api/v1/portfolio/*`). The new endpoint path is:

```
GET /api/v1/portfolio/clients/{clientTenantId}/partners
```

Follow the existing controller pattern exactly:
```java
@GetMapping("/clients/{clientTenantId}/partners")
public List<WatchlistEntryResponse> getClientPartners(
        @PathVariable UUID clientTenantId,
        @AuthenticationPrincipal Jwt jwt) {
    requireAccountantRole(jwt);
    UUID userId = resolveUserId(jwt);
    List<WatchlistEntry> entries = notificationService.getClientPartners(userId, clientTenantId);
    return entries.stream().map(WatchlistEntryResponse::from).toList();
}
```

### CRITICAL: Mandate check — use `IdentityService.getActiveMandateTenantIds()`, NOT a new repository

**Do NOT create `TenantMandateRepository.hasMandateOver()`** — it doesn't exist and would bypass the existing mandate architecture. The correct approach mirrors `getFlightControlSummary()` (lines 103–167 of `NotificationService.java`):

```java
@Transactional(readOnly = true)
public List<WatchlistEntry> getClientPartners(UUID userId, UUID clientTenantId) {
    List<UUID> mandatedTenantIds = identityService.getActiveMandateTenantIds(userId);
    if (!mandatedTenantIds.contains(clientTenantId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate over requested tenant");
    }
    return notificationRepository.findByTenantId(clientTenantId).stream()
            .map(NotificationService::toDomain)
            .collect(Collectors.toList());
}
```

### Backend backend work is minimal — `findByTenantId()` and `getWatchlistEntries()` already exist

`NotificationRepository.findByTenantId(UUID tenantId)` already exists (line 114) and already returns `previousVerdictStatus` after Story 7.3. `NotificationService.getWatchlistEntries(UUID tenantId)` (line 382) also already exists. The service method for T2 is essentially a mandate-gated wrapper around the existing `getWatchlistEntries()`.

### Frontend URL for `useClientPartners`

```ts
// CORRECT:
const data = await $fetch<WatchlistEntryResponse[]>(
  `/api/v1/portfolio/clients/${clientTenantId}/partners`
)

// WRONG — this URL pattern does not exist in the backend:
// /api/v1/flight-control/clients/{tenantId}/partners
```

### `postSwitchRedirect` pattern (from `index.vue` lines 88–110)

```ts
sessionStorage.setItem('postSwitchRedirect', '/dashboard')
await authStore.switchTenant(tenantId)
// switchTenant() calls window.location.reload() — code after this is unreachable
```

For AC 6 (View → opens screening after switch):
```ts
sessionStorage.setItem('postSwitchRedirect', `/screening/${taxNumber}`)
await authStore.switchTenant(clientId)
```

### Client name resolution — read from `flightControlStore.tenants`

`FlightControlTenantSummaryResponse` has `tenantId` and `tenantName`. In `index.vue`, `flightControlStore.fetchSummary()` is called on mount, so by the time the user navigates to `[clientId].vue`, the store already has the tenants list. Read the name client-side:

```ts
const flightControlStore = useFlightControlStore()
const clientName = computed(() =>
  flightControlStore.tenants.find(t => t.tenantId === route.params.clientId)?.tenantName ?? ''
)
```

**Do NOT use query params** for the name — encoding issues and stale state risks.

### `storeToRefs` pattern — follow `index.vue` exactly

```ts
const { isAccountant } = storeToRefs(authStore)
// NOT: authStore.isAccountant (loses reactivity)
```

### Accountant guard in `[clientId].vue` — replicate `index.vue` pattern

```ts
onMounted(async () => {
  if (!isAccountant.value) {
    router.push('/dashboard')
    return
  }
  // fetch partners here
})
```

### Trend arrow column — reuse Story 7.3 `STATUS_SEVERITY` pattern

`WatchlistEntryResponse` now has `previousVerdictStatus` (added by Story 7.3). The `[clientId].vue` partner table needs the same `STATUS_SEVERITY` map and `trendDirection()` helper as `WatchlistTable.vue`. Keep it local to the component (do NOT extract to shared composable — Story 7.1/7.2/7.3 pattern). Severity order: `RELIABLE(0) < INCOMPLETE(1) < UNAVAILABLE(2) < TAX_SUSPENDED(3) < AT_RISK(4)`.

### Spec file pattern — follow `FlightControl.spec.ts` exactly

The `FlightControl.spec.ts` (in `frontend/app/pages/flight-control/`) uses:
- `vi.mock('~/stores/auth', () => ({ useAuthStore: () => ({ isAccountant: mockIsAccountant, ... }) }))` — store factory returns plain object with `ref()` values
- `vi.stubGlobal('useI18n', ...)`, `vi.stubGlobal('definePageMeta', vi.fn())`, `vi.stubGlobal('useRouter', ...)`
- `vi.stubGlobal('useRoute', () => ({ params: { clientId: '...' } }))` — add this for `[clientId].spec.ts`

**Do NOT use `storeToRefs` in spec mocks** — the store factory returns a plain object; the component's `storeToRefs()` call works because the values ARE already refs. This is the established pattern for all flight-control specs.

### Previous Story Intelligence (Stories 7.1–7.3)

- **`storeToRefs` fails in tests** when the store mock returns plain objects with null reactive values (e.g. `ref(null)` causes TypeScript narrowing issues). Use `computed(() => store.property)` wrappers if needed — simpler and more test-friendly. [Source: Story 7.1]
- **Nuxt auto-imports NOT available in spec files.** `useDateRelative`, `useStatusColor`, `useClientPartners` must be stubbed via `vi.mock()` or `vi.stubGlobal()` before component imports. [Source: Story 7.1]
- **`$fetch` mocking:** use `vi.stubGlobal('$fetch', vi.fn())` in spec files.
- **`useIdentityStore` is unreliable** in HttpOnly-cookie auth flow. Use `useAuthStore().isAccountant` for role checks. [Source: Story 7.1]
- **`STATUS_SEVERITY` local map pattern confirmed**: Stories 7.1/7.2/7.3 all define LOCAL maps — do NOT extract. [Source: Stories 7.1–7.3]
- **`previousVerdictStatus` is now in `WatchlistEntryResponse`** (added by Story 7.3 — `api.d.ts` updated, backend fully propagated). Available for Trend column in the partner table with no additional backend changes.
- **`WatchlistEntryResponse` now has `currentVerdictStatus` (not `lastVerdictStatus`)** — the field is named `currentVerdictStatus` in the frontend type.
- **Flyway next available filename**: `V20260401_002__...` (V20260401_001__ is taken by Story 7.3's migration). This story has no DB migration.

### Git Intelligence (recent commits)

- `feat(7.3): Watchlist Table Enrichment` — added `previousVerdictStatus` column to `watchlist_entries`, propagated through domain layer, added Trend + Last Screened columns to `WatchlistTable.vue`
- `feat(7.2): Partner Detail Slide-Over Drawer` — added `WatchlistPartnerDrawer.vue`, `row-click` guard in `WatchlistTable`, wired drawer into `watchlist/index.vue`
- `feat(7.1): Risk Pulse Dashboard redesign` — added `DashboardStatBar`, `DashboardNeedsAttention`, `DashboardAlertFeed`; introduced local `STATUS_SEVERITY` map pattern

### Architecture Compliance Checklist

- [ ] New endpoint in **`PortfolioController.java`** under `/api/v1/portfolio/clients/{clientTenantId}/partners` ✓
- [ ] Mandate check via `IdentityService.getActiveMandateTenantIds()` — NOT a new repository ✓
- [ ] `ResponseStatusException(FORBIDDEN)` pattern (not custom exception classes) — matches existing `PortfolioController` pattern ✓
- [ ] DTO: existing `WatchlistEntryResponse` Java record with `static from()` factory — no new DTO needed ✓
- [ ] No cross-module table access from `NotificationRepository` ✓
- [ ] No Flyway migration needed — no schema change ✓
- [ ] Frontend: `tsc --noEmit` must pass after adding `[clientId].vue` and `useClientPartners.ts`
- [ ] `NamingConventionTest` — no new endpoints to check for naming (URL slug uses `kebab-case` for resources which is standard)

### Key files to touch

| File | Change |
|------|--------|
| `backend/…/notification/api/PortfolioController.java` | Add `GET /clients/{clientTenantId}/partners` endpoint |
| `backend/…/notification/domain/NotificationService.java` | Add `getClientPartners(userId, clientTenantId)` |
| `frontend/app/composables/api/useClientPartners.ts` | New composable |
| `frontend/app/pages/flight-control/[clientId].vue` | New page |
| `frontend/app/pages/flight-control/index.vue` | Add "View Partners →" link, refactor row-click |
| `frontend/app/i18n/en/notification.json` | New flightControl keys |
| `frontend/app/i18n/hu/notification.json` | Same keys in Hungarian |

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (2026-04-01)

### Debug Log References

No blocking issues. All tasks completed in single session.

### Completion Notes List

- **T1+T2 (Backend)**: Added `GET /api/v1/portfolio/clients/{clientTenantId}/partners` to `PortfolioController`. Added `getClientPartners(userId, clientTenantId)` to `NotificationService` — mandate-gated wrapper around existing `findByTenantId()` + `toDomain()`. Followed exact existing controller pattern.
- **T3 (Composable)**: Created `useClientPartners.ts` — `$fetch` with `credentials: include`, maps 403 → `error = 'forbidden'`, other errors → `error = 'unknown'`.
- **T4 (New page)**: `[clientId].vue` — breadcrumb from `flightControlStore.tenants`, amber banner, stat bar (computed from partners), partner table with STATUS_SEVERITY/trendDirection local map, name+status filter, Switch button, View→ confirmation modal, mobile stacked cards, forbidden error state.
- **T5 (index.vue update)**: Removed `@row-click` from DataTable; client name column is now a NuxtLink to `/flight-control/[tenantId]`; added "View Partners →" NuxtLink + "Switch →" icon button as last column; mobile cards now show tenant name (clickable for switch) + "View Partners →" NuxtLink.
- **T6 (i18n)**: 6 keys added alphabetically to both `en/notification.json` and `hu/notification.json`.
- **T7 (Tests)**: 4 backend tests in `PortfolioControllerTest` (200 with partners, 403 from service, 403 for non-accountant, empty list). 13 frontend tests in `ClientPartnerView.spec.ts`.
- **Full suite**: 662 frontend tests green, backend notification suite 93 tests green.
- **R1 review follow-ups (2026-04-01)**: ✅ Resolved [Decision] INCOMPLETE → Stale (already correct). ✅ Resolved [Patch] switchTenant error handling: try/catch + toast + sessionStorage cleanup in `handleSwitchToClient` and `confirmViewPartner`. ✅ Resolved [Patch] `clientName` empty on direct navigation: `onMounted` calls `fetchSummary()` when tenants empty. ✅ Resolved [Patch] Mobile `<p>` keyboard accessibility: added `role="button"`, `tabindex="0"`, `@keydown.enter`. 4 new tests added (17 total in spec). 666 frontend tests green.

### File List

- `backend/src/main/java/hu/riskguard/notification/api/PortfolioController.java` — added `getClientPartners` endpoint
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java` — added `getClientPartners` service method
- `backend/src/test/java/hu/riskguard/notification/api/PortfolioControllerTest.java` — added 4 client-partners tests
- `frontend/app/composables/api/useClientPartners.ts` — new composable
- `frontend/app/pages/flight-control/[clientId].vue` — new page
- `frontend/app/pages/flight-control/ClientPartnerView.spec.ts` — new spec (13 tests)
- `frontend/app/pages/flight-control/index.vue` — removed row-click, added View Partners column + mobile link, client name as NuxtLink
- `frontend/app/i18n/en/notification.json` — 6 new flightControl keys
- `frontend/app/i18n/hu/notification.json` — 6 new flightControl keys (Hungarian)

### Review Findings (R1 — 2026-04-01)

- [x] [Review][Decision] INCOMPLETE partners excluded from stat bar and stale filter — Decision: lump into "Stale" (option A). Verified: `staleCount` and stale filter already include `INCOMPLETE || UNAVAILABLE` — implementation was correct. No code change needed.
- [x] [Review][Patch] switchTenant failure not handled in `[clientId].vue` — Added try/catch to `handleSwitchToClient` and `confirmViewPartner`: on failure, clears `postSwitchRedirect` from sessionStorage and shows toast via `useApiError`+`useToast` (same pattern as `index.vue:handleClientClick`). [`[clientId].vue`]
- [x] [Review][Patch] `clientName` empty when navigating directly to `/flight-control/[id]` — `onMounted` now calls `flightControlStore.fetchSummary()` when `tenants.length === 0` before fetching partners. [`[clientId].vue`]
- [x] [Review][Patch] Mobile tenant name `<p @click>` in `index.vue` has no keyboard handler — Added `role="button"`, `tabindex="0"`, `@keydown.enter` to the `<p>` element. [`index.vue`]
- [x] [Review][Defer] Rate limiting / audit log absent on new `GET /clients/{clientTenantId}/partners` endpoint — pre-existing concern, out of scope for this story [`PortfolioController.java`]
- [x] [Review][Defer] `getActiveMandateTenantIds` null-return NPE in `getClientPartners` — same pre-existing unguarded pattern as in `getFlightControlSummary`; DEFER [`NotificationService.java` line 387]
- [x] [Review][Defer] `confirmViewPartner`/Escape key race — Dialog emits `update:visible` on Escape while `await switchTenant` is in flight; can cause `/screening/null` redirect; edge case, not customer-impacting in practice
- [x] [Review][Defer] No integration test for `findByTenantId` with `previousVerdictStatus` column — pre-existing test gap; `NotificationRepositoryIntegrationTest` does not exercise `findByTenantId`
- [x] [Review][Defer] `trendDirection` returns `'stable'` for two identical unknown status strings — both unknown values map to severity 99, `c === p` → `'stable'`; low risk as future statuses would also be caught by `verdictLabel` fallback

## Change Log

- 2026-03-31: Story created (moved from Epic 6 planning). Status → ready-for-dev.
- 2026-04-01: Story enriched with critical corrections (controller location, mandate check approach, URL pattern, backend method reuse, test patterns from FlightControl.spec.ts, 7.1–7.3 learnings).
- 2026-04-01: Implementation complete. All 7 tasks done. 662 frontend + backend notification suite green. Status → review.
- 2026-04-01: Code review R1 — 1 decision-needed, 3 patch, 5 deferred, 8 dismissed. Status → in-progress.
- 2026-04-01: Addressed code review findings — 4 items resolved (1 decision + 3 patches). 666 frontend tests green. Status → review.
- 2026-04-01: Code review R2 — 2 patches applied: (1) replaced FQN ResponseStatusException/HttpStatus with proper imports in NotificationService.java; (2) wrapped fetchSummary() in try/catch in [clientId].vue onMounted so fetchClientPartners always runs. 666 frontend + backend notification suite green. 5 E2E tests green. Status → done.
