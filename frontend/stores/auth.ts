import { defineStore } from 'pinia'
import { jwtDecode } from 'jwt-decode'
import authConfig from '~/risk-guard-tokens.json'

interface AuthState {
  token: string | null
  email: string | null
  homeTenantId: string | null
  activeTenantId: string | null
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
    homeTenantId: null,
    activeTenantId: null
  }),

  getters: {
    isAuthenticated: (state) => !!state.email || !!state.token,
    hasActiveTenant: (state) => !!state.activeTenantId
  },

  actions: {
    setToken(token: string) {
      this.token = token
      try {
        const decoded = jwtDecode<DecodedToken>(token)
        this.email = decoded.sub
        this.homeTenantId = decoded.home_tenant_id
        this.activeTenantId = decoded.active_tenant_id
        
        // Persist to cookie (secure, httpOnly=false for JS access ONLY IF token is passed manually)
        // Note: For SSO, the backend sets an HttpOnly cookie, so we don't need this.
        // But for manual token setting (like switching tenants), we might want to refresh it.
        const cookie = useCookie(authConfig.cookieName, {
          maxAge: 3600,
          secure: process.env.NODE_ENV === 'production',
          sameSite: 'lax'
        })
        cookie.value = token
      } catch (e) {
        console.error('Failed to decode token', e)
      }
    },

    async fetchMe() {
      try {
        // This will send the HttpOnly cookie automatically
        const data = await $fetch<any>(authConfig.endpoints.me)
        this.email = data.email
        this.homeTenantId = data.homeTenantId
        this.activeTenantId = data.activeTenantId
      } catch (e) {
        this.clearAuth()
      }
    },

    clearAuth() {
      this.token = null
      this.email = null
      this.homeTenantId = null
      this.activeTenantId = null
      
      const cookie = useCookie(authConfig.cookieName)
      cookie.value = null
    },

    async initializeAuth() {
      // First, try reading if it's NOT HttpOnly (legacy or manual token setting)
      const cookie = useCookie(authConfig.cookieName)
      if (cookie.value) {
        this.setToken(cookie.value as string)
      } else {
        // If no non-HttpOnly cookie, try fetching /me which will check for HttpOnly cookie
        await this.fetchMe()
      }
    },

    async switchTenant(tenantId: string) {
      try {
        const response = await $fetch<{ token: string }>(authConfig.endpoints.switchTenant, {
          method: 'POST',
          body: { tenantId }
        })

        // The backend also sets the HttpOnly cookie, but we update our store
        this.setToken(response.token)
        
        // Trigger a full page reload to ensure all data is refreshed for the new tenant
        window.location.reload()
      } catch (error) {
        console.error('Failed to switch tenant:', error)
        throw error
      }
    }
  }
})
