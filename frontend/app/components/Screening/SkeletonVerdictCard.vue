<script setup lang="ts">
import Skeleton from 'primevue/skeleton'

const { t } = useI18n()

defineProps<{
  visible: boolean
}>()

const sources = computed(() => [
  { key: 'navDebt', label: t('screening.sources.navDebt') },
  { key: 'legalStatus', label: t('screening.sources.legalStatus') },
  { key: 'companyRegistry', label: t('screening.sources.companyRegistry') }
])
</script>

<template>
  <div
    v-if="visible"
    class="flex flex-col gap-4 p-4 border border-slate-200 rounded-lg bg-white"
    role="status"
    aria-busy="true"
    data-testid="skeleton-verdict-card"
  >
    <span class="sr-only">{{ t('screening.search.searching') }}</span>
    <!-- Header skeleton -->
    <div class="flex items-center gap-3">
      <Skeleton
        shape="circle"
        size="3rem"
      />
      <div class="flex-1">
        <Skeleton
          width="60%"
          height="1.25rem"
        />
        <Skeleton
          width="40%"
          height="0.75rem"
          class="mt-2"
        />
      </div>
    </div>

    <!-- Source resolution placeholders -->
    <div class="flex flex-col gap-3 mt-2">
      <div
        v-for="source in sources"
        :key="source.key"
        class="flex items-center gap-3"
      >
        <span class="text-sm text-slate-500 w-40 shrink-0">
          <i class="pi pi-circle text-[10px] mr-1" />{{ source.label }}
        </span>
        <Skeleton
          width="100%"
          height="1rem"
        />
      </div>
    </div>

    <!-- Verdict skeleton -->
    <div class="flex gap-3 mt-2">
      <Skeleton
        width="30%"
        height="2rem"
      />
      <Skeleton
        width="20%"
        height="2rem"
      />
    </div>
  </div>
</template>
