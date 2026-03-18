<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useScreeningStore } from '~/stores/screening'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const screeningStore = useScreeningStore()
const { currentVerdict, currentProvenance, isSearching, searchError } = storeToRefs(screeningStore)

const taxNumber = computed(() => route.params.taxNumber as string)

// Mobile detection: reactive ref that responds to resize events
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

// Single onMounted: fetch data if needed + set up resize listener
onMounted(async () => {
  // Set up responsive mobile detection
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // If verdict is not in store (e.g. direct navigation / page refresh), trigger a search
  if (!currentVerdict.value || currentVerdict.value.taxNumber !== taxNumber.value) {
    await screeningStore.search(taxNumber.value)
  }
})

// Clean up resize listener to prevent memory leak
onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

function goBack() {
  router.push('/dashboard')
}
</script>

<template>
  <div class="max-w-6xl mx-auto px-4 py-6">
    <!-- ─── Breadcrumb / Back Navigation ───────────────────────────────────── -->
    <nav class="mb-4">
      <button
        class="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-800 hover:underline cursor-pointer"
        data-testid="back-to-dashboard"
        @click="goBack"
      >
        <i class="pi pi-arrow-left" />
        {{ t('screening.actions.backToDashboard') }}
      </button>
    </nav>

    <!-- ─── Loading State ─────────────────────────────────────────────────── -->
    <div
      v-if="isSearching"
      class="flex justify-center items-center py-16"
    >
      <ScreeningSkeletonVerdictCard :visible="true" />
    </div>

    <!-- ─── Verdict + Provenance Layout ───────────────────────────────────── -->
    <div
      v-else-if="currentVerdict"
      aria-live="polite"
      aria-atomic="true"
      data-testid="verdict-detail-layout"
    >
      <!-- Desktop: 60/40 two-column layout -->
      <div
        v-if="!isMobile"
        class="flex gap-6"
        data-testid="desktop-layout"
      >
        <!-- VerdictCard: 60% width -->
        <div class="w-3/5">
          <ScreeningVerdictCard :verdict="currentVerdict" />
        </div>
        <!-- ProvenanceSidebar: 40% width -->
        <div class="w-2/5">
          <ScreeningProvenanceSidebar
            :provenance="currentProvenance"
            :confidence="currentVerdict.confidence"
            :mobile="false"
          />
        </div>
      </div>

      <!-- Mobile: single-column, accordion provenance below -->
      <div
        v-else
        class="flex flex-col gap-4"
        data-testid="mobile-layout"
      >
        <!-- VerdictCard full-width -->
        <ScreeningVerdictCard :verdict="currentVerdict" />
        <!-- ProvenanceSidebar as accordion -->
        <ScreeningProvenanceSidebar
          :provenance="currentProvenance"
          :confidence="currentVerdict.confidence"
          :mobile="true"
        />
      </div>

      <!-- ─── Liability Disclaimer ──────────────────────────────────────── -->
      <div
        class="mt-6 text-xs text-slate-500 text-center max-w-2xl mx-auto"
        data-testid="liability-disclaimer"
      >
        {{ t('screening.disclaimer.text') }}
      </div>

      <!-- Screen reader announcement when verdict loads -->
      <span class="sr-only">
        {{ t('screening.verdict.announced', { status: currentVerdict.status }) }}
      </span>
    </div>

    <!-- ─── Search Error State ────────────────────────────────────────────── -->
    <div
      v-else-if="searchError"
      class="py-16 text-center"
      data-testid="verdict-search-error"
    >
      <i class="pi pi-times-circle text-4xl text-rose-400 mb-4 block" />
      <p class="text-lg text-rose-700 mb-2">
        {{ t('screening.verdict.searchFailed') }}
      </p>
      <p class="text-sm text-slate-500 mb-4">
        {{ searchError }}
      </p>
      <button
        type="button"
        class="text-blue-600 hover:underline cursor-pointer"
        @click="goBack"
      >
        {{ t('screening.actions.backToDashboard') }}
      </button>
    </div>

    <!-- ─── Error / Not Found State ───────────────────────────────────────── -->
    <div
      v-else
      class="py-16 text-center text-slate-500"
      data-testid="verdict-not-found"
    >
      <i class="pi pi-exclamation-circle text-4xl text-slate-400 mb-4 block" />
      <p class="text-lg">
        {{ t('screening.verdict.unavailable') }}
      </p>
      <button
        type="button"
        class="mt-4 text-blue-600 hover:underline cursor-pointer"
        @click="goBack"
      >
        {{ t('screening.actions.backToDashboard') }}
      </button>
    </div>
  </div>
</template>
