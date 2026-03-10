# Story 2.1: Tax Number Search & Skeleton UI

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a User,
I want to enter an 8 or 11-digit Hungarian tax number and see a loading animation that tracks the search progress,
so that I know the system is actively retrieving government data and I receive immediate feedback on invalid input.

## Acceptance Criteria

1. **Search Form Validation:** Given the `screening` module frontend dashboard, when I enter a tax number, then the input field auto-formats with visual masking (8-digit: `1234-5678`, 11-digit: `1234-5678-901`) and invalid formats are rejected by frontend Zod validation with a localized error message before any API call is made.

2. **Backend Search Endpoint:** Given a valid 11-digit Hungarian tax number submitted via the SearchBar, when the `PartnerSearchRequest` is sent to `POST /api/v1/screenings/search`, then the backend `ScreeningController` validates the input with `@HungarianTaxNumber`, creates a `company_snapshots` record, and returns a `VerdictResponse` (initially with status `INCOMPLETE` since scraper adapters are not yet implemented in this story).

3. **Skeleton Loading Animation:** Given a search in progress, when the API call is pending, then the UI displays PrimeVue `Skeleton` components showing source resolution progress with labeled placeholders: `[ ] NAV Debt`, `[ ] Legal Status`, `[ ] Company Registry` — each with a Skeleton bar animating in place of actual data.

4. **8-Digit Tax Number Handling:** Given an 8-digit tax number (company tax ID without check digits), when submitted, then the system accepts it and sends it to the backend, which stores and processes it equivalently to the 11-digit format (the backend normalizes/validates both formats).

5. **Screening Module Reference Implementation:** Given this is the first feature module, when the `screening` module is implemented, then it follows ALL architecture patterns exactly: module facade `@Service`, DTO records with `from()` factories, jOOQ repository scoped to owned tables (`company_snapshots`, `verdicts`, `search_audit_log`), application events, i18n keys in both `hu/screening.json` and `en/screening.json`, and co-located `.spec.ts` test files.

6. **Database Schema:** Given the screening module requirements, when Flyway migrations run, then the `company_snapshots`, `verdicts`, and `search_audit_log` tables are created with `tenant_id NOT NULL` constraints, proper indexes (BRIN on timestamps, B-tree on `tenant_id + tax_number`), and ENUM types for verdict status (`RELIABLE`, `AT_RISK`, `INCOMPLETE`, `TAX_SUSPENDED`, `UNAVAILABLE`) and confidence (`FRESH`, `STALE`, `UNAVAILABLE`).

## Tasks / Subtasks

- [x] **Task 1: Flyway Migrations — Screening Module Tables** (AC: 6)
  - [x] Create `V20260309_001__create_screening_tables.sql` with `company_snapshots`, `verdicts`, `search_audit_log` tables
  - [x] Define ENUM types: `verdict_status` (`RELIABLE`, `AT_RISK`, `INCOMPLETE`, `TAX_SUSPENDED`, `UNAVAILABLE`), `verdict_confidence` (`FRESH`, `STALE`, `UNAVAILABLE`)
  - [x] Add `tenant_id NOT NULL` FK to `tenants` on all three tables
  - [x] Add BRIN indexes on timestamp columns, B-tree on `(tenant_id, tax_number)` composites
  - [x] Verify migrations run cleanly against local Docker Compose PostgreSQL

- [x] **Task 2: Backend — Screening Module Scaffold** (AC: 2, 5)
  - [x] Create package structure: `hu.riskguard.screening.api/`, `hu.riskguard.screening.api.dto/`, `hu.riskguard.screening.domain/`, `hu.riskguard.screening.domain.events/`, `hu.riskguard.screening.internal/`
  - [x] Create `ScreeningService.java` — module facade `@Service` with `@Transactional`
  - [x] Create `ScreeningRepository.java` — jOOQ queries scoped to `company_snapshots`, `verdicts`, `search_audit_log` only
  - [x] Configure jOOQ codegen in `build.gradle` to scope screening module to its owned tables

- [x] **Task 3: Backend — DTOs and Validation** (AC: 2, 4)
  - [x] Create `PartnerSearchRequest.java` record with `@HungarianTaxNumber` validated `taxNumber` field
  - [x] Create or verify `@HungarianTaxNumber` custom validator in `core/validation/` — accepts 8-digit and 11-digit Hungarian tax numbers, rejects all other formats
  - [x] Create `VerdictResponse.java` record with `static from(Verdict)` factory method
  - [x] Create `CompanySnapshotResponse.java` record with `static from(CompanySnapshot)` factory method

- [x] **Task 4: Backend — Search Endpoint** (AC: 2)
  - [x] Create `core/util/HashUtil.java` — static method `sha256(String... parts)` that concatenates inputs and returns hex-encoded SHA-256 hash.
  - [x] Create `ScreeningController.java` with `POST /api/v1/screenings/search` accepting `@Valid @RequestBody PartnerSearchRequest`
  - [x] Implement search flow in `ScreeningService`: validate tax number → check idempotency guard (fresh snapshot < 15 min) → create stub `CompanySnapshot` with empty `snapshot_data` JSONB → create `Verdict` with status `INCOMPLETE` → write `search_audit_log` entry with SHA-256 hash via `HashUtil` → return `VerdictResponse`
  - [x] Publish `PartnerSearchCompleted` application event after search completes

- [x] **Task 5: Backend — Application Events** (AC: 5)
  - [x] Create `PartnerSearchCompleted.java` event record
  - [x] Create `PartnerStatusChanged.java` event record (placeholder for Story 2.3+)
  - [x] Document both events in `core/events/package-info.java` event catalog

- [x] **Task 6: Frontend — OpenAPI Type Generation** (AC: 5)
  - [x] Create `frontend/types/api.d.ts` with TypeScript types matching backend DTOs (manual creation — OpenAPI pipeline will regenerate when backend is live)
  - [x] Types include `PartnerSearchRequest`, `VerdictResponse`, `CompanySnapshotResponse`, `UserResponse`, `TenantResponse`, `TenantSwitchRequest`

- [x] **Task 7: Frontend — SearchBar Component** (AC: 1, 4)
  - [x] Create `frontend/app/components/Screening/SearchBar.vue` with `<script setup lang="ts">`
  - [x] Implement tax number input with auto-formatting mask (8-digit: `####-####`, 11-digit: `####-####-###`)
  - [x] Add regex validation — reject invalid formats with localized error via `$t('screening.search.invalidTaxNumber')`
  - [x] On valid submit, call `POST /api/v1/screenings/search` via Pinia store
  - [x] Create co-located `SearchBar.spec.ts` test file (18 tests)

- [x] **Task 8: Frontend — Skeleton Loading UI** (AC: 3)
  - [x] Create `frontend/app/components/Screening/SkeletonVerdictCard.vue` — shows PrimeVue `Skeleton` components mimicking the VerdictCard layout
  - [x] Display labeled source resolution placeholders: `[ ] NAV Debt`, `[ ] Legal Status`, `[ ] Company Registry`
  - [x] Each source row shows a `Skeleton` bar animating in place of actual data
  - [x] Toggle display based on `visible` prop (driven by store `isSearching` state)
  - [x] Create co-located `SkeletonVerdictCard.spec.ts` (6 tests)

- [x] **Task 9: Frontend — Screening Dashboard Page & Store** (AC: 1, 3)
  - [x] Create `frontend/app/pages/dashboard/index.vue` — integrate SearchBar and SkeletonVerdictCard
  - [x] Create `frontend/app/stores/screening.ts` Pinia store — manage search state, verdict cache, current tax number
  - [x] Wire SearchBar → store → API call → SkeletonVerdictCard display flow

- [x] **Task 10: Frontend — i18n Keys** (AC: 1, 5)
  - [x] Create `frontend/app/i18n/hu/screening.json` with all screening-related Hungarian translation keys
  - [x] Create `frontend/app/i18n/en/screening.json` with matching English keys (exact key parity)
  - [x] Keys include: `screening.search.placeholder`, `screening.search.submit`, `screening.search.invalidTaxNumber`, `screening.search.searching`, `screening.sources.navDebt`, `screening.sources.legalStatus`, `screening.sources.companyRegistry`, `screening.verdict.*`

- [x] **Task 11: Backend — Unit & Integration Tests** (AC: 2, 5, 6)
  - [x] Create `ScreeningControllerTest.java` — test `POST /api/v1/screenings/search` with valid/invalid tax numbers, mock `ScreeningService` (4 tests)
  - [x] Create `ScreeningServiceIntegrationTest.java` with `@Tag("integration")`, `@Testcontainers`, PostgreSQL container — test full search flow including DB writes and SHA-256 hash generation (4 tests)
  - [x] Create `HungarianTaxNumberValidatorTest.java` — test 8-digit, 11-digit, invalid formats (8 test methods)
  - [x] Verify `ModulithVerificationTest` passes with the new `screening` module

## Dev Notes

### Developer Context

**This is the most critical story in the entire project.** Story 2.1 establishes the `screening` module as the **reference implementation** that ALL future modules must follow. Every pattern decision here cascades to `epr`, `scraping`, `notification` modules. Get it right once, and the remaining 20+ stories follow the blueprint.

**What this story IS:**
- The `screening` module skeleton: controller → service facade → jOOQ repository → DTOs → events
- The SearchBar UI with Zod validation and Skeleton loading states
- Flyway migrations for 3 screening tables + ENUM types
- The OpenAPI → TypeScript → Zod pipeline exercised end-to-end for the first time on a feature module

**What this story is NOT:**
- No actual scraping (Story 2.2 implements `CompanyDataAggregator` with virtual threads + Resilience4j)
- No verdict logic (Story 2.3 implements `VerdictEngine` state machine)
- No verdict result card UI (Story 2.4 implements `VerdictCard.vue` with Emerald/Rose/Grey shields)
- No audit hash display (Story 2.5 adds SHA-256 to the frontend result card)
- The `VerdictResponse` returned by this story will always have `status: INCOMPLETE` because there are no scrapers yet — this is intentional and correct

**Idempotency Guard:** Before scraping (even stub scraping), check for a fresh snapshot (< 15 min old) for the same `tenant_id + tax_number`. If found, return the existing verdict. This prevents duplicate scrape requests. The 15-min threshold comes from `risk-guard-tokens.json` (not yet defined there — use a hardcoded constant with a TODO comment for now, or add the token).

### Technical Requirements

**Backend Stack (exact versions from architecture + verified latest):**
- Spring Boot 4.0.3 with Spring Modulith 2.0.3
- jOOQ 3.20.x (OSS, Apache 2.0) — codegen scoped per module
- PostgreSQL 17 (Docker Compose local, Neon staging, Cloud SQL prod)
- Java 25 (Temurin JDK)
- Flyway for migrations
- `HashUtil` for SHA-256 audit hashes — **DOES NOT EXIST YET**, must be created as `core/util/HashUtil.java` in this story

**Frontend Stack (exact versions from architecture + verified latest):**
- Nuxt 4.x with Vue 3 Composition API (`<script setup lang="ts">`)
- PrimeVue 4.5.x — `Skeleton`, `InputText`, `Button` components
- Pinia for state management
- Zod 4.x for schema validation (generated from OpenAPI + custom tax number validator)
- `useFetch` composable for API calls (reactive, auto-refetch)

**Key Technical Decisions:**
- **jOOQ codegen scoping:** The `screening` module must ONLY access `company_snapshots`, `verdicts`, `search_audit_log` tables. Configure `<includeTables>` in the jOOQ codegen configuration to limit generated code to these tables. This enforces the architecture's physical isolation boundary.
- **Tax number formats:** 8-digit = `adószám` (company tax ID), 11-digit = `adóazonosító jel` (full tax identification with area code + check digit). Backend must accept both. Store as VARCHAR(11) — if 8-digit received, pad or store as-is with a format indicator.
- **Tenant isolation:** Every query in `ScreeningRepository` MUST include `.where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))`. The `tenantId` is extracted from `TenantContext` (set by `TenantFilter` from the JWT claims).
- **RFC 7807 errors:** Validation failures return `application/problem+json` with `type: urn:riskguard:error:validation-failed`. Use Spring Boot's built-in `ProblemDetail` response for `@Valid` failures.

### Architecture Compliance

**Module Anatomy (MANDATORY pattern for screening — becomes the reference):**
```
backend/src/main/java/hu/riskguard/screening/
├── api/
│   ├── ScreeningController.java        # @RestController, delegates to facade only
│   └── dto/
│       ├── PartnerSearchRequest.java   # record with @Valid, @HungarianTaxNumber
│       ├── VerdictResponse.java        # record with static from() factory
│       └── CompanySnapshotResponse.java
├── domain/
│   ├── ScreeningService.java           # @Service facade — ONLY public entry point
│   └── events/
│       ├── PartnerSearchCompleted.java # Application event record
│       └── PartnerStatusChanged.java   # Placeholder for Story 2.3
├── internal/
│   └── ScreeningRepository.java        # jOOQ queries, package-private
└── package-info.java                   # Spring Modulith module declaration
```

**Architecture Rules (enforced by ArchUnit + Spring Modulith tests):**
1. Controller → only calls `ScreeningService` facade (never repository directly)
2. `internal/` package is NOT accessible from outside the `screening` module
3. No imports from other modules' `internal/` or `domain/` packages
4. Cross-module communication: facade calls (sync) or application events (async)
5. DTOs are `record` types with `static from(DomainObject)` factory methods — no manual field mapping
6. All user-facing strings via i18n keys — ZERO hardcoded strings in Vue templates

**Naming Conventions:**
- DB columns: `snake_case` (`tax_number`, `tenant_id`, `checked_at`)
- jOOQ generated: `camelCase` (auto-generated from DB columns)
- Java DTOs: `camelCase` fields (`taxNumber`, `tenantId`)
- JSON API: `camelCase` (`taxNumber`, `verdictStatus`)
- Vue components: `PascalCase` files (`SearchBar.vue`, `SkeletonVerdictCard.vue`)
- i18n keys: `dot.separated.camelCase` (`screening.search.invalidTaxNumber`)
- Flyway: `V{YYYYMMDD}_{NNN}__{description}.sql`

### Library & Framework Requirements

**Backend — No new dependencies needed.** All required libraries are already in `build.gradle`:
- `spring-boot-starter-web` — REST controllers
- `spring-boot-starter-validation` — `@Valid`, custom validators
- `spring-modulith-starter-core` — module verification
- `jooq` — database queries
- `flyway-core` + `flyway-database-postgresql` — migrations
- `spring-boot-starter-test` + `org.testcontainers:postgresql` — testing

**Backend — Configuration needed:**
- Add screening tables to jOOQ codegen `<includeTables>` in `build.gradle`
- Verify `@HungarianTaxNumber` validator is in a shared location (suggested: `core/validation/`)

**Frontend — No new dependencies needed.** All required libraries are already in `package.json`:
- `primevue` (4.x) — `Skeleton`, `InputText`, `Button`
- `pinia` — state management
- `@nuxtjs/i18n` — translations
- `zod` — validation (generated from OpenAPI)

### File Structure Requirements

**New files to CREATE:**

```
backend/
├── src/main/java/hu/riskguard/
│   ├── core/
│   │   ├── util/
│   │   │   └── HashUtil.java                   # SHA-256 hashing for audit trail (NEW — does not exist yet)
│   │   └── validation/
│   │       ├── HungarianTaxNumber.java         # @Constraint annotation
│   │       └── HungarianTaxNumberValidator.java # ConstraintValidator impl
│   └── screening/
│       ├── package-info.java
│       ├── api/
│       │   ├── ScreeningController.java
│       │   └── dto/
│       │       ├── PartnerSearchRequest.java
│       │       ├── VerdictResponse.java
│       │       └── CompanySnapshotResponse.java
│       ├── domain/
│       │   ├── ScreeningService.java
│       │   └── events/
│       │       ├── PartnerSearchCompleted.java
│       │       └── PartnerStatusChanged.java
│       └── internal/
│           └── ScreeningRepository.java
├── src/main/resources/db/migration/
│   └── V20260309_001__create_screening_tables.sql
└── src/test/java/hu/riskguard/
    ├── core/validation/
    │   └── HungarianTaxNumberValidatorTest.java
    └── screening/
        ├── api/
        │   └── ScreeningControllerTest.java
        └── ScreeningServiceIntegrationTest.java

frontend/
├── app/
│   ├── components/Screening/
│   │   ├── SearchBar.vue
│   │   ├── SearchBar.spec.ts
│   │   ├── SkeletonVerdictCard.vue
│   │   └── SkeletonVerdictCard.spec.ts
│   ├── i18n/
│   │   ├── hu/screening.json
│   │   └── en/screening.json
│   ├── stores/
│   │   └── screening.ts
│   └── pages/dashboard/
│       └── index.vue                            # UPDATE — integrate SearchBar + Skeleton
└── types/
    └── api.d.ts                                 # AUTO-GENERATED — regenerate from OpenAPI
```

**Files to MODIFY:**
- `backend/build.gradle` — Add screening tables to jOOQ codegen scope
- `frontend/app/pages/dashboard/index.vue` — Integrate SearchBar + SkeletonVerdictCard
- `frontend/types/api.d.ts` — Regenerated by OpenAPI pipeline

**DO NOT modify files outside the screening module and the shared `core/validation/` + `core/util/` unless absolutely necessary.**

### Testing Requirements

**Backend Tests (minimum coverage):**

| Test File | Type | What It Verifies |
|---|---|---|
| `HungarianTaxNumberValidatorTest.java` | Unit | 8-digit valid, 11-digit valid, too short, too long, non-numeric, null, empty string, letters mixed in |
| `ScreeningControllerTest.java` | Unit | `POST /api/v1/screenings/search` — valid request returns 200 + VerdictResponse; invalid tax number returns 400 + ProblemDetail; missing body returns 400 |
| `ScreeningServiceIntegrationTest.java` | Integration | Full search flow with real PostgreSQL: creates snapshot + verdict + audit log; verifies SHA-256 hash; verifies tenant isolation (user A cannot see user B's snapshots); verifies idempotency guard returns cached result within 15 min |
| `ModulithVerificationTest` (existing) | Architecture | Verify `screening` module boundaries are not violated |

**Frontend Tests (minimum coverage):**

| Test File | Type | What It Verifies |
|---|---|---|
| `SearchBar.spec.ts` | Component | Renders input with placeholder; validates 8-digit format; validates 11-digit format; shows error for invalid input; calls API on valid submit; shows loading state during fetch |
| `SkeletonVerdictCard.spec.ts` | Component | Renders skeleton layout with 3 source placeholders; each placeholder shows animated Skeleton bar; component renders/hides based on loading prop |

**Test Infrastructure:**
- Backend integration tests: `@Tag("integration")`, `@Testcontainers`, `@Container @ServiceConnection PostgreSQLContainer<?>` — follows the pattern established in Stories 1.3-1.4
- Frontend tests: Use existing `vitest.config.ts` and `vitest.setup.ts` from Story 1.4. **NOTE:** `renderWithProviders.ts` helper does NOT exist yet — create `frontend/app/test/helpers/renderWithProviders.ts` that wraps components with Pinia + i18n + PrimeVue for testing. Also create `frontend/app/test/helpers/mockFetch.ts` for standardized `$fetch` mocking.
- CI pipeline: `./gradlew test -DexcludedGroups=integration` (unit), `./gradlew test -Dgroups=integration` (integration), `npm run test` (frontend)

### Git Intelligence Summary

**Repository State:**
- Latest commit: `724bc69 chore(review): resolve Story 2.0 Round 2 review findings and mark done`
- Epic 1 (Identity & Infrastructure) fully complete — all 6 stories done with review findings resolved
- Story 2.0 (GCP Staging Bootstrap) complete — staging environment live and healthy
- All 42 backend unit + 5 integration + 12 frontend tests passing
- CI pipeline passing end-to-end

**Codebase patterns established by previous stories (FOLLOW THESE):**
- `identity` module is the only existing feature module — use it as secondary reference (but `screening` becomes the PRIMARY reference going forward)
- `IdentityController.java` → `IdentityService.java` → `IdentityRepository.java` pattern exists
- `core/security/TenantFilter.java`, `TenantContext.java`, `TenantAwareDSLContext.java` — tenant isolation infrastructure ready
- `core/security/TokenProvider.java` — JWT handling ready
- `core/util/CookieUtils.java` — established utility pattern
- `frontend/app/stores/auth.ts`, `identity.ts` — Pinia store patterns exist
- `frontend/app/composables/api/`, `auth/`, `formatting/` — composable organization established
- `frontend/vitest.config.ts`, `vitest.setup.ts` — test infrastructure ready

**Key learnings from previous stories to apply:**
1. **Story 1.5:** Gradle 9.x AOT classpath issue — avoid `forkedSpringBootRun` complications, keep AOT disabled
2. **Story 2.0:** Neon PostgreSQL for staging — ensure migrations are dialect-neutral (no Cloud SQL-specific syntax)
3. **Story 1.3-1.4:** `@Tag("integration")` + `@Testcontainers` pattern — use `@Container @ServiceConnection` for auto-wired datasource
4. **Story 2.0:** `npm ci` (not `npm install`) in CI — already fixed in deploy.yml
5. **Story 2.0:** `npm run generate` (not `npm run build`) for static hosting — already fixed

**Existing Flyway migrations (context for naming):**
```
V20260305_001__create_identity_tables.sql
V20260305_002__create_modulith_tables.sql
V20260306_001__add_guest_sessions_fk.sql
→ Next: V20260309_001__create_screening_tables.sql
```

### Latest Technical Information

**PrimeVue 4.5.x — Skeleton Component:**
```vue
<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center gap-3">
      <Skeleton shape="circle" size="3rem" />
      <div class="flex-1">
        <Skeleton width="100%" height="1rem" />
        <Skeleton width="75%" height="0.75rem" class="mt-2" />
      </div>
    </div>
  </div>
</template>
```
- Import: `import Skeleton from 'primevue/skeleton'`
- Props: `shape` (`"rectangle"` | `"circle"`), `size`, `width`, `height`, `borderRadius`
- Use `aria-hidden="true"` for accessibility
- For DataTable skeleton pattern: pass array of empty objects as `:value`, use `<template #body><Skeleton /></template>` per column

**Nuxt 4.x — `useFetch` for search API:**
```ts
const { data, status, error, refresh } = await useFetch<VerdictResponse>(
  '/api/v1/screenings/search',
  {
    method: 'POST',
    body: { taxNumber: taxNumberInput.value },
    lazy: true,
    immediate: false, // Don't fire on mount — only on explicit search
  }
)
// status.value === 'idle' | 'pending' | 'success' | 'error'
// Use: v-if="status === 'pending'" to show SkeletonVerdictCard
```

**Zod 4.x — Hungarian Tax Number Validation (frontend):**
```ts
import { z } from 'zod'
const hungarianTaxNumberSchema = z.string().regex(
  /^\d{8}(\d{3})?$/,
  'screening.search.invalidTaxNumber' // i18n key
)
```
- Zod 4 is a major rewrite but `z.string().regex()` API is stable
- `.safeParse()` for non-throwing validation: `const result = schema.safeParse(input)`

**Spring Boot 4.0.3 — Custom Validator Pattern:**
```java
@Documented
@Constraint(validatedBy = HungarianTaxNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HungarianTaxNumber {
    String message() default "Invalid Hungarian tax number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```
```java
public class HungarianTaxNumberValidator
    implements ConstraintValidator<HungarianTaxNumber, String> {
    private static final Pattern PATTERN = Pattern.compile("^\\d{8}(\\d{3})?$");
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return false;
        return PATTERN.matcher(value.replaceAll("[\\s-]", "")).matches();
    }
}
```

### Project Context Reference

**From `project-context.md` — rules directly relevant to Story 2.1:**

- **Communication Language:** English (for commit messages, code comments, PR descriptions). Hungarian only in i18n files.
- **Conventional Commits:** `feat: description`, `fix: description`, `chore: description` — use `feat(screening):` prefix for this story
- **Migration Naming:** `V{YYYYMMDD}_{NNN}__{description}.sql` — double underscore mandatory
- **Zero Hardcoded Strings:** ALL user-facing text via i18n keys. Backend exports/emails via `messages_hu.properties` / `messages_en.properties`. Frontend via `i18n/**/*.json`.
- **Zero Hardcoded Business Constants:** Use `risk-guard-tokens.json` for shared constants. If a constant (like the 15-min idempotency threshold) isn't there yet, add it or use a TODO comment.
- **No Direct Repository Access from Controllers:** Controller → Service facade → Repository. Never skip the facade.
- **Tenant Isolation:** Every tenant-scoped query MUST include `tenant_id` filter. `TenantFilter` sets it from JWT; repositories read it from `TenantContext`.
- **`@LogSafe` Types:** Never log domain objects directly. Use `@LogSafe` annotated types for structured logging.
- **ArchUnit Enforcement:** `ModulithVerificationTest` must pass — verifies no cross-module boundary violations.

### Story Completion Status

Status: review

### Project Structure Notes

**Existing modules (for context):**
- `core/` — shared infrastructure (security, config, util) — 16 files
- `identity/` — user management, SSO, tenant switching — established in Epic 1
- `jooq/` — generated jOOQ code (auto-generated, never edit manually)

**New module created by this story:**
- `screening/` — partner risk screening (search, snapshots, verdicts, audit trail) — ~12 new backend files, ~8 new frontend files

**The `screening` module is architecturally the most important module in the system.** It handles FR1 (search), FR2 (data retrieval — later stories), FR3 (state-machine verdicts — later stories), and FR4 (suspended tax detection — later stories). Getting its foundation right here is critical.

### References

- Architecture — module anatomy pattern: [Source: _bmad-output/planning-artifacts/architecture.md#Module Anatomy]
- Architecture — screening module files: [Source: _bmad-output/planning-artifacts/architecture.md#Requirements to Structure Mapping — FR1-FR4]
- Architecture — ER schema for screening tables: [Source: _bmad-output/planning-artifacts/architecture.md#screening Module Tables]
- Architecture — key indexes: [Source: _bmad-output/planning-artifacts/architecture.md#Key Indexes]
- Architecture — naming conventions: [Source: _bmad-output/planning-artifacts/architecture.md#Implementation Patterns]
- Architecture — data flow: [Source: _bmad-output/planning-artifacts/architecture.md#Data Flow]
- Architecture — cross-cutting concerns: [Source: _bmad-output/planning-artifacts/architecture.md#Cross-Cutting Concerns Mapping]
- PRD — FR1-FR4 (Partner Screening): [Source: _bmad-output/planning-artifacts/prd.md#Functional Requirements]
- PRD — NFR1 (< 30s verdict latency): [Source: _bmad-output/planning-artifacts/prd.md#Non-Functional Requirements]
- PRD — User Journey 1 (Midnight Risk Check): [Source: _bmad-output/planning-artifacts/prd.md#Journey 1]
- UX — SearchBar auto-masking: [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Phase 1]
- UX — Skeletal Trust loading pattern: [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Loading & Empty States]
- UX — Color system (Shield states): [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Color System]
- Epics — Story 2.1 definition: [Source: _bmad-output/planning-artifacts/epics.md#Story 2.1]
- Previous Story 2.0 — staging deployment context: [Source: _bmad-output/implementation-artifacts/2-0-bootstrap-gcp-staging-and-deploy-epic-1-baseline.md]
- Previous Story 1.5 — CI/CD + Gradle learnings: [Source: _bmad-output/implementation-artifacts/1-5-automated-ci-cd-and-gcp-infrastructure.md]
- Project context rules: [Source: _bmad-output/project-context.md]

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- Fixed `ScreeningRepository` visibility: Made class and methods `public` for within-module access (Spring Modulith enforces external boundaries, not internal Java visibility)
- Fixed integration test unique constraint violation: Used `UUID.randomUUID()` suffix for test user emails to avoid duplicate key errors across shared Spring context

### Completion Notes List

- ✅ Task 1: Flyway migration `V20260309_001__create_screening_tables.sql` creates 3 tables + 2 ENUM types + 7 indexes. Runs cleanly on PostgreSQL 17.
- ✅ Task 2: Full screening module scaffold following identity module pattern: `api/` → `domain/` → `internal/` with `package-info.java` for Modulith declaration.
- ✅ Task 3: DTOs as records with `from()` factories. `@HungarianTaxNumber` custom validator in `core/validation/` accepts 8-digit and 11-digit formats.
- ✅ Task 4: `POST /api/v1/screenings/search` endpoint with full flow: normalize → idempotency guard (15 min) → create snapshot → create verdict (INCOMPLETE) → write audit log with SHA-256 → publish event → return response.
- ✅ Task 5: `PartnerSearchCompleted` and `PartnerStatusChanged` event records. Event catalog in `core/events/package-info.java`.
- ✅ Task 6: TypeScript types created in `frontend/types/api.d.ts` matching backend DTOs. OpenAPI pipeline will regenerate when backend is live.
- ✅ Task 7: `SearchBar.vue` with auto-formatting mask (####-#### / ####-####-###), regex validation, localized errors, PrimeVue InputText + Button.
- ✅ Task 8: `SkeletonVerdictCard.vue` with 3 labeled source placeholders (NAV Debt, Legal Status, Company Registry) each with PrimeVue Skeleton bars.
- ✅ Task 9: Dashboard page integrates SearchBar + SkeletonVerdictCard. Pinia `screening` store manages search state, verdict cache, API calls.
- ✅ Task 10: i18n keys in `hu/screening.json` and `en/screening.json` with exact key parity. `nuxt.config.ts` updated to include screening locale files.
- ✅ Task 11: 16 backend tests (8 unit validator + 4 unit controller + 4 integration). All pass including ModulithVerification. 24 frontend tests (18 SearchBar + 6 SkeletonVerdictCard).
- 📊 Total test count: ~56 backend unit tests + ~9 integration tests + 36 frontend tests = ~101 total tests passing.

### Change Log

- **2026-03-09:** Story 2.1 implementation complete. Created screening module as reference implementation with full backend (migration, module scaffold, DTOs, validation, controller, service, repository, events, tests) and frontend (SearchBar, SkeletonVerdictCard, dashboard page, Pinia store, i18n, TypeScript types, tests).

### File List

**New Backend Files:**
- `backend/src/main/resources/db/migration/V20260309_001__create_screening_tables.sql`
- `backend/src/main/java/hu/riskguard/core/util/HashUtil.java`
- `backend/src/main/java/hu/riskguard/core/validation/HungarianTaxNumber.java`
- `backend/src/main/java/hu/riskguard/core/validation/HungarianTaxNumberValidator.java`
- `backend/src/main/java/hu/riskguard/core/events/package-info.java`
- `backend/src/main/java/hu/riskguard/screening/package-info.java`
- `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/PartnerSearchRequest.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/VerdictResponse.java`
- `backend/src/main/java/hu/riskguard/screening/api/dto/CompanySnapshotResponse.java`
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java`
- `backend/src/main/java/hu/riskguard/screening/domain/events/PartnerSearchCompleted.java`
- `backend/src/main/java/hu/riskguard/screening/domain/events/PartnerStatusChanged.java`
- `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java`
- `backend/src/test/java/hu/riskguard/core/validation/HungarianTaxNumberValidatorTest.java`
- `backend/src/test/java/hu/riskguard/screening/api/ScreeningControllerTest.java`
- `backend/src/test/java/hu/riskguard/screening/ScreeningServiceIntegrationTest.java`

**New Frontend Files:**
- `frontend/app/components/Screening/SearchBar.vue`
- `frontend/app/components/Screening/SearchBar.spec.ts`
- `frontend/app/components/Screening/SkeletonVerdictCard.vue`
- `frontend/app/components/Screening/SkeletonVerdictCard.spec.ts`
- `frontend/app/stores/screening.ts`
- `frontend/app/pages/dashboard/index.vue`
- `frontend/app/i18n/hu/screening.json`
- `frontend/app/i18n/en/screening.json`
- `frontend/types/api.d.ts`

**Modified Files:**
- `frontend/nuxt.config.ts` — Added screening i18n locale files
