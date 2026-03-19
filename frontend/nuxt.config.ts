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
      include: [
        'primevue/datatable', 'primevue/column', 'primevue/button',
        'primevue/dialog', 'primevue/inputtext', 'primevue/tag',
        'primevue/skeleton', 'primevue/badge', 'primevue/confirmdialog',
        'primevue/usetoast', 'primevue/useconfirm',
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
        files: ['hu/common.json', 'hu/auth.json', 'hu/identity.json', 'hu/landing.json', 'hu/screening.json', 'hu/notification.json']
      },
      { 
        code: 'en', 
        name: 'English',
        files: ['en/common.json', 'en/auth.json', 'en/identity.json', 'en/landing.json', 'en/screening.json', 'en/notification.json']
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
      // Default for local dev: empty string means same-origin (proxied via Nitro devProxy below).
      // In production/staging: overridden via NUXT_PUBLIC_API_BASE env var at build time
      // (e.g. NUXT_PUBLIC_API_BASE=https://risk-guard-backend-staging-xxx.run.app in CI/deploy.yml).
      // Nuxt automatically maps NUXT_PUBLIC_<KEY> → runtimeConfig.public.<key>.
      apiBase: ''
    }
  },

  // Dev proxy: route API calls through the Nuxt dev server to avoid cross-origin cookie issues.
  // In production, the frontend is served from the same domain as the API (Cloud Run) or
  // apiBase is set to the backend URL and CORS + SameSite=None handles cookies.
  nitro: {
    devProxy: {
      '/api/': {
        target: 'http://localhost:8080/api/',
        changeOrigin: true,
      },
      '/actuator/': {
        target: 'http://localhost:8080/actuator/',
        changeOrigin: true,
      },
      '/oauth2/': {
        target: 'http://localhost:8080/oauth2/',
        changeOrigin: true,
      },
      '/login/': {
        target: 'http://localhost:8080/login/',
        changeOrigin: true,
      },
    },
  },
})
