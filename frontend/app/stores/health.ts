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
  },
})
