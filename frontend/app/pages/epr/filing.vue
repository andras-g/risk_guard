<script setup lang="ts">
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputNumber from 'primevue/inputnumber'
import Panel from 'primevue/panel'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useAuthStore } from '~/stores/auth'
import { useEprStore } from '~/stores/epr'
import { useEprFilingStore } from '~/stores/eprFiling'
import type { InvoiceAutoFillLineDto } from '~/composables/api/useInvoiceAutoFill'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const authStore = useAuthStore()
const eprStore = useEprStore()
const filingStore = useEprFilingStore()

// Accountant with home tenant active = no client selected yet
const needsClientSelection = computed(() =>
  authStore.isAccountant && authStore.activeTenantId === authStore.homeTenantId,
)

onMounted(async () => {
  if (needsClientSelection.value) return
  if (hasAccess.value) {
    await eprStore.fetchMaterials()
    filingStore.initFromTemplates(eprStore.materials)
  }
})

function formatHuf(value: number): string {
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'
}

async function handleCalculate() {
  try {
    await filingStore.calculate()
  }
  catch (e: unknown) {
    const message = (e as { data?: { detail?: string } })?.data?.detail
      ?? (e instanceof Error ? e.message : 'Unknown error')
    toast.add({
      severity: 'error',
      summary: t('epr.filing.calculateError', { message }),
      life: 5000,
    })
  }
}

const unmatchedLines = ref<InvoiceAutoFillLineDto[]>([])

function onAutoFillApply(lines: InvoiceAutoFillLineDto[]) {
  unmatchedLines.value = []
  for (const line of lines) {
    if (line.existingTemplateId) {
      filingStore.updateQuantity(line.existingTemplateId, Math.ceil(line.aggregatedQuantity).toString())
    }
    else {
      unmatchedLines.value.push(line)
    }
  }
}

async function handleExport() {
  try {
    await filingStore.exportMohu()
    toast.add({
      severity: 'info',
      summary: t('epr.filing.exportLocaleNotice'),
      life: 5000,
    })
  }
  catch (e: unknown) {
    const message = (e as { data?: { detail?: string } })?.data?.detail
      ?? (e instanceof Error ? e.message : 'Unknown error')
    toast.add({
      severity: 'error',
      summary: t('epr.filing.exportError', { message }),
      life: 5000,
    })
  }
}
</script>

<template>
  <!-- Accountant with no client selected -->
  <div
    v-if="needsClientSelection"
    class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto"
    data-testid="epr-select-customer"
  >
    <i class="pi pi-info-circle text-6xl text-indigo-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('epr.selectCustomer.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.selectCustomer.description') }}
    </p>
  </div>

  <!-- Tier Gate -->
  <div v-else-if="!hasAccess" class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto">
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('epr.materialLibrary.tierGate.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <!-- Filing Page -->
  <div v-else class="w-full">
    <!-- Page Header -->
    <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
      <h1 class="text-2xl font-bold text-slate-800">
        {{ t('epr.filing.title') }}
      </h1>
      <div class="flex gap-2">
        <Button
          :label="t('epr.filing.backToLibrary')"
          icon="pi pi-arrow-left"
          severity="secondary"
          outlined
          data-testid="back-to-library-button"
          @click="router.push('/epr')"
        />
      </div>
    </div>

    <!-- Invoice Auto-Fill Panel -->
    <Panel
      :toggleable="true"
      :collapsed="true"
      :header="t('epr.autofill.panelTitle')"
      class="mb-6"
      data-testid="autofill-panel"
    >
      <InvoiceAutoFillPanel @apply="onAutoFillApply" />
    </Panel>

    <!-- Unmatched lines from auto-fill -->
    <div
      v-if="unmatchedLines.length > 0"
      class="mb-4 flex flex-wrap gap-2"
      data-testid="unmatched-lines"
    >
      <Tag
        v-for="line in unmatchedLines"
        :key="line.vtszCode + '-' + line.unitOfMeasure"
        severity="warning"
        :value="line.description || line.vtszCode"
        data-testid="unmatched-tag"
      />
    </div>

    <!-- Loading State -->
    <div v-if="eprStore.isLoading" class="flex justify-center py-12">
      <i class="pi pi-spin pi-spinner text-4xl text-slate-400" aria-hidden="true" />
    </div>

    <!-- Empty State -->
    <div
      v-else-if="filingStore.lines.length === 0"
      class="flex flex-col items-center justify-center py-12 text-center"
      data-testid="empty-state"
    >
      <i class="pi pi-box text-5xl text-slate-300 mb-3" aria-hidden="true" />
      <p class="text-slate-500">{{ t('epr.filing.emptyState') }}</p>
    </div>

    <!-- Filing Table -->
    <template v-else>
      <div class="overflow-x-auto mb-6">
        <DataTable :value="filingStore.lines" data-testid="filing-table">
          <Column field="name" :header="t('epr.filing.table.name')" />
          <Column field="kfCode" :header="t('epr.filing.table.kfCode')" />
          <Column field="baseWeightGrams" :header="t('epr.filing.table.baseWeight')" />
          <Column :header="t('epr.filing.table.feeRate')">
            <template #body="{ data: line }">
              <span v-if="line.feeRateHufPerKg !== null">{{ line.feeRateHufPerKg }}</span>
              <span v-else class="text-slate-400">—</span>
            </template>
          </Column>
          <Column :header="t('epr.filing.table.quantity')">
            <template #body="{ data: line }">
              <div class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                  <InputNumber
                    :model-value="line.quantityPcs"
                    :use-grouping="false"
                    :min-fraction-digits="0"
                    :max-fraction-digits="0"
                    :min="1"
                    input-class="w-20"
                    :class="[
                      line.isValid
                        ? 'border border-emerald-500 rounded'
                        : (line.validationMessage ? 'border border-red-600 rounded' : ''),
                    ]"
                    :data-testid="`quantity-input-${line.templateId}`"
                    @input="filingStore.updateQuantity(line.templateId, $event.value?.toString() ?? '')"
                  />
                  <i
                    v-if="line.isValid"
                    class="pi pi-check-circle text-emerald-500"
                    aria-hidden="true"
                  />
                </div>
                <span
                  v-if="line.validationMessage"
                  class="text-red-600 text-xs"
                  :data-testid="`validation-msg-${line.templateId}`"
                >
                  {{ t(line.validationMessage) }}
                </span>
              </div>
            </template>
          </Column>
          <Column :header="t('epr.filing.table.totalWeight')">
            <template #body="{ data: line }">
              <span v-if="filingStore.isCalculating">
                <i class="pi pi-spin pi-spinner" aria-hidden="true" />
              </span>
              <span v-else-if="line.totalWeightKg !== null">
                {{ line.totalWeightKg.toFixed(3) }} kg
              </span>
              <span v-else class="text-slate-400">—</span>
            </template>
          </Column>
          <Column :header="t('epr.filing.table.feeAmount')">
            <template #body="{ data: line }">
              <span v-if="filingStore.isCalculating">
                <i class="pi pi-spin pi-spinner" aria-hidden="true" />
              </span>
              <span v-else-if="line.feeAmountHuf !== null">
                {{ formatHuf(line.feeAmountHuf) }}
              </span>
              <span v-else class="text-slate-400">—</span>
            </template>
          </Column>
        </DataTable>
      </div>

      <!-- Calculate Button -->
      <div class="flex justify-end mb-6">
        <Button
          :label="t('epr.filing.calculateButton')"
          icon="pi pi-calculator"
          :disabled="!filingStore.hasValidLines || filingStore.isCalculating"
          :loading="filingStore.isCalculating"
          data-testid="calculate-button"
          @click="handleCalculate"
        />
      </div>

      <!-- Export for MOHU button (only visible after a successful Calculate) -->
      <div v-if="filingStore.serverResult !== null" class="flex justify-end mb-6">
        <Button
          :label="filingStore.isExporting ? t('epr.filing.exportGenerating') : t('epr.filing.exportButton')"
          icon="pi pi-download"
          :disabled="filingStore.isExporting"
          :loading="filingStore.isExporting"
          data-testid="export-mohu-button"
          @click="handleExport"
        />
      </div>

      <!-- Filing Summary (always visible — shows dashes until Calculate is run) -->
      <div
        class="bg-white border border-slate-200 rounded-lg p-6"
        data-testid="filing-summary"
      >
        <h2 class="text-lg font-semibold text-slate-800 mb-4">
          {{ t('epr.filing.summaryTitle') }}
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <p class="text-sm text-slate-500">{{ t('epr.filing.totalLines') }}</p>
            <p class="text-2xl font-bold text-slate-800" data-testid="summary-total-lines">
              <span v-if="filingStore.serverResult !== null">{{ filingStore.serverResult.lines.length }}</span>
              <span v-else class="text-slate-400">—</span>
            </p>
          </div>
          <div>
            <p class="text-sm text-slate-500">{{ t('epr.filing.grandTotalWeight') }}</p>
            <p class="text-2xl font-bold text-slate-800" data-testid="summary-total-weight">
              <span v-if="filingStore.serverResult !== null">{{ filingStore.grandTotalWeightKg.toFixed(3) }} kg</span>
              <span v-else class="text-slate-400">—</span>
            </p>
          </div>
          <div>
            <p class="text-sm text-slate-500">{{ t('epr.filing.grandTotalFee') }}</p>
            <p class="text-2xl font-bold text-emerald-700" data-testid="summary-total-fee">
              <span v-if="filingStore.serverResult !== null">{{ formatHuf(filingStore.grandTotalFeeHuf) }}</span>
              <span v-else class="text-slate-400">—</span>
            </p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
