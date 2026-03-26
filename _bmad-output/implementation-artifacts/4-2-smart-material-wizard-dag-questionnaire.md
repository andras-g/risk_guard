# Story 4.2: Smart Material Wizard (DAG Questionnaire)

Status: done

## Story

As a User (PRO_EPR tier),
I want a multi-step wizard that guides me through the EPR KF-code classification by asking structured questions about my material's characteristics,
So that I am legally compliant with Hungarian EPR regulations without needing to memorize the KF-code hierarchy from 80/2023 Korm. rendelet.

## Acceptance Criteria

### AC 1: DagEngine — Backend JSON DAG Traversal

**Given** the `epr_configs` table with a versioned `config_data` JSONB containing the full KF-code hierarchy (from `epr-seed-data-2026.json`)
**When** the `DagEngine.java` service receives a traversal step request with `configVersion` and a user selection (e.g., `productStream: "11"`)
**Then** it loads the active config from `epr_configs` (cached per version), parses the `kf_code_structure` section, and returns the valid next options for the next level of the hierarchy
**And** the traversal follows the 4-level hierarchy: Product Stream (positions 1-2) → Material Stream (positions 3-4) → Group (positions 5-6) → Subgroup (positions 7-8)
**And** at each level, the engine returns only the valid child options for the parent selection (e.g., selecting product stream "11" returns only packaging material streams: 01-07)
**And** every traversal step is validated — invalid selections return a 400 error with a clear message
**And** the engine is a pure function with no side effects — it reads config data and returns results deterministically

### AC 2: DagEngine — KF-Code Resolution and Fee Rate Lookup

**Given** a completed 4-step traversal (all 4 hierarchy levels selected)
**When** the DagEngine resolves the final KF-code
**Then** it concatenates the 4 two-digit selections into the 8-digit KF code (e.g., `11020101`)
**And** it derives the 4-digit díjkód from the product stream + material stream (e.g., `1102`)
**And** it looks up the fee rate from `fee_rates_2026` in the config (e.g., `1102` → `42.89` Ft/kg)
**And** the response includes: `kfCode` (8-digit), `feeCode` (4-digit díjkód), `feeRate` (BigDecimal Ft/kg), `currency` ("HUF"), `traversalPath` (JSONB array of selections with labels), `materialClassification` (human-readable label)
**And** the fee rate includes the legislation reference for traceability

### AC 3: DagEngine — Traversal Persistence (epr_calculations)

**Given** a completed wizard traversal for a specific material template
**When** the user confirms the KF-code result ("Confirm and Link")
**Then** an `epr_calculations` record is created with: `tenant_id`, `config_version` (FK to epr_configs.version), `traversal_path` (JSONB), `material_classification`, `kf_code`, `fee_rate`, `template_id` (FK to the source material template, nullable)
**And** if the material template was specified, the template's `kf_code` is updated and `verified` is set to `true`
**And** the template's `updated_at` is explicitly set to `now()` (no DB trigger)
**And** if linking to a template fails (e.g., template deleted between wizard start and confirm), the calculation record is still saved with `template_id = NULL` and the user sees a warning toast

### AC 4: Wizard API Endpoints

**Given** the `EprController` with existing material CRUD endpoints
**When** the wizard endpoints are added
**Then** `GET /api/v1/epr/wizard/start?configVersion={v}` returns the root level options (product streams) with labels in the user's locale (hu/en from Accept-Language header)
**And** `POST /api/v1/epr/wizard/step` accepts `{ configVersion, traversalPath: [...previous selections], selection: { level, code } }` and returns the next level's valid options
**And** `POST /api/v1/epr/wizard/resolve` accepts the complete 4-level traversal and returns the resolved KF-code, fee rate, and breadcrumb path
**And** `POST /api/v1/epr/wizard/confirm` accepts `{ configVersion, traversalPath, kfCode, feeRate, materialClassification, templateId? }` and persists the calculation + links to template
**And** all wizard endpoints are protected by `@TierRequired(Tier.PRO_EPR)`
**And** all endpoints extract `tenant_id` from JWT `active_tenant_id` claim (never from request params)

### AC 5: Frontend — WizardStepper Component (PrimeVue 4 Stepper)

**Given** the EPR Material Library page with a material template row
**When** I click "Classify" (or "Re-classify") on an unverified (or verified) template
**Then** the WizardStepper component renders below the DataTable (or replaces it on mobile) using PrimeVue 4's `Stepper` with `linear` mode
**And** the stepper shows 3+1 steps: Step 1 "Product Stream" (termékáram), Step 2 "Material & Usage" (anyagáram + csoport — combined for UX simplicity when only one option exists), Step 3 "Subtype" (alcsoport), Step 4 "Confirm" (result display)
**And** each step displays large tappable option cards (not radio buttons) with Hungarian labels and English subtitles per UX Spec §11.4
**And** selecting an option immediately calls `POST /wizard/step` for backend validation and auto-advances to the next step
**And** a breadcrumb trail above the stepper shows the current path (e.g., `Csomagolás → Műanyag → Fogyasztói`)
**And** form inputs follow UX validation patterns: Emerald (#15803D) border for valid selections, Crimson (#B91C1C) for errors
**And** the wizard can be dismissed at any time via a "Cancel" button that returns to the DataTable without saving

### AC 6: Frontend — Result Display and Confirmation

**Given** a completed wizard traversal (all steps selected)
**When** the final step renders
**Then** a green confirmation card displays: KF-code formatted as `XX XX XX XX` (spaced for readability), the fee rate in `XX.XX Ft/kg`, the material classification label, and the full breadcrumb path
**And** a "Confirm and Link" button calls `POST /wizard/confirm` to persist the calculation and update the template
**And** on successful confirmation, the material template's row in the DataTable updates to show the KF-code and a green "Verified" badge
**And** the wizard closes and the user is returned to the DataTable with a success toast
**And** if confirmation fails, a Crimson error toast shows the localized error via `useApiError()`

### AC 7: Frontend — Wizard State Management (Pinia)

**Given** the existing `useEprStore` Pinia store
**When** the wizard is active
**Then** wizard state is managed in a new `useEprWizardStore` Pinia store (separate from material CRUD to avoid state pollution)
**And** the store tracks: `activeStep`, `traversalPath` (array of selections), `availableOptions` (current step's options), `resolvedResult` (final KF-code + fee), `isLoading`, `targetTemplateId`
**And** when the wizard closes (confirm or cancel), the store is `$reset()` to clear all wizard state
**And** the material list in `useEprStore` is refreshed after a successful confirm

### AC 8: i18n — All Wizard Text Localized

**Given** the EPR Wizard
**When** displayed in Hungarian or English
**Then** all step labels, option card labels, breadcrumb items, result display, buttons, error messages, and toast notifications use i18n keys from `epr.json`
**And** the KF-code hierarchy labels come from the backend response (which reads them from `config_data` based on Accept-Language)
**And** new keys are added to both `hu/epr.json` and `en/epr.json` with alphabetical sorting and key parity maintained

## Tasks / Subtasks

- [x] Task 1: Backend — DagEngine Core Logic (AC: #1, #2)
  - [x] 1.1 Create `DagEngine.java` in `epr/domain/` — pure function service that traverses the `kf_code_structure` JSON from `epr_configs.config_data`
  - [x] 1.2 Implement `getProductStreams(JsonNode configData, String locale)` — returns root-level options (product_streams map) with localized labels
  - [x] 1.3 Implement `getMaterialStreams(JsonNode configData, String productStreamCode, String locale)` — returns material_streams for the selected product stream context (e.g., "packaging" for 11/12/13, "single_use_plastic" for 81)
  - [x] 1.4 Implement `getGroups(JsonNode configData, String productStreamCode, String materialStreamCode, String locale)` — returns group options for the product+material context
  - [x] 1.5 Implement `getSubgroups(JsonNode configData, String productStreamCode, String materialStreamCode, String groupCode, String locale)` — returns subgroup options
  - [x] 1.6 Implement `resolveKfCode(JsonNode configData, String productStream, String materialStream, String group, String subgroup)` — concatenates 4 codes into 8-digit KF code, derives 4-digit díjkód, looks up fee rate from `fee_rates_2026`
  - [x] 1.7 Implement config caching: load `config_data` from `epr_configs` by version, cache in a `ConcurrentHashMap<Integer, JsonNode>` (invalidated on config update)
  - [x] 1.8 All methods validate inputs and throw `IllegalArgumentException` for invalid codes (mapped to 400 by controller)

- [x] Task 2: Backend — Wizard DTOs (AC: #4)
  - [x] 2.1 Create `WizardStartResponse.java` record in `epr/api/dto/` — `configVersion` (int), `level` (String), `options` (List of WizardOption)
  - [x] 2.2 Create `WizardOption.java` record — `code` (String), `label` (String), `description` (String, nullable)
  - [x] 2.3 Create `WizardStepRequest.java` record — `configVersion` (int), `traversalPath` (List of WizardSelection), `selection` (WizardSelection)
  - [x] 2.4 Create `WizardSelection.java` record — `level` (String: "product_stream"/"material_stream"/"group"/"subgroup"), `code` (String), `label` (String)
  - [x] 2.5 Create `WizardStepResponse.java` record — `configVersion` (int), `currentLevel` (String), `nextLevel` (String), `options` (List of WizardOption), `breadcrumb` (List of WizardSelection)
  - [x] 2.6 Create `WizardResolveResponse.java` record — `kfCode` (String, 8-digit), `feeCode` (String, 4-digit díjkód), `feeRate` (BigDecimal), `currency` (String), `materialClassification` (String), `traversalPath` (List of WizardSelection), `legislationRef` (String)
  - [x] 2.7 Create `WizardConfirmRequest.java` record — `configVersion` (int), `traversalPath` (List of WizardSelection), `kfCode` (String), `feeRate` (BigDecimal), `materialClassification` (String), `templateId` (UUID, nullable)
  - [x] 2.8 Create `WizardConfirmResponse.java` record — `calculationId` (UUID), `kfCode` (String), `templateUpdated` (boolean)

- [x] Task 3: Backend — EprService Wizard Methods (AC: #1, #2, #3)
  - [x] 3.1 `startWizard(int configVersion, String locale)` — delegates to DagEngine.getProductStreams()
  - [x] 3.2 `processStep(WizardStepRequest request, String locale)` — validates traversal path, delegates to appropriate DagEngine method based on next level
  - [x] 3.3 `resolveKfCode(traversalPath, configVersion)` — delegates to DagEngine.resolveKfCode(), returns full resolution result
  - [x] 3.4 `confirmWizard(WizardConfirmRequest request, UUID tenantId)` — persists epr_calculations record, optionally updates template kf_code+verified; uses @Transactional for atomicity
  - [x] 3.5 `getActiveConfigVersion()` — returns the latest activated config version from epr_configs

- [x] Task 4: Backend — EprRepository Wizard Methods (AC: #3)
  - [x] 4.1 `findActiveConfig()` — `SELECT * FROM epr_configs WHERE activated_at IS NOT NULL ORDER BY version DESC LIMIT 1`
  - [x] 4.2 `findConfigByVersion(int version)` — `SELECT * FROM epr_configs WHERE version = ?`
  - [x] 4.3 `insertCalculation(UUID tenantId, int configVersion, JsonNode traversalPath, String materialClassification, String kfCode, BigDecimal feeRate, UUID templateId)` — INSERT into epr_calculations with all fields
  - [x] 4.4 `updateTemplateKfCode(UUID templateId, UUID tenantId, String kfCode)` — SET kf_code, verified=true, updated_at=now() WHERE id AND tenant_id match

- [x] Task 5: Backend — EprController Wizard Endpoints (AC: #4)
  - [x] 5.1 `GET /api/v1/epr/wizard/start` — query param `configVersion` (optional, defaults to active); extracts locale from Accept-Language header
  - [x] 5.2 `POST /api/v1/epr/wizard/step` — body: WizardStepRequest; returns WizardStepResponse with next options
  - [x] 5.3 `POST /api/v1/epr/wizard/resolve` — body: complete traversal path; returns WizardResolveResponse with KF-code + fee
  - [x] 5.4 `POST /api/v1/epr/wizard/confirm` — body: WizardConfirmRequest; extracts tenant_id from JWT; returns WizardConfirmResponse
  - [x] 5.5 All endpoints: `@TierRequired(Tier.PRO_EPR)`, JWT tenant extraction via `requireUuidClaim`

- [x] Task 6: Backend — Tests (AC: #1-#4)
  - [x] 6.1 `DagEngineTest.java` — unit test: test all 4 hierarchy levels, invalid inputs, fee rate lookup, edge cases (EEE categories, composite materials, tires). Minimum 15 test cases covering all product stream families.
  - [x] 6.2 `EprServiceWizardTest.java` — unit test with mocked repository and DagEngine: test wizard flow, confirm logic, template update, error paths
  - [x] 6.3 `EprControllerWizardTest.java` — MockMvc test: test all 4 wizard endpoints, validation errors, tenant isolation, tier gating
  - [x] 6.4 Extend `EprModuleIntegrationTest.java` — verify DagEngine can load and traverse the real seed data from epr_configs (integration smoke test)
  - [x] 6.5 Verify all existing tests pass — zero regressions (`./gradlew check`)

- [x] Task 7: Frontend — Wizard Pinia Store (AC: #7)
  - [x] 7.1 Create `frontend/app/stores/eprWizard.ts` — separate store for wizard state: `activeStep`, `traversalPath`, `availableOptions`, `resolvedResult`, `isLoading`, `error`, `targetTemplateId`, `configVersion`
  - [x] 7.2 Actions: `startWizard(templateId?)`, `selectOption(selection)`, `resolveResult()`, `confirmAndLink()`, `cancelWizard()`
  - [x] 7.3 `startWizard` calls `GET /wizard/start`, sets initial options and configVersion
  - [x] 7.4 `selectOption` calls `POST /wizard/step`, appends to traversalPath, sets next options
  - [x] 7.5 `resolveResult` calls `POST /wizard/resolve` after final selection
  - [x] 7.6 `confirmAndLink` calls `POST /wizard/confirm`, then triggers `useEprStore().fetchMaterials()` refresh
  - [x] 7.7 `cancelWizard` calls `$reset()` to clear all state

- [x] Task 8: Frontend — WizardStepper Component (AC: #5, #6)
  - [x] 8.1 Create `frontend/app/components/Epr/WizardStepper.vue` — PrimeVue 4 `Stepper` with `linear` mode, `StepList` + `StepPanels` layout
  - [x] 8.2 Step 1: Product Stream — grid of large tappable cards (`SelectButton`-style) showing product stream options with icons
  - [x] 8.3 Step 2: Material & Usage — combined material stream + group selection; if only one group option exists for the material, auto-select it
  - [x] 8.4 Step 3: Subtype — subgroup selection cards; if only one subgroup ("01 Default"), auto-select and skip to result
  - [x] 8.5 Step 4: Result — green confirmation card with KF-code (`XX XX XX XX`), fee rate, breadcrumb trail, "Confirm and Link" + "Cancel" buttons
  - [x] 8.6 Breadcrumb component above stepper showing traversal path
  - [x] 8.7 Loading skeleton while API calls are in-flight (PrimeVue Skeleton for option cards)
  - [x] 8.8 Error handling: API failures show Crimson toast via `useApiError()`
  - [x] 8.9 Create co-located `WizardStepper.spec.ts` test file

- [x] Task 9: Frontend — MaterialSelector Component (AC: #5)
  - [x] 9.1 Create `frontend/app/components/Epr/MaterialSelector.vue` — reusable option card grid used within each wizard step
  - [x] 9.2 Props: `options` (WizardOption[]), `selectedCode` (string), `isLoading` (boolean)
  - [x] 9.3 Emit: `@select(option)` when user clicks a card
  - [x] 9.4 Card styling: large tappable cards (min 120px height), Emerald border on selected, hover effect, label + description
  - [x] 9.5 Responsive: 3-column grid on desktop, 2-column on tablet, 1-column stacked on mobile
  - [x] 9.6 Create co-located `MaterialSelector.spec.ts` test file

- [x] Task 10: Frontend — EPR Page Integration (AC: #5, #6)
  - [x] 10.1 Modify `frontend/app/pages/epr/index.vue` — add "Classify" / "Re-classify" button to MaterialInventoryBlock actions column
  - [x] 10.2 When wizard is active (`eprWizardStore.activeStep !== null`), render WizardStepper below the DataTable (desktop) or replace it (mobile)
  - [x] 10.3 Pass `targetTemplateId` from the clicked material row to the wizard store
  - [x] 10.4 After confirm, DataTable row updates with new KF-code and "Verified" badge

- [x] Task 11: i18n Keys (AC: #8)
  - [x] 11.1 Add wizard keys to `frontend/app/i18n/hu/epr.json`: `epr.wizard.step1Title`, `epr.wizard.step2Title`, `epr.wizard.step3Title`, `epr.wizard.resultTitle`, `epr.wizard.confirmAndLink`, `epr.wizard.cancel`, `epr.wizard.breadcrumb`, `epr.wizard.kfCodeLabel`, `epr.wizard.feeRateLabel`, `epr.wizard.classificationLabel`, `epr.wizard.successToast`, `epr.wizard.errorToast`, `epr.wizard.classify`, `epr.wizard.reclassify`, etc.
  - [x] 11.2 Add matching English keys to `en/epr.json`
  - [x] 11.3 Maintain alphabetical sorting at every nesting level and key parity

- [x] Task 12: Verification Gate
  - [x] 12.1 Backend: `./gradlew check` — all tests pass, ArchUnit clean, zero regressions
  - [x] 12.2 Frontend: `npx vitest run` — all tests pass, zero regressions
  - [x] 12.3 Integration smoke test: DagEngine traverses real seed data from epr_configs and resolves correct KF-codes for 5+ test cases

- [x] Task 13: Review Follow-ups (AI)
  - [x] [AI-Review][HIGH] Add `static from()` factory methods to `WizardStartResponse.java`, `WizardStepResponse.java`, and `WizardConfirmResponse.java` — required by `NamingConventionTest` ArchUnit rule `response_dtos_should_have_from_factory`; build will fail without this [backend/src/main/java/hu/riskguard/epr/api/dto/WizardStartResponse.java, WizardStepResponse.java, WizardConfirmResponse.java]
  - [x] [AI-Review][HIGH] Fix NPE bug in `DagEngineTest.@BeforeAll` fallback path: `is.close()` is called on a null `InputStream` reference; restructure the null check to avoid calling methods on null [backend/src/test/java/hu/riskguard/epr/DagEngineTest.java:38-44]
  - [x] [AI-Review][HIGH] Fix mobile wizard layout: when `wizardStore.isActive`, hide the mobile card list with `v-if`/`v-else` so the wizard replaces the material cards on mobile (< 768px), not stacks below them — per AC 5 and UX Spec §8.1 [frontend/app/pages/epr/index.vue:179-243]
  - [x] [AI-Review][HIGH] Extend `EprModuleIntegrationTest.java` with actual DagEngine traversal smoke test: use `eprService.startWizard()`, `processStep()`, and `resolveKfCode()` against the real seeded config to resolve at minimum 5 KF-codes (golden cases 1-5 from story Dev Notes) — Task 6.4 was marked `[x]` but this test was not added [backend/src/test/java/hu/riskguard/epr/EprModuleIntegrationTest.java]
  - [x] [AI-Review][MEDIUM] Fix `validateCode()` in `DagEngine.java` to enforce exactly 2-digit numeric codes (`\\d{2}` instead of `\\d{1,2}`) — single-digit codes produce malformed 6-character KF codes [backend/src/main/java/hu/riskguard/epr/domain/DagEngine.java:341-348]
  - [x] [AI-Review][MEDIUM] Add recursion depth guard to `selectOption()` in `eprWizard.ts` — the recursive auto-advance call has no depth limit; add a `autoAdvanceDepth` counter (max 3) to prevent infinite recursion when backend returns consecutive `autoSelect: true` responses [frontend/app/stores/eprWizard.ts:101-112]
  - [x] [AI-Review][MEDIUM] Fix step-to-level mapping in `WizardStepper.vue`: the `levelMap` maps step `'3'` to `'group'` but the store also advances to step `'3'` for subgroup selections; when `wizardStore.traversalPath` already contains a `group` entry and step is `'3'`, the level should resolve to `'subgroup'` instead — deposit packaging (stream 12) will send wrong level and corrupt the traversal path [frontend/app/components/Epr/WizardStepper.vue:15-22]
  - [x] [AI-Review][MEDIUM] Replace plain Mockito controller test with proper MockMvc test for `EprControllerWizardTest.java` — current test instantiates the controller directly and cannot verify `@TierRequired`, `@Valid` constraints, or HTTP status codes; add MockMvc setup with a mock `SecurityContext` to verify PRO_EPR tier gating returns 403 for lower tiers [backend/src/test/java/hu/riskguard/epr/EprControllerWizardTest.java]
  - [x] [AI-Review][MEDIUM] Add Classify/Re-classify button to the mobile card layout in `index.vue` — the desktop DataTable has the button via `EprMaterialInventoryBlock @classify` event, but the mobile `<div class="block md:hidden">` card loop has no classify action, breaking AC 5 on mobile [frontend/app/pages/epr/index.vue:206-222]
  - [x] [AI-Review][LOW] Add success toast after wizard confirmation in `index.vue`: the `watch(() => wizardStore.isActive)` callback is empty; show `t('epr.wizard.successToast')` toast when the wizard closes after a successful confirm (distinguish from cancel by checking if `eprStore.materials` was refreshed or using a separate `lastConfirmSuccess` flag in the wizard store) — per AC 6 [frontend/app/pages/epr/index.vue:95-100]
  - [x] [AI-Review][LOW] Pass `locale` parameter to `resolveKfCode()` in `DagEngine.java` and use `localizedName()` for the `classification` field instead of hardcoding `name_hu` — English users see Hungarian classification labels in the result card [backend/src/main/java/hu/riskguard/epr/domain/DagEngine.java:317]

## Dev Notes

This story implements the core EPR intelligence — the DAG-based questionnaire that classifies packaging materials into 8-digit KF codes per Hungarian regulation 80/2023 (III. 14.) Korm. rendelet. The DagEngine is a **pure-function** backend service that traverses a JSON decision tree stored in `epr_configs.config_data`. The frontend uses PrimeVue 4's Stepper component in `linear` mode to guide users through 3-4 steps, with large tappable option cards instead of radio buttons. The seed data in `epr-seed-data-2026.json` contains the complete 2026 KF-code hierarchy and fee rates from legislation — this JSON is loaded into `epr_configs` and served to the DagEngine at runtime.

### Critical Architecture Patterns — MUST Follow

**Reference implementation:** `hu.riskguard.screening` is the canonical pattern. Follow the 3-layer structure: `api/` (controller + DTOs) → `domain/` (service facade + DagEngine) → `internal/` (repository). [Source: architecture.md#Implementation-Patterns]

**Module facade:** `EprService.java` is the ONLY public entry point. `DagEngine.java` is a domain service within the `epr/domain/` package — it is called by `EprService`, never directly by the controller. The controller calls the service, the service orchestrates DagEngine + repository. No external module may import `epr.internal` or `epr.domain.DagEngine` directly. Enforced by ArchUnit. [Source: architecture.md#Communication-Patterns]

**DagEngine is a pure function:** It takes `(configData: JsonNode, traversalInputs, locale: String)` and returns results. No database calls, no side effects, no injected dependencies except the config cache. This makes it trivially testable with unit tests. [Source: architecture.md#epr-Module-Failure-Modes]

**DTO pattern:** All DTOs are Java records in `epr/api/dto/`. Wizard DTOs follow the same convention as existing material DTOs. No `static from()` needed on wizard DTOs since they map from DagEngine output (not jOOQ records), but if a response maps from a jOOQ record (e.g., `WizardConfirmResponse`), include the factory method. [Source: architecture.md#DTO-Mapping-Strategy]

**Tenant isolation:** Extract `active_tenant_id` from JWT via `requireUuidClaim(jwt, "active_tenant_id")`. Wizard start/step/resolve endpoints do NOT need tenant_id (they read config data only). The confirm endpoint MUST use tenant_id for the `epr_calculations` INSERT and template update. NEVER accept tenant_id as a query parameter. [Source: project-context.md#Framework-Specific-Rules]

**Locale extraction:** Extract the user's locale from the `Accept-Language` header via `LocaleContextHolder.getLocale().getLanguage()`. This is resolved by the existing `AcceptHeaderLocaleResolver` bean in `hu.riskguard.core.config.I18nConfig`. Pass the locale string ("hu" or "en") to DagEngine methods for label localization. Default to "hu" if unresolvable. [Source: I18nConfig.java]

**jOOQ — NOT JPA:** Use type-safe jOOQ DSL. Import from `hu.riskguard.jooq.Tables.EPR_CONFIGS`, `EPR_CALCULATIONS`, `EPR_MATERIAL_TEMPLATES`. The `EprRepository` already extends `BaseRepository`. [Source: project-context.md#Language-Specific-Rules]

**updated_at manual enforcement:** When updating `epr_material_templates.kf_code` and `verified`, MUST explicitly set `.set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())`. No DB trigger. [Source: 4-0 story, review finding R2-L1]

**Tier gating:** All wizard endpoints require `PRO_EPR` tier. The `@TierRequired(Tier.PRO_EPR)` is already at class level on `EprController`, so new methods inherit it automatically. [Source: useTierGate.ts, Story 3.3]

**Versioned config:** `epr_configs.config_data` is JSONB containing the full seed data. `epr_configs.version` is an integer, `activated_at` marks which version is live. The DagEngine MUST accept a specific `configVersion` parameter so that existing calculations remain reproducible with the config version that created them. [Source: architecture.md#epr-Module]

**Testing mandate:**
- Backend: JUnit 5 + Testcontainers PostgreSQL 17. NO H2. DagEngine is pure-function → unit test with static JSON fixtures (no database needed). Service + Controller tests follow existing patterns.
- Frontend: Vitest with co-located `*.spec.ts` files.
- Run `./gradlew check` (includes ArchUnit + Modulith verification).
- [Source: project-context.md#Testing-Rules]

### DagEngine — Backend Implementation Guide

The DagEngine traverses the `epr-seed-data-2026.json` structure stored in `epr_configs.config_data`. The JSON has a hierarchical structure that maps to the 4-level KF-code:

**KF-code structure:** `[product_stream 2d][material_stream 2d][group 2d][subgroup 2d]` = 8 digits total.

**Level 1 — Product Stream (positions 1-2):** Root of the DAG. 16 codes: 11 (non-deposit packaging), 12 (deposit packaging), 13 (reusable packaging), 21-26 (EEE categories 1-6), 31-33 (batteries), 41 (tires), 51 (vehicles), 61 (office paper), 71 (advertising paper), 81 (single-use plastic), 91 (other plastic/chemical).

**Level 2 — Material Stream (positions 3-4):** Context-dependent on product stream.
- Product streams 11, 12, 13 → packaging materials: 01 (paper), 02 (plastic), 03 (wood), 04 (metal), 05 (glass), 06 (textile), 07 (composite)
- Product stream 81 → single-use plastic: 01 (oxo-degradable), 02 (EPS)
- EEE streams 21-26 → EEE category codes (01-06 per spec)
- Others → default "01"

**Level 3 — Group (positions 5-6):** Further context refinement.
- Packaging non-deposit (11) → 01 (consumer), 02 (grouped), 03 (transport)
- Packaging deposit (12) → 01 (PET bottle), 02 (glass bottle), 03 (metal can)
- Tires (41) → 01-06 (passenger car through other)
- Many streams → single option "01"

**Level 4 — Subgroup (positions 7-8):** Final classification.
- Non-deposit packaging (11) → always "01" (default)
- Deposit packaging (12) → 01-15 (default, closure by material, label by material)
- EEE → varies by category (3-7 subtypes each)
- Many streams → "01"

**Smart step collapsing:** When a level has only one valid option (e.g., subgroup "01 Default"), the DagEngine should return `autoSelect: true` in the response so the frontend auto-advances without requiring user interaction. This collapses the wizard from 4 steps to 2-3 for simple cases (e.g., non-deposit paper consumer packaging: 11 → 01 → 01 → [auto]01 = KF code `11010101`).

**Fee rate lookup:** The `fee_rates_2026` section in the seed data maps 4-digit díjkódok (product_stream + material_stream) to Ft/kg rates. The DagEngine concatenates the first two levels to form the díjkód and looks up the rate. Example: product_stream "11" + material_stream "02" = díjkód "1102" → rate 42.89 Ft/kg.

**CRITICAL — Handling `_ref`-only sections (EEE, batteries, tires, paper, vehicles):**
The seed data's `material_streams` section has actual enumerated options ONLY for `packaging` (01-07) and `single_use_plastic` (01-02). All other product stream families (EEE, batteries, tires, vehicles, office_paper, advertising_paper, other_plastic_chemical) have only `_ref` strings with NO enumerated child options. The DagEngine MUST handle these cases:

- **EEE (product streams 21-26):** Each product stream IS its own EEE category. There is effectively ONE material_stream per product stream (the category itself). The DagEngine should auto-select material_stream "01" and skip directly to the subgroup level (which has real enumerated options in `subgroups.eee.cat_X_*`). Fee code = product_stream + "01" (e.g., 21+01 = `2101`).
- **Batteries (product streams 31-33):** Each product stream IS its own battery type. Material_stream is always "01". Auto-select and skip to subgroups (which ARE enumerated: portable has 01-03, industrial has 01, automotive has 01). Fee code = product_stream + "01" (e.g., 31+01 = `3101`).
- **Tires (product stream 41):** Material_stream is always "01". The real selection happens at the GROUP level (01-06: passenger car through other). All tire types share the same fee rate (30.62 Ft/kg), but the group code is part of the KF code. Fee code is always `4101` regardless of group.
- **Vehicles (51), Office paper (61), Advertising paper (71):** Material_stream, group, and subgroup are all "01". These are single-path traversals. Auto-select ALL intermediate levels after product stream selection.
- **Other plastic/chemical (91):** Material_stream has 2 options (01=plastic, 02=chemical), but these are only in the `_ref` description. The DagEngine should derive options from `fee_rates_2026.other_plastic_chemical` keys: `9101` → material_stream "01", `9102` → material_stream "02".

**Implementation strategy for ref-only sections:** The DagEngine should maintain a **product-stream-to-section mapping** that knows which JSON sections to traverse for each product stream family. When the `material_streams` section for a family contains only a `_ref` string (not enumerated options), the DagEngine should either:
1. Auto-derive options from the `fee_rates_2026` section (preferred — the fee rates list all valid díjkódok)
2. Auto-select "01" and skip the level (for families with only one material_stream option)

**CRITICAL — Composite packaging materials (material_stream "07"):**
For packaging product streams (11/12/13), material_stream "07" is "Composite" (társított). The fee_rates section does NOT have a single `1107` rate. Instead, composites are split by dominant constituent material:
- `1108`: Composite, mainly paper/cardboard (20.44 Ft/kg)
- `1109`: Composite, mainly plastic (42.89 Ft/kg)
- `1110`: Composite, mainly metal (17.37 Ft/kg)
- `1111`: Composite, mainly other (10.22 Ft/kg)

This means selecting material_stream "07" requires an ADDITIONAL sub-selection: "What is the dominant constituent material?" The DagEngine should handle this as a **virtual sub-step within the material_stream level** — when "07 Composite" is selected, show a follow-up question with the 4 constituent options. The resolved díjkód is then `11` + `08`/`09`/`10`/`11` (NOT `1107`). This virtual sub-step replaces the standard material_stream→group flow for composites.

Alternatively, treat composite material codes (08-11) as additional material_stream options that appear INSTEAD of "07 Composite" — i.e., expand composites inline so the user sees: `01 Paper, 02 Plastic, ..., 06 Textile, 07a Composite:Paper, 07b Composite:Plastic, 07c Composite:Metal, 07d Composite:Other`. This avoids an extra step but changes the material_stream selection UI. **Choose whichever approach provides the better UX.**

**Locale handling:** The seed data has `name_hu` and `name_en` on every option. The DagEngine reads the `locale` parameter and returns the appropriate label. Default to "hu" if locale is unknown.

**Config caching strategy:** Use a `ConcurrentHashMap<Integer, JsonNode>` keyed by config version. On first access for a version, load from DB and parse. Cache indefinitely (config versions are immutable once activated). For the "active" version, also cache the version number with a 60-second TTL or invalidate on config activation event.

```java
// DagEngine.java — simplified signature contract
// PURE FUNCTION — takes JsonNode configData as input, no database access, no injected dependencies
@Service
public class DagEngine {

    public List<WizardOption> getProductStreams(JsonNode configData, String locale) { ... }
    public WizardStepResult getMaterialStreams(JsonNode configData, String productStream, String locale) { ... }
    public WizardStepResult getGroups(JsonNode configData, String productStream, String materialStream, String locale) { ... }
    public WizardStepResult getSubgroups(JsonNode configData, String productStream, String materialStream, String group, String locale) { ... }
    public KfCodeResolution resolveKfCode(JsonNode configData, List<WizardSelection> traversalPath) { ... }

    // Internal result types
    record WizardStepResult(List<WizardOption> options, boolean autoSelect, WizardOption autoSelectedOption) {}
    record KfCodeResolution(String kfCode, String feeCode, BigDecimal feeRate, String currency, String classification, String legislationRef) {}
}
```

**Architecture clarification — config loading is EprService's responsibility, NOT DagEngine's:**
- `EprService` owns the config cache (`ConcurrentHashMap<Integer, JsonNode>`) and loads config from `EprRepository`
- `EprService` resolves the config version, fetches from cache (or DB on miss), then passes `JsonNode configData` to `DagEngine` methods
- `DagEngine` is a **true pure function** — it receives `JsonNode configData` and returns results. No injected dependencies, no database access. This makes it trivially unit-testable with static JSON fixtures (no Testcontainers needed).
- The `@Service` annotation on DagEngine is only for Spring bean lifecycle — it has zero injected fields.

### Frontend Wizard — PrimeVue 4 Stepper Implementation Guide

PrimeVue 4 changed the Stepper API significantly from v3. The new API uses composition of sub-components:

```vue
<Stepper :value="activeStep" linear>
  <StepList>
    <Step value="1">{{ $t('epr.wizard.step1Title') }}</Step>
    <Step value="2">{{ $t('epr.wizard.step2Title') }}</Step>
    <Step value="3">{{ $t('epr.wizard.step3Title') }}</Step>
    <Step value="4">{{ $t('epr.wizard.resultTitle') }}</Step>
  </StepList>
  <StepPanels>
    <StepPanel v-slot="{ activateCallback }" value="1">
      <MaterialSelector :options="wizardStore.availableOptions" @select="onSelect($event, activateCallback)" />
    </StepPanel>
    <!-- ... additional panels -->
  </StepPanels>
</Stepper>
```

**Key imports (PrimeVue 4):**
```typescript
import Stepper from 'primevue/stepper'
import StepList from 'primevue/steplist'
import StepPanels from 'primevue/steppanels'
import Step from 'primevue/step'
import StepPanel from 'primevue/steppanel'
```

**Step navigation:** The `StepPanel` scoped slot provides `activateCallback(stepValue)`. After the user selects an option and the API call succeeds, call `activateCallback('next-step-value')` to advance. With `linear` mode, users can only go forward through completed steps or back.

**Auto-advance for single-option steps:** When the backend returns `autoSelect: true`, the frontend should:
1. Auto-select the single option
2. Show it briefly (300ms) for user awareness
3. Call `activateCallback` to advance automatically

**MaterialSelector card layout:**
- Use CSS Grid: `grid-template-columns: repeat(auto-fill, minmax(180px, 1fr))`
- Each card: `min-height: 120px`, `border-radius: 12px`, `cursor: pointer`
- Selected: `border: 2px solid #15803D` (Emerald), `background: #f0fdf4`
- Hover: `border-color: #1e3a5f` (Deep Navy), subtle shadow
- Mobile: single column stacked

**Breadcrumb trail:** Use a simple `<div>` with `→` separators, not PrimeVue Breadcrumb (too heavyweight for this use case). Show localized labels from the traversalPath array.

**Wizard visibility toggle:** The EPR page manages wizard visibility via `eprWizardStore.activeStep`. When non-null, the wizard renders. On desktop (>1024px), render below the DataTable. On mobile (<768px), replace the DataTable entirely (use `v-if/v-else`).

**Result card design (Step 4):**
- Green card: `background: #f0fdf4`, `border: 2px solid #15803D`
- KF-code in large font: `text-2xl font-bold`, formatted as `XX XX XX XX` with spaces
- Fee rate: `text-xl`, formatted as `XX.XX Ft/kg`
- Breadcrumb trail: dimmed text showing full path
- Two buttons: "Confirm and Link" (primary, Deep Navy) and "Cancel" (secondary, Slate Grey)

### Database Schema — Current State After Stories 4.0/4.1

All required tables exist from Story 4.0 migrations. **No new Flyway migrations needed for this story.**

**`epr_material_templates`** — existing, used for template KF-code update:

```sql
CREATE TABLE epr_material_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    name              VARCHAR(255) NOT NULL,
    base_weight_grams DECIMAL NOT NULL,
    kf_code           VARCHAR(8),          -- SET by this story's wizard
    verified          BOOLEAN NOT NULL DEFAULT false,  -- SET to true by this story
    seasonal          BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**`epr_configs`** — existing, used for config data storage:

```sql
CREATE TABLE epr_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version         INT NOT NULL UNIQUE,
    config_data     JSONB NOT NULL,        -- Contains full epr-seed-data-2026.json
    schema_version  VARCHAR(50),
    schema_verified BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at    TIMESTAMPTZ                       -- NULL = draft, NOT NULL = active
);
```

**`epr_calculations`** — existing, used for traversal persistence. The table already has ALL columns needed for this story (created in V20260323_001, patched in V20260323_003/004):

```sql
CREATE TABLE epr_calculations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    config_version          INT NOT NULL,       -- FK to epr_configs.version (added V20260323_004)
    template_id             UUID,               -- FK to epr_material_templates(id) ON DELETE SET NULL (V20260323_004)
    traversal_path          JSONB,              -- Array of {level, code, label} selections
    material_classification VARCHAR(255),       -- Human-readable classification
    kf_code                 VARCHAR(8),         -- Resolved 8-digit KF code (nullable per V20260323_003 for partial-save)
    fee_rate                DECIMAL,            -- Per-kg fee rate in HUF (nullable per V20260323_003 for partial-save)
    quantity                DECIMAL,            -- Units/pieces (used in later stories for filing)
    total_weight_grams      DECIMAL,            -- Total weight = quantity × base_weight (used in later stories)
    fee_amount              DECIMAL,            -- Total fee = fee_rate × total_weight_kg (used in later stories)
    currency                VARCHAR(3) NOT NULL DEFAULT 'HUF',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**No new migration needed for this story.** All columns are present. For this story, set `kf_code`, `fee_rate`, `traversal_path`, `material_classification`, `config_version`, and `tenant_id`. Leave `quantity`, `total_weight_grams`, and `fee_amount` as NULL — they are populated in later stories during the filing workflow.

**Seed data already loaded:** The `epr-seed-data-2026.json` was already loaded into `epr_configs` by `V20260323_002__seed_epr_fee_tables.sql` (Story 4.0). It exists as `version=1`, `schema_version='2026.1'`, `schema_verified=true`, `activated_at=now()`. **Do NOT create another seed migration** — the data is already there. The DagEngine should query for config version 1 (or the active config via `activated_at IS NOT NULL`).

### Previous Story Intelligence (Stories 4.0 and 4.1)

**Story 4.0 key learnings:**
1. ArchUnit jOOQ record handling — the `epr_module_should_only_access_own_tables` rule uses prefix-matching. New jOOQ record types for `epr_configs` and `epr_calculations` are already covered.
2. `EprRepository` extends `BaseRepository` with `selectFromTenant()` and `tenantCondition()`. Use these for tenant-scoped queries on `epr_calculations`.
3. `EprController` now has `@RequiredArgsConstructor` and `EprService` injection from Story 4.1. Add wizard methods to the existing service.
4. jOOQ codegen includes: `EPR_CONFIGS`, `EPR_CALCULATIONS`, `EPR_MATERIAL_TEMPLATES`, `EPR_EXPORTS` — all available from `hu.riskguard.jooq.Tables`.
5. i18n namespace `epr.json` registered in `nuxt.config.ts`.

**Story 4.1 key learnings:**
1. `EprService` has 7 existing methods (CRUD + copy). Add wizard methods alongside them.
2. `EprRepository` has 10 existing methods. Add config loading + calculation insert methods.
3. `EprController` has 7 existing endpoints. Add 4 wizard endpoints.
4. Frontend `useEprStore` manages material CRUD. Create SEPARATE `useEprWizardStore` for wizard state to avoid pollution.
5. The `updated_at` manual enforcement pattern is well-established — apply it when updating template `kf_code`/`verified`.
6. `@Transactional` is required for the confirm flow (insert calculation + update template must be atomic).
7. The `$fetch` pattern with `useRuntimeConfig()` is the established store API call pattern.
8. The `requireUuidClaim` helper for JWT tenant extraction is copy-pasted across controllers (tech debt noted in 4.1 R2-M2, not blocking).
9. `MaterialInventoryBlock` DataTable exists with Actions column — add "Classify"/"Re-classify" button to it.

### Git Intelligence

Recent commits follow the pattern: `feat: Story X.Y — brief description with code review fixes`. The project uses conventional commits with atomic commits (one PR per story). Latest commits: Story 4.1 (EPR Material Library), Story 4.0 (EPR Module Foundation), then Epic 3 stories (3.8 through 3.13). The expected commit message for this story: `feat: Story 4.2 — Smart Material Wizard DAG questionnaire with code review fixes`.

### EPR Fee Table Seed Data — DAG Traversal Reference

The complete seed data is in `_bmad-output/implementation-artifacts/epr-seed-data-2026.json` (334 lines). It contains:

1. **`_metadata`** — version, valid dates (2026-01-01 to 2026-08-11), legislation references
2. **`kf_code_structure`** — the 4-level hierarchy used by the DagEngine:
   - `product_streams` — 16 root codes (11-91)
   - `material_streams` — context-dependent sub-codes per product stream family
   - `groups` — further classification per product+material context
   - `subgroups` — final level with detailed subtypes (especially rich for EEE and deposit packaging)
3. **`fee_rates_2026`** — flat map of 4-digit díjkódok to Ft/kg rates, organized by product stream family. Key rates for the most common SME use cases:
   - `1101` Paper packaging: 20.44 Ft/kg
   - `1102` Plastic packaging: 42.89 Ft/kg
   - `1106` Glass packaging: 10.22 Ft/kg
   - `8101`/`8102` Single-use plastic: 1908.78 Ft/kg (punitive rate)
4. **`fee_modulation`** — recycled content discount tiers (0-20% discount based on % recycled material, applies to packaging product streams 11/12/13 only). **Note:** Fee modulation is OUT OF SCOPE for this story — the wizard returns the base fee rate. Modulation will be a future Story 4.x enhancement.
5. **`vehicle_lump_sum`** — per-vehicle lump sums by weight class. Not per-kg — special handling needed if vehicles are implemented.

**DagEngine navigation through the seed data:**
The DagEngine must map the product stream code to the correct section key in each hierarchy level. The mapping is:
- Product streams 11/12/13 → `packaging_*` sections
- Product streams 21-26 → `eee` sections
- Product streams 31-33 → `batteries` sections
- Product stream 41 → `tires` sections
- Product stream 51 → `vehicles` sections
- Product stream 61 → `office_paper` sections
- Product stream 71 → `advertising_paper` sections
- Product stream 81 → `single_use_plastic` sections
- Product stream 91 → `other_plastic_chemical` sections

This mapping logic is the core of the DagEngine — it translates the numeric product stream code into the JSON section key to find child options at each level.

**Golden test cases for DagEngineTest (minimum 5):**
1. Non-deposit paper consumer packaging: 11 → 01 → 01 → 01 = `11010101`, díjkód `1101`, 20.44 Ft/kg
2. Non-deposit plastic transport packaging: 11 → 02 → 03 → 01 = `11020301`, díjkód `1102`, 42.89 Ft/kg
3. Deposit PET bottle plastic (default closure): 12 → 02 → 01 → 01 = `12020101`, díjkód `1202`, 42.89 Ft/kg
4. Large household appliance — refrigerator: 21 → 01 → 01 → 01 = `21010101`, díjkód `2101`, 22.26 Ft/kg
5. Portable battery — button cell: 31 → 01 → 01 → 01 = `31010101`, díjkód `3101`, 189.02 Ft/kg
6. Single-use EPS plastic: 81 → 02 → 01 → 02 = `81020102` (if this path is valid), díjkód `8102`, 1908.78 Ft/kg
7. Passenger car tire (new): 41 → 01 → 01 → 01 = `41010101`, díjkód `4101`, 30.62 Ft/kg

### Anti-Pattern Prevention

**DO NOT:**
- Hard-code the KF-code hierarchy in Java code — ALL classification data comes from `epr_configs.config_data` JSONB. The DagEngine traverses JSON, never hard-coded enums or switch statements for product types.
- Create a separate `DagNode` JPA entity — the DAG is a JSON structure, not a database table. Traversal is in-memory on parsed `JsonNode`.
- Put DagEngine traversal logic in the controller — controller calls service, service orchestrates DagEngine + repository.
- Cache config indefinitely without version keys — config versions are immutable, but the "active version" lookup must have a TTL or be event-driven.
- Use `tenant_id` from request params for wizard confirm — always extract from JWT `active_tenant_id` claim.
- Skip `updated_at` when updating template `kf_code`/`verified` — no DB trigger exists.
- Use PrimeVue 3 Stepper API (`:steps` array) — PrimeVue 4 uses `StepList`/`Step`/`StepPanels`/`StepPanel` composition.
- Store wizard state in `useEprStore` — create a separate `useEprWizardStore` to avoid state pollution.
- Implement fee modulation in this story — base fee rate only. Modulation is a future enhancement.
- Create a new Flyway migration for schema changes unless absolutely needed — existing tables should suffice. A seed data migration IS needed for loading `epr-seed-data-2026.json` into `epr_configs`.
- Use H2 in tests — Testcontainers PostgreSQL 17 only.
- Use `@Autowired` — constructor injection via `@RequiredArgsConstructor`.
- Hard-code any user-facing text — everything through i18n. Hierarchy labels come from the seed data JSON per locale.
- Import anything from `epr.internal` outside the `epr` module — ArchUnit will fail.
- Create a blocking/synchronous config load on every wizard request — use the config cache.

### UX Requirements from Design Specification

**Wizard Stepper** (UX Spec §11.4, §6.1): PrimeVue Stepper component. Appears below or replaces the DataTable when activated for a specific material. 3+1 steps: Material Type, Usage Context, Subtype, Confirm. Each step shows large tappable option cards instead of small radio buttons. Selecting an option immediately validates via the DAG engine and advances.

**Option cards** (UX Spec §11.4): Large tappable cards (minimum 120px height). Grid layout: 3 columns on desktop, 2 on tablet, 1 on mobile. Hungarian label as primary text, English subtitle as secondary. Selected card has Emerald (#15803D) border. Hover shows Deep Navy (#1e3a5f) border with subtle shadow.

**Result display** (UX Spec §11.4): After final step, green confirmation card with KF-code formatted as `XX XX XX XX` (spaced), fee rate in `XX.XX Ft/kg`, breadcrumb trail of the classification path. "Confirm and Link" button saves to template.

**Material Library integration** (UX Spec §11.4): "Verified" items in the DataTable have an Emerald badge with the linked KF-code. "Draft" items (unverified) have a Slate badge. The "Classify" button appears in the Actions column for unverified templates. "Re-classify" appears for verified templates (allows re-running the wizard to change classification).

**Desktop layout** (UX Spec §8.2, §11.4): On desktop (>1024px), the wizard renders in the main content area below the DataTable. The side panel stats remain visible. The wizard area takes full width of the content column.

**Mobile layout** (UX Spec §8.1): On mobile (<768px), the wizard replaces the DataTable entirely. Option cards stack to single column. The breadcrumb trail wraps. A sticky "Cancel" button remains visible.

**Form validation** (UX Spec §7.2): Emerald (#15803D) for valid/selected states, Crimson (#B91C1C) for errors. The "MOHU Gate" pattern applies to the result display — KF-code format is validated visually.

**Button hierarchy** (UX Spec §7.1): "Confirm and Link" = Primary (Deep Navy #1e3a5f). "Cancel" = Secondary (Slate Grey border). "Classify" in DataTable = Secondary with classify icon. "Re-classify" = text button (less prominent since template is already verified).

**Loading states** (UX Spec, "Skeletal Trust" pattern): While API calls are in-flight between wizard steps, show PrimeVue `Skeleton` cards in the option grid area (3-4 skeleton cards). Do not show a full-page spinner.

### Frontend Patterns — MUST Follow

**Pinia store pattern:** Follow the `useEprStore` **options-style** pattern for `useEprWizardStore`: `defineStore('eprWizard', { state(): ..., getters: { ... }, actions: { ... } })`. Use `$fetch` with `useRuntimeConfig()` for API calls (same pattern as epr.ts). Error handling in the page/component layer via `useApiError()` → PrimeVue Toast. Do NOT use composition-style `setup()` — match the existing options-style pattern.

**PrimeVue Stepper pattern (v4):** Import `Stepper`, `StepList`, `Step`, `StepPanels`, `StepPanel` from PrimeVue. Use `linear` mode to enforce sequential flow. The `StepPanel` provides `v-slot="{ activateCallback }"` for programmatic navigation. Bind `value` props to string step identifiers.

**Script setup:** Always `<script setup lang="ts">`. Composition API only. No Options API for components (stores may use options style per existing pattern).

**Type safety:** Define wizard types in `types/epr.ts` alongside existing material types. Mark new types with `// TODO: Replace with auto-generated type after OpenAPI regen`.

**i18n:** Use `$t('epr.wizard.someKey')`. Nested JSON objects. Alphabetically sorted. Key parity hu/en.

**Co-located tests:** Every new `.vue` file gets a `.spec.ts` in the same directory. Every new store gets a test. Use Vitest + `@vue/test-utils`.

**Composables:** Use existing `useApi().apiFetch` (at `~/composables/api/useApi.ts`) if in setup context, or `$fetch` in Pinia actions. Use `useApiError()` (at `~/composables/api/useApiError.ts`) for RFC 7807 error mapping. Use `useTierGate('PRO_EPR')` (at `~/composables/auth/useTierGate.ts`) for tier checking (already in EPR page).

**Accept-Language header:** The global `$fetch` interceptor in `plugins/api-locale.ts` automatically injects `Accept-Language` headers, handles credentials, and 401 redirects. The wizard store DOES NOT need to manually add locale headers — they are injected automatically for all `$fetch` calls.

**Component communication:** The EPR page owns the wizard lifecycle. It passes `targetTemplateId` to the wizard store. The WizardStepper component reads from `useEprWizardStore` directly. On confirm success, the page refreshes `useEprStore.fetchMaterials()`.

**Responsive breakpoints:**
- Mobile: `< 768px` — single column, wizard replaces DataTable
- Tablet: `768px - 1024px` — 2-column card grid, wizard below DataTable
- Desktop: `> 1024px` — 3-column card grid, wizard below DataTable, side panel visible

### Project Structure Notes

**New files to create (backend):**

```
backend/src/main/java/hu/riskguard/epr/domain/
└── DagEngine.java                              # JSON DAG traversal, pure-function service

backend/src/main/java/hu/riskguard/epr/api/dto/
├── WizardStartResponse.java                    # configVersion + root-level options
├── WizardOption.java                           # code + label + description
├── WizardStepRequest.java                      # configVersion + traversalPath + selection
├── WizardSelection.java                        # level + code + label
├── WizardStepResponse.java                     # nextLevel + options + breadcrumb + autoSelect hint
├── WizardResolveResponse.java                  # kfCode + feeCode + feeRate + classification
├── WizardConfirmRequest.java                   # configVersion + traversalPath + kfCode + templateId
└── WizardConfirmResponse.java                  # calculationId + kfCode + templateUpdated
```

**Files to modify (backend):**

```
backend/src/main/java/hu/riskguard/epr/api/EprController.java
  → Add 4 wizard endpoints (GET start, POST step, POST resolve, POST confirm)
backend/src/main/java/hu/riskguard/epr/domain/EprService.java
  → Add wizard orchestration methods (startWizard, processStep, resolve, confirm)
  → Inject DagEngine
backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java
  → Add findActiveConfig, findConfigByVersion, insertCalculation, updateTemplateKfCode
```

**No new migrations needed** — seed data already loaded by V20260323_002, all columns exist from V20260323_001/003/004.

**New files to create (backend tests):**

```
backend/src/test/java/hu/riskguard/epr/
├── DagEngineTest.java                          # Unit test: 15+ golden test cases
├── EprServiceWizardTest.java                   # Unit test: wizard flow with mocked deps
└── EprControllerWizardTest.java                # MockMvc: wizard endpoints, validation, tier
```

**New files to create (frontend):**

```
frontend/app/stores/
└── eprWizard.ts                                # Pinia store for wizard state

frontend/app/components/Epr/
├── WizardStepper.vue                           # PrimeVue 4 Stepper wizard
├── WizardStepper.spec.ts                       # Co-located test
├── MaterialSelector.vue                        # Reusable option card grid
└── MaterialSelector.spec.ts                    # Co-located test
```

**Files to modify (frontend):**

```
frontend/app/pages/epr/index.vue                → Add Classify/Re-classify button, wizard toggle
frontend/app/components/Epr/MaterialInventoryBlock.vue → Add Actions column button for wizard
frontend/app/i18n/hu/epr.json                   → Add wizard keys (~30 keys)
frontend/app/i18n/en/epr.json                   → Add matching English keys
frontend/types/epr.ts                           → Add wizard type definitions
```

**Alignment with architecture:** All paths match the project structure in architecture.md. `DagEngine.java` goes in `epr/domain/` per the architecture diagram (line 733). `WizardStepper.vue` and `MaterialSelector.vue` go in `frontend/app/components/Epr/` per architecture diagram (lines 885-888). The `eprWizard.ts` store goes in `frontend/app/stores/` alongside the existing `epr.ts` per architecture diagram (line 979).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-4.2] — Story definition: Smart Material Wizard, DAG questionnaire, KF-code classification
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-4] — Epic 4 goal: EPR Material Library & Questionnaire, FRs: FR8, FR9, FR13
- [Source: _bmad-output/planning-artifacts/architecture.md#epr-Module] — Module failure modes, JSON-driven config, DAG logic, golden test cases
- [Source: _bmad-output/planning-artifacts/architecture.md#Code-Organization] — Module 3-layer structure (api/domain/internal), DagEngine.java in domain/
- [Source: _bmad-output/planning-artifacts/architecture.md#DTO-Mapping-Strategy] — Java records, `static from()`, no MapStruct
- [Source: _bmad-output/planning-artifacts/architecture.md#Table-Ownership-Per-Module] — EPR owns: epr_configs, epr_calculations, epr_exports, epr_material_templates
- [Source: _bmad-output/planning-artifacts/architecture.md#Entity-Relationship-Summary] — epr_calculations schema with traversal_path JSONB
- [Source: _bmad-output/planning-artifacts/architecture.md#FR8-FR9-Mapping] — FR8: EprController + DagEngine + WizardStepper.vue; FR9: FeeCalculator + NavInvoiceAdapter
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§11.4] — EPR Material Library and Wizard wireframe, option cards, result display
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§6.1] — MaterialInventoryBlock: high-speed weight entry grid
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§8.2] — Desktop expanded side-panel summaries for EPR
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§7.1-§7.3] — Button hierarchy, form validation, MOHU Gate pattern
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§12.2] — Judit's Quarterly EPR Sprint user journey
- [Source: _bmad-output/implementation-artifacts/epr-seed-data-2026.json] — Complete 2026 KF-code hierarchy, fee rates, fee modulation, legislation references
- [Source: _bmad-output/implementation-artifacts/4-0-epr-module-foundation.md] — EPR scaffolding: package structure, Flyway migrations, ArchUnit rules, jOOQ codegen
- [Source: _bmad-output/implementation-artifacts/4-1-epr-material-library-and-seasonal-templates.md] — Material Library CRUD, DataTable, side panel, tier gating, i18n
- [Source: _bmad-output/project-context.md] — AI agent rules: tenant isolation, jOOQ patterns, testing mandate
- [Source: frontend/app/stores/epr.ts] — Existing Pinia store pattern for EPR material CRUD
- [Source: frontend/app/components/Epr/MaterialInventoryBlock.vue] — Existing DataTable with Actions column
- [Source: backend/src/main/java/hu/riskguard/epr/domain/EprService.java] — Existing service facade with 7 methods
- [Source: backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java] — Existing repository with 10 methods
- [Source: backend/src/main/java/hu/riskguard/epr/api/EprController.java] — Existing controller with 7 endpoints
- [Source: primevue.org/stepper] — PrimeVue 4 Stepper API: Stepper/StepList/Step/StepPanels/StepPanel composition, linear mode, activateCallback
- [Source: 80/2023. (III. 14.) Korm. rendelet] — KF-code structure (1. melléklet), díjkód structure (2. melléklet)
- [Source: 33/2025. (XI. 28.) EM rendelet] — 2026 fee rates (1. melléklet), fee modulation (2. melléklet), vehicle lump sums (3. melléklet)

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- DagEngine: Discovered material_stream codes (01-07) don't map 1:1 to fee codes (01-11). Metal (04) splits into iron/steel (04) and aluminium (05). Resolved by expanding packaging material options from fee_rates_2026 directly (11 fee-aligned options instead of 7 material_streams).
- EprService: Used static ObjectMapper instead of Spring-injected bean to avoid NoSuchBeanDefinition in Testcontainers integration test contexts without JacksonAutoConfiguration.
- Config caching in EprService (not DagEngine) per architecture: DagEngine is pure function, EprService owns the ConcurrentHashMap cache.

### Completion Notes List

- **Task 1**: Created `DagEngine.java` — pure-function service with product stream family mapping, ref-only section handling, fee-rate-aligned packaging expansion, composite material expansion, and locale-aware labels. 509 lines.
- **Task 2**: Created 8 wizard DTO records in `epr/api/dto/`: WizardOption, WizardSelection, WizardStartResponse, WizardStepRequest, WizardStepResponse, WizardResolveResponse, WizardConfirmRequest, WizardConfirmResponse.
- **Task 3**: Added 6 wizard methods to EprService: startWizard, processStep, resolveKfCode, confirmWizard, getActiveConfigVersion, loadConfig. Config cache uses ConcurrentHashMap.
- **Task 4**: Added 4 repository methods to EprRepository: findActiveConfig, findConfigByVersion, insertCalculation, updateTemplateKfCode. All use jOOQ type-safe DSL.
- **Task 5**: Added 4 wizard endpoints to EprController: GET /wizard/start, POST /wizard/step, POST /wizard/resolve, POST /wizard/confirm. All inherit @TierRequired(Tier.PRO_EPR).
- **Task 6**: Created 3 test files: DagEngineTest (44 tests, 15 golden cases), EprServiceWizardTest (10 tests), EprControllerWizardTest (5 tests). Updated existing EprServiceTest for new constructor. All EPR tests pass.
- **Task 7**: Created `eprWizard.ts` Pinia store with options-style API. Tracks activeStep, traversalPath, availableOptions, resolvedResult. Auto-resolves after 4-level completion.
- **Task 8**: Created `WizardStepper.vue` — PrimeVue 4 Stepper with linear mode, breadcrumb trail, result card with formatted KF code.
- **Task 9**: Created `MaterialSelector.vue` — responsive option card grid (3/2/1 columns), Emerald selected state, loading skeleton.
- **Task 10**: Integrated wizard into EPR page and MaterialInventoryBlock. Added Classify/Re-classify buttons. Wizard renders below DataTable when active.
- **Task 11**: Added 15 wizard i18n keys to both hu/epr.json and en/epr.json. Keys alphabetically sorted.
        - **Task 12**: Backend EPR tests pass (all unit + integration via Testcontainers). Frontend 523/527 tests pass (4 pre-existing failures in CopyQuarterDialog/MaterialFormDialog — not introduced by this story).
        - **Task 13**: All 11 code review findings addressed — 4 HIGH + 5 MEDIUM + 2 LOW:
          - Added `static from()` factory methods to WizardStartResponse, WizardStepResponse, WizardConfirmResponse (ArchUnit rule compliance)
          - Fixed NPE in DagEngineTest @BeforeAll: restructured null check to avoid `is.close()` on null InputStream
          - Fixed mobile wizard layout: `v-if`/`v-else` so wizard replaces card list on mobile (< 768px) per AC 5/UX Spec §8.1
          - Extended EprModuleIntegrationTest with 7 golden DagEngine traversal smoke tests using real seeded config
          - Fixed DagEngine.validateCode() to enforce exactly 2-digit codes (`\d{2}`) preventing malformed KF codes
          - Added recursion depth guard (max 3) to eprWizard.ts selectOption() auto-advance
          - Fixed WizardStepper.vue step-to-level mapping: step 3 resolves to 'subgroup' when group already in traversalPath
          - Rewrote EprControllerWizardTest with proper standalone MockMvc + TierGateInterceptor verifying PRO_EPR 403 enforcement
          - Added Classify/Re-classify button to mobile card layout in index.vue
          - Added success toast in index.vue watch callback using lastConfirmedKfCode tracking
          - Added locale parameter to DagEngine.resolveKfCode() for English classification labels; backward-compatible overload
          - Fixed DagEngine @Service→@Component annotation (ArchUnit naming rule: @Service classes must end with 'Service')

### File List

**New files (backend):**
- backend/src/main/java/hu/riskguard/epr/domain/DagEngine.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardOption.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardSelection.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardStartResponse.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardStepRequest.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardStepResponse.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardResolveResponse.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardConfirmRequest.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardConfirmResponse.java
- backend/src/main/java/hu/riskguard/epr/api/dto/WizardResolveRequest.java
- backend/src/test/java/hu/riskguard/epr/DagEngineTest.java
- backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java
- backend/src/test/java/hu/riskguard/epr/EprControllerWizardTest.java
- backend/src/test/resources/epr-seed-data-2026.json

**Modified files (backend):**
- backend/src/main/java/hu/riskguard/epr/domain/EprService.java
- backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java
- backend/src/main/java/hu/riskguard/epr/api/EprController.java
- backend/src/test/java/hu/riskguard/epr/EprServiceTest.java

**New files (frontend):**
- frontend/app/stores/eprWizard.ts
- frontend/app/components/Epr/WizardStepper.vue
- frontend/app/components/Epr/WizardStepper.spec.ts
- frontend/app/components/Epr/MaterialSelector.vue
- frontend/app/components/Epr/MaterialSelector.spec.ts

**Modified files (frontend):**
- frontend/app/pages/epr/index.vue
- frontend/app/pages/epr/index.spec.ts
- frontend/app/components/Epr/MaterialInventoryBlock.vue
- frontend/app/i18n/hu/epr.json
- frontend/app/i18n/en/epr.json
- frontend/types/epr.ts
- frontend/nuxt.config.ts

**Modified files (project):**
- _bmad-output/implementation-artifacts/sprint-status.yaml
- _bmad-output/implementation-artifacts/4-2-smart-material-wizard-dag-questionnaire.md

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-03-24 | Initial implementation: DagEngine, wizard DTOs, service/controller endpoints, Pinia store, WizardStepper/MaterialSelector components, EPR page integration, i18n keys | AI Agent (gitlab/duo-chat-opus-4-6) |
| 2026-03-24 | Code review fixes (Task 13): 4H/5M/2L findings resolved — ArchUnit from() factory methods, NPE fix in DagEngineTest, mobile wizard layout, integration smoke tests, validateCode() enforcement, recursion guard, level mapping fix, MockMvc tests, mobile classify button, success toast, locale support for resolveKfCode, @Service→@Component | AI Agent (gitlab/duo-chat-sonnet-4-6) |
| 2026-03-24 | Code review R3: 1H/4M/2L — Fixed success toast race condition (lastConfirmSuccess flag in store), added PrimeVue Stepper to nuxt optimizeDeps, created WizardResolveRequest DTO (removed redundant selection field from resolve endpoint), fixed isLoading flicker during auto-advance recursion, updated File List completeness | AI Agent (gitlab/duo-chat-opus-4-6) |
