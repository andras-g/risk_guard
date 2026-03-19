/**
 * Global $fetch interceptor for all API calls.
 *
 * Responsibilities:
 * 1. Adds the Accept-Language header matching the user's current i18n locale,
 *    ensuring the backend's AcceptHeaderLocaleResolver resolves correctly.
 * 2. Catches 401 responses (expired token) and redirects to login — prevents
 *    the user from staying on an authenticated page with a stale session.
 *
 * Runs on every request to the configured apiBase URL. Non-API requests (e.g.,
 * CDN assets, third-party services) are left untouched.
 *
 * Story 3.1 — AC #5 (backend uses locale-aware message bundles).
 * Story 3.7 — Session expiry redirect on 401.
 */
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase as string

  globalThis.$fetch = globalThis.$fetch.create({
    onRequest({ options, request }) {
      // Only inject header for API calls (not CDN, third-party, etc.)
      const url = typeof request === 'string' ? request : request.toString()
      const isApiCall = url.startsWith('/api/') || url.startsWith(apiBase)

      if (isApiCall) {
        try {
          const { $i18n } = useNuxtApp()
          const locale = ($i18n as { locale: { value: string } }).locale.value
          const headers = new Headers(options.headers as HeadersInit)
          if (!headers.has('Accept-Language')) {
            headers.set('Accept-Language', locale)
          }
          options.headers = headers
        } catch {
          // i18n not yet initialized — skip header injection
        }
      }
    },

    onResponseError({ response, request }) {
      // Redirect to login on 401 — session/token has expired.
      // Only handle API calls to avoid interfering with third-party requests.
      // Skip auth-related endpoints to prevent redirect loops during login/initialization.
      if (response.status === 401) {
        const url = typeof request === 'string' ? request : request.toString()
        const isApiCall = url.startsWith('/api/') || url.startsWith(apiBase)
        const isAuthRelated = url.includes('/auth/') || url.includes('/identity/')

        if (isApiCall && !isAuthRelated) {
          try {
            const authStore = useAuthStore()
            authStore.$reset()
            navigateTo('/auth/login')
          } catch {
            // If store/router not ready, force a hard redirect
            if (import.meta.client) {
              window.location.href = '/auth/login'
            }
          }
        }
      }
    },
  })
})
