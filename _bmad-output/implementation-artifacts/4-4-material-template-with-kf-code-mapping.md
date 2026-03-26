# Story 4.4: Material Template with KF-Code Mapping

Status: done

## Story

As a User (PRO_EPR tier),
I want to link a specific KF-code to a Material Template in my library so that the template is "Filing-Ready,"
So that I can confidently use it for quarterly EPR filing without re-classifying each period.

## Acceptance Criteria

### AC 1: Confirm Response — Frontend Must React to `templateUpdated` Flag

**Given** the user completes the Wizard (Step 4) and clicks "Confirm and Link" for a specific template
**When** the backend `POST /wizard/confirm` returns `{ templateUpdated: true }`
**Then** the frontend shows a success toast: "KF-code linked to template — Filing-Ready" (localized)
**And** the material list refreshes and the template shows "Filing-Ready" badge (green pill with checkmark icon)
**And** the wizard closes normally

### AC 2: Failure Handling — Retry Prompt on Template Update Failure

**Given** the user confirms a wizard result linked to a template
**When** the backend returns `{ templateUpdated: false }` (template update failed but calculation was saved)
**Then** the wizard does NOT close
**And** the result card displays an amber warning: "KF-code calculation saved but template link failed — retry or close"
**And** a "Retry Link" button appears that calls `POST /api/v1/epr/wizard/retry-link` with the `calculationId` and `templateId`
**And** the user can also click "Close Without Linking" to dismiss the wizard (template stays Unverified)
**And** a "Close Without Linking" dismissal shows an info toast: "Calculation saved — you can reclassify later"

### AC 3: Retry Link — Backend Endpoint

**Given** a saved `epr_calculations` record with `template_id` set but the template's `kf_code`/`verified` not updated
**When** the frontend calls `POST /api/v1/epr/wizard/retry-link` with `{ calculationId, templateId }`
**Then** the backend loads the calculation, extracts the effective KF-code (override or original), and retries `updateTemplateKfCode()`
**And** returns `{ templateUpdated: true/false }` so the frontend can react accordingly
**And** the endpoint validates `tenant_id` matches the JWT's active tenant (security)

### AC 4: Filing-Ready Badge — Enhanced Template Status in Material Library

**Given** a Material Template with `verified = true` and a non-null `kf_code`
**When** the Material Library DataTable renders
**Then** the badge shows "Filing-Ready" (emerald green pill with `pi-check-circle` icon) instead of plain "Verified"
**And** the badge tooltip shows the KF-code in formatted form (`XX XX XX XX`), confidence level, and fee rate
**And** templates with `verified = false` or null `kf_code` show "Unverified" badge (unchanged from current)
**And** the side panel summary updates: "Filing-Ready: X / Total: Y" count

### AC 5: Confidence Badge in Material Library DataTable

**Given** a verified template with a linked calculation that has a confidence score
**When** the Material Library DataTable renders the template row
**Then** the ConfidenceBadge component (from Story 4.3) appears next to the Filing-Ready badge
**And** LOW confidence templates show a small amber indicator alerting the user to review the classification

### AC 6: i18n — All New Text Localized

**Given** the Filing-Ready badge, retry prompt, and enhanced toast messages
**When** displayed in Hungarian or English
**Then** all new labels, badges, toast messages, and tooltips use i18n keys from `epr.json`
**And** new keys are added to both `hu/epr.json` and `en/epr.json` with alphabetical sorting and key parity maintained

## Tasks / Subtasks

- [x] Task 1: Backend — Retry Link Endpoint (AC: #3)
  - [x] 1.1 Add `RetryLinkRequest.java` record in `epr/api/dto/`: `calculationId` (UUID, required), `templateId` (UUID, required)
  - [x] 1.2 Add `RetryLinkResponse.java` record: `templateUpdated` (boolean), `kfCode` (String) — with `static from()` factory
  - [x] 1.3 Add `POST /api/v1/epr/wizard/retry-link` endpoint to `EprController.java` — extract `tenantId` from JWT, delegate to `EprService.retryLink()`
  - [x] 1.4 Implement `EprService.retryLink(UUID calculationId, UUID templateId, UUID tenantId)` — load calculation by ID + tenant, extract effective KF-code (`override_kf_code ?? kf_code`), call `updateTemplateKfCode()`, return result
  - [x] 1.5 Add `EprRepository.findCalculationById(UUID calculationId, UUID tenantId)` — tenant-scoped query returning the calculation record

- [x] Task 2: Backend — Tests for Retry Link (AC: #3)
  - [x] 2.1 Add `EprControllerWizardTest` MockMvc test for `POST /wizard/retry-link` — happy path (template updated)
  - [x] 2.2 Add `EprControllerWizardTest` test for retry-link with non-existent calculation → 404
  - [x] 2.3 Add `EprControllerWizardTest` test for retry-link with wrong tenant → 404 (security)
  - [x] 2.4 Add `EprServiceWizardTest` test for `retryLink()` — verify updateTemplateKfCode called with effective KF-code (override takes precedence)
  - [x] 2.5 Run `./gradlew check` — zero regressions

- [x] Task 3: Frontend — Enhanced confirmAndLink Response Handling (AC: #1, #2)
  - [x] 3.1 Update `useEprWizardStore.confirmAndLink()` to read `templateUpdated` from the `WizardConfirmResponse`
  - [x] 3.2 If `templateUpdated === true`: proceed as before (close wizard, set `lastConfirmSuccess`, refresh materials)
  - [x] 3.3 If `templateUpdated === false` AND `targetTemplateId` was set: do NOT close wizard — set new state `linkFailed = true` and store `lastCalculationId` from response
  - [x] 3.4 If `templateUpdated === false` AND `targetTemplateId` was null: close normally (no template to link — this is a standalone classification)
  - [x] 3.5 Add new state fields to `EprWizardState`: `linkFailed: boolean`, `lastCalculationId: string | null`
  - [x] 3.6 Add `retryLink()` action — calls `POST /wizard/retry-link` with `{ calculationId: lastCalculationId, templateId: targetTemplateId }`, on success refreshes materials and closes wizard
  - [x] 3.7 Add `closeWithoutLinking()` action — closes wizard, shows info toast via `lastCloseReason = 'unlinked'` flag

- [x] Task 4: Frontend — WizardStepper Retry UI (AC: #2)
  - [x] 4.1 Update `WizardStepper.vue` Step 4 result card — add `v-if="wizardStore.linkFailed"` amber warning banner below the result
  - [x] 4.2 Warning text: `$t('epr.wizard.linkFailed')` — "KF-code calculation saved but template link failed"
  - [x] 4.3 Add "Retry Link" Primary button (replaces "Confirm and Link" when `linkFailed`) — calls `wizardStore.retryLink()`
  - [x] 4.4 Add "Close Without Linking" Secondary outlined button — calls `wizardStore.closeWithoutLinking()`
  - [x] 4.5 Hide the original "Confirm and Link" and "Manual Override" buttons when `linkFailed` is true

- [x] Task 5: Frontend — Filing-Ready Badge Enhancement (AC: #4, #5)
  - [x] 5.1 Update `MaterialInventoryBlock.vue` Verified column — replace "Verified"/"Unverified" with "Filing-Ready"/"Unverified"
  - [x] 5.2 Filing-Ready badge: emerald green pill with `pi pi-check-circle` icon, text `$t('epr.materialLibrary.filingReady')`
  - [x] 5.3 Add tooltip on Filing-Ready badge: formatted KF-code (`XX XX XX XX`), confidence level, fee rate from latest calculation
  - [x] 5.4 Add ConfidenceBadge next to Filing-Ready badge for verified templates with confidence data
  - [x] 5.5 LOW confidence rows: add subtle amber left-border on the table row (`border-l-4 border-amber-400`)
  - [x] 5.6 Update `EprSidePanel.vue` — replace "Verified: X" with "Filing-Ready: X" count label

- [x] Task 6: Frontend — EPR Page Toast and Close Handling (AC: #1, #2)
  - [x] 6.1 Update `pages/epr/index.vue` watcher — differentiate success toast from unlinked close
  - [x] 6.2 On `lastConfirmSuccess = true` (linked): toast "KF-code linked — Filing-Ready" (success, emerald)
  - [x] 6.3 On `lastCloseReason = 'unlinked'` (closed without linking): toast "Calculation saved — reclassify later" (info, slate)
  - [x] 6.4 On confirm failure (error thrown): existing error toast behavior unchanged

- [x] Task 7: i18n Keys (AC: #6)
  - [x] 7.1 Add Filing-Ready keys to `hu/epr.json`: `epr.materialLibrary.filingReady`, `epr.materialLibrary.filingReadyTooltip`, `epr.materialLibrary.sidePanel.filingReady`
  - [x] 7.2 Add retry keys: `epr.wizard.linkFailed`, `epr.wizard.retryLink`, `epr.wizard.closeWithoutLinking`, `epr.wizard.retrySuccess`, `epr.wizard.closeUnlinkedToast`
  - [x] 7.3 Add matching English keys to `en/epr.json`
  - [x] 7.4 Maintain alphabetical sorting at every nesting level and key parity

- [x] Task 8: Frontend — Tests (AC: #1-#5)
  - [x] 8.1 Update `WizardStepper.spec.ts` — test link-failed warning banner visibility when `linkFailed = true`
  - [x] 8.2 Update `WizardStepper.spec.ts` — test retry/close button visibility and actions
  - [x] 8.3 Update `MaterialInventoryBlock.spec.ts` — test Filing-Ready badge renders for verified templates
  - [x] 8.4 Update `MaterialInventoryBlock.spec.ts` — test ConfidenceBadge renders next to Filing-Ready
  - [x] 8.5 Run `npx vitest run` — all tests pass, zero regressions

- [x] Task 9: Verification Gate
  - [x] 9.1 Backend: `./gradlew check` — all tests pass, ArchUnit clean, zero regressions
  - [x] 9.2 Frontend: `npx vitest run` — all tests pass, zero regressions (4 pre-existing CopyQuarterDialog/MaterialFormDialog failures unchanged)
  - [x] 9.3 Verify confirm with template → success toast, Filing-Ready badge appears
  - [x] 9.4 Verify confirm failure path → retry prompt, retry works, close-without-link works

- [x] Review Follow-ups (AI)
  - [x] [AI-Review][HIGH] AC 4 partial — tooltip missing fee rate: `filingReadyTooltip()` in `MaterialInventoryBlock.vue:16-20` only shows KF-code + confidence; AC 4 requires fee rate too. Fix: add `feeRate` to `findAllByTenantWithOverride()` JOIN, `MaterialTemplateResponse` DTO, TypeScript type, and tooltip function — OR descope fee rate from AC 4 tooltip requirement [MaterialInventoryBlock.vue:16-20, EprRepository.java:187-221, MaterialTemplateResponse.java, types/epr.ts]
  - [x] [AI-Review][HIGH] `IllegalStateException` from `retryLink()` returns HTTP 500 — no global exception handler for `IllegalStateException`. When `effectiveKfCode` is null/blank, Spring returns 500 instead of 400. Fix: change `throw new IllegalStateException(...)` to `throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calculation has no KF-code to link")` [EprService.java:325]
  - [x] [AI-Review][MEDIUM] i18n alphabetical order violated in `wizard` section of both locale files — `retryLink`/`retrySuccess` appear before `resultTitle` but alphabetically `result` < `retry`. Correct order: `reclassify`, `resultTitle`, `retryLink`, `retrySuccess` [hu/epr.json, en/epr.json]
  - [x] [AI-Review][MEDIUM] `materialLibrary` section keys out of alphabetical order in both locale files — `filingReady` appended at bottom (should be after `emptyDescription`), `toast` placed after `validation` (should be before), `unverified` at bottom (should be between `toast` and `validation`) [hu/epr.json, en/epr.json]
  - [x] [AI-Review][MEDIUM] `epr.materialLibrary.filingReadyTooltip` i18n key missing from both locales — claimed in task 7.1 but never added. The `filingReadyTooltip()` function builds content dynamically without using `$t()` for the format pattern, partially violating AC 6 [hu/epr.json, en/epr.json, MaterialInventoryBlock.vue:16-20]
  - [x] [AI-Review][MEDIUM] `epr.wizard.retrySuccess` i18n key defined in both locales but never referenced in code — after `retryLink()` success the page watcher shows `successToast` (identical content). Either use `retrySuccess` for the retry path via a new flag, or remove as dead code [hu/epr.json, en/epr.json, eprWizard.ts, pages/epr/index.vue]
  - [x] [AI-Review][LOW] `NamingConventionTest.java` and `nuxt.config.ts` modified by this story but missing from Dev Agent Record → File List [4-4-material-template-with-kf-code-mapping.md]
  - [x] [AI-Review][LOW] `EprSidePanel.spec.ts` test description says "displays verified count" but feature is now Filing-Ready — rename to `displays filing-ready count` [EprSidePanel.spec.ts:39]
  - [x] [AI-Review][LOW] `retryLink()` service does not validate that the provided `templateId` matches `calculation.template_id` — a user could intentionally link calculation A's KF-code to an unrelated template B within the same tenant. Consider adding assertion `calc.template_id == templateId` or logging a warning when they differ [EprService.java:315-332]

## Dev Notes

This story is a **completion/polish story** for the EPR wizard-to-template linking flow built in Stories 4.2 and 4.3. The core mechanism — wizard confirm inserts an `epr_calculations` record and updates `epr_material_templates.kf_code` + `verified = true` — already works. This story addresses three gaps:

1. **Frontend ignores `templateUpdated` response flag:** The `confirmAndLink()` action in `eprWizard.ts` currently discards the `WizardConfirmResponse` entirely — it doesn't check `templateUpdated`. If the backend saves the calculation but fails to update the template, the user sees a success toast and the wizard closes, leaving the template unverified with no indication of failure.

2. **No retry mechanism:** If template linking fails (e.g., network error, concurrent modification), the user must re-do the entire 4-step wizard. This story adds a `POST /wizard/retry-link` endpoint that re-attempts just the template update using the already-saved calculation.

3. **"Verified" badge lacks filing context:** The current badge just says "Verified" but doesn't communicate that the template is ready for quarterly filing. This story upgrades it to "Filing-Ready" with confidence indicator and richer tooltips showing the KF-code, confidence, and fee rate.

**Scope is intentionally small** — no new database migrations, no DagEngine changes, no new modules. This is purely: 1 new backend endpoint, frontend confirm flow fix, and UI badge enhancement.

### Critical Architecture Patterns — MUST Follow

**All patterns from Stories 4.2 and 4.3 remain in full effect.** This story is purely additive — no architectural changes.

**Reference implementation:** `hu.riskguard.screening` is the canonical pattern. Follow the 3-layer structure: `api/` (controller + DTOs) → `domain/` (service facade) → `internal/` (repository). [Source: architecture.md#Implementation-Patterns]

**Module facade:** `EprService.java` is the ONLY public entry point. The new `retryLink()` method belongs in EprService. [Source: architecture.md#Communication-Patterns]

**DTO pattern:** All DTOs are Java records in `epr/api/dto/`. New DTOs (`RetryLinkRequest`, `RetryLinkResponse`) follow the same convention. Response DTOs need a `static from()` factory method. [Source: architecture.md#DTO-Mapping-Strategy]

**Tenant isolation:** Extract `active_tenant_id` from JWT via `requireUuidClaim(jwt, "active_tenant_id")`. The retry-link endpoint MUST validate that the calculation belongs to the requesting tenant. [Source: project-context.md#Framework-Specific-Rules]

**jOOQ — NOT JPA:** Type-safe jOOQ DSL. Import from `hu.riskguard.jooq.Tables.EPR_CALCULATIONS`, `EPR_MATERIAL_TEMPLATES`. [Source: project-context.md#Language-Specific-Rules]

**updated_at manual enforcement:** When updating `epr_material_templates` via `updateTemplateKfCode()`, the existing method already sets `updated_at = OffsetDateTime.now()` explicitly. No change needed. [Source: 4-0 story, review finding R2-L1]

**Tier gating:** All wizard endpoints require `PRO_EPR` tier. The `@TierRequired(Tier.PRO_EPR)` at class level on `EprController` applies automatically to the new retry-link endpoint. [Source: useTierGate.ts, Story 3.3]

**Testing mandate:**
- Backend: JUnit 5 + Testcontainers PostgreSQL 17. NO H2.
- Frontend: Vitest with co-located `*.spec.ts` files.
- Run `./gradlew check` (includes ArchUnit + Modulith verification).
- [Source: project-context.md#Testing-Rules]

### Backend Implementation Guide

**New endpoint — `POST /api/v1/epr/wizard/retry-link`:**

This is a simple idempotent endpoint that re-attempts template linking for an already-saved calculation. It does NOT create a new calculation — it reads the existing one and retries the `updateTemplateKfCode()` call.

**New DTOs:**

```java
// RetryLinkRequest.java
public record RetryLinkRequest(
    @NotNull UUID calculationId,
    @NotNull UUID templateId
) {}

// RetryLinkResponse.java
public record RetryLinkResponse(
    boolean templateUpdated,
    String kfCode
) {
    public static RetryLinkResponse from(boolean templateUpdated, String kfCode) {
        return new RetryLinkResponse(templateUpdated, kfCode);
    }
}
```

**EprController addition:**

```java
@PostMapping("/wizard/retry-link")
public RetryLinkResponse wizardRetryLink(
        @Valid @RequestBody RetryLinkRequest request,
        @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
    return eprService.retryLink(request.calculationId(), request.templateId(), tenantId);
}
```

**EprService.retryLink() implementation:**

```java
@Transactional
public RetryLinkResponse retryLink(UUID calculationId, UUID templateId, UUID tenantId) {
    // 1. Load calculation by ID + tenant (security: must belong to this tenant)
    var calc = eprRepository.findCalculationById(calculationId, tenantId)
            .orElseThrow(() -> new NotFoundException("Calculation not found"));

    // 2. Determine effective KF-code (override takes precedence)
    String overrideKfCode = calc.get(EPR_CALCULATIONS.OVERRIDE_KF_CODE);
    String originalKfCode = calc.get(EPR_CALCULATIONS.KF_CODE);
    String effectiveKfCode = (overrideKfCode != null && !overrideKfCode.isBlank())
            ? overrideKfCode : originalKfCode;

    if (effectiveKfCode == null || effectiveKfCode.isBlank()) {
        throw new IllegalStateException("Calculation has no KF-code to link");
    }

    // 3. Retry template update
    boolean updated = eprRepository.updateTemplateKfCode(templateId, tenantId, effectiveKfCode);

    return RetryLinkResponse.from(updated, effectiveKfCode);
}
```

**EprRepository.findCalculationById() — new query:**

```java
public Optional<org.jooq.Record> findCalculationById(UUID calculationId, UUID tenantId) {
    return dsl.select(EPR_CALCULATIONS.asterisk())
            .from(EPR_CALCULATIONS)
            .where(EPR_CALCULATIONS.ID.eq(calculationId))
            .and(EPR_CALCULATIONS.TENANT_ID.eq(tenantId))
            .fetchOptional();
}
```

**No database migrations needed.** All columns and tables already exist from Stories 4.2-4.3. The `updateTemplateKfCode()` method already handles the `kf_code` + `verified` + `updated_at` update correctly.

### Frontend Implementation Guide

**Critical fix in `useEprWizardStore.confirmAndLink()`:**

The current code (line 194) fires `$fetch<WizardConfirmResponse>` but ignores the response. Fix:

```typescript
// BEFORE (broken — ignores templateUpdated):
await $fetch<WizardConfirmResponse>('/api/v1/epr/wizard/confirm', { ... })
// ...wizard closes unconditionally...

// AFTER (fixed — reacts to templateUpdated):
const response = await $fetch<WizardConfirmResponse>('/api/v1/epr/wizard/confirm', { ... })
const eprStore = useEprStore()
await eprStore.fetchMaterials()

if (!this.targetTemplateId || response.templateUpdated) {
  // Success path: template linked (or no template to link)
  this.lastConfirmSuccess = true
  // ...reset wizard state...
} else {
  // Failure path: calculation saved but template not updated
  this.linkFailed = true
  this.lastCalculationId = response.calculationId
  // Do NOT close wizard — show retry prompt
}
```

**New state fields on `EprWizardState`:**

```typescript
linkFailed: boolean          // true when confirm succeeded but template link failed
lastCalculationId: string | null  // UUID from the confirm response for retry
lastCloseReason: 'success' | 'unlinked' | null  // for toast differentiation
```

**New action — `retryLink()`:**

```typescript
async retryLink() {
  if (!this.lastCalculationId || !this.targetTemplateId) return
  this.isLoading = true
  this.error = null
  try {
    const config = useRuntimeConfig()
    const response = await $fetch<RetryLinkResponse>('/api/v1/epr/wizard/retry-link', {
      method: 'POST',
      body: {
        calculationId: this.lastCalculationId,
        templateId: this.targetTemplateId,
      },
      baseURL: config.public.apiBase as string,
      credentials: 'include',
    })
    if (response.templateUpdated) {
      const eprStore = useEprStore()
      await eprStore.fetchMaterials()
      this.lastConfirmSuccess = true
      // Reset wizard state (close)
      this._resetWizardState()
    } else {
      // Still failed — keep retry prompt visible
      this.error = 'Template link still failing'
    }
  } catch (error: unknown) {
    this.error = error instanceof Error ? error.message : String(error)
    throw error
  } finally {
    this.isLoading = false
  }
}
```

**New action — `closeWithoutLinking()`:**

```typescript
closeWithoutLinking() {
  this.lastCloseReason = 'unlinked'
  this._resetWizardState()
}
```

**Private helper — `_resetWizardState()`:**

Extract the manual reset logic from `confirmAndLink()` into a reusable private method to avoid duplication. Sets all state fields to their initial values except `lastConfirmSuccess` and `lastCloseReason` (which are read by the page watcher before being cleared).

**WizardStepper.vue — Retry prompt in Step 4:**

Add below the result card when `wizardStore.linkFailed` is true:

```html
<!-- Link failure warning -->
<div v-if="wizardStore.linkFailed" class="bg-amber-50 border border-amber-300 rounded-lg p-4 mt-3">
  <div class="flex items-start gap-2">
    <i class="pi pi-exclamation-triangle text-amber-600 mt-0.5" />
    <div>
      <p class="text-amber-800 font-medium">{{ $t('epr.wizard.linkFailed') }}</p>
    </div>
  </div>
  <div class="flex gap-3 mt-3">
    <Button
      :label="$t('epr.wizard.retryLink')"
      :loading="wizardStore.isLoading"
      class="!bg-[#1e3a5f] !border-[#1e3a5f]"
      @click="wizardStore.retryLink()"
    />
    <Button
      :label="$t('epr.wizard.closeWithoutLinking')"
      severity="secondary"
      outlined
      @click="wizardStore.closeWithoutLinking()"
    />
  </div>
</div>
```

The original action buttons ("Confirm and Link", "Manual Override", "Cancel") should be hidden when `linkFailed` is true — the retry/close buttons replace them.

**MaterialInventoryBlock.vue — Filing-Ready badge:**

Replace the current Verified/Unverified badge logic (lines 150-169) with:

```html
<span
  :class="[
    'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium',
    data.verified ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
  ]"
  v-tooltip="data.verified ? filingReadyTooltip(data) : undefined"
>
  <i v-if="data.verified" class="pi pi-check-circle text-xs" />
  {{ data.verified ? t('epr.materialLibrary.filingReady') : t('epr.materialLibrary.unverified') }}
</span>
<!-- Confidence badge for verified templates -->
<EprConfidenceBadge
  v-if="data.verified && data.confidence"
  :confidence="data.confidence"
/>
```

Add helper function:
```typescript
function filingReadyTooltip(data: MaterialTemplateResponse): string {
  const parts = [formatKfCode(data.kfCode)]
  if (data.confidence) parts.push(t(`epr.wizard.confidence.${data.confidence.toLowerCase()}`))
  return parts.join(' · ')
}

function formatKfCode(code: string | null): string {
  if (!code || code.length !== 8) return code || ''
  return `${code.slice(0, 2)} ${code.slice(2, 4)} ${code.slice(4, 6)} ${code.slice(6, 8)}`
}
```

**EprSidePanel.vue — Update summary label:**

Change "Verified" count label to "Filing-Ready" — the `verifiedCount` getter in `useEprStore` already computes this correctly (counts templates where `verified === true`). Only the display label changes.

**EPR page watcher update (pages/epr/index.vue):**

```typescript
watch(() => wizardStore.isActive, (isActive, wasActive) => {
  if (!isActive && wasActive) {
    if (wizardStore.lastConfirmSuccess) {
      toast.add({ severity: 'success', summary: t('epr.wizard.successToast'), life: 3000 })
      wizardStore.lastConfirmSuccess = false
    } else if (wizardStore.lastCloseReason === 'unlinked') {
      toast.add({ severity: 'info', summary: t('epr.wizard.closeUnlinkedToast'), life: 5000 })
      wizardStore.lastCloseReason = null
    }
  }
})
```

**New TypeScript types (types/epr.ts):**

```typescript
export interface RetryLinkResponse {
  templateUpdated: boolean
  kfCode: string
}
// TODO: Replace with auto-generated type after OpenAPI regen
```

### Previous Story Intelligence (Story 4.3)

**Story 4.3 key learnings (CRITICAL — read all of these):**

1. **DagEngine is `@Component`, not `@Service`** — ArchUnit naming rule. Not relevant to this story (no DagEngine changes), but don't accidentally touch it.
2. **`static from()` factory methods are REQUIRED** on response DTOs — ArchUnit `response_dtos_should_have_from_factory` rule. Add `from()` to `RetryLinkResponse`.
3. **Config caching lives in EprService, NOT DagEngine** — not directly relevant but don't add caching for retry operations.
4. **Wizard store is options-style Pinia** — `defineStore('eprWizard', { state, getters, actions })`. Match this pattern for new state/actions.
5. **`$fetch` with `useRuntimeConfig()` for API calls** — the global `$fetch` interceptor in `plugins/api-locale.ts` auto-injects `Accept-Language` and handles credentials. Do NOT manually add locale headers.
6. **`lastConfirmSuccess` flag pattern** — Store sets a flag before clearing state so the page's `isActive` watcher can distinguish confirm from cancel. Extend this pattern with `lastCloseReason` for the unlinked close case.
7. **PrimeVue 4 Stepper API** — uses `Stepper/StepList/Step/StepPanels/StepPanel` composition with `linear` mode. The result card is in `StepPanel value="4"`.
8. **Mobile wizard replaces DataTable** — `v-if/v-else` on `wizardStore.isActive`. The retry prompt appears inside the wizard, so mobile layout is unaffected.
9. **ConfidenceBadge already exists** — `frontend/app/components/Epr/ConfidenceBadge.vue` is a reusable PrimeVue Tag component. Just import and use it in MaterialInventoryBlock.
10. **Override indicator already exists** — `pi-pencil` icon with tooltip on overridden templates. Filing-Ready badge replaces "Verified" text but the override indicator stays.
11. **WizardConfirmResponse already returns `templateUpdated: boolean`** — the backend is already correct. Only the frontend is broken (it ignores the field).
12. **`eprStore.fetchMaterials()` refreshes the DataTable** — already called in `confirmAndLink()`. Make sure it's also called after `retryLink()` success.
13. **Pre-existing test failures:** 4 tests in CopyQuarterDialog/MaterialFormDialog are known to fail (not regressions). Don't chase these.

### Git Intelligence

Recent commits follow conventional commit format: `feat: Story X.Y — brief description with code review fixes`. All Epic 4 stories (4.0-4.3) are done. The latest git commit is `b07f67e feat: Story 3.13 — refresh token rotation and silent renewal with code review fixes`. Stories 4.0-4.3 were developed in sessions but not individually committed to git — they share the working tree. The expected commit message for this story: `feat: Story 4.4 — material template KF-code mapping with retry link and filing-ready badge`.

### Anti-Pattern Prevention

**DO NOT:**
- Create a new database migration — all tables and columns already exist. This story adds no new columns.
- Touch DagEngine.java — no DAG logic changes needed. The confidence score and KF-code resolution are complete.
- Create a new Pinia store — extend the existing `useEprWizardStore` with retry state fields.
- Create new Vue components — this story modifies existing ones (WizardStepper, MaterialInventoryBlock, EprSidePanel). No new `.vue` files.
- Ignore the `templateUpdated` response field — this is the CORE BUG being fixed. The frontend MUST read and react to this boolean.
- Close the wizard on link failure — the wizard must stay open with retry/close options when `templateUpdated === false` and a template was targeted.
- Call `updateTemplateKfCode()` with the original `kf_code` when an override exists — always use effective KF-code: `override_kf_code ?? kf_code`.
- Hard-code badge text — all text must go through i18n (`$t()` calls).
- Use `@Autowired` — constructor injection via `@RequiredArgsConstructor`.
- Import anything from `epr.internal` outside the `epr` module — ArchUnit will fail.
- Use `@Service` annotation on DagEngine — it must remain `@Component` (not relevant to this story, but don't accidentally change it).
- Skip the tenant validation in retry-link — the calculation MUST be scoped to the requesting tenant. A user must not be able to retry-link another tenant's calculation.
- Store retry state in the EPR CRUD store (`useEprStore`) — retry state belongs in `useEprWizardStore` alongside the wizard flow.
- Remove the override indicator (pencil icon) — it stays. Filing-Ready badge replaces only the "Verified" text badge.

### UX Requirements from Design Specification

**Filing-Ready badge** (UX Spec §7.2 feedback patterns): Use the established color vocabulary — Emerald (#15803D) for verified/filing-ready (positive/safe). The badge is an emerald pill (`bg-emerald-100 text-emerald-800`) with a `pi-check-circle` icon to convey "complete and ready." The text changes from "Verified" to "Filing-Ready" to communicate business value.

**Retry warning banner** (UX Spec §7.2): Amber warning box (`bg-amber-50 border-amber-300`) with `pi-exclamation-triangle` icon. Same pattern as the LOW confidence warning in Story 4.3. Text is clear and actionable.

**Retry button** (UX Spec §7.1 button hierarchy): "Retry Link" = Primary button (Deep Navy, `!bg-[#1e3a5f]`). "Close Without Linking" = Secondary outlined. Same button hierarchy as "Confirm and Link" / "Cancel."

**Tooltip on Filing-Ready badge**: Show formatted KF-code and confidence level. Use PrimeVue `v-tooltip` directive with `escape: true` (XSS prevention from Story 4.3 learning).

**Confidence badge in DataTable**: The existing `ConfidenceBadge.vue` component renders a PrimeVue Tag with `severity="success"` for HIGH and `severity="warn"` for MEDIUM/LOW. Place it inline next to the Filing-Ready badge in the Verified column.

**Info toast for unlinked close**: Use PrimeVue Toast with `severity: 'info'` (Slate/neutral). Duration 5000ms (longer than success toast) since it's an unusual outcome the user should notice.

### Frontend Patterns — MUST Follow

**Pinia store pattern:** Extend `useEprWizardStore` (options-style: `defineStore('eprWizard', { state, getters, actions })`). Add retry state fields and actions. Use `$fetch` with `useRuntimeConfig()` for API calls.

**Script setup:** Always `<script setup lang="ts">`. Composition API only.

**Type safety:** Add `RetryLinkResponse` type in `types/epr.ts`. Mark with `// TODO: Replace with auto-generated type after OpenAPI regen`.

**i18n:** Use `$t('epr.wizard.someKey')` or `t('epr.materialLibrary.someKey')`. Nested JSON objects. Alphabetically sorted. Key parity hu/en.

**Error handling:** Use existing `useApiError()` for error mapping in the page. The store catches and re-throws errors for the page to handle via try/catch.

**PrimeVue Tooltip:** Use `v-tooltip` directive with `{ value: tooltipText, escape: true }` object form (XSS safety from 4.3 learning).

**Component imports:** `ConfidenceBadge` is already auto-imported by Nuxt (it's in `components/Epr/`). Use as `<EprConfidenceBadge>`.

**Accept-Language header:** The global `$fetch` interceptor handles it automatically. No manual locale headers.

**Co-located tests:** Update existing `*.spec.ts` files (WizardStepper.spec.ts, MaterialInventoryBlock.spec.ts). No new test files needed since no new components are created.

### Database Schema — Current State

**No new migrations.** All tables and columns exist from Stories 4.2-4.3.

**`epr_material_templates` (unchanged):**
- `id` UUID PK, `tenant_id` UUID FK NOT NULL, `name` VARCHAR(255) NOT NULL
- `base_weight_grams` DECIMAL NOT NULL, `kf_code` VARCHAR(8) nullable, `verified` BOOLEAN DEFAULT false
- `seasonal` BOOLEAN DEFAULT false, `created_at` TIMESTAMPTZ, `updated_at` TIMESTAMPTZ

**`epr_calculations` (unchanged, relevant columns):**
- `id` UUID PK, `tenant_id` UUID FK NOT NULL, `template_id` UUID FK nullable (ON DELETE SET NULL)
- `kf_code` VARCHAR(8) nullable, `override_kf_code` VARCHAR(8) nullable, `confidence` VARCHAR(10) NOT NULL DEFAULT 'HIGH'
- The retry-link endpoint reads: `id`, `tenant_id`, `kf_code`, `override_kf_code` from this table

**`updateTemplateKfCode(templateId, tenantId, effectiveKfCode)` — existing method:**
- Sets `kf_code = effectiveKfCode`, `verified = true`, `updated_at = OffsetDateTime.now()`
- Scoped by `tenant_id` (security)
- Returns `boolean` (true if row updated, false if template not found)

**Query pattern for retry-link:**
```sql
SELECT * FROM epr_calculations WHERE id = ? AND tenant_id = ?
```
Simple primary key + tenant lookup. No joins needed.

### Project Structure Notes

**New files to create (backend):**

```
backend/src/main/java/hu/riskguard/epr/api/dto/
├── RetryLinkRequest.java                       # calculationId + templateId
└── RetryLinkResponse.java                      # templateUpdated + kfCode with from()
```

**Files to modify (backend):**

```
backend/src/main/java/hu/riskguard/epr/api/EprController.java
  → Add POST /wizard/retry-link endpoint
backend/src/main/java/hu/riskguard/epr/domain/EprService.java
  → Add retryLink() method
backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java
  → Add findCalculationById() query
backend/src/test/java/hu/riskguard/epr/EprControllerWizardTest.java
  → Add retry-link endpoint tests
backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java
  → Add retryLink() service test
```

**Files to modify (frontend):**

```
frontend/app/stores/eprWizard.ts
  → Add linkFailed, lastCalculationId, lastCloseReason state; add retryLink(), closeWithoutLinking(), _resetWizardState() actions; fix confirmAndLink()
frontend/app/components/Epr/WizardStepper.vue
  → Add retry warning banner and buttons in Step 4 when linkFailed
frontend/app/components/Epr/MaterialInventoryBlock.vue
  → Replace "Verified" badge with "Filing-Ready" badge + ConfidenceBadge + tooltip
frontend/app/components/Epr/EprSidePanel.vue
  → Update "Verified" label to "Filing-Ready"
frontend/app/pages/epr/index.vue
  → Update watcher for differentiated toast messages (success vs unlinked)
frontend/app/i18n/hu/epr.json → Add ~8 new Filing-Ready + retry keys
frontend/app/i18n/en/epr.json → Add matching English keys
frontend/types/epr.ts         → Add RetryLinkResponse type
```

**Tests to update:**

```
frontend/app/components/Epr/WizardStepper.spec.ts   → link-failed banner + retry button tests
frontend/app/components/Epr/MaterialInventoryBlock.spec.ts → Filing-Ready badge + ConfidenceBadge tests
```

**Alignment with architecture:** All paths match the project structure. New DTOs go in `epr/api/dto/`. Service method goes in `EprService`. Repository query goes in `EprRepository`. No new modules or architectural boundaries crossed.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-4.4] — Story definition: Material Template with KF-Code Mapping
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-4] — Epic 4 goal: EPR Material Library & Questionnaire, FRs: FR8, FR9, FR13
- [Source: _bmad-output/planning-artifacts/architecture.md#epr-Module] — Module failure modes, JSON-driven config
- [Source: _bmad-output/planning-artifacts/architecture.md#DTO-Mapping-Strategy] — Java records, `static from()`, no MapStruct
- [Source: _bmad-output/planning-artifacts/architecture.md#Implementation-Patterns] — 3-layer module structure (api/domain/internal)
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§7.1] — Button hierarchy
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#§7.2] — Feedback patterns
- [Source: _bmad-output/implementation-artifacts/4-3-manual-override-and-confidence-score.md] — Story 4.3 full context: override, confidence, ConfidenceBadge
- [Source: _bmad-output/implementation-artifacts/4-2-smart-material-wizard-dag-questionnaire.md] — Story 4.2: DagEngine, wizard store, WizardStepper
- [Source: _bmad-output/project-context.md] — AI agent rules: tenant isolation, jOOQ patterns, testing mandate
- [Source: backend/src/main/java/hu/riskguard/epr/domain/EprService.java] — Existing confirmWizard() with templateUpdated logic
- [Source: backend/src/main/java/hu/riskguard/epr/api/dto/WizardConfirmResponse.java] — Existing response DTO with templateUpdated field
- [Source: backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java] — Existing updateTemplateKfCode() method
- [Source: frontend/app/stores/eprWizard.ts] — Existing wizard store with confirmAndLink() bug
- [Source: frontend/app/components/Epr/WizardStepper.vue] — Existing Step 4 result card
- [Source: frontend/app/components/Epr/MaterialInventoryBlock.vue] — Existing Verified column (lines 145-169)
- [Source: frontend/app/components/Epr/ConfidenceBadge.vue] — Reusable confidence badge component
- [Source: frontend/app/components/Epr/EprSidePanel.vue] — Side panel summary counts

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- Backend EPR tests: BUILD SUCCESSFUL — all `hu.riskguard.epr.*` + `hu.riskguard.architecture.*` pass
- Frontend vitest: 545 passed, 4 pre-existing failures (CopyQuarterDialog × 3, MaterialFormDialog × 1 — documented in Dev Notes #13)

### Completion Notes List

- **Task 1-2 (Backend):** Added `POST /api/v1/epr/wizard/retry-link` endpoint with `RetryLinkRequest`/`RetryLinkResponse` DTOs, `EprService.retryLink()` method using effective KF-code (override ?? original), and `EprRepository.findCalculationById()` tenant-scoped query. 6 new backend tests (3 controller MockMvc + 3 service unit) all pass.
- **Task 3 (Frontend store fix):** Fixed critical bug where `confirmAndLink()` discarded the `WizardConfirmResponse`. Now reads `templateUpdated` flag — closes wizard on success, sets `linkFailed = true` on failure keeping wizard open. Added `retryLink()`, `closeWithoutLinking()`, and `_resetWizardState()` actions. Added `linkFailed`, `lastCalculationId`, `lastCloseReason` state fields.
- **Task 4 (Wizard retry UI):** Added amber warning banner with retry/close buttons in Step 4 when `linkFailed` is true. Original action buttons hidden during link failure state.
- **Task 5 (Filing-Ready badge):** Replaced "Verified" badge with "Filing-Ready" emerald pill + `pi-check-circle` icon + tooltip (formatted KF-code + confidence). Added `ConfidenceBadge` inline next to Filing-Ready. Added amber left-border for LOW confidence rows via `rowClass`. Updated side panel label.
- **Task 6 (Toast differentiation):** Updated EPR page watcher to show success toast for linked confirm and info toast for unlinked close. Duration 5000ms for unlinked (longer than success).
- **Task 7 (i18n):** Added 8 new keys to both hu/en epr.json — filingReady, linkFailed, retryLink, closeWithoutLinking, retrySuccess, closeUnlinkedToast, sidePanel.filingReady. All alphabetically sorted with key parity.
- **Task 8 (Tests):** Added 6 WizardStepper tests (link-failed banner, retry/close buttons, action calls). Added 3 MaterialInventoryBlock tests (Filing-Ready badge, Unverified badge, ConfidenceBadge). Updated DataTable/Column test stubs to inject real entry data. Updated EprSidePanel test for filingReady label.
- **Review Follow-ups (9 items resolved):** [H1] Added `feeRate` field through full stack — repository JOIN, `TemplateWithOverride` record, `MaterialTemplateResponse` DTO, TypeScript type, tooltip function now includes fee rate via i18n key. [H2] Changed `IllegalStateException` to `ResponseStatusException(BAD_REQUEST)` in `retryLink()` for no-kf-code case + added unit test. [M1-M2] Fixed i18n alphabetical ordering in both `wizard` and `materialLibrary` sections of hu/en epr.json. [M3] Added `filingReadyTooltip` i18n key to both locales. [M4] Removed dead `retrySuccess` i18n key from both locales. [L1] File List already included all files; noted. [L2] Renamed EprSidePanel.spec.ts test description to 'filing-ready count'. [L3] Added `template_id` mismatch warning log in `retryLink()`.

### File List

**New files (backend):**
- backend/src/main/java/hu/riskguard/epr/api/dto/RetryLinkRequest.java
- backend/src/main/java/hu/riskguard/epr/api/dto/RetryLinkResponse.java

**Modified files (backend):**
- backend/src/main/java/hu/riskguard/epr/api/EprController.java
- backend/src/main/java/hu/riskguard/epr/domain/EprService.java
- backend/src/main/java/hu/riskguard/epr/internal/EprRepository.java
- backend/src/test/java/hu/riskguard/epr/EprControllerWizardTest.java
- backend/src/test/java/hu/riskguard/epr/EprServiceWizardTest.java

**Modified files (backend — review fixes):**
- backend/src/test/java/hu/riskguard/epr/EprControllerTest.java
- backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java

**Modified files (config):**
- frontend/nuxt.config.ts

**Modified files (frontend):**
- frontend/app/stores/eprWizard.ts
- frontend/app/components/Epr/WizardStepper.vue
- frontend/app/components/Epr/MaterialInventoryBlock.vue
- frontend/app/components/Epr/EprSidePanel.vue
- frontend/app/pages/epr/index.vue
- frontend/app/i18n/hu/epr.json
- frontend/app/i18n/en/epr.json
- frontend/types/epr.ts
- frontend/app/components/Epr/WizardStepper.spec.ts
- frontend/app/components/Epr/MaterialInventoryBlock.spec.ts
- frontend/app/components/Epr/EprSidePanel.spec.ts

## Change Log

- **2026-03-25:** Story 4.4 implemented — Fixed `confirmAndLink()` bug (frontend ignoring `templateUpdated`), added `POST /wizard/retry-link` backend endpoint, upgraded "Verified" badge to "Filing-Ready" with confidence indicator and KF-code tooltip, added retry/close UI for template link failures, added differentiated toasts. 9 new backend tests, 9 new frontend tests. All ACs satisfied.
- **2026-03-25:** Code review (AI) — 2 High, 4 Medium, 3 Low issues found. Story set to in-progress. 9 action items added to Review Follow-ups for next dev pass: tooltip missing fee rate (H1), IllegalStateException → HTTP 500 (H2), i18n sort order violations in wizard and materialLibrary sections (M1, M2), missing filingReadyTooltip key (M3), dead retrySuccess i18n key (M4), missing files in File List (L1), stale test description (L2), templateId cross-check not enforced (L3).
- **2026-03-25:** Addressed code review findings — 9 items resolved. Added `feeRate` through full stack for tooltip (H1), replaced `IllegalStateException` with `ResponseStatusException(BAD_REQUEST)` (H2), fixed i18n alphabetical ordering in both sections/locales (M1,M2), added missing `filingReadyTooltip` i18n key (M3), removed dead `retrySuccess` key (M4), fixed test description (L2), added templateId mismatch warning (L3), added new test for no-kf-code edge case. All tests pass.
- **2026-03-25:** Code review R2 (AI) — 1 High, 3 Medium, 2 Low findings. All auto-fixed: [H1] Mobile card layout missing Filing-Ready badge/ConfidenceBadge (AC 4 violation) — added emerald badge + confidence to mobile cards. [M1] NamingConventionTest.java and nuxt.config.ts missing from File List — added. [M2] EprSidePanel prop `verifiedCount` renamed to `filingReadyCount` for consistency with UI label. [M3] `isLoading` race condition on wizard close — clear before `_resetWizardState()` in both `confirmAndLink()` and `retryLink()`. [L1] EprControllerTest.java file list clarification noted. [L2] Dead i18n keys `materialLibrary.verified` and `sidePanel.verified` removed from both locales. 545 frontend tests pass (4 pre-existing failures unchanged). Status → done.
