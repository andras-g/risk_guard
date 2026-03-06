// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
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
        files: ['hu/common.json', 'hu/auth.json', 'hu/identity.json']
      },
      { 
        code: 'en', 
        name: 'English',
        files: ['en/common.json', 'en/auth.json', 'en/identity.json']
      }
    ],
    lazy: true,
    langDir: 'i18n/',
    defaultLocale: 'hu',
    strategy: 'no_prefix'
  },
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    public: {
      apiBase: process.env.API_BASE || 'http://localhost:8080/api/v1'
    }
  }
})
