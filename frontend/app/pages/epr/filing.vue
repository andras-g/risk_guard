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

// Export period state — defaults to current quarter
const currentDate = new Date()
const currentYear = currentDate.getFullYear()
const currentQuarter = Math.ceil((currentDate.getMonth() + 1) / 3)
const quarterStartMonth = (currentQuarter - 1) * 3 + 1
const exportFrom = ref(
  `${currentYear}-${String(quarterStartMonth).padStart(2, '0')}-01`,
)
const exportTo = ref(
  new Date(currentYear, quarterStartMonth + 2, 0).toISOString().slice(0, 10),
)
const exportTaxNumber = ref('')

// Accountant with home tenant active = no client selected yet
const needsClientSelection = computed(() =>
  authStore.isAccountant && authStore.activeTenantId === authStore.homeTenantId,
)

onMounted(async () => {
  filingStore.exportError = null
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

// Provenance panel state
const previewLoading = ref(false)
const previewData = ref<OkirkapuPreviewData | null>(null)
const previewError = ref<string | null>(null)

interface OkirkapuPreviewLine {
  invoiceNumber: string
  lineNumber: number
  vtszCode: string
  productName: string
  quantity: string
  unitOfMeasure: string
  tag: 'REGISTRY_MATCH' | 'VTSZ_FALLBACK' | 'UNMATCHED'
  resolvedKfCode: string | null
  aggregatedWeightKg: string | null
}

interface OkirkapuPreviewData {
  provenanceLines: OkirkapuPreviewLine[]
  summaryReport: string
}

async function handlePreview() {
  if (!exportTaxNumber.value) return
  previewLoading.value = true
  previewError.value = null
  previewData.value = null
  try {
    const config = useRuntimeConfig()
    const result = await $fetch<OkirkapuPreviewData>('/api/v1/epr/filing/okirkapu-preview', {
      method: 'POST',
      body: { from: exportFrom.value, to: exportTo.value, taxNumber: exportTaxNumber.value },
      baseURL: config.public.apiBase as string,
      credentials: 'include',
    })
    previewData.value = result
  }
  catch (e: unknown) {
    const status = (e as { status?: number })?.status
    if (status === 412) {
      previewError.value = 'producer.profile.incomplete'
    }
    else {
      previewError.value = e instanceof Error ? e.message : String(e)
    }
  }
  finally {
    previewLoading.value = false
  }
}

function registerProductRoute(vtsz: string, name: string): string {
  return `/registry/new?vtsz=${encodeURIComponent(vtsz)}&name=${encodeURIComponent(name)}`
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
    await filingStore.exportOkirkapu(exportFrom.value, exportTo.value, exportTaxNumber.value)
  }
  catch (e: unknown) {
    if (filingStore.exportError === 'producer.profile.incomplete') {
      toast.add({
        severity: 'warn',
        summary: t('epr.okirkapu.profileIncomplete'),
        detail: t('epr.okirkapu.profileIncompleteDetail'),
        life: 8000,
      })
      return
    }
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

      <!-- OKIRkapu XML Export Panel -->
      <div class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="okirkapu-export-panel">
        <h2 class="text-lg font-semibold text-slate-800 mb-4">
          {{ t('epr.okirkapu.exportTitle') }}
        </h2>
        <div class="flex flex-wrap gap-3 items-end mb-4">
          <div class="flex flex-col gap-1">
            <label for="export-from-input" class="text-sm text-slate-600">{{ t('epr.autofill.fromLabel') }}</label>
            <input
              id="export-from-input"
              v-model="exportFrom"
              type="date"
              class="border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="export-from-input"
            />
          </div>
          <div class="flex flex-col gap-1">
            <label for="export-to-input" class="text-sm text-slate-600">{{ t('epr.autofill.toLabel') }}</label>
            <input
              id="export-to-input"
              v-model="exportTo"
              type="date"
              class="border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="export-to-input"
            />
          </div>
          <div class="flex flex-col gap-1">
            <label for="export-tax-number-input" class="text-sm text-slate-600">{{ t('epr.autofill.taxNumberLabel') }}</label>
            <input
              id="export-tax-number-input"
              v-model="exportTaxNumber"
              type="text"
              :placeholder="t('epr.autofill.taxNumberPlaceholder')"
              class="border border-slate-300 rounded px-3 py-2 text-sm w-32"
              data-testid="export-tax-number-input"
            />
          </div>
        </div>
        <!-- Profile incomplete warning -->
        <div
          v-if="filingStore.exportError === 'producer.profile.incomplete'"
          class="mb-4 p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3"
          data-testid="profile-incomplete-warning"
        >
          <i class="pi pi-exclamation-triangle text-amber-500" aria-hidden="true" />
          <span class="text-amber-800 text-sm">{{ t('epr.okirkapu.profileIncomplete') }}</span>
          <Button
            :label="t('epr.okirkapu.openSettings')"
            severity="warning"
            size="small"
            outlined
            @click="router.push('/settings/producer-profile')"
          />
        </div>
        <div class="flex gap-2">
          <Button
            :label="t('epr.okirkapu.preview.title')"
            icon="pi pi-eye"
            severity="secondary"
            outlined
            :disabled="previewLoading || !exportTaxNumber"
            :loading="previewLoading"
            data-testid="preview-report-button"
            @click="handlePreview"
          />
          <Button
            :label="filingStore.isExporting ? t('epr.okirkapu.exportGenerating') : t('epr.okirkapu.exportButton')"
            icon="pi pi-file-export"
            :disabled="filingStore.isExporting || !exportTaxNumber"
            :loading="filingStore.isExporting"
            data-testid="export-okirkapu-button"
            @click="handleExport"
          />
        </div>
      </div>

      <!-- OKIRkapu Provenance Preview Panel -->
      <div v-if="previewData || previewLoading || previewError" class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="provenance-panel">
        <h2 class="text-lg font-semibold text-slate-800 mb-4">
          {{ t('epr.okirkapu.preview.title') }}
        </h2>

        <div v-if="previewLoading" class="flex justify-center py-8">
          <i class="pi pi-spin pi-spinner text-3xl text-slate-400" aria-hidden="true" />
        </div>

        <div v-else-if="previewError === 'producer.profile.incomplete'" class="p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3">
          <span class="text-amber-800 text-sm">{{ t('epr.okirkapu.preview.profileIncomplete') }}</span>
          <Button
            :label="t('epr.okirkapu.preview.profileOpenSettings')"
            severity="warning"
            size="small"
            outlined
            class="ml-3"
            @click="router.push('/settings/producer-profile')"
          />
        </div>

        <div v-else-if="previewError" class="p-3 bg-red-50 border border-red-200 rounded text-red-800 text-sm">
          {{ t('epr.okirkapu.exportError', { message: previewError }) }}
        </div>

        <template v-else-if="previewData">
          <!-- REGISTRY_MATCH rows -->
          <div v-if="previewData.provenanceLines.filter(l => l.tag === 'REGISTRY_MATCH').length > 0" class="mb-4">
            <h3 class="text-sm font-semibold text-emerald-700 mb-2">
              {{ t('epr.okirkapu.preview.registryMatch') }}
            </h3>
            <div
              v-for="line in previewData.provenanceLines.filter(l => l.tag === 'REGISTRY_MATCH')"
              :key="`${line.invoiceNumber}-${line.lineNumber}`"
              class="text-xs p-1 border-b border-emerald-100 flex gap-2 text-slate-700"
              data-testid="provenance-registry-match"
            >
              <span>{{ line.invoiceNumber }}/{{ line.lineNumber }}</span>
              <span>{{ line.vtszCode }}</span>
              <span>{{ line.productName }}</span>
              <span>{{ line.resolvedKfCode }}</span>
              <span>{{ line.aggregatedWeightKg }} kg</span>
            </div>
          </div>

          <!-- VTSZ_FALLBACK rows -->
          <div v-if="previewData.provenanceLines.filter(l => l.tag === 'VTSZ_FALLBACK').length > 0" class="mb-4">
            <h3 class="text-sm font-semibold text-amber-700 mb-2">
              {{ t('epr.okirkapu.preview.vtszFallback') }}
            </h3>
            <div
              v-for="line in previewData.provenanceLines.filter(l => l.tag === 'VTSZ_FALLBACK')"
              :key="`${line.invoiceNumber}-${line.lineNumber}`"
              class="text-xs p-1 border-b border-amber-100 flex gap-2 text-slate-700"
              data-testid="provenance-vtsz-fallback"
            >
              <span>{{ line.invoiceNumber }}/{{ line.lineNumber }}</span>
              <span>{{ line.vtszCode }}</span>
              <span>{{ line.productName }}</span>
              <span>{{ line.resolvedKfCode }}</span>
            </div>
          </div>

          <!-- UNMATCHED rows -->
          <div v-if="previewData.provenanceLines.filter(l => l.tag === 'UNMATCHED').length > 0" class="mb-4">
            <h3 class="text-sm font-semibold text-red-700 mb-2">
              {{ t('epr.okirkapu.preview.unmatched') }}
            </h3>
            <div
              v-for="line in previewData.provenanceLines.filter(l => l.tag === 'UNMATCHED')"
              :key="`${line.invoiceNumber}-${line.lineNumber}`"
              class="text-xs p-1 border-b border-red-100 flex gap-2 items-center text-slate-700"
              data-testid="provenance-unmatched"
            >
              <span>{{ line.invoiceNumber }}/{{ line.lineNumber }}</span>
              <span>{{ line.vtszCode }}</span>
              <span>{{ line.productName }}</span>
              <Button
                :label="t('epr.okirkapu.preview.unmatchedRegisterShortcut')"
                size="small"
                severity="danger"
                outlined
                @click="router.push(registerProductRoute(line.vtszCode, line.productName))"
              />
            </div>
          </div>
        </template>
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
