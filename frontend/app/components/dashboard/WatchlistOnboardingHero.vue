<script setup lang="ts">
import { useWatchlistStore } from '~/stores/watchlist'

const { t } = useI18n()
const watchlistStore = useWatchlistStore()

const emit = defineEmits<{
  'focus-search': []
}>()

const addDialogVisible = ref(false)

async function handleAddSubmit(taxNumber: string, companyName: string | null, verdictStatus: string | null) {
  try {
    const { duplicate } = await watchlistStore.addEntry(taxNumber, companyName, verdictStatus)
    if (!duplicate) {
      // addEntry already called fetchEntries internally; reactive entries update
      // will trigger the v-else-if branch in dashboard/index.vue automatically
      addDialogVisible.value = false
    }
  }
  catch {
    // Network error — leave dialog open; watchlistStore.error is set
  }
}

const steps = [
  { icon: 'pi-plus-circle', titleKey: 'dashboard.howItWorksStep1Title', bodyKey: 'dashboard.howItWorksStep1Body' },
  { icon: 'pi-sync', titleKey: 'dashboard.howItWorksStep2Title', bodyKey: 'dashboard.howItWorksStep2Body' },
  { icon: 'pi-bell', titleKey: 'dashboard.howItWorksStep3Title', bodyKey: 'dashboard.howItWorksStep3Body' },
]
</script>

<template>
  <div class="flex flex-col gap-8" data-testid="onboarding-hero">
    <!-- Muted stat bar preview -->
    <div class="opacity-40 pointer-events-none select-none">
      <DashboardStatBar :entries="[]" :is-loading="false" />
    </div>

    <!-- Hero heading and CTAs -->
    <div class="flex flex-col items-center gap-4 text-center py-6">
      <h2 class="text-2xl font-bold text-slate-800" data-testid="onboarding-title">
        {{ t('dashboard.onboardingTitle') }}
      </h2>
      <p class="text-slate-500 max-w-md" data-testid="onboarding-subtitle">
        {{ t('dashboard.onboardingSubtitle') }}
      </p>

      <div class="flex flex-wrap gap-3 justify-center mt-2">
        <Button
          :label="t('dashboard.addFirstPartner')"
          icon="pi pi-plus"
          data-testid="add-first-partner-btn"
          @click="addDialogVisible = true"
        />
        <Button
          :label="t('dashboard.searchByTaxNumber')"
          icon="pi pi-search"
          severity="secondary"
          outlined
          data-testid="search-by-tax-btn"
          @click="emit('focus-search')"
        />
      </div>
    </div>

    <!-- How-it-works strip -->
    <div class="flex flex-wrap gap-4 justify-center" data-testid="how-it-works-strip">
      <div
        v-for="step in steps"
        :key="step.titleKey"
        class="flex flex-col items-center gap-2 text-center max-w-[180px]"
        data-testid="how-it-works-step"
      >
        <i :class="`pi ${step.icon} text-3xl text-primary`" />
        <span class="font-semibold text-slate-700 text-sm">{{ t(step.titleKey) }}</span>
        <span class="text-slate-500 text-xs">{{ t(step.bodyKey) }}</span>
      </div>
    </div>

    <!-- WatchlistAddDialog embedded -->
    <WatchlistAddDialog
      v-model:visible="addDialogVisible"
      @submit="handleAddSubmit"
    />
  </div>
</template>
