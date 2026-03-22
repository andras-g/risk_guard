/**
 * Global $fetch interceptor for all API calls.
 *
 * Responsibilities:
 * 1. Adds the Accept-Language header matching the user's current i18n locale,
 *    ensuring the backend's AcceptHeaderLocaleResolver resolves correctly.
 * 2. Catches 401 responses (expired access token) and attempts a silent refresh
 *    via the refresh token before falling back to login redirect.
 *
 * Runs on every request to the configured apiBase URL. Non-API requests (e.g.,
 * CDN assets, third-party services) are left untouched.
 *
 * Story 3.1 — AC #5 (backend uses locale-aware message bundles).
 * Story 3.7 — Session expiry redirect on 401.
 * Story 3.13 — Silent refresh interceptor (AC #2, #6, #9).
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

    async onResponseError({ response, request, options }) {
      // Silent refresh on 401 — attempt to refresh the access token before redirecting.
      // Only handle API calls to avoid interfering with third-party requests.
      // Skip auth-related endpoints to prevent redirect loops during login/refresh.
      if (response.status === 401) {
        const url = typeof request === 'string' ? request : request.toString()
        const isApiCall = url.startsWith('/api/') || url.startsWith(apiBase)
        // Only skip silent refresh for endpoints that would cause loops:
        // - /auth/refresh (the refresh endpoint itself)
        // - /auth/login, /auth/register (pre-auth endpoints)
        // Notably, /identity/me MUST attempt refresh — a 401 on /me during
        // initializeAuth() should trigger silent renewal, not be swallowed.
        const isRefreshOrPreAuth = url.includes('/auth/refresh') || url.includes('/auth/login') || url.includes('/auth/register')

        if (isApiCall && !isRefreshOrPreAuth) {
          // Guard: if this request was already a retry after refresh, do NOT attempt again
          // (prevents infinite loop). The _isRetryAfterRefresh flag is set below.
          if ((options as any)._isRetryAfterRefresh) {
            redirectToLogin(response)
            return
          }

          try {
            const { useTokenRefresh } = await import('~/composables/auth/useTokenRefresh')
            const { attemptRefresh, isTokenFamilyRevoked } = useTokenRefresh()

            // Check for TOKEN_FAMILY_REVOKED (potential compromise) — show toast + redirect
            if (isTokenFamilyRevoked(response)) {
              showCompromisedToast()
              redirectToLogin()
              return
            }

            const refreshed = await attemptRefresh()

            if (refreshed) {
              // Retry the original request exactly once with the new access token.
              // Mark it as a retry so we don't loop if it also fails.
              // NOTE: oFetch's onResponseError cannot replace the original response — the
              // original caller will still see the 401 error even if this retry succeeds.
              // This is acceptable because: (1) the retry's side-effect (setting new cookies)
              // makes subsequent requests succeed, and (2) callers like fetchMe() already
              // handle 401 gracefully via clearAuth(skipLogoutCall: true).
              const retryOptions = {
                ...options,
                _isRetryAfterRefresh: true,
              }
              await $fetch(url, retryOptions as any)
              return
            }
          } catch {
            // Refresh or retry failed — fall through to login redirect
          }

          // Refresh failed or retry failed — redirect to login
          redirectToLogin()
        }
      }
    },
  })
})

function redirectToLogin(response?: Response) {
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

async function showCompromisedToast() {
  try {
    const { $i18n } = useNuxtApp()
    const t = ($i18n as any).t
    const message = t ? t('identity.session.sessionCompromised') : 'Session compromised — please log in again'
    // Use PrimeVue toast if available, otherwise console.warn.
    // Dynamic ESM import — avoids CJS require() which is unavailable in Nuxt 3 ESM context.
    try {
      const { useToast } = await import('primevue/usetoast')
      const toast = useToast()
      toast.add({ severity: 'error', summary: message, life: 5000 })
    } catch {
      console.warn(message)
    }
  } catch {
    console.warn('Session compromised — please log in again')
  }
}
