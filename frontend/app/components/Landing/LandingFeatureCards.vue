<script setup lang="ts">
const { t } = useI18n()

const features = [
  {
    key: 'verdicts',
    icon: 'shield',
    titleKey: 'landing.features.verdicts.title',
    descriptionKey: 'landing.features.verdicts.description'
  },
  {
    key: 'speed',
    icon: 'clock',
    titleKey: 'landing.features.speed.title',
    descriptionKey: 'landing.features.speed.description'
  },
  {
    key: 'evidence',
    icon: 'lock',
    titleKey: 'landing.features.evidence.title',
    descriptionKey: 'landing.features.evidence.description'
  }
] as const
</script>

<template>
  <div
    data-testid="feature-grid"
    class="grid gap-8"
    style="grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));"
  >
    <article
      v-for="feature in features"
      :key="feature.key"
      data-testid="feature-card"
      class="rounded-2xl shadow-lg p-8 bg-white"
    >
      <!-- Shield Icon -->
      <svg
        v-if="feature.icon === 'shield'"
        class="w-10 h-10 text-authority mb-4"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>

      <!-- Clock Icon -->
      <svg
        v-if="feature.icon === 'clock'"
        class="w-10 h-10 text-authority mb-4"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <circle
          cx="12"
          cy="12"
          r="10"
        />
        <polyline points="12 6 12 12 16 14" />
      </svg>

      <!-- Lock Icon -->
      <svg
        v-if="feature.icon === 'lock'"
        class="w-10 h-10 text-authority mb-4"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <rect
          x="3"
          y="11"
          width="18"
          height="11"
          rx="2"
          ry="2"
        />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>

      <h3 class="text-lg font-semibold text-authority mb-2">
        {{ t(feature.titleKey) }}
      </h3>
      <p class="text-slate-600 leading-relaxed">
        {{ t(feature.descriptionKey) }}
      </p>
    </article>
  </div>
</template>
