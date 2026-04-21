<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import type { ProvenanceLine, ProvenanceTag } from '~/types/epr'

const props = defineProps<{
  rows: ProvenanceLine[]
  totalElements: number
  isLoading: boolean
  period: { from: string; to: string }
}>()

const emit = defineEmits<{
  page: [{ page: number; rows: number }]
}>()

const { t } = useI18n()

const pageSize = ref(50)
const currentPage = ref(0)

function onPage(event: { page: number; rows: number }) {
  currentPage.value = event.page
  pageSize.value = event.rows
  emit('page', event)
}

function tagSeverity(tag: ProvenanceTag): 'success' | 'warn' | 'danger' | 'contrast' {
  switch (tag) {
    case 'REGISTRY_MATCH': return 'success'
    case 'VTSZ_FALLBACK': return 'warn'
    case 'UNRESOLVED': return 'danger'
    case 'UNSUPPORTED_UNIT': return 'contrast'
  }
}

function formatWeight(value: number): string {
  return value.toFixed(4)
}

watch(() => props.period, () => {
  currentPage.value = 0
})
</script>

<template>
  <div>
    <!-- Loading skeleton -->
    <div v-if="isLoading" class="space-y-2" data-testid="provenance-skeleton">
      <div v-for="i in 5" :key="i" class="flex gap-3">
        <Skeleton width="12%" height="1.25rem" />
        <Skeleton width="5%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="20%" height="1.25rem" />
        <Skeleton width="8%" height="1.25rem" />
        <Skeleton width="20%" height="1.25rem" />
        <Skeleton width="10%" height="1.25rem" />
        <Skeleton width="8%" height="1.25rem" />
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
      data-testid="provenance-table"
      @page="onPage"
    >
      <template #empty>
        <span class="text-slate-400 text-sm">{{ t('epr.filing.audit.emptyMessage') }}</span>
      </template>
      <Column field="invoiceNumber" :header="t('epr.filing.audit.columns.invoiceNumber')" />
      <Column field="lineNumber" :header="t('epr.filing.audit.columns.lineNumber')" />
      <Column field="vtsz" :header="t('epr.filing.audit.columns.vtsz')" />
      <Column field="description" :header="t('epr.filing.audit.columns.description')" />
      <Column :header="t('epr.filing.audit.columns.quantity')">
        <template #body="{ data: row }">
          {{ row.quantity }} {{ row.unitOfMeasure }}
        </template>
      </Column>
      <Column :header="t('epr.filing.audit.columns.productName')">
        <template #body="{ data: row }">
          <a
            v-if="row.resolvedProductId"
            :href="`/registry/${row.resolvedProductId}`"
            class="text-indigo-600 hover:underline"
          >{{ row.productName }}</a>
          <span v-else class="text-slate-400">—</span>
        </template>
      </Column>
      <Column field="componentKfCode" :header="t('epr.filing.audit.columns.kfCode')" />
      <Column field="wrappingLevel" :header="t('epr.filing.audit.columns.wrappingLevel')" />
      <Column :header="t('epr.filing.audit.columns.weightContributionKg')">
        <template #body="{ data: row }">
          {{ formatWeight(row.weightContributionKg) }}
        </template>
      </Column>
      <Column :header="t('epr.filing.audit.columns.provenanceTag')">
        <template #body="{ data: row }">
          <Tag
            :value="t(`epr.filing.audit.tagLabel.${row.provenanceTag}`)"
            :severity="tagSeverity(row.provenanceTag)"
            :data-testid="`tag-${row.provenanceTag}`"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>
