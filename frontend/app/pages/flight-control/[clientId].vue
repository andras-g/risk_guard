<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { FilterMatchMode } from '@primevue/core/api'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import { useFlightControlStore } from '~/stores/flightControl'
import { useAuthStore } from '~/stores/auth'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import { useClientPartners } from '~/composables/api/useClientPartners'
import { useApiError } from '~/composables/api/useApiError'
import type { WatchlistEntryResponse } from '~/types/api'

definePageMeta({ middleware: 'auth' })

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const flightControlStore = useFlightControlStore()
const { formatRelative } = useDateRelative()
const { partners, isLoading, error, fetchClientPartners } = useClientPartners()

const { isAccountant } = storeToRefs(authStore)
const { mapErrorType } = useApiError()
const toast = useToast()

function extractErrorType(err: unknown): string | undefined {
  if (err && typeof err === 'object') {
    const fetchError = err as { data?: { type?: string } }
    if (fetchError.data?.type) return fetchError.data.type
  }
  return undefined
}

const clientId = computed(() => route.params.clientId as string)

const clientName = computed(() =>
  flightControlStore.tenants.find(t => t.tenantId === clientId.value)?.tenantName ?? '',
)

// Status filter
type StatusFilter = 'all' | 'reliable' | 'at-risk' | 'stale'
const statusFilter = ref<StatusFilter>('all')
const statusFilterOptions = computed(() => [
  { label: t('notification.flightControl.filterAll'), value: 'all' as StatusFilter },
  { label: t('notification.flightControl.columnReliable'), value: 'reliable' as StatusFilter },
  { label: t('notification.flightControl.columnAtRisk'), value: 'at-risk' as StatusFilter },
  { label: t('notification.flightControl.columnStale'), value: 'stale' as StatusFilter },
])

// Name search filter
const nameSearch = ref('')

// Filtered partners (client-side)
const filteredPartners = computed(() => {
  let result = partners.value

  const query = nameSearch.value.trim().toLowerCase()
  if (query) {
    result = result.filter(p => p.companyName.toLowerCase().includes(query) || p.taxNumber.includes(query))
  }

  if (statusFilter.value === 'reliable') return result.filter(p => p.currentVerdictStatus === 'RELIABLE')
  if (statusFilter.value === 'at-risk') return result.filter(p => p.currentVerdictStatus === 'AT_RISK' || p.currentVerdictStatus === 'TAX_SUSPENDED')
  if (statusFilter.value === 'stale') return result.filter(p => p.currentVerdictStatus === 'UNAVAILABLE' || p.currentVerdictStatus === 'INCOMPLETE')
  return result
})

// Summary stat bar computed from partners
const reliableCount = computed(() => partners.value.filter(p => p.currentVerdictStatus === 'RELIABLE').length)
const atRiskCount = computed(() => partners.value.filter(p => p.currentVerdictStatus === 'AT_RISK' || p.currentVerdictStatus === 'TAX_SUSPENDED').length)
const staleCount = computed(() => partners.value.filter(p => p.currentVerdictStatus === 'UNAVAILABLE' || p.currentVerdictStatus === 'INCOMPLETE').length)

// Trend arrow — local STATUS_SEVERITY map (same as WatchlistTable.vue pattern)
const STATUS_SEVERITY: Record<string, number> = {
  RELIABLE: 0, INCOMPLETE: 1, UNAVAILABLE: 2, TAX_SUSPENDED: 3, AT_RISK: 4,
}

function trendDirection(current: string | null, previous: string | null): 'improved' | 'worsened' | 'stable' | null {
  if (!current || !previous) return null
  const c = STATUS_SEVERITY[current] ?? 99
  const p = STATUS_SEVERITY[previous] ?? 99
  if (c < p) return 'improved'
  if (c > p) return 'worsened'
  return 'stable'
}

// Verdict badge
function verdictBadgeClass(status: string | null): string {
  switch (status) {
    case 'RELIABLE': return 'bg-emerald-100 text-emerald-800'
    case 'AT_RISK': return 'bg-rose-100 text-rose-800'
    case 'TAX_SUSPENDED': return 'bg-amber-100 text-amber-800'
    case 'INCOMPLETE':
    case 'UNAVAILABLE':
    default: return 'bg-slate-100 text-slate-600'
  }
}

function verdictLabel(status: string | null): string {
  if (!status) return '—'
  switch (status) {
    case 'RELIABLE': return t('screening.verdict.reliable')
    case 'AT_RISK': return t('screening.verdict.atRisk')
    case 'TAX_SUSPENDED': return t('screening.verdict.taxSuspended')
    case 'INCOMPLETE': return t('screening.verdict.incomplete')
    case 'UNAVAILABLE': return t('screening.verdict.unavailable')
    default: return status
  }
}

// Switch to client (AC 5)
async function handleSwitchToClient() {
  try {
    sessionStorage.setItem('postSwitchRedirect', '/dashboard')
    await authStore.switchTenant(clientId.value)
  }
  catch (err: unknown) {
    sessionStorage.removeItem('postSwitchRedirect')
    const errorType = extractErrorType(err)
    toast.add({ severity: 'error', summary: mapErrorType(errorType), life: 5000 })
  }
}

// Confirmation modal for "View →" (AC 6)
const showConfirmModal = ref(false)
const pendingTaxNumber = ref<string | null>(null)

function handleViewPartner(partner: WatchlistEntryResponse) {
  pendingTaxNumber.value = partner.taxNumber
  showConfirmModal.value = true
}

async function confirmViewPartner() {
  if (!pendingTaxNumber.value) return
  showConfirmModal.value = false
  try {
    sessionStorage.setItem('postSwitchRedirect', `/screening/${pendingTaxNumber.value}`)
    await authStore.switchTenant(clientId.value)
  }
  catch (err: unknown) {
    sessionStorage.removeItem('postSwitchRedirect')
    const errorType = extractErrorType(err)
    toast.add({ severity: 'error', summary: mapErrorType(errorType), life: 5000 })
  }
}

function cancelViewPartner() {
  showConfirmModal.value = false
  pendingTaxNumber.value = null
}

// Mount — guard non-accountant, fetch partners
onMounted(async () => {
  if (!isAccountant.value) {
    router.push('/dashboard')
    return
  }
  // Ensure tenants list is populated for clientName breadcrumb (handles direct navigation / hard refresh)
  if (flightControlStore.tenants.length === 0) {
    try {
      await flightControlStore.fetchSummary()
    }
    catch {
      // Non-fatal: breadcrumb will show empty name, but partners still load
    }
  }
  await fetchClientPartners(clientId.value)
})
</script>

<template>
  <div data-testid="client-partner-view" class="space-y-6">
    <!-- Breadcrumb -->
    <nav class="flex items-center gap-2 text-sm text-slate-500" data-testid="breadcrumb">
      <NuxtLink to="/flight-control" class="hover:text-indigo-600 hover:underline">
        {{ t('notification.flightControl.pageTitle') }}
      </NuxtLink>
      <span>/</span>
      <span class="text-slate-900 font-medium" data-testid="breadcrumb-client-name">
        {{ clientName }}
      </span>
    </nav>

    <!-- Loading state -->
    <div v-if="isLoading" class="space-y-4" data-testid="client-partners-loading">
      <div class="flex gap-4">
        <div v-for="i in 3" :key="i" class="h-16 w-40 bg-slate-100 rounded-lg animate-pulse" />
      </div>
      <div class="bg-white rounded-lg border border-slate-200 p-4 space-y-3">
        <div v-for="i in 5" :key="i" class="h-10 bg-slate-100 rounded animate-pulse" />
      </div>
    </div>

    <!-- Forbidden error state (AC 4) -->
    <div
      v-else-if="error === 'forbidden'"
      class="flex flex-col items-center py-12 text-center"
      data-testid="forbidden-error"
    >
      <i class="pi pi-lock text-5xl text-red-400 mb-4" />
      <p class="text-slate-700 font-semibold text-lg mb-4">
        {{ t('notification.flightControl.forbiddenError') }}
      </p>
      <NuxtLink to="/flight-control" class="text-sm text-indigo-600 hover:underline">
        {{ t('notification.flightControl.pageTitle') }}
      </NuxtLink>
    </div>

    <!-- Generic error state -->
    <div
      v-else-if="error === 'unknown'"
      class="flex flex-col items-center py-12 text-center"
      data-testid="generic-error"
    >
      <i class="pi pi-exclamation-circle text-5xl text-red-400 mb-4" />
      <p class="text-slate-700 font-semibold text-lg">
        {{ t('common.states.error') }}
      </p>
    </div>

    <!-- Main content -->
    <template v-else>
      <!-- Read-only amber banner (AC 2) -->
      <div
        class="flex items-start gap-3 p-4 rounded-lg border border-amber-300 bg-amber-50 text-amber-800"
        data-testid="read-only-banner"
      >
        <i class="pi pi-info-circle mt-0.5 shrink-0" />
        <p class="text-sm">{{ t('notification.flightControl.readOnlyBanner') }}</p>
      </div>

      <!-- Header row: client name + Switch to Client button -->
      <div class="flex items-center justify-between flex-wrap gap-3">
        <h1 class="text-2xl font-bold text-slate-900" data-testid="client-name-heading">
          {{ clientName }}
        </h1>
        <Button
          :label="t('notification.flightControl.switchToClient')"
          icon="pi pi-arrow-right-arrow-left"
          class="p-button-sm"
          data-testid="switch-to-client-button"
          @click="handleSwitchToClient"
        />
      </div>

      <!-- Summary stat bar (AC 2) -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4" data-testid="stat-bar">
        <div class="bg-emerald-50 rounded-lg border border-emerald-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-emerald-600 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.columnReliable') }}
          </span>
          <span class="text-3xl font-bold text-emerald-700" data-testid="stat-reliable">
            {{ reliableCount }}
          </span>
        </div>
        <div class="bg-red-50 rounded-lg border border-red-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-red-600 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.columnAtRisk') }}
          </span>
          <span class="text-3xl font-bold text-red-700" data-testid="stat-at-risk">
            {{ atRiskCount }}
          </span>
        </div>
        <div class="bg-amber-50 rounded-lg border border-amber-200 p-3 flex flex-col">
          <span class="text-xs font-medium text-amber-600 uppercase tracking-wide mb-1">
            {{ t('notification.flightControl.columnStale') }}
          </span>
          <span class="text-3xl font-bold text-amber-700" data-testid="stat-stale">
            {{ staleCount }}
          </span>
        </div>
      </div>

      <!-- Filter bar -->
      <div class="flex flex-wrap gap-3" data-testid="partner-filter-bar">
        <label for="partner-name-search" class="sr-only">
          {{ t('notification.watchlist.searchPlaceholder') }}
        </label>
        <InputText
          id="partner-name-search"
          v-model="nameSearch"
          :placeholder="t('notification.watchlist.searchPlaceholder')"
          class="w-64"
          data-testid="partner-name-search"
        />
        <Select
          v-model="statusFilter"
          :options="statusFilterOptions"
          option-label="label"
          option-value="value"
          class="w-48"
          data-testid="partner-status-filter"
        />
      </div>

      <!-- Mobile stacked cards (<768px) — AC 7 -->
      <div class="md:hidden space-y-3" data-testid="partner-mobile-cards">
        <div
          v-if="filteredPartners.length === 0"
          class="text-slate-400 text-sm text-center py-8"
        >
          {{ t('notification.watchlist.emptyTitle') }}
        </div>
        <div
          v-for="partner in filteredPartners"
          :key="partner.id"
          class="bg-white rounded-lg border border-slate-200 p-3 space-y-2"
          data-testid="partner-mobile-card"
        >
          <div class="flex items-center justify-between gap-2">
            <div>
              <p class="font-semibold text-slate-900 truncate">{{ partner.companyName }}</p>
              <p class="text-xs font-mono text-slate-500">{{ partner.taxNumber }}</p>
            </div>
            <span
              class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
              :class="verdictBadgeClass(partner.currentVerdictStatus)"
            >
              {{ verdictLabel(partner.currentVerdictStatus) }}
            </span>
          </div>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <span>{{ partner.lastCheckedAt ? formatRelative(partner.lastCheckedAt.toString()) : t('notification.watchlist.neverScreened') }}</span>
            <button
              class="text-indigo-600 hover:underline text-xs"
              data-testid="partner-view-mobile"
              @click="handleViewPartner(partner)"
            >
              {{ t('notification.watchlist.viewScreening') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Desktop partner table (AC 3) -->
      <div class="hidden md:block bg-white rounded-lg border border-slate-200 overflow-hidden" data-testid="partner-table">
        <div v-if="filteredPartners.length === 0" class="py-12 text-center text-slate-400 text-sm">
          {{ t('notification.watchlist.emptyTitle') }}
        </div>
        <table v-else class="w-full text-sm">
          <thead class="bg-slate-50 border-b border-slate-200">
            <tr>
              <th class="text-left px-4 py-3 font-medium text-slate-600">
                {{ t('notification.watchlist.columns.companyName') }}
              </th>
              <th class="text-left px-4 py-3 font-medium text-slate-600">
                {{ t('notification.watchlist.columns.verdictStatus') }}
              </th>
              <th class="text-left px-4 py-3 font-medium text-slate-600">
                {{ t('notification.watchlist.columns.trend') }}
              </th>
              <th class="text-left px-4 py-3 font-medium text-slate-600">
                {{ t('notification.watchlist.columns.lastScreened') }}
              </th>
              <th class="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="partner in filteredPartners"
              :key="partner.id"
              class="border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors"
              data-testid="partner-row"
            >
              <td class="px-4 py-3">
                <p class="font-semibold text-slate-900">{{ partner.companyName }}</p>
                <p class="text-xs font-mono text-slate-400">{{ partner.taxNumber }}</p>
              </td>
              <td class="px-4 py-3">
                <span
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                  :class="verdictBadgeClass(partner.currentVerdictStatus)"
                  data-testid="partner-status-badge"
                >
                  {{ verdictLabel(partner.currentVerdictStatus) }}
                </span>
              </td>
              <td class="px-4 py-3" data-testid="partner-trend">
                <span
                  v-if="trendDirection(partner.currentVerdictStatus, partner.previousVerdictStatus) === 'improved'"
                  class="text-emerald-600 font-bold text-base"
                  title="Improved"
                >↑</span>
                <span
                  v-else-if="trendDirection(partner.currentVerdictStatus, partner.previousVerdictStatus) === 'worsened'"
                  class="text-rose-600 font-bold text-base"
                  title="Worsened"
                >↓</span>
                <span
                  v-else-if="trendDirection(partner.currentVerdictStatus, partner.previousVerdictStatus) === 'stable'"
                  class="text-slate-400 text-base"
                  title="Stable"
                >→</span>
                <span v-else class="text-slate-300">—</span>
              </td>
              <td class="px-4 py-3 text-slate-500">
                {{ partner.lastCheckedAt ? formatRelative(partner.lastCheckedAt.toString()) : t('notification.watchlist.neverScreened') }}
              </td>
              <td class="px-4 py-3 text-right">
                <button
                  class="text-indigo-600 hover:underline text-sm"
                  data-testid="partner-view-button"
                  @click="handleViewPartner(partner)"
                >
                  {{ t('notification.watchlist.viewScreening') }} →
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- AC 6: Confirmation modal for "View →" -->
    <Dialog
      v-model:visible="showConfirmModal"
      :header="t('notification.flightControl.switchConfirmTitle')"
      modal
      :style="{ width: '28rem' }"
      data-testid="switch-confirm-modal"
    >
      <p class="text-slate-700 text-sm mb-4">
        {{ t('notification.flightControl.switchConfirmBody', { clientName }) }}
      </p>
      <template #footer>
        <Button
          :label="t('notification.watchlist.confirmNo')"
          class="p-button-text"
          data-testid="confirm-cancel"
          @click="cancelViewPartner"
        />
        <Button
          :label="t('notification.flightControl.switchToClient')"
          class="p-button-sm"
          data-testid="confirm-switch"
          @click="confirmViewPartner"
        />
      </template>
    </Dialog>
  </div>
</template>
