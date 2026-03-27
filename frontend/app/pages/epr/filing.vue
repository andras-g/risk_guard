<script setup lang="ts">
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputNumber from 'primevue/inputnumber'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useEprStore } from '~/stores/epr'
import { useEprFilingStore } from '~/stores/eprFiling'

const { t } = useI18n()
const router = useRouter()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const eprStore = useEprStore()
const filingStore = useEprFilingStore()

onMounted(async () => {
  if (hasAccess.value) {
    await eprStore.fetchMaterials()
    filingStore.initFromTemplates(eprStore.materials)
  }
})

function formatHuf(value: number): string {
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'
}

async function handleCalculate() {
  await filingStore.calculate()
}
</script>

<template>
  <!-- Tier Gate -->
  <div v-if="!hasAccess" class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto">
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('epr.materialLibrary.tierGate.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <!-- Filing Page -->
  <div v-else class="mx-auto p-6">
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
      <DataTable :value="filingStore.lines" class="mb-6" data-testid="filing-table">
        <Column field="name" :header="t('epr.filing.table.name')" />
        <Column field="kfCode" :header="t('epr.filing.table.kfCode')" />
        <Column field="baseWeightGrams" :header="t('epr.filing.table.baseWeight')" />
        <Column field="feeRateHufPerKg" :header="t('epr.filing.table.feeRate')" />
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
