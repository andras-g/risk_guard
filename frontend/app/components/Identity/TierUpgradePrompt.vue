<script setup lang="ts">
import type { Tier } from '~/composables/auth/useTierGate'

const props = defineProps<{
  requiredTier: Tier
  featureName: string
}>()

const { t } = useI18n()

const tierName = computed(() => t(`common.tiers.${props.requiredTier}`))
</script>

<template>
  <div
    role="region"
    aria-labelledby="tier-gate-title"
    class="tier-upgrade-prompt mx-auto max-w-md rounded-lg border border-slate-200 bg-slate-50 p-6 text-center shadow-sm"
    data-testid="tier-upgrade-prompt"
  >
    <div class="mb-4 flex justify-center">
      <i class="pi pi-lock text-4xl text-slate-400" aria-hidden="true" />
    </div>
    <h2 id="tier-gate-title" class="mb-2 text-xl font-semibold text-slate-800">
      {{ t('common.tierGate.title') }}
    </h2>
    <p class="mb-1 text-sm text-slate-600" data-testid="feature-name">
      {{ t(featureName) }}
    </p>
    <p class="mb-6 text-sm text-slate-500" data-testid="tier-description">
      {{ t('common.tierGate.description', { tier: tierName }) }}
    </p>
    <Button
      :label="t('common.tierGate.cta')"
      severity="info"
      class="w-full"
      data-testid="tier-upgrade-cta"
    />
  </div>
</template>
