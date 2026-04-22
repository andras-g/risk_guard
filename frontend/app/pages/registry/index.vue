<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import { useConfirm } from 'primevue/useconfirm'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useRegistry } from '~/composables/api/useRegistry'
import { useRegistryCompleteness } from '~/composables/api/useRegistryCompleteness'
import { useRegistryStore } from '~/stores/registry'
import { useApiError } from '~/composables/api/useApiError'
import { useAuthStore } from '~/stores/auth'
import { useEprFilingStore } from '~/stores/eprFiling'
import type { ProductSummaryResponse, RegistryPageResponse } from '~/composables/api/useRegistry'

const { t } = useI18n()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const router = useRouter()
const route = useRoute()
const registryStore = useRegistryStore()
const { listProducts, archiveProduct, resetDemoPackaging } = useRegistry()
const { mapErrorType } = useApiError()
const toast = useToast()
const confirm = useConfirm()
const registryCompleteness = useRegistryCompleteness()
const authStore = useAuthStore()
const filingStore = useEprFilingStore()

// ─── Filter state ─────────────────────────────────────────────────────────────
// Hydrate from query params so InvoiceBootstrapDialog's onOpenRegistry navigation
// (`/registry?reviewState=MISSING_PACKAGING` or `?classifierSource=VTSZ_FALLBACK`)
// actually applies the filter on landing.
const searchQ = ref<string>('')
const statusFilter = ref<'ACTIVE' | 'ARCHIVED' | 'DRAFT' | null>(null)
const kfCodeFilter = ref<string>('')
const onlyIncomplete = ref(route.query.reviewState === 'MISSING_PACKAGING')
const onlyUncertain = ref(route.query.classifierSource === 'VTSZ_FALLBACK')
// Story 10.11 AC #18 — "Only unclassified scope" filter chip + deep-link support
const onlyUnknownScope = ref(route.query.filter === 'epr-scope-unknown')
// Story 10.11 AC #18 — unknown-scope warning banner count sourced from the filing-aggregation
// metadata per the spec (previous client-side aggregate only saw the current page and was
// structurally always-0 before eprScope landed in the list projection).
const unknownScopeProducts = computed(() =>
  filingStore.aggregation?.metadata?.unknownScopeProductsInPeriod ?? 0)

// Story 10.11 AC #15c — demo reset button visibility. The backend is @Profile({"demo","e2e"})
// gated AND tenant-whitelisted; the frontend here only hides the button for non-demo tenants
// so it never appears in production. Demo tenant UUIDs match R__demo_data.sql seed values.
const DEMO_TENANT_IDS = new Set([
  '00000000-0000-4000-b000-000000000020',
  '00000000-0000-4000-b000-000000000021',
])
const isDemoTenant = computed(() =>
  !!authStore.activeTenantId && DEMO_TENANT_IDS.has(authStore.activeTenantId))

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
      reviewState: onlyIncomplete.value ? 'MISSING_PACKAGING' : undefined,
      classifierSource: onlyUncertain.value ? 'VTSZ_FALLBACK' : undefined,
      onlyUnknownScope: onlyUnknownScope.value || undefined,
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
watch(onlyIncomplete, onFilterChange)
watch(onlyUncertain, onFilterChange)
watch(onlyUnknownScope, (next) => {
  // Keep ?filter=epr-scope-unknown in sync with the chip so the deep-link from the filing
  // page survives a page refresh and the back/forward buttons restore the visible filter
  // state. Falls back to a bare /registry URL when toggled off.
  const target = next ? 'epr-scope-unknown' : undefined
  if (route.query.filter !== target) {
    const next_query = { ...route.query }
    if (target) next_query.filter = target
    else delete next_query.filter
    router.replace({ query: next_query })
  }
  onFilterChange()
})
// Hydrate the chip when the route query changes via in-app navigation (e.g., the filing-page
// banner click while the user is already on /registry — Vue may reuse this component instance,
// so the ref initialiser does not re-run).
watch(() => route.query.filter, (next) => {
  const wantsUnknown = next === 'epr-scope-unknown'
  if (onlyUnknownScope.value !== wantsUnknown) onlyUnknownScope.value = wantsUnknown
})

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

// ─── Story 10.11 AC #15c — Demo reset ─────────────────────────────────────────
function onDemoReset() {
  confirm.require({
    message: t('registry.demo.resetPackaging.confirmMessage'),
    header: t('registry.demo.resetPackaging.button'),
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: t('common.actions.cancel'), severity: 'secondary', outlined: true },
    acceptProps: { label: t('registry.demo.resetPackaging.button'), severity: 'danger' },
    accept: async () => {
      try {
        const res = await resetDemoPackaging()
        toast.add({
          severity: 'success',
          summary: t('registry.demo.resetPackaging.success', { count: res.deletedComponents }),
          life: 4000,
        })
        await registryCompleteness.refresh()
        fetchProducts()
      }
      catch (err: unknown) {
        const e = err as { data?: { errorMessageKey?: string } }
        const key = e?.data?.errorMessageKey
        toast.add({
          severity: 'error',
          summary: key ? t(key) : t('registry.demo.resetPackaging.error'),
          life: 5000,
        })
      }
    },
  })
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

onMounted(async () => {
  await registryCompleteness.refresh()
  if (registryCompleteness.totalProducts.value > 0
    && registryCompleteness.productsWithComponents.value === 0) {
    onlyIncomplete.value = true
  }
  // Skip the listing call when the registry is provably empty — the onboarding
  // block renders instead of the DataTable, so any list response would be discarded.
  if (registryCompleteness.totalProducts.value > 0) {
    fetchProducts()
  }
})

function onBootstrapCompleted() {
  registryCompleteness.refresh().then(() => fetchProducts())
}
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
      <div class="flex items-center gap-2">
        <!-- Story 10.10: secondary CTA to jump into the quarterly filing flow once there is data to report. -->
        <Button
          v-if="registryCompleteness.productsWithComponents.value > 0"
          :label="t('registry.actions.openFiling')"
          icon="pi pi-file"
          severity="secondary"
          outlined
          data-testid="header-cta-filing"
          @click="router.push('/epr/filing')"
        />
        <Button
          :label="t('registry.actions.create')"
          icon="pi pi-plus"
          @click="router.push('/registry/new')"
        />
        <!-- Story 10.11 AC #15c — demo-only packaging reset button -->
        <Button
          v-if="isDemoTenant"
          :label="t('registry.demo.resetPackaging.button')"
          icon="pi pi-refresh"
          severity="danger"
          outlined
          data-testid="demo-reset-packaging-btn"
          @click="onDemoReset"
        />
      </div>
    </div>
    <ConfirmDialog />

    <!-- Filters (hidden when registry is wholly empty — AC #14: only header stays visible) -->
    <div
      v-if="!(registryCompleteness.totalProducts.value === 0 && !registryCompleteness.isLoading.value)"
      class="flex flex-wrap gap-3 items-end"
    >
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

      <!-- Filter chips (AC #24) -->
      <div class="flex gap-2 items-center self-end pb-0.5">
        <Button
          :label="t('registry.filter.onlyIncomplete')"
          :severity="onlyIncomplete ? 'warn' : 'secondary'"
          :outlined="!onlyIncomplete"
          size="small"
          data-testid="filter-chip-incomplete"
          @click="onlyIncomplete = !onlyIncomplete"
        />
        <Button
          :label="t('registry.filter.onlyUncertain')"
          :severity="onlyUncertain ? 'warn' : 'secondary'"
          :outlined="!onlyUncertain"
          size="small"
          data-testid="filter-chip-uncertain"
          @click="onlyUncertain = !onlyUncertain"
        />
        <!-- Story 10.11 AC #18 — "Only unclassified EPR scope" chip -->
        <Button
          :label="t('registry.filters.eprScopeUnknown')"
          :severity="onlyUnknownScope ? 'warn' : 'secondary'"
          :outlined="!onlyUnknownScope"
          size="small"
          data-testid="filter-chip-epr-scope-unknown"
          @click="onlyUnknownScope = !onlyUnknownScope"
        />
      </div>
    </div>

    <!-- Story 10.11 AC #18 — unknown-scope warning banner -->
    <div
      v-if="unknownScopeProducts > 0"
      class="p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3"
      data-testid="banner-unknown-scope"
    >
      <i class="pi pi-info-circle text-amber-500" aria-hidden="true" />
      <span class="text-amber-800 text-sm">
        {{ t('registry.list.banner.unknownScope', { n: unknownScopeProducts }) }}
      </span>
    </div>

    <!-- Empty registry onboarding (no products at all) -->
    <RegistryOnboardingBlock
      v-if="registryCompleteness.totalProducts.value === 0 && !registryCompleteness.isLoading.value"
      context="registry"
      @bootstrap-completed="onBootstrapCompleted"
    />

    <!-- All-incomplete banner (products exist but none have kf_code components) -->
    <div
      v-if="registryCompleteness.totalProducts.value > 0 && registryCompleteness.productsWithComponents.value === 0 && !registryCompleteness.isLoading.value"
      class="p-3 bg-amber-50 border border-amber-200 rounded flex items-center gap-3"
      data-testid="all-incomplete-banner"
    >
      <i class="pi pi-exclamation-triangle text-amber-500" aria-hidden="true" />
      <span class="text-amber-800 text-sm">{{ t('registry.onboarding.allIncompleteBanner') }}</span>
    </div>

    <!-- Data table (shown when products exist, even if all incomplete) -->
    <DataTable
      v-show="registryCompleteness.totalProducts.value > 0"
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
      <Column field="name" :header="t('registry.list.columns.name')">
        <template #body="{ data }">
          <div class="flex items-center gap-2">
            {{ data.name }}
            <Tag
              v-if="data.reviewState === 'MISSING_PACKAGING'"
              :value="t('registry.rowBadge.missingPackaging')"
              severity="warn"
              class="text-xs"
              data-testid="badge-missing-packaging"
            />
            <Tag
              v-else-if="data.classifierSource === 'VTSZ_FALLBACK'"
              severity="secondary"
              class="text-xs"
              data-testid="badge-vtsz-fallback"
            >
              {{ t('registry.rowBadge.vtszFallback') }}
            </Tag>
          </div>
        </template>
      </Column>
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
