<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const screeningStore = useScreeningStore()
const { currentVerdict, isSearching, searchError } = storeToRefs(screeningStore)
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.dashboard') }}
    </h1>

    <!-- Search Bar -->
    <ScreeningSearchBar />

    <!-- Skeleton Loading UI — shown while search is pending -->
    <ScreeningSkeletonVerdictCard :visible="isSearching" />

    <!-- Error state -->
    <div
      v-if="searchError"
      class="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700"
    >
      {{ searchError }}
    </div>

    <!-- Verdict result (basic display — full VerdictCard is Story 2.4) -->
    <div
      v-if="currentVerdict && !isSearching"
      class="p-4 border border-slate-200 rounded-lg bg-white"
    >
      <div class="flex items-center gap-3 mb-3">
        <span class="text-lg font-semibold">
          {{ currentVerdict.taxNumber }}
        </span>
        <span
          class="px-2 py-1 rounded text-sm font-medium"
          :class="{
            'bg-gray-100 text-gray-700': currentVerdict.status === 'INCOMPLETE',
            'bg-emerald-100 text-emerald-700': currentVerdict.status === 'RELIABLE',
            'bg-rose-100 text-rose-700': currentVerdict.status === 'AT_RISK' || currentVerdict.status === 'TAX_SUSPENDED',
            'bg-amber-100 text-amber-700': currentVerdict.status === 'UNAVAILABLE'
          }"
        >
          {{ t(`screening.verdict.${currentVerdict.status.toLowerCase()}`) }}
        </span>
      </div>
      <div class="text-sm text-slate-500">
        {{ t('screening.verdict.confidence') }}: {{ t(`screening.verdict.${currentVerdict.confidence.toLowerCase()}`) }}
      </div>
    </div>
  </div>
</template>
