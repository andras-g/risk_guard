import { defineStore } from 'pinia'
import { jwtDecode } from 'jwt-decode'
import authConfig from '../risk-guard-tokens.json'

interface AuthState {
  token: string | null
  email: string | null
  name: string | null
  role: string | null
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

interface DecodedToken {
  sub: string
  home_tenant_id: string
  active_tenant_id: string
  role: string
  exp: number
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    email: null,
    name: null,
    role: null,
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
    setToken(token: string) {
      this.token = token
      try {
        const decoded = jwtDecode<DecodedToken>(token)
        this.email = decoded.sub
        this.role = decoded.role
        this.homeTenantId = decoded.home_tenant_id
        this.activeTenantId = decoded.active_tenant_id
      } catch (e) {
        // Clear stale token to prevent zombie auth state where token is set but
        // claims are empty (isAuthenticated would return true due to non-null token)
        this.clearAuth()
        console.error('Failed to decode token', e)
      }
    },

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
        this.homeTenantId = data.homeTenantId
        this.activeTenantId = data.activeTenantId
        
        // If accountant, auto-fetch mandates
        if (this.isAccountant) {
          await this.fetchMandates()
        }
      } catch (e) {
        this.clearAuth()
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

    async clearAuth() {
      this.token = null
      this.email = null
      this.name = null
      this.role = null
      this.homeTenantId = null
      this.activeTenantId = null
      this.mandates = []

      // Call backend logout to clear the HttpOnly auth_token cookie.
      // useCookie().value = null has no effect on HttpOnly cookies.
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
      // First, try reading if it's NOT HttpOnly (legacy or manual token setting)
      const cookie = useCookie(authConfig.cookieName)
      if (cookie.value) {
        this.setToken(cookie.value as string)
      }
      
      // Always fetch /me to get full profile and re-validate session via HttpOnly cookie
      await this.fetchMe()
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

        // Reset switching state before reload — if reload fails or is cancelled,
        // the ContextGuard overlay will not permanently block the UI.
        this.isSwitchingTenant = false
        window.location.reload()
      } catch (error) {
        this.isSwitchingTenant = false
        this.switchError = error instanceof Error ? error.message : String(error)
        console.error('Failed to switch tenant:', error)
        throw error
      }
    }
  }
})
