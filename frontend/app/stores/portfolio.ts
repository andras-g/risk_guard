import { defineStore } from 'pinia'
import type { PortfolioAlertResponse } from '~/types/api'

interface PortfolioState {
  alerts: PortfolioAlertResponse[]
  isLoading: boolean
  error: string | null
}

/**
 * Portfolio store — manages cross-tenant portfolio alert state for accountants.
 *
 * Uses `$fetch` directly (same pattern as watchlist store) because Pinia
 * options-style actions run outside Vue's setup context.
 */
export const usePortfolioStore = defineStore('portfolio', {
  state: (): PortfolioState => ({
    alerts: [],
    isLoading: false,
    error: null,
  }),

  actions: {
    async fetchAlerts(days: number = 7) {
      this.isLoading = true
      this.error = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<PortfolioAlertResponse[]>('/api/v1/portfolio/alerts', {
          params: { days },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.alerts = data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
      }
      finally {
        this.isLoading = false
      }
    },
  },
})
