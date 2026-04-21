<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Skeleton from 'primevue/skeleton'
import type { KfCodeTotal } from '~/types/epr'

defineProps<{
  kfTotals: KfCodeTotal[]
  loading: boolean
}>()

const { t } = useI18n()

function formatKfCode(code: string | null | undefined): string {
  if (!code) return '—'
  if (code.length !== 8) return code
  return code.replace(/(.{2})/g, '$1 ').trim()
}

function formatWeight(value: number): string {
  return value.toFixed(3) + ' kg'
}

function formatHuf(value: number): string {
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' Ft'
}

function formatFeeRate(value: number): string {
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 0 }).format(value)
}

defineExpose({ formatKfCode, formatWeight, formatHuf })
</script>

<template>
  <div>
    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-2" data-testid="kf-totals-skeleton">
      <div v-for="i in 5" :key="i" class="flex gap-4">
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="25%" height="1.25rem" />
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
      </div>
    </div>

    <!-- Data table -->
    <DataTable
      v-else
      :value="kfTotals"
      sort-field="totalFeeHuf"
      :sort-order="-1"
      data-testid="kf-totals-table"
    >
      <Column :header="t('epr.filing.kfTotals.columns.kfCode')" sortable>
        <template #body="{ data: row }">
          <span>{{ formatKfCode(row.kfCode) }}</span>
          <i
            v-if="row.hasFallback"
            class="pi pi-exclamation-triangle text-yellow-500 ml-1"
            aria-hidden="true"
            :title="t('epr.filing.kfTotals.fallbackTooltip')"
            data-testid="fallback-icon"
          />
          <i
            v-if="row.hasOverflowWarning"
            class="pi pi-exclamation-circle text-orange-500 ml-1"
            aria-hidden="true"
            :title="t('epr.filing.kfTotals.overflowTooltip')"
            data-testid="overflow-icon"
          />
        </template>
      </Column>
      <Column :header="t('epr.filing.kfTotals.columns.classification')">
        <template #body="{ data: row }">
          <span v-if="row.classificationLabel">{{ row.classificationLabel }}</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </Column>
      <Column field="totalWeightKg" :header="t('epr.filing.kfTotals.columns.weightKg')" sortable>
        <template #body="{ data: row }">
          {{ formatWeight(row.totalWeightKg) }}
        </template>
      </Column>
      <Column field="feeRateHufPerKg" :header="t('epr.filing.kfTotals.columns.feeRate')" sortable>
        <template #body="{ data: row }">
          {{ formatFeeRate(row.feeRateHufPerKg) }}
        </template>
      </Column>
      <Column field="totalFeeHuf" :header="t('epr.filing.kfTotals.columns.feeFt')" sortable>
        <template #body="{ data: row }">
          {{ formatHuf(row.totalFeeHuf) }}
        </template>
      </Column>
      <Column field="contributingProductCount" :header="t('epr.filing.kfTotals.columns.productCount')" sortable />
    </DataTable>
  </div>
</template>
