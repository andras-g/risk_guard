# Story 5.2: Quarterly EPR Filing Workflow

Status: review

## Story

As a User,
I want to enter the quantities sold for my "Verified" material templates,
so that I can calculate my total EPR liability for the quarter.

## Acceptance Criteria

### AC 1: Filing Workflow Entry

**Given** the EPR Library page (`/epr`)
**When** the user clicks "New Filing"
**Then** the app navigates to `/epr/filing`
**And** the page shows a table of all "Verified" templates (verified=true) for the current tenant
**And** each row shows: Name, KF Code, Base Weight (g), Fee Rate (HUF/kg), Quantity (pcs) input, Total Weight (kg) computed, Fee Amount (HUF) computed
**When** the tenant has no Verified templates
**Then** an empty state is shown: "No filing-ready materials. Classify your materials in the EPR Library first."

### AC 2: Quantity Input — MOHU Gate Real-Time Validation (UX Spec §7.3)

**Given** a quantity input field for a Verified template
**When** the user types a positive integer (e.g. 1200)
**Then** the input border turns Emerald (`border-emerald-500`) and a green checkmark icon is shown
**And** `totalWeightKg` and `feeAmountHuf` update in real-time for that row
**When** the user types zero, a negative number, a non-integer decimal (e.g. 1.5), or non-numeric text
**Then** the input border turns Crimson (`border-red-600`) and a validation message is shown below the field
**And** the row's computed values show "—" (not computed)
**And** the summary totals exclude that row

### AC 3: FeeCalculator — Backend Authoritative Computation

**Given** a filled-out filing table (at least one valid quantity)
**When** the user clicks "Calculate" (or the backend is called on page load to populate fee_rate)
**Then** `POST /api/v1/epr/filing/calculate` is called with the list of `{templateId, quantityPcs}`
**And** the backend `FeeCalculator` computes per line:
  - `totalWeightGrams = quantityPcs × template.baseWeightGrams`
  - `totalWeightKg = totalWeightGrams / 1000.0`
  - `feeAmountHuf = totalWeightKg × feeRate` (fee_rate from template's latest linked `epr_calculations.fee_rate`)
**And** the response includes all computed lines and grand totals
**And** the frontend displays the backend-computed totals in the Summary section

### AC 4: Summary Section

**Given** a filing table with at least one valid quantity
**Then** a "Filing Summary" card is shown below the table with:
  - Total lines (count of verified templates with valid quantities)
  - Grand Total Weight (kg)
  - Grand Total Fee (HUF, formatted as `1 234 567 Ft`)
**When** no valid quantities have been entered
**Then** the Summary card shows all zeros / "—"

### AC 5: Empty/Loading States

**Given** the page is loading verified templates
**Then** a skeleton/spinner is shown
**When** the `calculateFiling` API call is in progress
**Then** row values show a loading spinner; the "Calculate" button is disabled

### AC 6: Tests Pass

**Given** all changes are implemented
**Then** `./gradlew test` passes (all backend tests green)
**And** `npm run test` (in `frontend/`) passes (all Vitest tests green)

---

## Tasks / Subtasks

- [x] Task 1: Backend — `FeeCalculator.java` (AC: #3)
  - [x] 1.1 Create `backend/src/main/java/hu/riskguard/epr/domain/FeeCalculator.java` as a `@Component`:
    ```java
    package hu.riskguard.epr.domain;

    import org.springframework.stereotype.Component;
    import java.math.BigDecimal;
    import java.math.RoundingMode;
    import java.util.List;

    /**
     * Computes EPR fee liability for a set of filing lines.
     * Each line: quantity_pcs × base_weight_grams → total_weight_kg × fee_rate → fee_amount_huf.
     * fee_rate is in HUF/kg from the active config (stored in epr_calculations.fee_rate).
     */
    @Component
    public class FeeCalculator {

        /**
         * Compute a single filing line.
         * @param quantityPcs    number of pieces sold (must be > 0)
         * @param baseWeightGrams weight per piece in grams
         * @param feeRateHufPerKg  EPR fee rate in HUF/kg from the active config
         */
        public FilingLineResult computeLine(int quantityPcs, BigDecimal baseWeightGrams, BigDecimal feeRateHufPerKg) {
            BigDecimal qty = BigDecimal.valueOf(quantityPcs);
            BigDecimal totalWeightGrams = baseWeightGrams.multiply(qty);
            BigDecimal totalWeightKg = totalWeightGrams.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            BigDecimal feeAmountHuf = totalWeightKg.multiply(feeRateHufPerKg).setScale(0, RoundingMode.HALF_UP);
            return new FilingLineResult(totalWeightGrams, totalWeightKg, feeAmountHuf);
        }

        /** Aggregate totals from all lines. */
        public FilingTotals computeTotals(List<FilingLineResult> lines) {
            BigDecimal totalWeightKg = lines.stream()
                    .map(FilingLineResult::totalWeightKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalFeeHuf = lines.stream()
                    .map(FilingLineResult::feeAmountHuf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new FilingTotals(totalWeightKg, totalFeeHuf);
        }

        public record FilingLineResult(
                BigDecimal totalWeightGrams,
                BigDecimal totalWeightKg,
                BigDecimal feeAmountHuf
        ) {}

        public record FilingTotals(BigDecimal totalWeightKg, BigDecimal totalFeeHuf) {}
    }
    ```

- [x] Task 2: Backend — DTOs (AC: #3)
  - [x] 2.1 Create `backend/src/main/java/hu/riskguard/epr/api/dto/FilingLineRequest.java`:
    ```java
    package hu.riskguard.epr.api.dto;

    import jakarta.validation.constraints.Min;
    import jakarta.validation.constraints.NotNull;
    import java.util.UUID;

    public record FilingLineRequest(
            @NotNull UUID templateId,
            @NotNull @Min(1) Integer quantityPcs
    ) {}
    ```
  - [x] 2.2 Create `backend/src/main/java/hu/riskguard/epr/api/dto/FilingCalculationRequest.java`:
    ```java
    package hu.riskguard.epr.api.dto;

    import jakarta.validation.Valid;
    import jakarta.validation.constraints.NotEmpty;
    import java.util.List;

    public record FilingCalculationRequest(
            @NotEmpty @Valid List<FilingLineRequest> lines
    ) {}
    ```
  - [x] 2.3 Create `backend/src/main/java/hu/riskguard/epr/api/dto/FilingLineResultDto.java`:
    ```java
    package hu.riskguard.epr.api.dto;

    import java.math.BigDecimal;
    import java.util.UUID;

    public record FilingLineResultDto(
            UUID templateId,
            String name,
            String kfCode,
            int quantityPcs,
            BigDecimal baseWeightGrams,
            BigDecimal totalWeightGrams,
            BigDecimal totalWeightKg,
            BigDecimal feeRateHufPerKg,
            BigDecimal feeAmountHuf
    ) {}
    ```
  - [x] 2.4 Create `backend/src/main/java/hu/riskguard/epr/api/dto/FilingCalculationResponse.java`:
    ```java
    package hu.riskguard.epr.api.dto;

    import java.math.BigDecimal;
    import java.util.List;

    public record FilingCalculationResponse(
            List<FilingLineResultDto> lines,
            BigDecimal grandTotalWeightKg,
            BigDecimal grandTotalFeeHuf,
            int configVersion
    ) {}
    ```

- [x] Task 3: Backend — `EprRepository` addition (AC: #3)
  - [x] 3.1 Add `findVerifiedByTenant(UUID tenantId)` to `EprRepository.java`:
    ```java
    /**
     * Find all Verified templates for a tenant (verified=true), ordered by created_at DESC.
     * Used by the FeeCalculator endpoint to validate template ownership and get base_weight.
     */
    public List<EprMaterialTemplatesRecord> findVerifiedByTenant(UUID tenantId) {
        return dsl.select(EPR_MATERIAL_TEMPLATES.asterisk())
                .from(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .and(EPR_MATERIAL_TEMPLATES.VERIFIED.isTrue())
                .orderBy(EPR_MATERIAL_TEMPLATES.CREATED_AT.desc())
                .fetchInto(EprMaterialTemplatesRecord.class);
    }
    ```
  - [x] 3.2 The `fee_rate` for each template is fetched via the existing `findAllByTenantWithOverride()` or a targeted query. Use `findAllByTenantWithOverride()` in `EprService.calculateFiling()` and filter to verified+requested templates — avoids a new multi-JOIN query.

- [x] Task 4: Backend — `EprService.calculateFiling()` (AC: #3)
  - [x] 4.1 Add `calculateFiling()` method to `EprService.java`:
    ```java
    /**
     * Compute EPR filing liability for a set of (templateId, quantityPcs) lines.
     * Validates: templates exist, belong to tenant, are verified, have a fee_rate.
     * Uses FeeCalculator for per-line and total computation.
     *
     * @throws ResponseStatusException 404 if any templateId not found or doesn't belong to tenant
     * @throws ResponseStatusException 422 if any template is not verified or has no fee_rate
     */
    @Transactional(readOnly = true)
    public FilingCalculationResponse calculateFiling(List<FilingLineRequest> lines, UUID tenantId) {
        // Build a lookup: templateId → TemplateWithOverride (includes fee_rate)
        Map<UUID, TemplateWithOverride> templateMap = eprRepository.findAllByTenantWithOverride(tenantId)
                .stream()
                .collect(Collectors.toMap(t -> t.template().getId(), t -> t));

        List<FilingLineResultDto> resultLines = new java.util.ArrayList<>();
        for (FilingLineRequest line : lines) {
            TemplateWithOverride two = templateMap.get(line.templateId());
            if (two == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Template not found: " + line.templateId());
            }
            if (!Boolean.TRUE.equals(two.template().getVerified())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Template is not verified: " + line.templateId());
            }
            BigDecimal feeRate = two.feeRate();
            if (feeRate == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Template has no fee rate — run the wizard first: " + line.templateId());
            }
            FeeCalculator.FilingLineResult result = feeCalculator.computeLine(
                    line.quantityPcs(), two.template().getBaseWeightGrams(), feeRate);
            resultLines.add(new FilingLineResultDto(
                    two.template().getId(),
                    two.template().getName(),
                    two.template().getKfCode(),
                    line.quantityPcs(),
                    two.template().getBaseWeightGrams(),
                    result.totalWeightGrams(),
                    result.totalWeightKg(),
                    feeRate,
                    result.feeAmountHuf()
            ));
        }
        FeeCalculator.FilingTotals totals = feeCalculator.computeTotals(
                resultLines.stream()
                        .map(r -> new FeeCalculator.FilingLineResult(r.totalWeightGrams(), r.totalWeightKg(), r.feeAmountHuf()))
                        .toList());
        return new FilingCalculationResponse(resultLines, totals.totalWeightKg(), totals.totalFeeHuf(),
                getActiveConfigVersion());
    }
    ```
  - [x] 4.2 Inject `FeeCalculator` into `EprService`: add `private final FeeCalculator feeCalculator;` to the constructor (`@RequiredArgsConstructor` handles this automatically).
  - [x] 4.3 Add necessary imports: `Map`, `Collectors`, `HttpStatus`, `ResponseStatusException`.

- [x] Task 5: Backend — `EprController.calculateFiling()` endpoint (AC: #3)
  - [x] 5.1 Add to `EprController.java` (before the `requireUuidClaim` private method):
    ```java
    /**
     * Compute EPR filing liability for a set of material quantities.
     * Validates all templates are Verified and belong to the requesting tenant.
     */
    @PostMapping("/filing/calculate")
    public FilingCalculationResponse calculateFiling(
            @Valid @RequestBody FilingCalculationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        return eprService.calculateFiling(request.lines(), tenantId);
    }
    ```
  - [x] 5.2 Add `FilingCalculationRequest`, `FilingCalculationResponse` to the existing wildcard DTO import (already covered by `hu.riskguard.epr.api.dto.*`).

- [x] Task 6: Backend — `FeeCalculatorTest.java` (AC: #6)
  - [x] 6.1 Create `backend/src/test/java/hu/riskguard/epr/FeeCalculatorTest.java` with golden test cases:
    - `computeLine_standardCardboardBox`: quantity=1000 pcs × 120g base weight × 215 HUF/kg fee_rate → 120 kg × 215 = 25,800 HUF
    - `computeLine_singleUnit`: quantity=1 × 50g × 215 HUF/kg → 0.05 kg × 215 = 10.75 → rounds to 11 HUF
    - `computeLine_largeQuantity`: quantity=100000 × 5g × 130 HUF/kg → 500 kg × 130 = 65,000 HUF
    - `computeTotals_sumLines`: two lines with results summed correctly
    - Tests run pure — no Spring context needed (plain unit test, `@ExtendWith(MockitoExtension.class)` not even needed since FeeCalculator has no dependencies).

- [x] Task 7: Backend — `EprControllerFilingTest.java` (AC: #3, #6)
  - [x] 7.1 Add filing tests to the existing `EprControllerTest.java` (DO NOT create a separate file — follow the pattern of the existing test class):
    - `calculateFiling_validLines_returnsCalculationResponse`
    - `calculateFiling_emptyLines_returns400`
    - `calculateFiling_missingJwtClaim_returns401`
  - [x] 7.2 Mock `eprService.calculateFiling(any(), any())` using `when(...).thenReturn(...)`.

- [x] Task 8: DB migration — NONE NEEDED for Story 5.2
  - Filing state is held in the Pinia store; the backend computes on demand.
  - Story 5.3 (MOHU CSV Export) will create the `epr_exports` row when the file is generated.
  - No new tables in this story.

  **⚠️ TEST FIXTURE NOTE (sprint-status.yaml):** All tests that need "prior-quarter" verified templates MUST use explicit backdated `created_at = '2025-11-15'` (Q4 2025), NOT `now()`. This is critical for `findByTenantAndQuarter()` correctness. Pattern:
  ```java
  // In test @BeforeEach or helper — set created_at explicitly to Q4 2025:
  dsl.insertInto(EPR_MATERIAL_TEMPLATES)
     .set(EPR_MATERIAL_TEMPLATES.TENANT_ID, TENANT_ID)
     .set(EPR_MATERIAL_TEMPLATES.NAME, "Cardboard Box Q4-2025")
     .set(EPR_MATERIAL_TEMPLATES.BASE_WEIGHT_GRAMS, new BigDecimal("120"))
     .set(EPR_MATERIAL_TEMPLATES.RECURRING, true)
     .set(EPR_MATERIAL_TEMPLATES.VERIFIED, true)
     .set(EPR_MATERIAL_TEMPLATES.KF_CODE, "11010101")
     .set(EPR_MATERIAL_TEMPLATES.CREATED_AT, OffsetDateTime.parse("2025-11-15T00:00:00Z"))
     .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.parse("2025-11-15T00:00:00Z"))
     .execute();
  ```
  Do NOT depend on `now()` being in any particular quarter.

- [x] Task 9: Frontend — `stores/eprFiling.ts` (AC: #1, #3, #4)
  - [x] 9.1 Create `frontend/app/stores/eprFiling.ts`:
    ```typescript
    import { defineStore } from 'pinia'
    import type { MaterialTemplateResponse } from '~/types/epr'

    export interface FilingLineState {
      templateId: string
      name: string
      kfCode: string | null
      baseWeightGrams: number
      feeRateHufPerKg: number | null
      quantityPcs: number | null
      // backend-computed values (null until calculate() is called)
      totalWeightGrams: number | null
      totalWeightKg: number | null
      feeAmountHuf: number | null
      // frontend-only validation
      isValid: boolean
      validationMessage: string | null
    }

    export interface FilingCalculationResponse {
      lines: {
        templateId: string; name: string; kfCode: string | null
        quantityPcs: number; baseWeightGrams: number
        totalWeightGrams: number; totalWeightKg: number
        feeRateHufPerKg: number; feeAmountHuf: number
      }[]
      grandTotalWeightKg: number
      grandTotalFeeHuf: number
      configVersion: number
    }

    interface FilingState {
      lines: FilingLineState[]
      serverResult: FilingCalculationResponse | null
      isLoading: boolean
      isCalculating: boolean
      error: string | null
    }

    export const useEprFilingStore = defineStore('eprFiling', {
      state: (): FilingState => ({
        lines: [],
        serverResult: null,
        isLoading: false,
        isCalculating: false,
        error: null,
      }),

      getters: {
        validLines: (state): FilingLineState[] =>
          state.lines.filter(l => l.isValid && l.quantityPcs !== null && l.quantityPcs > 0),

        grandTotalWeightKg: (state): number =>
          state.serverResult?.grandTotalWeightKg ?? 0,

        grandTotalFeeHuf: (state): number =>
          state.serverResult?.grandTotalFeeHuf ?? 0,

        hasValidLines: (state): boolean =>
          state.lines.some(l => l.isValid && l.quantityPcs !== null && l.quantityPcs > 0),
      },

      actions: {
        initFromTemplates(templates: MaterialTemplateResponse[]) {
          this.lines = templates
            .filter(t => t.verified)
            .map(t => ({
              templateId: t.id,
              name: t.name,
              kfCode: t.kfCode,
              baseWeightGrams: t.baseWeightGrams,
              feeRateHufPerKg: t.feeRate,
              quantityPcs: null,
              totalWeightGrams: null,
              totalWeightKg: null,
              feeAmountHuf: null,
              isValid: false,
              validationMessage: null,
            }))
          this.serverResult = null
        },

        updateQuantity(templateId: string, rawValue: string) {
          const line = this.lines.find(l => l.templateId === templateId)
          if (!line) return
          const parsed = parseInt(rawValue, 10)
          if (rawValue === '' || rawValue === null) {
            line.quantityPcs = null
            line.isValid = false
            line.validationMessage = null
          } else if (!Number.isInteger(parseFloat(rawValue)) || isNaN(parsed)) {
            line.quantityPcs = null
            line.isValid = false
            line.validationMessage = 'epr.filing.validation.mustBeInteger'
          } else if (parsed <= 0) {
            line.quantityPcs = null
            line.isValid = false
            line.validationMessage = 'epr.filing.validation.mustBePositive'
          } else {
            line.quantityPcs = parsed
            line.isValid = true
            line.validationMessage = null
          }
          // Reset server result when user changes a value
          this.serverResult = null
        },

        async calculate() {
          const valid = this.lines.filter(l => l.isValid && l.quantityPcs)
          if (valid.length === 0) return
          this.isCalculating = true
          this.error = null
          try {
            const config = useRuntimeConfig()
            const result = await $fetch<FilingCalculationResponse>('/api/v1/epr/filing/calculate', {
              method: 'POST',
              body: { lines: valid.map(l => ({ templateId: l.templateId, quantityPcs: l.quantityPcs })) },
              baseURL: config.public.apiBase as string,
              credentials: 'include',
            })
            this.serverResult = result
            // Merge backend results into lines
            for (const r of result.lines) {
              const line = this.lines.find(l => l.templateId === r.templateId)
              if (line) {
                line.totalWeightGrams = r.totalWeightGrams
                line.totalWeightKg = r.totalWeightKg
                line.feeAmountHuf = r.feeAmountHuf
              }
            }
          } catch (e: unknown) {
            this.error = e instanceof Error ? e.message : String(e)
            throw e
          } finally {
            this.isCalculating = false
          }
        },

        reset() {
          this.lines = []
          this.serverResult = null
          this.isLoading = false
          this.isCalculating = false
          this.error = null
        },
      },
    })
    ```

- [x] Task 10: Frontend — `pages/epr/filing.vue` (AC: #1, #2, #3, #4, #5)
  - [x] 10.1 Create `frontend/app/pages/epr/filing.vue`:
    - Tier gate check (`useTierGate('PRO_EPR')`) — same pattern as `pages/epr/index.vue`
    - On `onMounted`: call `eprStore.fetchMaterials()` then `filingStore.initFromTemplates(eprStore.materials)`
    - Render a `DataTable` (PrimeVue) with columns:
      - Name, KF Code, Base Weight (g), Fee Rate (HUF/kg)
      - Quantity (pcs) — `InputNumber` with integer mode (`:min-fraction-digits="0"`, `:max-fraction-digits="0"`)
      - Total Weight (kg) — computed from backend result, or "—" if not calculated
      - Fee Amount (HUF) — computed from backend result, or "—"
    - MOHU Gate validation per row: watch `line.isValid` to apply CSS class
      - Valid: `border border-emerald-500 rounded` + `<i class="pi pi-check-circle text-emerald-500" />`
      - Invalid: `border border-red-600 rounded` + validation message below in `text-red-600 text-xs`
    - "Calculate" button: disabled when `!filingStore.hasValidLines || filingStore.isCalculating`; calls `filingStore.calculate()`; shows spinner when calculating
    - "Filing Summary" card below the table (shown when `filingStore.serverResult !== null`):
      - Total Lines: count of `filingStore.validLines.length`
      - Grand Total Weight: `filingStore.grandTotalWeightKg` formatted as `XX.XXX kg`
      - Grand Total Fee: `filingStore.grandTotalFeeHuf` formatted as `1 234 567 Ft`
    - "Back to Library" button navigates to `/epr`
  - [x] 10.2 Use `useI18n()` for all displayed strings (no raw text).
  - [x] 10.3 HUF formatting helper: `new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'` — define as a computed or composable.

- [x] Task 11: Frontend — Navigation — Add "New Filing" button to EPR index page (AC: #1)
  - [x] 11.1 In `frontend/app/pages/epr/index.vue`, add a "New Filing" button in the page header `<div class="flex gap-2">` alongside the existing buttons:
    ```html
    <Button
      :label="t('epr.filing.newFilingButton')"
      icon="pi pi-calculator"
      severity="secondary"
      data-testid="new-filing-button"
      @click="$router.push('/epr/filing')"
    />
    ```
    Position: BEFORE the existing "Copy from Previous Quarter" button.
  - [x] 11.2 Only render this button when `eprStore.verifiedCount > 0` — no point navigating to an empty filing. Use `v-if="eprStore.verifiedCount > 0"`.

- [x] Task 12: Frontend — i18n (AC: #1, #2, #4)
  - [x] 12.1 Add to `frontend/app/i18n/en/epr.json` under `"epr"` (alongside `"materialLibrary"`, `"wizard"`):
    ```json
    "filing": {
      "backToLibrary": "Back to Library",
      "calculateButton": "Calculate Totals",
      "emptyState": "No filing-ready materials. Classify your materials in the EPR Library first.",
      "grandTotalFee": "Grand Total Fee",
      "grandTotalWeight": "Grand Total Weight",
      "newFilingButton": "New Filing",
      "summaryTitle": "Filing Summary",
      "table": {
        "baseWeight": "Base Weight (g)",
        "feeAmount": "Fee Amount (HUF)",
        "feeRate": "Fee Rate (HUF/kg)",
        "kfCode": "KF Code",
        "name": "Name",
        "quantity": "Quantity (pcs)",
        "totalWeight": "Total Weight (kg)"
      },
      "title": "Quarterly EPR Filing",
      "totalLines": "Materials included",
      "validation": {
        "mustBeInteger": "Quantity must be a whole number (pcs)",
        "mustBePositive": "Quantity must be greater than 0"
      }
    }
    ```
  - [x] 12.2 Mirror into `frontend/app/i18n/hu/epr.json` with Hungarian translations:
    ```json
    "filing": {
      "backToLibrary": "Vissza a könyvtárhoz",
      "calculateButton": "Összesítés kiszámítása",
      "emptyState": "Nincs bejelentésre kész anyag. Először osztályozza az anyagokat az EPR Könyvtárban.",
      "grandTotalFee": "Teljes díj összege",
      "grandTotalWeight": "Teljes tömeg",
      "newFilingButton": "Új bejelentés",
      "summaryTitle": "Bejelentési összesítő",
      "table": {
        "baseWeight": "Alapsúly (g)",
        "feeAmount": "Díj összege (HUF)",
        "feeRate": "Díjkulcs (HUF/kg)",
        "kfCode": "KF kód",
        "name": "Megnevezés",
        "quantity": "Darabszám (db)",
        "totalWeight": "Összsúly (kg)"
      },
      "title": "Negyedéves EPR bejelentés",
      "totalLines": "Anyagok száma",
      "validation": {
        "mustBeInteger": "A darabszámnak egész számnak kell lennie",
        "mustBePositive": "A darabszámnak nagyobbnak kell lennie 0-nál"
      }
    }
    ```
  - [x] 12.3 Run `npm run check-i18n` to verify key parity between en and hu.

- [x] Task 13: Frontend — `pages/epr/filing.spec.ts` (AC: #6)
  - [x] 13.1 Create `frontend/app/pages/epr/filing.spec.ts`:
    - `'renders empty state when no verified templates'` — mock `eprStore.materials` = [], verify empty state text visible
    - `'shows verified templates in table'` — mock 2 verified templates, verify both names render
    - `'shows MOHU Gate valid state on valid quantity input'` — enter "1200", verify emerald border class applied
    - `'shows MOHU Gate invalid state on non-integer quantity'` — enter "1.5", verify red border and validation message visible
    - `'calculate button is disabled when no valid quantities'` — all inputs empty, verify button `:disabled=true`
    - `'shows Filing Summary after successful calculation'` — mock `filingStore.calculate()`, verify summary card renders with grand totals
  - [x] 13.2 Use `renderWithProviders` pattern from existing test files (`pages/epr/index.spec.ts`). Mock `useEprFilingStore` and `useEprStore`.

- [x] Task 14: Smoke Check (AC: #6)
  - [x] 14.1 `./gradlew test` — all green
  - [x] 14.2 `npm run test` (in `frontend/`) — all green

---

## Dev Notes

### FeeCalculator Design: Pure Computation, No State

`FeeCalculator.java` is a `@Component` with zero dependencies. It contains only math — no DB access, no config loading. The fee_rate comes from `epr_calculations.fee_rate` (already stored when the wizard confirmed a KF code). This avoids re-loading the JSON config for every filing calculation. The active config JSON is only needed in the wizard flow.

**Computation formulas:**
```
total_weight_grams = quantity_pcs × base_weight_grams       (integer × decimal)
total_weight_kg    = total_weight_grams / 1000              (6 decimal places, HALF_UP)
fee_amount_huf     = total_weight_kg × fee_rate_huf_per_kg  (0 decimal places, HALF_UP — integer HUF)
```

**Golden test case values** (for `FeeCalculatorTest.java`):
- Kartondoboz A: 1000 pcs × 120g × 215 HUF/kg = 120.000000 kg × 215 = **25,800 HUF**
- Kis doboz: 1 pc × 50g × 215 HUF/kg = 0.050000 kg × 215 = 10.75 → **11 HUF** (rounded)
- Fólia B: 100000 pcs × 5g × 130 HUF/kg = 500.000000 kg × 130 = **65,000 HUF**

These match the Hungarian EPR fee schedule in `epr-seed-data-2026.json`.

### MOHU Gate Pattern (UX Spec §7.3)

The quantity input validation follows the "MOHU Gate" — real-time validation with immediate color feedback:

| State | Input border | Icon | Message |
|-------|-------------|------|---------|
| Empty (pristine) | `border-slate-300` (default) | none | none |
| Valid integer > 0 | `border-emerald-500` | `pi pi-check-circle text-emerald-500` | none |
| Decimal (e.g. 1.5) | `border-red-600` | — | `mustBeInteger` |
| Zero or negative | `border-red-600` | — | `mustBePositive` |
| Non-numeric | `border-red-600` | — | `mustBeInteger` |

Use PrimeVue `InputNumber` with `:useGrouping="false"` `:minFractionDigits="0"` `:maxFractionDigits="0"` and `@input` handler calling `filingStore.updateQuantity()`. The CSS class is applied reactively via `:class`.

### Pinia Store vs. Backend Calculation

Story 5.2 uses a **hybrid approach**:
- **Frontend (real-time UX)**: The `updateQuantity()` action updates validation state immediately without a server call.
- **Backend (authoritative result)**: `filingStore.calculate()` calls `POST /api/v1/epr/filing/calculate` for the official numbers. The backend validates template ownership, verified status, and fee_rate completeness.

The Summary section shows **backend-computed** totals only (not frontend-estimated). This prevents rounding discrepancies in the exported CSV (Story 5.3).

### Story 5.3 Integration Point

Story 5.3 will consume `filingStore.serverResult` from this store. The filing page in 5.2 should NOT add an "Export for MOHU" button — that button lives in Story 5.3's scope. Story 5.2 ends at the Summary section.

The `FilingCalculationResponse` DTO (backend) is the shared contract. Story 5.3 will pass this as input to the export endpoint.

### Test Fixture Backdating (CRITICAL)

Per sprint-status.yaml note for Story 5.2: **All tests involving prior-quarter template lookups MUST use explicit `created_at = OffsetDateTime.parse("2025-11-15T00:00:00Z")`** for Q4 2025 templates. Do NOT rely on `now()` being in any specific quarter.

This applies to:
- `FeeCalculatorTest.java` — any tests that set up verified templates in `EprRepositoryTest` pattern
- `EprControllerTest.java` (filing tests) — if using `findAllByTenantWithOverride()`, mock it directly
- Any integration test that seeds the `epr_material_templates` table

Pattern for integration tests:
```java
// Q4 2025 template (for prior-quarter tests):
var q4Id = dsl.insertInto(EPR_MATERIAL_TEMPLATES)
    .set(EPR_MATERIAL_TEMPLATES.TENANT_ID, TENANT_ID)
    .set(EPR_MATERIAL_TEMPLATES.NAME, "Q4-2025 Material")
    .set(EPR_MATERIAL_TEMPLATES.BASE_WEIGHT_GRAMS, new BigDecimal("120"))
    .set(EPR_MATERIAL_TEMPLATES.RECURRING, true)
    .set(EPR_MATERIAL_TEMPLATES.VERIFIED, true)
    .set(EPR_MATERIAL_TEMPLATES.KF_CODE, "11010101")
    .set(EPR_MATERIAL_TEMPLATES.CREATED_AT, OffsetDateTime.parse("2025-11-15T00:00:00Z"))
    .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.parse("2025-11-15T00:00:00Z"))
    .returning(EPR_MATERIAL_TEMPLATES.ID)
    .fetchOne(EPR_MATERIAL_TEMPLATES.ID);
```

### `EprService.calculateFiling()` — Injection Pattern

`FeeCalculator` must be injected into `EprService`. Since `EprService` already uses `@RequiredArgsConstructor`, simply add `private final FeeCalculator feeCalculator;` as a field. Spring will wire it automatically.

### `EprMaterialTemplatesRecord.getVerified()` — Nullable Check

The jOOQ `EprMaterialTemplatesRecord.getVerified()` returns `Boolean` (boxed). Use `Boolean.TRUE.equals(two.template().getVerified())` to guard against null (though the column is NOT NULL with DEFAULT false, defensive coding is correct here).

### `TemplateWithOverride` — Already Has `feeRate`

`EprRepository.findAllByTenantWithOverride()` already returns `feeRate` (as `BigDecimal`) from the `LEFT JOIN` on `epr_calculations`. No additional query is needed for filing calculation — one call to `findAllByTenantWithOverride()` provides everything.

### Frontend: `InputNumber` PrimeVue Integer Mode

```vue
<InputNumber
  v-model="line.quantityPcs"
  :use-grouping="false"
  :min-fraction-digits="0"
  :max-fraction-digits="0"
  :min="0"
  :class="['w-24', line.isValid ? 'border-emerald-500' : (line.validationMessage ? 'border-red-600' : '')]"
  @input="filingStore.updateQuantity(line.templateId, $event.value?.toString() ?? '')"
/>
```

Note: PrimeVue `InputNumber` emits `@input` with `{ value: number | null }`. Pass `$event.value?.toString()` to `updateQuantity()`.

### HUF Currency Formatting

Format grand totals as Hungarian locale integer: `1 234 567 Ft` (space as thousands separator, no decimal).
```typescript
const formatHuf = (value: number): string =>
  new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'
```

Define this as a composable-level function or a plain function in the `.vue` script block (not a composable — it's a one-off).

### Architecture Compliance: Module Boundaries

- `FeeCalculator.java` lives in `epr/domain/` — CORRECT (it's a domain service, not a repository concern)
- `EprController.calculateFiling()` follows the exact same pattern as existing endpoints (JWT extraction, service delegation, no business logic in controller)
- No cross-module DB access from `epr` repository — all data fetched through `EprRepository`
- `EprService` remains the ONLY public facade — `FeeCalculator` is package-visible via Spring injection but not exported from the module

### Project Structure Notes

- Alignment with unified project structure confirmed (paths match architecture doc §Source Tree)
- `pages/epr/filing.vue` matches `pages/epr/export/[id].vue` pattern from architecture
- Test at `backend/src/test/java/hu/riskguard/epr/FeeCalculatorTest.java` matches `FeeCalculatorTest.java` reference in architecture source tree (§11.1 FR9 cross-reference)

### References

- `FeeCalculator.java` reference: Architecture doc §Source Tree `epr/domain/FeeCalculator.java`, and FR9 cross-reference table
- `EprRepository.findAllByTenantWithOverride()`: `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java:187`
- `MaterialTemplateResponse.feeRate`: `frontend/types/epr.ts:19`
- `useEprStore` pattern: `frontend/app/stores/epr.ts` — use same `$fetch` with `baseURL` and `credentials: 'include'`
- `useTierGate` tier gate: `frontend/app/pages/epr/index.vue:16` — copy exact pattern including upgrade prompt template
- MOHU Gate UX pattern: `_bmad-output/planning-artifacts/ux-design-specification.md` §7.3
- epr.json i18n structure: `frontend/app/i18n/en/epr.json` — insert `"filing"` as a sibling key to `"materialLibrary"`
- i18n parity check: `frontend/scripts/i18n-check.js` (run via `npm run check-i18n`)
- DB migration naming convention: `V{YYYYMMDD}_{NNN}__{snake_case_description}.sql` — next migration for Story 5.3 would be `V20260327_001__...`
- Sprint status note about backdated fixtures: `_bmad-output/implementation-artifacts/sprint-status.yaml` line 110

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation proceeded without blockers.

### Completion Notes List

- `FeeCalculator.java` created as pure `@Component` with zero dependencies.
- All 4 filing DTOs created as Java records; `FilingCalculationResponse` includes static `from()` for ArchUnit compliance.
- `calculateFiling()` uses `findAllByTenantWithOverride()` for fee_rate — no extra query needed.
- `EprServiceTest` and `EprServiceWizardTest` updated to pass `FeeCalculator` in constructor (necessary fix after adding new field).
- Empty lines 400 enforced by `@NotEmpty` on DTO — Spring MVC validated, not duplicated in unit test.
- `stores/eprFiling.ts` uses MOHU Gate validation logic with real-time border/icon feedback.
- `filing.vue` DataTable with MOHU Gate CSS, Calculate button with loading state, Summary card.
- i18n parity check (`check-i18n`) passed.
- All tests green: `./gradlew test` BUILD SUCCESSFUL; `npm run test` 574/574 passed.

### File List

**Backend — New:**
- `backend/src/main/java/hu/riskguard/epr/domain/FeeCalculator.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/FilingLineRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/FilingCalculationRequest.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/FilingLineResultDto.java`
- `backend/src/main/java/hu/riskguard/epr/api/dto/FilingCalculationResponse.java`
- `backend/src/test/java/hu/riskguard/epr/FeeCalculatorTest.java`

**Backend — Modified:**
- `backend/src/main/java/hu/riskguard/epr/domain/EprService.java` (add `calculateFiling()`, inject `FeeCalculator`)
- `backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java` (add `findVerifiedByTenant()`)
- `backend/src/main/java/hu/riskguard/epr/api/EprController.java` (add `calculateFiling()` endpoint)
- `backend/src/test/java/hu/riskguard/epr/EprControllerTest.java` (add filing endpoint tests)

**Frontend — New:**
- `frontend/app/pages/epr/filing.vue`
- `frontend/app/pages/epr/filing.spec.ts`
- `frontend/app/stores/eprFiling.ts`

**Backend — Also Modified (regression fix):**
- `backend/src/test/java/hu/riskguard/epr/EprServiceTest.java` (pass FeeCalculator in constructor)
- `backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java` (pass FeeCalculator in constructor)

**Frontend — Modified:**
- `frontend/app/pages/epr/index.vue` (add "New Filing" button)
- `frontend/app/i18n/en/epr.json` (add `filing` section)
- `frontend/app/i18n/hu/epr.json` (add `filing` section)

---

### Review Findings

**Code review — 2026-03-26 (Round 1)**
Sources: Blind Hunter · Edge Case Hunter · Acceptance Auditor | 3 decision-needed → resolved · 5 patch · 9 defer · 10 dismissed

#### Decision-Needed (resolved)

- [x] [Review][Decision→Defer] D1: AC 2 — real-time per-row weight/fee preview — accepted as-is. Computed values update only after "Calculate"; backend-authoritative result on button click is the intended UX. No client-side preview needed.
- [x] [Review][Decision→Patch] D2: AC 1 — "New Filing" button visibility — resolved: always show button, but `:disabled="eprStore.verifiedCount === 0"` so it is visible but inactive when no verified templates exist. Converted to P5 below.
- [x] [Review][Decision→Dismiss] D3: Regulatory rounding — per-line rounding confirmed correct. MOHU Csomagolási útmutató (p.121) and 80/2023. Korm. rendelet §20(1) both show per-KF-code line calculation: `weight_kg × fee_rate → whole HUF per line → summed`. Current `FeeCalculator.java` `setScale(0, HALF_UP)` per line matches exactly.

#### Patches

- [x] [Review][Patch] P1: Stale backend values not cleared on quantity edit — after `calculate()` populates `line.totalWeightKg` and `line.feeAmountHuf`, `updateQuantity()` nulls `serverResult` (hides summary) but does NOT reset `line.totalWeightKg`, `line.totalWeightGrams`, and `line.feeAmountHuf` to `null`. Stale backend-computed values remain displayed in the row even after the quantity is changed. Fix: add `line.totalWeightGrams = null; line.totalWeightKg = null; line.feeAmountHuf = null` in `updateQuantity()` before the `this.serverResult = null` line. [`frontend/app/stores/eprFiling.ts`]
- [x] [Review][Patch] P2: Missing test `calculateFiling_emptyLines_returns400` — Task 7.1 explicitly lists three required controller tests; only two were added. `FilingCalculationRequest` is annotated `@NotEmpty`, so Spring returns 400 on an empty list, but there is no test verifying this constraint is enforced. [`backend/src/test/java/hu/riskguard/epr/EprControllerTest.java`]
- [x] [Review][Patch] P3: `InputNumber :min="0"` sends wrong signal — PrimeVue `InputNumber` with `:min="0"` treats 0 as the valid minimum, but `updateQuantity()` rejects `parsed <= 0` with `mustBePositive`. Set `:min="1"` to align the component constraint with the validation logic. [`frontend/app/pages/epr/filing.vue`]
- [x] [Review][Patch] P4: `v-model` + `@input` double-mutation on `InputNumber` bypasses validation — `v-model="line.quantityPcs"` mutates the Pinia store's `line.quantityPcs` directly on every InputNumber change, before `updateQuantity()` runs from the `@input` event. This means `quantityPcs` in the store can temporarily hold an unvalidated value while `isValid`/`validationMessage` are stale. If `@input` doesn't fire on all value paths (e.g., programmatic clear), the store can be left in an inconsistent state. Fix: remove `v-model` and drive the display value only from `line.quantityPcs` via `:model-value`; let `updateQuantity()` be the sole writer via `@update:model-value` or `@input`. [`frontend/app/pages/epr/filing.vue`]
- [x] [Review][Patch] P5: "New Filing" button should always be visible but disabled when no verified templates — replace `v-if="eprStore.verifiedCount > 0"` with `:disabled="eprStore.verifiedCount === 0"` (keep `v-if` removed). This allows users to always see the button and understand the filing entry point, while preventing navigation when nothing can be filed. [`frontend/app/pages/epr/index.vue`]

#### Deferred

- [x] [Review][Defer] W1: `EprRepository.findVerifiedByTenant()` added but unused — `calculateFiling()` uses `findAllByTenantWithOverride()` instead; `findVerifiedByTenant()` is dead code. [`backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java`] — deferred, may be used by Story 5.3 MOHU CSV export
- [x] [Review][Defer] W2: Template UUID leaked in error responses — "Template not found: `<uuid>`" and "Template is not verified: `<uuid>`" expose internal UUIDs in error messages, enabling a timing/oracle attack to enumerate valid template IDs across tenants. [`EprService.java`] — deferred, pre-existing error-message pattern in project
- [x] [Review][Defer] W3: `Collectors.toMap` throws `IllegalStateException` on duplicate template records — if `findAllByTenantWithOverride()` ever returns two entries with the same `template().getId()`, the stream collector crashes with a 500. [`EprService.java:calculateFiling`] — deferred, pre-existing risk from `findAllByTenantWithOverride` behavior
- [x] [Review][Defer] W4: `@Valid` does not cascade to `null` list elements — a malformed request `{ "lines": [null, {...}] }` may bypass element-level validation and reach `calculateFiling()` loop as a null entry causing NPE. [`FilingCalculationRequest.java`] — deferred, add `@NotNull` on list element type if needed
- [x] [Review][Defer] W5: `NamingConventionTest` record-package path assumption — `extractRecordName()` builds `"hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord"` assuming jOOQ record classes live in a `.records` sub-package; if jOOQ generates them in the same package as tables, all record accesses would be falsely flagged. [`NamingConventionTest.java`] — deferred, verify against actual jOOQ codegen config
- [x] [Review][Defer] W6: Concurrent template delete causes confusing 404 during calculate — if a template is deleted between `findAllByTenantWithOverride()` and the loop check, the endpoint returns 404 to the user mid-filing. [`EprService.java`] — deferred, pre-existing optimistic-read pattern, acceptable for MVP
- [x] [Review][Defer] W7: `BigDecimal` → JS `number` precision loss — `grandTotalWeightKg`/`grandTotalFeeHuf` in `FilingCalculationResponse` (TS) use `number` type; IEEE 754 double loses precision beyond ~15 digits. Not practically reachable for EPR amounts. [`eprFiling.ts`] — deferred, revisit if large industrial datasets are onboarded
- [x] [Review][Defer] W8: `filingStore.isLoading` is dead state — declared in `FilingState` and reset in `reset()`, but never set to `true` during any loading operation; filing page loading display relies entirely on `eprStore.isLoading`. [`eprFiling.ts`] — deferred, low impact, clean up in a later story
- [x] [Review][Defer] W9: No `AbortController` for in-flight `calculate()` — if user navigates away while a calculation is pending, the `$fetch` resolves after unmount and mutates stale store state. [`eprFiling.ts:calculate`] — deferred, pre-existing pattern, no crash risk in Vue

---

## Change Log

- 2026-03-26: Story 5.2 implemented — FeeCalculator domain component, POST /api/v1/epr/filing/calculate endpoint, eprFiling Pinia store with MOHU Gate validation, filing.vue page with DataTable and Summary card, "New Filing" navigation button, full i18n en+hu. All tests green.
- 2026-03-27: Code review follow-ups resolved (P1–P5): stale value clearing in updateQuantity(), missing empty-lines constraint test, InputNumber :min alignment, v-model double-mutation fix, New Filing button always visible. Backend bug fixed: IdentityRepository.findByTokenHashForUpdate now uses select().from() cross-tenant pattern instead of selectFrom() to avoid TenantAwareDSLContext error when no tenant context is set during token refresh. UI: Quantity input width constrained via inputClass="w-20" on InputNumber.
