<script setup lang="ts">
import type { WatchlistEntryResponse } from '~/types/api'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const props = defineProps<{
  entries: WatchlistEntryResponse[]
  isLoading: boolean
}>()

const { t } = useI18n()
const { formatRelative } = useDateRelative()

// Priority order: AT_RISK=0, TAX_SUSPENDED=1, INCOMPLETE=2, UNAVAILABLE=3
const PRIORITY: Record<string, number> = {
  AT_RISK: 0,
  TAX_SUSPENDED: 1,
  INCOMPLETE: 2,
  UNAVAILABLE: 3,
}

// PrimeVue Tag severity per status (useStatusColor not used — see Dev Notes)
const STATUS_SEVERITY: Record<string, string> = {
  AT_RISK: 'danger',
  TAX_SUSPENDED: 'danger',
  INCOMPLETE: 'warn',
  UNAVAILABLE: 'secondary',
}

const attentionEntries = computed(() => {
  return props.entries
    .filter(e =>
      e.currentVerdictStatus === 'AT_RISK'
      || e.currentVerdictStatus === 'TAX_SUSPENDED'
      || e.currentVerdictStatus === 'INCOMPLETE'
      || e.currentVerdictStatus === 'UNAVAILABLE',
    )
    .sort((a, b) => {
      const pa = PRIORITY[a.currentVerdictStatus ?? ''] ?? 99
      const pb = PRIORITY[b.currentVerdictStatus ?? ''] ?? 99
      return pa - pb
    })
    .slice(0, 10)
})
</script>

<template>
  <div data-testid="needs-attention">
    <h2 class="text-lg font-semibold text-slate-800 mb-3">
      {{ t('dashboard.needsAttention') }}
    </h2>

    <!-- Loading skeletons -->
    <template v-if="isLoading">
      <div
        v-for="i in 3"
        :key="i"
        class="flex items-center gap-3 p-3 mb-2 rounded-lg bg-slate-50"
      >
        <Skeleton width="100%" height="40px" class="rounded" />
      </div>
    </template>

    <!-- Entry rows -->
    <template v-else>
      <div
        v-for="entry in attentionEntries"
        :key="entry.id"
        class="flex items-center justify-between p-3 mb-2 rounded-lg bg-slate-50 hover:bg-slate-100 transition-colors"
        data-testid="attention-row"
      >
        <div class="flex flex-col gap-1 min-w-0">
          <span class="font-bold text-slate-800 truncate">{{ entry.companyName ?? entry.taxNumber }}</span>
          <span class="font-mono text-xs text-slate-500">{{ entry.taxNumber }}</span>
        </div>
        <div class="flex items-center gap-3 flex-shrink-0 ml-3">
          <Tag
            :severity="STATUS_SEVERITY[entry.currentVerdictStatus ?? ''] ?? 'secondary'"
            :value="entry.currentVerdictStatus ?? ''"
            class="text-xs"
          />
          <span class="text-xs text-slate-400">{{ formatRelative(entry.lastCheckedAt) }}</span>
          <NuxtLink
            :to="`/screening/${entry.taxNumber}`"
            class="text-slate-400 hover:text-slate-700 transition-colors"
            data-testid="attention-row-link"
          >
            <i class="pi pi-arrow-right" />
          </NuxtLink>
        </div>
      </div>

      <p
        v-if="attentionEntries.length === 0"
        class="text-slate-400 text-sm"
        data-testid="no-attention"
      >
        —
      </p>
    </template>
  </div>
</template>
