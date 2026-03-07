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
}

interface DecodedToken {
  sub: string
  home_tenant_id: string
  active_tenant_id: string
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
    mandates: []
  }),

  getters: {
    isAuthenticated: (state) => !!state.email || !!state.token,
    hasActiveTenant: (state) => !!state.activeTenantId,
    isAccountant: (state) => state.role === 'ACCOUNTANT'
  },

  actions: {
    setToken(token: string) {
      this.token = token
      try {
        const decoded = jwtDecode<DecodedToken>(token)
        this.email = decoded.sub
        this.homeTenantId = decoded.home_tenant_id
        this.activeTenantId = decoded.active_tenant_id
      } catch (e) {
        console.error('Failed to decode token', e)
      }
    },

    async fetchMe() {
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<any>(authConfig.endpoints.me, {
          baseURL: config.public.apiBase as string
        })
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
          baseURL: config.public.apiBase as string
        })
        this.mandates = data.map(m => ({ id: m.id, name: m.name }))
      } catch (e) {
        console.error('Failed to fetch mandates', e)
      }
    },

    clearAuth() {
      this.token = null
      this.email = null
      this.name = null
      this.role = null
      this.homeTenantId = null
      this.activeTenantId = null
      this.mandates = []
      
      const cookie = useCookie(authConfig.cookieName)
      cookie.value = null
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
      try {
        const config = useRuntimeConfig()
        // Token is now set via HttpOnly cookie by the backend (no token in response body)
        await $fetch(authConfig.endpoints.switchTenant, {
          method: 'POST',
          body: { tenantId },
          baseURL: config.public.apiBase as string
        })

        // Trigger a full page reload to ensure all data is refreshed for the new tenant
        // The new JWT is already set as an HttpOnly cookie by the backend
        window.location.reload()
      } catch (error) {
        console.error('Failed to switch tenant:', error)
        throw error
      }
    }
  }
})
