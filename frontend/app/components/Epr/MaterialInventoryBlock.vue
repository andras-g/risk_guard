<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import InputText from 'primevue/inputtext'
import InputSwitch from 'primevue/inputswitch'
import Select from 'primevue/select'
import { FilterMatchMode } from '@primevue/core/api'
import type { MaterialTemplateResponse } from '~/types/epr'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const { t } = useI18n()
const { formatRelative } = useDateRelative()

function filingReadyTooltip(data: MaterialTemplateResponse): string {
  const parts = [formatKfCode(data.kfCode)]
  if (data.confidence) parts.push(t(`epr.wizard.confidence.${data.confidence.toLowerCase()}`))
  if (data.feeRate != null) parts.push(t('epr.materialLibrary.filingReadyTooltip', { feeRate: data.feeRate }))
  return parts.join(' · ')
}

function formatKfCode(code: string | null): string {
  if (!code || code.length !== 8) return code || ''
  return `${code.slice(0, 2)} ${code.slice(2, 4)} ${code.slice(4, 6)} ${code.slice(6, 8)}`
}

const props = defineProps<{
  entries: MaterialTemplateResponse[]
  isLoading: boolean
}>()

const emit = defineEmits<{
  edit: [entry: MaterialTemplateResponse]
  delete: [entry: MaterialTemplateResponse]
  toggleRecurring: [entry: MaterialTemplateResponse, recurring: boolean]
  classify: [entry: MaterialTemplateResponse]
}>()

// Client-side global filter
const filters = ref({
  global: { value: null as string | null, matchMode: FilterMatchMode.CONTAINS },
})

// Recurring filter: 'all' | 'recurring' | 'one-time'
const recurringFilter = ref<string>('all')

const recurringFilterOptions = computed(() => [
  { label: t('epr.materialLibrary.filter.all'), value: 'all' },
  { label: t('epr.materialLibrary.filter.recurringOnly'), value: 'recurring' },
  { label: t('epr.materialLibrary.filter.oneTimeOnly'), value: 'one-time' },
])

const filteredEntries = computed(() => {
  if (recurringFilter.value === 'all') return props.entries
  if (recurringFilter.value === 'recurring') return props.entries.filter(e => e.recurring)
  return props.entries.filter(e => !e.recurring)
})
</script>

<template>
  <!-- Empty State -->
  <div
    v-if="!isLoading && entries.length === 0"
    class="flex flex-col items-center justify-center py-16 text-center"
    data-testid="epr-empty-state"
  >
    <i class="pi pi-box text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h3 class="text-lg font-semibold text-slate-700 mb-2">
      {{ t('epr.materialLibrary.empty') }}
    </h3>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.emptyDescription') }}
    </p>
  </div>

  <!-- Loading Skeleton -->
  <div
    v-else-if="isLoading"
    data-testid="epr-skeleton"
  >
    <div
      v-for="i in 5"
      :key="i"
      class="flex gap-4 p-4 border-b border-slate-100"
    >
      <Skeleton width="25%" height="1.25rem" />
      <Skeleton width="15%" height="1.25rem" />
      <Skeleton width="10%" height="1.25rem" />
      <Skeleton width="10%" height="1.25rem" />
      <Skeleton width="10%" height="1.25rem" />
      <Skeleton width="15%" height="1.25rem" />
      <Skeleton width="15%" height="1.25rem" />
    </div>
  </div>

  <!-- DataTable -->
  <DataTable
    v-else
    v-model:filters="filters"
    :value="filteredEntries"
    :global-filter-fields="['name']"
    :rows="20"
    :paginator="filteredEntries.length > 20"
    :row-class="(data: MaterialTemplateResponse) => data.verified && data.confidence === 'LOW' ? 'border-l-4 border-amber-400' : ''"
    striped-rows
    table-style="width: 100%"
    data-testid="epr-materials-table"
  >
    <template #header>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <Select
          v-model="recurringFilter"
          :options="recurringFilterOptions"
          option-label="label"
          option-value="value"
          class="w-48"
          data-testid="recurring-filter"
        />
        <InputText
          v-model="filters['global'].value"
          :placeholder="t('epr.materialLibrary.searchPlaceholder')"
          class="w-64"
          data-testid="epr-search-input"
        />
      </div>
    </template>

    <Column
      field="name"
      :header="t('epr.materialLibrary.columns.name')"
      sortable
    >
      <template #body="{ data }">
        <span class="font-medium text-slate-800">{{ data.name }}</span>
      </template>
    </Column>

    <Column
      field="baseWeightGrams"
      :header="t('epr.materialLibrary.columns.baseWeight')"
      sortable
    >
      <template #body="{ data }">
        <span class="font-mono text-slate-700">{{ data.baseWeightGrams }} g</span>
      </template>
    </Column>

    <Column
      field="kfCode"
      :header="t('epr.materialLibrary.columns.kfCode')"
      sortable
    >
      <template #body="{ data }">
        <span class="text-slate-600">{{ data.kfCode || '—' }}</span>
      </template>
    </Column>

    <Column
      field="verified"
      :header="t('epr.materialLibrary.columns.verified')"
      sortable
    >
      <template #body="{ data }">
        <span class="inline-flex items-center gap-1">
          <span
            :class="[
              'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium',
              data.verified ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
            ]"
            v-tooltip="data.verified ? { value: filingReadyTooltip(data), escape: true } : undefined"
          >
            <i v-if="data.verified" class="pi pi-check-circle text-xs" />
            {{ data.verified ? t('epr.materialLibrary.filingReady') : t('epr.materialLibrary.unverified') }}
          </span>
          <EprConfidenceBadge
            v-if="data.verified && data.confidence"
            :confidence="data.confidence"
            data-testid="confidence-badge-in-table"
          />
          <span
            v-if="data.overrideKfCode"
            v-tooltip="{ value: t('epr.wizard.override.tooltip') + ': ' + data.overrideKfCode + (data.overrideReason ? ' — ' + data.overrideReason : ''), escape: true }"
            class="inline-flex items-center text-slate-500 cursor-help"
            data-testid="override-indicator"
          >
            <i class="pi pi-pencil text-xs" />
          </span>
        </span>
      </template>
    </Column>

    <Column
      field="recurring"
      :header="t('epr.materialLibrary.columns.recurring')"
      sortable
    >
      <template #body="{ data }">
        <div class="flex items-center gap-2">
          <InputSwitch
            :model-value="data.recurring"
            data-testid="recurring-toggle"
            @update:model-value="emit('toggleRecurring', data, $event as boolean)"
          />
          <span
            v-if="!data.recurring"
            class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800"
          >
            {{ t('epr.materialLibrary.oneTimeBadge') }}
          </span>
        </div>
      </template>
    </Column>

    <Column
      field="createdAt"
      :header="t('epr.materialLibrary.columns.created')"
      sortable
    >
      <template #body="{ data }">
        <span class="text-sm text-slate-500">
          {{ formatRelative(data.createdAt) }}
        </span>
      </template>
    </Column>

    <Column :header="t('epr.materialLibrary.columns.actions')">
      <template #body="{ data }">
        <div class="flex gap-1">
          <Button
            v-tooltip.top="data.verified ? t('epr.wizard.reclassify') : t('epr.wizard.classify')"
            icon="pi pi-sitemap"
            :severity="data.verified ? undefined : 'secondary'"
            :text="data.verified"
            size="small"
            rounded
            data-testid="classify-template-button"
            @click="emit('classify', data)"
          />
          <Button
            v-tooltip.top="t('epr.materialLibrary.editButton')"
            icon="pi pi-pencil"
            severity="secondary"
            text
            size="small"
            rounded
            data-testid="edit-template-button"
            @click="emit('edit', data)"
          />
          <Button
            v-tooltip.top="t('epr.materialLibrary.deleteButton')"
            icon="pi pi-trash"
            severity="danger"
            text
            size="small"
            rounded
            data-testid="delete-template-button"
            @click="emit('delete', data)"
          />
        </div>
      </template>
    </Column>
  </DataTable>
</template>
