<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const router = useRouter()
const screeningStore = useScreeningStore()
const { currentVerdict, isSearching, searchError } = storeToRefs(screeningStore)

onMounted(() => {
  screeningStore.clearSearch()
})

watch(currentVerdict, (verdict) => {
  if (verdict) {
    router.push(`/screening/${verdict.taxNumber}`)
  }
})
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.screening') }}
    </h1>

    <ScreeningSearchBar />

    <ScreeningSkeletonVerdictCard :visible="isSearching" />

    <div
      v-if="searchError"
      class="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700"
      data-testid="search-error"
    >
      {{ searchError }}
    </div>
  </div>
</template>
