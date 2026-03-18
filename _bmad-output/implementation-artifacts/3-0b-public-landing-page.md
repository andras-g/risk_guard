# Story 3.0b: Public Landing Page

Status: done

Story ID: 3.0b
Story Key: 3-0b-public-landing-page
Epic: 3 — Automated Monitoring & Alerts (Watchlist)
Created: 2026-03-16

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a Visitor (unauthenticated),
I want a public landing page that lets me instantly search a tax number without signing up,
so that I can experience the product's value before committing to registration.

## Acceptance Criteria

1. **Given** a public (unauthenticated) visitor, **When** I navigate to the root URL (`/`), **Then** the Landing Page renders using the `public.vue` layout with the "airy, horizontal, marketing-focused" Gateway aesthetic (large spacing, relaxed line-height, breathable feel).
2. **Given** the landing page, **When** I view the hero section above the fold, **Then** a prominent Tax ID search input is the singular focal point, using intelligent masking for 8-digit (`1234-5678`) and 11-digit (`1234-5678-901`) Hungarian Tax Numbers (UX Spec §7.3), with a clear CTA button.
3. **Given** the landing page, **When** I view below the hero, **Then** the page clearly communicates the product value proposition with 3 feature cards: Deterministic Verdicts (shield icon), Less than 30s Results (clock icon), and Court-Ready Evidence (lock icon), plus a social proof / trust signals section.
4. **Given** the landing page, **When** it is rendered by the server, **Then** the page is server-side rendered (SSR) by Nuxt for SEO performance, with appropriate `<head>` meta tags (title, description, Open Graph, JSON-LD Organization schema).
5. **Given** an authenticated user, **When** they navigate to the root URL (`/`), **Then** they are redirected to `/dashboard` (preserving current behavior for logged-in users).
6. **Given** a mobile viewport (<768px), **When** I view the landing page, **Then** the layout is single-column, the search input fills full width, the CTA button is full-width below the input (not inline), and the value proposition cards stack vertically.
7. **Given** the search service is unavailable, **When** I attempt a search from the landing page, **Then** the page displays a clear "Service Temporarily Unavailable" message instead of a broken UI.
8. **Given** the landing page, **When** I view it, **Then** all user-facing text uses i18n keys from `hu/` and `en/` translation files — no hardcoded strings. Hungarian is the default locale.

## Tasks / Subtasks

- [x] Task 1: Create i18n translation files for landing page (AC: #8)
  - [x] 1.1 Create `frontend/app/i18n/hu/landing.json` with all Hungarian landing page strings (hero headline, tagline, search placeholder, CTA label, 3 feature card titles + descriptions, social proof text, disclaimer)
  - [x] 1.2 Create `frontend/app/i18n/en/landing.json` with matching English translations (key parity required)
  - [x] 1.3 Register the new `landing` namespace in the Nuxt i18n module configuration (`nuxt.config.ts`)
  - [x] 1.4 Run `npm run check-i18n` to verify parity passes

- [x] Task 2: Extract shared tax number formatting utility (AC: #2)
  - [x] 2.1 Create `frontend/app/utils/taxNumber.ts` exporting `formatTaxNumber(raw: string): string` and the Zod validation schema `taxNumberSchema`
  - [x] 2.2 Refactor existing `SearchBar.vue` to import from `utils/taxNumber.ts` instead of inline logic (preserve all existing behavior, no visual changes)
  - [x] 2.3 Verify existing `SearchBar.spec.ts` tests still pass after refactor

- [x] Task 3: Create `LandingSearchBar.vue` component (AC: #2, #6, #7)
  - [x] 3.1 Create `frontend/app/components/Landing/LandingSearchBar.vue` using PrimeVue `InputText` + `Button`, importing shared `formatTaxNumber` and `taxNumberSchema` from `utils/taxNumber.ts`
  - [x] 3.2 Implement intelligent masking: auto-format as user types (8-digit: `1234-5678`, 11-digit: `1234-5678-901`)
  - [x] 3.3 On valid submit, navigate to `/auth/login?redirect=/screening/{taxNumber}` to preserve search intent through auth flow
  - [x] 3.4 On invalid input, show inline validation error below input using i18n key
  - [x] 3.5 When health store indicates service unavailable, disable the input and show `$t('landing.search.serviceUnavailable')` message
  - [x] 3.6 Mobile responsive: input full-width, CTA button full-width below input (not inline) on viewports < 768px; inline layout on desktop
  - [x] 3.7 Create `frontend/app/components/Landing/LandingSearchBar.spec.ts` with tests for: rendering, formatting, validation, submit navigation, disabled state, mobile layout

- [x] Task 4: Create `LandingFeatureCards.vue` component (AC: #3, #6)
  - [x] 4.1 Create `frontend/app/components/Landing/LandingFeatureCards.vue` displaying 3 value proposition cards in a horizontal row (desktop) / vertical stack (mobile)
  - [x] 4.2 Card 1: Shield icon + "Deterministic Verdicts" (i18n keys: `landing.features.verdicts.title`, `landing.features.verdicts.description`)
  - [x] 4.3 Card 2: Clock icon + "Less than 30s Results" (i18n keys: `landing.features.speed.title`, `landing.features.speed.description`)
  - [x] 4.4 Card 3: Lock icon + "Court-Ready Evidence" (i18n keys: `landing.features.evidence.title`, `landing.features.evidence.description`)
  - [x] 4.5 Use Landing Hero Card variant from UX Spec: 32px padding, `rounded-2xl`, `shadow-lg`, no border
  - [x] 4.6 Grid: CSS Grid `auto-fill, minmax(280px, 1fr)` for responsive card layout
  - [x] 4.7 Create `frontend/app/components/Landing/LandingFeatureCards.spec.ts` with tests for: rendering all 3 cards, i18n key usage, responsive class assertions

- [x] Task 5: Create `LandingSocialProof.vue` component (AC: #3)
  - [x] 5.1 Create `frontend/app/components/Landing/LandingSocialProof.vue` with trust signals section (partner count badge, trust badges placeholder area)
  - [x] 5.2 Use i18n keys for all text content
  - [x] 5.3 Create `frontend/app/components/Landing/LandingSocialProof.spec.ts`

- [x] Task 6: Adjust `public.vue` layout for full-width slot (AC: #1)
  - [x] 6.1 Remove `max-w-prose` constraint from the `<slot />` wrapper div in `frontend/app/layouts/public.vue` — change to bare `<slot />` or a `flex-1` wrapper. The landing page will manage its own section widths.
  - [x] 6.2 Keep `leading-relaxed` at page level (Gateway density profile)
  - [x] 6.3 Verify no other pages use the `public` layout that would break (currently only `index.vue` planned)

- [x] Task 7: Rewrite `pages/index.vue` as the landing page (AC: #1, #2, #3, #4, #5, #6)
  - [x] 7.1 Replace the redirect-only `pages/index.vue` with full landing page content
  - [x] 7.2 Add `definePageMeta({ layout: 'public' })` to use the public layout
  - [x] 7.3 Add auth-aware redirect: if user is authenticated, `navigateTo('/dashboard')` in setup (AC5)
  - [x] 7.4 Compose the page from: `LandingSearchBar`, `LandingFeatureCards`, `LandingSocialProof` components
  - [x] 7.5 Hero section: centered content with `space-16` (64px) vertical padding, headline in 36px (`text-4xl`), tagline in 18px (`text-lg`), search bar below
  - [x] 7.6 Feature section: `max-w-6xl mx-auto` container with `space-12` (48px) top/bottom padding
  - [x] 7.7 Social proof section: `max-w-4xl mx-auto` container
  - [x] 7.8 Add SEO meta via `useSeoMeta()`: title, description, og:title, og:description, og:type, og:url
  - [x] 7.9 Add JSON-LD Organization schema via `useHead()` with `script` tag of type `application/ld+json`
  - [x] 7.10 Footer disclaimer text using i18n key

- [x] Task 8: Configure SSR route rule (AC: #4)
  - [x] 8.1 Add `routeRules: { '/': { ssr: true } }` to `nuxt.config.ts` (if not already the default)
  - [x] 8.2 Verify the landing page renders server-side by checking the HTML response contains the hero content (not just a shell)

- [x] Task 9: Write integration-level page test (AC: #1-#8)
  - [x] 9.1 Create `frontend/app/pages/index.spec.ts` with tests for: page renders with public layout, search bar present, feature cards present, social proof present, i18n keys resolve, auth redirect for logged-in user
  - [x] 9.2 Verify all existing tests still pass: `npm run test`
  - [x] 9.3 Run ESLint and verify 0 errors: `npm run lint`
  - [x] 9.4 Run i18n parity check: `npm run check-i18n`

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Fixed wrong CSS selector in E2E test — changed `.text-red-500` to `[data-testid="validation-error"]` and added `data-testid="validation-error"` attribute to `<small>` elements in both `SearchBar.vue` and `LandingSearchBar.vue`. [frontend/e2e/search-flow.e2e.ts:83, frontend/app/components/Screening/SearchBar.vue:57, frontend/app/components/Landing/LandingSearchBar.vue:60]
- [x] [AI-Review][HIGH] Fixed partial adapter data discarded for unavailable adapters — when `available=false`, now merges status/reason metadata INTO the adapter's actual `result.data()` instead of replacing it, preserving risk-relevant fields like `hasPublicDebt`. [backend/src/main/java/hu/riskguard/datasource/internal/CompanyDataAggregator.java:112-118]
- [x] [AI-Review][MEDIUM] Fixed inconsistent timestamps across audit trail — removed third `OffsetDateTime.now()` call and reused `checkedAt` as `evaluationTime` in `VerdictEngine.evaluate()`, ensuring consistent timestamps across snapshot, verdict, and audit log. [backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java:189]
- [x] [AI-Review][MEDIUM] Fixed duplicate `onMounted` hooks with navigation race — merged two concurrent `onMounted` async hooks into a single hook with early return after `navigateTo('/dashboard')`, preventing the health check from running on an unmounting component. [frontend/app/pages/index.vue:8-29]
- [x] [AI-Review][MEDIUM] Fixed stale `package-info.java` documentation — updated from `@Profile("test")` to `@Profile({"test", "e2e"})` to match actual annotations on `TestAuthController` and `TestSecurityConfig`. [backend/src/main/java/hu/riskguard/testing/package-info.java:3,10]
- [x] [AI-Review][LOW] Removed unused `import java.util.HashMap` from `ScreeningService.java`. [backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java:25]
- [x] [AI-Review][LOW] Removed redundant `-Dspring.profiles.active=test` JVM arg from CI E2E job — the `SPRING_PROFILES_ACTIVE=test` env var (line 201) is the reliable mechanism and was already present. [.github/workflows/ci.yml:179]

### Review Follow-ups Round 2 (AI) — 2026-03-16

- [x] [AI-Review-R2][HIGH] Fixed weak test assertions for tax number formatting — `LandingSearchBar.spec.ts` format tests used `toContain('1234')` which passes even with broken formatting. Changed to exact `toBe('1234-5678')` and `toBe('1234-5678-901')` assertions with proper `nextTick()`. [frontend/app/components/Landing/LandingSearchBar.spec.ts:52-73]
- [x] [AI-Review-R2][HIGH] Fixed missing WCAG 2.1 AA accessibility on search form — added `role="search"`, `aria-label` (i18n key), visually-hidden `<label>` linked via `for`/`id`, `aria-describedby` linking input to validation error, `aria-invalid` state, and `role="alert"` on error. Added 3 new accessibility tests. [frontend/app/components/Landing/LandingSearchBar.vue:43-81, frontend/app/components/Landing/LandingSearchBar.spec.ts:143-174]
- [x] [AI-Review-R2][MEDIUM] Fixed non-reactive SEO meta tags — `useSeoMeta()` called with static `t()` values that wouldn't update on locale switch. Changed `title`, `description`, `ogTitle`, `ogDescription` to getter functions `() => t(...)` for reactive locale-aware SSR. Updated test to verify getter function pattern. [frontend/app/pages/index.vue:35-42, frontend/app/pages/index.spec.ts:104-115]
- [x] [AI-Review-R2][MEDIUM] Fixed static JSON-LD schema — `JSON.stringify` with `t()` baked a static string at setup time. Wrapped in `computed()` so JSON-LD `description` re-serializes on locale change. Updated test to handle computed ref `innerHTML`. [frontend/app/pages/index.vue:44-58, frontend/app/pages/index.spec.ts:129-135]
- [x] [AI-Review-R2][MEDIUM] Replaced inline CSS `style` for grid layout with Tailwind classes — `LandingFeatureCards.vue` used raw `style="display: grid; gap: 2rem;"` bypassing the design token system. Changed to Tailwind `grid gap-8` classes; only `grid-template-columns` kept as inline style (no Tailwind utility for `auto-fill, minmax(280px, 1fr)`). Updated test assertions. [frontend/app/components/Landing/LandingFeatureCards.vue:29, frontend/app/components/Landing/LandingFeatureCards.spec.ts:54-61]
- [x] [AI-Review-R2][i18n] Added `landing.hero.searchAriaLabel` key to both `hu/landing.json` and `en/landing.json` for the new `aria-label` on the search form. i18n parity check passed.

## Dev Notes

### Why This Story Exists
The `public.vue` layout was scaffolded in Story 3.0a but contains only a header/footer shell with an empty `<slot />`. The current `pages/index.vue` is a hard redirect to `/dashboard`. There is no public-facing page for unauthenticated visitors — the product is invisible to anyone who hasn't logged in. This story creates the first impression: a zero-friction landing page where visitors can experience the core value (tax number search) before registering. It is the conversion funnel entry point for both organic search and direct traffic.

Story 3.11 (SEO Gateway Stubs) and Story 3.12 (Demo Mode & Guest Rate Limiting) depend on this landing page existing. This story does NOT implement the full guest search flow (that's 3.12) — it implements the landing page UI that funnels visitors toward registration or guest demo.

### Current State (Gap Analysis)

| Specification | Current Code | Gap |
|---|---|---|
| Public landing page at `/` | `pages/index.vue` redirects to `/dashboard` via `navigateTo('/dashboard', { redirectCode: 301 })` | Full page rewrite needed |
| `public.vue` layout for landing | Scaffolded: header (logo + login/register), `<slot />` wrapped in `max-w-prose leading-relaxed`, footer | Slot content constraint too narrow for hero — needs widening for landing page |
| Hero search input with masking | `SearchBar.vue` exists but is tightly coupled to `useScreeningStore` (authenticated API) | Need a new `LandingSearchBar.vue` that reuses formatting logic but funnels to registration instead of calling the API |
| Value proposition cards | Not implemented | New component needed |
| SEO meta tags / JSON-LD | Not implemented on any page | Need `useHead()` / `useSeoMeta()` in `index.vue` |
| Mobile responsive landing | `public.vue` has no responsive adjustments beyond `max-w-prose` | Need responsive hero and card grid |
| i18n for landing content | No landing page keys exist | Need new keys in `hu/common.json` and `en/common.json` (or new `landing.json` namespace) |

### Key Decisions

1. **New `LandingSearchBar.vue` component, not reusing `SearchBar.vue` directly.** The existing `SearchBar.vue` imports `useScreeningStore` which calls an authenticated API endpoint with `credentials: 'include'`. The landing page search must NOT call the authenticated API. Instead, `LandingSearchBar.vue` will:
   - Reuse the same Zod validation regex (`/^\d{8}(\d{3})?$/`) and `formatTaxNumber()` masking logic (extract to a shared utility or duplicate the ~10 lines).
   - On submit, navigate to `/auth/login?redirect=/screening/{taxNumber}` (funneling to registration/login with intent preserved). When Story 3.12 (guest rate limiting) is implemented, this will change to trigger a guest search directly.
   - Use the same PrimeVue `InputText` + `Button` component pattern.

2. **Landing page i18n keys go in a new `landing.json` namespace** (not `common.json`) to keep the common namespace lean. New files: `i18n/hu/landing.json` and `i18n/en/landing.json`. This follows the architecture's namespace-per-file pattern. [Source: architecture.md §i18n File Organization]

3. **The `public.vue` layout needs a minor adjustment.** The current `max-w-prose leading-relaxed` wrapper on the `<slot />` is correct for the Gateway density profile (UX Spec §10.4) for text content, but the landing page hero needs full-width capability. The layout should be adjusted to remove the `max-w-prose` constraint from the slot wrapper — the landing page itself will manage its own content widths per section (hero: full-width, text: `max-w-prose`, card grid: `max-w-6xl`).

4. **SSR routing configuration.** The landing page (`/`) must be SSR-rendered for SEO. Add `routeRules` in `nuxt.config.ts`: `'/': { ssr: true }`. Authenticated routes remain SPA. [Source: architecture.md §SEO Gateway Stubs — Nuxt hybrid rendering]

5. **Auth-aware conditional rendering.** `pages/index.vue` must check auth state: if authenticated → `navigateTo('/dashboard')`; if not → render landing page. Use `useAuth` composable or the auth store to check login state. This preserves the current redirect behavior for logged-in users (AC5).

6. **No backend changes required.** This is a frontend-only story. The landing page is static marketing content with a search CTA that funnels to auth. No new API endpoints.

7. **Search CTA behavior when service is unavailable (AC7).** Since the landing page search doesn't actually call the API (it redirects to login), the "service unavailable" state applies to the visual feedback. If the backend health endpoint indicates the search service is down, the search input should show a disabled state with a "Service Temporarily Unavailable" message. Use the existing `stores/health.ts` Pinia store if it exposes a health check, or implement a lightweight `/actuator/health` ping on page load.

### Predecessor Context (Story 3.0a Learnings)

From the completed Story 3.0a code review and dev notes:
- **198 unit tests passing** (107 existing + 91 new). All must continue to pass.
- **ESLint 0 errors** (9 pre-existing warnings in untouched files). Must maintain.
- **i18n parity enforced**: every key in `hu/*.json` must exist in `en/*.json` and vice versa. Run `npm run check-i18n` after adding keys.
- **`@intlify/vue-i18n/no-raw-text`** ESLint rule is active — zero tolerance for hardcoded strings in `<template>` blocks.
- **PrimeVue components are stubbed** in unit tests via the existing `vitest.setup.ts` configuration. New PrimeVue components used (e.g., `InputText`, `Button`) will auto-resolve.
- **`vitest.setup.ts`** already has `useRoute`, `useRouter`, `useNuxtApp` stubs from Story 3.0a.
- **Isactive path matching bug was fixed** in 3.0a — `route.path === path || route.path.startsWith(path + '/')` — not relevant to this story but shows the level of rigor expected.
- **Font loading is already configured**: Inter + JetBrains Mono via `@fontsource` packages, imported in `main.css`.
- **`public.vue` has i18n keys ready**: `common.app.name`, `common.actions.login`, `common.actions.register`, `common.app.copyright` — all used with `$t()` calls.

### Migration Strategy

The only existing file that gets a significant rewrite is `pages/index.vue` (currently 3 lines — a redirect). The `public.vue` layout gets a minor slot wrapper adjustment. Everything else is new files.

**Risk assessment:** LOW. No existing components are modified. The redirect behavior for authenticated users is preserved. The landing page is additive content.

### UX Specification References

The landing page design is specified in detail across multiple UX spec sections:

- **§4 (Vault Pivot):** Sharp visual transition between public "Gateway" (airy, horizontal, marketing) and private workspace. The landing page IS the Gateway.
- **§6.2 (Page Map):** Landing Page = "Zero-friction Tax ID search."
- **§10.4 (Gateway Profile):** `leading-relaxed` (1.75), `max-w-prose` for text, 48-64px section spacing, headings 36px, body 18px.
- **§11.1 (Landing Page Layout):** Complete wireframe with desktop and mobile layouts, search input behavior, value proposition cards, social proof section.
- **§12.1 (Gabor's Midnight Risk Check):** Step-by-step interaction flow starting from the landing page.
- **§12.5 (Guest Demo-to-Signup):** Conversion flow from landing page guest search.
- **§13.2 (Guest Rate Limit Reached):** Empty state for when guest limits are hit (implemented in Story 3.12, but the landing page should have the placeholder structure).
- **§14.2 (Shield Verdict Reveal):** The landing page search triggers this sequence (when guest search is implemented in 3.12).

### Project Structure Notes

**New files to create (8 files):**

| File | Purpose |
|---|---|
| `frontend/app/utils/taxNumber.ts` | Shared tax number formatting + Zod validation (extracted from SearchBar) |
| `frontend/app/components/Landing/LandingSearchBar.vue` | Public search CTA with masking, funnels to auth |
| `frontend/app/components/Landing/LandingSearchBar.spec.ts` | Unit tests for LandingSearchBar |
| `frontend/app/components/Landing/LandingFeatureCards.vue` | 3 value proposition cards |
| `frontend/app/components/Landing/LandingFeatureCards.spec.ts` | Unit tests for LandingFeatureCards |
| `frontend/app/components/Landing/LandingSocialProof.vue` | Trust signals section |
| `frontend/app/components/Landing/LandingSocialProof.spec.ts` | Unit tests for LandingSocialProof |
| `frontend/app/i18n/hu/landing.json` | Hungarian translations for landing page |
| `frontend/app/i18n/en/landing.json` | English translations for landing page |
| `frontend/app/pages/index.spec.ts` | Integration test for the landing page |

**Existing files to modify (3 files):**

| File | Change |
|---|---|
| `frontend/app/pages/index.vue` | Rewrite from redirect-only to full landing page |
| `frontend/app/layouts/public.vue` | Remove `max-w-prose` constraint from slot wrapper |
| `frontend/app/components/Screening/SearchBar.vue` | Refactor to import shared `formatTaxNumber` from `utils/taxNumber.ts` |
| `frontend/nuxt.config.ts` | Add `landing` i18n namespace registration; optionally add SSR route rule |

**Naming convention compliance:**
- Component directory `Landing/` follows PascalCase convention for component directories (matches existing `Screening/`, `Common/`, `Identity/`, etc.)
- Component files `LandingSearchBar.vue` follow PascalCase convention
- Utils file `taxNumber.ts` follows camelCase convention for utility files
- i18n files `landing.json` follow lowercase module namespace convention
- All consistent with architecture.md naming patterns

**No structural conflicts detected.** The new `Landing/` component directory is a natural peer to the existing `Screening/`, `Common/`, etc. directories. The `utils/` directory may need to be created if it doesn't already exist (verify at implementation time).

### Architecture Compliance Checklist

This story is frontend-only. The following architecture constraints apply:

- [ ] **i18n: No hardcoded strings.** ESLint rule `@intlify/vue-i18n/no-raw-text` is enforced. Every visible string in `<template>` must use `$t()` or `t()`. [Source: architecture.md, Frontend Implementation Checklist step 7]
- [ ] **i18n: Key parity.** Every key in `hu/landing.json` must exist in `en/landing.json` and vice versa. CI script `check-i18n-parity.sh` enforces this. [Source: architecture.md, i18n Enforcement]
- [ ] **Component naming: PascalCase.** All `.vue` files use PascalCase (`LandingSearchBar.vue`). ESLint rule `vue/component-name-in-template-casing` enforces PascalCase in templates. [Source: architecture.md, Code Naming Conventions]
- [ ] **No magic numbers.** Business constants (e.g., guest rate limits) come from `risk-guard-tokens.json`. This story has no business constants but if any numeric thresholds are introduced, they must be sourced from the tokens file. [Source: architecture.md, Business Constants Token Pattern]
- [ ] **SEO: Nuxt hybrid rendering.** The landing page at `/` must be SSR-rendered. Public pages use SSR/ISR; authenticated routes use SPA. [Source: architecture.md, SEO Gateway Stubs]
- [ ] **Button hierarchy.** Primary CTA uses Deep Navy (`bg-authority`) with white text. Secondary uses Slate Grey border. Tertiary is borderless. [Source: ux-design-specification.md, Section 7.1]
- [ ] **Spacing: Gateway density profile.** Landing page uses `space-8` to `space-16` (32-64px) for section spacing, `leading-relaxed` (1.75) line-height, headings 36px, body 18px. [Source: ux-design-specification.md, Section 10.4]
- [ ] **Card system: Landing Hero Card variant.** 32px padding, `rounded-2xl`, `shadow-lg`, no border. [Source: ux-design-specification.md, Section 10.3]
- [ ] **Responsive: Dual-Context strategy.** Mobile (<768px) single-column, card-based. Desktop (>1024px) full-width sections. [Source: ux-design-specification.md, Section 8.2]
- [ ] **Accessibility: WCAG 2.1 AA.** Strict 4.5:1 contrast for metadata, 7:1 for primary verdicts. Status colors paired with icons. [Source: ux-design-specification.md, Section 8.3]
- [ ] **Co-located specs.** Every new `.vue` file gets a co-located `.spec.ts` file. [Source: architecture.md, Frontend Implementation Checklist step 8]

### Library and Framework Requirements

**No new dependencies required.** All libraries needed are already installed from Story 3.0a:

| Library | Version | Usage in This Story | Already Installed |
|---|---|---|---|
| Nuxt 3 | (current) | Page routing, SSR, `useSeoMeta()`, `useHead()`, `definePageMeta()` | Yes |
| PrimeVue 4 | (current) | `InputText`, `Button` components in LandingSearchBar | Yes |
| Tailwind CSS 4 | (current) | All styling, responsive breakpoints, spacing tokens | Yes |
| Zod | (current) | Tax number validation schema in `utils/taxNumber.ts` | Yes |
| @nuxtjs/i18n | (current) | `useI18n()`, `$t()` for all user-facing strings | Yes |
| @fontsource/inter | (current) | Primary typography (already loaded in `main.css`) | Yes |
| @fontsource/jetbrains-mono | (current) | Monospace for tax numbers (already loaded) | Yes |
| Pinia | (current) | `stores/health.ts` for service availability check | Yes |

**Do NOT add:**
- No new npm packages
- No icon libraries (use inline SVG or PrimeVue built-in icons for the 3 feature cards: shield, clock, lock)
- No animation libraries (use CSS transitions with the existing motion tokens from `main.css`)

### File Structure Requirements

```
frontend/
  app/
    utils/
      taxNumber.ts                              # NEW: Shared formatting + Zod validation
    components/
      Landing/                                  # NEW DIRECTORY
        LandingSearchBar.vue                    # NEW: Public search CTA
        LandingSearchBar.spec.ts                # NEW: Unit tests
        LandingFeatureCards.vue                 # NEW: 3 value prop cards
        LandingFeatureCards.spec.ts             # NEW: Unit tests
        LandingSocialProof.vue                  # NEW: Trust signals
        LandingSocialProof.spec.ts              # NEW: Unit tests
      Screening/
        SearchBar.vue                           # MODIFIED: Import from utils/taxNumber.ts
    layouts/
      public.vue                                # MODIFIED: Remove max-w-prose from slot
    pages/
      index.vue                                 # REWRITTEN: Full landing page
      index.spec.ts                             # NEW: Page-level integration test
    i18n/
      hu/
        landing.json                            # NEW: Hungarian landing page strings
      en/
        landing.json                            # NEW: English landing page strings
  nuxt.config.ts                                # MODIFIED: Register landing i18n namespace
```

**Critical path dependency order:**
1. `utils/taxNumber.ts` first (shared dependency)
2. `i18n/*/landing.json` files (needed by components)
3. `nuxt.config.ts` i18n registration (needed for namespace to resolve)
4. `SearchBar.vue` refactor (import from new util, verify tests)
5. `LandingSearchBar.vue` + spec (depends on util + i18n)
6. `LandingFeatureCards.vue` + spec (depends on i18n)
7. `LandingSocialProof.vue` + spec (depends on i18n)
8. `public.vue` layout adjustment
9. `pages/index.vue` rewrite (depends on all components + layout)
10. `pages/index.spec.ts` integration test (depends on everything)

### Testing Requirements

**Unit Tests (co-located `.spec.ts` files):**

| Test File | Key Test Cases |
|---|---|
| `LandingSearchBar.spec.ts` | Renders input + button; formats 8-digit tax number with dashes; formats 11-digit tax number with dashes; shows validation error on invalid input; navigates to `/auth/login?redirect=/screening/{taxNumber}` on valid submit; disables input when health store indicates unavailable; shows service unavailable message; mobile: button renders full-width below input |
| `LandingFeatureCards.spec.ts` | Renders 3 cards; each card has icon + title + description from i18n; cards use Landing Hero Card styling (padding, border-radius, shadow); responsive grid classes applied |
| `LandingSocialProof.spec.ts` | Renders trust signals section; all text from i18n keys |
| `index.spec.ts` | Page renders with `public` layout meta; LandingSearchBar component present; LandingFeatureCards component present; LandingSocialProof component present; authenticated user gets redirected to `/dashboard`; SEO meta tags present (title, description); JSON-LD script tag present |

**Existing test preservation:**
- All 198 existing tests must continue to pass
- `SearchBar.spec.ts` must pass after refactor to use `utils/taxNumber.ts`

**Test infrastructure available (from Story 3.0a):**
- `renderWithProviders.ts` helper wraps components with Pinia + i18n + PrimeVue
- `mockFetch.ts` for standardized `$fetch` mocking
- `vitest.setup.ts` has `useRoute`, `useRouter`, `useNuxtApp` stubs
- PrimeVue components auto-stubbed

**Verification commands:**
```bash
cd frontend && npm run test          # All unit tests
cd frontend && npm run lint          # ESLint (0 errors expected)
cd frontend && npm run check-i18n   # i18n key parity
```

### Previous Story Intelligence (3.0a)

**Key learnings that directly apply to 3.0b:**

1. **PrimeVue component registration:** Components are globally registered via `plugins/primevue.ts`. No per-file imports needed for PrimeVue components like `InputText` and `Button` — they auto-resolve.

2. **i18n namespace loading:** The `nuxt.config.ts` i18n configuration must explicitly list the landing namespace. Check the pattern used for `screening.json`, `identity.json`, etc. and follow the same registration pattern.

3. **Layout meta declaration:** Story 3.0a established the pattern: `definePageMeta({ layout: 'public' })` in the page's `<script setup>`. This must be used in the new `index.vue`.

4. **Vitest mocking pattern:** Router navigation is mocked via `vi.fn()` on `useRouter().push`. The `navigateTo` Nuxt helper may need to be mocked separately — check how `useRouter` is stubbed in `vitest.setup.ts`.

5. **CSS token usage:** All colors use the semantic names from `main.css` (`bg-authority`, `text-reliable`, `text-at-risk`, `text-stale`). Never use raw hex values in templates.

6. **Breakpoint pattern:** Tailwind responsive prefixes (`sm:`, `md:`, `lg:`) are used directly in templates. Mobile-first approach: default styles are mobile, then override at breakpoints.

### Git Intelligence Summary

Recent commit pattern analysis (last 15 commits):

| Pattern | Convention |
|---|---|
| Commit prefix | `feat(frontend):` for new features, `fix:` for fixes, `chore:` for maintenance |
| Scope | Module name in parentheses: `(frontend)`, `(screening)`, `(auth)`, `(infra)` |
| Story reference | Story ID mentioned in commit message body |
| Most recent relevant commit | `feat(frontend): implement Story 3.0a — Safe Harbor design system and application shell` |

**Recent work context:** The codebase just completed Story 3.0a which set up the entire design system (color tokens, typography, motion tokens), application shell (sidebar, top bar, layouts), and scaffolded the `public.vue` layout. Story 3.0b directly builds on top of this foundation.

### Project Context Reference

**Product:** RiskGuard (PartnerRadar) — B2B SaaS for Hungarian SME partner risk screening and EPR compliance
**Primary language:** Hungarian (with English fallback)
**Target users for this story:** Unauthenticated visitors — SME owners and accountants discovering the product
**Business goal:** Convert visitors to registered users via zero-friction tax number search experience
**Technical context:** Nuxt 3 frontend with PrimeVue 4, Tailwind CSS 4, deployed to Cloud Storage/CDN. Backend is Spring Boot 4.0.3 on Cloud Run (not touched by this story).
**Data source mode:** Demo mode active for development — no real NAV API calls needed for landing page (the landing page doesn't call the API at all in this story)

### Story Completion Status

**Status:** ready-for-dev
**Confidence:** HIGH — All artifacts thoroughly analyzed, all gaps identified, all technical decisions documented with source references.
**Risk:** LOW — Frontend-only, additive content, no existing component modifications beyond safe refactoring.
**Estimated complexity:** MEDIUM — 10 new files, 4 modified files, 34 subtasks across 9 tasks.
**Dependencies:** Story 3.0a (DONE) provides the design system and application shell. No blocking dependencies.
**Blocked stories:** Story 3.11 (SEO Gateway Stubs) and Story 3.12 (Demo Mode and Guest Rate Limiting) depend on this landing page.

### References

| Reference | Source | Section |
|---|---|---|
| Landing page wireframe (desktop + mobile) | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 11.1 |
| Gateway density profile (spacing, typography) | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 10.4 |
| Card system variants (Landing Hero Card) | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 10.3 |
| Vault Pivot design direction | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 4 |
| Gabor's Midnight Risk Check flow | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 12.1 |
| Guest conversion flow | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 12.5 |
| i18n namespace-per-file pattern | `_bmad-output/planning-artifacts/architecture.md` | Section: i18n File Organization |
| Frontend Implementation Checklist (8 steps) | `_bmad-output/planning-artifacts/architecture.md` | Section: Frontend Implementation Checklist |
| SEO gateway stubs (Nuxt hybrid rendering) | `_bmad-output/planning-artifacts/architecture.md` | Section: Important Decisions |
| Safe Harbor color system + design tokens | `frontend/app/assets/css/main.css` | Lines 1-31 |
| Existing SearchBar implementation | `frontend/app/components/Screening/SearchBar.vue` | Full file (92 lines) |
| Screening store search flow | `frontend/app/stores/screening.ts` | Full file (85 lines) |
| Public layout scaffold | `frontend/app/layouts/public.vue` | Full file (44 lines) |
| Current index.vue redirect | `frontend/app/pages/index.vue` | Full file (3 lines) |
| Story 3.0a implementation notes | `_bmad-output/implementation-artifacts/3-0a-design-system-tokens-and-application-shell.md` | Dev Notes, Code Review sections |
| Motion design tokens | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 14.1 |
| Button hierarchy and feedback states | `_bmad-output/planning-artifacts/ux-design-specification.md` | Sections 7.1, 14.6 |
| Hungarian tax number format | `_bmad-output/planning-artifacts/ux-design-specification.md` | Section 7.3 |

## Senior Developer Review (AI) — Round 1 — 2026-03-16

**Reviewer:** External code review (REVIEW_FINDINGS.md)
**Issues Found:** 2 HIGH, 3 MEDIUM, 2 LOW
**Issues Fixed:** 2 HIGH, 3 MEDIUM, 2 LOW (7/7 — all resolved)

**Action Items:**
- [x] [HIGH] Wrong CSS selector in E2E test (`.text-red-500` → `data-testid="validation-error"`)
- [x] [HIGH] Partial adapter data discarded for unavailable adapters (CompanyDataAggregator)
- [x] [MEDIUM] Inconsistent timestamps across audit trail (3 separate `OffsetDateTime.now()` calls)
- [x] [MEDIUM] Duplicate onMounted hooks with navigation race (pages/index.vue)
- [x] [MEDIUM] Stale package-info documentation (testing module profiles)
- [x] [LOW] Unused HashMap import in ScreeningService.java
- [x] [LOW] Redundant `-Dspring.profiles.active=test` in CI workflow

## Senior Developer Review (AI) — Round 2 — 2026-03-16

**Reviewer:** Adversarial code review (dev agent)
**Issues Found:** 2 HIGH, 3 MEDIUM, 2 LOW
**Issues Fixed:** 2 HIGH, 3 MEDIUM (5/5 mandatory fixes — all resolved)
**Low issues noted:** 2 LOW (tech debt, not blocking)

**Action Items:**
- [x] [HIGH] Weak test assertions for tax number formatting — `toContain('1234')` → `toBe('1234-5678')`
- [x] [HIGH] Missing WCAG 2.1 AA accessibility on search form — added label, aria-label, aria-describedby, aria-invalid, role="search"
- [x] [MEDIUM] Non-reactive SEO meta — static `t()` values → getter functions `() => t(...)`
- [x] [MEDIUM] Static JSON-LD schema — `JSON.stringify` → `computed()` wrapper
- [x] [MEDIUM] Inline CSS grid layout — raw `style` → Tailwind `grid gap-8` classes
- [ ] [LOW] Triple `nextTick()` in health check test — fragile timing pattern (tech debt)
- [ ] [LOW] No neutral typography tokens in design system — `text-slate-*` used without semantic aliases (tech debt, project-wide)

## Dev Agent Record

### Agent Model Used

duo-chat-opus-4-6

### Debug Log References

No halts or debug issues encountered. Clean implementation throughout.

### Completion Notes List

- **Task 1:** Created `hu/landing.json` and `en/landing.json` with 20 i18n keys covering hero, search, features, social proof, disclaimer, and SEO. Registered `landing` namespace in `nuxt.config.ts`. Parity check passed.
- **Task 2:** Extracted `formatTaxNumber()` and `taxNumberSchema` into `utils/taxNumber.ts`. Refactored `SearchBar.vue` to import from shared utility. All 18 existing SearchBar tests pass unchanged.
- **Task 3:** Created `LandingSearchBar.vue` with PrimeVue InputText+Button, intelligent masking, Zod validation, auth redirect on submit, service unavailable state via prop, and responsive mobile-first layout (flex-col default, md:flex-row for desktop). 12 tests pass.
- **Task 4:** Created `LandingFeatureCards.vue` with 3 value proposition cards (shield/clock/lock inline SVG icons), CSS Grid `auto-fill, minmax(280px, 1fr)`, Landing Hero Card styling (p-8, rounded-2xl, shadow-lg). 7 tests pass.
- **Task 5:** Created `LandingSocialProof.vue` with trust signals section (trusted-by text, partner count badge, 3 trust badges). All text via i18n. 5 tests pass.
- **Task 6:** Adjusted `public.vue` layout — removed `max-w-prose` from slot wrapper, kept `leading-relaxed` on `<main>`. Verified `auth/login.vue` and `login/callback.vue` both manage own widths and are unaffected.
- **Task 7:** Rewrote `pages/index.vue` from 3-line redirect to full landing page. Auth-aware redirect (AC5), SEO meta via `useSeoMeta()` (AC4), JSON-LD Organization schema via `useHead()` (AC4), composed from LandingSearchBar + LandingFeatureCards + LandingSocialProof + disclaimer. Lightweight `/actuator/health` ping for service availability (AC7).
- **Task 8:** Added `'/': { ssr: true }` to `routeRules` in `nuxt.config.ts` for explicit SSR on landing page.
- **Task 9:** Created `pages/index.spec.ts` with 14 integration tests covering layout, component composition, auth redirect, SEO meta, JSON-LD schema, service unavailability. Full suite: 236 tests pass (198 existing + 38 new). ESLint: 0 errors (9 pre-existing warnings). i18n parity: passed.

### Implementation Plan

Red-green-refactor cycle followed for all component tasks. Tests written first (RED), component implemented (GREEN), then formatting cleanup (REFACTOR). No new dependencies added. All architecture compliance checklist items satisfied.

### File List

**New files (10):**
- `frontend/app/utils/taxNumber.ts` — Shared tax number formatting + Zod validation
- `frontend/app/i18n/hu/landing.json` — Hungarian landing page translations
- `frontend/app/i18n/en/landing.json` — English landing page translations
- `frontend/app/components/Landing/LandingSearchBar.vue` — Public search CTA component
- `frontend/app/components/Landing/LandingSearchBar.spec.ts` — Unit tests (12 tests)
- `frontend/app/components/Landing/LandingFeatureCards.vue` — Value proposition cards
- `frontend/app/components/Landing/LandingFeatureCards.spec.ts` — Unit tests (7 tests)
- `frontend/app/components/Landing/LandingSocialProof.vue` — Trust signals section
- `frontend/app/components/Landing/LandingSocialProof.spec.ts` — Unit tests (5 tests)
- `frontend/app/pages/index.spec.ts` — Page-level integration tests (14 tests)

**Modified files (8):**
- `frontend/app/pages/index.vue` — REWRITTEN from 3-line redirect to full landing page; merged duplicate onMounted hooks (review fix)
- `frontend/app/layouts/public.vue` — Removed `max-w-prose` from slot wrapper, kept `leading-relaxed` on `<main>`
- `frontend/app/components/Screening/SearchBar.vue` — Refactored to import from `utils/taxNumber.ts`; added `data-testid="validation-error"` (review fix)
- `frontend/app/components/Landing/LandingSearchBar.vue` — Added `data-testid="validation-error"` (review fix)
- `frontend/nuxt.config.ts` — Added `landing` i18n namespace + `'/'` SSR route rule
- `frontend/e2e/search-flow.e2e.ts` — Fixed selector from `.text-red-500` to `[data-testid="validation-error"]` (review fix)
- `backend/src/main/java/hu/riskguard/datasource/internal/CompanyDataAggregator.java` — Preserve partial adapter data for unavailable sources (review fix)
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — Standardized timestamps, removed unused import (review fix)
- `backend/src/main/java/hu/riskguard/testing/package-info.java` — Updated profile docs to include "e2e" (review fix)
- `.github/workflows/ci.yml` — Removed redundant `-Dspring.profiles.active=test` JVM arg (review fix)
- `frontend/app/pages/index.spec.ts` — Adjusted nextTick count for combined onMounted async chain (review fix)

## Change Log

- 2026-03-16: Story 3.0b implemented — Public landing page with zero-friction tax number search, 3 value proposition feature cards, social proof section, SSR + SEO meta + JSON-LD, auth-aware redirect, mobile responsive layout, service unavailability handling. 38 new tests added (236 total). Extracted shared `taxNumber.ts` utility from existing SearchBar.
- 2026-03-16: Addressed code review findings (Round 1) — 7 items resolved (2 HIGH, 3 MEDIUM, 2 LOW). E2E test selector fixed to use data-testid, adapter data preservation for degraded sources, audit trail timestamps standardized, duplicate onMounted race condition eliminated, stale docs updated, unused import removed, redundant CI profile flag removed.
- 2026-03-16: Adversarial code review (Round 2) — 5 items fixed (2 HIGH, 3 MEDIUM). Weak format test assertions strengthened to exact match, WCAG 2.1 AA accessibility added to search form (label, aria-label, aria-describedby, role="search"), SEO meta made reactive with getter functions, JSON-LD wrapped in computed(), inline CSS grid replaced with Tailwind classes. 3 new accessibility tests added (239 total). 2 LOW tech debt items noted.
