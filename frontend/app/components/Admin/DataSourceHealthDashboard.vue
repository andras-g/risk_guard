<script setup lang="ts">
import Card from 'primevue/card'
import ProgressBar from 'primevue/progressbar'
import Skeleton from 'primevue/skeleton'
import type { AdapterHealth } from '~/stores/health'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const { t } = useI18n()
const { formatRelative } = useDateRelative()

const props = defineProps<{
  adapters: AdapterHealth[]
  loading: boolean
}>()

// --- ARIA live region ---
const ariaStatusMessage = ref('')
const previousStates = ref<Record<string, string>>({})

watch(
  () => props.adapters,
  (newAdapters) => {
    const messages: string[] = []
    for (const adapter of newAdapters) {
      const prev = previousStates.value[adapter.adapterName]
      if (prev && prev !== adapter.circuitBreakerState) {
        messages.push(
          t('admin.datasources.a11y.statusChanged', {
            adapter: adapter.adapterName,
            state: adapter.circuitBreakerState,
          })
        )
      }
      previousStates.value[adapter.adapterName] = adapter.circuitBreakerState
    }
    if (messages.length > 0) {
      ariaStatusMessage.value = messages.join('. ')
      setTimeout(() => {
        ariaStatusMessage.value = ''
      }, 3000)
    }
  },
  { deep: true, immediate: true }
)

// --- Badge helpers ---
function cbStateBadgeClass(state: string): string {
  switch (state) {
    case 'CLOSED': return 'bg-emerald-100 text-emerald-800'
    case 'HALF_OPEN': return 'bg-amber-100 text-amber-800'
    case 'OPEN': return 'bg-red-100 text-red-800'
    case 'DISABLED': return 'bg-slate-100 text-slate-600'
    default: return 'bg-slate-100 text-slate-600'
  }
}

function cbStateBadgeLabel(state: string): string {
  switch (state) {
    case 'CLOSED': return t('admin.datasources.states.healthy')
    case 'HALF_OPEN': return t('admin.datasources.states.degraded')
    case 'OPEN': return t('admin.datasources.states.circuitOpen')
    case 'DISABLED': return t('admin.datasources.states.disabled')
    default: return state
  }
}

function modeBadgeClass(mode: string): string {
  switch (mode) {
    case 'DEMO': return 'bg-indigo-100 text-indigo-800'
    case 'TEST': return 'bg-amber-100 text-amber-800'
    case 'LIVE': return 'bg-emerald-100 text-emerald-800'
    default: return 'bg-slate-100 text-slate-600'
  }
}

function credentialBadgeClass(status: string): string {
  switch (status) {
    case 'VALID': return 'bg-emerald-100 text-emerald-800'
    case 'EXPIRED': return 'bg-red-100 text-red-800'
    case 'MISSING': return 'bg-amber-100 text-amber-800'
    case 'NOT_CONFIGURED': return 'bg-slate-100 text-slate-600'
    default: return 'bg-slate-100 text-slate-600'
  }
}

function successRateBarClass(pct: number): string {
  if (pct >= 90) return 'text-emerald-600'
  if (pct >= 60) return 'text-amber-600'
  return 'text-red-600'
}
</script>

<template>
  <!-- ARIA live region: announces circuit breaker state changes to screen readers -->
  <div aria-live="polite" aria-atomic="true" class="sr-only">{{ ariaStatusMessage }}</div>

  <!-- Loading skeleton grid -->
  <div v-if="loading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
    <div v-for="i in 3" :key="i" class="p-4 border rounded-lg bg-white shadow-sm">
      <Skeleton height="1.5rem" class="mb-3" />
      <Skeleton height="1rem" class="mb-2" />
      <Skeleton height="1rem" class="mb-2" />
      <Skeleton height="1rem" />
    </div>
  </div>

  <!-- Adapter cards grid -->
  <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
    <Card v-for="adapter in adapters" :key="adapter.adapterName" class="shadow-sm">
      <template #title>
        <div class="flex items-center justify-between gap-2">
          <span class="font-semibold text-slate-800 capitalize">{{ adapter.adapterName }}</span>
          <!-- Demo Mode banner -->
          <span
            v-if="adapter.dataSourceMode === 'DEMO'"
            class="text-xs font-medium px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-800"
          >
            {{ t('admin.datasources.states.demoMode') }}
          </span>
        </div>
      </template>

      <template #content>
        <div class="flex flex-col gap-3 text-sm">
          <!-- Circuit Breaker State -->
          <div class="flex justify-between items-center">
            <span class="text-slate-500">{{ t('admin.datasources.adapterCard.circuitBreaker') }}</span>
            <span
              :class="['text-xs font-medium px-2 py-0.5 rounded-full', cbStateBadgeClass(adapter.circuitBreakerState)]"
            >
              {{ cbStateBadgeLabel(adapter.circuitBreakerState) }}
            </span>
          </div>

          <!-- Success Rate -->
          <div>
            <div class="flex justify-between items-center mb-1">
              <span class="text-slate-500">{{ t('admin.datasources.adapterCard.successRate') }}</span>
              <span :class="['font-semibold', successRateBarClass(adapter.successRatePct)]">
                {{ adapter.successRatePct.toFixed(1) }}%
              </span>
            </div>
            <ProgressBar :value="adapter.successRatePct" :show-value="false" class="h-2" />
          </div>

          <!-- MTBF -->
          <div class="flex justify-between items-center">
            <span class="text-slate-500">{{ t('admin.datasources.adapterCard.mtbf') }}</span>
            <span class="text-slate-700">
              {{ adapter.mtbfHours != null ? adapter.mtbfHours.toFixed(1) : '—' }}
            </span>
          </div>

          <!-- Last Success -->
          <div class="flex justify-between items-center">
            <span class="text-slate-500">{{ t('admin.datasources.adapterCard.lastSuccess') }}</span>
            <span class="text-slate-700">
              {{ adapter.lastSuccessAt ? formatRelative(adapter.lastSuccessAt) : '—' }}
            </span>
          </div>

          <!-- Credential Status -->
          <div class="flex justify-between items-center">
            <span class="text-slate-500">{{ t('admin.datasources.adapterCard.credential') }}</span>
            <span
              v-if="adapter.credentialStatus === 'NOT_CONFIGURED'"
              class="text-xs font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-600"
            >
              {{ t('admin.datasources.states.notConfigured') }}
            </span>
            <span
              v-else
              :class="['text-xs font-medium px-2 py-0.5 rounded-full', credentialBadgeClass(adapter.credentialStatus)]"
            >
              {{ adapter.credentialStatus }}
            </span>
          </div>

          <!-- Data Source Mode -->
          <div class="flex justify-between items-center">
            <span class="text-slate-500">{{ t('admin.datasources.adapterCard.mode') }}</span>
            <span :class="['text-xs font-medium px-2 py-0.5 rounded-full', modeBadgeClass(adapter.dataSourceMode)]">
              {{ adapter.dataSourceMode }}
            </span>
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>
