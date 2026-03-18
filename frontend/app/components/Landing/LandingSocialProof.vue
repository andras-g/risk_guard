<script setup lang="ts">
const { t } = useI18n()

const badges = [
  'landing.socialProof.badge.secure',
  'landing.socialProof.badge.realtime',
  'landing.socialProof.badge.compliant'
] as const
</script>

<template>
  <section
    data-testid="social-proof"
    class="text-center"
  >
    <p class="text-lg font-medium text-slate-700 mb-3">
      {{ t('landing.socialProof.trustedBy') }}
    </p>

    <p class="text-2xl font-bold text-authority mb-6">
      {{ t('landing.socialProof.partnerCount') }}
    </p>

    <div class="flex flex-wrap justify-center gap-3">
      <span
        v-for="badge in badges"
        :key="badge"
        class="inline-flex items-center px-4 py-2 rounded-full bg-slate-100 text-sm font-medium text-slate-700"
      >
        {{ t(badge) }}
      </span>
    </div>
  </section>
</template>
