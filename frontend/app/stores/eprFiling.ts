import { defineStore } from 'pinia'
import type { FilingAggregationResult, KfCodeTotal } from '~/types/epr'

export type ProducerProfileIncompleteError = 'producer.profile.incomplete'

interface FilingState {
  period: { from: string; to: string }
  aggregation: FilingAggregationResult | null
  isLoading: boolean
  isExporting: boolean
  error: string | null
  exportError: ProducerProfileIncompleteError | string | null
}

export const useEprFilingStore = defineStore('eprFiling', {
  state: (): FilingState => ({
    period: { from: '', to: '' },
    aggregation: null,
    isLoading: false,
    isExporting: false,
    error: null,
    exportError: null,
  }),

  getters: {
    grandTotalWeightKg: (state): number =>
      state.aggregation?.kfTotals.reduce((sum: number, t: KfCodeTotal) => sum + (t.totalWeightKg ?? 0), 0) ?? 0,

    grandTotalFeeHuf: (state): number =>
      state.aggregation?.kfTotals.reduce((sum: number, t: KfCodeTotal) => sum + (t.totalFeeHuf ?? 0), 0) ?? 0,

    totalKfCodes: (state): number =>
      state.aggregation?.kfTotals.length ?? 0,
  },

  actions: {
    async fetchAggregation(from: string, to: string): Promise<void> {
      this.isLoading = true
      this.error = null
      this.exportError = null
      this.aggregation = null
      this.period = { from, to }
      try {
        const config = useRuntimeConfig()
        const result = await $fetch<FilingAggregationResult>('/api/v1/epr/filing/aggregation', {
          query: { from, to },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.aggregation = result
      }
      catch (e: unknown) {
        this.aggregation = null
        const status = (e as { status?: number })?.status
        if (status === 412) {
          this.error = 'producer.profile.incomplete'
        }
        else if (status === 402) {
          this.error = 'tier.gate'
        }
        else {
          this.error = e instanceof Error ? e.message : String(e)
        }
      }
      finally {
        this.isLoading = false
      }
    },

    async exportOkirkapu(from: string, to: string): Promise<void> {
      this.isExporting = true
      this.exportError = null
      try {
        const config = useRuntimeConfig()
        // Fetch tax number from producer profile — only 404 means "not configured";
        // transport/auth/5xx errors must surface as real errors, not silent fall-through.
        let taxNumber = ''
        try {
          const profile = await $fetch<{ taxNumber?: string }>('/api/v1/epr/producer-profile', {
            baseURL: config.public.apiBase as string,
            credentials: 'include',
          })
          taxNumber = profile?.taxNumber ?? ''
        }
        catch (profileErr: unknown) {
          const status = (profileErr as { status?: number })?.status
          if (status !== 404) {
            throw profileErr
          }
          // 404 → profile not configured; proceed with empty taxNumber, backend 412 will confirm
        }
        const blob = await $fetch<Blob>('/api/v1/epr/filing/okirkapu-export', {
          method: 'POST',
          body: { from, to, taxNumber },
          responseType: 'blob',
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        if (import.meta.client) {
          // Parse YYYY-MM-DD manually to avoid timezone drift from `new Date(iso)`
          const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(from)
          const year = parts ? Number(parts[1]) : NaN
          const month = parts ? Number(parts[2]) : NaN
          const quarter = Number.isFinite(month) ? Math.ceil(month / 3) : NaN
          const filename = Number.isFinite(year) && Number.isFinite(quarter)
            ? `okir-kg-kgyf-ne-${year}-Q${quarter}.zip`
            : `okir-kg-kgyf-ne.zip`
          const url = URL.createObjectURL(blob)
          const anchor = document.createElement('a')
          anchor.href = url
          anchor.download = filename
          document.body.appendChild(anchor)
          anchor.click()
          document.body.removeChild(anchor)
          setTimeout(() => URL.revokeObjectURL(url), 100)
        }
      }
      catch (e: unknown) {
        const status = (e as { status?: number })?.status
        if (status === 412) {
          this.exportError = 'producer.profile.incomplete'
        }
        else {
          this.exportError = e instanceof Error ? e.message : String(e)
        }
        throw e
      }
      finally {
        this.isExporting = false
      }
    },

    reset() {
      this.period = { from: '', to: '' }
      this.aggregation = null
      this.isLoading = false
      this.isExporting = false
      this.error = null
      this.exportError = null
    },
  },
})
