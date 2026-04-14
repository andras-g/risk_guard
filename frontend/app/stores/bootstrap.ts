import { defineStore } from 'pinia'
import type { BootstrapCandidateResponse } from '~/composables/api/useBootstrap'

interface BootstrapState {
  candidates: BootstrapCandidateResponse[]
  total: number
  triggerState: 'idle' | 'loading' | 'done' | 'error'
  isLoading: boolean
  error: string | null
}

export const useBootstrapStore = defineStore('bootstrap', {
  state: (): BootstrapState => ({
    candidates: [],
    total: 0,
    triggerState: 'idle',
    isLoading: false,
    error: null,
  }),

  actions: {
    setCandidates(candidates: BootstrapCandidateResponse[], total: number) {
      this.candidates = candidates
      this.total = total
    },

    updateCandidate(updated: BootstrapCandidateResponse) {
      const idx = this.candidates.findIndex(c => c.id === updated.id)
      if (idx !== -1) {
        this.candidates[idx] = updated
      }
    },

    setTriggerState(state: BootstrapState['triggerState']) {
      this.triggerState = state
    },

    setLoading(loading: boolean) {
      this.isLoading = loading
    },

    setError(error: string | null) {
      this.error = error
    },

    clearError() {
      this.error = null
    },
  },
})
