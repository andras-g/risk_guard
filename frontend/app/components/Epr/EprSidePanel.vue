<script setup lang="ts">
const { t } = useI18n()

defineProps<{
  totalCount: number
  filingReadyCount: number
  oneTimeCount: number
}>()
</script>

<template>
  <aside
    class="hidden lg:block w-72 shrink-0 rounded-lg border border-slate-200 bg-white p-5"
    data-testid="epr-side-panel"
  >
    <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-4">
      {{ t('epr.materialLibrary.sidePanel.title') }}
    </h3>

    <div class="flex flex-col gap-4">
      <!-- Total Templates -->
      <div class="flex items-center justify-between">
        <span class="text-sm text-slate-600">{{ t('epr.materialLibrary.sidePanel.total') }}</span>
        <span class="text-lg font-bold text-slate-800" data-testid="total-count">{{ totalCount }}</span>
      </div>

      <div class="border-t border-slate-100" />

      <!-- Filing-Ready -->
      <div class="flex items-center justify-between">
        <span class="text-sm text-slate-600">{{ t('epr.materialLibrary.sidePanel.filingReady') }}</span>
        <span class="text-lg font-bold text-emerald-700" data-testid="verified-count">{{ filingReadyCount }}</span>
      </div>

      <!-- One-time -->
      <div class="flex items-center justify-between">
        <span class="text-sm text-slate-600">{{ t('epr.materialLibrary.sidePanel.oneTime') }}</span>
        <span class="text-lg font-bold text-amber-700" data-testid="one-time-count">{{ oneTimeCount }}</span>
      </div>
    </div>
  </aside>
</template>
