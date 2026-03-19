import { defineStore } from 'pinia'
import type { AddWatchlistEntryResponse, WatchlistEntryResponse, WatchlistCountResponse } from '~/types/api'

interface WatchlistState {
  entries: WatchlistEntryResponse[]
  count: number
  isLoading: boolean
  error: string | null
}

/**
 * Watchlist store — manages watchlist CRUD state.
 *
 * Uses `$fetch` directly instead of the `useApi()` composable because Pinia
 * options-style actions run outside Vue's setup context, where composables
 * like `useI18n()` and `useRuntimeConfig()` are not available.
 * The global `$fetch` interceptor (api-locale.ts plugin) handles
 * Accept-Language headers, credentials, and 401 redirects automatically.
 */
export const useWatchlistStore = defineStore('watchlist', {
  state: (): WatchlistState => ({
    entries: [],
    count: 0,
    isLoading: false,
    error: null,
  }),

  actions: {
    async fetchEntries() {
      this.isLoading = true
      this.error = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<WatchlistEntryResponse[]>('/api/v1/watchlist', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.entries = data
        this.count = data.length
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
      }
      finally {
        this.isLoading = false
      }
    },

    async addEntry(taxNumber: string, companyName?: string | null, verdictStatus?: string | null): Promise<{ duplicate: boolean }> {
      this.error = null

      try {
        const config = useRuntimeConfig()
        const data = await $fetch<AddWatchlistEntryResponse>('/api/v1/watchlist', {
          method: 'POST',
          body: { taxNumber, companyName: companyName ?? null, verdictStatus: verdictStatus ?? null },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })

        // Backend signals duplicate via the response flag — no guessing needed
        if (data.duplicate) {
          return { duplicate: true }
        }

        // Re-fetch the full list so the new entry includes enriched data
        await this.fetchEntries()
        return { duplicate: false }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async removeEntry(id: string) {
      this.error = null

      try {
        const config = useRuntimeConfig()
        await $fetch(`/api/v1/watchlist/${id}`, {
          method: 'DELETE',
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })

        this.entries = this.entries.filter(e => e.id !== id)
        this.count = Math.max(0, this.count - 1)
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async fetchCount() {
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<WatchlistCountResponse>('/api/v1/watchlist/count', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.count = data.count
      }
      catch {
        // Silent fail for badge — non-critical
      }
    },

    isOnWatchlist(taxNumber: string): boolean {
      return this.entries.some(e => e.taxNumber === taxNumber)
    },
  },
})
