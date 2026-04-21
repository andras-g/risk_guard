<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import Button from 'primevue/button'
import type { SoldProductLine, UnresolvedInvoiceLine } from '~/types/epr'

const props = defineProps<{
  soldProducts: SoldProductLine[]
  unresolvedLines: UnresolvedInvoiceLine[]
  loading: boolean
}>()

const { t } = useI18n()
const router = useRouter()

const onlyMissingActive = ref(false)
const onlyUncertainActive = ref(false)
const paginatorFirst = ref(0)
watch([onlyMissingActive, onlyUncertainActive], () => {
  paginatorFirst.value = 0
})

type RowStatus = 'ready' | 'uncertain' | 'missing'

function getRowStatus(row: SoldProductLine): RowStatus {
  const hasZeroComponents = props.unresolvedLines.some(
    u => u.reason === 'ZERO_COMPONENTS' && u.vtsz === row.vtsz && u.description === row.description,
  )
  if (hasZeroComponents) return 'missing'
  const hasFallback = props.unresolvedLines.some(
    u => u.reason === 'VTSZ_FALLBACK' && u.vtsz === row.vtsz && u.description === row.description,
  )
  if (hasFallback) return 'uncertain'
  return 'ready'
}

const filteredProducts = computed(() => {
  let rows = props.soldProducts
  if (onlyMissingActive.value) {
    rows = rows.filter(r => getRowStatus(r) === 'missing')
  }
  if (onlyUncertainActive.value) {
    rows = rows.filter(r => getRowStatus(r) === 'uncertain')
  }
  return rows
})

function badgeSeverity(status: RowStatus): 'success' | 'warn' | 'danger' {
  if (status === 'missing') return 'danger'
  if (status === 'uncertain') return 'warn'
  return 'success'
}

function badgeLabel(status: RowStatus): string {
  if (status === 'missing') return t('epr.filing.soldProducts.badge.missing')
  if (status === 'uncertain') return t('epr.filing.soldProducts.badge.uncertain')
  return t('epr.filing.soldProducts.badge.ready')
}

function formatQuantity(value: number): string {
  return new Intl.NumberFormat('hu-HU', { style: 'decimal', maximumFractionDigits: 2 }).format(value)
}

function onRowClick(row: SoldProductLine | undefined) {
  if (!row || !row.vtsz) return
  if (row.productId) {
    router.push(`/registry/${row.productId}`)
  }
  else {
    router.push(`/registry?vtsz=${encodeURIComponent(row.vtsz)}&q=${encodeURIComponent(row.description ?? '')}`)
  }
}
</script>

<template>
  <div>
    <!-- Filter chips -->
    <div class="flex gap-2 mb-3">
      <Button
        :label="t('epr.filing.soldProducts.filterChips.onlyMissing')"
        size="small"
        :severity="onlyMissingActive ? 'danger' : 'secondary'"
        :outlined="!onlyMissingActive"
        data-testid="filter-chip-missing"
        @click="onlyMissingActive = !onlyMissingActive"
      />
      <Button
        :label="t('epr.filing.soldProducts.filterChips.onlyUncertain')"
        size="small"
        :severity="onlyUncertainActive ? 'warn' : 'secondary'"
        :outlined="!onlyUncertainActive"
        data-testid="filter-chip-uncertain"
        @click="onlyUncertainActive = !onlyUncertainActive"
      />
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-2" data-testid="sold-products-skeleton">
      <div v-for="i in 5" :key="i" class="flex gap-4">
        <Skeleton width="15%" height="1.25rem" />
        <Skeleton width="30%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
      </div>
    </div>

    <!-- Data table -->
    <DataTable
      v-else
      :value="filteredProducts"
      sort-field="totalQuantity"
      :sort-order="-1"
      :paginator="true"
      :first="paginatorFirst"
      :rows="10"
      :rows-per-page-options="[10, 25, 50]"
      row-hover
      data-testid="sold-products-table"
      @page="(e) => (paginatorFirst = e.first)"
      @row-click="(e) => onRowClick(e.data)"
    >
      <Column field="vtsz" :header="t('epr.filing.soldProducts.columns.vtsz')" sortable />
      <Column field="description" :header="t('epr.filing.soldProducts.columns.description')" sortable />
      <Column field="totalQuantity" :header="t('epr.filing.soldProducts.columns.quantity')" sortable>
        <template #body="{ data: row }">
          {{ formatQuantity(row.totalQuantity) }}
        </template>
      </Column>
      <Column field="unitOfMeasure" :header="t('epr.filing.soldProducts.columns.unit')" sortable />
      <Column field="matchingInvoiceLines" :header="t('epr.filing.soldProducts.columns.lines')" sortable />
      <Column :header="t('epr.filing.soldProducts.columns.status')">
        <template #body="{ data: row }">
          <Tag
            :severity="badgeSeverity(getRowStatus(row))"
            :value="badgeLabel(getRowStatus(row))"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>
