<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import { useAdminClassifier } from '~/composables/api/useAdminClassifier'
import type { ClassifierUsageSummaryResponse } from '~/composables/api/useAdminClassifier'
import { useAuthStore } from '~/stores/auth'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { getUsage } = useAdminClassifier()

const rows = ref<ClassifierUsageSummaryResponse[]>([])
const isLoading = ref(false)
const loadError = ref(false)

const currentPeriod = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
})

onMounted(async () => {
  if (authStore.role !== 'PLATFORM_ADMIN') {
    router.replace('/dashboard')
    return
  }
  await load()
})

async function load() {
  isLoading.value = true
  loadError.value = false
  try {
    rows.value = await getUsage()
  }
  catch {
    loadError.value = true
  }
  finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">
          {{ t('admin.classifier.title') }}
        </h1>
        <p class="text-sm text-slate-500 mt-1">
          {{ t('admin.classifier.period', { period: currentPeriod }) }}
        </p>
      </div>
      <Button
        icon="pi pi-refresh"
        :label="t('admin.classifier.refresh')"
        :loading="isLoading"
        @click="load"
      />
    </div>

    <div v-if="loadError" class="text-red-500 text-sm">
      {{ t('admin.classifier.errors.loadFailed') }}
    </div>

    <div v-else-if="isLoading" class="py-8 text-center text-slate-400">
      {{ t('common.states.loading') }}
    </div>

    <DataTable
      v-else
      :value="rows"
      data-key="tenantId"
      data-testid="ai-usage-table"
    >
      <template #empty>
        <div class="py-4 text-center text-slate-400">{{ t('admin.classifier.empty') }}</div>
      </template>

      <Column field="tenantName" :header="t('admin.classifier.columns.tenantName')" />
      <Column field="callCount" :header="t('admin.classifier.columns.callCount')" />
      <Column :header="t('admin.classifier.columns.inputTokens')">
        <template #body="{ data }">
          {{ data.inputTokens.toLocaleString() }}
        </template>
      </Column>
      <Column :header="t('admin.classifier.columns.outputTokens')">
        <template #body="{ data }">
          {{ data.outputTokens.toLocaleString() }}
        </template>
      </Column>
    </DataTable>
  </div>
</template>
