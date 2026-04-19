<script setup lang="ts">
import Stepper from 'primevue/stepper'
import StepList from 'primevue/steplist'
import StepPanels from 'primevue/steppanels'
import Step from 'primevue/step'
import StepPanel from 'primevue/steppanel'
import Button from 'primevue/button'
import { useEprWizardStore } from '~/stores/eprWizard'
import type { WizardOption } from '~/types/epr'

const { t, locale } = useI18n()
const wizardStore = useEprWizardStore()

// Re-fetch wizard options when locale changes so card labels update
watch(locale, () => {
  if (wizardStore.isActive) {
    wizardStore.refreshOptions()
  }
})

defineEmits<{
  (e: 'openOverride'): void
}>()

async function onSelect(option: WizardOption, activateCallback: (value: string) => void) {
  const currentStep = wizardStore.activeStep || '1'
  // Step 3 is reused for both group and subgroup selections.
  // If the traversal path already contains a 'group' entry, the current step-3 selection is 'subgroup'.
  // This prevents deposit packaging (stream 12) and other flows from sending the wrong level
  // and corrupting the traversal path.
  const hasGroup = wizardStore.traversalPath.some(s => s.level === 'group')
  const levelMap: Record<string, string> = {
    '1': 'product_stream',
    '2': 'material_stream',
    '3': hasGroup ? 'subgroup' : 'group',
  }
  const level = levelMap[currentStep] || 'subgroup'

  try {
    await wizardStore.selectOption({
      level,
      code: option.code,
      label: option.label,
    })
    // Advance to the next step in the UI
    if (wizardStore.activeStep) {
      activateCallback(wizardStore.activeStep)
    }
  }
  catch {
    // Error is handled by store + page-level toast
  }
}

/**
 * Returns a contextual hint for the current wizard step.
 * Helps the user understand what each level means relative to their previous selections.
 */
const stepHint = computed((): string | null => {
  const step = wizardStore.activeStep
  if (!step || step === '4') return null

  if (step === '1') return t('epr.wizard.hints.step1')
  if (step === '2') return t('epr.wizard.hints.step2')

  // Step 3 — hint depends on context
  const hasGroup = wizardStore.traversalPath.some(s => s.level === 'group')
  if (hasGroup) {
    // Selecting subgroup
    return t('epr.wizard.hints.step3subgroup')
  }

  // Selecting group — provide context-specific hint for deposit packaging
  const productStream = wizardStore.traversalPath.find(s => s.level === 'product_stream')
  if (productStream?.code === '12') {
    return t('epr.wizard.hints.step3depositGroup')
  }
  return t('epr.wizard.hints.step3group')
})

function formatKfCode(code: string): string {
  if (code.length !== 8) return code
  return `${code.slice(0, 2)} ${code.slice(2, 4)} ${code.slice(4, 6)} ${code.slice(6, 8)}`
}
</script>

<template>
  <div
    class="bg-white border border-slate-200 rounded-xl p-6"
    data-testid="wizard-stepper"
  >
    <!-- Breadcrumb trail -->
    <div
      v-if="wizardStore.breadcrumb.length > 0"
      class="text-sm text-slate-500 mb-4"
      data-testid="wizard-breadcrumb"
    >
      <span
        v-for="(crumb, index) in wizardStore.breadcrumb"
        :key="index"
      >
        <span class="font-medium text-slate-700">{{ crumb.label }}</span>
        <span
          v-if="index < wizardStore.breadcrumb.length - 1"
          class="mx-2"
        >→</span>
      </span>
    </div>

    <!-- PrimeVue 4 Stepper -->
    <Stepper :value="wizardStore.activeStep || '1'" linear>
      <StepList>
        <Step value="1">{{ t('epr.wizard.step1Title') }}</Step>
        <Step value="2">{{ t('epr.wizard.step2Title') }}</Step>
        <Step value="3">{{ t('epr.wizard.step3Title') }}</Step>
        <Step value="4">{{ t('epr.wizard.resultTitle') }}</Step>
      </StepList>
      <StepPanels>
        <!-- Step 1: Product Stream -->
        <StepPanel v-slot="{ activateCallback }" value="1">
          <p v-if="stepHint" class="text-sm text-slate-500 mb-4 flex items-start gap-2">
            <i class="pi pi-info-circle text-slate-400 mt-0.5 shrink-0" />
            <span>{{ stepHint }}</span>
          </p>
          <EprMaterialSelector
            :options="wizardStore.availableOptions"
            :selected-code="null"
            :is-loading="wizardStore.isLoading"
            @select="onSelect($event, activateCallback)"
          />
        </StepPanel>

        <!-- Step 2: Material & Usage -->
        <StepPanel v-slot="{ activateCallback }" value="2">
          <p v-if="stepHint" class="text-sm text-slate-500 mb-4 flex items-start gap-2">
            <i class="pi pi-info-circle text-slate-400 mt-0.5 shrink-0" />
            <span>{{ stepHint }}</span>
          </p>
          <EprMaterialSelector
            :options="wizardStore.availableOptions"
            :selected-code="null"
            :is-loading="wizardStore.isLoading"
            @select="onSelect($event, activateCallback)"
          />
        </StepPanel>

        <!-- Step 3: Subtype / Group -->
        <StepPanel v-slot="{ activateCallback }" value="3">
          <p v-if="stepHint" class="text-sm text-slate-500 mb-4 flex items-start gap-2">
            <i class="pi pi-info-circle text-slate-400 mt-0.5 shrink-0" />
            <span>{{ stepHint }}</span>
          </p>
          <EprMaterialSelector
            :options="wizardStore.availableOptions"
            :selected-code="null"
            :is-loading="wizardStore.isLoading"
            @select="onSelect($event, activateCallback)"
          />
        </StepPanel>

        <!-- Step 4: Result / Confirm -->
        <StepPanel value="4">
          <div v-if="wizardStore.resolvedResult">
            <!-- LOW confidence warning banner -->
            <div
              v-if="wizardStore.resolvedResult.confidenceScore === 'LOW'"
              class="bg-amber-50 border border-amber-300 rounded-lg p-3 mb-3"
              data-testid="wizard-low-confidence-warning"
            >
              <i class="pi pi-exclamation-triangle text-amber-600 mr-2" />
              <span class="text-amber-800">
                {{ t(`epr.wizard.confidence.reason.${wizardStore.resolvedResult.confidenceReason}`) }}
              </span>
            </div>

            <div
              class="bg-emerald-50 border-2 border-[#15803D] rounded-xl p-6"
              data-testid="wizard-result-card"
            >
              <!-- Confidence badge + KF-code -->
              <div class="text-center mb-4">
                <div class="text-sm text-slate-600 mb-1">{{ t('epr.wizard.kfCodeLabel') }}</div>
                <div class="flex items-center justify-center gap-2">
                  <div class="text-2xl font-bold text-slate-900">
                    {{ wizardStore.isOverrideActive
                      ? formatKfCode(wizardStore.overrideKfCode!)
                      : formatKfCode(wizardStore.resolvedResult.kfCode) }}
                  </div>
                  <EprConfidenceBadge
                    :confidence="wizardStore.resolvedResult.confidenceScore"
                    data-testid="wizard-confidence-badge"
                  />
                  <span
                    v-if="wizardStore.isOverrideActive"
                    class="text-xs bg-slate-200 text-slate-700 px-2 py-0.5 rounded-full"
                    data-testid="wizard-overridden-badge"
                  >
                    {{ t('epr.wizard.override.badge') }}
                  </span>
                </div>
                <!-- Original suggestion when override is active -->
                <div
                  v-if="wizardStore.isOverrideActive"
                  class="text-sm text-slate-400 mt-1"
                  data-testid="wizard-original-suggestion"
                >
                  {{ t('epr.wizard.override.original') }}: {{ formatKfCode(wizardStore.resolvedResult.kfCode) }}
                </div>
              </div>

              <div class="text-center mb-4">
                <div class="text-sm text-slate-600 mb-1">{{ t('epr.wizard.feeRateLabel') }}</div>
                <div class="text-xl font-semibold text-slate-800">
                  {{ wizardStore.isOverrideActive
                    ? wizardStore.overrideFeeRate
                    : wizardStore.resolvedResult.feeRate }} Ft/kg
                </div>
              </div>
              <div class="text-center mb-6">
                <div class="text-sm text-slate-600 mb-1">{{ t('epr.wizard.classificationLabel') }}</div>
                <div class="text-base text-slate-700">
                  {{ wizardStore.isOverrideActive
                    ? wizardStore.overrideClassification
                    : wizardStore.resolvedResult.materialClassification }}
                </div>
              </div>

              <!-- Breadcrumb in result card -->
              <div class="text-sm text-slate-400 text-center mb-6">
                <span
                  v-for="(crumb, index) in wizardStore.resolvedResult.traversalPath"
                  :key="index"
                >
                  {{ crumb.label }}
                  <span v-if="index < wizardStore.resolvedResult.traversalPath.length - 1"> → </span>
                </span>
              </div>

              <!-- Link failure warning -->
              <div
                v-if="wizardStore.linkFailed"
                class="bg-amber-50 border border-amber-300 rounded-lg p-4 mt-3"
                data-testid="wizard-link-failed-banner"
              >
                <div class="flex items-start gap-2">
                  <i class="pi pi-exclamation-triangle text-amber-600 mt-0.5" />
                  <div>
                    <p class="text-amber-800 font-medium">{{ t('epr.wizard.linkFailed') }}</p>
                  </div>
                </div>
                <div class="flex gap-3 mt-3">
                  <Button
                    :label="t('epr.wizard.retryLink')"
                    :loading="wizardStore.isLoading"
                    class="!bg-[#1e3a5f] !border-[#1e3a5f]"
                    data-testid="wizard-retry-link-button"
                    @click="wizardStore.retryLink()"
                  />
                  <Button
                    :label="t('epr.wizard.closeWithoutLinking')"
                    severity="secondary"
                    outlined
                    data-testid="wizard-close-without-linking-button"
                    @click="wizardStore.closeWithoutLinking()"
                  />
                </div>
              </div>

              <!-- Action buttons (hidden when link failed — retry/close buttons replace them) -->
              <div v-if="!wizardStore.linkFailed" class="flex flex-col sm:flex-row justify-center gap-3">
                <!-- Template-linking mode (default) — Story 4.x flow -->
                <template v-if="!wizardStore.isResolveOnlyMode">
                  <Button
                    :label="t('epr.wizard.cancel')"
                    severity="secondary"
                    outlined
                    data-testid="wizard-cancel-button"
                    @click="wizardStore.cancelWizard()"
                  />
                  <Button
                    :label="t('epr.wizard.override.button')"
                    severity="secondary"
                    outlined
                    data-testid="wizard-override-button"
                    @click="$emit('openOverride')"
                  />
                  <Button
                    :label="t('epr.wizard.confirmAndLink')"
                    :loading="wizardStore.isLoading"
                    class="!bg-[#1e3a5f] !border-[#1e3a5f]"
                    data-testid="wizard-confirm-button"
                    @click="wizardStore.confirmAndLink()"
                  />
                </template>
                <!-- Resolve-only mode (Story 10.2 Registry Browse) — no template link, no override -->
                <template v-else>
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
                </template>
              </div>
            </div>
          </div>
        </StepPanel>
      </StepPanels>
    </Stepper>

    <!-- Navigation buttons -->
    <div v-if="wizardStore.activeStep !== '4'" class="flex justify-between mt-4">
      <Button
        v-if="wizardStore.traversalPath.length > 0"
        :label="t('epr.wizard.back')"
        icon="pi pi-arrow-left"
        severity="secondary"
        text
        size="small"
        :loading="wizardStore.isLoading"
        data-testid="wizard-back-button"
        @click="wizardStore.goBack()"
      />
      <span v-else />
      <Button
        :label="t('epr.wizard.cancel')"
        severity="secondary"
        text
        size="small"
        data-testid="wizard-cancel-inline"
        @click="wizardStore.cancelWizard()"
      />
    </div>
  </div>
</template>
