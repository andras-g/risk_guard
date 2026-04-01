<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { FilterMatchMode } from '@primevue/core/api'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { useFlightControlStore } from '~/stores/flightControl'
import { usePortfolioStore } from '~/stores/portfolio'
import { useAuthStore } from '~/stores/auth'
import { useStatusColor } from '~/composables/formatting/useStatusColor'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import { useApiError } from '~/composables/api/useApiError'
import type { FlightControlTenantSummaryResponse } from '~/types/api'
import type { PortfolioAlertResponse } from '~/types/api'

definePageMeta({ middleware: 'auth' })

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const flightControlStore = useFlightControlStore()
const portfolioStore = usePortfolioStore()
const { mapErrorType } = useApiError()
const { formatRelative } = useDateRelative()
const { statusColorClass, statusIconClass, statusI18nKey } = useStatusColor()
const toast = useToast()

const { tenants, totals, isLoading, error } = storeToRefs(flightControlStore)
const { alerts: recentAlerts, isLoading: alertsLoading } = storeToRefs(portfolioStore)
const { isAccountant } = storeToRefs(authStore)

// AC3: DataTable filtering state — global text filter + risk-level filter
const filters = ref({
  global: { value: null as string | null, matchMode: FilterMatchMode.CONTAINS },
})

// Risk-level filter: show only tenants with At-Risk > 0 (or all)
type RiskFilter = 'all' | 'at-risk' | 'stale'
const riskFilter = ref<RiskFilter>('all')

const riskFilterOptions = computed(() => [
  { label: t('notification.flightControl.filterAll'), value: 'all' as RiskFilter },
  { label: t('notification.flightControl.filterAtRisk'), value: 'at-risk' as RiskFilter },
  { label: t('notification.flightControl.filterStale'), value: 'stale' as RiskFilter },
])

// Computed: filtered tenants (combines text filter + risk-level filter).
// DataTable handles its own globalFilterFields internally, but mobile stacked cards
// iterate filteredTenants directly — so we must apply the text filter here too.
const filteredTenants = computed(() => {
  let result = tenants.value

  // Apply text filter (client name search) — used by mobile cards
  const query = filters.value.global?.value?.trim().toLowerCase()
  if (query) {
    result = result.filter(t => t.tenantName.toLowerCase().includes(query))
  }

  // Apply risk-level filter
  if (riskFilter.value === 'at-risk') return result.filter(t => t.atRiskCount > 0)
  if (riskFilter.value === 'stale') return result.filter(t => t.staleCount > 0)
  return result
})

// Redirect non-accountants to dashboard
onMounted(async () => {
  if (!isAccountant.value) {
    router.push('/dashboard')
    return
  }
  // M2: Parallel loading — both fetches are independent, fire concurrently
  await Promise.all([
    flightControlStore.fetchSummary(),
    portfolioStore.fetchAlerts(7),
  ])
})

// Computed: is empty state? (no clients at all)
const isEmpty = computed(() => !isLoading.value && !error.value && totals.value?.totalClients === 0)

/**
 * Handle click on a client row — switch tenant context and navigate to dashboard.
 * Follows the exact pattern from PortfolioPulse.vue (Story 3.9 learning).
 */
async function handleClientClick(tenant: FlightControlTenantSummaryResponse) {
  try {
    if (authStore.activeTenantId !== tenant.tenantId) {
      // switchTenant() triggers window.location.reload() after the HTTP call,
      // so router.push would be dead code. Store the redirect target in
      // sessionStorage so the app can navigate after the reload completes.
      sessionStorage.setItem('postSwitchRedirect', '/dashboard')
      await authStore.switchTenant(tenant.tenantId)
      // If we reach here, the page is reloading — code below is unreachable.
      return
    }
    router.push('/dashboard')
  }
  catch (err: unknown) {
    sessionStorage.removeItem('postSwitchRedirect')
    const errorType = extractErrorType(err)
    toast.add({
      severity: 'error',
      summary: mapErrorType(errorType),
      life: 5000,
    })
  }
}

/**
 * Handle click on a recent alert — switch tenant context and navigate to screening.
 */
async function handleAlertClick(alert: PortfolioAlertResponse) {
  try {
    if (authStore.activeTenantId !== alert.tenantId) {
      // switchTenant() triggers window.location.reload() — store redirect target
      sessionStorage.setItem('postSwitchRedirect', `/screening/${alert.taxNumber}`)
      await authStore.switchTenant(alert.tenantId)
      return
    }
    router.push(`/screening/${alert.taxNumber}`)
  }
  catch (err: unknown) {
    sessionStorage.removeItem('postSwitchRedirect')
    const errorType = extractErrorType(err)
    toast.add({
      severity: 'error',
      summary: mapErrorType(errorType),
      life: 5000,
    })
  }
}

/** Localized status change text for alerts (reuses screening.verdict.* i18n keys). */
function localizedStatusChange(previousStatus: string | null, newStatus: string | null): string {
  const prev = t(statusI18nKey(previousStatus))
  const next = t(statusI18nKey(newStatus))
  return t('notification.portfolio.statusChange', { previous: prev, new: next })
}

/** Extract RFC 7807 error type from FetchError (Story 3.9 learning). */
function extractErrorType(err: unknown): string | undefined {
  if (err && typeof err === 'object') {
    const fetchError = err as { data?: { type?: string } }
    if (fetchError.data?.type) return fetchError.data.type
  }
  return undefined
}
</script>

<template>
  <div data-testid="flight-control-page" class="space-y-6">
    <!-- Page Header -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-slate-900">
        {{ t('notification.flightControl.pageTitle') }}
      </h1>
    </div>

    <!-- Loading skeleton for summary bar + table -->
    <div v-if="isLoading" class="space-y-4" data-testid="flight-control-loading">
      <!-- Summary pills skeleton -->
      <div class="flex gap-4">
        <Skeleton v-for="i in 3" :key="i" width="180px" height="72px" border-radius="8px" />
      </div>
      <!-- Table skeleton -->
      <div class="bg-white rounded-lg border border-slate-200 p-4 space-y-3">
        <Skeleton v-for="i in 5" :key="i" height="44px" />
      </div>
    </div>

    <!-- Error state -->
    <div
      v-else-if="error"
      class="flex flex-col items-center py-12 text-center"
      data-testid="flight-control-error"
    >
      <i class="pi pi-exclamation-circle text-5xl text-red-400 mb-4" />
      <p class="text-slate-700 font-semibold text-lg">
        {{ t('common.states.error') }}
      </p>
      <button
        class="mt-4 text-sm text-blue-600 hover:underline"
        @click="flightControlStore.fetchSummary()"
      >
        {{ t('common.actions.retry') }}
      </button>
    </div>

    <!-- Empty state (no mandated clients) -->
    <div
      v-else-if="isEmpty"
      class="flex flex-col items-center py-16 text-center"
      data-testid="flight-control-empty"
    >
      <i class="pi pi-wifi text-6xl text-slate-300 mb-4" />
      <h2 class="text-xl font-semibold text-slate-700 mb-2">
        {{ t('notification.flightControl.emptyTitle') }}
      </h2>
      <p class="text-slate-500 max-w-sm mb-6">
        {{ t('notification.flightControl.emptyBody') }}
      </p>
      <button class="px-4 py-2 bg-indigo-600 text-white rounded-md text-sm font-medium hover:bg-indigo-700 transition-colors">
        {{ t('notification.flightControl.emptyAction') }}
      </button>
    </div>

    <!-- Main content: Summary + Table + Alerts -->
    <template v-else>
      <!-- AC3/AC6: Summary Bar — three metric pills (mobile: 1-col, tablet: 2-col, desktop: 3-col) -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" data-testid="flight-control-summary">
        <!-- Total Clients (neutral) -->
        <div class="bg-white rounded-lg border border-slate-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-slate-500 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.summaryTotalClients') }}
          </span>
          <span class="text-3xl font-bold text-slate-900" data-testid="summary-total-clients">
            {{ totals?.totalClients ?? 0 }}
          </span>
        </div>
        <!-- At-Risk (Crimson) -->
        <div class="bg-red-50 rounded-lg border border-red-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-red-600 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.summaryAtRisk') }}
          </span>
          <span class="text-3xl font-bold text-red-700" data-testid="summary-at-risk">
            {{ totals?.totalAtRisk ?? 0 }}
          </span>
        </div>
        <!-- Stale (Amber) -->
        <div class="bg-amber-50 rounded-lg border border-amber-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-amber-600 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.summaryStale') }}
          </span>
          <span class="text-3xl font-bold text-amber-700" data-testid="summary-stale">
            {{ totals?.totalStale ?? 0 }}
          </span>
        </div>
      </div>

      <!-- AC6/H2: Mobile stacked cards (<768px) — Compact Card layout per UX Spec §8.1 -->
      <div class="md:hidden space-y-3" data-testid="flight-control-mobile-cards">
        <!-- Mobile filter bar -->
        <div class="flex flex-col gap-2">
          <label for="filter-client-mobile" class="sr-only">
            {{ t('notification.flightControl.filterPlaceholder') }}
          </label>
          <InputText
            id="filter-client-mobile"
            v-model="filters['global'].value"
            :placeholder="t('notification.flightControl.filterPlaceholder')"
            class="w-full"
            data-testid="filter-client-name-mobile"
          />
          <Select
            v-model="riskFilter"
            :options="riskFilterOptions"
            option-label="label"
            option-value="value"
            :aria-label="t('notification.flightControl.filterAll')"
            class="w-full"
            data-testid="filter-risk-level-mobile"
          />
        </div>
        <!-- Stacked tenant cards -->
        <div
          v-for="tenant in filteredTenants"
          :key="tenant.tenantId"
          class="bg-white rounded-lg border border-slate-200 p-3"
          data-testid="mobile-tenant-card"
        >
          <div class="flex items-start justify-between gap-2 mb-2">
            <p
              role="button"
              tabindex="0"
              class="font-semibold text-slate-900 truncate cursor-pointer hover:text-indigo-600"
              @click="handleClientClick(tenant)"
              @keydown.enter="handleClientClick(tenant)"
            >
              {{ tenant.tenantName }}
            </p>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <Tag :value="String(tenant.atRiskCount)" severity="danger" />
            <span class="text-xs text-slate-500">{{ t('notification.flightControl.columnAtRisk') }}</span>
            <Tag :value="String(tenant.staleCount)" severity="warn" />
            <span class="text-xs text-slate-500">{{ t('notification.flightControl.columnStale') }}</span>
            <Tag :value="String(tenant.reliableCount)" severity="success" />
            <span class="text-xs text-slate-500">{{ t('notification.flightControl.columnReliable') }}</span>
          </div>
          <div class="flex items-center justify-between mt-2">
            <p class="text-xs text-slate-400">
              {{ tenant.lastCheckedAt ? formatRelative(tenant.lastCheckedAt) : '—' }}
            </p>
            <NuxtLink
              :to="`/flight-control/${tenant.tenantId}`"
              class="text-xs text-indigo-600 hover:underline"
              data-testid="mobile-view-partners-link"
            >
              {{ t('notification.flightControl.viewPartners') }}
            </NuxtLink>
          </div>
        </div>
      </div>

      <!-- AC3/AC4/AC6: Client DataTable (hidden on mobile, shown on tablet+) -->
      <div class="hidden md:block bg-white rounded-lg border border-slate-200 overflow-hidden">
        <DataTable
          v-model:filters="filters"
          :value="filteredTenants"
          :global-filter-fields="['tenantName']"
          sort-field="atRiskCount"
          :sort-order="-1"
          row-hover
          data-testid="flight-control-table"
        >
          <!-- AC3: Filter header — text search + risk-level dropdown -->
          <template #header>
            <div class="flex flex-wrap items-center gap-3 py-1" data-testid="table-filter-header">
              <label for="filter-client-desktop" class="sr-only">
                {{ t('notification.flightControl.filterPlaceholder') }}
              </label>
              <InputText
                id="filter-client-desktop"
                v-model="filters['global'].value"
                :placeholder="t('notification.flightControl.filterPlaceholder')"
                class="w-64"
                data-testid="filter-client-name"
              />
              <Select
                v-model="riskFilter"
                :options="riskFilterOptions"
                option-label="label"
                option-value="value"
                :aria-label="t('notification.flightControl.filterAll')"
                class="w-48"
                data-testid="filter-risk-level"
              />
            </div>
          </template>
          <Column
            field="tenantName"
            :header="t('notification.flightControl.columnClient')"
            sortable
          >
            <template #body="{ data }">
              <NuxtLink
                :to="`/flight-control/${data.tenantId}`"
                class="font-semibold text-slate-900 hover:text-indigo-600 hover:underline"
              >
                {{ data.tenantName }}
              </NuxtLink>
            </template>
          </Column>
          <Column
            field="reliableCount"
            :header="t('notification.flightControl.columnReliable')"
            sortable
          >
            <template #body="{ data }">
              <Tag :value="String(data.reliableCount)" severity="success" />
            </template>
          </Column>
          <Column
            field="atRiskCount"
            :header="t('notification.flightControl.columnAtRisk')"
            sortable
          >
            <template #body="{ data }">
              <Tag :value="String(data.atRiskCount)" severity="danger" />
            </template>
          </Column>
          <Column
            field="staleCount"
            :header="t('notification.flightControl.columnStale')"
            sortable
          >
            <template #body="{ data }">
              <Tag :value="String(data.staleCount)" severity="warn" />
            </template>
          </Column>
          <Column
            field="totalPartners"
            :header="t('notification.flightControl.columnTotal')"
            sortable
          />
          <Column
            field="lastCheckedAt"
            :header="t('notification.flightControl.columnLastCheck')"
            sortable
          >
            <template #body="{ data }">
              <span class="text-sm text-slate-500">
                {{ data.lastCheckedAt ? formatRelative(data.lastCheckedAt) : '—' }}
              </span>
            </template>
          </Column>
          <Column header="">
            <template #body="{ data }">
              <div class="flex items-center gap-2 justify-end">
                <NuxtLink
                  :to="`/flight-control/${data.tenantId}`"
                  class="text-sm text-indigo-600 hover:underline whitespace-nowrap"
                  data-testid="view-partners-link"
                >
                  {{ t('notification.flightControl.viewPartners') }}
                </NuxtLink>
                <button
                  class="p-1 text-slate-500 hover:text-indigo-600"
                  :title="t('notification.flightControl.switchToClient')"
                  data-testid="switch-tenant-button"
                  @click="handleClientClick(data)"
                >
                  <i class="pi pi-arrow-right-arrow-left text-sm" />
                </button>
              </div>
            </template>
          </Column>
        </DataTable>
      </div>

      <!-- AC4: Recent Alerts section (max 10 items, reuses portfolio alerts endpoint) -->
      <section
        class="bg-white rounded-lg border border-slate-200 p-4"
        data-testid="flight-control-alerts"
      >
        <h2 class="text-lg font-semibold text-slate-800 mb-3">
          {{ t('notification.flightControl.recentAlerts') }}
        </h2>
        <!-- Alerts loading -->
        <div v-if="alertsLoading" class="space-y-2">
          <div v-for="i in 3" :key="i" class="h-14 bg-slate-100 rounded animate-pulse" />
        </div>
        <!-- Alerts empty -->
        <p
          v-else-if="recentAlerts.length === 0"
          class="text-slate-400 text-sm"
          data-testid="alerts-empty"
        >
          {{ t('notification.portfolio.emptyTitle') }}
        </p>
        <!-- Alert feed (max 10) -->
        <ul v-else class="space-y-2">
          <li
            v-for="alert in recentAlerts.slice(0, 10)"
            :key="alert.alertId"
            role="button"
            tabindex="0"
            class="flex items-start gap-3 p-3 rounded-md border-l-4 cursor-pointer hover:bg-slate-50 transition-colors"
            :class="statusColorClass(alert.newStatus)"
            data-testid="alert-item"
            @click="handleAlertClick(alert)"
            @keydown.enter="handleAlertClick(alert)"
          >
            <i :class="statusIconClass(alert.newStatus)" class="text-lg mt-0.5 shrink-0" />
            <div class="flex-1 min-w-0">
              <p class="text-xs text-slate-500 font-medium truncate">{{ alert.tenantName }}</p>
              <p class="text-sm font-semibold text-slate-800 truncate">{{ alert.companyName }}</p>
              <p class="text-xs text-slate-500">
                {{ localizedStatusChange(alert.previousStatus, alert.newStatus) }}
              </p>
            </div>
            <span class="text-xs text-slate-400 whitespace-nowrap shrink-0">
              {{ formatRelative(alert.changedAt) }}
            </span>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>
