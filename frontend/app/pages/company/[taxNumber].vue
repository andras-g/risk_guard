<script setup lang="ts">
/**
 * Public SEO gateway stub page for company data.
 * Renders company name, tax number, address (if available),
 * JSON-LD structured data, and a CTA to sign up.
 *
 * This page is:
 * - ISR-rendered (routeRules '/company/**' in nuxt.config.ts)
 * - Unauthenticated (no auth store, no Pinia)
 * - Uses public.vue layout (no sidebar)
 *
 * Story 3.11
 */
import Skeleton from 'primevue/skeleton'
import type { PublicCompanyResponse } from '~/types/api'

definePageMeta({ layout: 'public' })

const { t } = useI18n()
const route = useRoute()
const config = useRuntimeConfig()

const taxNumber = computed(() => route.params.taxNumber as string)

const { data: company, error } = await useAsyncData<PublicCompanyResponse>(
  () => `public-company-${taxNumber.value}`,
  () => $fetch<PublicCompanyResponse>(`/api/v1/public/companies/${taxNumber.value}`, {
    baseURL: config.public.apiBase as string,
    credentials: 'omit',
  })
)

// JSON-LD Organization schema
const jsonLd = computed(() => {
  if (!company.value) return null
  const ld: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    'name': company.value.companyName || taxNumber.value,
    'taxID': taxNumber.value,
    'url': `https://riskguard.hu/company/${taxNumber.value}`,
  }
  if (company.value.address) {
    ld.address = {
      '@type': 'PostalAddress',
      'streetAddress': company.value.address,
    }
  }
  return ld
})

// SEO: page title, meta, canonical URL, and JSON-LD — single useHead() call
useHead({
  title: () => company.value?.companyName
    ? t('screening.company.title', { companyName: company.value.companyName })
    : `${taxNumber.value} — RiskGuard`,
  meta: [
    {
      name: 'description',
      content: () => company.value?.companyName
        ? t('screening.company.description', {
            companyName: company.value.companyName,
            taxNumber: taxNumber.value,
          })
        : `Tax number ${taxNumber.value} — RiskGuard`,
    },
    {
      name: 'robots',
      content: 'index, follow',
    },
  ],
  link: [
    {
      rel: 'canonical',
      href: `https://riskguard.hu/company/${taxNumber.value}`,
    },
  ],
  script: computed(() =>
    jsonLd.value
      ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(jsonLd.value) }]
      : []
  ),
})

const ctaUrl = computed(() => `/auth/login?redirect=/screening/${taxNumber.value}`)
</script>

<template>
  <!-- Company found state -->
  <div
    v-if="company"
    class="max-w-3xl mx-auto px-4 py-12"
    data-testid="public-company-page"
  >
    <!-- Company header -->
    <div class="mb-8">
      <h1
        class="text-3xl font-bold text-slate-900 mb-2"
        data-testid="company-name"
      >
        {{ company.companyName || taxNumber }}
      </h1>

      <div class="flex flex-col gap-2 text-slate-600">
        <p data-testid="company-tax-number">
          <span class="font-medium">{{ t('screening.company.taxNumber') }}:</span>
          {{ company.taxNumber }}
        </p>
        <p
          v-if="company.address"
          data-testid="company-address"
        >
          <span class="font-medium">{{ t('screening.company.address') }}:</span>
          {{ company.address }}
        </p>
      </div>
    </div>

    <!-- Generic status indicator (NOT a verdict) -->
    <div
      class="mb-8 p-4 bg-indigo-50 rounded-lg border border-indigo-100"
      data-testid="generic-indicator"
    >
      <div class="flex items-center gap-3">
        <i class="pi pi-verified text-2xl text-indigo-600" />
        <span class="text-indigo-800 font-medium">
          {{ t('screening.company.genericIndicator') }}
        </span>
      </div>
    </div>

    <!-- CTA section -->
    <div
      class="p-6 bg-gradient-to-r from-indigo-500 to-indigo-700 rounded-xl text-white text-center"
      data-testid="cta-section"
    >
      <h2 class="text-xl font-semibold mb-3">
        {{ t('screening.company.ctaHeading') }}
      </h2>
      <NuxtLink
        :to="ctaUrl"
        class="inline-block px-6 py-3 bg-white text-indigo-700 font-semibold rounded-lg hover:bg-indigo-50 transition-colors"
        data-testid="cta-button"
      >
        {{ t('screening.company.cta') }}
      </NuxtLink>
    </div>
  </div>

  <!-- Not found state (404 from API) -->
  <div
    v-else-if="error"
    class="max-w-3xl mx-auto px-4 py-16 text-center"
    data-testid="company-not-found"
  >
    <i class="pi pi-search text-5xl text-slate-400 mb-4 block" />
    <h1 class="text-2xl font-bold text-slate-900 mb-2">
      {{ t('screening.company.notFound') }}
    </h1>
    <p class="text-slate-600 mb-6">
      {{ t('screening.company.notFoundDescription') }}
    </p>
    <NuxtLink
      to="/"
      class="inline-block px-6 py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 transition-colors"
      data-testid="search-now-link"
    >
      {{ t('screening.actions.searchNow') }}
    </NuxtLink>
  </div>

  <!-- Loading skeleton -->
  <div
    v-else
    class="max-w-3xl mx-auto px-4 py-12"
    data-testid="company-loading"
  >
    <Skeleton
      width="60%"
      height="2rem"
      class="mb-4"
    />
    <Skeleton
      width="40%"
      height="1.5rem"
      class="mb-2"
    />
    <Skeleton
      width="50%"
      height="1.5rem"
      class="mb-8"
    />
    <Skeleton
      width="100%"
      height="4rem"
      class="mb-8"
    />
    <Skeleton
      width="100%"
      height="8rem"
    />
  </div>
</template>
