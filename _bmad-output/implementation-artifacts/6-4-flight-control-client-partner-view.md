# Story 6.4: Flight Control — Client Partner View

Status: ready-for-dev

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
   - Given the accountant's `home_tenant_id` does NOT have a mandate over the requested `tenantId`
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

- [ ] T1 — Backend: new endpoint `GET /api/v1/flight-control/clients/{tenantId}/partners` (AC: 2, 4)
  - [ ] Add to `FlightControlController.java` (or create if not yet extracted from existing controller)
  - [ ] Path: `GET /api/v1/flight-control/clients/{clientTenantId}/partners`
  - [ ] Auth: extract `home_tenant_id` from JWT (use `JwtUtil.requireUuidClaim` per Story 6.0); verify the home tenant has a mandate over `clientTenantId` via `tenant_mandates` table — throw 403 if not
  - [ ] Response: `List<WatchlistEntryResponse>` — reuse existing DTO; query `watchlist_entries WHERE tenant_id = clientTenantId` via `NotificationRepository` or `WatchlistService` facade
  - [ ] Do NOT use `active_tenant_id` from JWT — this endpoint is explicitly cross-tenant read using `home_tenant_id`

- [ ] T2 — Backend: mandate validation helper (AC: 4)
  - [ ] In `TenantMandateRepository` (or `AccountantService`), add `boolean hasMandateOver(UUID homeTenantId, UUID clientTenantId)` — single jOOQ `EXISTS` query on `tenant_mandates`
  - [ ] Reuse in T1

- [ ] T3 — Frontend: `useClientPartners` composable (AC: 2, 3, 4)
  - [ ] File: `frontend/app/composables/api/useClientPartners.ts`
  - [ ] `fetchClientPartners(clientTenantId: string): Promise<WatchlistEntryResponse[]>`
  - [ ] Calls `GET /api/v1/flight-control/clients/{tenantId}/partners`
  - [ ] Handles 403 (sets `error` ref to `'forbidden'`)

- [ ] T4 — Frontend: new page `flight-control/[clientId].vue` (AC: 2–7)
  - [ ] File: `frontend/app/pages/flight-control/[clientId].vue`
  - [ ] `definePageMeta({ middleware: 'auth' })` — accountant guard: redirect non-accountants to `/dashboard`
  - [ ] Breadcrumb, read-only amber banner, stat bar (computed from partners array), partner table
  - [ ] Filter bar: name search (InputText) + status dropdown (Select)
  - [ ] "Switch to Client →" button: calls `authStore.switchTenant(route.params.clientId)`
  - [ ] "View →" per row: opens confirmation modal → on confirm, stores `postSwitchRedirect` and calls `authStore.switchTenant`
  - [ ] Mobile stacked cards (consistent with `flight-control/index.vue` pattern)
  - [ ] Error state for 403 with back link

- [ ] T5 — Frontend: update `flight-control/index.vue` — "View Partners →" link (AC: 1)
  - [ ] Add last column to DataTable with "View Partners →" `NuxtLink` to `/flight-control/[tenantId]`
  - [ ] Make client name in first column a `NuxtLink` with same target
  - [ ] Keep existing row-click (`handleClientClick`) as the "Switch to Client →" action — move it to a separate icon button (person/switch icon) in the Actions column; remove `@row-click` from the DataTable to avoid ambiguity
  - [ ] Mobile cards: add "View Partners →" link; existing card tap triggers client switch (keep)

- [ ] T6 — i18n keys (AC: 2, 4, 5, 6)
  - [ ] Add to `en/` and `hu/`: `notification.flightControl.viewPartners`, `notification.flightControl.readOnlyBanner`, `notification.flightControl.switchToClient`, `notification.flightControl.switchConfirmTitle`, `notification.flightControl.switchConfirmBody`, `notification.flightControl.forbiddenError`
  - [ ] Keep files alphabetically sorted

- [ ] T7 — Tests (AC: 1–6)
  - [ ] Backend: integration test for new endpoint — assert 200 with correct partner list, 403 for non-mandated tenant
  - [ ] Frontend: spec for `[clientId].vue` — mock `useClientPartners`, assert partner table renders, breadcrumb correct, 403 shows error state, switch button triggers `authStore.switchTenant`

## Dev Notes

### Cross-tenant read authorization model
This endpoint uses the JWT's `home_tenant_id` (not `active_tenant_id`) to verify mandate ownership. Pattern:
```java
UUID homeTenantId = JwtUtil.requireUuidClaim(jwt, "home_tenant_id");
UUID clientTenantId = UUID.fromString(pathVariable);
if (!tenantMandateRepository.hasMandateOver(homeTenantId, clientTenantId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate over requested tenant");
}
```
This is the ONLY endpoint that uses `home_tenant_id` for cross-tenant reads — all other endpoints use `active_tenant_id`. Document this in the controller Javadoc.

### Existing `handleClientClick` in flight-control
Currently `@row-click` on the DataTable fires `handleClientClick`, which does a tenant switch. After T5, the row-click is removed from the DataTable. Replace it with:
- Client name column → `NuxtLink` to `/flight-control/[tenantId]`
- "View Partners →" last column → same `NuxtLink`
- New icon button in an "Actions" column → calls existing `handleClientClick(tenant)`
This prevents accidental tenant switches when the user just wants to view partners.

### `postSwitchRedirect` pattern (existing, from Story 3.10)
```ts
sessionStorage.setItem('postSwitchRedirect', `/screening/${taxNumber}`)
await authStore.switchTenant(tenantId)
```
Reuse this exact pattern for AC 6 (View → partner row action).

### Client name resolution
`FlightControlTenantSummaryResponse` has `tenantName`. The `[clientId].vue` page needs the client name for the breadcrumb before partners load. Pass it as a query param from the link: `/flight-control/[tenantId]?name=Kovács+Kft`, or store it in the `flightControlStore` (already loaded). Prefer reading from `flightControlStore.tenants` by `tenantId` — avoids query param encoding issues.

### Key files to touch
| File | Change |
|------|--------|
| `FlightControlController.java` | New endpoint |
| `TenantMandateRepository.java` (or similar) | `hasMandateOver` query |
| `frontend/app/composables/api/useClientPartners.ts` | New |
| `frontend/app/pages/flight-control/[clientId].vue` | New |
| `frontend/app/pages/flight-control/index.vue` | Add "View Partners →", refactor row-click |
| i18n files | New keys |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
