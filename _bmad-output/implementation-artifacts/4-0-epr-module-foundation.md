# Story 4.0: EPR Module Foundation

Status: done

## Story

As a Developer,
I want the `epr` Spring Modulith module scaffolded with package structure, ArchUnit rules, Flyway migration, EPR fee table seed data, i18n namespace files, and an integration test baseline,
So that Stories 4.1-4.4 can build EPR business logic on a properly configured, tested foundation without any scaffolding delays.

## Acceptance Criteria

### AC 1: EPR Module Package Structure (Retro T1)

**Given** the existing Spring Modulith project with modules `screening`, `datasource`, `notification`, `identity`
**When** the `epr` module scaffold is created
**Then** the package `hu.riskguard.epr` exists with sub-packages `api/`, `api/dto/`, `domain/`, `domain/events/`, `internal/`
**And** a `package-info.java` exists in `hu.riskguard.epr` documenting the module's purpose
**And** `ModulithVerificationTest.verifyModulith()` passes with `epr` detected as a module
**And** the module follows the exact 3-layer structure of the `screening` reference implementation

### AC 2: EPR Module Facade and Minimal API Surface

**Given** the new `epr` module
**When** compiled
**Then** `EprService.java` exists as the module facade (`@Service`) — initially empty with a placeholder method
**And** `EprController.java` exists as a `@RestController` with `@RequestMapping("/api/v1/epr")` — initially empty
**And** `EprRepository.java` exists in `internal/` as the jOOQ repository — initially empty

### AC 3: EPR Flyway Migration — Create Tables (Retro T1)

**Given** a running PostgreSQL 17 database with existing migrations (through V20260320_002)
**When** Flyway executes the new migration `V20260323_001__create_epr_tables.sql`
**Then** tables `epr_material_templates`, `epr_configs`, `epr_calculations`, `epr_exports` are created
**And** all tables have `tenant_id NOT NULL` (FK to `tenants.id`) for tenant isolation
**And** `epr_configs` has columns: `id` (UUID PK), `version` (INT), `config_data` (JSONB), `schema_version` (VARCHAR), `schema_verified` (BOOLEAN), `created_at`, `activated_at`
**And** `epr_material_templates` has columns: `id` (UUID PK), `tenant_id`, `name` (VARCHAR), `base_weight_grams` (DECIMAL), `kf_code` (VARCHAR, nullable), `verified` (BOOLEAN DEFAULT false), `seasonal` (BOOLEAN DEFAULT false), `created_at`, `updated_at`
**And** `epr_calculations` has columns: `id` (UUID PK), `tenant_id`, `config_version` (INT), `template_id` (FK to epr_material_templates, nullable), `traversal_path` (JSONB), `material_classification` (VARCHAR), `kf_code` (VARCHAR), `fee_rate` (DECIMAL), `quantity` (DECIMAL, nullable), `total_weight_grams` (DECIMAL, nullable), `fee_amount` (DECIMAL, nullable), `currency` (VARCHAR DEFAULT 'HUF'), `created_at`
**And** `epr_exports` has columns per architecture: `id`, `tenant_id`, `calculation_id`, `config_version`, `export_format`, `file_hash`, `exported_at`
**And** table and column names follow `lower_snake_case` convention
**And** appropriate indexes are created: `idx_epr_templates_tenant` on `(tenant_id)`, `idx_epr_calculations_tenant` on `(tenant_id)`, `idx_epr_configs_version` on `(version)`

### AC 4: EPR Fee Table Seed Data (Retro T2)

**Given** the new `epr_configs` table
**When** Flyway executes seed migration `V20260323_002__seed_epr_fee_tables.sql`
**Then** an initial EPR config record is inserted with `version=1`, `schema_verified=true`
**And** `config_data` contains a JSONB structure with three sections: `kf_code_structure`, `fee_rates_2026`, and `fee_modulation`
**And** `kf_code_structure` encodes the 8-digit KF code hierarchy from 80/2023 Korm. rendelet 1. melléklet:
  - `product_streams` (termékáram, positions 1-2): 11=Nem visszaváltandó egyszer használatos csomagolás, 12=Kötelezően visszaváltandó, 13=Újrahasználható, 21-26=EEE, 31-33=Elem/akku, 41=Gumiabroncs, 51=Gépjármű, 61=Irodai papír, 71=Reklámhordozó papír, 81=Egyszer használatos műanyag, 91=Egyéb műanyag/vegyipari
  - `material_streams` (anyagáram, positions 3-4): 01=Papír/karton, 02=Műanyag, 03=Fa, 04=Fém(vas/acél/alu), 05=Üveg, 06=Textil/egyéb természetes, 07=Társított
  - `groups` (csoport, positions 5-6): 01=Fogyasztói, 02=Gyűjtő, 03=Szállítási (for non-deposit packaging)
  - `subgroups` (alcsoport, positions 7-8): 01=default
**And** `fee_rates_2026` encodes the exact 2026 díjtételek from 33/2025 (XI. 28.) EM rendelet 1. melléklet:
  - Csomagolás díjkódok with verified HUF/kg rates: 1101=20.44 (papír/karton), 1102=42.89 (műanyag), 1103=10.22 (fa), 1104=17.37 (vas/acél), 1105=17.37 (alumínium), 1106=10.22 (üveg), 1107=10.22 (textil/természetes), 1108=20.44 (társított:papír), 1109=42.89 (társított:műanyag), 1110=17.37 (társított:fém), 1111=10.22 (társított:egyéb)
  - Same díjtételek apply for 12xx (kötelezően visszaváltandó) and 13xx (újrahasználható) product streams
  - All other product stream fee rates (EEE, batteries, tires, vehicles, paper, single-use plastic, other)
**And** `fee_modulation` encodes the recycled content discount tiers from 33/2025 EM rendelet 2. melléklet:
  - 0-10%: 0%, 10.01-20%: -3%, 20.01-30%: -5%, 30.01-50%: -8%, 50.01-80%: -15%, 80.01-100%: -20%
**And** the config includes metadata: `legislation_ref`, `valid_from` (2026-01-01), `valid_until` (2026-08-11, per njt.hu time-state), `currency` (HUF)

### AC 5: ArchUnit Rules for EPR Module (Retro T1)

**Given** the existing `NamingConventionTest.java` ArchUnit test suite
**When** the EPR module rules are added
**Then** a rule `epr_module_should_only_access_own_tables` enforces that `epr` module code only accesses jOOQ table references for: `EprConfigs`, `EprCalculations`, `EprExports`, `EprMaterialTemplates`
**And** a rule `epr_internal_should_not_be_accessed_externally` enforces no class outside `..epr..` depends on `..epr.internal..`
**And** all existing ArchUnit tests continue to pass

### AC 6: Integration Test Baseline (Retro T3)

**Given** the new `epr` module scaffold
**When** `./gradlew check` runs
**Then** a `@SpringBootTest` integration test (`EprModuleIntegrationTest.java`) verifies the Spring context loads with the `epr` module
**And** the test verifies Flyway migrations apply successfully (including EPR tables and seed data)
**And** the test verifies jOOQ codegen includes the new EPR tables
**And** all existing tests continue to pass (zero regressions)

### AC 7: i18n Namespace Files (Retro E2)

**Given** the existing i18n infrastructure with namespace-per-file pattern
**When** the EPR i18n files are created
**Then** `frontend/app/i18n/hu/epr.json` exists with initial keys: `epr.title`, `epr.materialLibrary.title`, `epr.wizard.title`, `epr.export.title`, `epr.config.title` (and their Hungarian translations)
**And** `frontend/app/i18n/en/epr.json` exists with matching English translations
**And** key parity is maintained (every key in `hu/epr.json` exists in `en/epr.json` and vice versa)
**And** keys are sorted alphabetically

## Tasks / Subtasks

- [x] Task 1: Create EPR module package structure (AC: #1, #2)
  - [x] 1.1 Create package `hu.riskguard.epr` with `package-info.java` documenting module purpose
  - [x] 1.2 Create `api/` sub-package with `EprController.java` (`@RestController`, `@RequestMapping("/api/v1/epr")`, empty)
  - [x] 1.3 Create `api/dto/` sub-package (empty — ready for Story 4.1 DTOs)
  - [x] 1.4 Create `domain/` sub-package with `EprService.java` (`@Service` facade, placeholder health-check method)
  - [x] 1.5 Create `domain/events/` sub-package (empty — ready for `EprExportGenerated` event in Story 4.4)
  - [x] 1.6 Create `internal/` sub-package with `EprRepository.java` (jOOQ repository, empty)
  - [x] 1.7 Verify `ModulithVerificationTest.verifyModulith()` detects `epr` as a module

- [x] Task 2: Create Flyway migration for EPR tables (AC: #3)
  - [x] 2.1 Create `V20260323_001__create_epr_tables.sql` with 4 tables: `epr_material_templates`, `epr_configs`, `epr_calculations`, `epr_exports`
  - [x] 2.2 All tables include `tenant_id UUID NOT NULL REFERENCES tenants(id)` (except `epr_configs` which is global)
  - [x] 2.3 Add indexes: `idx_epr_templates_tenant`, `idx_epr_calculations_tenant`, `idx_epr_configs_version`
  - [x] 2.4 Verify migration applies cleanly: `./gradlew flywayMigrate`

- [x] Task 3: Seed EPR fee table data (AC: #4)
  - [x] 3.1 Create `V20260323_002__seed_epr_fee_tables.sql` inserting initial `epr_configs` record (version=1, schema_verified=true)
  - [x] 3.2 Structure `config_data` JSONB with 3 sections: `kf_code_structure` (from 80/2023 Korm. rendelet 1. melléklet), `fee_rates_2026` (from 33/2025 EM rendelet 1. melléklet), `fee_modulation` (from 33/2025 EM rendelet 2. melléklet)
  - [x] 3.3 Encode full KF code hierarchy: product_streams (2 digit) → material_streams (2 digit) → groups (2 digit) → subgroups (2 digit)
  - [x] 3.4 Encode exact 2026 csomagolás díjtételek: 1101=20.44, 1102=42.89, 1103=10.22, 1104=17.37, 1105=17.37, 1106=10.22, 1107=10.22, 1108=20.44, 1109=42.89, 1110=17.37, 1111=10.22 (Ft/kg)
  - [x] 3.5 Encode all other product stream fee rates (EEE, batteries, tires, vehicles, paper, plastics)
  - [x] 3.6 Encode fee modulation tiers: 0-10%→0%, 10-20%→-3%, 20-30%→-5%, 30-50%→-8%, 50-80%→-15%, 80-100%→-20%
  - [x] 3.7 Add metadata: legislation_ref, valid_from=2026-01-01, valid_until=2026-08-11, currency=HUF

- [x] Task 4: Add ArchUnit rules for EPR module (AC: #5)
  - [x] 4.1 Add `epr_module_should_only_access_own_tables` rule to `NamingConventionTest.java` — whitelist: `EprConfigs`, `EprCalculations`, `EprExports`, `EprMaterialTemplates`
  - [x] 4.2 Add `epr_internal_should_not_be_accessed_externally` rule to `NamingConventionTest.java`
  - [x] 4.3 Verify all existing ArchUnit tests still pass

- [x] Task 5: Create integration test baseline (AC: #6)
  - [x] 5.1 Create `EprModuleIntegrationTest.java` in `src/test/java/hu/riskguard/epr/`
  - [x] 5.2 `@SpringBootTest` test verifies Spring context loads with `epr` module
  - [x] 5.3 Test verifies EPR tables exist after Flyway migration (query `information_schema.tables`)
  - [x] 5.4 Test verifies seed data: `epr_configs` has version=1 record
  - [x] 5.5 Run full `./gradlew check` — zero regressions

- [x] Task 6: Create i18n namespace files (AC: #7)
  - [x] 6.1 Create `frontend/app/i18n/hu/epr.json` with initial EPR keys in Hungarian
  - [x] 6.2 Create `frontend/app/i18n/en/epr.json` with matching English keys
  - [x] 6.3 Verify key parity and alphabetical sorting
  - [x] 6.4 Register `epr` namespace in i18n config if needed

- [x] Task 7: Verification gate (process improvement P1)
  - [x] 7.1 Run `./gradlew check` locally — all tests green
  - [x] 7.2 Verify `ModulithVerificationTest` passes with `epr` module detected
  - [x] 7.3 Verify no regressions in existing 500+ frontend tests

### Review Follow-ups (AI) — R1

- [x] [AI-Review][HIGH] ArchUnit EPR table rule missing jOOQ record type handling — add `extractRecordName()` helper (matching identity pattern) so `hu.riskguard.jooq.tables.records.EprConfigsRecord` etc. are allowed. Without this fix, Stories 4.1-4.4 will fail ArchUnit on any INSERT/UPDATE using record types. [NamingConventionTest.java:182-210]
- [x] [AI-Review][HIGH] `epr_configs.version` has no UNIQUE constraint — add `ALTER TABLE epr_configs ADD CONSTRAINT uq_epr_configs_version UNIQUE(version)` in a new migration (or change existing index to `CREATE UNIQUE INDEX`). Prevents duplicate version records that would break fee lookups. [V20260323_001__create_epr_tables.sql:8]
- [x] [AI-Review][HIGH] `epr_calculations.kf_code` and `fee_rate` are NOT NULL but should be nullable — the DAG wizard (Story 4.2) needs partial-save support. AC spec does not mandate NOT NULL for these columns. Create a new migration to `ALTER COLUMN kf_code DROP NOT NULL; ALTER COLUMN fee_rate DROP NOT NULL`. [V20260323_001__create_epr_tables.sql:37-38]
- [x] [AI-Review][MEDIUM] `EprRepository` does not extend `BaseRepository` — refactor to `extends BaseRepository` with `super(dsl)` constructor, matching the screening reference implementation. Provides `selectFromTenant()` and `tenantCondition()` helpers needed for tenant-scoped queries. [EprRepository.java:14]
- [x] [AI-Review][MEDIUM] Integration test uses string-based `DSL.table()`/`DSL.field()` instead of jOOQ generated classes — import `hu.riskguard.jooq.tables.EprConfigs` etc. to actually verify codegen succeeded per AC 6. [EprModuleIntegrationTest.java:55-101]
- [x] [AI-Review][MEDIUM] AC 3 text says "all tables have tenant_id" but `epr_configs` is correctly global — amend AC 3 text to state the exception explicitly (e.g., "all tenant-scoped tables have tenant_id; epr_configs is global"). [Story AC 3] — NOTE: AC text not modified (outside permitted edit sections); implementation is correct; clarification added to Completion Notes.
- [x] [AI-Review][LOW] Legislation PDFs and `risk_epr.md` in repo root — add `*.pdf` to `.gitignore` and move/delete research files before committing. [.gitignore, repo root]
- [x] [AI-Review][LOW] Redundant `::jsonb` cast in seed migration — cannot remove from applied Flyway migration (checksum integrity). Cosmetic finding accepted as-is; noted for Story 6.3 when migration is superseded. [V20260323_002__seed_epr_fee_tables.sql:14]

### Review Follow-ups (AI) — R2

- [x] [AI-Review][HIGH] `epr_exports.export_format` uses `VARCHAR(50)` instead of a PostgreSQL ENUM — architecture spec explicitly states `ENUM: CSV/XLSX`. Project pattern (verdict_status, verdict_confidence) uses `CREATE TYPE ... AS ENUM(...)`. VARCHAR allows invalid values silently. Fix: `CREATE TYPE export_format_type AS ENUM ('CSV', 'XLSX')` and `ALTER TABLE epr_exports ALTER COLUMN export_format TYPE export_format_type USING export_format::export_format_type`. [V20260323_001__create_epr_tables.sql:52, V20260323_004__epr_review_r2_fixes.sql]
- [x] [AI-Review][MEDIUM] `epr_calculations.template_id` and `epr_exports.calculation_id` FK constraints have no `ON DELETE` clause — PostgreSQL defaults to RESTRICT, which will silently block template deletion and calculation deletion in Stories 4.1/4.4. Fix: `ON DELETE SET NULL` for both (calculations survive template deletion as "unclassified"; exports survive calculation deletion for audit trail). [V20260323_001__create_epr_tables.sql:34,50, V20260323_004__epr_review_r2_fixes.sql]
- [x] [AI-Review][MEDIUM] `config_version` in `epr_calculations` and `epr_exports` is a bare INT with no FK to `epr_configs(version)` — dangling references possible, no referential integrity. epr_configs.version has UNIQUE INDEX (from V20260323_003) so FK is now feasible. Fix: Add named FK constraints with `ON DELETE RESTRICT`. [V20260323_001__create_epr_tables.sql:33,51, V20260323_004__epr_review_r2_fixes.sql]
- [x] [AI-Review][MEDIUM] `EprController` declares `private final EprService eprService` via `@RequiredArgsConstructor` but has zero methods — field is completely unused (IDE warning, unnecessary eager initialization). A clean scaffold controller should declare no injections until it has methods that use them. Fix: Remove field and `@RequiredArgsConstructor`; add javadoc reminder to inject EprService when first endpoint is added. [EprController.java:17-21]
- [x] [AI-Review][LOW] `epr_material_templates.updated_at` has no auto-update mechanism (no DB trigger, no application-level enforcement) — Story 4.1 repository methods must explicitly set `updated_at` on every UPDATE or it will stay at the creation timestamp. Fix: Document this requirement in `EprRepository` javadoc. [EprRepository.java, V20260323_001__create_epr_tables.sql:26]
- [x] [AI-Review][LOW] `EprModuleIntegrationTest.eprTablesExistAfterMigration()` uses `assertEquals(4, tables.size())` — fragile assertion that will break when Stories 4.1-4.4 add more EPR tables. Fix: Change to `assertTrue(tables.size() >= 4)`. [EprModuleIntegrationTest.java:75]

## Dev Notes

### Origin and Purpose

This story was created from **Epic 3 retrospective action items** (2026-03-23):
- **T1:** Scaffold `epr` Spring Modulith module — package structure, ArchUnit rules, empty migration folder
- **T2:** Create EPR JSON fee tables from legislation — seed data for Material Library
- **T3:** Establish integration test baseline count in CI — regression = pipeline failure
- **E2:** Create `epr.json` i18n namespace files — empty files with initial keys

This is a **scaffolding-only story**. No business logic, no API endpoints with real behavior, no frontend components. The goal is to give Stories 4.1-4.4 a clean, tested foundation to build on.

### Critical Architecture Patterns to Follow

**Reference implementation:** Follow `hu.riskguard.screening` exactly. The screening module's 3-layer structure (`api/` → `domain/` → `internal/`) is the canonical pattern. See [Source: architecture.md#Implementation-Patterns--Consistency-Rules].

**Module facade pattern:** `EprService.java` is the ONLY public entry point. All cross-module calls go through this facade. No direct imports of `epr.internal` from any other module. [Source: architecture.md#Communication-Patterns]

**Table ownership:** The `epr` module owns: `epr_configs`, `epr_calculations`, `epr_exports`, `epr_material_templates`. No other module may access these tables. Enforced by ArchUnit. NOTE: The architecture doc currently lists only 3 EPR tables (`epr_configs`, `epr_calculations`, `epr_exports`). The `epr_material_templates` table is a **NEW addition from this story** (Epic 3 retro T2), not yet reflected in the architecture doc. The story's definitions take precedence.

**Schema expansion note:** The `epr_calculations` table in this story has additional columns (`template_id`, `kf_code`, `fee_rate`, `quantity`, `total_weight_grams`) beyond what the architecture doc's initial ER summary listed. These were added during Epic 3 retro planning to support Stories 4.1-4.4 requirements (template linking, fee rate capture, weight tracking). **This story's column definitions take precedence over the architecture doc.**

**jOOQ codegen:** The project uses a SINGLE jOOQ codegen configuration generating ALL tables into `hu.riskguard.jooq`. Table isolation is enforced by ArchUnit rules, NOT by separate codegen configs. After adding EPR tables via Flyway, run `./gradlew generateJooq` to regenerate — the new table classes will appear automatically. [Source: build.gradle jOOQ config]

### EPR Fee Table Data — Verified from Legislation

**CRITICAL: Use the verified seed data file as-is — do NOT invent or approximate fee data.**

The complete, legislation-verified seed data is at: `_bmad-output/implementation-artifacts/epr-seed-data-2026.json`

This file contains the EXACT data from two authoritative sources:
1. **80/2023 (III. 14.) Korm. rendelet — 1. melléklet**: Full KF code hierarchy (8-digit structure: termékáram → anyagáram → csoport → alcsoport)
2. **33/2025 (XI. 28.) EM rendelet — 1-3. melléklet**: Exact 2026 fee rates (HUF/kg by díjkód), fee modulation (recycled content discounts), and vehicle lump sums

**Key terminology (correct legal terms):**
- **KF kód** = Körforgásos kód (8-digit circular code) — NOT "KT kód"
- **KGyfR díj** = Kiterjesztett Gyártói Felelősségi díj (EPR fee)
- **Díjkód** = Fee code (4-digit: termékáram + díjkategória) — maps to fee rate
- **MOHU** = Koncessziós társaság (collects fees) — NOT NAV (that's the old termékdíj system)
- **OHH** = Országos Hulladékgazdálkodási Hatóság (registration authority)

**KF code structure (8 digits):**
```
[XX]   [XX]   [XX]   [XX]
 │      │      │      └── Alcsoport (subgroup)
 │      │      └───────── Csoport (group: fogyasztói/gyűjtő/szállítási)
 │      └──────────────── Anyagáram (material: papír/műanyag/fa/fém/üveg/textil/társított)
 └─────────────────────── Termékáram (product stream: 11=packaging, 21-26=EEE, etc.)
```

**Example: A plastic consumer packaging → KF kód: 11020101**
- 11 = Nem visszaváltandó egyszer használatos csomagolás
- 02 = Műanyag
- 01 = Fogyasztói csomagolás
- 01 = Alapértelmezett alcsoport
- Díjkód: 1102 → **42.89 Ft/kg**

**How to use the seed data file:**
The `V20260323_002__seed_epr_fee_tables.sql` migration should INSERT the entire JSON content of `epr-seed-data-2026.json` as the `config_data` JSONB column value in the `epr_configs` table (version=1, schema_verified=true).

**IMPORTANT — Temporal validity:** The data is valid from 2026-01-01 to 2026-08-11. After 2026-08-12, a new time-state of 80/2023 Korm. rendelet takes effect (visible on njt.hu). Story 6.3 (Hot-Swappable EPR JSON Manager) will handle updates.

### Flyway Migration Conventions

- Filename format: `V{YYYYMMDD}_{NNN}__{description}.sql` (two underscores before description)
- Latest existing migration: `V20260320_002__create_refresh_tokens.sql`
- New migrations for this story: `V20260323_001__create_epr_tables.sql`, `V20260323_002__seed_epr_fee_tables.sql`
- All table names: `lower_snake_case`, plural
- All column names: `lower_snake_case`
- Primary keys: always `id` (UUID)
- Foreign keys: `{target_table_singular}_id`
- Every tenant-scoped table: `tenant_id UUID NOT NULL REFERENCES tenants(id)`
- **Exception:** `epr_configs` is a GLOBAL table (not tenant-scoped) — it stores the legislation-driven fee schedule that applies to all tenants equally

### ArchUnit Rules — Exact Pattern

**CRITICAL: Do NOT use pseudocode — copy the exact patterns from the existing rules.**

**Table-isolation rule:** Use the **`identity_module_should_only_access_own_tables`** pattern (NOT the simpler screening pattern). The identity pattern uses **prefix-matching** which correctly handles jOOQ nested classes (`EprConfigs.EprConfigsRecord`, etc.):
- Define `ALLOWED_TABLE_PREFIXES` set with fully-qualified class names: `hu.riskguard.jooq.tables.EprConfigs`, `hu.riskguard.jooq.tables.EprCalculations`, `hu.riskguard.jooq.tables.EprExports`, `hu.riskguard.jooq.tables.EprMaterialTemplates`
- Use inline `ArchCondition<JavaClass>` with `anyMatch(prefix -> targetName.equals(prefix) || targetName.startsWith(prefix + "."))` pattern
- The simpler `Set.contains()` from the screening rule will cause false positives on jOOQ record types

**Internal-protection rule:** CRITICAL — must include **three exclusions** from the existing `screening_internal_should_not_be_accessed_externally` rule:
1. `.and().resideOutsideOfPackage("..architecture..")` — excludes ArchUnit test classes themselves
2. `.and().haveSimpleNameNotContaining("BeanDefinitions")` — excludes Spring AOT generated classes
3. `.and().haveSimpleNameNotContaining("BeanFactoryRegistrations")` — excludes Spring AOT generated classes

Without these exclusions, the rule will fail against Spring Boot's AOT framework and the ArchUnit tests.

**SCOPE: Only add EPR rules. Do not modify or add rules for other modules.**

### Integration Test Pattern

Follow the existing `@SpringBootTest` integration test patterns. The test should:
1. Use `@SpringBootTest` with the test profile (`@ActiveProfiles("test")`)
2. Inject `DSLContext` to verify jOOQ can access EPR tables
3. Verify the seed data exists: `SELECT count(*) FROM epr_configs WHERE version = 1`
4. The test uses Testcontainers (PostgreSQL 17) — NO H2. See `application-test.yml` for config.

### i18n File Pattern

Follow existing namespace files (e.g., `hu/screening.json`). **CRITICAL: Use nested JSON objects, NOT flat dot-notation keys.** Example structure:

```json
{
  "epr": {
    "config": {
      "title": "EPR Konfiguráció",
      "version": "Verzió"
    },
    "export": {
      "title": "MOHU Export"
    },
    "materialLibrary": {
      "empty": "Még nincs anyagsablon",
      "title": "Anyagkönyvtár"
    },
    "title": "EPR Kezelő",
    "wizard": {
      "title": "Anyag besorolás"
    }
  }
}
```

Rules:
- **Nested JSON** objects matching `screening.json` pattern (NOT flat `epr.title` strings)
- Alphabetically sorted at every nesting level
- Key parity: every key in `hu/epr.json` must exist in `en/epr.json`
- English translations: EPR Manager, EPR Configuration, Version, MOHU Export, No material templates yet, Material Library, Material Classification

### Process Improvements (from Epic 3 Retro)

**P1 — Story completion gate:** Run `./gradlew check` locally before marking done. Not just unit tests.
**P2 — Verify test state:** Do NOT copy disclaimers from previous stories. Run the actual checks.
**P3 — Escalate recurring failures:** If something fails twice, flag it as a blocker.
**P4 — Pre-review checklist:** Before first review round, verify: module boundary compliance, test file existence for every new source file, fail-closed defaults.

### Testing Standards Summary

- Backend: JUnit 5, `@SpringBootTest` with Testcontainers PostgreSQL 17, MockMvc for controllers
- Real-DB mandate: NO H2 — always Testcontainers
- Every new source file must have a corresponding test file
- Run `./gradlew check` (not just `test`) — this includes ArchUnit + Modulith verification
- Frontend: Vitest with co-located `*.spec.ts` files (not applicable for this story — no frontend components)

### Project Structure Notes

**New files to create (backend):**

```
backend/src/main/java/hu/riskguard/epr/
├── package-info.java
├── api/
│   ├── EprController.java
│   └── dto/                          (empty directory — ready for Story 4.1)
├── domain/
│   ├── EprService.java
│   └── events/                       (empty directory — ready for EprExportGenerated)
└── internal/
    └── EprRepository.java
```

**New files to create (migrations):**

```
backend/src/main/resources/db/migration/
├── V20260323_001__create_epr_tables.sql
└── V20260323_002__seed_epr_fee_tables.sql
```

**New files to create (tests):**

```
backend/src/test/java/hu/riskguard/epr/
└── EprModuleIntegrationTest.java
```

**Files to modify:**

```
backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java
  → Add 2 new ArchUnit rules for epr module
```

**New files to create (frontend i18n):**

```
frontend/app/i18n/hu/epr.json
frontend/app/i18n/en/epr.json
```

**Directory to create (placeholder for Story 4.1):**

```
frontend/app/components/Epr/          (create with .gitkeep — git does not track empty directories)
```

**Alignment with architecture:** All paths match the project structure defined in [Source: architecture.md#Complete-Project-Directory-Structure]. No variances detected.

**IMPORTANT — No other files should be modified.** This is a scaffolding story. Do not touch:
- `build.gradle` — jOOQ codegen is already configured to generate ALL tables in `public` schema
- `application.yml` — no new configuration properties needed for scaffolding
- `SecurityConfig.java` — no new security rules needed
- Any existing module code — zero cross-module changes

### References

- [Source: _bmad-output/implementation-artifacts/epic-3-retro-2026-03-23.md#Action-Items] — T1, T2, T3, E2 action items defining this story's scope
- [Source: _bmad-output/planning-artifacts/architecture.md#Code-Organization] — Module package structure (`hu.riskguard.epr`)
- [Source: _bmad-output/planning-artifacts/architecture.md#Implementation-Patterns--Consistency-Rules] — Reference implementation pattern (screening module)
- [Source: _bmad-output/planning-artifacts/architecture.md#Table-Ownership-Per-Module] — EPR owns: `epr_configs`, `epr_calculations`, `epr_exports`
- [Source: _bmad-output/planning-artifacts/architecture.md#Entity-Relationship-Summary] — EPR table schemas with columns and relationships
- [Source: _bmad-output/planning-artifacts/architecture.md#Automated-Fail-Safes--CI-Pipeline] — ArchUnit test organization
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-4] — Epic 4 story list and FRs covered (FR8, FR9, FR13)
- [Source: _bmad-output/planning-artifacts/architecture.md#epr-Module-Failure-Mode-Analysis] — EPR failure modes and safeguards
- [Source: _bmad-output/planning-artifacts/architecture.md#i18n--l10n-Patterns] — Namespace-per-file, key parity, alphabetical sorting
- [Source: _bmad-output/implementation-artifacts/epr-seed-data-2026.json] — Verified 2026 EPR seed data (KF codes, fee rates, modulation) — USE AS-IS
- [Legislation: 80/2023. (III. 14.) Korm. rendelet — 1. melléklet] — KF kód struktúra (hatályos: 2026.01.01-2026.08.11)
- [Legislation: 80/2023. (III. 14.) Korm. rendelet — 2. melléklet] — Díjkód struktúra
- [Legislation: 33/2025. (XI. 28.) EM rendelet — 1. melléklet] — 2026. évi KGyfR díjtételek (Ft/kg)
- [Legislation: 33/2025. (XI. 28.) EM rendelet — 2. melléklet] — Díjmoduláció (újrafeldolgozott tartalom kedvezmény)
- [Legislation: 33/2025. (XI. 28.) EM rendelet — 3. melléklet] — Gépjármű díjátalány súlykategóriánként
- [Source: risk_epr.md#EPR-towards-MOHU] — Korábbi EPR kutatás: KKV workflow, fájdalompontok, MOHU vs NAV rendszer
- [Source: _bmad-output/project-context.md] — AI agent implementation rules, testing rules, tool usage rules
- [Source: backend/build.gradle] — jOOQ codegen config (single config, `hu.riskguard.jooq` package, Flyway → jOOQ → compile chain)
- [Source: backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java] — Existing ArchUnit rules to extend
- [Source: backend/src/main/java/hu/riskguard/screening/package-info.java] — Reference module documentation pattern

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- `./gradlew generateJooq` — jOOQ codegen detected 16 tables (4 new EPR + 12 existing). EPR tables: EprCalculations, EprConfigs, EprExports, EprMaterialTemplates.
- `./gradlew check` — BUILD SUCCESSFUL. All backend tests pass (0 failures, 0 errors). ModulithVerificationTest.verifyModulith() passes. NamingConventionTest 12/12 rules pass (including 2 new EPR rules).
- `npx vitest run` — 479 frontend tests passed (47 test files, 0 failures). Zero regressions.

### Completion Notes List

- **Task 1:** Created `hu.riskguard.epr` module with 3-layer structure matching `screening` reference. `package-info.java` documents module purpose, table ownership, and cross-module access rules. `EprController` wired with `@RequestMapping("/api/v1/epr")`, `EprService` has `isHealthy()` placeholder, `EprRepository` injects `DSLContext`. Empty `api/dto/` and `domain/events/` dirs with `.gitkeep`.
- **Task 2:** Flyway migration `V20260323_001__create_epr_tables.sql` creates 4 tables. `epr_configs` is global (no tenant_id), other 3 are tenant-scoped with FK to `tenants(id)`. 3 indexes created per AC. `epr_calculations` includes expanded columns (template_id, kf_code, fee_rate, quantity, total_weight_grams) per story spec.
- **Task 3:** Seed migration `V20260323_002__seed_epr_fee_tables.sql` inserts complete legislation-verified JSON from `epr-seed-data-2026.json` as `config_data` JSONB. Used Python script to inline the 20KB JSON to avoid SQL escaping issues. Schema version `2026.1`, `schema_verified=true`.
- **Task 4:** Added 2 ArchUnit rules to `NamingConventionTest.java`: (1) `epr_module_should_only_access_own_tables` using identity-pattern prefix-matching for jOOQ nested class support, (2) `epr_internal_should_not_be_accessed_externally` with architecture/BeanDefinitions/BeanFactoryRegistrations exclusions. All 12 ArchUnit rules pass.
- **Task 5:** `EprModuleIntegrationTest.java` with 4 tests: contextLoadsWithEprModule, eprTablesExistAfterMigration (checks all 4 tables via information_schema), seedDataExists (version=1 count check), seedDataContainsExpectedJsonStructure (kf_code_structure/fee_rates_2026/fee_modulation). Uses Testcontainers PostgreSQL 17, @ActiveProfiles("test"), MockitoBean JwtDecoder.
- **Task 6:** Created `hu/epr.json` and `en/epr.json` with nested JSON objects (matching screening.json pattern). Keys: epr.config.title, epr.config.version, epr.export.title, epr.materialLibrary.empty, epr.materialLibrary.title, epr.title, epr.wizard.title. Registered in `nuxt.config.ts` locales files arrays. Created `frontend/app/components/Epr/.gitkeep` placeholder.
- **Task 7:** Full verification gate passed. `./gradlew check` BUILD SUCCESSFUL (5m). 479 frontend Vitest tests passed (0 regressions). ModulithVerificationTest confirms `epr` module detected.
- **✅ Resolved review finding [HIGH]: ArchUnit EPR table rule** — Added `extractRecordName()` helper to `epr_module_should_only_access_own_tables` rule. Now allows `hu.riskguard.jooq.tables.records.EprConfigsRecord` etc., matching identity module pattern. Stories 4.1-4.4 will no longer fail ArchUnit on jOOQ record types.
- **✅ Resolved review finding [HIGH]: epr_configs.version UNIQUE constraint** — Created `V20260323_003__epr_review_fixes.sql` which drops the plain index and creates `CREATE UNIQUE INDEX idx_epr_configs_version ON epr_configs(version)`. Prevents duplicate version records.
- **✅ Resolved review finding [HIGH]: epr_calculations nullable columns** — Same migration V20260323_003 issues `ALTER COLUMN kf_code DROP NOT NULL` and `ALTER COLUMN fee_rate DROP NOT NULL`. Story 4.2 DAG wizard can now partial-save calculations.
- **✅ Resolved review finding [MEDIUM]: EprRepository extends BaseRepository** — Refactored to `extends BaseRepository` with `super(dsl)` constructor. Now provides `selectFromTenant()` and `tenantCondition()` for Stories 4.1-4.4.
- **✅ Resolved review finding [MEDIUM]: Integration test uses jOOQ generated classes** — Replaced all `DSL.table()`/`DSL.field()` with type-safe `EPR_CONFIGS`, `EPR_MATERIAL_TEMPLATES`, `EPR_CALCULATIONS`, `EPR_EXPORTS` references. Compile-time proof that jOOQ codegen produced the expected classes (AC 6). Note: tenant-scoped tables verified via `getName()` assertion + information_schema query (no tenant context in test environment).
- **✅ Resolved review finding [MEDIUM]: AC 3 tenant_id exception** — AC 3 text notes "all tables have tenant_id" — implementation is correct (`epr_configs` is global, per AC text and Dev Notes). AC section is immutable per workflow rules; clarification is captured here for reviewer context.
- **✅ Resolved review finding [LOW]: .gitignore PDF exclusion** — Added `*.pdf` and `risk_epr.md` to `.gitignore`. Files were never tracked in git; now excluded from accidental staging.
- **✅ Accepted review finding [LOW]: ::jsonb cast** — Cannot remove from already-applied Flyway migration without breaking checksum integrity. Cosmetic finding; noted for Story 6.3 when the seed migration is superseded by the hot-swappable config manager.
- **✅ Resolved review finding R2 [HIGH]: export_format VARCHAR → ENUM** — Created `V20260323_004__epr_review_r2_fixes.sql`. Creates `export_format_type AS ENUM ('CSV', 'XLSX')` and alters `epr_exports.export_format` to use the ENUM type. Architecture spec compliance restored.
- **✅ Resolved review finding R2 [MEDIUM]: ON DELETE semantics for template_id and calculation_id FKs** — Same migration V20260323_004. `epr_calculations.template_id` → `ON DELETE SET NULL` (calculations survive template deletion as unclassified). `epr_exports.calculation_id` → `ON DELETE SET NULL` (export records preserved for audit trail after calculation deletion). Named constraints added.
- **✅ Resolved review finding R2 [MEDIUM]: config_version FK referential integrity** — Same migration V20260323_004. Added named FK constraints `fk_epr_calculations_config_version` and `fk_epr_exports_config_version` referencing `epr_configs(version)` ON DELETE RESTRICT. Enabled by the UNIQUE index on `epr_configs.version` (from V20260323_003).
- **✅ Resolved review finding R2 [MEDIUM]: EprController unused eprService field** — Removed `private final EprService eprService` field and `@RequiredArgsConstructor` from `EprController.java`. Clean scaffold controller now has no injections. Added javadoc reminder to inject `EprService` when first endpoint is added.
- **✅ Resolved review finding R2 [LOW]: EprRepository updated_at documentation** — Added javadoc to `EprRepository` documenting the manual `updated_at` update requirement for all UPDATE operations on `epr_material_templates`.
- **✅ Resolved review finding R2 [LOW]: Integration test fragile assertEquals(4)** — Changed `assertEquals(4, tables.size())` to `assertTrue(tables.size() >= 4)` in `EprModuleIntegrationTest.eprTablesExistAfterMigration()`. Test will not break when Stories 4.1-4.4 add more EPR tables.

### File List

**New files:**
- `backend/src/main/java/hu/riskguard/epr/package-info.java`
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/.gitkeep`
- `backend/src/main/java/hu/riskguard/epr/domain/EprService.java`
- `backend/src/main/java/hu/riskguard/epr/domain/events/.gitkeep`
- `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java`
- `backend/src/main/resources/db/migration/V20260323_001__create_epr_tables.sql`
- `backend/src/main/resources/db/migration/V20260323_002__seed_epr_fee_tables.sql`
- `backend/src/test/java/hu/riskguard/epr/EprModuleIntegrationTest.java`
- `frontend/app/i18n/hu/epr.json`
- `frontend/app/i18n/en/epr.json`
- `frontend/app/components/Epr/.gitkeep`

**New files (review follow-up R1):**
- `backend/src/main/resources/db/migration/V20260323_003__epr_review_fixes.sql`

**New files (review follow-up R2):**
- `backend/src/main/resources/db/migration/V20260323_004__epr_review_r2_fixes.sql`

**Modified files:**
- `backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java` — Added 2 EPR ArchUnit rules; R1: added `extractRecordName()` helper for jOOQ record type support
- `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java` — R1: refactored to extend `BaseRepository`; R2: added javadoc for updated_at manual update requirement
- `backend/src/test/java/hu/riskguard/epr/EprModuleIntegrationTest.java` — R1: replaced string-based DSL with jOOQ generated table references (`EPR_CONFIGS`, etc.); R2: changed assertEquals(4) to assertTrue(size >= 4)
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` — R2: removed unused eprService field and @RequiredArgsConstructor; clean scaffold controller
- `frontend/nuxt.config.ts` — Registered `epr.json` in both locale file lists
- `.gitignore` — R1: added `*.pdf` and `risk_epr.md` exclusions

## Change Log

- 2026-03-23: Story 4.0 implemented — EPR module scaffolding complete. 7 tasks, all ACs satisfied. 4 new DB tables, 2 Flyway migrations, 4 integration tests, 2 ArchUnit rules, i18n namespace files for hu/en.
- 2026-03-23: Code review R1 — 8 findings (3H/3M/2L). H1: ArchUnit EPR rule missing jOOQ record type handling (will break Stories 4.1-4.4). H2: epr_configs.version missing UNIQUE constraint. H3: epr_calculations.kf_code/fee_rate over-constrained as NOT NULL. M1: EprRepository not extending BaseRepository. M2: Integration test uses string-based DSL instead of type-safe jOOQ. M3: AC 3 text contradicts implementation re: tenant_id on epr_configs. 8 action items created. Status → in-progress.
- 2026-03-23: Addressed code review findings R1 — 8 items resolved. Added V20260323_003 migration (UNIQUE version constraint + nullable kf_code/fee_rate). ArchUnit rule updated with extractRecordName() helper. EprRepository extends BaseRepository. Integration test uses type-safe jOOQ generated classes. .gitignore updated. Status → review.
- 2026-03-23: Code review R2 — 6 findings (1H/3M/2L). H1: export_format VARCHAR should be ENUM per architecture spec. M1: FK ON DELETE semantics missing for template_id and calculation_id. M2: config_version lacks FK referential integrity. M3: EprController unused eprService field. L1: updated_at not documented in EprRepository. L2: Integration test assertEquals(4) fragile. All 6 items auto-fixed. Status → review.
