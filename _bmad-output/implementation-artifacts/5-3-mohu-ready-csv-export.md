# Story 5.3: MOHU-Ready CSV Export

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a User,
I want to generate a CSV file that matches the exact schema required by the MOHU portal,
So that I can complete my quarterly filing with zero manual edits.

## Acceptance Criteria

1. **Given** a completed EPR Filing calculation (`filingStore.serverResult` is not null on `/epr/filing`),
   **When** I click "Export for MOHU",
   **Then** the backend generates a CSV file using semicolon delimiters and UTF-8 BOM prefix, returned as a file download.

2. **And** the file columns match the MOHU schema for config_version 1 (schema_version "2026.1"):
   `KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)` — with comma as decimal separator for kg values (Hungarian locale), integers for HUF fee amounts.

3. **And** the export is logged in the `epr_exports` table with: `tenant_id`, `config_version` (from `FilingCalculationResponse.configVersion`), `export_format = 'CSV'`, `file_hash` (hex-encoded SHA-256 of the raw CSV bytes), and `calculation_id = NULL` (bulk filing export — no single calculation row).

4. **And** if the export generation fails (e.g., missing template data, backend error), the user sees a localized error toast identifying the issue and no corrupt file is downloaded to the browser.

---

## Tasks / Subtasks

- [x] Task 1 — Backend: Create `MohuExporter.java` (AC: 1, 2)
  - [x] 1.1 Create `backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java` as `@Component` annotated with `@ExportLocale("hu")`
  - [x] 1.2 Implement `byte[] generate(List<ExportLineRequest> lines)` method:
    - Prepend UTF-8 BOM (3 bytes: `0xEF 0xBB 0xBF`) before the first character
    - Header row: `KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)`
    - One data row per line using `String.format`: `%s;%s;%d;%s;%d` where kg uses `String.format("%.6f", totalWeightKg).replace('.', ',')`
    - Encode full output as `UTF-8` bytes AFTER prepending BOM bytes
    - No trailing newline after last row
  - [x] 1.3 Add CSV column header keys to `backend/src/main/resources/messages_hu.properties` (for `@ExportLocale` documentation alignment — the literal column headers are hardcoded in Hungarian per MOHU spec, not looked up at runtime)

- [x] Task 2 — Backend: Create export DTOs (AC: 1, 2)
  - [x] 2.1 Create `backend/src/main/java/hu/riskguard/epr/api/dto/ExportLineRequest.java` as a Java record:
    ```java
    public record ExportLineRequest(
        @NotNull UUID templateId,
        @NotBlank String kfCode,
        @NotBlank String name,
        @NotNull @Positive int quantityPcs,
        @NotNull BigDecimal totalWeightKg,
        @NotNull BigDecimal feeAmountHuf
    ) {}
    ```
  - [x] 2.2 Create `backend/src/main/java/hu/riskguard/epr/api/dto/MohuExportRequest.java` as a Java record:
    ```java
    public record MohuExportRequest(
        @NotEmpty List<@Valid ExportLineRequest> lines,
        @NotNull int configVersion
    ) {}
    ```

- [x] Task 3 — Backend: `EprRepository.saveExport()` (AC: 3)
  - [x] 3.1 Add `saveExport(UUID tenantId, int configVersion, String fileHash)` to `EprRepository.java`:
    - Add static import for `EPR_EXPORTS` table
    - Add import for `hu.riskguard.jooq.enums.ExportFormatType`
    - Insert row: `tenant_id`, `config_version`, `export_format = ExportFormatType.CSV`, `file_hash`, `exported_at = now()`, `calculation_id = null`

- [x] Task 4 — Backend: `EprService.exportMohuCsv()` (AC: 1, 2, 3, 4)
  - [x] 4.1 Inject `MohuExporter mohuExporter` field into `EprService` (using existing `@RequiredArgsConstructor`)
  - [x] 4.2 Add `public byte[] exportMohuCsv(MohuExportRequest request, UUID tenantId)`:
    - Validate config version matches `getActiveConfigVersion()` — throw `ResponseStatusException(422)` with message `"Config version mismatch"` if not matching
    - Call `mohuExporter.generate(request.lines())`
    - Compute SHA-256 hex of the result bytes using `HashUtil.sha256Hex(bytes)` (check existing `HashUtil.java` in `core/util/`)
    - Call `eprRepository.saveExport(tenantId, request.configVersion(), fileHash)`
    - Return the bytes
  - [x] 4.3 Add `@Transactional` to `exportMohuCsv()` — the DB write and CSV generation are a unit
  - [x] 4.4 Update `EprServiceTest.java` / `EprServiceWizardTest.java` if they construct `EprService` manually — inject `MohuExporter` mock (same fix pattern as FeeCalculator was added in Story 5.2)

- [x] Task 5 — Backend: `EprController.exportMohu()` endpoint (AC: 1, 4)
  - [x] 5.1 Add endpoint to `EprController.java`
  - [x] 5.2 Add `import org.springframework.http.HttpHeaders;` and `import org.springframework.http.ResponseEntity;` to `EprController.java`

- [x] Task 6 — Backend: Tests (AC: 1, 2, 3, 4)
  - [x] 6.1 Create `backend/src/test/java/hu/riskguard/epr/MohuExporterTest.java`:
    - Test 1 — BOM prefix: first 3 bytes of output are `0xEF`, `0xBB`, `0xBF`
    - Test 2 — Header row: line 0 (after BOM) equals `KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)`
    - Test 3 — Golden row: 1000 pcs × 120g × fee → `11010101;Kartondoboz A;1000;120,000000;25800`
    - Test 4 — Rounding: 1 pc × 50g → `11010202;Kis doboz;1;0,050000;11`
    - Test 5 — Multi-row: verify row count = input lines count
    - Test 6 — Empty name containing semicolon: escape with RFC 4180 double-quote wrapping
    - Parse CSV lines using `\uFEFF` BOM stripping before splitting
  - [x] 6.2 Add export endpoint tests to `EprControllerTest.java`:
    - Happy path: 200, Content-Type `text/csv`, body returned
    - Invalid configVersion: 422
    - Empty lines list: Bean Validation `@NotEmpty` rejects
    - Unauthenticated: 401

- [x] Task 7 — Frontend: i18n keys (AC: 1, 4)
  - [x] 7.1 Add to `frontend/app/i18n/en/epr.json` under `epr.filing`
  - [x] 7.2 Add same keys to `frontend/app/i18n/hu/epr.json` under `epr.filing`
  - [x] 7.3 Run `npm run check-i18n` to verify parity — passed

- [x] Task 8 — Frontend: `eprFiling.ts` store — `exportMohu()` action (AC: 1, 3, 4)
  - [x] 8.1 Add `isExporting: boolean` to the `FilingState` interface and initial state (`false`)
  - [x] 8.2 Add `exportMohu()` action with Blob download, anchor click, `URL.revokeObjectURL`
  - [x] 8.3 Update `reset()` action to also reset `isExporting = false`

- [x] Task 9 — Frontend: `filing.vue` — "Export for MOHU" button (AC: 1, 4)
  - [x] 9.1 Import `useToast` from PrimeVue and initialize: `const toast = useToast()`
  - [x] 9.2 Add `handleExport()` async function with error toast on catch
  - [x] 9.3 Add "Export for MOHU" button conditional on `serverResult !== null`
  - [x] 9.4 Place Export button AFTER Calculate button row, BEFORE Summary card

- [x] Task 10 — Frontend: `filing.spec.ts` — Export tests (AC: 1, 4)
  - [x] 10.1 Test: button hidden when serverResult is null
  - [x] 10.2 Test: button visible when serverResult is populated
  - [x] 10.3 Test: button click calls exportMohu() action
  - [x] 10.4 Test: export error shows toast with severity 'error'

---

## Dev Notes

### No Flyway Migration Required

The `epr_exports` table already exists and has all required columns — it was created in `V20260323_001__create_epr_tables.sql` and updated in `V20260323_004__epr_review_r2_fixes.sql`. The `export_format_type` ENUM already has `'CSV'` as a valid value.

**`epr_exports` schema (current):**
```sql
CREATE TABLE epr_exports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    calculation_id    UUID REFERENCES epr_calculations(id) ON DELETE SET NULL,
    config_version    INT NOT NULL REFERENCES epr_configs(version) ON DELETE RESTRICT,
    export_format     export_format_type NOT NULL,  -- ENUM: CSV | XLSX
    file_hash         VARCHAR(64),
    exported_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Set `calculation_id = NULL` for filing CSV exports — the export covers multiple templates, not a single `epr_calculations` row. This is by design (the FK is nullable).

### MOHU CSV Format (Exact)

```
<BOM>KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)
11010101;Kartondoboz A;1000;120,000000;25800
11010202;Kis doboz;1;0,050000;11
```

Key encoding rules:
- **BOM**: 3-byte UTF-8 BOM (`new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}`) — prepend to the `byte[]` output, NOT to the Java String
- **Delimiter**: semicolon (`;`) — no spaces around delimiter
- **Decimal separator**: comma (`,`) for kg values — use `String.format("%.6f", totalWeightKg).replace('.', ',')` (do NOT use `Locale.of("hu","HU")` in `String.format` since it would also affect integer formatting)
- **Fee amount**: plain integer, no decimal separator, no currency symbol — `feeAmountHuf.intValue()`
- **KF code**: verbatim 8-character string from `ExportLineRequest.kfCode` (e.g. `"11010101"`)
- **Name field**: if the material name contains a semicolon, wrap the field in double quotes per RFC 4180: `"name with ; semicolon"` — implement minimal RFC 4180 quoting for name field only
- **Line ending**: `\n` (LF) — NOT `\r\n` (MOHU portal accepts both; LF is simpler)

### `MohuExporter.java` — Structure Pattern

```java
@Component
@ExportLocale("hu")
public class MohuExporter {

    private static final String HEADER = "KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)";
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    public byte[] generate(List<ExportLineRequest> lines) {
        var sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        for (var line : lines) {
            sb.append(escapeField(line.kfCode())).append(';')
              .append(escapeField(line.name())).append(';')
              .append(line.quantityPcs()).append(';')
              .append(String.format("%.6f", line.totalWeightKg()).replace('.', ',')).append(';')
              .append(line.feeAmountHuf().intValue()).append('\n');
        }
        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csvBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csvBytes, 0, result, UTF8_BOM.length, csvBytes.length);
        return result;
    }

    private String escapeField(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
```

### `EprService.exportMohuCsv()` — Config Version Guard

Config version mismatch guard is important: if a user calculates with configVersion=1 but the active config has since been updated to version=2, their export would be based on stale data. The guard prevents this:

```java
int activeVersion = getActiveConfigVersion();
if (request.configVersion() != activeVersion) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Config version mismatch: request has version " + request.configVersion()
        + " but active version is " + activeVersion);
}
```

### `HashUtil.java` — SHA-256 for `file_hash`

Check `backend/src/main/java/hu/riskguard/core/util/HashUtil.java` for existing SHA-256 support. If `sha256Hex(byte[])` exists, use it directly. If not, add the overload (the existing method likely takes `String` for the audit trail). Do not import Bouncy Castle — Java's built-in `MessageDigest.getInstance("SHA-256")` is sufficient for a file hash (Bouncy Castle is only used for SHA3-512 NAV request signing).

### `EprRepository.saveExport()` — jOOQ Pattern

Follow the `insertCalculation()` pattern at line ~268 for the jOOQ insert. The `export_format_type` ENUM is generated as `hu.riskguard.jooq.enums.ExportFormatType` — import it and use `ExportFormatType.CSV`.

```java
import static hu.riskguard.jooq.Tables.EPR_EXPORTS;
import hu.riskguard.jooq.enums.ExportFormatType;

public void saveExport(UUID tenantId, int configVersion, String fileHash) {
    dsl.insertInto(EPR_EXPORTS)
       .set(EPR_EXPORTS.ID, UUID.randomUUID())
       .set(EPR_EXPORTS.TENANT_ID, tenantId)
       .set(EPR_EXPORTS.CONFIG_VERSION, configVersion)
       .set(EPR_EXPORTS.EXPORT_FORMAT, ExportFormatType.CSV)
       .set(EPR_EXPORTS.FILE_HASH, fileHash)
       // calculation_id left null — bulk export, no single calculation row
       .execute();
}
```

### `EprService` Constructor — Inject `MohuExporter`

`EprService` uses `@RequiredArgsConstructor`. Add `private final MohuExporter mohuExporter;` as a field. Spring Boot's component scan will wire it automatically since `MohuExporter` is a `@Component`. Both `EprServiceTest` and `EprServiceWizardTest` construct `EprService` manually — add a `mock(MohuExporter.class)` argument to those constructors, matching the pattern already done for `FeeCalculator` in Story 5.2.

### Frontend — Blob Download Pattern (Nuxt 3)

The `$fetch` utility with `responseType: 'blob'` is the correct way to download binary files in Nuxt 3. This differs from a standard JSON fetch:

```typescript
const blob = await $fetch<Blob>('/api/v1/epr/filing/export', {
  method: 'POST',
  body: { lines: [...], configVersion: n },
  responseType: 'blob',       // ← critical: tells ofetch not to parse as JSON
  baseURL: config.public.apiBase as string,
  credentials: 'include',
})
```

**Do NOT** use `useFetch()` or `useAsyncData()` for file downloads — they are designed for SSR data fetching. Use `$fetch` directly inside the action.

**Anchor cleanup:** Always `document.body.removeChild(anchor)` AND `URL.revokeObjectURL(url)` after click to avoid memory leaks. Use `setTimeout(() => URL.revokeObjectURL(url), 100)` if the browser needs a moment to initiate the download before the URL is revoked.

### Frontend — Export Button Placement

Story 5.2's `filing.vue` ends at the Summary card. The "Export for MOHU" button belongs AFTER the Calculate button row and BEFORE the Summary card — but only visible when `serverResult !== null` (i.e., after a successful Calculate call). Layout flow:

```
[Filing Table]
[Calculate button, right-aligned]
[Export for MOHU button, right-aligned — conditional on serverResult]
[Summary card — always visible after Calculate]
```

Story 5.4 will add the "Export generated in Hungarian" Indigo Toast above this button — do NOT add that toast here.

### Frontend — Error Handling Pattern

If `$fetch` throws (e.g., 422 config mismatch, 500 server error), catch and show a PrimeVue toast using the `useToast()` composable. The error message from `ofetch` is in `error.data?.detail` (RFC 7807 `detail` field) or `error.message`. Extract safely:

```typescript
const message = (e as { data?: { detail?: string } })?.data?.detail ?? (e instanceof Error ? e.message : 'Unknown error')
```

### MohuExporterTest.java — BOM Parsing

When parsing the CSV output in tests, strip the BOM before splitting into lines:

```java
String raw = new String(bytes, StandardCharsets.UTF_8);
// Strip BOM if present
if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
String[] lines = raw.split("\n");
assertThat(lines[0]).isEqualTo("KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)");
```

### Test Fixture Values (from Story 5.2 FeeCalculator golden cases)

Reuse these verified values in `MohuExporterTest`:
- Kartondoboz A: kfCode=`11010101`, qty=1000, totalWeightKg=`120.000000`, feeAmountHuf=`25800`
- Kis doboz: kfCode=`11010202`, qty=1, totalWeightKg=`0.050000`, feeAmountHuf=`11`
- Fólia B: kfCode=`11010303`, qty=100000, totalWeightKg=`500.000000`, feeAmountHuf=`65000`

### Architecture Compliance Checklist

- [ ] `MohuExporter.java` in `epr/domain/` — correct package (domain service, not repository concern)
- [ ] `EprService` remains the ONLY public facade — `MohuExporter` is injected but not exported
- [ ] `MohuExportRequest` and `ExportLineRequest` in `epr/api/dto/` — ArchUnit enforces records-only in `api.dto` packages
- [ ] Both DTOs have no `from()` factory (they are request DTOs, not response DTOs — only response DTOs require `from()`)
- [ ] Controller uses `eprService.exportMohuCsv()` — no business logic in controller
- [ ] `epr_exports` table access is ONLY from `EprRepository` (no cross-module DB access)
- [ ] `@TierRequired(Tier.PRO_EPR)` is class-level on `EprController` — new endpoint inherits it automatically

### Project Structure Notes

- `MohuExporter.java`: `backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java`
- `MohuExporterTest.java`: `backend/src/test/java/hu/riskguard/epr/MohuExporterTest.java`
- `MohuExportRequest.java`: `backend/src/main/java/hu/riskguard/epr/api/dto/MohuExportRequest.java`
- `ExportLineRequest.java`: `backend/src/main/java/hu/riskguard/epr/api/dto/ExportLineRequest.java`
- `eprFiling.ts`: `frontend/app/stores/eprFiling.ts` (modify existing)
- `filing.vue`: `frontend/app/pages/epr/filing.vue` (modify existing)
- `filing.spec.ts`: `frontend/app/pages/epr/filing.spec.ts` (modify existing)
- `epr.json` i18n: `frontend/app/i18n/en/epr.json` and `frontend/app/i18n/hu/epr.json` (modify existing)

### References

- `epr_exports` table schema: `backend/src/main/resources/db/migration/V20260323_001__create_epr_tables.sql`
- `export_format_type` ENUM & FK constraints: `backend/src/main/resources/db/migration/V20260323_004__epr_review_r2_fixes.sql`
- `FeeCalculator.java` pattern (same package, same `@Component`): `backend/src/main/java/hu/riskguard/epr/domain/FeeCalculator.java`
- `EprRepository.insertCalculation()` (jOOQ insert pattern): `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java:268`
- `EprService.calculateFiling()` (service facade pattern, config version usage): `backend/src/main/java/hu/riskguard/epr/domain/EprService.java`
- `FilingCalculationResponse.java` (shared contract): `backend/src/main/java/hu/riskguard/epr/api/dto/FilingCalculationResponse.java`
- `FilingLineResultDto.java` (line-level result fields): `backend/src/main/java/hu/riskguard/epr/api/dto/FilingLineResultDto.java`
- `HashUtil.java` (SHA-256): `backend/src/main/java/hu/riskguard/core/util/HashUtil.java`
- `ExportLocale.java` annotation: `backend/src/main/java/hu/riskguard/core/security/ExportLocale.java`
- `eprFiling.ts` store: `frontend/app/stores/eprFiling.ts`
- `filing.vue` page: `frontend/app/pages/epr/filing.vue`
- Architecture — MohuExporter, @ExportLocale, CSV encoding: [Source: architecture.md §ADR-6, §i18n patterns]
- Architecture — DTO conventions (records, from() for responses): [Source: architecture.md §DTO Mapping Strategy]
- Architecture — Blob download pattern: [Source: architecture.md §Process Patterns]
- Story 5.2 integration point note: [Source: `_bmad-output/implementation-artifacts/5-2-quarterly-epr-filing-workflow.md` §Story 5.3 Integration Point]
- Story 5.2 FeeCalculator golden test values: [Source: `_bmad-output/implementation-artifacts/5-2-quarterly-epr-filing-workflow.md` §FeeCalculator Design]

---

### Review Findings

- [x] [Review][Patch] Typo in messages_hu.properties: `Össsúly` (3× s) should be `Összsúly` (matches HEADER constant) [backend/src/main/resources/messages_hu.properties]
- [x] [Review][Patch] `escapeField` does not escape `\r` (carriage return) — a material name with bare `\r` is emitted unquoted, breaking row boundaries in Windows CSV readers [backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java:62]
- [x] [Review][Patch] Redundant `v-if="filingStore.serverResult !== null"` on `<Button>` inside already-guarded `<div v-if>` — dead condition, remove the inner `v-if` [frontend/app/pages/epr/filing.vue:172]
- [x] [Review][Patch] `exportMohu()` action uses `document.body.appendChild` without `process.client` guard — crashes SSR if ever called server-side [frontend/app/stores/eprFiling.ts:173]
- [x] [Review][Defer] `feeAmountHuf.intValue()` truncates without rounding — pre-existing design contract (FeeCalculator always produces integers); deferred
- [x] [Review][Defer] Duplicate POST creates duplicate `epr_exports` rows (no unique constraint on tenant_id+file_hash) — requires DB migration, out of Story 5.3 scope; deferred
- [x] [Review][Defer] `Content-Disposition` filename uses `LocalDate.now()` without explicit timezone — cosmetic only, acceptable; deferred

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — clean implementation, all tests passed first run.

### Completion Notes List

- Created `ExportLocale.java` marker annotation (missing prerequisite referenced in story).
- Added `HashUtil.sha256Hex(byte[])` overload — existing method only took `String... parts`.
- `EprController` uses `active_tenant_id` claim (consistent with all other endpoints) rather than `tenant_id` as shown in the story spec code sample.
- `MohuExporter.generate()` strips trailing newline after last data row per spec ("No trailing newline after last row").
- RFC 4180 quoting implemented: semicolons, double-quotes, and newlines in name field are wrapped in double-quotes with internal double-quote escaping.
- All 9 MohuExporterTest tests pass, all 20 EprControllerTest tests pass (4 new), 579 frontend tests pass.
- i18n parity check passed.
- ✅ Resolved review finding [Patch]: Typo `Össsúly` → `Összsúly` fixed in messages_hu.properties.
- ✅ Resolved review finding [Patch]: `escapeField` now also quotes `\r` (carriage return); added `carriageReturnInNameShouldBeQuoted` test to MohuExporterTest.
- ✅ Resolved review finding [Patch]: Removed redundant inner `v-if` on `<Button>` in filing.vue (outer `<div v-if>` is the guard).
- ✅ Resolved review finding [Patch]: DOM manipulation in `exportMohu()` now wrapped with `if (import.meta.client)` guard.

### Change Log

- 2026-03-28: Story 5.3 implemented — MohuExporter, ExportLocale annotation, ExportLineRequest/MohuExportRequest DTOs, EprRepository.saveExport(), EprService.exportMohuCsv(), EprController.exportMohu() endpoint, HashUtil.sha256Hex(byte[]) overload, frontend store exportMohu() action + filing.vue Export button. 9 backend tests + 4 frontend tests added.
- 2026-03-29: Addressed code review findings — 4 Patch items resolved (messages_hu typo, \r escaping, redundant v-if, SSR guard)
- 2026-03-29: Code review R2 — 0 patch items found; 3 existing defers confirmed; 25 dismissed; status → done

### File List

backend/src/main/java/hu/riskguard/core/security/ExportLocale.java (new)
backend/src/main/java/hu/riskguard/core/util/HashUtil.java (modified — added sha256Hex(byte[]))
backend/src/main/java/hu/riskguard/epr/api/dto/ExportLineRequest.java (new)
backend/src/main/java/hu/riskguard/epr/api/dto/MohuExportRequest.java (new)
backend/src/main/java/hu/riskguard/epr/domain/MohuExporter.java (new)
backend/src/main/java/hu/riskguard/epr/domain/EprService.java (modified — added mohuExporter field + exportMohuCsv())
backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java (modified — added saveExport())
backend/src/main/java/hu/riskguard/epr/api/EprController.java (modified — added exportMohu() endpoint)
backend/src/main/resources/messages_hu.properties (modified — added MOHU CSV header doc keys)
backend/src/test/java/hu/riskguard/epr/MohuExporterTest.java (new)
backend/src/test/java/hu/riskguard/epr/EprControllerTest.java (modified — added 4 export tests)
backend/src/test/java/hu/riskguard/epr/EprServiceTest.java (modified — injected MohuExporter mock)
backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java (modified — injected MohuExporter mock)
frontend/app/i18n/en/epr.json (modified — added exportButton/exportError/exportGenerating)
frontend/app/i18n/hu/epr.json (modified — added exportButton/exportError/exportGenerating)
frontend/app/stores/eprFiling.ts (modified — added isExporting + exportMohu() + reset update)
frontend/app/pages/epr/filing.vue (modified — added handleExport(), useToast, Export button)
frontend/app/pages/epr/filing.spec.ts (modified — added 4 export tests + primevue/usetoast mock)
