// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  future: {
    compatibilityVersion: 4
  },

  // Nuxt Hybrid Rendering — SEO stubs for /company/[taxNumber] use ISR (AC: 3)
  // All other routes use SPA/client-side rendering.
  routeRules: {
    '/company/**': { isr: true },   // ISR: cached on CDN, revalidates in background
  },

  modules: [
    '@primevue/nuxt-module',
    '@pinia/nuxt',
    '@nuxtjs/i18n'
  ],
  primevue: {
    options: {
      ripple: true
    },
    importTheme: { from: '~/assets/themes/risk-guard.ts' }
  },
  i18n: {
    locales: [
      { 
        code: 'hu', 
        name: 'Magyar',
        files: ['hu/common.json', 'hu/auth.json', 'hu/identity.json', 'hu/screening.json']
      },
      { 
        code: 'en', 
        name: 'English',
        files: ['en/common.json', 'en/auth.json', 'en/identity.json', 'en/screening.json']
      }
    ],
    lazy: false,
    langDir: 'app/i18n',
    restructureDir: '',
    defaultLocale: 'hu',
    strategy: 'no_prefix'
  },
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    public: {
      // Default for local dev. Overridden at build time via NUXT_PUBLIC_API_BASE env var
      // (e.g. NUXT_PUBLIC_API_BASE=https://risk-guard-backend-staging-xxx.run.app in CI/deploy.yml).
      // Nuxt automatically maps NUXT_PUBLIC_<KEY> → runtimeConfig.public.<key>.
      // Do NOT use process.env.API_BASE — that env var is never set and would bake in localhost.
      apiBase: 'http://localhost:8080'
    }
  }
})
