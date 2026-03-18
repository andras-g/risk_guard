/**
 * Global $fetch interceptor that adds the Accept-Language header to all API calls.
 *
 * This ensures the backend's AcceptHeaderLocaleResolver always receives the user's
 * current i18n locale, regardless of whether the calling code uses the useApi
 * composable or raw $fetch.
 *
 * Runs on every request to the configured apiBase URL. Non-API requests (e.g.,
 * CDN assets, third-party services) are left untouched.
 *
 * Story 3.1 — AC #5 (backend uses locale-aware message bundles).
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
  })
})
