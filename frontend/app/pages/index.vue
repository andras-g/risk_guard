<script setup lang="ts">
definePageMeta({ layout: 'public' })

const { t } = useI18n()
const authStore = useAuthStore()

// AC7: Lightweight health check for service availability
const serviceUnavailable = ref(false)

// AC5 + AC7: Combined mounted hook — auth redirect first, then health check.
// Avoids a navigation race where the health check response arrives for an unmounting component.
onMounted(async () => {
  // AC5: If authenticated, redirect to dashboard and skip health check
  if (!authStore.isAuthenticated) {
    await authStore.initializeAuth()
  }
  if (authStore.isAuthenticated) {
    navigateTo('/dashboard')
    return // Skip health check — component is unmounting
  }

  // AC7: Only run health check for unauthenticated visitors who will see the landing page
  try {
    const config = useRuntimeConfig()
    await $fetch('/actuator/health', {
      baseURL: config.public.apiBase as string,
      timeout: 5000
    })
  } catch {
    serviceUnavailable.value = true
  }
})

// AC4: SEO meta tags — use getter functions for reactive locale-aware values.
// When the locale switches (hu ↔ en), Nuxt re-evaluates the getters and updates <head>.
useSeoMeta({
  title: () => t('landing.seo.title'),
  description: () => t('landing.seo.description'),
  ogTitle: () => t('landing.seo.title'),
  ogDescription: () => t('landing.seo.description'),
  ogType: 'website',
  ogUrl: '/'
})

// AC4: JSON-LD Organization schema — computed so it re-serializes on locale change.
const jsonLd = computed(() => JSON.stringify({
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: 'RiskGuard',
  url: '/',
  description: t('landing.seo.description')
}))

useHead({
  script: [
    {
      type: 'application/ld+json',
      innerHTML: jsonLd
    }
  ]
})
</script>

<template>
  <div>
    <!-- Hero Section -->
    <section class="py-16 px-6 text-center">
      <div class="max-w-3xl mx-auto">
        <h1 class="text-4xl font-bold text-authority mb-4">
          {{ t('landing.hero.headline') }}
        </h1>
        <p class="text-lg text-slate-600 mb-10 max-w-prose mx-auto">
          {{ t('landing.hero.tagline') }}
        </p>

        <LandingSearchBar :service-unavailable="serviceUnavailable" />
      </div>
    </section>

    <!-- Feature Cards Section -->
    <section class="py-12 px-6">
      <div class="max-w-6xl mx-auto">
        <LandingFeatureCards />
      </div>
    </section>

    <!-- Social Proof Section -->
    <section class="py-12 px-6">
      <div class="max-w-4xl mx-auto">
        <LandingSocialProof />
      </div>
    </section>

    <!-- Footer Disclaimer -->
    <section class="py-8 px-6 text-center">
      <p class="text-sm text-secondary-text max-w-prose mx-auto">
        {{ t('landing.disclaimer') }}
      </p>
    </section>
  </div>
</template>
