import { defineStore } from 'pinia'
import type { FlightControlResponse, FlightControlTenantSummaryResponse, FlightControlTotals } from '~/types/api'

interface FlightControlState {
  tenants: FlightControlTenantSummaryResponse[]
  totals: FlightControlTotals | null
  isLoading: boolean
  error: string | null
  /** RFC 7807 error type URN preserved from FetchError (Story 3.9 R1-M4 learning). */
  errorType: string | null
}

/**
 * Flight Control store — manages cross-tenant portfolio summary state for accountants.
 *
 * Uses options-style Pinia store matching the pattern from stores/portfolio.ts and
 * stores/watchlist.ts. Fetches from the backend flight control endpoint with HttpOnly
 * cookie auth (credentials: 'include').
 */
export const useFlightControlStore = defineStore('flightControl', {
  state: (): FlightControlState => ({
    tenants: [],
    totals: null,
    isLoading: false,
    error: null,
    errorType: null,
  }),

  actions: {
    async fetchSummary() {
      this.isLoading = true
      this.error = null
      this.errorType = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<FlightControlResponse>(
          '/api/v1/portfolio/flight-control',
          {
            baseURL: config.public.apiBase as string,
            credentials: 'include',
          },
        )
        this.tenants = data.tenants
        this.totals = data.totals
      }
      catch (error: unknown) {
        // Preserve RFC 7807 error type from FetchError (Story 3.9 R1-M4 learning)
        if (error && typeof error === 'object') {
          const fetchError = error as { data?: { type?: string } }
          if (fetchError.data?.type) {
            this.errorType = fetchError.data.type
          }
        }
        this.error = error instanceof Error ? error.message : String(error)
      }
      finally {
        this.isLoading = false
      }
    },
  },
})
