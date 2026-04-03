import { defineStore } from 'pinia'
import authConfig from '../risk-guard-tokens.json'

interface AuthState {
  token: string | null
  email: string | null
  name: string | null
  role: string | null
  tier: 'ALAP' | 'PRO' | 'PRO_EPR' | null
  preferredLanguage: string | null
  homeTenantId: string | null
  activeTenantId: string | null
  mandates: Array<{ id: string, name: string }>
  /** True while a tenant switch HTTP call is in flight */
  isSwitchingTenant: boolean
  /** Non-null when the last tenant switch failed */
  switchError: string | null
  /** The target tenant ID of the last failed switch — used by ContextGuard retry */
  switchTargetTenantId: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    email: null,
    name: null,
    role: null,
    tier: null,
    preferredLanguage: null,
    homeTenantId: null,
    activeTenantId: null,
    mandates: [],
    isSwitchingTenant: false,
    switchError: null,
    switchTargetTenantId: null
  }),

  getters: {
    // With HttpOnly cookies, the token is never stored in JS state.
    // Authentication is determined by whether /me succeeded (email is set).
    isAuthenticated: (state) => !!state.email,
    hasActiveTenant: (state) => !!state.activeTenantId,
    isAccountant: (state) => state.role === 'ACCOUNTANT'
  },

  actions: {
    async fetchMe() {
      try {
        const config = useRuntimeConfig()
        const raw = await $fetch<any>(authConfig.endpoints.me, {
          baseURL: config.public.apiBase as string,
          credentials: 'include'
        })
        // Defensive: handle both object and single-element array responses
        const data = Array.isArray(raw) ? raw[0] : raw
        this.email = data.email
        this.name = data.name
        this.role = data.role
        this.tier = data.tier ?? null
        this.preferredLanguage = data.preferredLanguage ?? 'hu'
        this.homeTenantId = data.homeTenantId
        this.activeTenantId = data.activeTenantId
        
        // If accountant, auto-fetch mandates
        if (this.isAccountant) {
          await this.fetchMandates()
        }
      } catch (e) {
        // /me failed — session is invalid. Skip the logout POST since the server
        // already rejected our auth. This prevents a wasteful network call.
        this.clearAuth(/* skipLogoutCall */ true)
      }
    },

    async fetchMandates() {
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<any[]>(authConfig.endpoints.mandates, {
          baseURL: config.public.apiBase as string,
          credentials: 'include'
        })
        this.mandates = data.map(m => ({ id: m.id, name: m.name }))
      } catch (e) {
        console.error('Failed to fetch mandates', e)
      }
    },

    /**
     * Clear all auth state. Optionally skip the server-side logout POST
     * when the session is already known to be invalid (e.g., after a failed
     * silent refresh on 401). This avoids a wasted network call and prevents
     * the logout POST from itself triggering another silent-refresh cycle.
     *
     * @param skipLogoutCall - If true, only clears local state without calling the logout endpoint.
     */
    async clearAuth(skipLogoutCall = false) {
      this.token = null
      this.email = null
      this.name = null
      this.role = null
      this.tier = null
      this.preferredLanguage = null
      this.homeTenantId = null
      this.activeTenantId = null
      this.mandates = []

      if (skipLogoutCall) {
        // Session is already invalid (e.g., 401 + failed refresh) — no point
        // calling the logout endpoint. The refresh token will expire naturally
        // or be cleaned up by the scheduled job.
        return
      }

      // Call backend logout to clear the HttpOnly auth_token cookie and revoke
      // the refresh token in the DB. useCookie().value = null has no effect on
      // HttpOnly cookies — this endpoint is the only way to properly clear them.
      try {
        const config = useRuntimeConfig()
        await $fetch(authConfig.endpoints.logout, {
          method: 'POST',
          baseURL: config.public.apiBase as string,
          credentials: 'include'
        })
      } catch {
        // Best-effort — if logout call fails (e.g., network error, already expired),
        // the local state is already cleared. The HttpOnly cookie will expire naturally.
      }
    },

    async initializeAuth() {
      try {
        // Fetch /me to get full profile and validate session via HttpOnly cookie.
        // With HttpOnly cookies, the frontend NEVER reads JWT contents directly.
        // Auth state comes exclusively from GET /api/v1/identity/me.
        await this.fetchMe()

        // Sync UI locale from the user's stored preference (AC #3).
        // Uses $i18n directly because Pinia actions don't have a composable context.
        if (this.preferredLanguage) {
          try {
            const { $i18n } = useNuxtApp()
            const i18n = $i18n as { locale: { value: string }, setLocale?: (l: string) => Promise<void> }
            if (i18n.locale.value !== this.preferredLanguage) {
              if (typeof i18n.setLocale === 'function') {
                await i18n.setLocale(this.preferredLanguage)
              } else {
                i18n.locale.value = this.preferredLanguage
              }
            }
          } catch {
            // Best-effort — if i18n isn't ready yet, the cookie/default will apply.
          }
        }
      } catch {
        // initializeAuth failed — fetchMe or locale sync threw unexpectedly.
        // Auth state is already cleared by fetchMe's own catch block.
      }
    },

    async switchTenant(tenantId: string) {
      this.isSwitchingTenant = true
      this.switchError = null
      this.switchTargetTenantId = tenantId
      try {
        const config = useRuntimeConfig()
        // Token is now set via HttpOnly cookie by the backend (no token in response body)
        await $fetch(authConfig.endpoints.switchTenant, {
          method: 'POST',
          body: { tenantId },
          baseURL: config.public.apiBase as string,
          credentials: 'include'
        })
        // Refresh auth state — backend updated the HttpOnly cookie, fetchMe() picks it up
        await this.fetchMe()
        this.isSwitchingTenant = false
      } catch (error) {
        this.isSwitchingTenant = false
        this.switchError = error instanceof Error ? error.message : String(error)
        console.error('Failed to switch tenant:', error)
        throw error
      }
    }
  }
})
