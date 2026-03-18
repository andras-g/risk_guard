# Story 3.1: Internationalization (i18n) Infrastructure

Status: done

Story ID: 3.1
Story Key: 3-1-internationalization-i18n-infrastructure
Epic: 3 — Automated Monitoring & Alerts (Watchlist)
Created: 2026-03-17

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a User,
I want the application to be available in Hungarian (primary) and English (fallback),
so that I can use the product comfortably in my preferred language.

## Acceptance Criteria

1. **Given** a Nuxt 4 frontend with `@nuxtjs/i18n` configured, **When** the application loads, **Then** Hungarian (hu) is the default locale and English (en) is the fallback locale.
2. **Given** the frontend application, **When** any user-facing text is rendered, **Then** all text is sourced from JSON message files (`frontend/app/i18n/hu/*.json`, `frontend/app/i18n/en/*.json`) — zero hardcoded strings.
3. **Given** an authenticated user with `preferred_language` set in the `users` table, **When** they log in, **Then** the application switches to their preferred locale automatically.
4. **Given** the application header or user menu, **When** I click the language switcher, **Then** I can toggle between HU and EN, and the entire UI re-renders in the selected language without a page reload.
5. **Given** the Spring Boot backend, **When** server-generated content is needed (emails, exports), **Then** the backend uses `messages_hu.properties` and `messages_en.properties` for localized content.
6. **Given** a missing translation key in Hungarian, **When** the application renders that key, **Then** the English fallback is displayed without crashing or showing a raw key.

## Tasks / Subtasks

- [x] Task 1: Audit and complete frontend i18n JSON message files (AC: #1, #2, #6)
  - [x] 1.1 Audit ALL existing `.vue` components for any remaining hardcoded user-facing strings that are NOT using `$t()` or `t()`. Run ESLint with `@intlify/vue-i18n/no-raw-text` (already installed from Story 3.0c). Document any violations.
  - [x] 1.2 For each violation found in 1.1, extract the hardcoded string, create an appropriate i18n key following the namespace convention (`{module}.{page}.{component}.{element}`), and add the key to BOTH `hu/{module}.json` AND `en/{module}.json`. Replace the hardcoded string with `$t('key')`.
  - [x] 1.3 Verify the existing i18n file organization follows the architecture spec: `common.json` (shared actions, states, errors), `screening.json`, `landing.json`, `identity.json`, `auth.json`. Confirm all files are sorted alphabetically by key. Fix any out-of-order keys.
  - [x] 1.4 Run `npm run check-i18n` to verify key parity between `hu/*.json` and `en/*.json`. Fix any missing keys.
  - [x] 1.5 Verify that `@nuxtjs/i18n` fallback behavior works: temporarily remove a key from `hu/common.json`, confirm the EN value displays, then restore the key.
  - [x] 1.6 Run `npm run lint` — 0 errors expected (including the `no-raw-text` rule).
  - [x] 1.7 Run `npm run test` — all existing tests (289) must still pass.

- [x] Task 2: Create the LocaleSwitcher component (AC: #4)
  - [x] 2.1 Create `frontend/app/components/Common/LocaleSwitcher.vue` — a button/dropdown that shows the current locale flag/label ("HU" / "EN") and toggles to the other. Use `useI18n()` composable to get/set locale. On click, call `setLocale('hu')` or `setLocale('en')`. Use PrimeVue `Select` or a simple `<button>` with the locale codes. Style: compact, fits in the top-bar next to AppUserMenu.
  - [x] 2.2 Add i18n keys for the switcher to `hu/common.json` and `en/common.json`: `common.locale.switchTo` ("Váltás angolra" / "Switch to Hungarian"), `common.locale.currentLanguage` ("Magyar" / "English").
  - [x] 2.3 Add ARIA label to the LocaleSwitcher button: `$t('common.locale.switchTo')` so screen readers announce the action.
  - [x] 2.4 Create `frontend/app/components/Common/LocaleSwitcher.spec.ts` — tests: renders current locale label, clicking toggles locale, has aria-label, uses i18n keys.
  - [x] 2.5 Integrate `<CommonLocaleSwitcher />` into `AppTopBar.vue` — place it BEFORE the `AppUserMenu` in the top-bar right section. On mobile, also include it in `AppMobileDrawer.vue` nav section.
  - [x] 2.6 Run `npm run check-i18n` to verify key parity.

- [x] Task 3: Persist locale preference on login (AC: #3)
  - [x] 3.1 In the existing `UserProfileResponse` DTO (or equivalent), verify that `preferredLanguage` field is exposed from the backend API. The `users` table already has `preferred_language VARCHAR(10) NOT NULL DEFAULT 'hu'` (confirmed in migration V20260305_001).
  - [x] 3.2 In the frontend auth flow (after successful login/SSO callback), read `user.preferredLanguage` from the user profile API response. Call `setLocale(user.preferredLanguage)` to switch the UI to the user's stored preference.
  - [x] 3.3 When the user changes locale via LocaleSwitcher, send a `PATCH /api/v1/users/me/preferences` (or equivalent) request to persist the `preferred_language` value to the `users` table. This ensures the preference is remembered across sessions and devices.
  - [x] 3.4 For guest users (no auth), persist locale preference in a cookie only (no API call). The `@nuxtjs/i18n` `detectBrowserLanguage.useCookie` option handles this.
  - [x] 3.5 Update the `useAuth` composable or create a `useLocaleSync` composable that handles the login → locale sync → API persist flow.

- [x] Task 4: Backend i18n message bundles (AC: #5)
  - [x] 4.1 Create `backend/src/main/resources/messages.properties` (default/fallback — English content).
  - [x] 4.2 Create `backend/src/main/resources/messages_hu.properties` (Hungarian content).
  - [x] 4.3 Create `backend/src/main/resources/messages_en.properties` (English content — mirrors default).
  - [x] 4.4 Populate initial message keys for: error messages returned by `GlobalExceptionHandler` (RFC 7807 `detail` field for server-generated messages only — note: most error mapping happens on the frontend via `useApiError`), email templates (Story 3.8 will use these — add placeholder keys now: `email.subject.statusChange`, `email.body.statusChange`), export locale labels (`export.locale.notice` = "Export generated in Hungarian (required by MOHU)").
  - [x] 4.5 Configure `AcceptHeaderLocaleResolver` in a new `I18nConfig.java` (or add to existing `SecurityConfig.java`): set default locale to `hu`, supported locales to `[hu, en]`. This allows the backend to resolve locale from the `Accept-Language` header sent by the Nuxt frontend.
  - [x] 4.6 Verify `spring.messages.basename=messages` is set in `application.yml` (Spring Boot auto-configures `MessageSource` but confirm).
  - [x] 4.7 Write a unit test: `I18nConfigTest.java` — verify that `MessageSource` resolves a key in both `hu` and `en` locales.

- [x] Task 5: Frontend locale-aware API calls (AC: #3, #5)
  - [x] 5.1 Update the `useApi` composable (or the global `$fetch` interceptor) to send `Accept-Language` header matching the current i18n locale on every API call. This ensures the backend resolves the correct locale for any server-generated content.
  - [x] 5.2 Verify that the `useDateShort`, `useDateFull`, `useDateRelative` composables (created in architecture spec, may or may not be implemented yet) are locale-aware — they should use `Intl.DateTimeFormat` with the current i18n locale. If not implemented, create stubs that use the current locale from `useI18n()`.
  - [x] 5.3 Write tests for the API `Accept-Language` header injection.

- [x] Task 6: Nuxt i18n configuration hardening (AC: #1, #6)
  - [x] 6.1 Review current `nuxt.config.ts` i18n configuration. The project uses `strategy: 'no_prefix'` which is correct for this SPA-first app (no locale in URL). Verify `defaultLocale: 'hu'` and `lazy: false` (note: @nuxtjs/i18n v10 always lazy-loads — if using v10, remove the `lazy: false` setting as it may be ignored or cause a warning).
  - [x] 6.2 Add `detectBrowserLanguage` configuration: `{ useCookie: true, cookieKey: 'rg_locale', redirectOn: 'root', fallbackLocale: 'en' }`. This detects browser language on first visit and persists preference in a cookie.
  - [x] 6.3 Add `i18n.config.ts` (Vue I18n runtime config) if not present: configure `fallbackLocale: 'en'`, `missingWarn: false` (production), `fallbackWarn: false` (production). In development mode, enable warnings to catch missing keys early.
  - [x] 6.4 Ensure all locale files are registered in `nuxt.config.ts` `i18n.locales[].files` array. Currently registered: `common.json`, `auth.json`, `identity.json`, `landing.json`, `screening.json`. These are complete for the current codebase.
  - [x] 6.5 Verify the SEO meta tags on the landing page (`pages/index.vue`) react correctly to locale changes (Story 3.0b already made these reactive via getters — confirm they still work with explicit locale switching).
  - [x] 6.6 Add `<html lang>` attribute management: `@nuxtjs/i18n` should automatically set `<html lang="hu">` or `<html lang="en">` based on current locale. Verify this works.

- [x] Task 7: End-to-end locale switching verification (AC: #1-#6)
  - [x] 7.1 Create `frontend/test/i18n/locale-switching.spec.ts` — integration test: render a component, switch locale from `hu` to `en`, verify all `$t()` calls return English values.
  - [x] 7.2 Create `frontend/test/i18n/fallback.spec.ts` — test: when a key is missing in `hu`, the `en` fallback value is returned.
  - [x] 7.3 Run the full test suite: `npm run test` — all tests pass (existing 289 + new i18n tests).
  - [x] 7.4 Run `npm run lint` — 0 errors.
  - [x] 7.5 Run `npm run check-i18n` — key parity confirmed.

## Dev Notes

### Why This Story Exists

The RiskGuard application already has `@nuxtjs/i18n` installed and configured (added in Story 3.0a), with 5 JSON namespace files per locale (common, auth, identity, landing, screening). However, the i18n infrastructure is incomplete:

1. **No LocaleSwitcher component** — users cannot toggle between HU/EN. The architecture spec defines `Common/LocaleSwitcher.vue` in the frontend structure, but it has not been implemented.
2. **No locale persistence on login** — the `users.preferred_language` column exists in the database but is never read by the frontend after login.
3. **No backend message bundles** — `messages_hu.properties` and `messages_en.properties` do not exist yet. The backend has zero i18n infrastructure.
4. **No `Accept-Language` header** on API calls — the frontend doesn't tell the backend which locale the user has selected.
5. **No browser locale detection** — `detectBrowserLanguage` is not configured in `nuxt.config.ts`.
6. **Possible hardcoded strings** — while Story 3.0c added ESLint `@intlify/vue-i18n/no-raw-text` enforcement, there may be pre-existing strings that predate the rule (especially in components from Epic 2).

This story completes the i18n foundation so that all subsequent stories (3.2 through 3.12 and beyond) can build on a fully operational bilingual system.

### Current State Analysis

**Frontend i18n (PARTIALLY DONE):**
- `@nuxtjs/i18n` module installed and registered in `nuxt.config.ts`
- `defaultLocale: 'hu'`, `strategy: 'no_prefix'` configured
- 5 namespace files per locale: `common.json`, `auth.json`, `identity.json`, `landing.json`, `screening.json`
- `lazy: false` set (may need adjustment for @nuxtjs/i18n v10)
- `$t()` used throughout all components added in Stories 3.0a, 3.0b, 3.0c
- ESLint `@intlify/vue-i18n/no-raw-text` enforced (from 3.0c)
- `check-i18n` script validates key parity
- `detectBrowserLanguage` NOT configured
- No `i18n.config.ts` runtime config file
- No `LocaleSwitcher.vue` component

**Backend i18n (NOT DONE):**
- `preferred_language` column exists in `users` table with default `'hu'`
- No `messages.properties` / `messages_hu.properties` / `messages_en.properties` files
- No `LocaleResolver` configured
- No `Accept-Language` header handling
- Error responses use raw error keys (RFC 7807 `type` field), NOT localized messages — this is BY DESIGN per architecture (frontend does the i18n mapping via `useApiError`)

**Architecture i18n Spec (MUST FOLLOW):**
- Frontend i18n JSON files are the SINGLE source of truth for all user-facing text
- Backend `messages_*.properties` reserved for: email templates, government export content, audit log descriptions
- Backend API returns error CODES only (RFC 7807 `type` field) — frontend maps codes to localized messages
- Display locale follows `user.preferred_language`; export locale is ALWAYS Hungarian via `@ExportLocale("hu")`
- Namespace-per-file: `i18n/hu/common.json`, `i18n/hu/screening.json`, etc.
- Files MUST be sorted alphabetically by key
- `common.*` namespace for reusable text; module keys may NOT duplicate `common.*` values
- 4 date formatting composables: `useDateShort`, `useDateFull`, `useDateRelative`, `useDateApi`
- ESLint `@intlify/vue-i18n/no-raw-text` — zero tolerance for hardcoded strings
- CI key-parity check: every key in `hu/*.json` must exist in `en/*.json` and vice versa

### Key Decisions

1. **Keep `strategy: 'no_prefix'`** — no locale prefix in URLs. This project is an SPA-first B2B app, not a content site. SEO is handled by the public landing page (SSR) and company gateway stubs (ISR), both of which use Hungarian as the primary language. Adding `/en/` prefixes would break existing routes and add unnecessary complexity.

2. **Backend i18n is MINIMAL** — the architecture explicitly states that the frontend owns ALL user-facing text. Backend message bundles are only for: (a) email templates (Story 3.8), (b) export content (Story 5.3/5.4), (c) audit log descriptions. We create the infrastructure and placeholder keys now, not full translations.

3. **LocaleSwitcher in top-bar, NOT a dropdown** — for a 2-locale app, a simple toggle button ("HU" ↔ "EN") is more efficient than a dropdown. Users click once to switch. This follows the pattern established by many Hungarian B2B apps.

4. **Persist preference via API on explicit switch only** — when the user clicks the LocaleSwitcher, we persist to the backend. On page load, we read from the cookie first (fast), then sync with the backend preference on login.

5. **@nuxtjs/i18n version alignment** — the project already has `@nuxtjs/i18n` installed. Check the current version in `package.json`. If v9.x, the `lazy: false` option is valid. If v10.x, remove `lazy` (always lazy in v10) and adjust config accordingly. Do NOT upgrade major versions — use whatever is currently installed.

### Predecessor Context (Stories 3.0a, 3.0b, 3.0c)

**From Story 3.0a (Design System):**
- `@nuxtjs/i18n` module installed and configured with 5 locale files per language
- `AppTopBar.vue` has a right section with `AppUserMenu` — LocaleSwitcher goes before it
- All components use `$t()` for visible text
- `renderWithProviders` test helper wraps with i18n plugin

**From Story 3.0b (Landing Page):**
- Landing page SEO meta tags are reactive to locale changes (getters, not static values)
- `LandingSearchBar.vue` uses `$t()` extensively
- JSON-LD `Organization` schema uses locale-aware values

**From Story 3.0c (WCAG Accessibility):**
- `eslint-plugin-vuejs-accessibility` + `@intlify/vue-i18n/no-raw-text` enforced
- All ARIA labels use `$t('common.a11y.*')` keys
- 289 tests passing, 0 ESLint errors, i18n key parity verified
- `LiveRegion.vue` uses i18n for screen reader announcements
- `check-i18n` script confirmed working

### Git Intelligence Summary

| Pattern | Convention |
|---|---|
| Commit prefix | `feat(frontend):` or `feat(backend):` for new features |
| Scope | Module in parentheses: `(frontend)`, `(backend)`, `(screening)` |
| Last commit | `feat(frontend): implement Story 3.0a -- Safe Harbor design system and application shell` |
| Uncommitted work | Stories 3.0b and 3.0c work exists but is not yet committed |

### Project Structure Notes

**New files to create:**

| File | Purpose |
|---|---|
| `frontend/app/components/Common/LocaleSwitcher.vue` | Language toggle component (HU/EN) |
| `frontend/app/components/Common/LocaleSwitcher.spec.ts` | Unit tests for LocaleSwitcher |
| `frontend/app/composables/i18n/useLocaleSync.ts` | Composable: syncs locale with user preference on login and persists changes |
| `frontend/app/composables/i18n/useLocaleSync.spec.ts` | Unit tests for useLocaleSync |
| `frontend/i18n.config.ts` | Vue I18n runtime config (fallbackLocale, warnings) |
| `frontend/test/i18n/locale-switching.spec.ts` | Integration test for locale switching |
| `frontend/test/i18n/fallback.spec.ts` | Test for missing key fallback behavior |
| `backend/src/main/resources/messages.properties` | Default message bundle (English fallback) |
| `backend/src/main/resources/messages_hu.properties` | Hungarian message bundle |
| `backend/src/main/resources/messages_en.properties` | English message bundle |
| `backend/src/main/java/hu/riskguard/core/config/I18nConfig.java` | LocaleResolver + MessageSource config |
| `backend/src/test/java/hu/riskguard/core/config/I18nConfigTest.java` | Unit test for i18n configuration |

**Existing files to modify:**

| File | Change |
|---|---|
| `frontend/nuxt.config.ts` | Add `detectBrowserLanguage` config, possibly remove `lazy: false` |
| `frontend/app/components/Common/AppTopBar.vue` | Add `<CommonLocaleSwitcher />` before AppUserMenu |
| `frontend/app/components/Common/AppMobileDrawer.vue` | Add LocaleSwitcher in mobile nav |
| `frontend/app/components/Common/AppTopBar.spec.ts` | Add test for LocaleSwitcher presence |
| `frontend/app/i18n/hu/common.json` | Add `common.locale.*` keys |
| `frontend/app/i18n/en/common.json` | Add `common.locale.*` keys |
| `frontend/app/composables/api/useApi.ts` | Add `Accept-Language` header to API calls |
| `frontend/app/composables/auth/useAuth.ts` | Add locale sync on login |
| `backend/src/main/resources/application.yml` | Verify `spring.messages.basename=messages` |

**No database migrations needed** — `preferred_language` column already exists.

**Naming convention compliance:**
- `LocaleSwitcher.vue` follows PascalCase in `Common/` directory (peer to existing AppSidebar, AppTopBar, SkipLink, LiveRegion)
- `useLocaleSync.ts` follows camelCase in `composables/i18n/` (new subdirectory, parallels `composables/a11y/`, `composables/formatting/`, `composables/api/`, `composables/auth/`)
- Backend `I18nConfig.java` follows PascalCase in `core/config/` (peer to SecurityConfig, JacksonConfig, etc.)
- Test files co-located with source files

### Architecture Compliance Checklist

- [ ] **i18n: No hardcoded strings.** All new visible text uses `$t()`. New keys added for all locale switcher text and ARIA labels. ESLint `@intlify/vue-i18n/no-raw-text` enforced.
- [ ] **i18n: Key parity.** Every new key in `hu/*.json` exists in `en/*.json` and vice versa. Run `npm run check-i18n`.
- [ ] **i18n: Alphabetical sort.** All JSON files sorted alphabetically by key.
- [ ] **i18n: Namespace-per-file.** No new namespace files created (existing 5 per locale are sufficient). `common.locale.*` keys go in `common.json`.
- [ ] **Component naming: PascalCase.** `LocaleSwitcher.vue` follows convention.
- [ ] **Co-located specs.** Every new `.vue` and `.ts` file has a co-located `.spec.ts`.
- [ ] **Module facade pattern.** Backend i18n config is in `core/config/` (shared infrastructure, not a module). No module boundary violations.
- [ ] **Frontend Implementation Checklist.** Steps 1-8 followed for LocaleSwitcher component.
- [ ] **Test preservation.** All 289 existing tests must continue to pass.
- [ ] **Backend: messages_*.properties reserved for server-generated content only.** NOT for API error messages (those are RFC 7807 codes mapped by frontend).

### Library and Framework Requirements

**No new dependencies required.** All packages are already installed:
- `@nuxtjs/i18n` (already in `package.json` from Story 3.0a)
- `vue-i18n` (transitive dependency of `@nuxtjs/i18n`)
- Spring Boot `spring-boot-starter-web` (includes `MessageSource` auto-config)

**Do NOT add:**
- `vue-i18n-routing` (redundant with `@nuxtjs/i18n`)
- `i18next` or `react-i18next` (wrong framework)
- Any third-party locale detection library (use `@nuxtjs/i18n` built-in)
- Any backend i18n library beyond Spring's built-in `MessageSource`

**Version check:** Before starting, verify `@nuxtjs/i18n` version in `frontend/package.json`:
- If v9.x: `lazy: false` is valid, keep it
- If v10.x: remove `lazy` option (always lazy in v10), adjust config per v10 docs

### Testing Requirements

**New i18n-specific tests:**

| Test File | Key Test Cases |
|---|---|
| `LocaleSwitcher.spec.ts` | Renders current locale label ("HU"/"EN"); clicking toggles locale; has aria-label; emits no errors |
| `useLocaleSync.spec.ts` | Sets locale from user profile on login; persists locale change via API; handles guest mode (cookie-only) |
| `locale-switching.spec.ts` | Component renders HU text; after setLocale('en'), renders EN text; all $t() calls resolve |
| `fallback.spec.ts` | Missing HU key falls back to EN value; no console errors; no raw key displayed |
| `I18nConfigTest.java` | MessageSource resolves key in HU locale; resolves same key in EN locale; default locale is HU |

**Modified test files:**

| Test File | Added Assertions |
|---|---|
| `AppTopBar.spec.ts` | LocaleSwitcher component is rendered in the top-bar |

**Verification commands:**
```bash
cd frontend && npm run test          # All unit tests (existing 289 + new i18n)
cd frontend && npm run lint          # ESLint with no-raw-text rule (0 errors)
cd frontend && npm run check-i18n   # i18n key parity
cd backend && ./gradlew test         # Backend unit tests including I18nConfigTest
```

### Previous Story Intelligence (3.0c)

From the completed Story 3.0c code review and dev notes:

- **289 tests passing** (239 original + 50 new from 3.0c). All must continue to pass.
- **0 ESLint errors** (9 pre-existing warnings). Must maintain.
- **i18n key parity enforced** by `check-i18n` script.
- **`renderWithProviders` test helper** already wraps components with i18n plugin — all locale-dependent component tests work out of the box.
- **Story 3.0c established** `eslint-plugin-vuejs-accessibility` which includes `no-raw-text` enforcement from the `@intlify/vue-i18n` plugin.
- **PrimeVue 4 Aura** components are globally registered — LocaleSwitcher can use PrimeVue `Button` or `Select` components without imports.
- **Review pattern:** All findings documented with `[AI-Review]` tags, file paths, and line numbers. Follow this pattern.

### UX Specification References

| Reference | Source | Section |
|---|---|---|
| i18n Architecture Spec | `_bmad-output/planning-artifacts/architecture.md` | i18n & l10n Patterns (Step 5) |
| Display vs Export Locale | `_bmad-output/planning-artifacts/architecture.md` | i18n & l10n Patterns |
| Date Formatting Composables | `_bmad-output/planning-artifacts/architecture.md` | Localization Formatters |
| Frontend Structure (i18n/) | `_bmad-output/planning-artifacts/architecture.md` | Frontend Structure |
| i18n Requirement | `_bmad-output/planning-artifacts/epics.md` | i18n requirement (Additional Requirements) |
| PRD Language Requirement | `_bmad-output/planning-artifacts/prd.md` | Hungarian primary, English fallback |
| Project Context i18n Rule | `_bmad-output/project-context.md` | i18n: Use $t('key'), alphabetical JSON files |
| nuxt.config.ts (current) | `frontend/nuxt.config.ts` | i18n config block (lines 32-50) |
| users table migration | `backend/src/main/resources/db/migration/V20260305_001__create_identity_tables.sql` | preferred_language column |

### Latest Technical Information

**@nuxtjs/i18n (v10.2.x — verify installed version):**
- v10 always lazy-loads locale files — the `lazy` option was removed
- `detectBrowserLanguage` config options: `useCookie`, `cookieKey`, `redirectOn`, `fallbackLocale`
- Composables: `useI18n()` for `t()`, `locale`, `setLocale()`; `useSwitchLocalePath()` for locale links
- `i18n.config.ts` for Vue I18n runtime config (`fallbackLocale`, `missingWarn`, etc.)
- `<html lang>` attribute auto-managed by the module
- v10 breaking changes from v9: `lazy` removed, `tc()`/`$tc()` removed, hooks renamed to `i18n:localeSwitched`/`i18n:beforeLocaleSwitch`

**Spring Boot 4.0.3 MessageSource:**
- Auto-configures `ResourceBundleMessageSource` when `messages.properties` exists on classpath
- `spring.messages.basename=messages` (default) — supports comma-separated list for multiple bundles
- `AcceptHeaderLocaleResolver` reads `Accept-Language` header — ideal for stateless REST API
- `LocaleContextHolder` available in any Spring-managed bean for programmatic locale access

### Project Context Reference

**Product:** RiskGuard (PartnerRadar) — B2B SaaS for Hungarian SME partner risk screening and EPR compliance
**Primary language:** Hungarian (hu) with English (en) fallback
**Target users for this story:** All users — both Hungarian-speaking primary users and English-speaking secondary users (international accountants, EU auditors)
**Business goal:** Complete the bilingual foundation so all future stories build i18n-compliant features from day one
**Technical context:** Nuxt 4.3.1 with @nuxtjs/i18n (already installed), Spring Boot 4.0.3 with zero i18n infrastructure
**Dependencies:** Story 3.0a (DONE — i18n module installed), Story 3.0b (DONE — locale-reactive SEO), Story 3.0c (DONE — i18n ESLint enforcement). No blocking external dependencies.
**Blocked stories:** All subsequent stories inherit this i18n infrastructure. Story 3.8 (Email Alerts) depends on backend message bundles. Story 5.4 (Export Locale Enforcement) depends on `@ExportLocale` pattern.

### Story Completion Status

**Status:** done
**Confidence:** HIGH — Exhaustive artifact analysis. All gaps identified with specific file-level remediation plans. No ambiguity in acceptance criteria.
**Risk:** LOW — Most infrastructure is already in place (module installed, JSON files exist, ESLint enforced). Remaining work is: 1 new component (LocaleSwitcher), 1 new composable (useLocaleSync), backend message bundles (minimal), and config hardening.
**Estimated complexity:** MEDIUM — 12 new files, 9 modified files, 35+ subtasks across 7 tasks. The heaviest task is the hardcoded string audit (Task 1) which may surface pre-existing violations in Epic 2 components.
**Dependencies:** Stories 3.0a, 3.0b, 3.0c (all DONE).
**Blocked stories:** All Epic 3+ UI stories depend on this i18n foundation. Story 3.8 depends on backend message bundles.

## Dev Agent Record

### Agent Model Used

duo-chat-opus-4-6

### Debug Log References

- Task 1: Audited all 29 .vue files. Found 3 HIGH violations (hardcoded "Coming soon" on admin, epr, watchlist pages) + 3 LOW (colons in ProvenanceSidebar, brackets in SkeletonVerdictCard). All fixed.
- Task 4: Backend build requires running PostgreSQL for jOOQ codegen. Code compiles when jOOQ generated sources are present. I18nConfigTest requires @SpringBootTest with DB — runs in CI only.

### Completion Notes List

- **Task 1 complete:** Audited 29 .vue files, fixed 6 hardcoded string violations, added `common.placeholder.comingSoon` key, folded colons into i18n values for `screening.provenance.checkedAt` and `screening.freshness.label`, replaced `[ ]` text prefix with icon in SkeletonVerdictCard. Sorted `common.json` keys alphabetically. All 289 existing tests pass.
- **Task 2 complete:** Created `LocaleSwitcher.vue` — simple HU/EN toggle button with ARIA label. Added `common.locale.switchTo` and `common.locale.currentLanguage` keys. Integrated into AppTopBar (before UserMenu) and AppMobileDrawer. 6 new tests (LocaleSwitcher.spec.ts), updated AppTopBar.spec.ts and AppMobileDrawer.spec.ts with stubs and new assertions.
- **Task 3 complete:** `UserResponse.preferredLanguage` already exposed from backend `/me` endpoint. Created `useLocaleSync` composable (syncLocaleFromProfile + changeLocale). Updated auth store to read `preferredLanguage` from API. Added `PATCH /api/v1/identity/me/language` backend endpoint (IdentityController + UpdateLanguageRequest DTO + IdentityService + IdentityRepository). Guest mode uses cookie only. 7 new tests (useLocaleSync.spec.ts).
- **Task 4 complete:** Created 3 message bundle files (messages.properties, messages_hu.properties, messages_en.properties) with email template and export locale keys. Created I18nConfig.java with AcceptHeaderLocaleResolver (default: hu, supported: [hu, en]). Added `spring.messages.basename=messages` to application.yml. Created I18nConfigTest.java (4 test methods).
- **Task 5 complete:** Created `useApi` composable with `apiFetch()` that adds `Accept-Language` header matching current i18n locale. Created `useDateShort` and `useDateFull` stub composables (locale-aware via `Intl.DateTimeFormat`). Verified `useDateRelative` is already locale-aware. 5 new tests (useApi.spec.ts).
- **Task 6 complete:** Removed `lazy: false` from nuxt.config.ts (@nuxtjs/i18n v10 always lazy-loads). Added `detectBrowserLanguage` config with `rg_locale` cookie. Created `i18n.config.ts` runtime config with `fallbackLocale: 'en'` and environment-aware warning suppression. Verified SEO meta tags are reactive (getter functions). Verified `<html lang>` auto-managed by module.
- **Task 7 complete:** Created `locale-switching.spec.ts` (3 tests: HU rendering, EN switching, round-trip). Created `fallback.spec.ts` (3 tests: existing key, missing key fallback, no raw key display). Full suite: 315 tests pass, 0 lint errors, i18n key parity confirmed.

### File List

**New files (frontend):**
- `frontend/app/components/Common/LocaleSwitcher.vue`
- `frontend/app/components/Common/LocaleSwitcher.spec.ts`
- `frontend/app/composables/i18n/useLocaleSync.ts`
- `frontend/app/composables/i18n/useLocaleSync.spec.ts`
- `frontend/app/composables/api/useApi.ts`
- `frontend/app/composables/api/useApi.spec.ts`
- `frontend/app/composables/formatting/useDateShort.ts`
- `frontend/app/composables/formatting/useDateShort.spec.ts`
- `frontend/app/composables/formatting/useDateFull.ts`
- `frontend/app/composables/formatting/useDateFull.spec.ts`
- `frontend/app/plugins/api-locale.ts`
- `frontend/i18n.config.ts`
- `frontend/test/i18n/locale-switching.spec.ts`
- `frontend/test/i18n/fallback.spec.ts`

**New files (backend):**
- `backend/src/main/resources/messages.properties`
- `backend/src/main/resources/messages_hu.properties`
- `backend/src/main/resources/messages_en.properties`
- `backend/src/main/java/hu/riskguard/core/config/I18nConfig.java`
- `backend/src/main/java/hu/riskguard/identity/api/dto/UpdateLanguageRequest.java`
- `backend/src/test/java/hu/riskguard/core/config/I18nConfigTest.java`

**Modified files (frontend):**
- `frontend/nuxt.config.ts` — removed `lazy: false`, added `detectBrowserLanguage` config
- `frontend/app/i18n/hu/common.json` — added `locale.*`, `placeholder.comingSoon` keys; sorted `app.*` alphabetically
- `frontend/app/i18n/en/common.json` — added `locale.*`, `placeholder.comingSoon` keys; sorted `app.*` alphabetically
- `frontend/app/i18n/hu/screening.json` — folded colons into `checkedAt` and `freshness.label` values
- `frontend/app/i18n/en/screening.json` — folded colons into `checkedAt` and `freshness.label` values
- `frontend/app/components/Common/AppTopBar.vue` — added `<CommonLocaleSwitcher />`
- `frontend/app/components/Common/AppTopBar.spec.ts` — added LocaleSwitcher stub and presence test
- `frontend/app/components/Common/AppMobileDrawer.vue` — added LocaleSwitcher in drawer
- `frontend/app/components/Common/AppMobileDrawer.spec.ts` — added LocaleSwitcher stub and presence test
- `frontend/app/components/Screening/ProvenanceSidebar.vue` — removed hardcoded colons
- `frontend/app/components/Screening/SkeletonVerdictCard.vue` — replaced `[ ]` text with icon
- `frontend/app/components/Screening/SkeletonVerdictCard.spec.ts` — updated test for icon instead of `[ ]`
- `frontend/app/pages/admin/index.vue` — replaced hardcoded "Coming soon" with `$t()`
- `frontend/app/pages/epr/index.vue` — replaced hardcoded "Coming soon — Epic 4" with `$t()`
- `frontend/app/pages/watchlist/index.vue` — replaced hardcoded "Coming soon — Story 3.6" with `$t()`
- `frontend/app/stores/auth.ts` — added `preferredLanguage` to state, `fetchMe` response handling, and locale sync in `initializeAuth()`
- `frontend/app/components/Common/LocaleSwitcher.vue` — [AI-Review] added `title` attribute for sighted users
- `frontend/test/i18n/fallback.spec.ts` — [AI-Review] removed unused `vi` import
- `frontend/test/i18n/locale-switching.spec.ts` — [AI-Review] removed unused `vi` import

**Modified files (backend):**
- `backend/src/main/resources/application.yml` — added `spring.messages.basename=messages`
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java` — added `PATCH /me/language` endpoint
- `backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java` — added `updatePreferredLanguage` method
- `backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java` — added `updatePreferredLanguage` method; [AI-Review] removed redundant `@Transactional`

### Change Log

- **2026-03-17:** Story 3.1 implemented — Complete i18n infrastructure for HU/EN bilingual support. 11 new frontend files, 6 new backend files, 16 modified frontend files, 4 modified backend files. 26 new tests added (315 total, all passing). Zero hardcoded strings remaining. LocaleSwitcher in top-bar and mobile drawer. Backend AcceptHeaderLocaleResolver + message bundles. Frontend detectBrowserLanguage + i18n.config.ts runtime config. useLocaleSync composable for login-time locale sync and PATCH persistence. useApi composable for Accept-Language header injection.
- **2026-03-17:** [AI-Review] Adversarial code review — 8 findings (2 HIGH, 4 MEDIUM, 2 LOW), all resolved. H1: wired locale sync into `initializeAuth()` (AC #3 was broken — `syncLocaleFromProfile` never called). H2: created `api-locale.ts` global `$fetch` interceptor plugin for `Accept-Language` header injection (orphaned `useApi` composable was dead code). M1: added co-located specs for `useDateShort` and `useDateFull` (12 new tests). M2: removed redundant `@Transactional` from `IdentityRepository.updatePreferredLanguage`. M3: removed unused `vi` imports in 2 test files (lint warnings 11→9). L1: added `title` tooltip to LocaleSwitcher button. L2: fixed story completion status section. 327 tests pass (was 315). 0 lint errors, 9 warnings. Status → done.
