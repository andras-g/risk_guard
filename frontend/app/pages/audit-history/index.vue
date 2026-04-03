<script setup lang="ts">
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import type { AuditHistoryEntryResponse } from '~/types/api'
import { useAuditHistory } from '~/composables/useAuditHistory'
import { useStatusColor } from '~/composables/formatting/useStatusColor'

const { t } = useI18n()
const toast = useToast()
const { statusColorClass } = useStatusColor()

const audit = useAuditHistory()
const expandedRows = ref<Record<string, boolean>>({})
const verifyResults = ref<Record<string, { match: boolean, computedHash: string, storedHash: string, unavailable?: boolean } | null>>({})
const verifyingIds = ref<Set<string>>(new Set())

const checkSourceOptions = [
  { label: t('screening.audit.filters.sourceAll'), value: null },
  { label: t('screening.audit.source.MANUAL'), value: 'MANUAL' },
  { label: t('screening.audit.source.AUTOMATED'), value: 'AUTOMATED' },
]

const selectedSource = ref<{ label: string, value: string | null }>(checkSourceOptions[0])
const dateRange = ref<Date[] | null>(null)

onMounted(async () => {
  await audit.fetchPage()
})

async function onPage(event: { page: number, rows: number }) {
  audit.page.value = event.page
  audit.pageSize.value = event.rows
  await audit.fetchPage()
}

async function onSort() {
  audit.page.value = 0
  await audit.fetchPage()
}

async function applyFilters() {
  audit.page.value = 0
  if (dateRange.value && dateRange.value.length === 2 && dateRange.value[0] && dateRange.value[1]) {
    audit.setFilter('startDate', formatDate(dateRange.value[0]))
    audit.setFilter('endDate', formatDate(dateRange.value[1]))
  }
  else {
    audit.setFilter('startDate', null)
    audit.setFilter('endDate', null)
  }
  audit.setFilter('checkSource', selectedSource.value.value as 'MANUAL' | 'AUTOMATED' | null)
  await audit.fetchPage()
}

async function clearFilters() {
  dateRange.value = null
  selectedSource.value = checkSourceOptions[0]
  audit.resetFilters()
  await audit.fetchPage()
}

function formatDate(d: Date): string {
  return d.toISOString().split('T')[0]
}

function truncateHash(hash: string): string {
  if (!hash || hash === 'HASH_UNAVAILABLE') return hash
  return hash.substring(0, 12) + '…'
}

async function copyHash(hash: string) {
  try {
    await navigator.clipboard.writeText(hash)
    toast.add({ severity: 'success', summary: t('screening.audit.expand.copyHashSuccess'), life: 2000 })
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 2000 })
  }
}

async function onVerifyHash(entry: AuditHistoryEntryResponse) {
  if (verifyingIds.value.has(entry.id)) return
  verifyingIds.value = new Set([...verifyingIds.value, entry.id])
  verifyResults.value[entry.id] = null
  try {
    const result = await audit.verifyHash(entry.id)
    verifyResults.value[entry.id] = result
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
  }
  finally {
    const next = new Set(verifyingIds.value)
    next.delete(entry.id)
    verifyingIds.value = next
  }
}

function verdictSeverity(status: string | null): 'success' | 'warning' | 'danger' | 'secondary' {
  switch (status) {
    case 'RELIABLE': return 'success'
    case 'AT_RISK':
    case 'TAX_SUSPENDED': return 'danger'
    case 'INCOMPLETE':
    case 'UNAVAILABLE': return 'warning'
    default: return 'secondary'
  }
}
</script>

<template>
  <div class="max-w-7xl mx-auto p-6">
    <!-- Page Header -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-slate-800">
        {{ t('screening.audit.title') }}
      </h1>
    </div>

    <!-- Filter Panel -->
    <div class="bg-white rounded-lg border border-slate-200 p-4 mb-4 flex flex-wrap gap-4 items-end">
      <!-- Date Range -->
      <div class="flex flex-col gap-1">
        <label for="audit-date-range" class="text-xs font-medium text-slate-500">{{ t('screening.audit.filters.dateRange') }}</label>
        <DatePicker
          v-model="dateRange"
          input-id="audit-date-range"
          selection-mode="range"
          date-format="yy-mm-dd"
          :placeholder="t('screening.audit.filters.dateRangePlaceholder')"
          show-button-bar
          class="w-60"
        />
      </div>

      <!-- Partner filter -->
      <div class="flex flex-col gap-1">
        <label for="audit-tax-number" class="text-xs font-medium text-slate-500">{{ t('screening.audit.filters.taxNumber') }}</label>
        <InputText
          id="audit-tax-number"
          :model-value="audit.filters.value.taxNumber"
          :placeholder="t('screening.audit.filters.taxNumberPlaceholder')"
          class="w-44"
          @update:model-value="audit.setFilter('taxNumber', $event)"
        />
      </div>

      <!-- Check Source -->
      <div class="flex flex-col gap-1">
        <span class="text-xs font-medium text-slate-500">{{ t('screening.audit.filters.checkSource') }}</span>
        <SelectButton
          v-model="selectedSource"
          :options="checkSourceOptions"
          option-label="label"
          aria-label="Check source filter"
        />
      </div>

      <!-- Actions -->
      <div class="flex gap-2 ml-auto">
        <Button
          :label="t('screening.audit.filters.apply')"
          icon="pi pi-filter"
          size="small"
          @click="applyFilters"
        />
        <Button
          :label="t('screening.audit.filters.clear')"
          icon="pi pi-filter-slash"
          severity="secondary"
          outlined
          size="small"
          @click="clearFilters"
        />
      </div>
    </div>

    <!-- Audit Table -->
    <DataTable
      :value="audit.entries.value"
      :loading="audit.loading.value"
      :total-records="audit.totalElements.value"
      :rows="audit.pageSize.value"
      :first="audit.page.value * audit.pageSize.value"
      lazy
      paginator
      :rows-per-page-options="[10, 20, 50]"
      v-model:expanded-rows="expandedRows"
      data-key="id"
      sort-field="searchedAt"
      :sort-order="-1"
      class="w-full"
      @page="onPage"
      @sort="onSort"
    >
      <!-- Empty state -->
      <template #empty>
        <div class="flex flex-col items-center justify-center py-16 text-center">
          <i class="pi pi-clock text-5xl text-slate-300 mb-4" aria-hidden="true" />
          <p class="text-slate-500 text-sm">
            {{ t('screening.audit.empty') }}
          </p>
        </div>
      </template>

      <!-- Expand toggle -->
      <Column expander style="width: 3rem" />

      <!-- Company Name -->
      <Column
        :header="t('screening.audit.columns.companyName')"
        field="companyName"
        style="min-width: 10rem"
      >
        <template #body="{ data }">
          <span class="font-medium text-slate-800">{{ data.companyName ?? data.taxNumber }}</span>
        </template>
      </Column>

      <!-- Tax Number -->
      <Column
        :header="t('screening.audit.columns.taxNumber')"
        field="taxNumber"
        style="min-width: 8rem"
      >
        <template #body="{ data }">
          <span class="font-mono text-sm">{{ data.taxNumber }}</span>
        </template>
      </Column>

      <!-- Verdict Status -->
      <Column
        :header="t('screening.audit.columns.verdict')"
        field="verdictStatus"
        style="min-width: 8rem"
      >
        <template #body="{ data }">
          <Tag
            v-if="data.verdictStatus"
            :severity="verdictSeverity(data.verdictStatus)"
            :value="t(`screening.verdict.${data.verdictStatus === 'AT_RISK' ? 'atRisk' : data.verdictStatus === 'TAX_SUSPENDED' ? 'taxSuspended' : data.verdictStatus.toLowerCase()}`)"
          />
          <span v-else class="text-slate-400 text-sm">—</span>
        </template>
      </Column>

      <!-- Searched At -->
      <Column
        :header="t('screening.audit.columns.searchedAt')"
        field="searchedAt"
        sortable
        style="min-width: 10rem"
      >
        <template #body="{ data }">
          <span class="text-sm text-slate-600">{{ new Date(data.searchedAt).toLocaleString() }}</span>
        </template>
      </Column>

      <!-- SHA-256 Hash (truncated) -->
      <Column
        :header="t('screening.audit.columns.hash')"
        field="sha256Hash"
        style="min-width: 9rem"
      >
        <template #body="{ data }">
          <span class="font-mono text-xs text-slate-500">{{ truncateHash(data.sha256Hash) }}</span>
        </template>
      </Column>

      <!-- Mode Badge -->
      <Column
        :header="t('screening.audit.columns.mode')"
        field="dataSourceMode"
        style="min-width: 6rem"
      >
        <template #body="{ data }">
          <Tag
            :severity="data.dataSourceMode === 'LIVE' ? 'success' : 'secondary'"
            :value="t(`screening.audit.mode.${data.dataSourceMode}`)"
          />
        </template>
      </Column>

      <!-- Source Badge -->
      <Column
        :header="t('screening.audit.columns.source')"
        field="checkSource"
        style="min-width: 7rem"
      >
        <template #body="{ data }">
          <Tag
            :severity="data.checkSource === 'AUTOMATED' ? 'info' : 'secondary'"
            :value="t(`screening.audit.source.${data.checkSource}`)"
          />
        </template>
      </Column>

      <!-- Row expansion -->
      <template #expansion="{ data }">
        <div class="bg-slate-50 rounded p-4 space-y-3">
          <!-- Full Hash + Copy -->
          <div class="flex items-start gap-3">
            <div class="flex-1">
              <p class="text-xs font-semibold text-slate-500 mb-1">
                {{ t('screening.audit.expand.fullHash') }}
              </p>
              <code class="text-xs font-mono break-all text-slate-700">{{ data.sha256Hash }}</code>
            </div>
            <Button
              icon="pi pi-copy"
              size="small"
              severity="secondary"
              outlined
              :aria-label="t('screening.audit.expand.copyHash')"
              @click="copyHash(data.sha256Hash)"
            />
          </div>

          <!-- Source URLs -->
          <div v-if="data.sourceUrls && data.sourceUrls.length > 0">
            <p class="text-xs font-semibold text-slate-500 mb-1">
              {{ t('screening.audit.expand.sourceUrls') }}
            </p>
            <ul class="space-y-1">
              <li v-for="url in data.sourceUrls" :key="url">
                <a
                  :href="url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-xs text-blue-600 hover:underline break-all"
                >{{ url }}</a>
              </li>
            </ul>
          </div>

          <!-- Confidence -->
          <div v-if="data.verdictConfidence">
            <p class="text-xs font-semibold text-slate-500 mb-1">
              {{ t('screening.audit.expand.confidence') }}
            </p>
            <Tag
              :severity="data.verdictConfidence === 'FRESH' ? 'success' : data.verdictConfidence === 'STALE' ? 'warning' : 'secondary'"
              :value="t(`screening.freshness.${data.verdictConfidence.toLowerCase()}`)"
            />
          </div>

          <!-- Disclaimer -->
          <div v-if="data.disclaimerText">
            <p class="text-xs font-semibold text-slate-500 mb-1">
              {{ t('screening.audit.expand.disclaimer') }}
            </p>
            <p class="text-xs text-slate-600">
              {{ data.disclaimerText }}
            </p>
          </div>

          <!-- Verify Hash -->
          <div class="flex items-center gap-3">
            <Button
              :label="t('screening.audit.expand.verifyHash')"
              icon="pi pi-shield"
              size="small"
              severity="secondary"
              :loading="verifyingIds.has(data.id)"
              :disabled="verifyingIds.has(data.id)"
              @click="onVerifyHash(data)"
            />
            <span
              v-if="verifyResults[data.id] !== undefined && verifyResults[data.id] !== null"
              class="text-sm font-medium flex items-center gap-1"
            >
              <template v-if="verifyResults[data.id]?.unavailable">
                <i class="pi pi-exclamation-circle text-yellow-600" />
                <span class="text-yellow-700">{{ t('screening.audit.verify.unavailable') }}</span>
              </template>
              <template v-else-if="verifyResults[data.id]?.match">
                <i class="pi pi-check-circle text-green-600" />
                <span class="text-green-700">{{ t('screening.audit.verify.match') }}</span>
              </template>
              <template v-else>
                <i class="pi pi-times-circle text-red-600" />
                <span class="text-red-700">{{ t('screening.audit.verify.mismatch') }}</span>
              </template>
            </span>
          </div>
        </div>
      </template>
    </DataTable>
  </div>
</template>
