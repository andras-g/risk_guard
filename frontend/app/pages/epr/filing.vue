<script setup lang="ts">
import Button from 'primevue/button'
import Panel from 'primevue/panel'
import Skeleton from 'primevue/skeleton'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import { useToast } from 'primevue/usetoast'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useAuthStore } from '~/stores/auth'
import { useEprFilingStore } from '~/stores/eprFiling'
import { useRegistryCompleteness } from '~/composables/api/useRegistryCompleteness'
import { useEprFilingProvenance } from '~/composables/api/useEprFilingProvenance'
import { useEprSubmissions } from '~/composables/api/useEprSubmissions'
import EprSoldProductsTable from '~/components/Epr/EprSoldProductsTable.vue'
import EprKfTotalsTable from '~/components/Epr/EprKfTotalsTable.vue'
import EprProvenanceTable from '~/components/Epr/EprProvenanceTable.vue'
import EprSubmissionsTable from '~/components/Epr/EprSubmissionsTable.vue'
import type { UnresolvedInvoiceLine } from '~/types/epr'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const authStore = useAuthStore()
const filingStore = useEprFilingStore()
const registryCompleteness = useRegistryCompleteness()
const provenance = useEprFilingProvenance()
const submissions = useEprSubmissions()

// Accountant with home tenant active = no client selected yet
const needsClientSelection = computed(() =>
  authStore.isAccountant && authStore.activeTenantId === authStore.homeTenantId,
)

// Period state — defaults to previous completed quarter
const currentDate = new Date()
const currentYear = currentDate.getFullYear()
const currentQuarter = Math.ceil((currentDate.getMonth() + 1) / 3)
const prevQuarter = currentQuarter === 1 ? 4 : currentQuarter - 1
const prevQuarterYear = currentQuarter === 1 ? currentYear - 1 : currentYear
const prevQuarterStartMonth = (prevQuarter - 1) * 3 + 1
const periodFrom = ref(`${prevQuarterYear}-${String(prevQuarterStartMonth).padStart(2, '0')}-01`)
const _lastDay = new Date(prevQuarterYear, prevQuarterStartMonth + 2, 0)
const periodTo = ref(
  `${_lastDay.getFullYear()}-${String(_lastDay.getMonth() + 1).padStart(2, '0')}-${String(_lastDay.getDate()).padStart(2, '0')}`,
)

// Debounced aggregation fetch (500ms)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const pendingRefresh = ref(false)
watch([periodFrom, periodTo], ([from, to]) => {
  provenance.invalidate()
  if (debounceTimer) clearTimeout(debounceTimer)
  // Empty or inverted range → skip fetch (user is mid-edit); surface as client error
  if (!from || !to) {
    pendingRefresh.value = false
    return
  }
  if (from > to) {
    pendingRefresh.value = false
    filingStore.error = 'period.invalidRange'
    return
  }
  // Registry empty → onboarding block is showing; aggregation fetch would be discarded.
  if (registryCompleteness.isEmpty.value) {
    pendingRefresh.value = false
    return
  }
  pendingRefresh.value = true
  debounceTimer = setTimeout(() => {
    pendingRefresh.value = false
    filingStore.fetchAggregation(from, to)
  }, 500)
})

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  pendingRefresh.value = false
})

onMounted(async () => {
  filingStore.exportError = null
  if (!needsClientSelection.value && hasAccess.value) {
    await registryCompleteness.refresh()
    if (!registryCompleteness.isEmpty.value) {
      filingStore.fetchAggregation(periodFrom.value, periodTo.value)
    }
  }
})

function onBootstrapCompleted() {
  registryCompleteness.refresh().then(() => {
    if (!registryCompleteness.isEmpty.value) {
      filingStore.fetchAggregation(periodFrom.value, periodTo.value)
    }
  })
}

// Summary card derived values
// Story 10.11 AC #21 — unknown-scope counter surfaced by the aggregation response metadata.
const unknownScopeProductsInPeriod = computed(() => {
  return filingStore.aggregation?.metadata?.unknownScopeProductsInPeriod ?? 0
})

const grandTotalWeight = computed(() => {
  if (!filingStore.aggregation) return null
  return filingStore.grandTotalWeightKg.toFixed(3) + ' kg'
})

const grandTotalFee = computed(() => {
  if (!filingStore.aggregation) return null
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(
    filingStore.grandTotalFeeHuf,
  ) + ' Ft'
})

// Submission History panel
const submissionsPanelCollapsed = ref(true)

function onSubmissionsPanelToggle(collapsed: boolean) {
  submissionsPanelCollapsed.value = collapsed
  if (!collapsed && submissions.rows.value.length === 0 && !submissions.isLoading.value) {
    submissions.fetch(0, 25)
  }
}

// Audit panel
const auditPanelCollapsed = ref(true)

function onAuditPanelToggle(collapsed: boolean) {
  auditPanelCollapsed.value = collapsed
  if (!collapsed && provenance.rows.value.length === 0) {
    provenance.fetch(periodFrom.value, periodTo.value, 0, 50)
  }
}

function onProvenancePage(event: { page: number; rows: number }) {
  provenance.fetch(periodFrom.value, periodTo.value, event.page, event.rows)
}

// Unresolved panel
const unresolvedPanelCollapsed = ref(true)

function unresolvedReasonKey(reason: UnresolvedInvoiceLine['reason']): string {
  return `epr.filing.unresolved.reason.${reason}`
}

function registerProductRoute(vtsz: string, description: string): string {
  return `/registry/new?vtsz=${encodeURIComponent(vtsz)}&name=${encodeURIComponent(description)}`
}

// Export
async function handleExport() {
  try {
    await filingStore.exportOkirkapu(filingStore.period.from, filingStore.period.to)
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

const exportDisabled = computed(() =>
  !filingStore.aggregation
  || filingStore.aggregation.kfTotals.length === 0
  || filingStore.isExporting
  || filingStore.isLoading
  || pendingRefresh.value,
)

// Generic (non-profile) error banner text
const genericErrorMessage = computed(() => {
  const err = filingStore.error
  if (!err || err === 'producer.profile.incomplete') return null
  if (err === 'tier.gate') return t('epr.filing.loadErrorTierGate')
  if (err === 'period.invalidRange') return t('epr.filing.loadErrorInvalidRange')
  return t('epr.filing.loadError', { message: err })
})
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
    </div>

    <!-- Top-level error banner (e.g. producer profile incomplete from aggregation 412) -->
    <div
      v-if="filingStore.error === 'producer.profile.incomplete'"
      class="mb-6 p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3"
      data-testid="aggregation-profile-incomplete-banner"
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

    <!-- Generic error banner (tier gate, invalid period, transport/5xx, etc.) -->
    <div
      v-if="genericErrorMessage"
      class="mb-6 p-3 bg-red-50 border border-red-200 rounded flex items-center gap-3"
      data-testid="aggregation-error-banner"
    >
      <i class="pi pi-times-circle text-red-500" aria-hidden="true" />
      <span class="text-red-800 text-sm">{{ genericErrorMessage }}</span>
    </div>

    <!-- Story 10.11 AC #21 — unknown-scope warning banner with deep-link to Registry filter -->
    <div
      v-if="unknownScopeProductsInPeriod > 0"
      class="mb-6 p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3 cursor-pointer"
      data-testid="filing-banner-unknown-scope"
      role="button"
      tabindex="0"
      @click="router.push('/registry?filter=epr-scope-unknown')"
      @keyup.enter="router.push('/registry?filter=epr-scope-unknown')"
    >
      <i class="pi pi-info-circle text-amber-500" aria-hidden="true" />
      <span class="text-amber-800 text-sm">
        {{ t('epr.filing.banner.unknownScope', { n: unknownScopeProductsInPeriod }) }}
      </span>
    </div>

    <!-- Registry completeness loading skeleton (first load only) -->
    <div
      v-if="registryCompleteness.isLoading.value && registryCompleteness.totalProducts.value === 0"
      class="py-8"
      data-testid="registry-completeness-loading"
    >
      <Skeleton height="200px" />
    </div>

    <!-- Empty Registry onboarding block -->
    <RegistryOnboardingBlock
      v-else-if="registryCompleteness.isEmpty.value && !registryCompleteness.isLoading.value"
      context="filing"
      @bootstrap-completed="onBootstrapCompleted"
    />

    <!-- Period Selector (only when registry is not empty) -->
    <template v-else>

    <!-- Period Selector -->
    <div class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="period-selector">
      <h2 class="text-lg font-semibold text-slate-800 mb-4">
        {{ t('epr.filing.periodSelector.title') }}
      </h2>
      <div class="flex flex-wrap gap-4">
        <div class="flex flex-col gap-1">
          <label for="period-from-input" class="text-sm text-slate-600">{{ t('epr.filing.periodSelector.fromLabel') }}</label>
          <input
            id="period-from-input"
            v-model="periodFrom"
            type="date"
            class="border border-slate-300 rounded px-3 py-2 text-sm"
            data-testid="period-from-input"
          />
        </div>
        <div class="flex flex-col gap-1">
          <label for="period-to-input" class="text-sm text-slate-600">{{ t('epr.filing.periodSelector.toLabel') }}</label>
          <input
            id="period-to-input"
            v-model="periodTo"
            type="date"
            class="border border-slate-300 rounded px-3 py-2 text-sm"
            data-testid="period-to-input"
          />
        </div>
      </div>
    </div>

    <!-- Sold Products Table -->
    <div class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="sold-products-section">
      <h2 class="text-lg font-semibold text-slate-800 mb-4">
        {{ t('epr.filing.soldProducts.title') }}
      </h2>
      <EprSoldProductsTable
        :sold-products="filingStore.aggregation?.soldProducts ?? []"
        :unresolved-lines="filingStore.aggregation?.unresolved ?? []"
        :loading="filingStore.isLoading"
        data-testid="sold-products-table-section"
      />
    </div>

    <!-- KF Totals Table -->
    <div class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="kf-totals-section">
      <h2 class="text-lg font-semibold text-slate-800 mb-4">
        {{ t('epr.filing.kfTotals.title') }}
      </h2>
      <EprKfTotalsTable
        :kf-totals="filingStore.aggregation?.kfTotals ?? []"
        :loading="filingStore.isLoading"
        data-testid="kf-totals-table-section"
      />
    </div>

    <!-- Summary Cards -->
    <div
      class="bg-white border border-slate-200 rounded-lg p-6 mb-6"
      data-testid="filing-summary"
    >
      <h2 class="text-lg font-semibold text-slate-800 mb-4">
        {{ t('epr.filing.summaryTitle') }}
      </h2>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div>
          <p class="text-sm text-slate-500">{{ t('epr.filing.summary.totalKfCodes') }}</p>
          <p class="text-2xl font-bold text-slate-800" data-testid="summary-total-kf-codes">
            <span v-if="filingStore.aggregation !== null">{{ filingStore.totalKfCodes }}</span>
            <span v-else class="text-slate-400">—</span>
          </p>
        </div>
        <div>
          <p class="text-sm text-slate-500">{{ t('epr.filing.summary.grandTotalWeight') }}</p>
          <p class="text-2xl font-bold text-slate-800" data-testid="summary-total-weight">
            <span v-if="grandTotalWeight !== null">{{ grandTotalWeight }}</span>
            <span v-else class="text-slate-400">—</span>
          </p>
        </div>
        <div>
          <p class="text-sm text-slate-500">{{ t('epr.filing.summary.grandTotalFee') }}</p>
          <p class="text-2xl font-bold text-emerald-700" data-testid="summary-total-fee">
            <span v-if="grandTotalFee !== null">{{ grandTotalFee }}</span>
            <span v-else class="text-slate-400">—</span>
          </p>
        </div>
      </div>
    </div>

    <!-- Unresolved Panel -->
    <div
      v-if="filingStore.aggregation && filingStore.aggregation.unresolved.length > 0"
      class="mb-6"
      data-testid="unresolved-panel-wrapper"
    >
      <Panel
        :header="t('epr.filing.unresolved.panelTitle', { count: filingStore.aggregation.unresolved.length })"
        :collapsed="unresolvedPanelCollapsed"
        toggleable
        data-testid="unresolved-panel"
      >
        <DataTable
          :value="filingStore.aggregation.unresolved"
          data-testid="unresolved-table"
        >
          <Column field="invoiceNumber" :header="t('epr.filing.unresolved.columns.invoiceNumber')" />
          <Column field="lineNumber" :header="t('epr.filing.unresolved.columns.lineNumber')" />
          <Column field="vtsz" :header="t('epr.filing.unresolved.columns.vtsz')" />
          <Column field="description" :header="t('epr.filing.unresolved.columns.description')" />
          <Column field="quantity" :header="t('epr.filing.unresolved.columns.quantity')" />
          <Column field="unitOfMeasure" :header="t('epr.filing.unresolved.columns.unit')" />
          <Column :header="t('epr.filing.unresolved.columns.reason')">
            <template #body="{ data: row }">
              <span>{{ t(unresolvedReasonKey(row.reason)) }}</span>
              <span
                v-if="row.reason === 'UNSUPPORTED_UNIT_OF_MEASURE'"
                class="ml-1 text-slate-500 text-xs"
                :title="t('epr.filing.unresolved.unsupportedUnitTooltip')"
              >ℹ</span>
              <span
                v-if="row.reason === 'VTSZ_FALLBACK'"
                class="ml-1 text-slate-500 text-xs block"
              >{{ t('epr.filing.unresolved.vtszFallbackNote') }}</span>
            </template>
          </Column>
          <Column :header="t('epr.filing.unresolved.columns.action')">
            <template #body="{ data: row }">
              <Button
                v-if="row.reason === 'NO_MATCHING_PRODUCT' || row.reason === 'ZERO_COMPONENTS'"
                :label="t('epr.filing.unresolved.registerProduct')"
                size="small"
                severity="secondary"
                outlined
                @click="router.push(registerProductRoute(row.vtsz, row.description))"
              />
            </template>
          </Column>
        </DataTable>
      </Panel>
    </div>

    <!-- OKIRkapu Export -->
    <div class="bg-white border border-slate-200 rounded-lg p-6 mb-6" data-testid="okirkapu-export-panel">
      <h2 class="text-lg font-semibold text-slate-800 mb-4">
        {{ t('epr.okirkapu.exportTitle') }}
      </h2>
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
          :label="filingStore.isExporting ? t('epr.okirkapu.exportGenerating') : t('epr.okirkapu.exportButton')"
          icon="pi pi-file-export"
          :disabled="exportDisabled"
          :loading="filingStore.isExporting"
          data-testid="export-okirkapu-button"
          @click="handleExport"
        />
      </div>
    </div>

    <!-- Audit / Provenance Panel -->
    <div class="mb-6" data-testid="audit-panel-wrapper">
      <Panel
        :header="t('epr.filing.audit.panelTitle')"
        :collapsed="auditPanelCollapsed"
        toggleable
        data-testid="audit-panel"
        @update:collapsed="onAuditPanelToggle"
      >
        <template #icons>
          <Button
            icon="pi pi-file-export"
            :aria-label="t('epr.filing.audit.exportCsv')"
            :title="t('epr.filing.audit.exportCsv')"
            text
            rounded
            :loading="provenance.isCsvExporting.value"
            data-testid="audit-csv-export"
            @click="provenance.exportCsv(periodFrom, periodTo)"
          />
        </template>
        <EprProvenanceTable
          :rows="provenance.rows.value"
          :total-elements="provenance.totalElements.value"
          :is-loading="provenance.isLoading.value"
          :period="{ from: periodFrom, to: periodTo }"
          data-testid="provenance-table-section"
          @page="onProvenancePage"
        />
      </Panel>
    </div>

    <!-- Submission History Panel -->
    <div class="mb-6" data-testid="submissions-panel-wrapper">
      <Panel
        v-if="!registryCompleteness.isEmpty.value"
        :header="t('epr.submissions.panelTitle')"
        :collapsed="submissionsPanelCollapsed"
        toggleable
        data-testid="submissions-panel"
        @update:collapsed="onSubmissionsPanelToggle"
      >
        <EprSubmissionsTable
          :rows="submissions.rows.value"
          :total-elements="submissions.totalElements.value"
          :is-loading="submissions.isLoading.value"
          @page="({ page, rows }) => submissions.fetch(page, rows)"
          @download="({ id, fileName }) => submissions.downloadXml(id, fileName)"
        />
      </Panel>
    </div>

    </template>
  </div>
</template>
