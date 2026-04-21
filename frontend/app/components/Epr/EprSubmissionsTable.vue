<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import type { EprSubmissionSummary } from '~/types/epr'

const props = defineProps<{
  rows: EprSubmissionSummary[]
  totalElements: number
  isLoading: boolean
}>()

const emit = defineEmits<{
  page: [{ page: number; rows: number }]
  download: [{ id: string; fileName: string | null }]
}>()

const { t } = useI18n()
const { formatRelative } = useDateRelative()

const pageSize = ref(25)
const currentPage = ref(0)

function onPage(event: { page: number; rows: number }) {
  currentPage.value = event.page
  pageSize.value = event.rows
  emit('page', event)
}

function formatPeriod(start: string, end: string): string {
  return `${start} / ${end}`
}

function formatSubmittedAt(dateStr: string): string {
  return formatRelative(dateStr)
}

function absoluteIso(dateStr: string): string {
  const parsed = new Date(dateStr)
  return isNaN(parsed.getTime()) ? dateStr : parsed.toISOString()
}

function formatFee(value: number | null): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'
}

function formatWeight(value: number | null): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return value.toFixed(3) + ' kg'
}
</script>

<template>
  <div>
    <!-- Loading skeleton -->
    <div v-if="isLoading" class="space-y-2" data-testid="submissions-skeleton">
      <div v-for="i in 4" :key="i" class="flex gap-3">
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="12%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="20%" height="1.25rem" />
        <Skeleton width="6rem" height="1.25rem" />
      </div>
    </div>

    <!-- Data table -->
    <DataTable
      v-else
      :value="rows"
      :lazy="true"
      :paginator="true"
      :rows="pageSize"
      :total-records="totalElements"
      :first="currentPage * pageSize"
      data-testid="submissions-table"
      @page="onPage"
    >
      <template #empty>
        <span class="text-slate-400 text-sm">{{ t('epr.submissions.emptyMessage') }}</span>
      </template>

      <Column :header="t('epr.submissions.columns.period')">
        <template #body="{ data }">
          {{ formatPeriod(data.periodStart, data.periodEnd) }}
        </template>
      </Column>

      <Column :header="t('epr.submissions.columns.submittedAt')">
        <template #body="{ data }">
          <span :title="absoluteIso(data.exportedAt)">{{ formatSubmittedAt(data.exportedAt) }}</span>
        </template>
      </Column>

      <Column :header="t('epr.submissions.columns.totalFee')">
        <template #body="{ data }">
          {{ formatFee(data.totalFeeHuf) }}
        </template>
      </Column>

      <Column :header="t('epr.submissions.columns.totalWeight')">
        <template #body="{ data }">
          {{ formatWeight(data.totalWeightKg) }}
        </template>
      </Column>

      <Column :header="t('epr.submissions.columns.submittedBy')">
        <template #body="{ data }">
          <span v-if="data.submittedByUserEmail">{{ data.submittedByUserEmail }}</span>
          <span v-else class="text-muted italic">{{ t('epr.submissions.deletedUser') }}</span>
        </template>
      </Column>

      <Column :header="t('epr.submissions.columns.download')" style="width: 6rem">
        <template #body="{ data }">
          <Button
            icon="pi pi-download"
            text
            rounded
            :disabled="!data.hasXmlContent"
            :aria-label="t('epr.submissions.columns.download')"
            :title="data.hasXmlContent ? '' : t('epr.submissions.downloadUnavailable')"
            :data-testid="`submission-download-${data.id}`"
            @click="emit('download', { id: data.id, fileName: data.fileName })"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>
