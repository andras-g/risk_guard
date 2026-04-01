<script setup lang="ts">
import type { WatchlistEntryResponse } from '~/types/api'

const props = defineProps<{
  entries: WatchlistEntryResponse[]
  isLoading: boolean
}>()

const { t } = useI18n()

const reliableCount = computed(() =>
  props.entries.filter(e => e.currentVerdictStatus === 'RELIABLE').length,
)

const atRiskCount = computed(() =>
  props.entries.filter(e =>
    e.currentVerdictStatus === 'AT_RISK'
    || e.currentVerdictStatus === 'TAX_SUSPENDED'
    || e.currentVerdictStatus === 'INCOMPLETE',
  ).length,
)

const staleCount = computed(() =>
  props.entries.filter(e => e.currentVerdictStatus === 'UNAVAILABLE').length,
)
</script>

<template>
  <div class="flex flex-wrap gap-4" data-testid="stat-bar">
    <!-- Loading skeletons -->
    <template v-if="isLoading">
      <Skeleton
        v-for="i in 3"
        :key="i"
        width="180px"
        height="72px"
        class="rounded-lg"
      />
    </template>

    <!-- Stat cards -->
    <template v-else>
      <!-- Reliable -->
      <div
        class="flex flex-col items-center justify-center px-6 py-4 rounded-lg border-2 border-emerald-500 bg-emerald-50 min-w-[180px]"
        data-testid="stat-reliable"
      >
        <span class="text-3xl font-bold text-emerald-700">{{ reliableCount }}</span>
        <span class="text-sm font-medium text-emerald-700 mt-1">{{ t('dashboard.statReliable') }}</span>
      </div>

      <!-- At Risk -->
      <div
        class="flex flex-col items-center justify-center px-6 py-4 rounded-lg border-2 border-red-700 bg-red-50 min-w-[180px]"
        data-testid="stat-at-risk"
      >
        <span class="text-3xl font-bold text-red-700">{{ atRiskCount }}</span>
        <span class="text-sm font-medium text-red-700 mt-1">{{ t('dashboard.statAtRisk') }}</span>
      </div>

      <!-- Stale / Unavailable -->
      <div
        class="flex flex-col items-center justify-center px-6 py-4 rounded-lg border-2 border-amber-500 bg-amber-50 min-w-[180px]"
        data-testid="stat-stale"
      >
        <span class="text-3xl font-bold text-amber-600">{{ staleCount }}</span>
        <span class="text-sm font-medium text-amber-600 mt-1">{{ t('dashboard.statStale') }}</span>
      </div>
    </template>
  </div>
</template>
