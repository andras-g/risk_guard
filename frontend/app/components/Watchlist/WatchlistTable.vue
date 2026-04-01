<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import InputText from 'primevue/inputtext'
import { FilterMatchMode } from '@primevue/core/api'
import type { DataTableRowClickEvent } from 'primevue/datatable'
import type { WatchlistEntryResponse } from '~/types/api'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const { t } = useI18n()
const { formatRelative } = useDateRelative()

const props = withDefaults(defineProps<{
  entries: WatchlistEntryResponse[]
  isLoading: boolean
  selection: WatchlistEntryResponse[]
}>(), {
  selection: () => [],
})

const emit = defineEmits<{
  remove: [entry: WatchlistEntryResponse]
  'row-select': [entry: WatchlistEntryResponse]
  'update:selection': [entries: WatchlistEntryResponse[]]
}>()

function onRowClick(event: DataTableRowClickEvent) {
  const target = event.originalEvent.target as HTMLElement
  if (target.closest('.p-checkbox') || target.closest('[data-testid="remove-entry-button"]')) return
  emit('row-select', event.data as WatchlistEntryResponse)
}

const internalSelection = computed({
  get: () => props.selection,
  set: (v) => emit('update:selection', v),
})

// Client-side global filter
const filters = ref({
  global: { value: null as string | null, matchMode: FilterMatchMode.CONTAINS },
})

// Verdict status badge styling — matches VerdictCard pattern
function verdictBadgeClass(status: string | null): string {
  switch (status) {
    case 'RELIABLE': return 'bg-emerald-100 text-emerald-800'
    case 'AT_RISK': return 'bg-rose-100 text-rose-800'
    case 'TAX_SUSPENDED': return 'bg-amber-100 text-amber-800'
    case 'INCOMPLETE':
    case 'UNAVAILABLE':
    default: return 'bg-slate-100 text-slate-600'
  }
}

function verdictLabel(status: string | null): string {
  if (!status) return '—'
  switch (status) {
    case 'RELIABLE': return t('screening.verdict.reliable')
    case 'AT_RISK': return t('screening.verdict.atRisk')
    case 'TAX_SUSPENDED': return t('screening.verdict.taxSuspended')
    case 'INCOMPLETE': return t('screening.verdict.incomplete')
    case 'UNAVAILABLE': return t('screening.verdict.unavailable')
    default: return status
  }
}

const STATUS_SEVERITY: Record<string, number> = {
  RELIABLE: 0, INCOMPLETE: 1, UNAVAILABLE: 2, TAX_SUSPENDED: 3, AT_RISK: 4,
}

function trendDirection(current: string | null, previous: string | null): 'improved' | 'worsened' | 'stable' | null {
  if (!current || !previous) return null
  const c = STATUS_SEVERITY[current] ?? 99
  const p = STATUS_SEVERITY[previous] ?? 99
  if (c < p) return 'improved'
  if (c > p) return 'worsened'
  return 'stable'
}
</script>

<template>
  <!-- Empty State -->
  <div
    v-if="!isLoading && entries.length === 0"
    class="flex flex-col items-center justify-center py-16 text-center"
    data-testid="watchlist-empty-state"
  >
    <i class="pi pi-eye text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h3 class="text-lg font-semibold text-slate-700 mb-2">
      {{ t('notification.watchlist.emptyTitle') }}
    </h3>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('notification.watchlist.emptyDescription') }}
    </p>
    <NuxtLink
      to="/dashboard"
      class="text-indigo-600 hover:text-indigo-800 font-medium text-sm"
      data-testid="empty-cta-link"
    >
      {{ t('notification.watchlist.emptyCta') }}
    </NuxtLink>
  </div>

  <!-- Loading Skeleton -->
  <div
    v-else-if="isLoading"
    data-testid="watchlist-skeleton"
  >
    <div
      v-for="i in 5"
      :key="i"
      class="flex gap-4 p-4 border-b border-slate-100"
    >
      <Skeleton width="30%" height="1.25rem" />
      <Skeleton width="15%" height="1.25rem" />
      <Skeleton width="15%" height="1.25rem" />
      <Skeleton width="20%" height="1.25rem" />
      <Skeleton width="10%" height="1.25rem" />
    </div>
  </div>

  <!-- DataTable -->
  <DataTable
    v-else
    v-model:selection="internalSelection"
    v-model:filters="filters"
    :value="entries"
    :global-filter-fields="['companyName', 'taxNumber']"
    :rows="20"
    :paginator="entries.length > 20"
    selection-mode="multiple"
    striped-rows
    data-testid="watchlist-table"
    @row-click="onRowClick"
  >
    <template #header>
      <div class="flex justify-end">
        <InputText
          v-model="filters['global'].value"
          :placeholder="t('notification.watchlist.searchPlaceholder')"
          class="w-64"
          data-testid="watchlist-search-input"
        />
      </div>
    </template>

    <Column selection-mode="multiple" style="width: 3rem" />

    <Column
      field="companyName"
      :header="t('notification.watchlist.columns.companyName')"
      sortable
    >
      <template #body="{ data }">
        <span class="font-medium text-slate-800">{{ data.companyName || '—' }}</span>
      </template>
    </Column>

    <Column
      field="taxNumber"
      :header="t('notification.watchlist.columns.taxNumber')"
      sortable
    >
      <template #body="{ data }">
        <span class="font-mono text-slate-700">{{ data.taxNumber }}</span>
      </template>
    </Column>

    <Column
      field="currentVerdictStatus"
      :header="t('notification.watchlist.columns.verdictStatus')"
      sortable
    >
      <template #body="{ data }">
        <span
          :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', verdictBadgeClass(data.currentVerdictStatus)]"
          data-testid="verdict-badge"
        >
          {{ verdictLabel(data.currentVerdictStatus) }}
        </span>
      </template>
    </Column>

    <Column
      field="lastCheckedAt"
      :header="t('notification.watchlist.columns.lastScreened')"
      sortable
    >
      <template #body="{ data }">
        <span class="text-sm text-slate-500">
          {{ data.lastCheckedAt ? formatRelative(data.lastCheckedAt) : '—' }}
        </span>
      </template>
    </Column>

    <Column :header="t('notification.watchlist.columns.trend')">
      <template #body="{ data }">
        <template v-if="data.previousVerdictStatus === null || data.previousVerdictStatus === undefined">
          <span class="text-sm text-slate-400">—</span>
        </template>
        <template v-else-if="trendDirection(data.currentVerdictStatus, data.previousVerdictStatus) === 'improved'">
          <i class="pi pi-arrow-up text-emerald-600" aria-label="improved" />
        </template>
        <template v-else-if="trendDirection(data.currentVerdictStatus, data.previousVerdictStatus) === 'worsened'">
          <i class="pi pi-arrow-down text-rose-600" aria-label="worsened" />
        </template>
        <template v-else>
          <i class="pi pi-minus text-slate-400" aria-label="stable" />
        </template>
      </template>
    </Column>

    <Column :header="t('notification.watchlist.columns.actions')">
      <template #body="{ data }">
        <Button
          :label="t('notification.watchlist.removeButton')"
          icon="pi pi-trash"
          severity="danger"
          text
          size="small"
          data-testid="remove-entry-button"
          @click="emit('remove', data)"
        />
      </template>
    </Column>
  </DataTable>
</template>
