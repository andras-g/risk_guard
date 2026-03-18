<script setup lang="ts">
const { t } = useI18n()

const authStore = useAuthStore()

const tier = computed(() => authStore.tier ?? 'ALAP')

const isVisible = computed(() => authStore.isAuthenticated)

const tierLabel = computed(() => t(`common.tiers.${tier.value}`))

const colorClass = computed(() => {
  switch (tier.value) {
    case 'PRO': return 'bg-indigo-600 text-white'
    case 'PRO_EPR': return 'bg-emerald-600 text-white'
    default: return 'bg-slate-500 text-white'
  }
})
</script>

<template>
  <span
    v-if="isVisible"
    :class="['inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', colorClass]"
    data-testid="tier-badge"
  >
    {{ tierLabel }}
  </span>
</template>
