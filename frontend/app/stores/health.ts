import { defineStore } from 'pinia'

export interface AdapterHealth {
  adapterName: string
  circuitBreakerState: string
  successRatePct: number
  failureCount: number
  lastSuccessAt: string | null
  lastFailureAt: string | null
  mtbfHours: number | null
  dataSourceMode: string
  credentialStatus: string
}

interface HealthState {
  adapters: AdapterHealth[]
  loading: boolean
  error: string | null
  lastUpdated: Date | null
  quarantining: Record<string, boolean>
}

/**
 * Pinia store for data source adapter health state.
 * Fetches from GET /api/v1/admin/datasources/health.
 */
export const useHealthStore = defineStore('health', {
  state: (): HealthState => ({
    adapters: [],
    loading: false,
    error: null,
    lastUpdated: null,
    quarantining: {} as Record<string, boolean>,
  }),

  actions: {
    async fetchHealth() {
      this.loading = true
      this.error = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<AdapterHealth[]>('/api/v1/admin/datasources/health', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.adapters = data
        this.lastUpdated = new Date()
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
      }
      finally {
        this.loading = false
      }
    },

    async quarantineAdapter(adapterName: string, quarantined: boolean) {
      this.quarantining[adapterName] = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        await $fetch(`/api/v1/admin/datasources/${adapterName}/quarantine`, {
          method: 'POST',
          body: { quarantined },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        await this.fetchHealth()
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.quarantining[adapterName] = false
      }
    },
  },
})
