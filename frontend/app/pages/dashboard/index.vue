<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { useIdentityStore } from '~/stores/identity'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const router = useRouter()
const screeningStore = useScreeningStore()
const identityStore = useIdentityStore()
const { currentVerdict, isSearching, searchError } = storeToRefs(screeningStore)
const isAccountant = computed(() => identityStore.user?.role === 'ACCOUNTANT')

// Clear any previous verdict when the dashboard mounts so the watcher below
// does not immediately redirect back to the detail page if the user navigated here
// from /screening/[taxNumber] and the store still holds the previous result.
onMounted(() => {
  screeningStore.clearSearch()
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
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.dashboard') }}
    </h1>

    <!-- Search Bar -->
    <ScreeningSearchBar />

    <!-- Portfolio Pulse — accountant-only cross-tenant alert feed (Story 3.9) -->
    <NotificationPortfolioPulse v-if="isAccountant" />

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

    <!-- After search completes, navigation to /screening/[taxNumber] happens via watcher -->
  </div>
</template>
