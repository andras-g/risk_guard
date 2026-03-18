import { defineStore } from 'pinia'
import type { SnapshotProvenanceResponse, VerdictResponse } from '~/types/api'

interface ScreeningState {
  currentTaxNumber: string
  currentVerdict: VerdictResponse | null
  currentProvenance: SnapshotProvenanceResponse | null
  isSearching: boolean
  isLoadingProvenance: boolean
  searchError: string | null
  provenanceError: string | null
}

export const useScreeningStore = defineStore('screening', {
  state: (): ScreeningState => ({
    currentTaxNumber: '',
    currentVerdict: null,
    currentProvenance: null,
    isSearching: false,
    isLoadingProvenance: false,
    searchError: null,
    provenanceError: null
  }),

  actions: {
    async search(taxNumber: string) {
      this.currentTaxNumber = taxNumber
      this.isSearching = true
      this.searchError = null
      this.currentVerdict = null
      this.currentProvenance = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<VerdictResponse>('/api/v1/screenings/search', {
          method: 'POST',
          body: { taxNumber },
          baseURL: config.public.apiBase as string,
          credentials: 'include'
        })
        this.currentVerdict = data

        // Eagerly fetch provenance after verdict loads
        if (data.snapshotId) {
          await this.fetchProvenance(data.snapshotId)
        }
      } catch (error: unknown) {
        this.searchError = error instanceof Error ? error.message : String(error)
      } finally {
        this.isSearching = false
      }
    },

    async fetchProvenance(snapshotId: string) {
      this.isLoadingProvenance = true
      this.provenanceError = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<SnapshotProvenanceResponse>(
          `/api/v1/screenings/snapshots/${snapshotId}/provenance`,
          {
            baseURL: config.public.apiBase as string,
            credentials: 'include'
          }
        )
        this.currentProvenance = data
      } catch (error: unknown) {
        this.provenanceError = error instanceof Error ? error.message : String(error)
      } finally {
        this.isLoadingProvenance = false
      }
    },

    clearSearch() {
      this.currentTaxNumber = ''
      this.currentVerdict = null
      this.currentProvenance = null
      this.searchError = null
      this.provenanceError = null
      this.isSearching = false
      this.isLoadingProvenance = false
    }
  }
})
