// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  future: {
    compatibilityVersion: 4
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
    importTheme: { from: 'assets/themes/risk-guard.ts' }
  },
  i18n: {
    locales: [
      { 
        code: 'hu', 
        name: 'Magyar',
        files: ['i18n/hu/common.json', 'i18n/hu/auth.json', 'i18n/hu/identity.json']
      },
      { 
        code: 'en', 
        name: 'English',
        files: ['i18n/en/common.json', 'i18n/en/auth.json', 'i18n/en/identity.json']
      }
    ],
    lazy: true,
    langDir: '',
    defaultLocale: 'hu',
    strategy: 'no_prefix'
  },
  css: ['assets/css/main.css'],
  runtimeConfig: {
    public: {
      apiBase: process.env.API_BASE || 'http://localhost:8080/api/v1'
    }
  }
})
