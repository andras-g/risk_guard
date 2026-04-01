<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { useAuthStore } from '~/stores/auth'
import { useWatchlistStore } from '~/stores/watchlist'
import { usePortfolioStore } from '~/stores/portfolio'

const { t } = useI18n()
const router = useRouter()
const screeningStore = useScreeningStore()
const authStore = useAuthStore()
const watchlistStore = useWatchlistStore()
const portfolioStore = usePortfolioStore()

const currentVerdict = computed(() => screeningStore.currentVerdict)
const isSearching = computed(() => screeningStore.isSearching)
const searchError = computed(() => screeningStore.searchError)
const watchlistEntries = computed(() => watchlistStore.entries)
const watchlistLoading = computed(() => watchlistStore.isLoading)
const alerts = computed(() => portfolioStore.alerts)
const alertsLoading = computed(() => portfolioStore.isLoading)

const isAccountant = computed(() => authStore.isAccountant)

// Clear any previous verdict when the dashboard mounts so the watcher below
// does not immediately redirect back to the detail page if the user navigated here
// from /screening/[taxNumber] and the store still holds the previous result.
onMounted(async () => {
  screeningStore.clearSearch()

  // Redirect accountants to flight-control — this guard was not present before Story 7.1.
  if (isAccountant.value) {
    router.push('/flight-control')
    return
  }

  await Promise.all([
    watchlistStore.fetchEntries(),
    portfolioStore.fetchAlerts(7),
  ])
})

// Navigate to Verdict Detail page as soon as a verdict arrives.
// Do NOT gate on !isSearching — the store sets currentVerdict before fetchProvenance()
// completes, so isSearching is still true when this watcher fires. Gating on it means
// navigation never happens. The verdict detail page handles a still-loading provenance
// gracefully (ProvenanceSidebar renders with null provenance until the store updates).
watch(currentVerdict, (verdict) => {
  if (verdict) {
    router.push(`/screening/${verdict.taxNumber}`)
  }
})
</script>

<template>
  <div v-if="!isAccountant" class="flex flex-col gap-6 p-6 max-w-5xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.dashboard') }}
    </h1>

    <!-- Empty watchlist hint (Story 7.5 replaces with onboarding component) -->
    <p
      v-if="watchlistEntries.length === 0 && !watchlistLoading"
      class="text-slate-400 text-sm"
      data-testid="empty-watchlist-hint"
    >
      {{ t('dashboard.emptyWatchlistHint') }}
    </p>

    <!-- Stat bar -->
    <DashboardStatBar
      :entries="watchlistEntries"
      :is-loading="watchlistLoading"
    />

    <!-- Two-column: attention list (60%) + alert feed (40%) -->
    <div class="grid grid-cols-1 md:grid-cols-5 gap-6">
      <!-- Needs Attention — 3/5 columns (~60%) -->
      <div class="md:col-span-3">
        <DashboardNeedsAttention
          :entries="watchlistEntries"
          :is-loading="watchlistLoading"
        />
      </div>

      <!-- Recent Status Changes — 2/5 columns (~40%) -->
      <div class="md:col-span-2">
        <DashboardAlertFeed
          :alerts="alerts"
          :is-loading="alertsLoading"
        />
      </div>
    </div>

    <!-- Search Bar — always visible, not blocked by loading -->
    <ScreeningSearchBar />

    <!-- Skeleton Loading UI — shown while search is pending -->
    <ScreeningSkeletonVerdictCard :visible="isSearching" />

    <!-- Error state -->
    <div
      v-if="searchError"
      class="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700"
      data-testid="search-error"
    >
      {{ searchError }}
    </div>
  </div>
</template>
