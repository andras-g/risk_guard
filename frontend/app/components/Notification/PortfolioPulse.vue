<script setup lang="ts">
import { usePortfolioStore } from '~/stores/portfolio'
import { useIdentityStore } from '~/stores/identity'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import { useStatusColor } from '~/composables/formatting/useStatusColor'
import { useApiError } from '~/composables/api/useApiError'
import { storeToRefs } from 'pinia'
import type { PortfolioAlertResponse } from '~/types/api'

const { t } = useI18n()
const router = useRouter()
const portfolioStore = usePortfolioStore()
const identityStore = useIdentityStore()
const { alerts, isLoading, error } = storeToRefs(portfolioStore)
const { formatRelative } = useDateRelative()
const { statusColorClass, statusIconClass, statusI18nKey } = useStatusColor()
const { mapErrorType } = useApiError()
const toast = useToast()

onMounted(() => {
  portfolioStore.fetchAlerts()
})

/**
 * Get the localized status change text using i18n keys.
 * Uses existing screening.verdict.* keys for status labels per AC8.
 */
function localizedStatusChange(previousStatus: string | null, newStatus: string | null): string {
  const prev = t(statusI18nKey(previousStatus))
  const next = t(statusI18nKey(newStatus))
  return t('notification.portfolio.statusChange', { previous: prev, new: next })
}

/**
 * Extract RFC 7807 error type from a caught error object.
 * FetchError from $fetch includes response data with type field.
 */
function extractErrorType(error: unknown): string | undefined {
  if (error && typeof error === 'object') {
    const fetchError = error as { data?: { type?: string } }
    if (fetchError.data?.type) {
      return fetchError.data.type
    }
  }
  return undefined
}

/**
 * Handle click on a portfolio alert item.
 * Switches tenant context (if needed) then navigates to the partner's verdict detail page.
 */
async function handleAlertClick(alert: PortfolioAlertResponse) {
  try {
    const currentTenantId = identityStore.user?.activeTenantId
    if (currentTenantId !== alert.tenantId) {
      await identityStore.switchTenant(alert.tenantId)
    }
    router.push(`/screening/${alert.taxNumber}`)
  }
  catch (error: unknown) {
    toast.add({
      severity: 'error',
      summary: mapErrorType(extractErrorType(error)),
      life: 5000,
    })
  }
}
</script>

<template>
  <section
    class="bg-white rounded-lg border border-slate-200 p-4"
    data-testid="portfolio-pulse"
  >
    <h2 class="text-lg font-semibold text-slate-800 mb-3">
      {{ t('notification.portfolio.title') }}
    </h2>

    <!-- Loading state -->
    <div v-if="isLoading" class="space-y-2">
      <div v-for="i in 3" :key="i" class="h-16 bg-slate-100 rounded animate-pulse" />
    </div>

    <!-- Error state — fetch failed (network error, 403, 500, etc.) -->
    <div
      v-else-if="error"
      class="flex flex-col items-center py-6 text-center"
      data-testid="portfolio-pulse-error"
    >
      <i class="pi pi-exclamation-circle text-4xl text-red-400 mb-3" />
      <p class="text-slate-600 font-medium">
        {{ t('common.states.error') }}
      </p>
      <button
        class="mt-3 text-sm text-blue-600 hover:underline"
        @click="portfolioStore.fetchAlerts()"
      >
        {{ t('common.actions.retry') }}
      </button>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="alerts.length === 0"
      class="flex flex-col items-center py-6 text-center"
      data-testid="portfolio-pulse-empty"
    >
      <i class="pi pi-wifi text-4xl text-slate-300 mb-3" />
      <p class="text-slate-600 font-medium">
        {{ t('notification.portfolio.emptyTitle') }}
      </p>
      <p class="text-slate-400 text-sm mt-1">
        {{ t('notification.portfolio.emptyBody') }}
      </p>
    </div>

    <!-- Alert feed -->
    <ul v-else class="space-y-2 max-h-96 overflow-y-auto">
      <li
        v-for="alert in alerts.slice(0, 20)"
        :key="alert.alertId"
        class="flex items-start gap-3 p-3 rounded-md border-l-4 cursor-pointer hover:bg-slate-50 transition-colors"
        :class="statusColorClass(alert.newStatus)"
        data-testid="portfolio-pulse-item"
        @click="handleAlertClick(alert)"
      >
        <i
          :class="statusIconClass(alert.newStatus)"
          class="text-lg mt-0.5 shrink-0"
        />
        <div class="flex-1 min-w-0">
          <p class="text-xs text-slate-500 font-medium truncate">
            {{ alert.tenantName }}
          </p>
          <p class="text-sm font-semibold text-slate-800 truncate">
            {{ alert.companyName }}
          </p>
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
