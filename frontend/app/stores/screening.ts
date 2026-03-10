import { defineStore } from 'pinia'

export interface VerdictResponse {
  id: string
  snapshotId: string
  taxNumber: string
  status: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE'
  confidence: 'FRESH' | 'STALE' | 'UNAVAILABLE'
  createdAt: string
}

interface ScreeningState {
  currentTaxNumber: string
  currentVerdict: VerdictResponse | null
  isSearching: boolean
  searchError: string | null
}

export const useScreeningStore = defineStore('screening', {
  state: (): ScreeningState => ({
    currentTaxNumber: '',
    currentVerdict: null,
    isSearching: false,
    searchError: null
  }),

  actions: {
    async search(taxNumber: string) {
      this.currentTaxNumber = taxNumber
      this.isSearching = true
      this.searchError = null
      this.currentVerdict = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<VerdictResponse>('/api/v1/screenings/search', {
          method: 'POST',
          body: { taxNumber },
          baseURL: config.public.apiBase as string
        })
        this.currentVerdict = data
      } catch (error: unknown) {
        this.searchError = error instanceof Error ? error.message : String(error)
        console.error('Search failed:', error)
      } finally {
        this.isSearching = false
      }
    },

    clearSearch() {
      this.currentTaxNumber = ''
      this.currentVerdict = null
      this.searchError = null
      this.isSearching = false
    }
  }
})
