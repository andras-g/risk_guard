import { defineStore } from 'pinia'
import { jwtDecode } from 'jwt-decode'

export interface UserProfile {
  email: string
  name: string
  role: 'GUEST' | 'SME_ADMIN' | 'ACCOUNTANT'
  tier: 'ALAP' | 'PRO' | 'PRO_EPR'
  homeTenantId: string
  activeTenantId: string
}

export interface Tenant {
  id: string
  name: string
}

export const useIdentityStore = defineStore('identity', () => {
  const token = ref<string | null>(null)
  const user = ref<UserProfile | null>(null)
  const mandates = ref<Tenant[]>([])

  const isAuthenticated = computed(() => !!token.value)
  
  function setToken(newToken: string) {
    token.value = newToken
    const decoded: any = jwtDecode(newToken)
    user.value = {
      email: decoded.sub,
      name: decoded.name,
      role: decoded.role,
      tier: decoded.tier,
      homeTenantId: decoded.home_tenant_id,
      activeTenantId: decoded.active_tenant_id
    }
    // In a real app, we'd persist this to localStorage
  }

  async function switchTenant(tenantId: string) {
    // API call to backend to get new JWT
    const { apiBase } = useRuntimeConfig().public
    try {
      const response: any = await $fetch(`${apiBase}/identity/tenants/switch`, {
        method: 'POST',
        body: { tenantId },
        headers: {
          Authorization: `Bearer ${token.value}`
        }
      })
      setToken(response.token)
      // Reload dashboard data here or trigger a refresh event
    } catch (e) {
      console.error('Failed to switch tenant', e)
    }
  }

  return {
    token,
    user,
    mandates,
    isAuthenticated,
    setToken,
    switchTenant
  }
})
