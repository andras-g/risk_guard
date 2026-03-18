# Story 3.11: SEO Gateway Stubs (Public Company Pages)
> Moved from Story 2.6 on 2026-03-16 — growth/conversion feature fits better in Epic 3.

Status: ready-for-dev

## Story

As a Product Owner,
I want public, indexable company pages with structured data for every Hungarian company searched,
so that organic search traffic drives new users to the platform.

## Acceptance Criteria

1. **Given** a previously searched Hungarian company, **When** a search engine or unauthenticated user visits `/company/{taxNumber}`, **Then** Nuxt renders the page via SSR/ISR with the company name, tax number, and a generic status indicator.

2. **Given** the public company page renders, **Then** the page includes JSON-LD structured data (Organization schema) with the company's publicly available information (name, tax number, address if available).

3. **Given** the public company page renders, **Then** the page includes a clear CTA to "Check this company's live risk status" that requires login to proceed.

4. **Given** the public company page renders, **Then** the page does NOT expose any tenant-specific verdict data, audit hash, or per-tenant risk signals.

5. **Given** Nuxt `routeRules` configuration, **Then** public company pages at `/company/**` use ISR (Incremental Static Regeneration) while all authenticated routes use SPA mode — no separate SSR service needed.

6. **Given** a company has never been searched (no snapshot exists in the database), **When** someone navigates to `/company/{taxNumber}`, **Then** the backend returns a 404 and the Nuxt page renders a "Company not found — search now" prompt (not a broken page).

7. **Given** a new backend `GET /api/v1/public/companies/{taxNumber}` endpoint, **When** called with a valid tax number, **Then** it returns public-safe company data (name, tax number, address) WITHOUT authentication, without tenant context, and WITHOUT any verdict/audit information.


## Tasks / Subtasks

- [ ] **Task 1: Backend — Public Company Data Endpoint** (AC: #6, #7)
  - [ ] 1.1 Create `PublicCompanyResponse` record in `hu.riskguard.screening.api.dto` with fields: `taxNumber` (String), `companyName` (String | null), `address` (String | null) — NO verdict, NO hash, NO tenant data
  - [ ] 1.2 Create `GET /api/v1/public/companies/{taxNumber}` endpoint in `ScreeningController` — NO `@AuthenticationPrincipal`, no tenant check
  - [ ] 1.3 Add `getPublicCompanyData(String taxNumber)` method to `ScreeningService` — queries `company_snapshots` WITHOUT tenant filter (cross-tenant public data, most recent snapshot for tax number)
  - [ ] 1.4 Add `findMostRecentPublicSnapshot(String taxNumber)` to `ScreeningRepository` — queries WITHOUT `tenant_id` filter, returns only `tax_number`, `snapshot_data` (for name/address extraction), `checked_at`
  - [ ] 1.5 Permit `/api/v1/public/**` in `SecurityConfig.java` — add `requestMatchers("/api/v1/public/**").permitAll()` BEFORE the authenticated catch-all
  - [ ] 1.6 Return 404 (`ResponseStatusException(HttpStatus.NOT_FOUND)`) if no snapshot found for that tax number
  - [ ] 1.7 Add `ScreeningControllerPublicTest.java` — test public endpoint: returns 200 with company data (no auth needed), returns 404 for unknown tax number, returns NO verdict/audit fields

- [ ] **Task 2: Backend — ArchUnit & Module Boundary compliance** (AC: #4, #7)
  - [ ] 2.1 Verify `PublicCompanyResponse` is a Java record in `api.dto` — ArchUnit `DtoConventionTest` will enforce this automatically
  - [ ] 2.2 Verify `findMostRecentPublicSnapshot()` is in `ScreeningRepository` — module boundary correct (screening owns `company_snapshots`)
  - [ ] 2.3 Ensure NO fields from `verdicts` or `search_audit_log` are included in the public response — add test assertion

- [ ] **Task 3: Nuxt — Public company page** (AC: #1, #2, #3, #5, #6)
  - [ ] 3.1 Create `frontend/app/pages/company/[taxNumber].vue` — the public SSR/ISR page (note: `routeRules` already configures `/company/**` as ISR in `nuxt.config.ts`)
  - [ ] 3.2 Use Nuxt `useAsyncData` (NOT `useFetch` with auth headers) to call `GET /api/v1/public/companies/{taxNumber}` — this is unauthenticated
  - [ ] 3.3 Render company name, tax number, and a "Data verified by RiskGuard" generic indicator (NOT a verdict status — AC #4)
  - [ ] 3.4 Add JSON-LD `<script type="application/ld+json">` block via `useHead()` with Organization schema: `@context`, `@type: Organization`, `name`, `taxID`, `address` (if available)
  - [ ] 3.5 Add CTA section: prominent "Check this company's live risk status" button that navigates to `/auth/login` (with `?redirect=/screening/{taxNumber}` query param for post-login redirect)
  - [ ] 3.6 Handle 404: when `useAsyncData` returns null/404, render "Company not found — search now" state with SearchBar link (AC #6)
  - [ ] 3.7 Use `public.vue` layout (NOT `default.vue`) — no authenticated sidebar, public marketing aesthetic
  - [ ] 3.8 Add `<Head>` meta tags via `useHead()`: `<title>`, `<meta name="description">`, `<link rel="canonical">`

- [ ] **Task 4: Nuxt — i18n for public company page** (AC: #1)
  - [ ] 4.1 Add `company` namespace to `hu/screening.json` (or new `hu/public.json` if preferred — but stay consistent with existing namespace structure): keys for page title, description, CTA text, not-found message
  - [ ] 4.2 Add matching English keys to `en/screening.json` (or `en/public.json`)
  - [ ] 4.3 Keys must be alphabetically sorted per project convention

- [ ] **Task 5: Nuxt — Server route for JSON-LD (optional Nuxt server route)** (AC: #2)
  - [ ] 5.1 Architecture specifies `frontend/server/routes/company/[taxNumber].get.ts` as a Nuxt server route for JSON-LD SEO data. However, since the public page already uses `useAsyncData` and `useHead()` for JSON-LD injection, this server route is OPTIONAL unless the team wants a dedicated `/company/{taxNumber}.json` endpoint for crawlers. Mark as optional unless AC #2 explicitly requires it.

- [ ] **Task 6: Tests** (AC: all)
  - [ ] 6.1 `ScreeningControllerPublicTest.java` — backend: 200 with public data (no auth), 404 for missing, no verdict fields in response
  - [ ] 6.2 `frontend/app/pages/company/[taxNumber].spec.ts` — Nuxt page: renders company name/taxNumber, renders JSON-LD script tag, renders CTA button, renders not-found state on 404
  - [ ] 6.3 Verify `nuxt.config.ts` already has `/company/**` ISR rule (it does — confirmed in codebase) — no changes needed there


## Dev Notes

### Critical Context — What This Story Builds

Story 2.6 implements **SEO Gateway Stubs** — public, unauthenticated, ISR-rendered Nuxt pages at `/company/{taxNumber}` that:

1. **Drive organic traffic** — Google indexes these pages for Hungarian company names/tax numbers
2. **Convert visitors** — Each page has a CTA to sign up and get the real risk verdict
3. **Stay safe** — NO tenant data, NO verdict statuses, NO audit hashes exposed

The page serves **cached, public-safe** company data (name, tax number, address) extracted from the most recent snapshot in the database — regardless of which tenant triggered the original search.

**Key constraint:** This is the FIRST unauthenticated backend endpoint in the project. It bypasses `TenantFilter` by design. The `ScreeningRepository.findMostRecentPublicSnapshot()` method MUST NOT use `TenantContext.getCurrentTenant()` — it queries across all tenants for the most recent snapshot matching the tax number.

**What is NOT in this story:**
- Authenticated verdict viewing (Story 2.1–2.5 already handle that)
- Guest rate limiting (Story 2.7 handles that separately)
- Watchlist or audit features (Epic 3, 5)

### Architecture Compliance — CRITICAL

**1. Public Endpoint Security (MUST DO)**

`SecurityConfig.java` currently has a catch-all that requires authentication. You MUST add the public exemption BEFORE the catch-all:

```java
// In SecurityConfig.java — add this BEFORE the authenticated catch-all matcher
.requestMatchers("/api/v1/public/**").permitAll()
```

**Verify:** The `TenantFilter` reads from `SecurityContextHolder`. For the public endpoint, the filter should gracefully handle null tenant (no tenant context = no data isolation needed for public data). Check `TenantFilter.java` — if it throws on null tenant, add a bypass for `/api/v1/public/**` paths.

**2. No Tenant Filter in Repository Method**

```java
// WRONG — do NOT do this in findMostRecentPublicSnapshot():
UUID tenantId = requireTenantId();  // throws if no tenant context

// CORRECT — cross-tenant public query:
dsl.select(COMPANY_SNAPSHOTS.TAX_NUMBER, COMPANY_SNAPSHOTS.SNAPSHOT_DATA, COMPANY_SNAPSHOTS.CHECKED_AT)
   .from(COMPANY_SNAPSHOTS)
   .where(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
   .orderBy(COMPANY_SNAPSHOTS.CHECKED_AT.desc())
   .limit(1)
   .fetchOptional(...)
```

**3. DTO must be a Java record with `from()` factory (ArchUnit enforced)**

```java
// In hu.riskguard.screening.api.dto:
public record PublicCompanyResponse(
    String taxNumber,
    String companyName,   // nullable — may not be in snapshot data
    String address        // nullable — may not be in snapshot data
) {
    public static PublicCompanyResponse from(String taxNumber, String companyName, String address) {
        return new PublicCompanyResponse(taxNumber, companyName, address);
    }
}
```

**4. Extract company name and address from SnapshotData**

The `snapshot_data` JSONB column stores the aggregated data from adapters. Use the existing `SnapshotDataParser` or `SnapshotData` domain class to extract `companyName` and `address`. Check `SnapshotData.java` for available fields before adding new parsing logic.

**5. Nuxt ISR is already configured**

`nuxt.config.ts` already has:
```typescript
routeRules: {
  '/company/**': { isr: true },  // Already present!
}
```
Do NOT add this again. The page just needs to exist at `pages/company/[taxNumber].vue`.

**6. Use `public.vue` layout**

Architecture specifies a `public.vue` layout for SEO pages. This layout does NOT exist yet — the authenticated `default.vue` layout exists. You will need to create `frontend/app/layouts/public.vue` — a minimal layout with no sidebar, no auth nav, just the public marketing header.

**7. JSON-LD Organization Schema**

The JSON-LD block must conform to schema.org Organization type:
```json
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "name": "Company Name Kft.",
  "taxID": "12345678-2-41",
  "url": "https://riskguard.hu/company/12345678241"
}
```
Inject via Nuxt `useHead()`:
```typescript
useHead({
  script: [{
    type: 'application/ld+json',
    innerHTML: JSON.stringify(jsonLd)
  }]
})
```

### Existing Code Reference

| File | Path | Relevance |
|------|------|-----------|
| `ScreeningController.java` | `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java` | **MODIFYING** — add `GET /api/v1/public/companies/{taxNumber}` method |
| `ScreeningService.java` | `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` | **MODIFYING** — add `getPublicCompanyData()` method |
| `ScreeningRepository.java` | `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java` | **MODIFYING** — add `findMostRecentPublicSnapshot()` WITHOUT tenant filter |
| `SecurityConfig.java` | `backend/src/main/java/hu/riskguard/core/config/SecurityConfig.java` | **MODIFYING** — add `/api/v1/public/**` permit-all |
| `TenantFilter.java` | `backend/src/main/java/hu/riskguard/core/security/TenantFilter.java` | **CHECK** — ensure null tenant doesn't crash for public endpoints |
| `SnapshotData.java` | `backend/src/main/java/hu/riskguard/screening/domain/SnapshotData.java` | **READ** — check available fields (companyName, address) |
| `nuxt.config.ts` | `frontend/nuxt.config.ts` | **READ-ONLY** — ISR rule already present |
| `pages/screening/[taxNumber].vue` | `frontend/app/pages/screening/[taxNumber].vue` | **REFERENCE** — pattern for authenticated page; public page is DIFFERENT (no store, no auth) |
| `api.d.ts` | `frontend/types/api.d.ts` | **MODIFYING** — add `PublicCompanyResponse` interface |

### DANGER ZONES — Common LLM Mistakes to Avoid

1. **DO NOT expose verdict status on the public page.** The public page shows ONLY: company name, tax number, address (if available), and a CTA. No shield icons, no "Reliable/At-Risk" labels. AC #4 is explicit.

2. **DO NOT use `requireTenantId()` in `findMostRecentPublicSnapshot()`.** This is intentionally a cross-tenant query. The data returned is public-safe (name, address only — no verdict, no hash).

3. **DO NOT use the `default.vue` layout** for the public page. The `public.vue` layout needs to be created — it's a minimal, marketing-style layout without the authenticated sidebar.

4. **DO NOT use `$fetch` with auth headers** in the public page. Use `useAsyncData` with a plain fetch. The endpoint is unauthenticated.

5. **DO NOT forget to add `PublicCompanyResponse` to `api.d.ts`.** The frontend relies on this for TypeScript type safety. Add it after `CompanySnapshotResponse`.

6. **DO NOT set a `<meta name="robots" content="noindex">` tag.** The entire point of this story is SEO indexability. All public company pages should be indexable.

7. **DO NOT use `pages/company/[taxNumber].vue` for authenticated verdict flows.** The `/screening/[taxNumber].vue` page already handles authenticated verdict viewing. The `/company/[taxNumber].vue` page is ONLY for public SEO stubs.

8. **DO NOT call `ScreeningService.search()` from the public endpoint.** The public endpoint is read-only — it queries existing snapshots, never triggers new data fetches. No auth, no side effects.

9. **DO NOT use the `SnapshotDataParser` if it uses internal types.** If `SnapshotDataParser` is `internal/` scoped, extract only the fields needed (companyName, address) directly from the `snapshot_data` JSONB using jOOQ's `get()` accessor or a simple `JsonNode` parse. Prefer the simplest approach that doesn't violate module boundaries.

10. **DO NOT add i18n keys to `hu/identity.json` or `hu/auth.json`.** Public company page keys belong in `hu/screening.json` (under a `company` sub-key) to stay consistent with the screening module that owns this data.

### Nuxt Public Page Implementation Checklist

Per Architecture Frontend Implementation Checklist (8 steps):
1. ✅ `api.d.ts` — add `PublicCompanyResponse` interface (no verdict/audit fields)
2. ✅ `risk-guard-tokens.json` — no new business constants needed for this story
3. ✅ `hu/screening.json` / `en/screening.json` — add `screening.company.*` keys
4. ✅ No Pinia store needed — public page uses `useAsyncData` directly (stateless SSR)
5. ✅ PrimeVue components — use `Button` for CTA, `Skeleton` for loading state
6. 🔨 Create `frontend/app/pages/company/[taxNumber].vue`
7. 🔨 Create `frontend/app/layouts/public.vue`
8. ✅ Write `frontend/app/pages/company/[taxNumber].spec.ts` co-located

### Database Query Design

**`findMostRecentPublicSnapshot()` — cross-tenant, public-safe:**
```java
dsl.select(
    COMPANY_SNAPSHOTS.TAX_NUMBER,
    COMPANY_SNAPSHOTS.SNAPSHOT_DATA,
    COMPANY_SNAPSHOTS.CHECKED_AT
)
.from(COMPANY_SNAPSHOTS)
.where(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
.orderBy(COMPANY_SNAPSHOTS.CHECKED_AT.desc().nullsLast())
.limit(1)
.fetchOptional(r -> new PublicSnapshotRecord(
    r.get(COMPANY_SNAPSHOTS.TAX_NUMBER),
    r.get(COMPANY_SNAPSHOTS.SNAPSHOT_DATA),
    r.get(COMPANY_SNAPSHOTS.CHECKED_AT)
))
```

Note: No `tenant_id` filter — this is intentional for public data. The JSONB `snapshot_data` contains demo/real company info (name, address) which is public-safe. Only exclude: verdict status, audit hash, tenant ID.

### New Files to Create

```
frontend/app/layouts/
  public.vue                                          # NEW — minimal public layout (no auth sidebar)

frontend/app/pages/company/
  [taxNumber].vue                                     # NEW — public SEO gateway stub page
  [taxNumber].spec.ts                                 # NEW — co-located test spec

backend/src/main/java/hu/riskguard/screening/api/dto/
  PublicCompanyResponse.java                          # NEW — public-safe DTO (no verdict/audit)

backend/src/test/java/hu/riskguard/screening/api/
  ScreeningControllerPublicTest.java                  # NEW — public endpoint tests
```

### Modified Files

```
backend/src/main/java/hu/riskguard/core/config/
  SecurityConfig.java                    # Add /api/v1/public/** permit-all

backend/src/main/java/hu/riskguard/screening/api/
  ScreeningController.java               # Add GET /api/v1/public/companies/{taxNumber}

backend/src/main/java/hu/riskguard/screening/domain/
  ScreeningService.java                  # Add getPublicCompanyData() method

backend/src/main/java/hu/riskguard/screening/internal/
  ScreeningRepository.java               # Add findMostRecentPublicSnapshot() (no tenant filter)

frontend/types/
  api.d.ts                               # Add PublicCompanyResponse interface

frontend/app/i18n/hu/
  screening.json                         # Add screening.company.* keys (alphabetical)

frontend/app/i18n/en/
  screening.json                         # Add screening.company.* keys (alphabetical)
```

### Project Structure Notes

- The `/company/**` ISR rule is already in `nuxt.config.ts` — confirmed in codebase
- The `public.vue` layout is in the architecture spec but not yet created — needs to be created as a new file
- The `frontend/server/routes/company/[taxNumber].get.ts` Nuxt server route (mentioned in architecture) is OPTIONAL for this story — the JSON-LD can be injected via `useHead()` directly in the page component
- `ScreeningRepository` currently only has `requireTenantId()` as its tenant helper — `findMostRecentPublicSnapshot()` should bypass this entirely, not call it

### Previous Story Intelligence (Story 2.5 — most recent)

1. **`ScreeningRepository` pattern** — All existing methods call `requireTenantId()`. The new `findMostRecentPublicSnapshot()` is the FIRST method that intentionally skips this. Be explicit with a comment: `// Intentionally cross-tenant — public data only`.
2. **`ScreeningController` pattern** — All existing endpoints use `@AuthenticationPrincipal Jwt jwt` and call `requireUuidClaim()`. The new public endpoint MUST NOT have these parameters — it's unauthenticated.
3. **`api.d.ts` is manually maintained** (not yet auto-generated) — add the new interface following the existing record style.
4. **107 frontend tests pass** — do not regress. The new `[taxNumber].spec.ts` spec is additive.
5. **`HASH_UNAVAILABLE_SENTINEL`** — Story 2.5 added this constant. The public endpoint must never return audit hash data (AC #4), so this sentinel is irrelevant to this story.

### Git Intelligence

Recent commits:
- `docs: implementation readiness review, epic fixes, and comprehensive UX design enhancements` — suggests comprehensive design work has happened
- `fix: resolve 10 cross-boundary bugs blocking local E2E flow` — indicates E2E integration has been tested
- `feat(screening): implement Story 2.1` — screening module is mature reference implementation

The codebase is production-ready for Epics 1 and 2. This story adds a new public surface — be conservative and surgical.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.6] Story AC: public pages SSR/ISR, JSON-LD Organization schema, CTA requiring login, no tenant data, routeRules, 404 for never-searched companies
- [Source: _bmad-output/planning-artifacts/epics.md#NFR-Coverage-Map] NFR6 → SEO Gateway Stubs (Story 2.6)
- [Source: _bmad-output/planning-artifacts/architecture.md#Core-Architectural-Decisions] SEO Gateway Stubs: Nuxt hybrid rendering, routeRules, public company pages SSR/ISR, authenticated routes SPA
- [Source: _bmad-output/planning-artifacts/architecture.md#Project-Structure] `pages/company/[taxNumber].vue` — PUBLIC: SEO gateway stub (SSR/ISR); `server/routes/company/[taxNumber].get.ts` — Nuxt server route for JSON-LD SEO data
- [Source: _bmad-output/planning-artifacts/architecture.md#Architectural-Boundaries] All REST endpoints behind `/api/v1/`. Public SEO pages served by Nuxt SSR at `/company/{taxNumber}`.
- [Source: _bmad-output/planning-artifacts/architecture.md#Requirements-Coverage] NFR6: SEO gateway stubs with JSON-LD ✅ — Nuxt hybrid rendering, `pages/company/[taxNumber].vue` (SSR/ISR)
- [Source: _bmad-output/project-context.md] SEO Stubs: `/company/[taxNumber]` routes must be configured as `static` or `swr` in `routeRules`; use `useApiError()` for error handling; frontend spec co-location mandatory
- [Source: frontend/nuxt.config.ts] routeRules already has `/company/**` ISR — confirmed in codebase
- [Source: _bmad-output/implementation-artifacts/2-5-sha-256-audit-logging-legal-proof.md#Dev-Notes] Pattern reference: ScreeningController uses `@AuthenticationPrincipal Jwt jwt` — public endpoint must NOT use this
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend-Structure] `layouts/public.vue` — SEO public pages layout (not yet created)

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

