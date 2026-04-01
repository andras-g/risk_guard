<script setup lang="ts">
import type { PortfolioAlertResponse } from '~/types/api'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import { useStatusColor } from '~/composables/formatting/useStatusColor'

const props = defineProps<{
  alerts: PortfolioAlertResponse[]
  isLoading: boolean
}>()

const { t } = useI18n()
const { formatRelative } = useDateRelative()
const { statusColorClass, statusI18nKey } = useStatusColor()

const recentAlerts = computed(() => props.alerts.slice(0, 5))
</script>

<template>
  <div data-testid="alert-feed">
    <h2 class="text-lg font-semibold text-slate-800 mb-3">
      {{ t('dashboard.recentChanges') }}
    </h2>

    <!-- Loading skeletons -->
    <template v-if="isLoading">
      <div
        v-for="i in 3"
        :key="i"
        class="mb-2 pl-3 border-l-4 border-slate-200"
      >
        <Skeleton width="100%" height="48px" class="rounded" />
      </div>
    </template>

    <!-- Alert items -->
    <template v-else>
      <div
        v-if="recentAlerts.length === 0"
        class="text-slate-400 text-sm"
        data-testid="no-alerts"
      >
        {{ t('dashboard.noRecentChanges') }}
      </div>

      <div
        v-for="alert in recentAlerts"
        :key="alert.alertId"
        class="mb-3 pl-3 border-l-4 py-2"
        :class="statusColorClass(alert.newStatus)"
        data-testid="alert-item"
      >
        <div class="flex flex-col gap-0.5">
          <span class="font-semibold text-slate-800 text-sm">{{ alert.companyName ?? alert.taxNumber }}</span>
          <span class="text-xs text-slate-500">
            {{ t(statusI18nKey(alert.previousStatus)) }} → {{ t(statusI18nKey(alert.newStatus)) }}
          </span>
          <span class="text-xs text-slate-400">{{ formatRelative(alert.changedAt) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>
