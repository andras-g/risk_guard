# Story 6.3: Hot-Swappable EPR JSON Manager

Status: done

## Story

As an Admin,
I want to edit the EPR fee tables and KF-code logic via a browser-based editor,
so that I can comply with legislation changes in seconds without a redeployment.

## Acceptance Criteria

<!-- SECTION: acceptance_criteria -->

1. **Admin EPR Config page loads current active config**
   - Given an authenticated `SME_ADMIN` user
   - When they navigate to `/admin/epr-config`
   - Then the page displays the current active `epr_configs` JSON in a Monaco code editor (read/write mode)
   - And shows the current version number and `activated_at` timestamp
   - And non-`SME_ADMIN` callers receive HTTP 403 from the backend APIs

2. **Validate button runs EPR Golden Test Cases**
   - Given the admin has edited the JSON in the editor
   - When they click "Validate"
   - Then the frontend calls `POST /api/v1/admin/epr/config/validate` with the current editor content
   - And the backend parses the JSON, runs the 5 hard-coded EPR Golden Test Cases using `DagEngine.resolveKfCode()`
   - And returns `{valid: true, errors: []}` if all cases pass
   - And returns `{valid: false, errors: ["Test 1 FAILED: expected kfCode=11010101, got null", ...]}` listing each failure
   - And malformed JSON (not parseable) returns `{valid: false, errors: ["Invalid JSON: <parse error message>"]}`
   - And the frontend displays the validation result: green success panel or red error list

3. **Publish button activates new config version**
   - Given validation has passed (Publish button is enabled only after successful validation)
   - When the admin clicks "Publish"
   - Then the frontend calls `POST /api/v1/admin/epr/config/publish` with the editor content
   - And the backend atomically: inserts a new `epr_configs` row with `version = MAX(version) + 1` and `activated_at = now()`, then logs to `admin_action_log`
   - And returns `{version: <new_version>, activatedAt: <timestamp>}`
   - And the frontend shows a success toast: "EPR Config v{N} published"
   - And the page header updates to reflect the new version

4. **Old calculations retain config version reference**
   - Given existing `epr_calculations` rows reference `config_version = 1`
   - When a new config version 2 is published
   - Then `epr_calculations` rows still reference `config_version = 1` (FK constraint `config_version INT NOT NULL REFERENCES epr_configs(version)` is satisfied by the old row)
   - And the `EprService` config cache will return version 1 data for those old calculations
   - And new wizard sessions start with `getActiveConfigVersion()` which now returns version 2

5. **EPR Service cache handles new published version**
   - Given `EprService.configCache` is a `ConcurrentHashMap<Integer, JsonNode>`
   - When version 2 is published, the new version is NOT pre-loaded into the cache
   - Then `getActiveConfigVersion()` calls `findActiveConfig()` which queries the DB directly — no caching — correctly returning version 2 immediately after publish
   - And the first wizard session using version 2 will load and cache it on demand via `loadConfig(2)`
   - NOTE: Publishing does NOT need to evict the cache — new version is just a new key

6. **Audit trail logged**
   - Given a successful publish
   - When the action completes
   - Then a row is inserted into `admin_action_log`: `action="PUBLISH_EPR_CONFIG"`, `target="epr_config:v<N>"`, `details={"version": N, "previous_version": N-1}`, `actor_user_id=<JWT user_id claim>`

<!-- END SECTION -->

## Tasks / Subtasks

<!-- SECTION: tasks -->

- [x] Task 1 — Backend: New DTOs (AC: #1, #2, #3)
  - [x] Create `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigResponse.java`
    - Java record: `(int version, String configData, java.time.Instant activatedAt)`
    - Add `static from(org.jooq.Record r)` factory: `new EprConfigResponse(r.get("version", Integer.class), ((JSONB) r.get("config_data")).data(), r.get("activated_at", java.time.OffsetDateTime.class).toInstant())`
  - [x] Create `EprConfigValidateRequest.java` — record: `(String configData)`
  - [x] Create `EprConfigValidateResponse.java` — record: `(boolean valid, java.util.List<String> errors)`
    - Add factory: `static ok() { return new EprConfigValidateResponse(true, List.of()); }`
    - Add factory: `static failed(List<String> errors) { return new EprConfigValidateResponse(false, errors); }`
  - [x] Create `EprConfigPublishRequest.java` — record: `(String configData)`
  - [x] Create `EprConfigPublishResponse.java` — record: `(int version, java.time.Instant activatedAt)`

- [x] Task 2 — Backend: `EprConfigValidator` domain service (AC: #2)
  - [x] Create `backend/src/main/java/hu/riskguard/epr/domain/EprConfigValidator.java`
  - [x] Annotate: `@Component @RequiredArgsConstructor`
  - [x] Inject `DagEngine dagEngine`
  - [x] Define inner record: `record GoldenTestCase(String name, List<WizardSelection> path, String expectedKfCode, java.math.BigDecimal expectedFeeRateHufPerKg)`
  - [x] Define `GOLDEN_CASES` as a static final list of 5 test cases (see Dev Notes §Golden Test Cases for exact values)
  - [x] Method `validate(String configData)` → `EprConfigValidateResponse`:
    1. Try `OBJECT_MAPPER.readTree(configData)` — on JsonProcessingException return `failed(List.of("Invalid JSON: " + e.getMessage()))`
    2. For each golden case: call `dagEngine.resolveKfCode(configNode, productStream, materialStream, group, subgroup, "hu")`
    3. Compare `resolution.kfCode()` with `expectedKfCode` — if mismatch, add error string
    4. Compare `resolution.feeRateHufPerKg()` with `expectedFeeRateHufPerKg` (BigDecimal.compareTo) — if mismatch, add error string
    5. If errors list is empty → `ok()`, else → `failed(errors)`
  - [x] Add `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`
  - [x] Check `DagEngine.KfCodeResolution` record for exact field names before implementing (see `DagEngine.java` for the return type)

- [x] Task 3 — Backend: `EprRepository` additions (AC: #3, #6)
  - [x] Add `getMaxConfigVersion()` → `int`:
    - `return dsl.select(DSL.max(EPR_CONFIGS.VERSION)).from(EPR_CONFIGS).fetchOne(0, Integer.class);`
    - Return 0 if null (empty table edge case)
  - [x] Add `insertConfig(int version, String configData)`:
    - INSERT into `epr_configs` with `id=UUID.randomUUID()`, `version`, `config_data=JSONB.jsonb(configData)`, `schema_version=null`, `schema_verified=false`, `created_at=now()`, `activated_at=now()`
    - `activated_at = now()` because this story does not have a "draft" concept — publish immediately activates
  - [x] Add `insertAdminActionLog(UUID actorUserId, String action, String target, String details)`:
    - Use raw SQL string (jOOQ scoped codegen does not generate `ADMIN_ACTION_LOG` in the EPR module scope):
    ```java
    dsl.execute(
        "INSERT INTO admin_action_log (actor_user_id, action, target, details, performed_at) VALUES (?, ?, ?, ?::jsonb, ?)",
        actorUserId, action, target, details, java.time.Instant.now()
    );
    ```

- [x] Task 4 — Backend: `EprService` additions (AC: #1, #2, #3, #4, #5)
  - [x] Inject `EprConfigValidator eprConfigValidator`
  - [x] Add `getActiveConfigFull()` → `EprConfigResponse`:
    - `return eprRepository.findActiveConfig().map(EprConfigResponse::from).orElseThrow(() -> new IllegalStateException("No active EPR config found"))`
  - [x] Add `validateNewConfig(String configData)` → `EprConfigValidateResponse`:
    - `return eprConfigValidator.validate(configData)`
  - [x] Add `@Transactional publishNewConfig(String configData, UUID actorUserId)` → `EprConfigPublishResponse`:
    1. `int previousVersion = getActiveConfigVersion()`
    2. `int newVersion = eprRepository.getMaxConfigVersion() + 1`
    3. `eprRepository.insertConfig(newVersion, configData)`
    4. `String details = "{\"version\":" + newVersion + ",\"previous_version\":" + previousVersion + "}"`
    5. `eprRepository.insertAdminActionLog(actorUserId, "PUBLISH_EPR_CONFIG", "epr_config:v" + newVersion, details)`
    6. `return new EprConfigPublishResponse(newVersion, Instant.now())`
    - NOTE: Do NOT evict or pre-populate `configCache` — new version loaded on-demand by first wizard call

- [x] Task 5 — Backend: `EprAdminController` (AC: #1, #2, #3, #6)
  - [x] Create `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java`
  - [x] Annotate: `@RestController @RequestMapping("/api/v1/admin/epr") @RequiredArgsConstructor`
  - [x] Inject: `EprService eprService`
  - [x] Implement `requireAdminRole(Jwt jwt)` — same pattern as `DataSourceAdminController`: check `jwt.getClaimAsString("role")` equals `"SME_ADMIN"`, throw `ResponseStatusException(FORBIDDEN, "Admin access required")` if not
  - [x] `GET /config` → `EprConfigResponse getConfig(@AuthenticationPrincipal Jwt jwt)`:
    - `requireAdminRole(jwt); return eprService.getActiveConfigFull();`
  - [x] `POST /config/validate` → `EprConfigValidateResponse validate(@RequestBody EprConfigValidateRequest req, @AuthenticationPrincipal Jwt jwt)`:
    - `requireAdminRole(jwt); return eprService.validateNewConfig(req.configData());`
  - [x] `POST /config/publish` → `EprConfigPublishResponse publish(@RequestBody EprConfigPublishRequest req, @AuthenticationPrincipal Jwt jwt)`:
    - `requireAdminRole(jwt);`
    - `UUID actorUserId = JwtUtil.requireUuidClaim(jwt, "user_id");`
    - `return eprService.publishNewConfig(req.configData(), actorUserId);`
  - [x] Import: `import hu.riskguard.core.util.JwtUtil;`

- [x] Task 6 — Backend: `EprAdminControllerTest` (AC: #1, #2, #3)
  - [x] Create `backend/src/test/java/hu/riskguard/epr/EprAdminControllerTest.java`
  - [x] Follow `DataSourceAdminControllerTest` pattern: `@ExtendWith(MockitoExtension.class)`, `@MockitoSettings(strictness = LENIENT)`, mock `EprService`
  - [x] Build JWT via `TestJwtBuilder.smeAdmin()` and `TestJwtBuilder.proEprUser()` — check existing test utilities for JWT builder pattern
  - [x] Tests:
    - `getConfig_smeAdmin_returns200WithVersionAndConfigData`
    - `getConfig_nonAdmin_returns403`
    - `validate_validJson_returns200Valid`
    - `validate_invalidJson_returns200WithErrors`
    - `validate_nonAdmin_returns403`
    - `publish_smeAdmin_returns200WithNewVersion`
    - `publish_nonAdmin_returns403`

- [x] Task 7 — Backend: Verify `NamingConventionTest` passes
  - [x] New DTOs must be in `hu.riskguard.epr.api.dto.*` — correct package
  - [x] `EprConfigValidator` must be in `hu.riskguard.epr.domain.*` — correct package
  - [x] `EprAdminController` must be in `hu.riskguard.epr.api.*` — correct package
  - [x] Run `./gradlew test` to verify no ArchUnit / NamingConventionTest violations

- [x] Task 8 — Frontend: Add Monaco editor dependency
  - [x] `cd frontend && npm install monaco-editor` (version ^0.52.x or latest stable)
  - [x] Create `frontend/app/components/Admin/MonacoEditor.vue` — a `<ClientOnly>`-wrapped dynamic import:
    ```vue
    <template>
      <ClientOnly>
        <div ref="editorContainer" class="h-96 border border-surface-200 rounded" />
        <template #fallback><Skeleton height="24rem" /></template>
      </ClientOnly>
    </template>
    <script setup lang="ts">
    import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
    const props = defineProps<{ modelValue: string; readonly?: boolean }>()
    const emit = defineEmits<{ 'update:modelValue': [string] }>()
    const editorContainer = ref<HTMLElement | null>(null)
    let monacoEditor: import('monaco-editor').editor.IStandaloneCodeEditor | null = null
    onMounted(async () => {
      const monaco = await import('monaco-editor')
      // suppress workers warning in SSR-fallback context
      self.MonacoEnvironment = { getWorker: () => new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker?worker', import.meta.url)) }
      monacoEditor = monaco.editor.create(editorContainer.value!, {
        value: props.modelValue,
        language: 'json',
        theme: 'vs-light',
        automaticLayout: true,
        readOnly: props.readonly ?? false,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        fontSize: 13,
      })
      monacoEditor.onDidChangeModelContent(() => {
        emit('update:modelValue', monacoEditor!.getValue())
      })
    })
    watch(() => props.modelValue, (val) => {
      if (monacoEditor && monacoEditor.getValue() !== val) monacoEditor.setValue(val)
    })
    onBeforeUnmount(() => { monacoEditor?.dispose() })
    </script>
    ```
  - [x] In `nuxt.config.ts`, add to `vite.optimizeDeps.include`: `'monaco-editor'` if needed; also add `ssr: false` to the build config for monaco (or use dynamic import + ClientOnly which already handles it)

- [x] Task 9 — Frontend: i18n keys (AC: #1, #2, #3)
  - [x] Add to `frontend/app/i18n/en/admin.json` under a new `"eprConfig"` namespace:
    ```json
    "eprConfig": {
      "title": "EPR Config Manager",
      "subtitle": "Edit the EPR fee tables and KF-code structure. Validate before publishing.",
      "currentVersion": "Active version: v{version}",
      "activatedAt": "Activated: {date}",
      "validate": "Validate",
      "publish": "Publish",
      "validationPassed": "All {count} golden test cases passed",
      "validationFailed": "Validation failed: {count} error(s)",
      "publishSuccess": "EPR Config v{version} published successfully",
      "publishConfirm": "Publish this config as the new active version?",
      "errors": {
        "loadFailed": "Failed to load active EPR config",
        "validateFailed": "Validation request failed",
        "publishFailed": "Publish failed"
      }
    }
    ```
  - [x] Add equivalent HU translations in `frontend/app/i18n/hu/admin.json`

- [x] Task 10 — Frontend: `pages/admin/epr-config.vue` (AC: #1, #2, #3)
  - [x] Create `frontend/app/pages/admin/epr-config.vue`
  - [x] Use `useRuntimeConfig()` for `apiBase`, `useFetch` or `$fetch` directly (no new Pinia store needed — simple enough for local ref state)
  - [x] State: `configData: ref('')`, `version: ref(0)`, `activatedAt: ref('')`, `validationResult: ref<{valid: boolean, errors: string[]} | null>(null)`, `publishing: ref(false)`, `validating: ref(false)`
  - [x] On `onMounted`: `GET /api/v1/admin/epr/config` → populate `configData`, `version`, `activatedAt`
  - [x] `handleValidate()`: POST to `/api/v1/admin/epr/config/validate` with `{configData: configData.value}`, set `validationResult`
  - [x] `handlePublish()`: POST to `/api/v1/admin/epr/config/publish` with `{configData: configData.value}`, on success update `version`, show success toast, reset `validationResult`
  - [x] Publish button disabled unless `validationResult?.valid === true`
  - [x] Include `<MonacoEditor v-model="configData" />` (from Task 8)
  - [x] Show current version badge and activatedAt timestamp in page header
  - [x] Show `<Panel>` with green color for validation success, red for failure with error list
  - [x] Use `useToast()` from PrimeVue for success/error notifications
  - [x] Role guard: check `identityStore.user?.role === 'SME_ADMIN'` — redirect to `/` if not admin (consistent with other admin pages)
  - [x] Follow `pages/admin/datasources.vue` for layout/structure patterns

- [x] Task 11 — Frontend: Admin navigation (AC: #1)
  - [x] Add "EPR Config" nav item in admin sidebar/navigation component — check existing admin nav component (likely `components/Common/AppSidebar.vue` or similar)
  - [x] Link to `/admin/epr-config`
  - [x] Only visible for `SME_ADMIN` role (check existing admin nav role guard pattern)

- [x] Task 12 — Frontend: Tests (AC: #2, #3)
  - [x] Create `frontend/app/pages/admin/epr-config.spec.ts`
  - [x] Mock `$fetch` for GET config, POST validate, POST publish
  - [x] Test: page loads and displays version from GET response
  - [x] Test: Validate button calls POST /validate and shows success panel on `{valid: true}`
  - [x] Test: Validate button shows error list on `{valid: false, errors: [...]}`
  - [x] Test: Publish button disabled before validation passes
  - [x] Test: Publish button calls POST /publish and shows success toast
  - [x] Stub `MonacoEditor` component (it requires DOM/browser APIs, mock as `<div data-testid="monaco-editor" />`)

- [x] Task 13 — Final verification
  - [x] Backend: `./gradlew test` — BUILD SUCCESSFUL, 0 failures (expected ~690+ tests)
  - [x] Frontend: `vitest run` — all green (expected ~600+ tests)
  - [x] NamingConventionTest: no violations
  - [x] Manual smoke: `GET /api/v1/admin/epr/config` returns version 1 config JSON

### Review Findings

- [x] [Review][Patch] P1: Remove unused `EprConfigValidateResponse.from()` dead factory method [EprConfigValidateResponse.java] — Fixed.
- [x] [Review][Patch] P2: Remove unused `EprConfigPublishResponse.from()` dead factory method [EprConfigPublishResponse.java] — Fixed.
- [x] [Review][Patch] P3: Remove unused `loading` ref from epr-config.vue (declared and maintained but never bound to template) [epr-config.vue] — Fixed.
- [x] [Review][Defer] D1: Race condition on concurrent publishes — two separate queries for getMaxConfigVersion()/insertConfig() in READ_COMMITTED isolation [EprService.java] — deferred, UNIQUE constraint safeguard prevents data corruption, admin-only low-frequency operation
- [x] [Review][Defer] D2: No config payload size limit — large JSON could cause resource exhaustion [EprAdminController.java] — deferred, pre-existing design gap
- [x] [Review][Defer] D3: `EprConfigResponse.from()` returns null activatedAt while record type is declared non-nullable — unreachable via findActiveConfig() [EprConfigResponse.java] — deferred, unreachable in practice via the only caller
- [x] [Review][Defer] D4: `getActiveConfigFull()` throws IllegalStateException (500) when no active config exists — ResponseStatusException(404) would be more appropriate [EprService.java] — deferred, spec-prescribed, admin-only edge case

<!-- END SECTION -->

## Dev Notes

<!-- SECTION: dev_notes -->

### Backend: EPR Config Schema (existing `epr_configs` table — no new migration needed)

```sql
CREATE TABLE epr_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version INT UNIQUE NOT NULL,
    config_data JSONB NOT NULL,
    schema_version VARCHAR(50),
    schema_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    activated_at TIMESTAMPTZ       -- NULL = draft, NOT NULL = active
);
```

- Active config = highest `version` where `activated_at IS NOT NULL` (query in `findActiveConfig()`)
- Version 1 is the seeded 2026 KGyfR data from `V20260323_002__seed_epr_fee_tables.sql`
- Published config sets `activated_at = now()` immediately — no draft state in this story
- **No new migration needed** — existing schema supports this story fully

### Backend: Module Boundary — `admin_action_log` from EPR Module

`admin_action_log` was created in `V20260331_002` (datasource module migration). The jOOQ scoped codegen does NOT generate `ADMIN_ACTION_LOG` in the EPR module's scope. Therefore:
- Use `dsl.execute(rawSqlString, params...)` in `EprRepository.insertAdminActionLog()` — this is a raw SQL string, NOT the type-safe jOOQ DSL, so ArchUnit module boundary checks will NOT catch it (they check Java class imports, not SQL strings)
- This is an intentional trade-off: `admin_action_log` is a cross-cutting admin concern; raw SQL is acceptable for an audit-only write path
- Do NOT import any class from `hu.riskguard.datasource.*` in the EPR module — that WOULD violate module isolation

### Backend: `getMaxConfigVersion()` — Version Atomicity

```java
// In EprRepository:
public int getMaxConfigVersion() {
    Integer max = dsl.select(org.jooq.impl.DSL.max(EPR_CONFIGS.VERSION))
            .from(EPR_CONFIGS)
            .fetchOne(0, Integer.class);
    return max != null ? max : 0;
}
```

In `EprService.publishNewConfig()`, the `@Transactional` annotation ensures the MAX + INSERT is atomic at the DB transaction level. Race condition risk is negligible (only SME_ADMIN can publish).

### Backend: Golden Test Cases for `EprConfigValidator`

These 5 cases represent regression coverage across the major EPR product streams. They are derived from the seeded 2026 config (version 1). **Before hard-coding, verify the exact field name for fee rate in `DagEngine.KfCodeResolution`** — check `DagEngine.java` to see if the field is `feeRateHufPerKg()`, `feeRate()`, or similar.

```java
private static final List<GoldenTestCase> GOLDEN_CASES = List.of(
    new GoldenTestCase(
        "Paper/cardboard consumer non-deposit packaging",
        List.of(new WizardSelection("product_stream","11"), new WizardSelection("material_stream","01"),
                new WizardSelection("group","01"), new WizardSelection("subgroup","01")),
        "11010101", new BigDecimal("20.44")),
    new GoldenTestCase(
        "Plastic consumer non-deposit packaging",
        List.of(new WizardSelection("product_stream","11"), new WizardSelection("material_stream","02"),
                new WizardSelection("group","01"), new WizardSelection("subgroup","01")),
        "11020101", new BigDecimal("42.89")),
    new GoldenTestCase(
        "Portable batteries — general purpose",
        List.of(new WizardSelection("product_stream","31"), new WizardSelection("material_stream","01"),
                new WizardSelection("group","01"), new WizardSelection("subgroup","02")),
        "31010102", new BigDecimal("189.02")),
    new GoldenTestCase(
        "Office paper",
        List.of(new WizardSelection("product_stream","61"), new WizardSelection("material_stream","01"),
                new WizardSelection("group","01"), new WizardSelection("subgroup","01")),
        "61010101", new BigDecimal("20.44")),
    new GoldenTestCase(
        "EPS single-use plastic product",
        List.of(new WizardSelection("product_stream","81"), new WizardSelection("material_stream","02"),
                new WizardSelection("group","01"), new WizardSelection("subgroup","02")),
        "81020102", new BigDecimal("1908.78"))
);
```

**CRITICAL:** Check `DagEngineTest.java` for existing test cases to verify these exact traversal paths produce the expected KF codes. If any path doesn't match, adjust the golden cases to match what `DagEngine` actually produces for the seeded config. The existing `EprModuleIntegrationTest.java` may also have full traversal assertions.

### Backend: `EprConfigResponse` factory — JSONB handling

```java
// In EprConfigResponse.java:
public static EprConfigResponse from(org.jooq.Record r) {
    Object raw = r.get("config_data");
    String configJson = (raw instanceof JSONB jsonb) ? jsonb.data() : raw.toString();
    OffsetDateTime activatedAt = r.get("activated_at", OffsetDateTime.class);
    return new EprConfigResponse(
        r.get("version", Integer.class),
        configJson,
        activatedAt != null ? activatedAt.toInstant() : null
    );
}
```

Import: `import org.jooq.JSONB;`

### Backend: Role Check Pattern (reuse, don't reinvent)

Copy `requireAdminRole(Jwt jwt)` from `DataSourceAdminController` into `EprAdminController`. There is no shared base class for this — both controllers duplicate the 3-line method. Do NOT create a base class or shared utility just for these 3 lines.

```java
private void requireAdminRole(Jwt jwt) {
    String role = jwt.getClaimAsString("role");
    if (!"SME_ADMIN".equals(role)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
}
```

### Backend: Test JWT Builder Pattern

Check existing `DataSourceAdminControllerTest` for the JWT builder utility. It uses something like:
```java
Jwt jwt = Jwt.withTokenValue("token")
    .header("alg", "HS256")
    .claim("role", "SME_ADMIN")
    .claim("user_id", UUID.randomUUID().toString())
    .claim("active_tenant_id", UUID.randomUUID().toString())
    .issuedAt(Instant.now())
    .expiresAt(Instant.now().plusSeconds(3600))
    .build();
```
Do NOT use `TestJwtBuilder.smeAdmin()` unless confirmed it exists. Check the existing test first.

### Frontend: Monaco Editor — Nuxt 4 / Vite Compatibility

Monaco editor requires browser globals and Web Workers. Key points:
- **Always wrap in `<ClientOnly>`** — Monaco cannot run SSR
- Use `import('monaco-editor')` as a dynamic import inside `onMounted()` — not at module level
- Monaco workers: the `MonacoEnvironment.getWorker` override in the example uses a `?worker` Vite import — if this causes issues, use `getWorker: () => new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker', import.meta.url))` without the `?worker` suffix (Vite 5 handles both)
- Add `monaco-editor` to `vite.optimizeDeps.exclude` if build warnings appear (Monaco uses dynamic imports internally)
- The `automaticLayout: true` option handles editor resize when container dimensions change
- Use `PrimeVue Skeleton` as the `#fallback` slot content while Monaco loads

### Frontend: No New Pinia Store Needed

This page is self-contained (admin-only, single-purpose). Use local `ref()` state within the component. No global state is shared with other components. Avoid creating a Pinia store for single-page state.

### Frontend: Admin Page Layout Pattern

Check `pages/admin/datasources.vue` for the admin page structure:
- Same `SME_ADMIN` role guard at page level (`identityStore.user?.role === 'SME_ADMIN'`)
- Same title/subtitle pattern
- Use `<div class="p-6">` outer container consistent with other admin pages

### Frontend: Publish Confirmation

The epic acceptance criteria does not mandate a confirmation dialog before publish. Do NOT add one — keep it simple: validate → see results → click Publish. This matches the "no confirmation dialog for quarantine toggle" precedent (Story 6.2 W3 deferred item).

### Frontend: Frontend Test Stub for Monaco

In the Vitest spec, stub `MonacoEditor` at the module level:
```typescript
vi.mock('~/components/Admin/MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: ['modelValue', 'readonly'],
    template: '<textarea data-testid="monaco-editor" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    emits: ['update:modelValue'],
  }
}))
```

<!-- END SECTION -->

### Project Structure Notes

<!-- SECTION: project_structure -->

| Artifact | Path | New/Modified |
|----------|------|-------------|
| DTO: EprConfigResponse | `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigResponse.java` | new |
| DTO: EprConfigValidateRequest | `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigValidateRequest.java` | new |
| DTO: EprConfigValidateResponse | `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigValidateResponse.java` | new |
| DTO: EprConfigPublishRequest | `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigPublishRequest.java` | new |
| DTO: EprConfigPublishResponse | `backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigPublishResponse.java` | new |
| Domain: EprConfigValidator | `backend/src/main/java/hu/riskguard/epr/domain/EprConfigValidator.java` | new |
| Repository additions | `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java` | modified |
| Service additions | `backend/src/main/java/hu/riskguard/epr/domain/EprService.java` | modified |
| Controller | `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java` | new |
| Controller test | `backend/src/test/java/hu/riskguard/epr/EprAdminControllerTest.java` | new |
| Monaco component | `frontend/app/components/Admin/MonacoEditor.vue` | new |
| Admin page | `frontend/app/pages/admin/epr-config.vue` | new |
| Page spec | `frontend/app/pages/admin/epr-config.spec.ts` | new |
| EN i18n | `frontend/app/i18n/en/admin.json` | modified |
| HU i18n | `frontend/app/i18n/hu/admin.json` | modified |
| Admin nav component | find existing admin sidebar component | modified |
| package.json | `frontend/package.json` | modified (add monaco-editor) |

<!-- END SECTION -->

### References

<!-- SECTION: references -->

- [Source: _bmad-output/planning-artifacts/epics.md#Story 6.3] — acceptance criteria and epic goal
- [Source: _bmad-output/implementation-artifacts/6-2-manual-adapter-quarantine.md] — previous story: `requireAdminRole()` pattern, `JwtUtil.requireUuidClaim()`, `admin_action_log` schema, `@Transactional` atomic writes, JWT builder pattern in tests, `DataSourceAdminController` as the admin controller template
- [Source: backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java] — existing jOOQ patterns: `EPR_CONFIGS` table constants, `findActiveConfig()`, `findConfigByVersion()`, JSONB handling, `dsl.insertInto().returning()` pattern
- [Source: backend/src/main/java/hu/riskguard/epr/domain/EprService.java] — `configCache`, `loadConfig()`, `getActiveConfigVersion()`, `ObjectMapper` as static final, `@Transactional` usage
- [Source: backend/src/main/java/hu/riskguard/epr/domain/DagEngine.java] — `resolveKfCode()` API and `KfCodeResolution` return type (read this to get exact field names before implementing EprConfigValidator)
- [Source: backend/src/test/java/hu/riskguard/epr/DagEngineTest.java] — existing golden traversal assertions; verify golden test case traversal paths here
- [Source: backend/src/main/java/hu/riskguard/datasource/api/DataSourceAdminController.java] — `requireAdminRole()` implementation to copy
- [Source: backend/src/main/resources/db/migration/V20260323_002__seed_epr_fee_tables.sql] — fee rate values (42.89 plastic, 20.44 paper, 189.02 portable battery, 1908.78 EPS) for golden test validation
- [Source: backend/src/main/resources/db/migration/V20260331_002__add_quarantine_and_admin_action_log.sql] — `admin_action_log` table schema
- [Source: frontend/app/pages/admin/datasources.vue] — admin page layout, role guard, `identityStore` usage
- [Source: frontend/app/components/Admin/DataSourceHealthDashboard.vue] — admin component structure patterns
- [Source: _bmad-output/planning-artifacts/project-context.md] — AI Agent Tool Usage Rules (large file write rules), all technology versions

<!-- END SECTION -->

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- NamingConventionTest `response_dtos_should_have_from_factory` failed: `EprConfigValidateResponse` and `EprConfigPublishResponse` were missing `static from()` methods. Fixed by adding `from(boolean, List)` and `from(int, Instant)` factories.
- `EprServiceTest` and `EprServiceWizardTest` compile error: `EprService` constructor gained `EprConfigValidator` parameter. Fixed by adding `@Mock EprConfigValidator` to both test classes.
- `KfCodeResolution` field: story spec used `feeRateHufPerKg()` but actual field in `DagEngine.java` is `feeRate()` — verified from source and corrected in `EprConfigValidator`.

### Completion Notes List

- 5 new backend DTOs in `hu.riskguard.epr.api.dto` — all records with `from()` factories per NamingConventionTest rule.
- `EprConfigValidator`: 5 golden test cases against seeded 2026 config, using `DagEngine.resolveKfCode()`; null/blank guard added (D1/P1 review fixes).
- `EprRepository`: `getMaxConfigVersion()`, `insertConfig(version, configData, activatedAt)` (timestamp param added — P4 fix), `insertAdminActionLog()` (raw SQL for cross-module audit log, intentional per arch notes).
- `EprService`: `getActiveConfigFull()`, `validateNewConfig()`, `@Transactional publishNewConfig()` — server-side re-validation before insert (D1), single `OffsetDateTime` timestamp for DB and response (P4); no cache eviction, new version loaded on-demand.
- `EprAdminController`: `GET /config`, `POST /config/validate`, `POST /config/publish` — all `SME_ADMIN`-guarded; `@Valid` on request bodies (P1).
- Request DTOs: `@NotBlank` on `configData` in `EprConfigValidateRequest` and `EprConfigPublishRequest` (P1).
- `epr-config.vue`: `watch(configData, ...)` resets `validationResult` on editor change (P2).
- `epr-config.spec.ts`: 2 new tests — header version DOM assertion after publish (P5), non-admin redirect test (P6). Total: 6 tests.
- Backend: 690 tests, BUILD SUCCESSFUL. Frontend: 6/6 spec tests green.

### File List

backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigResponse.java
backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigValidateRequest.java
backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigValidateResponse.java
backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigPublishRequest.java
backend/src/main/java/hu/riskguard/epr/api/dto/EprConfigPublishResponse.java
backend/src/main/java/hu/riskguard/epr/domain/EprConfigValidator.java
backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java
backend/src/main/java/hu/riskguard/epr/domain/EprService.java
backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java
backend/src/test/java/hu/riskguard/epr/EprAdminControllerTest.java
backend/src/test/java/hu/riskguard/epr/EprServiceTest.java
backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java
frontend/app/components/Admin/MonacoEditor.vue
frontend/app/pages/admin/epr-config.vue
frontend/app/pages/admin/epr-config.spec.ts
frontend/app/pages/admin/index.vue
frontend/app/i18n/en/admin.json
frontend/app/i18n/hu/admin.json
frontend/nuxt.config.ts
frontend/package.json

### Review Findings

- [x] [Review][Patch] D1: Server-side re-validation before publish — `publishNewConfig()` should call `eprConfigValidator.validate(configData)` before `insertConfig()`; if not valid, throw 400 with the error list. Prevents any client bypassing the UI guard from storing a broken config. [EprService.java:465]
- [x] [Review][Patch] P1: `configData` null/blank → NPE in `validate()` — `ObjectMapper.readTree(null)` throws `IllegalArgumentException`, not `JsonProcessingException`; the catch block misses it. Add `@Valid` + `@NotBlank` to `EprConfigValidateRequest.configData` (and `EprConfigPublishRequest.configData`) and `@Valid` on the `@RequestBody` params. [EprAdminController.java:49–54]
- [x] [Review][Patch] P2: Stale `validationResult` after editing — Publish button stays enabled after user edits config in Monaco editor post-validation. Add `watch(configData, () => { validationResult.value = null })` in `epr-config.vue`. [epr-config.vue:14–17]
- [x] [Review][Patch] P3: Duplicate `SCHEMA_VERSION` `.set()` in `insertConfig` — `.set(EPR_CONFIGS.SCHEMA_VERSION, (String) null)` appears twice consecutively; remove the duplicate. [EprRepository.java:~261] — code verified clean (no duplicate present in current impl)
- [x] [Review][Patch] P4: Publish response `activatedAt` uses a second `Instant.now()` call — `insertConfig()` sets `ACTIVATED_AT = OffsetDateTime.now()` in the DB row, then `publishNewConfig()` constructs the response with a separate `java.time.Instant.now()`. The two timestamps may differ. Return the timestamp passed to `insertConfig` or returned from the DB. [EprService.java:481]
- [x] [Review][Patch] P5: Frontend test missing assertion for header version update after publish — Test "publish button calls POST /publish and shows success toast" asserts the toast but never checks that `version` in the DOM updates to 2 (AC3: "header updates to reflect new version"). [epr-config.spec.ts:151–174]
- [x] [Review][Patch] P6: No test covering role-guard redirect — `mockUserRole` and `mockRouterReplace` infrastructure exists in the spec but no test exercises the non-SME_ADMIN → redirect path. [epr-config.spec.ts]
- [x] [Review][Defer] W1: Concurrent publish race — spec explicitly accepts negligible risk (SME_ADMIN only, UNIQUE constraint on `version` acts as hard stop with 500); no `SELECT FOR UPDATE` needed per design decision. [EprRepository.java, EprService.java] — deferred, pre-existing design decision
- [x] [Review][Defer] W2: First-ever publish when no active config exists → `IllegalStateException` — in practice impossible; v1 is always seeded by migration `V20260323_002`. — deferred, pre-existing
- [x] [Review][Defer] W3: `details` JSON built by string concatenation — safe with int-only values; see also deferred-work W1 from 6-2 (same pattern). — deferred, pre-existing
- [x] [Review][Defer] W4: `activatedAt` displayed raw as ISO string — no locale-aware date formatting applied in template. [epr-config.vue:120–122] — deferred, pre-existing
- [x] [Review][Defer] W5: Frontend role guard may false-redirect SME_ADMIN when `identityStore.user` is null (store not hydrated on hard refresh). [epr-config.vue:23] — deferred, pre-existing pattern across all admin pages
- [x] [Review][Defer] W6: `admin.eprConfig.publishConfirm` i18n key is dead — spec explicitly prohibits a confirmation dialog. Key present in both EN/HU files with no usage. — deferred, pre-existing
- [x] [Review][Defer] W7: `getActiveConfigFull()` and `getActiveConfigVersion()` not annotated `@Transactional(readOnly=true)`. — deferred, pre-existing
- [x] [Review][Defer] W8: `MonacoEditor` overwrites `self.MonacoEnvironment` on every mount — only editor.worker registered. — deferred, pre-existing
- [x] [Review][Defer] W9: Monaco `setValue` in `watch` resets cursor/undo history on external model updates. — deferred, pre-existing

## Change Log

- 2026-03-31: Story created — Hot-Swappable EPR JSON Manager with Monaco editor, validate/publish endpoints, golden test case validation, admin audit logging.
- 2026-03-31: Story implemented — All 13 tasks complete. Backend: 690 tests pass. Frontend: 599 tests pass.
- 2026-03-31: Addressed code review findings — 7 items resolved (D1: server-side re-validation, P1: @NotBlank + null guard, P2: watch stale validationResult, P3: verified no duplicate, P4: single timestamp, P5: DOM version assertion, P6: redirect test). Backend BUILD SUCCESSFUL. Frontend: 6/6 tests green.
