import tailwindcss from '@tailwindcss/vite'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  future: {
    compatibilityVersion: 4
  },
  vite: {
    plugins: [tailwindcss()],
    // Speed up dev startup: skip full-bundle optimisation on cold start
    optimizeDeps: {
      exclude: ['monaco-editor'],
      include: [
        'primevue/datatable', 'primevue/column', 'primevue/button',
        'primevue/dialog', 'primevue/inputtext', 'primevue/tag',
        'primevue/skeleton', 'primevue/badge', 'primevue/confirmdialog',
        'primevue/usetoast', 'primevue/useconfirm',
        'primevue/stepper', 'primevue/steplist', 'primevue/steppanels',
        'primevue/step', 'primevue/steppanel',
        'primevue/autocomplete', 'primevue/textarea',
        '@primevue/core/api',
        'pinia', 'vue-router', 'jwt-decode', 'zod',
      ],
    },
  },

  // SPA by default — prevents flash-of-wrong-page on hard refresh.
  // SSR/ISR only for specific SEO pages.
  ssr: false,
  routeRules: {
    '/': { ssr: true },              // Landing page: SSR for SEO (Story 3.0b AC4)
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
        files: ['hu/admin.json', 'hu/auth.json', 'hu/common.json', 'hu/dashboard.json', 'hu/epr.json', 'hu/identity.json', 'hu/landing.json', 'hu/notification.json', 'hu/screening.json']
      },
      {
        code: 'en',
        name: 'English',
        files: ['en/admin.json', 'en/auth.json', 'en/common.json', 'en/dashboard.json', 'en/epr.json', 'en/identity.json', 'en/landing.json', 'en/notification.json', 'en/screening.json']
      }
    ],
    langDir: 'app/i18n',
    restructureDir: '',
    defaultLocale: 'hu',
    strategy: 'no_prefix',
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'rg_locale',
      redirectOn: 'root',
      fallbackLocale: 'en'
    }
  },
  css: ['primeicons/primeicons.css', '~/assets/css/main.css'],
  runtimeConfig: {
    public: {
      // Default for local dev. Overridden at build time via NUXT_PUBLIC_API_BASE env var
      // (e.g. NUXT_PUBLIC_API_BASE=https://risk-guard-backend-staging-xxx.run.app in CI/deploy.yml).
      // Nuxt automatically maps NUXT_PUBLIC_<KEY> → runtimeConfig.public.<key>.
      apiBase: 'http://localhost:8080'
    }
  }
})
