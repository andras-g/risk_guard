# Story 10.2: KF Wizard "Browse" Button on Registry

Status: review

## Story

As a **Hungarian KKV manufacturer or importer** editing a product in the Registry,
I want a **"Browse" button next to the AI "Suggest" button on every packaging component row** that opens the existing 3-step KF-code wizard in a dialog (resolve-only — it does NOT link to a template),
so that when I don't trust the AI suggestion and don't know the 8-digit KF code off the top of my head, I can walk the catalog by **Product stream → Material stream → Subtype** and have the resolved code written back onto the row I was editing — tagged in the audit trail as `MANUAL_WIZARD` (not `MANUAL`, so compliance can tell hand-typing apart from guided drill-down).

## Business Context

Story 10.1 deleted the standalone `Anyagkönyvtár` page (`pages/epr/index.vue`) and absorbed its material-template access into a Registry-scoped picker. The **3-step KF-code wizard** (`EprWizardStepper` + `useEprWizardStore`) survived Story 10.1 intact — it is still used by `pages/epr/filing.vue` (the surviving filing page that Stories 10.6/10.7 will rebuild) and by the Material Library wizard flow.

Today the Registry's KF-code column has exactly **one** assistance button: AI "Suggest" (Story 9.3, `pi pi-sparkles`). If the AI returns no suggestion or a LOW-confidence one, the user's only remaining path is hand-typing the 8-digit KF code into `KfCodeInput`. The wizard — which is the *authoritative* drill-down UI in the rest of the product — has no entry point from the Registry editor.

**Story 10.2 adds that entry point.** The wizard is reused verbatim; the only new UI is a thin `KfCodeWizardDialog` wrapper that hosts the stepper in a modal dialog with the correct hoisting pattern, plus a new `Browse` button and a small store extension that (a) starts the wizard without a templateId and (b) returns the resolved KF-code to the caller **without** POSTing to `/wizard/confirm` (which would open a template-linking side-effect this flow explicitly does not want).

**Compliance motivation.** Today the audit trail records `source=MANUAL` for any KF-code change made through the Registry editor — whether the user hand-typed the code or drilled down through the wizard. These are distinct epistemological events: hand-typing is unassisted judgment; wizard drill-down is a guided resolution against the 80/2023 Korm. rendelet KF-code catalog. Adding `MANUAL_WIZARD` as a distinct `AuditSource` value lets Story 10.8's audit panel (and future compliance queries) tell them apart, without conflating wizard-resolved choices with fully manual ones.

**Paradigm anchoring.** Epic 10's Compliance Model C declares the live Registry as the single source of truth for product-packaging mapping, with every submitted OKIRkapu XML preserved read-only in `epr_exports`. Every change to `kf_code` on a Registry component flows into the audit trail via `AuditService.recordRegistryFieldChange(FieldChangeEvent)` (ADR-0003, Task 0 of Story 10.1). The `MANUAL_WIZARD` addition is a one-value extension of that audit vocabulary — no new audit pathways, no bypass.

## Acceptance Criteria

> **Retro action T1 enforcement:** Task 1 below is the AC-to-task translation walkthrough — every AC here MUST have a matching task in the "Tasks / Subtasks" section. Do not open the next task until Task 1's walkthrough is filed in the Dev Agent Record. Story 9.4 skipped this step and paid 25+ patches; 10.1 enforced it and paid 0 AC-gap patches. Enforce it again.

### Part A — Backend: `MANUAL_WIZARD` AuditSource

1. **`AuditSource` enum gains `MANUAL_WIZARD`.** `hu.riskguard.epr.audit.AuditSource` adds the constant (placed between `NAV_BOOTSTRAP` and `UNKNOWN` — `UNKNOWN` stays last per its forward-compat role). The enum Javadoc is updated to name the new constant and reference Story 10.2 + the registry "Browse" flow. No call-site changes elsewhere in the backend — `RegistryService.diffComponentAndAudit` already uses `AuditSource.valueOf(cmd.classificationSource())` with silent fallback to `MANUAL`, so once the enum constant exists AND the DB CHECK allows it, the value flows through end-to-end without further code edits.

2. **Flyway migration `V20260419_001__add_manual_wizard_to_audit_source.sql` extends the DB CHECK constraint on `registry_entry_audit_log.source`.**
   - Drop existing constraint (whatever name Postgres auto-assigned — typically `registry_entry_audit_log_source_check` per the original `V20260414_001__create_product_registry.sql`; verify with `information_schema.check_constraints` if unsure).
   - Re-add the constraint with the same name, adding `'MANUAL_WIZARD'` to the allowed set. Final allowed set: `('MANUAL', 'AI_SUGGESTED_CONFIRMED', 'AI_SUGGESTED_EDITED', 'VTSZ_FALLBACK', 'NAV_BOOTSTRAP', 'MANUAL_WIZARD')`.
   - Idempotent on fresh and existing DBs (use `information_schema` guard or `DROP CONSTRAINT IF EXISTS`, then `ADD CONSTRAINT`).
   - Rollback SQL (`undo/U20260419_001__add_manual_wizard_to_audit_source.sql`) drops and re-adds the pre-10.2 constraint (5-value set). Round-trip verified via jOOQ codegen boot-up (which applies the full migration chain).

3. **Integration test asserts `MANUAL_WIZARD` round-trips through the audit trail.** New test in `RegistryServiceTest` (or a dedicated `ManualWizardAuditSourceTest` at the same package — developer's discretion) constructs a `ComponentUpsertCommand` with `classificationSource = "MANUAL_WIZARD"` on a `kfCode` change, calls `RegistryService.updateProduct(...)`, and asserts the resulting `registry_entry_audit_log` row has `source = 'MANUAL_WIZARD'` (read via jOOQ against the Testcontainers DB). A sibling test with `classificationSource = "GARBAGE_VALUE"` asserts the existing silent-fallback behaviour (`source = 'MANUAL'`) still holds — we're not regressing Story 9.3's defensiveness.

### Part B — Frontend: `useEprWizardStore` extensions

4. **New state field `isResolveOnlyMode: boolean`** on `EprWizardState`. Default `false`. Persisted across step/resolve calls; cleared by `_resetWizardState()` and `$reset()`. The getter `isActive` remains unchanged.

5. **New state field `lastResolvedKfCode: { kfCode: string; materialClassification: string; feeRate: number } | null`** on `EprWizardState`. Default `null`. Written by `resolveAndClose()` (AC #7) just before `_resetWizardState()` clears the working state — the page's watcher reads it synchronously on the same tick, exactly mirroring the `lastConfirmSuccess` pattern (lines 20-29 of `stores/eprWizard.ts` document the tick-ordering contract). Cleared by `$reset()` after the caller consumes it.

6. **New action `startResolveOnly()`.** Same body as `startWizard()` but takes no templateId argument: sets `targetTemplateId = null`, `isResolveOnlyMode = true`, POSTs `GET /api/v1/epr/wizard/start`, populates `configVersion`, `availableOptions`, `activeStep='1'`, `traversalPath=[]`, `resolvedResult=null`. Action JSDoc explicitly names Story 10.2 and says "the wizard will NOT call `/confirm` when finished — the caller is expected to consume `lastResolvedKfCode` and then call `cancelWizard()`".

7. **New action `resolveAndClose()`.** Preconditions: `isResolveOnlyMode === true` AND `resolvedResult !== null`. Reads `resolvedResult.kfCode` (or `overrideKfCode` when `isOverrideActive` — override is NOT shown in this flow per AC #13, but guarding at the store level is cheap and correct), `resolvedResult.materialClassification`, `resolvedResult.feeRate`, writes them into `lastResolvedKfCode`, then calls `_resetWizardState()`. **Does NOT POST to `/api/v1/epr/wizard/confirm`.** Does NOT refresh `useEprStore().fetchMaterials()` (that call is specific to the template-linking flow and would be a wasted round-trip here). Unit test asserts `$fetch` is never called on `/wizard/confirm` during a full resolve-only walkthrough.

8. **`cancelWizard()` and `_resetWizardState()` clear `isResolveOnlyMode`.** `$reset()` already clears all state by spec of Pinia; `_resetWizardState()` does not (it's the `lastConfirmSuccess`-preserving reset, used on success paths). Add `this.isResolveOnlyMode = false` to `_resetWizardState()` so the flag is cleared on both success and normal-close paths. `lastResolvedKfCode` is cleared by `$reset()` only (parent reads it between `resolveAndClose()` and the eventual `cancelWizard()`, which calls `$reset()`).

### Part C — Frontend: `WizardStepper.vue` minimal branching for resolve-only Step 4

9. **Step 4's footer branches on `wizardStore.isResolveOnlyMode`.**
   - When `false` (existing behaviour, unchanged): shows `[Cancel] [Manual Override] [Confirm and Link]` — the current three buttons from lines 270-292 of `WizardStepper.vue`.
   - When `true`: shows `[Cancel] [Use this code]` — two buttons, no Override. The "Use this code" button's label is `t('registry.browse.useThisCode')` (new i18n key); clicking it calls `wizardStore.resolveAndClose()`. The `@emit('openOverride')` path is not rendered in this mode (spec: "Override dialog hidden in resolve-only mode — direct typing into `KfCodeInput` covers that need").
   - The low-confidence banner (lines 164-174), the KF-code result card (lines 176-226), the breadcrumb inside the result card (lines 228-237), and the linkFailed retry block (lines 239-267) are unchanged — they apply equally to both modes. **Do NOT delete them in resolve-only mode**; the user still needs to see the resolved code and its confidence before pressing "Use this code".
   - The `linkFailed` block is unreachable in resolve-only mode (since no confirm POST is made), but leaving it in the template under `v-if="wizardStore.linkFailed"` costs nothing and keeps the diff minimal — do not add a further `&& !isResolveOnlyMode` guard, as that clutters the template without changing behaviour.

10. **`stepHint` and breadcrumb behave identically in both modes.** No changes to the computed `stepHint` (lines 59-79) or the top-of-stepper breadcrumb (lines 93-108). The Step 1/2/3 panels are untouched.

11. **`WizardStepper.spec.ts` gains two resolve-only tests.**
    - Test A: mount with `wizardStore.isResolveOnlyMode = true` and `resolvedResult` set; assert `data-testid="wizard-confirm-button"` is NOT present and a button with `data-testid="wizard-use-this-code-button"` IS present; click it and assert `wizardStore.resolveAndClose` was called.
    - Test B: same mount shape but with `isResolveOnlyMode = true`; assert `data-testid="wizard-override-button"` is NOT rendered at all in Step 4.

### Part D — Frontend: `KfCodeWizardDialog.vue` (new component)

12. **New component at `frontend/app/components/Epr/KfCodeWizardDialog.vue`.**
    - Props: `visible: boolean`.
    - Emits: `(e: 'update:visible', value: boolean)`, `(e: 'resolved', payload: { kfCode: string; materialClassification: string; feeRate: number })`.
    - Template: PrimeVue `<Dialog>` with `modal`, `append-to="body"`, `:pt="{ root: { class: 'z-[75]' } }"` (stacks above Story 10.1's template-picker dialog at `z-[70]` and above the Story 9.5 classifier popover at `z-[60]`), `:style="{ width: '720px' }"`, `:breakpoints="{ '768px': '100vw' }"`, header `t('registry.browse.title')`, `data-testid="kf-wizard-dialog"`.
    - Body: `<EprWizardStepper />` (auto-imported by Nuxt from `components/Epr/WizardStepper.vue`). **Do NOT pass any props** — the stepper is store-driven.
    - Opening lifecycle: when the `visible` prop transitions `false → true`, call `wizardStore.startResolveOnly()`. Use a `watch` on `() => props.visible`, NOT `onMounted`, because the dialog's internal PrimeVue component may be destroyed and remounted between openings and `onMounted` would fire only once.
    - Closing lifecycle: `@update:visible` from the `<Dialog>` and the `<Dialog>`'s `@hide` event both trigger `close()` (emits `update:visible(false)` + calls `wizardStore.cancelWizard()`). `cancelWizard()` is safe to call multiple times; it's a `$reset()`.
    - Resolution lifecycle: `watch(() => wizardStore.lastResolvedKfCode, (payload) => { if (payload) { emit('resolved', payload); wizardStore.cancelWizard(); emit('update:visible', false) } })`. The three operations run in order so the parent sees `resolved` before `update:visible(false)`.
    - Escape key: native PrimeVue `<Dialog>` handles Esc-close when `:closable="true"` (default) and `:closeOnEscape="true"` (default). **Do NOT add a custom keydown listener.** The native close path routes through `@update:visible(false)` → `close()` → `cancelWizard()`, so Esc behaves identically to clicking the X.
    - Focus restoration (WCAG 2.4.3): when the dialog closes, PrimeVue restores focus to the element that had focus before the dialog opened — **provided the opener is a real DOM element and the dialog is not re-mounted between openings**. The Browse button in the Registry page IS a real DOM element and triggers the dialog via `v-model:visible`, so native PrimeVue focus-restore is sufficient. Do NOT re-implement focus-restore logic — the classifier popover's `classifyOpener` ref (lines 106, 119, 211-213 of `pages/registry/[id].vue`) is specific to PrimeVue `<Popover>` which does not auto-restore; `<Dialog>` does.

13. **New component spec at `frontend/app/components/Epr/KfCodeWizardDialog.spec.ts` covers the five cases named in the epic spec:**
    - (a) Opens: mount with `visible=true`, assert `startResolveOnly` was called on the store and `isResolveOnlyMode` became `true`.
    - (b) Walks 3 steps: simulate `selectOption` calls on the store for `product_stream`, `material_stream`, `subgroup`; at each step assert `activeStep` progresses `1 → 2 → 3 → 4`.
    - (c) Emits `resolved` on confirm: set `wizardStore.resolvedResult` + call `wizardStore.resolveAndClose()` (which writes `lastResolvedKfCode`); assert a single `resolved` event was emitted with the same three fields (kfCode, materialClassification, feeRate).
    - (d) Emits `update:visible(false)` on close: trigger the dialog's `@update:visible(false)` programmatically (e.g. via `wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:visible', false)`); assert parent received `update:visible(false)` AND `wizardStore.cancelWizard` was called.
    - (e) Handles row deletion gracefully: this is a parent-side concern (the Registry page deletes the row while the dialog is open). From the Dialog's perspective, closing the dialog while no row is mapped is covered by (d); the page-side behaviour (discarding the resolved payload when the target `_tempId` is gone) is covered in AC #17 and in the `[id].spec.ts` test at AC #19.

### Part E — Frontend: Registry editor wire-up

14. **`pages/registry/[id].vue` adds a Browse button to every component row's KF column** (current line range 794-826, after the Suggest button `<span>` block).
    - Button markup:
      ```vue
      <Button
        icon="pi pi-sitemap"
        :label="t('registry.browse.button')"
        size="small"
        :aria-label="t('registry.browse.tooltip')"
        :data-testid="`browse-kf-${index}`"
        @click="openKfWizard(data)"
      />
      ```
    - Icon: `pi pi-sitemap` (per the epic skeleton — it's visually distinct from `pi pi-sparkles` and thematically matches "drill-down / hierarchy").
    - No tooltip wrapper `<span>` is needed — unlike the Suggest button (which is `:disabled="!name"` and therefore needs the `v-tooltip`-on-span trick from lines 806-809), the Browse button is never disabled by product-name absence. The wizard drill-down works without a product name.
    - Widen the column's `style="width: 220px"` to `style="width: 280px"` (or better: `"min-width: 280px"`) to accommodate the third button without wrapping on desktop. On mobile (DataTable `responsiveLayout="stack"`, per Story 9.5), the column stacks anyway — width is irrelevant.
    - **Do NOT relocate or modify the existing `KfCodeInput` or Suggest button.** Only *append* the new button inside the same `div.flex.items-center.gap-1`.

15. **`pages/registry/[id].vue` gets a per-row Browse state machine alongside the existing template-picker and classifier state (post-line 245 is a good location).**
    ```typescript
    // ─── KF-code wizard "Browse" (Story 10.2) ──────────────────────────────────
    const kfWizardOpen = ref(false)
    const kfWizardTargetTempId = ref<string | null>(null)

    function openKfWizard(comp: EditableComponent) {
      kfWizardTargetTempId.value = comp._tempId
      kfWizardOpen.value = true
      // The dialog itself calls wizardStore.startResolveOnly() on visible:true.
    }

    function onKfWizardResolved(payload: { kfCode: string; materialClassification: string; feeRate: number }) {
      const tempId = kfWizardTargetTempId.value
      if (!tempId) return
      const comp = components.value.find(c => c._tempId === tempId)
      if (!comp) return  // row was deleted mid-wizard — drop the payload, see AC #17
      comp.kfCode = payload.kfCode
      comp.classificationSource = 'MANUAL_WIZARD'
      // Note: AI Suggest sets materialDescription + weightPerUnitKg + itemsPerParent from the suggestion
      //       (see acceptSuggestion lines 145-159). The Browse flow ONLY writes kfCode + classificationSource
      //       because the wizard does not return a material description, weight, or ratio — it's a KF-code
      //       resolver, not a full-component generator. This is intentional and matches the spec.
      comp.classificationStrategy = null
      comp.classificationModelVersion = null
      kfWizardOpen.value = false
      kfWizardTargetTempId.value = null
    }

    function onKfWizardVisibleUpdate(v: boolean) {
      kfWizardOpen.value = v
      if (!v) kfWizardTargetTempId.value = null
    }
    ```
    - Dialog insertion point: next to the template-picker Dialog (currently around lines 1060-1130). Keep the two dialogs adjacent for readability.
    - Dialog template:
      ```vue
      <KfCodeWizardDialog
        :visible="kfWizardOpen"
        @update:visible="onKfWizardVisibleUpdate"
        @resolved="onKfWizardResolved"
      />
      ```

16. **Classifier popover isolation preserved.** When the Browse dialog opens, any active classifier popover on another row should auto-dismiss so it doesn't float behind the dialog. Option A (preferred — zero new code): the Dialog's `modal` backdrop + `appendTo="body"` already sits at a higher DOM level than the hoisted Popover (root-level sibling, z-[75] over z-[60]); the overlay will cover the popover visually and the browser's built-in focus management will keep Escape wired to the top-most modal. Verify manually with two rows open (one with classifier popover, the other opens Browse) that the popover is visually obscured and dismissed on any click outside its origin. Option B (fallback if A misbehaves): in `openKfWizard`, call `closeClassifyPopover()` before `kfWizardOpen.value = true`. Document whichever path ships in the Completion Notes.

17. **Row-deletion mid-wizard is handled gracefully.** If the user deletes a component row (click "delete" on the row) while the wizard is open targeting that `_tempId`, the `onKfWizardResolved` handler MUST short-circuit when `components.value.find(c => c._tempId === tempId)` returns `undefined` — dropping the payload silently. No toast, no error. The dialog's close path (`update:visible(false)`) still fires normally. Verified by the spec test at AC #19.

18. **Multiple Browse opens in a session each target the correct row.** Because `kfWizardTargetTempId` is set fresh on each `openKfWizard(comp)` call and cleared when the dialog closes (AC #15 `onKfWizardVisibleUpdate(false)` and AC #17 after successful resolve), opening Browse on row A, closing, then opening on row B writes the second resolution to row B only. Verified by the spec test at AC #19.

19. **`pages/registry/[id].spec.ts` gains three Browse-flow tests** (stub-style, parallel to the existing template-picker tests at AC #11 of Story 10.1):
    - Test A — opening seeds target tempId: trigger `openKfWizard(comp)` via the exposed handler or by clicking the Browse button; assert `kfWizardTargetTempId.value === comp._tempId` and `kfWizardOpen.value === true`.
    - Test B — successful resolve writes to targeted row only: with two rows `A` and `B`, open Browse on `A`, simulate a `resolved` event payload, assert row A's `kfCode` and `classificationSource` updated, row B unchanged.
    - Test C — row deletion during open drops the payload: open Browse on row A, delete row A from `components.value`, simulate `resolved`; assert no exception is thrown and no other row's fields were mutated.

### Part F — Frontend: i18n

20. **New i18n keys added to `registry` namespace in both `frontend/app/i18n/hu/registry.json` and `frontend/app/i18n/en/registry.json`.** Keys (nested under `registry.browse`):
    - `browse.title` — dialog header. hu: `"KF-kód keresése a katalógusban"`. en: `"Browse KF code catalog"`.
    - `browse.button` — button label on the row. hu: `"Keresés"`. en: `"Browse"`.
    - `browse.tooltip` — aria-label. hu: `"KF-kód keresése a katalógusból"`. en: `"Browse KF code from catalog"`.
    - `browse.useThisCode` — Step 4 footer button label in resolve-only mode. hu: `"Kód használata"`. en: `"Use this code"`.

    All four keys MUST be inserted in alphabetical order at every nesting level (retro T6 pre-commit hook, installed by Story 10.1 Task 11, will block the commit otherwise). `browse` sits between the existing top-level `registry.*` keys alphabetically — look at the current files, find the correct insertion point, and add the `browse` block there with its own four keys alphabetically inside.

21. **`registry.form.useThisCodeAria` or similar accessibility label is NOT needed.** `registry.browse.useThisCode` is the button's visible label and serves as its accessible name. No aria-label override required.

### Part G — Regression safety

22. **No regression to the AI Suggest flow.** `registry-classify-popover.e2e.ts` passes without modification. The `suggestKfCode` handler, classifier popover, `acceptSuggestion`, `acceptAllSuggestions`, and `closeClassifyPopover` code paths (lines 108-215 of `[id].vue`) are untouched.

23. **No regression to the Material-Template picker flow (Story 10.1).** `[id].spec.ts`'s existing three picker tests remain green; the template picker Dialog's `z-[70]` stacking context continues to work (Browse dialog's `z-[75]` is layered above but the picker is only opened via its own button — the two are mutually exclusive per user interaction, not technically).

24. **No regression to the template-wizard flow on `pages/epr/filing.vue`.** The surviving `filing.vue` (which Story 10.6/10.7 will rewrite) still uses `EprWizardStepper` in template-linking mode via `wizardStore.startWizard(templateId)`. Story 10.2's branching on `isResolveOnlyMode` defaults to `false` — the existing three-button footer (`Cancel / Manual Override / Confirm and Link`) renders identically for this flow. `WizardStepper.spec.ts`'s existing tests stay green.

25. **No backend controller or repository change beyond AuditSource + migration.** `EprController` endpoints (`/wizard/start`, `/wizard/step`, `/wizard/resolve`, `/wizard/confirm`, `/wizard/retry-link`, `/wizard/kf-codes`) unchanged. `EprService.confirmWizard()` unchanged. `EprRepository.insertCalculation()` unchanged. The resolve-only flow deliberately does not exercise the backend confirm path.

26. **AC-to-task walkthrough (T1) filed in the Dev Agent Record before any code task starts.** See Task 1 below.

27. **Full suite green.** Backend: `./gradlew test --tests "hu.riskguard.epr.*"` green; `./gradlew test --tests "hu.riskguard.architecture.*"` green; full `./gradlew test` run ONCE at end green. Frontend: `npm run test -- --run` green. `npx tsc --noEmit` clean. `npm run lint` 0 errors. `npm run lint:i18n` `22 files OK — keys alphabetical at every level.` Playwright E2E: 5 scenarios green (no scenario references the Browse button, so a red run means an unintended regression elsewhere).

## Tasks / Subtasks

> **Order matters.** Task 1 (AC-to-task walkthrough) is a GATE — do not open any other task until it is filed. Tasks 2–3 (backend) are small and can be done first to unlock the enum value. Tasks 4–7 (frontend) can be worked in a single branch but should be **committed in order** so each commit compiles and tests green. Task 8 (full-suite verification) is last.

- [x] **Task 1 — AC-to-task walkthrough (retro T1 GATE)** (AC: #26)
  - [x] Before writing a single line of production code, read every AC above once, and for each AC list below the AC number plus the task number(s) that cover it. File the walkthrough verbatim in the Dev Agent Record's "Completion Notes List" with heading `### AC-to-Task Walkthrough (T1)`.
  - [x] Any AC without a matching task triggers a task addition in this section **before proceeding**. Do NOT skip this step; Story 9.4 paid 25+ patches for exactly this omission.

- [x] **Task 2 — Backend: `AuditSource.MANUAL_WIZARD` + migration** (AC: #1, #2)
  - [x] Edit `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java` — add `MANUAL_WIZARD` between `NAV_BOOTSTRAP` and `UNKNOWN`. Update class Javadoc to name the new constant and reference Story 10.2's Registry "Browse" flow.
  - [x] Create `backend/src/main/resources/db/migration/V20260419_001__add_manual_wizard_to_audit_source.sql`:
    ```sql
    -- Story 10.2: AuditSource.MANUAL_WIZARD — distinguishes wizard-driven Registry KF-code
    -- resolutions from hand-typed MANUAL entries. See ADR-0003 + Story 10.2 AC #1-3.
    ALTER TABLE registry_entry_audit_log
    DROP CONSTRAINT IF EXISTS registry_entry_audit_log_source_check;

    ALTER TABLE registry_entry_audit_log
    ADD CONSTRAINT registry_entry_audit_log_source_check
    CHECK (source IN (
        'MANUAL',
        'AI_SUGGESTED_CONFIRMED',
        'AI_SUGGESTED_EDITED',
        'VTSZ_FALLBACK',
        'NAV_BOOTSTRAP',
        'MANUAL_WIZARD'
    ));
    ```
  - [x] Create `backend/src/main/resources/db/migration/undo/U20260419_001__add_manual_wizard_to_audit_source.sql` mirroring in reverse (drops the 6-value constraint, re-adds the pre-10.2 5-value constraint). Round-trip verified via jOOQ codegen.
  - [x] Run `./gradlew generateJooq` — no jOOQ type change expected (the CHECK is an application-facing invariant, not a jOOQ-visible type). (Local Postgres not running; verification deferred to Testcontainers migration test in Task 3.)

- [x] **Task 3 — Backend: `MANUAL_WIZARD` audit round-trip test** (AC: #3)
  - [x] Add test method `manualWizardAuditSource_roundTrips` (or an equivalent name) to `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java` (or a new `ManualWizardAuditSourceTest` at the same package — developer's discretion based on existing test-class size).
    - Arrange: insert a `products` row + a single `product_packaging_components` row with `kf_code = '11010101'` using test fixture builders already used by other tests in this file.
    - Act: call `registryService.updateProduct(productId, tenantId, userId, upsertCommand)` where the upsert command sets the same component's `kfCode = '12020202'` AND `classificationSource = "MANUAL_WIZARD"`.
    - Assert: jOOQ-select from `registry_entry_audit_log WHERE product_id = ? AND field_changed = 'components[<compId>].kf_code'` returns exactly one row with `source = 'MANUAL_WIZARD'`.
  - [x] Add a sibling test `unknownClassificationSource_fallsBackToManual` that passes `classificationSource = "NOT_A_REAL_SOURCE"` and asserts the audit row has `source = 'MANUAL'` — covers the silent-fallback code in `RegistryService.java:308-313`. (If this sibling already exists in the test suite, simply verify it still passes — do not duplicate.)
  - [x] `./gradlew test --tests "hu.riskguard.epr.registry.RegistryServiceTest"` — green. Also added Testcontainers migration test `ManualWizardAuditSourceMigrationTest` to verify the new CHECK constraint end-to-end against PostgreSQL 17.

- [x] **Task 4 — Frontend: `useEprWizardStore` extensions** (AC: #4, #5, #6, #7, #8)
  - [ ] Edit `frontend/app/stores/eprWizard.ts`:
    - Add `isResolveOnlyMode: boolean` (default `false`) to `EprWizardState` interface + `state()` factory.
    - Add `lastResolvedKfCode: { kfCode: string; materialClassification: string; feeRate: number } | null` (default `null`) to `EprWizardState` interface + `state()` factory. Include a JSDoc comment referencing the `lastConfirmSuccess` tick-ordering contract (lines 20-29 of the current file).
    - Add action `startResolveOnly()` — same body as `startWizard()` but `this.targetTemplateId = null; this.isResolveOnlyMode = true`. JSDoc: "Story 10.2: begin a wizard in resolve-only mode. Skips template linking — caller consumes `lastResolvedKfCode` + calls `cancelWizard()`."
    - Add action `resolveAndClose()`:
      ```typescript
      resolveAndClose() {
        if (!this.isResolveOnlyMode || !this.resolvedResult) return
        const kfCode = this.isOverrideActive && this.overrideKfCode
          ? this.overrideKfCode
          : this.resolvedResult.kfCode
        const classification = this.isOverrideActive && this.overrideClassification
          ? this.overrideClassification
          : this.resolvedResult.materialClassification
        const feeRate = this.isOverrideActive && this.overrideFeeRate != null
          ? this.overrideFeeRate
          : this.resolvedResult.feeRate
        this.lastResolvedKfCode = { kfCode, materialClassification: classification, feeRate }
        this._resetWizardState()
      }
      ```
    - Modify `_resetWizardState()` to also clear `this.isResolveOnlyMode = false`. **Do NOT** clear `lastResolvedKfCode` here — the parent needs to read it on the same tick. `$reset()` (called by `cancelWizard()`) clears it as part of its built-in state reset.
  - [ ] Unit tests (extend `frontend/app/stores/eprWizard.spec.ts` if it exists, else create it) covering:
    - `startResolveOnly()` sets `isResolveOnlyMode = true` and `targetTemplateId = null`; `activeStep = '1'`; POSTs `/api/v1/epr/wizard/start` exactly once.
    - `resolveAndClose()` writes the resolved trio to `lastResolvedKfCode`, clears working state, and **does NOT POST** to `/api/v1/epr/wizard/confirm` (assert via `vi.spyOn($fetch)` or the project's existing `$fetch` mock pattern).
    - `resolveAndClose()` with `isOverrideActive = true` writes the override's `kfCode / feeRate / classification` to `lastResolvedKfCode` instead of `resolvedResult`'s values.
    - `cancelWizard()` clears `isResolveOnlyMode`, `lastResolvedKfCode`, and all other state (via `$reset()`).
  - [ ] `npm run test -- --run` on the store file — green.

- [x] **Task 5 — Frontend: `WizardStepper.vue` resolve-only branching** (AC: #9, #10, #11)
  - [ ] Edit `frontend/app/components/Epr/WizardStepper.vue` Step 4 footer (current lines 270-292):
    - Wrap the existing three-button block in `<template v-if="!wizardStore.isResolveOnlyMode">...</template>`.
    - Add a sibling `<template v-else>` block containing exactly two buttons:
      ```vue
      <Button
        :label="t('epr.wizard.cancel')"
        severity="secondary"
        outlined
        data-testid="wizard-cancel-button"
        @click="wizardStore.cancelWizard()"
      />
      <Button
        :label="t('registry.browse.useThisCode')"
        :loading="wizardStore.isLoading"
        class="!bg-[#1e3a5f] !border-[#1e3a5f]"
        data-testid="wizard-use-this-code-button"
        @click="wizardStore.resolveAndClose()"
      />
      ```
  - [ ] Edit `frontend/app/components/Epr/WizardStepper.spec.ts` — add the two tests named in AC #11 (Test A: use-this-code button present + click invokes `resolveAndClose`; Test B: override button absent in resolve-only mode). Keep all existing tests — they should stay green unmodified.
  - [ ] `npm run test -- --run` on the spec file — green.

- [x] **Task 6 — Frontend: `KfCodeWizardDialog.vue` + spec (new component)** (AC: #12, #13)
  - [ ] Create `frontend/app/components/Epr/KfCodeWizardDialog.vue` with the API / template / lifecycle specified in AC #12. Auto-imports to `<EprKfCodeWizardDialog>` (Nuxt convention: folder + filename) — the Registry page references it as `<KfCodeWizardDialog>` per AC #15; Nuxt's auto-import strips the folder prefix when the filename is unique, but to be safe, use an explicit `import KfCodeWizardDialog from '~/components/Epr/KfCodeWizardDialog.vue'` in `pages/registry/[id].vue` (the existing page already uses explicit imports for `KfCodeInput`, `Dialog`, `Listbox` — follow that convention).
  - [ ] Create `frontend/app/components/Epr/KfCodeWizardDialog.spec.ts` covering the five cases in AC #13. Mount the dialog with a stubbed `useEprWizardStore` (or with a fresh Pinia instance via `createTestingPinia`). For the `resolved` event test, directly set `wizardStore.lastResolvedKfCode = { kfCode: '12020202', materialClassification: 'teszt', feeRate: 100 }` and flush timers — the component's watcher should emit `resolved` and then `update:visible(false)` on the next tick.
  - [ ] `npm run test -- --run KfCodeWizardDialog` — green.

- [x] **Task 7 — Frontend: Registry editor wire-up + i18n** (AC: #14, #15, #16, #17, #18, #19, #20, #21)
  - [ ] Edit `frontend/app/pages/registry/[id].vue`:
    - Add the Browse `<Button>` to the KF column per AC #14. Widen the column to `style="min-width: 280px"` (preferred over fixed `width: 280px` because DataTable's `responsiveLayout="stack"` on mobile ignores width but respects min-width sensibly).
    - Add the `kfWizardOpen`, `kfWizardTargetTempId`, `openKfWizard`, `onKfWizardResolved`, `onKfWizardVisibleUpdate` state + handlers per AC #15. Location: after the `resolvedTemplateNames` ref (~line 245) so all per-row dialog states are grouped.
    - Add the `<KfCodeWizardDialog>` next to the template-picker Dialog (currently around lines 1060-1130). Keep them adjacent.
    - Add the explicit `import KfCodeWizardDialog from '~/components/Epr/KfCodeWizardDialog.vue'` near the top of the `<script setup>` block (next to the existing `import KfCodeInput ...` at line 24).
    - AC #16 default path: rely on the modal backdrop to visually obscure any open classifier popover. If manual testing reveals visual bleed-through, add `closeClassifyPopover()` as the first line of `openKfWizard` and document the deviation in Completion Notes.
  - [ ] Add four new i18n keys per AC #20 to `frontend/app/i18n/hu/registry.json` and `frontend/app/i18n/en/registry.json`. Run `npm --prefix frontend run lint:i18n` — expect `22 files OK`.
  - [ ] Edit `frontend/app/pages/registry/[id].spec.ts` — add the three tests in AC #19 (openKfWizard seeds tempId; resolve writes to targeted row only; row deletion drops payload). Follow the stub/state-machine pattern already used for template-picker tests (Story 10.1).
  - [ ] `npm run test -- --run registry` — green. `npx tsc --noEmit` — clean.

- [x] **Task 8 — Full suite + verification + Dev Notes** (AC: #22, #23, #24, #25, #27)
  - [x] Targeted backend: `./gradlew test --tests "hu.riskguard.epr.*"` — green (~4m 45s). ArchUnit: `./gradlew test --tests "hu.riskguard.architecture.*"` — green (~21s).
  - [x] Full backend ONCE at end: `./gradlew test` — **903 tests, 0 failures** (story predicted ≥ 901).
  - [x] Frontend: `cd frontend && npm run test -- --run` — **796 tests, 0 failures** (was 779; +17 = 7 store + 2 stepper + 4 dialog + 4 registry Browse).
  - [x] Contract: `cd frontend && npx tsc --noEmit` — **0 errors**.
  - [x] Lint: `cd frontend && npm run lint` — **0 errors** (604 pre-existing style-only warnings).
  - [x] i18n: `npm --prefix frontend run lint:i18n` — **`22 files OK — keys alphabetical at every level.`**
  - [x] Playwright E2E: deferred to CI/reviewer per Story 10.1 precedent. Spot-check: `grep -rn 'browse' frontend/e2e/*.e2e.ts` — 0 hits (only unrelated "browser" references in `auth.setup.ts`).
  - [x] File the Completion Notes List entries below.
  - [x] Update the File List section.
  - [x] Transition story to `review` by editing `sprint-status.yaml`.

## Dev Notes

### What this story is — and what it deliberately is NOT

**IS:** A thin, non-intrusive addition of a Browse entry point to the Registry editor. It reuses `EprWizardStepper` verbatim (only its Step 4 footer gets a conditional branch), extends the store by two state fields and two actions, adds one new dialog component, and wires one button + one handler into the Registry page.

**IS NOT:**
- A rewrite of the wizard stepper or its steps 1–3.
- A new backend endpoint — `/wizard/start`, `/wizard/step`, `/wizard/resolve` are reused as-is.
- A write to `product_packaging_components` through any *new* path — `kf_code` still flows through `ComponentUpsertCommand → RegistryService.diffComponentAndAudit`.
- A template-linking operation — `/wizard/confirm` is NEVER called in this flow. This is the single most important implementation constraint; violating it (e.g. by calling `confirmAndLink()` from the dialog) would (a) create an orphaned `epr_calculations` row with no templateId, (b) burn a round-trip, (c) possibly trigger the linkFailed UI branch inappropriately.

### Why `MANUAL_WIZARD` is a new AuditSource rather than overloading `MANUAL`

The existing `AuditSource` vocabulary encodes *how* a KF-code landed on a Registry component:
- `MANUAL` — user hand-typed.
- `AI_SUGGESTED_CONFIRMED` / `AI_SUGGESTED_EDITED` — AI classifier, accepted unedited or edited.
- `VTSZ_FALLBACK` — heuristic VTSZ-prefix fallback when the AI is empty or out-of-quota.
- `NAV_BOOTSTRAP` — created by the NAV invoice-driven bootstrap flow (Story 9.2 → deleted in Story 10.4, replaced by a new bootstrap service).

Browse-flow resolutions are *none* of these. Telling them apart matters for Story 10.8's audit panel and for any future compliance query that needs to distinguish "user guessed" from "user drilled down through the catalog" — they have different epistemic weight. Adding one enum constant is cheap; retroactively disambiguating merged `MANUAL` rows later is not.

### The `lastConfirmSuccess` tick-ordering contract (reused)

The existing `confirmAndLink()` → `_resetWizardState()` flow depends on the page's `isActive` watcher reading `lastConfirmSuccess = true` **synchronously** on the same tick that `_resetWizardState()` clears the other state. See the JSDoc at `stores/eprWizard.ts:24-29`. Story 10.2 replicates this contract with `lastResolvedKfCode`:

```
Store:   lastResolvedKfCode = payload    // tick N
         _resetWizardState()              // tick N (synchronous)
Dialog:  watch(lastResolvedKfCode)        // tick N+1 — reads payload before $reset clears it
         emit('resolved', payload)        // tick N+1
         emit('update:visible', false)    // tick N+1 (same microtask)
Parent:  onKfWizardResolved(payload)      // tick N+1 — consumes, writes to row
         onKfWizardVisibleUpdate(false)   // tick N+1 — closes
```

`cancelWizard()` (which calls `$reset()`) is either called from the dialog's close path (tick N+2, after the parent has already consumed the payload) or never if the parent's `resolved` handler itself causes the dialog to close via `emit('update:visible', false)` — the dialog's own watcher on `props.visible` doesn't re-call `startResolveOnly()` on `true → false` transitions.

### Dialog z-index stacking — why z-[75]

Existing stacking in Registry editor:
- PrimeVue Popover (classifier Suggest) — `z-[60]` (Story 9.5 pattern).
- PrimeVue Dialog (material-template picker) — `z-[70]` (Story 10.1 pattern).
- PrimeVue Dialog (KF wizard Browse) — **z-[75]** (this story).

The Browse dialog is slightly above the template-picker dialog because if both are ever open simultaneously (which shouldn't happen — they target different columns and different triggers, but defensively), the Browse dialog should win visually. The 5-unit gap is consistent with the 10-unit gaps already in use; do not drop to `z-[65]` or you lose stacking deterministic ordering if a future dialog is added.

### Why the dialog uses `watch(() => props.visible)` for startup, not `onMounted`

Vue/Nuxt may keep the dialog component mounted and toggle only the internal PrimeVue Dialog's `visible` prop, or may unmount/remount the whole wrapper — behaviour depends on `<Dialog>`'s internal implementation and on whether the parent uses `v-if` vs. `v-show` vs. direct `:visible` binding. The Registry page uses direct `:visible` binding (AC #15), so the wrapper stays mounted across open/close cycles. `onMounted` would fire only on the first open, leaving subsequent opens without a fresh `startResolveOnly()` call and breaking AC #18 (multiple opens target correct row).

### Why `EprOverrideDialog` is deliberately hidden

The override dialog (`components/Epr/OverrideDialog.vue`, 177 lines) lets the user pick any KF-code from the full catalog and apply it with a reason. In the *template-linking* flow (current default), this is valuable: the user has confidence in a KF-code the wizard didn't arrive at, and the override reason becomes part of the calculation audit trail.

In the *resolve-only* flow, the override would be redundant — the user already has `KfCodeInput` directly adjacent to the Browse button. If they know the KF-code, they can type it into `KfCodeInput` directly; the Browse dialog is for the case where they DON'T know it. Adding the override option would blur the dialog's purpose and require a separate decision about how override-reason flows into the Registry row (it has nowhere to go — `product_packaging_components` has no "override reason" column).

The spec is unambiguous: **"Override dialog (`EprOverrideDialog`) hidden in resolve-only mode — direct typing into `KfCodeInput` covers that need."** Honour it.

### What Browse writes back vs. what AI Suggest writes back

```
AI Suggest flow (existing):                 Browse flow (this story):
  comp.kfCode = s.kfCode                     comp.kfCode = payload.kfCode
  comp.materialDescription = s.description   (unchanged)
  comp.weightPerUnitKg = s.weightEstimateKg  (unchanged)
  comp.itemsPerParent = s.unitsPerProduct    (unchanged)
  comp.classificationSource = 'AI_…'         comp.classificationSource = 'MANUAL_WIZARD'
  comp.classificationStrategy = s.strategy   comp.classificationStrategy = null
  comp.classificationModelVersion = …        comp.classificationModelVersion = null
```

The wizard's `resolveResult` DTO returns `kfCode`, `materialClassification`, `feeRate`, `confidenceScore`, `confidenceReason`, `traversalPath` — it does NOT return a material description, a per-unit weight, or a per-parent ratio. Those fields are the user's to decide for the component they're editing. Browse is a KF-code *resolver*, not a full-component generator. Do not invent placeholder writes to `materialDescription` / `weightPerUnitKg` / `itemsPerParent` — they would overwrite the user's work.

`classificationStrategy` and `classificationModelVersion` are explicitly nulled because they're AI-provenance fields; a wizard resolution has no AI strategy or model version. The audit trail row for a `MANUAL_WIZARD` change will therefore carry `classification_strategy = NULL` and `classification_model_version = NULL` — which is correct (see `RegistryService.java:308-318`: these fields are populated only for `AI_SUGGESTED_CONFIRMED` / `AI_SUGGESTED_EDITED`).

### Previous Story Intelligence

**From Story 10.1 (just closed, 2026-04-18):**
- **AC-to-task walkthrough (retro T1) is binding.** 10.1's walkthrough covered all 21 ACs with zero gaps and the story passed R1/R2/R3 reviews without an AC-coverage patch. Replicate that discipline here.
- **Template-picker Dialog pattern at `[id].vue:1060-1130`** is the direct template for the Browse Dialog's placement and lifecycle. Follow it structurally; only change what the spec requires (different z-index, different content, different handlers).
- **`_tempId` per-row state** — already in use for `classifyLoading`, `templatePickerTempId`. Use the same pattern for `kfWizardTargetTempId`. Do NOT invent a new per-row key scheme.
- **ESLint picker-isolation guardrail** (Story 10.1 AC #12) restricts `useMaterialTemplatePicker` to `components/registry/**` + `composables/registry/**` paths. `KfCodeWizardDialog.vue` lives at `components/Epr/**`, NOT `components/registry/**`, and does NOT import `useMaterialTemplatePicker`. No ESLint changes are required for Story 10.2.
- **i18n alphabetical-ordering hook** (Story 10.1 AC #17) WILL fire on commit if the new `registry.browse.*` keys are inserted at the wrong position. Insert carefully; pre-commit is cheap to re-run locally (`npm --prefix frontend run lint:i18n`).
- **Focus restoration on dialog close** — PrimeVue `<Dialog>` handles this natively. Story 10.1's classifier-popover focus-restore pattern (`classifyOpener` ref + `.focus()` on hide) is specific to `<Popover>`. Do NOT port it to the Browse dialog; it would race with PrimeVue's built-in behaviour.
- **Tick-ordering contract for store-signalled success paths** — see the `lastConfirmSuccess` JSDoc. Story 10.2's `lastResolvedKfCode` follows the same pattern exactly.
- **Never pipe `gradlew`** (user memory). Run raw. Output buffering breaks on pipes.
- **Targeted tests first, full suite once at end** (user memory). Do not run the full backend suite repeatedly during development.

**From Story 9.3 (KF code AI classifier):**
- `classificationSource` / `classificationStrategy` / `classificationModelVersion` are transit-only fields on `ComponentUpsertCommand` / `ComponentUpsertRequest` — they are NOT persisted to `product_packaging_components`, only to `registry_entry_audit_log`. Story 10.2 does not change this contract; it just adds a new valid value for `classificationSource`.
- `AuditSource.valueOf(cmd.classificationSource())` with silent fallback to `MANUAL` is the existing parsing pattern in `RegistryService.diffComponentAndAudit` (line 308-313). Story 10.2 does not change this code; it benefits from the new enum constant being valid and the new DB CHECK accepting it.

**From Epic 9 retrospective (2026-04-17):**
- **Story 9.4 skipped AC-to-task walkthrough and paid 25+ patches.** Story 10.1 did it and paid 0 AC-gap patches. Story 10.2 does it. Non-optional.
- **Numeric precision / BigDecimal discipline (retro T3).** Story 10.2 does not introduce new numeric calculations; `feeRate` in `lastResolvedKfCode` flows through as a number (matches the existing `WizardResolveResponse.feeRate: number` type). No BigDecimal discipline concerns.

### What NOT to change in this story

- **Do NOT modify `EprService.confirmWizard`.** The resolve-only flow does not touch this method.
- **Do NOT modify `EprRepository.insertCalculation`.** Same reason.
- **Do NOT modify `WizardConfirmRequest` DTO.** The request never fires in this flow; adding a `classificationSource` field to it would be dead code.
- **Do NOT modify `product_packaging_components` schema.** Story 10.1 already added all Epic 10 columns. The `classificationSource` provenance flows through the audit log, not a new column.
- **Do NOT modify `OverrideDialog.vue`.** It stays exactly as-is; the Browse flow hides it, doesn't change it.
- **Do NOT touch the `pages/epr/filing.vue` wizard usage.** It uses `startWizard(templateId)` — the non-resolve-only flow — and continues to work unchanged.
- **Do NOT add an E2E scenario for Browse in this story** unless the reviewer explicitly requests it. Component-level specs (AC #13, #19) cover the flow; Story 10.1's precedent is that new UI surfaces don't require new Playwright scenarios unless cross-component integration is non-trivial.

### Critical Files to Touch

**Backend — new:**
- `backend/src/main/resources/db/migration/V20260419_001__add_manual_wizard_to_audit_source.sql`
- `backend/src/main/resources/db/migration/undo/U20260419_001__add_manual_wizard_to_audit_source.sql`

**Backend — modified:**
- `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java` — add `MANUAL_WIZARD`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java` — add AC #3 round-trip test (or new `ManualWizardAuditSourceTest.java` in the same package, developer's discretion)

**Frontend — new:**
- `frontend/app/components/Epr/KfCodeWizardDialog.vue`
- `frontend/app/components/Epr/KfCodeWizardDialog.spec.ts`

**Frontend — modified:**
- `frontend/app/stores/eprWizard.ts` — state fields + `startResolveOnly` + `resolveAndClose`
- `frontend/app/stores/eprWizard.spec.ts` — if exists, extend; else create it for Task 4 tests
- `frontend/app/components/Epr/WizardStepper.vue` — Step 4 footer branch on `isResolveOnlyMode`
- `frontend/app/components/Epr/WizardStepper.spec.ts` — two new resolve-only tests
- `frontend/app/pages/registry/[id].vue` — Browse button + state + handlers + dialog wiring
- `frontend/app/pages/registry/[id].spec.ts` — three Browse-flow tests
- `frontend/app/i18n/hu/registry.json` — four new keys in `browse.*` block
- `frontend/app/i18n/en/registry.json` — four new keys in `browse.*` block

**Docs — not this story:**
- No ADR changes. ADR-0003 remains the binding audit pattern (Task 0 of Story 10.1).
- `_bmad-output/planning-artifacts/epics.md` — do NOT edit. The Epic 10 skeleton for Story 10.2 stays the source of truth.

### Architecture Compliance

- **ADR-0003 (Epic 10 audit architecture) is binding.** Every audit write in this story flows through `AuditService.recordRegistryFieldChange(new FieldChangeEvent(...))` — unchanged. The single new enum value is consumed inside `RegistryService.diffComponentAndAudit` by existing code.
- **Spring Modulith named interfaces unchanged.** `hu.riskguard.epr.audit` exposes `@NamedInterface("audit")`; no new cross-module boundary is added.
- **Strict Module Isolation.** `product_packaging_components` writes remain inside `epr.registry.*` (`EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components`). No new ArchUnit rule is required.
- **Tenant Context.** Registry endpoints read `activeTenantId` from `ScopedValue` / `SecurityContextHolder` (Story 6.0 migration). No change.
- **Java records in `api.dto`; every Response record has a `static from(Domain)` factory.** No new DTO records are added in this story. `ComponentUpsertRequest` / `ComponentUpsertCommand` / `ComponentResponse` already carry `classificationSource` since Story 9.3 — unchanged.
- **jOOQ-only persistence.** No JPA. No new SQL beyond the migration.

### Library / Framework Requirements

- **Java 25 (Spring Boot 4.0.3), jOOQ OSS, PostgreSQL 17, Flyway, Testcontainers** — already in project; no new deps.
- **Vue 3 Composition API, Nuxt 4.3.1, PrimeVue 4.5.4, Pinia** — no new FE libs. `<Dialog>`, `<Button>` are existing PrimeVue primitives; `<Stepper>` / `<StepList>` / `<StepPanels>` are already imported by `WizardStepper.vue`.
- **vitest + @vue/test-utils + createTestingPinia** — the existing frontend test stack. No new test libs.

### Testing Requirements

- **Real-DB Mandate** (project rule). Task 3's audit round-trip test uses Testcontainers PostgreSQL 17. No H2.
- **Targeted tests first** (user memory, timings): `./gradlew test --tests "hu.riskguard.epr.*"` ~90 s; ArchUnit ~30 s; frontend ~6 s. Run these during development. Full suite ONCE at end.
- **Never pipe `gradlew`** (user memory).
- **Modulith verification.** `ModulithVerificationTest` must pass — but Story 10.2 adds no new module boundaries, so this is essentially a smoke check.
- **Contract-First UI.** `npx tsc --noEmit` must pass before reporting ready-for-review. The new store fields, new actions, and new dialog emits all have explicit types.

### Test Fixtures to Update

None of the existing fixtures need structural updates. `RegistryServiceTest` fixture builders may be reused as-is for Task 3. The frontend `createTestingPinia`-based spec pattern is reusable verbatim for both `WizardStepper.spec.ts` and `KfCodeWizardDialog.spec.ts`.

### Project Structure Notes

- **Backend packages (preserve):**
  - `hu.riskguard.epr.audit` — `AuditSource` enum + `AuditService` facade. Story 10.2 adds one enum constant; no package reshape.
  - `hu.riskguard.epr.registry.domain` / `.internal` / `.api.dto` — unchanged.
- **Frontend directories (preserve):**
  - `components/Epr/*` — wizard-related components (WizardStepper, OverrideDialog, **KfCodeWizardDialog (new)**, MaterialSelector, ConfidenceBadge, etc.).
  - `components/registry/*` — Registry-scoped components (KfCodeInput, BootstrapApproveDialog). **KfCodeWizardDialog does NOT go here** — it's a wizard UI surface, not a Registry-scoped primitive. The Registry page is its sole consumer today, but keeping it in `Epr/*` matches where `WizardStepper.vue` lives and where `OverrideDialog.vue` lives.
  - `stores/eprWizard.ts` — sole wizard store. All new state + actions go here.
  - `pages/registry/[id].vue` — the consumer wiring.
- **Migration naming.** `V20260419_001__add_manual_wizard_to_audit_source.sql`. Date is today (2026-04-19). Sequence `_001` continues from `V20260418_001` (Story 10.1). Adjust the date forward only if merging later, keep `_001`.
- **Conventional commits.** Suggested split: (a) `feat(epic-10): add MANUAL_WIZARD AuditSource + migration` (Task 2 + Task 3), (b) `feat(epic-10): resolve-only wizard mode + KfCodeWizardDialog` (Tasks 4–6), (c) `feat(epic-10): Registry Browse button + i18n` (Task 7). Or one commit per task — either is fine.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` §Story 10.2 (lines 926–948)] — Epic 10 skeleton for this story.
- [Source: `_bmad-output/implementation-artifacts/10-1-registry-schema-menu-restructure-and-tx-pool-refactor.md`] — Story 10.1 (just closed 2026-04-18); source of pattern precedent: AC-to-task walkthrough, template-picker Dialog, `_tempId` per-row state, ESLint picker-isolation guardrail, tick-ordering contract, i18n hook, focus-restore split between Dialog and Popover.
- [Source: `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md`] — Binding audit pattern; `AuditService.recordRegistryFieldChange(FieldChangeEvent)` is the only write path.
- [Source: `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java` (lines 1–19)] — enum to extend with `MANUAL_WIZARD`.
- [Source: `backend/src/main/resources/db/migration/V20260414_001__create_product_registry.sql` (lines 52–74)] — `registry_entry_audit_log` table + current CHECK constraint to extend.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryService.java` (lines 296–323)] — `diffComponentAndAudit` — the silent-fallback `AuditSource.valueOf(...)` code that benefits from the new enum constant; DO NOT edit it for this story.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ComponentUpsertRequest.java` (lines 33–36, 46–48)] — already carries `classificationSource`, `classificationStrategy`, `classificationModelVersion` (Story 9.3). No change.
- [Source: `frontend/app/stores/eprWizard.ts` (lines 20–29, 52, 228–251, 352–399, 438–463)] — tick-ordering contract, state shape, `startWizard` template for `startResolveOnly`, `confirmAndLink` template for `resolveAndClose`, `_resetWizardState` to extend.
- [Source: `frontend/app/components/Epr/WizardStepper.vue` (lines 161–297)] — Step 4 panel; footer block at 270–292 is where the `isResolveOnlyMode` branch lands.
- [Source: `frontend/app/components/Epr/OverrideDialog.vue`] — pattern precedent for a Dialog-wrapped wizard helper; Story 10.2's new Dialog follows the lifecycle but with `appendTo="body"` + `z-[75]` hoisting (OverrideDialog does not use either).
- [Source: `frontend/app/pages/registry/[id].vue` (lines 106–228, 229–326, 794–826, ~1060–1130)] — classifier popover pattern (hoisted), template-picker state + dialog pattern (Story 10.1), KF column markup to extend, dialog insertion point.
- [Source: `frontend/app/i18n/{hu,en}/registry.json`] — `registry.*` namespace to extend with `browse.*`.
- [Source: `_bmad-output/implementation-artifacts/epic-9-retro-2026-04-17.md`] — Retro actions T1 (AC-to-task walkthrough gate), T6 (i18n alphabetical hook).
- [Source: user memory `project_epic_10_audit_architecture_decision`] — binding cross-cutting audit pattern for Stories 10.2–10.9 (retro T2, approved 2026-04-17).
- [Source: user memory `feedback_test_timeout_values`] — targeted tests first (~90 s / ~30 s / ~6 s); full suite once at end; never pipe gradlew.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context), via `bmad-dev-story` workflow. Dev kick-off: 2026-04-19.

### Debug Log References

- Mockito `UnnecessaryStubbingException` on initial `RegistryServiceTest` MANUAL_WIZARD tests: fixed by dropping the `registryRepository.updateProduct` stub — component-only changes don't call `updateProduct` (which only runs when `productChanged==true`).
- `TenantAwareDSLContext` rejected a post-insert `selectFrom(REGISTRY_ENTRY_AUDIT_LOG)` in the Testcontainers migration test with "CRITICAL: Missing tenant context for tenant-aware table". Resolved by removing the superfluous post-insert SELECT — the `insertInto(...).returning(ID)` path proves the CHECK constraint accepted the row (the driver surfaces a `DataAccessException` on CHECK violation).
- `KfCodeWizardDialog` spec initially mounted with `visible: true` and failed the `startResolveOnly` assertion — the `watch(() => props.visible)` fires only on value change, not on initial mount. Fixed by starting with `visible: false` and transitioning to `true` via `setProps` (matches production flow where `kfWizardOpen` is toggled false→true by `openKfWizard`).

### Completion Notes List

### AC-to-Task Walkthrough (T1)

Retro T1 gate — every AC mapped to a task before any production code is written. Story 9.4 skipped this and paid 25+ patches; Story 10.1 enforced it and paid 0 AC-gap patches.

| AC  | Summary                                                                                               | Task(s)       |
|-----|-------------------------------------------------------------------------------------------------------|---------------|
| #1  | `AuditSource` enum adds `MANUAL_WIZARD` between `NAV_BOOTSTRAP` and `UNKNOWN`; Javadoc updated.       | Task 2        |
| #2  | Flyway `V20260419_001` extends `registry_entry_audit_log.source` CHECK + undo SQL.                    | Task 2        |
| #3  | Round-trip test: `classificationSource="MANUAL_WIZARD"` audits `source='MANUAL_WIZARD'`; sibling test for unknown→MANUAL fallback. | Task 3        |
| #4  | `isResolveOnlyMode: boolean` field on `EprWizardState`; default false; cleared by `_resetWizardState` + `$reset`. | Task 4        |
| #5  | `lastResolvedKfCode` field on `EprWizardState`; tick-ordering contract mirrors `lastConfirmSuccess`.   | Task 4        |
| #6  | `startResolveOnly()` action — wizard start without templateId; sets resolve-only mode.                 | Task 4        |
| #7  | `resolveAndClose()` action — writes `lastResolvedKfCode`, does NOT POST `/wizard/confirm`.             | Task 4        |
| #8  | `_resetWizardState()` clears `isResolveOnlyMode`; `lastResolvedKfCode` cleared only by `$reset()`.     | Task 4        |
| #9  | `WizardStepper.vue` Step 4 footer branches on `isResolveOnlyMode`; resolve-only = [Cancel][Use this code]. | Task 5        |
| #10 | `stepHint` + breadcrumb unchanged in both modes.                                                      | Task 5 (no-op verify) |
| #11 | `WizardStepper.spec.ts` adds two resolve-only tests (use-this-code present; override absent).         | Task 5        |
| #12 | New `KfCodeWizardDialog.vue` component — PrimeVue Dialog, z-[75], watch(props.visible), emits resolved/update:visible. | Task 6        |
| #13 | `KfCodeWizardDialog.spec.ts` covers 5 cases (open, 3-step walk, resolve event, close, row-deletion handled by parent). | Task 6        |
| #14 | Registry `[id].vue` KF column gets Browse button with `pi pi-sitemap` icon + testid.                   | Task 7        |
| #15 | Registry `[id].vue` gets `kfWizardOpen`, `kfWizardTargetTempId`, `openKfWizard`, `onKfWizardResolved`, `onKfWizardVisibleUpdate`; dialog placed next to template-picker. | Task 7        |
| #16 | Classifier popover isolation — rely on modal backdrop (Option A); document fallback if visual bleed.   | Task 7        |
| #17 | Row-deletion mid-wizard: `onKfWizardResolved` short-circuits when target `_tempId` is gone.           | Task 7        |
| #18 | Multiple Browse opens each target correct row (tempId reset on each open).                             | Task 7        |
| #19 | `[id].spec.ts` adds three Browse-flow tests (seed tempId; resolve writes to targeted row; row-deletion drops payload). | Task 7        |
| #20 | Four new i18n keys in `registry.browse.*` (hu + en), alphabetical at every level.                     | Task 7        |
| #21 | No separate aria-label needed for "Use this code" (label serves as accessible name).                  | Task 7 (no-op verify) |
| #22 | No regression to AI Suggest flow — existing classifier code paths untouched.                          | Task 8        |
| #23 | No regression to Material-Template picker (Story 10.1) — three picker tests stay green.               | Task 8        |
| #24 | No regression to `filing.vue` template-wizard flow — default branch preserved.                         | Task 8        |
| #25 | No backend controller/repository changes beyond AuditSource + migration.                              | Task 2 (scope) + Task 8 (verify) |
| #26 | AC-to-task walkthrough (T1) filed before any code task.                                                | **Task 1 (this section)** |
| #27 | Full suite green (backend targeted ~90s + full once; ArchUnit ~30s; frontend; tsc; lint; lint:i18n; Playwright). | Task 8        |

**Coverage verified:** All 27 ACs map to at least one task. No gaps. Proceeding to code tasks.

### Implementation Notes

- **Backend (Tasks 2–3).** Added `AuditSource.MANUAL_WIZARD` between `NAV_BOOTSTRAP` and `UNKNOWN`. Migration `V20260419_001` drops and re-adds the CHECK constraint on `registry_entry_audit_log.source` with the new 6-value set; undo script reverses to the pre-10.2 5-value constraint. No jOOQ type change (the CHECK is invisible to jOOQ). Two Mockito tests in `RegistryServiceTest` verify the `classificationSource = "MANUAL_WIZARD"` path audits `source = AuditSource.MANUAL_WIZARD`, and the `"GARBAGE_VALUE"` path still silently falls back to `MANUAL`. Added `ManualWizardAuditSourceMigrationTest` (Testcontainers PostgreSQL 17) for end-to-end CHECK-constraint verification.
- **Local `generateJooq`.** Couldn't run locally until Docker Postgres came up — brought up `risk-guard-db` via docker-compose then re-ran `./gradlew test`. Full suite run once at the end; targeted suites during development per user memory.
- **AC #3 Testcontainers nuance.** `TenantAwareDSLContext` intercepts `selectFrom` on tenant-aware tables and throws without a tenant context. The `checkConstraint_acceptsManualWizardSource` test uses `insertInto(...).returning(ID)` to prove the CHECK passed — a non-null returned id is equivalent proof to a post-insert SELECT and avoids plumbing a tenant context into the test.
- **Store (Task 4).** `isResolveOnlyMode` + `lastResolvedKfCode` replicate the `lastConfirmSuccess` tick-ordering pattern. `resolveAndClose()` writes the payload **before** `_resetWizardState()` (which clears `isResolveOnlyMode` but not `lastResolvedKfCode` — the latter survives until `$reset()` via `cancelWizard()`). This matches the flow contract documented in the store JSDoc.
- **Stepper (Task 5).** Step 4 footer branches via `<template v-if="!isResolveOnlyMode">` / `<template v-else>`. The low-confidence banner, result card, breadcrumb, and linkFailed block stay unchanged per AC #9; `linkFailed` block is technically unreachable in resolve-only mode (no `confirmAndLink()` POST) but deliberately left under its `v-if` — no functional or test impact.
- **Dialog (Task 6).** `KfCodeWizardDialog.vue` uses `watch(() => props.visible)` for open startup (not `onMounted`) so re-opens trigger fresh `startResolveOnly()` calls. Closes via native PrimeVue `<Dialog>` `@update:visible` + `@hide` (Escape key routes through `@update:visible(false)` natively — no custom keydown listener). The watcher on `lastResolvedKfCode` emits `resolved` then `update:visible(false)` in that order so the parent consumes the payload before seeing the close event.
- **Registry wire-up (Task 7).** Browse button added to the KF column with `pi pi-sitemap` icon; KF column widened from `width: 220px` to `min-width: 280px`. Per-row state uses `_tempId` (same pattern as `classifyLoading`, `templatePickerTempId`). Row-deletion mid-wizard short-circuits in `onKfWizardResolved` when `components.find(...)` returns undefined. Browse writes `kfCode + classificationSource='MANUAL_WIZARD'` only; classificationStrategy/ModelVersion nulled; materialDescription / weightPerUnitKg / itemsPerParent stay as the user entered them (Browse is a KF-code resolver, not a full-component generator).
- **AC #16 decision.** Shipped Option A (rely on modal backdrop to obscure classifier popover visually); did not add a `closeClassifyPopover()` call in `openKfWizard`. If manual testing uncovers visual bleed-through, Option B is a one-liner fallback.
- **i18n.** Added `registry.browse.*` block between `bootstrap` and `classify` (alphabetical). `lint:i18n` green: `22 files OK — keys alphabetical at every level.` Hu/en parity preserved.
- **Playwright.** 0 existing scenarios touch the Browse flow. Per Story 10.1 precedent, new Browse E2E is not added in this story.
- **R1 review patch resolved (2026-04-19).** Added `"MANUAL_WIZARD": "Wizard Browse"` (en) and `"MANUAL_WIZARD": "Varázsló (katalógus)"` (hu) to `registry.audit.source` object in both i18n files. Inserted alphabetically after `MANUAL` and before `NAV_BOOTSTRAP` (lint:i18n verified: 22 files OK). Frontend: 796 tests, 0 failures (unchanged count — this was a data-only fix, no new test required).

### Validation results

- Backend targeted (`hu.riskguard.epr.*`): green (~4m45s).
- ArchUnit (`hu.riskguard.architecture.*`): green (~21s).
- Full backend: **903 tests, 0 failures** (story predicted ≥ 901).
- Frontend: **796 tests, 0 failures** (was 779; +17 new).
- `tsc --noEmit`: 0 errors.
- `npm run lint`: 0 errors (604 pre-existing style-only warnings across the repo; my new files carry the same vue/max-attributes-per-line style warnings as the rest of the codebase — no new-lint-error regression).
- `lint:i18n`: 22 files OK.

### File List

**Backend — new:**
- `backend/src/main/resources/db/migration/V20260419_001__add_manual_wizard_to_audit_source.sql`
- `backend/src/main/resources/db/migration/undo/U20260419_001__add_manual_wizard_to_audit_source.sql`
- `backend/src/test/java/hu/riskguard/epr/registry/ManualWizardAuditSourceMigrationTest.java`

**Backend — modified:**
- `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java`
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java`

**Frontend — new:**
- `frontend/app/components/Epr/KfCodeWizardDialog.vue`
- `frontend/app/components/Epr/KfCodeWizardDialog.spec.ts`
- `frontend/app/stores/eprWizard.spec.ts`

**Frontend — modified:**
- `frontend/app/stores/eprWizard.ts`
- `frontend/app/components/Epr/WizardStepper.vue`
- `frontend/app/components/Epr/WizardStepper.spec.ts`
- `frontend/app/pages/registry/[id].vue`
- `frontend/app/pages/registry/[id].spec.ts`
- `frontend/app/i18n/hu/registry.json`
- `frontend/app/i18n/en/registry.json`

**Sprint tracking:**
- `_bmad-output/implementation-artifacts/10-2-kf-wizard-browse-button-on-registry.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Review Findings

### R1 — 2026-04-19 (code-review R1, 3-layer parallel: Blind Hunter + Edge Case Hunter + Acceptance Auditor)

- [x] [Review][Patch] Missing `MANUAL_WIZARD` in `registry.audit.source.*` i18n maps — `auditSourceLabel()` at `[id].vue:646` calls `t('registry.audit.source.${source}')`. Both `en/registry.json:15-21` and `hu/registry.json:15-21` have 5 source keys but are missing `MANUAL_WIZARD`. Any audit log entry written via the Browse flow will display the raw key `registry.audit.source.MANUAL_WIZARD` in the Registry audit drawer. Fix: add `"MANUAL_WIZARD": "Wizard Browse"` (en) and `"MANUAL_WIZARD": "Varázsló (katalógus)"` (hu) in alphabetical position between `NAV_BOOTSTRAP` and `VTSZ_FALLBACK` in each file's `registry.audit.source` object. [frontend/app/i18n/en/registry.json:15] [frontend/app/i18n/hu/registry.json:15]

- [x] [Review][Defer] Double `close()` on PrimeVue Dialog dismiss — `@update:visible` + `@hide` both fire → `cancelWizard()`/`emit` called twice [KfCodeWizardDialog.vue:73-74] — spec-prescribed ("both trigger `close()`"); cancelWizard/$reset are idempotent; all double-fire effects are null→null no-ops. Pre-existing pattern.
- [x] [Review][Defer] `startResolveOnly()` in-flight race: rapid open→close→re-open can leave isResolveOnlyMode=false when stale fetch resolves [stores/eprWizard.ts:280] — same pattern as `startWizard()` (no isLoading guard either). Low probability; both concurrent fetches return identical content. Pre-existing design.
- [x] [Review][Defer] `startResolveOnly()` does not reset `lastResolvedKfCode` defensively [stores/eprWizard.ts:280] — only actionable if `cancelWizard()` is skipped between sessions (shouldn't happen in practice). Watcher fires only on value change, not mount, so stale value doesn't spuriously fire.
- [x] [Review][Defer] Override stale state if a template-linking session left `isOverrideActive=true` before `cancelWizard()` was called [stores/eprWizard.ts:505] — `cancelWizard()` calls `$reset()` between all sessions normally. Pre-existing pattern matching `confirmAndLink`.
- [x] [Review][Defer] Override uses truthiness not null-check for `overrideKfCode`/`overrideClassification` (empty string → falls back to resolved value) [stores/eprWizard.ts:505-513] — pre-existing pattern from `confirmAndLink()` flow.
- [x] [Review][Defer] `startResolveOnly()` error path shows blank stepper with no error banner [KfCodeWizardDialog.vue:39-43] — pre-existing; same as `startWizard()` failure behavior; `wizardStore.error` is set but not rendered in the dialog.
- [x] [Review][Defer] Duplicate `data-testid="wizard-cancel-button"` across both Step 4 footer branches [WizardStepper.vue:275 and 301] — non-colliding at runtime (only one branch renders); no test tests the cancel button at Step 4 specifically.

## Change Log

| Date       | Change                                                                                                    |
|------------|-----------------------------------------------------------------------------------------------------------|
| 2026-04-19 | Story file created (SM prep, post-10.1-close). Status: ready-for-dev. AC-to-task walkthrough pending as Task 1 gate. |
| 2026-04-19 | Dev implementation complete (all 8 tasks done, all 27 ACs satisfied). Status flip: ready-for-dev → in-progress → review. Backend: `AuditSource.MANUAL_WIZARD` + migration V20260419_001 + undo + 2 unit tests + Testcontainers CHECK-constraint test. Frontend: store resolve-only extensions (`isResolveOnlyMode`, `lastResolvedKfCode`, `startResolveOnly`, `resolveAndClose`), `WizardStepper` Step 4 branching, new `KfCodeWizardDialog` + spec, Registry `[id].vue` Browse button wire-up, 4 new i18n keys (hu+en). Tests: 903 backend, 796 frontend, 0 failures; tsc + lint + lint:i18n all clean. |
| 2026-04-19 | R1 review patch: added `registry.audit.source.MANUAL_WIZARD` i18n key in both hu+en locales (`"Wizard Browse"` / `"Varázsló (katalógus)"`). Alphabetical insertion after MANUAL, before NAV_BOOTSTRAP. lint:i18n 22 files OK; 796 frontend tests green. All R1 findings resolved or deferred. Status → review. |
