<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Button from 'primevue/button'
import { useAdminAudit } from '~/composables/useAdminAudit'
import InputText from 'primevue/inputtext'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { useIdentityStore } from '~/stores/identity'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const identityStore = useIdentityStore()

const { results, pending, error, search } = useAdminAudit()

const taxNumber = ref('')
const tenantId = ref('')
const currentPage = ref(0)
const pageSize = ref(20)

const canSearch = computed(
  () => taxNumber.value.trim() !== '' || tenantId.value.trim() !== '',
)

onMounted(() => {
  if (identityStore.user?.role !== 'SME_ADMIN') {
    router.replace('/')
  }
})

watch(error, (val) => {
  if (val) {
    toast.add({ severity: 'error', summary: t('admin.auditSearch.errors.loadFailed'), life: 4000 })
  }
})

async function handleSearch(page = 0) {
  currentPage.value = page
  await search(
    taxNumber.value.trim() || null,
    tenantId.value.trim() || null,
    page,
    pageSize.value,
  )
}

function formatHash(hash: string): string {
  if (!hash || hash === 'HASH_UNAVAILABLE') return hash
  return hash.substring(0, 16) + '…'
}
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-7xl mx-auto">
    <!-- Breadcrumb -->
    <nav class="text-sm text-slate-500 flex items-center gap-1">
      <NuxtLink to="/admin" class="hover:text-slate-700">
        {{ t('common.nav.admin') }}
      </NuxtLink>
      <span>/</span>
      <span class="text-slate-800">{{ t('admin.auditSearch.title') }}</span>
    </nav>

    <!-- Page header -->
    <div>
      <h1 class="text-2xl font-bold text-slate-800">
        {{ t('admin.auditSearch.title') }}
      </h1>
      <p class="text-slate-500 mt-1">{{ t('admin.auditSearch.subtitle') }}</p>
    </div>

    <!-- Search form -->
    <div class="flex flex-wrap gap-4 items-end">
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium text-slate-700">
          {{ t('admin.auditSearch.taxNumberLabel') }}
        </label>
        <InputText
          v-model="taxNumber"
          :placeholder="t('admin.auditSearch.taxNumberPlaceholder')"
          data-testid="tax-number-input"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium text-slate-700">
          {{ t('admin.auditSearch.tenantIdLabel') }}
        </label>
        <InputText
          v-model="tenantId"
          :placeholder="t('admin.auditSearch.tenantIdPlaceholder')"
          data-testid="tenant-id-input"
        />
      </div>
      <Button
        :label="t('admin.auditSearch.searchButton')"
        icon="pi pi-search"
        :disabled="!canSearch"
        :loading="pending"
        data-testid="search-btn"
        @click="handleSearch(0)"
      />
    </div>

    <!-- Loading skeleton -->
    <div v-if="pending" data-testid="loading-skeleton">
      <Skeleton height="4rem" />
    </div>

    <!-- Results table -->
    <div v-else-if="results" data-testid="results-table">
      <DataTable
        lazy
        :value="results.content"
        :total-records="results.totalElements"
        :rows="pageSize"
        :first="currentPage * pageSize"
        paginator
        @page="handleSearch($event.page)"
      >
        <template #empty>
          {{ t('admin.auditSearch.noRecords') }}
        </template>

        <Column field="searchedAt" :header="t('admin.auditSearch.columns.searchedAt')">
          <template #body="{ data }">
            {{ new Date(data.searchedAt).toLocaleString() }}
          </template>
        </Column>

        <Column field="taxNumber" :header="t('admin.auditSearch.columns.taxNumber')" />

        <Column field="sha256Hash" :header="t('admin.auditSearch.columns.sha256Hash')">
          <template #body="{ data }">
            <span :title="data.sha256Hash">{{ formatHash(data.sha256Hash) }}</span>
          </template>
        </Column>

        <Column field="sourceUrls" :header="t('admin.auditSearch.columns.sourceUrls')">
          <template #body="{ data }">
            <Tag
              v-if="data.sourceUrls && data.sourceUrls.length > 0"
              :value="`${data.sourceUrls.length} URLs`"
              :title="data.sourceUrls.join('\n')"
            />
            <span v-else>0</span>
          </template>
        </Column>

        <Column field="userId" :header="t('admin.auditSearch.columns.userId')" />

        <Column field="tenantId" :header="t('admin.auditSearch.columns.tenantId')" />

        <Column field="checkSource" :header="t('admin.auditSearch.columns.checkSource')" />

        <Column field="verdictStatus" :header="t('admin.auditSearch.columns.verdictStatus')" />
      </DataTable>
    </div>
  </div>
</template>
