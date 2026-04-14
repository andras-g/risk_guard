<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useRegistry } from '~/composables/api/useRegistry'
import { useRegistryStore } from '~/stores/registry'
import { useApiError } from '~/composables/api/useApiError'
import type { ProductSummaryResponse, RegistryPageResponse } from '~/composables/api/useRegistry'

const { t } = useI18n()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const router = useRouter()
const registryStore = useRegistryStore()
const { listProducts, archiveProduct } = useRegistry()
const { mapErrorType } = useApiError()
const toast = useToast()

// ─── Filter state ─────────────────────────────────────────────────────────────
const searchQ = ref<string>('')
const statusFilter = ref<'ACTIVE' | 'ARCHIVED' | 'DRAFT' | null>(null)
const kfCodeFilter = ref<string>('')

const statusOptions = computed(() => [
  { label: t('registry.list.statusAll'), value: null },
  { label: t('registry.status.ACTIVE'), value: 'ACTIVE' as const },
  { label: t('registry.status.DRAFT'), value: 'DRAFT' as const },
  { label: t('registry.status.ARCHIVED'), value: 'ARCHIVED' as const },
])

// ─── Pagination state ─────────────────────────────────────────────────────────
const currentPage = ref(0)
const pageSize = ref(50)
const totalRecords = ref(0)

// ─── Data state ───────────────────────────────────────────────────────────────
const products = ref<ProductSummaryResponse[]>([])
const isLoading = ref(false)
const error = ref<string | null>(null)

// ─── Fetch ────────────────────────────────────────────────────────────────────
async function fetchProducts() {
  isLoading.value = true
  error.value = null
  try {
    const result: RegistryPageResponse = await listProducts({
      q: searchQ.value || undefined,
      status: statusFilter.value || undefined,
      kfCode: kfCodeFilter.value || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })
    products.value = result.items
    totalRecords.value = Number(result.total)
  }
  catch (err: unknown) {
    const e = err as { data?: { type?: string } }
    error.value = mapErrorType(e.data?.type)
  }
  finally {
    isLoading.value = false
  }
}

// ─── Debounced filter ─────────────────────────────────────────────────────────
let debounceTimer: ReturnType<typeof setTimeout> | null = null
function onFilterChange() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchProducts()
  }, 250)
}

watch(searchQ, onFilterChange)
watch(statusFilter, onFilterChange)
watch(kfCodeFilter, onFilterChange)

function onPage(event: { page: number; rows: number }) {
  currentPage.value = event.page
  pageSize.value = event.rows
  fetchProducts()
}

// ─── Status Tag severity ──────────────────────────────────────────────────────
function tagSeverity(status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'): 'success' | 'warn' | 'secondary' {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'DRAFT': return 'warn'
    case 'ARCHIVED': return 'secondary'
  }
}

// ─── Archive ──────────────────────────────────────────────────────────────────
async function onArchive(product: ProductSummaryResponse) {
  try {
    await archiveProduct(product.id)
    toast.add({ severity: 'success', summary: t('registry.actions.archived'), life: 3000 })
    fetchProducts()
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 4000 })
  }
}

onMounted(() => fetchProducts())

const isEmpty = computed(() => !isLoading.value && !error.value && products.value.length === 0 && !searchQ.value && !kfCodeFilter.value && !statusFilter.value)
</script>

<template>
  <!-- Tier Gate: PRO_EPR required (AC 12) -->
  <div
    v-if="!hasAccess"
    class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto"
    data-testid="registry-tier-gate"
  >
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('registry.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <div v-else class="p-4 flex flex-col gap-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ t('registry.title') }}</h1>
      <Button
        :label="t('registry.actions.create')"
        icon="pi pi-plus"
        @click="router.push('/registry/new')"
      />
    </div>

    <!-- Filters -->
    <div class="flex flex-wrap gap-3">
      <div class="flex flex-col gap-1 flex-1 min-w-48">
        <label for="registry-search" class="text-sm font-medium">{{ t('registry.list.search') }}</label>
        <InputText
          id="registry-search"
          v-model="searchQ"
          :placeholder="t('registry.list.searchPlaceholder')"
        />
      </div>
      <div class="flex flex-col gap-1 min-w-40">
        <label for="registry-status" class="text-sm font-medium">{{ t('registry.list.statusFilter') }}</label>
        <Select
          id="registry-status"
          v-model="statusFilter"
          :options="statusOptions"
          option-label="label"
          option-value="value"
        />
      </div>
      <div class="flex flex-col gap-1 min-w-40">
        <label for="registry-kf" class="text-sm font-medium">{{ t('registry.list.kfCodeFilter') }}</label>
        <InputText
          id="registry-kf"
          v-model="kfCodeFilter"
          :placeholder="t('registry.list.kfCodePlaceholder')"
        />
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-if="isEmpty"
      class="flex flex-col items-center justify-center py-16 gap-4 text-center"
      aria-live="polite"
    >
      <i class="pi pi-box text-5xl text-gray-400" aria-hidden="true" />
      <h2 class="text-xl font-medium">{{ t('registry.empty.title') }}</h2>
      <p class="text-gray-500">{{ t('registry.empty.description') }}</p>
      <Button
        :label="t('registry.empty.cta')"
        icon="pi pi-plus"
        @click="router.push('/registry/new')"
      />
    </div>

    <!-- Data table -->
    <DataTable
      v-else
      :value="products"
      :loading="isLoading"
      lazy
      paginator
      :rows="pageSize"
      :total-records="totalRecords"
      :rows-per-page-options="[20, 50, 100]"
      aria-live="polite"
      @page="onPage"
    >
      <Column field="articleNumber" :header="t('registry.list.columns.articleNumber')" />
      <Column field="name" :header="t('registry.list.columns.name')" />
      <Column field="vtsz" :header="t('registry.list.columns.vtsz')" />
      <Column :header="t('registry.list.columns.componentCount')">
        <template #body="{ data }">
          {{ data.componentCount }}
        </template>
      </Column>
      <Column :header="t('registry.list.columns.status')">
        <template #body="{ data }">
          <Tag :severity="tagSeverity(data.status)" :value="t(`registry.status.${data.status}`)" />
        </template>
      </Column>
      <Column field="updatedAt" :header="t('registry.list.columns.updatedAt')">
        <template #body="{ data }">
          {{ new Date(data.updatedAt).toLocaleDateString() }}
        </template>
      </Column>
      <Column :header="t('registry.list.columns.actions')">
        <template #body="{ data }">
          <div class="flex gap-2">
            <Button
              icon="pi pi-pencil"
              :aria-label="t('registry.actions.edit')"
              size="small"
              text
              @click="router.push(`/registry/${data.id}`)"
            />
            <Button
              v-if="data.status !== 'ARCHIVED'"
              icon="pi pi-archive"
              :aria-label="t('registry.actions.archive')"
              size="small"
              text
              severity="secondary"
              @click="onArchive(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>
  </div>
</template>
