import { defineStore } from 'pinia'
import type { MaterialTemplateResponse } from '~/types/epr'

export interface FilingLineState {
  templateId: string
  name: string
  kfCode: string | null
  baseWeightGrams: number
  feeRateHufPerKg: number | null
  quantityPcs: number | null
  // backend-computed values (null until calculate() is called)
  totalWeightGrams: number | null
  totalWeightKg: number | null
  feeAmountHuf: number | null
  // frontend-only validation
  isValid: boolean
  validationMessage: string | null
}

export interface FilingCalculationResponse {
  lines: {
    templateId: string; name: string; kfCode: string | null
    quantityPcs: number; baseWeightGrams: number
    totalWeightGrams: number; totalWeightKg: number
    feeRateHufPerKg: number; feeAmountHuf: number
  }[]
  grandTotalWeightKg: number
  grandTotalFeeHuf: number
  configVersion: number
}

export type ProducerProfileIncompleteError = 'producer.profile.incomplete'

interface FilingState {
  lines: FilingLineState[]
  serverResult: FilingCalculationResponse | null
  isCalculating: boolean
  isExporting: boolean
  error: string | null
  exportError: ProducerProfileIncompleteError | string | null
}

export const useEprFilingStore = defineStore('eprFiling', {
  state: (): FilingState => ({
    lines: [],
    serverResult: null,
    isCalculating: false,
    isExporting: false,
    error: null,
    exportError: null,
  }),

  getters: {
    validLines: (state): FilingLineState[] =>
      state.lines.filter(l => l.isValid && l.quantityPcs !== null && l.quantityPcs > 0),

    grandTotalWeightKg: (state): number =>
      state.serverResult?.grandTotalWeightKg ?? 0,

    grandTotalFeeHuf: (state): number =>
      state.serverResult?.grandTotalFeeHuf ?? 0,

    hasValidLines: (state): boolean =>
      state.lines.some(l => l.isValid && l.quantityPcs !== null && l.quantityPcs > 0),
  },

  actions: {
    initFromTemplates(templates: MaterialTemplateResponse[]) {
      // Only include templates that are verified AND have a fee rate (completed the wizard).
      // Templates without a fee rate will cause a 422 from the backend.
      this.lines = templates
        .filter(t => t.verified && t.feeRate != null)
        .map(t => ({
          templateId: t.id,
          name: t.name,
          kfCode: t.kfCode,
          baseWeightGrams: t.baseWeightGrams,
          feeRateHufPerKg: t.feeRate,
          quantityPcs: null,
          totalWeightGrams: null,
          totalWeightKg: null,
          feeAmountHuf: null,
          isValid: false,
          validationMessage: null,
        }))
      this.serverResult = null
    },

    updateQuantity(templateId: string, rawValue: string) {
      const line = this.lines.find(l => l.templateId === templateId)
      if (!line) return
      const parsed = parseInt(rawValue, 10)
      if (rawValue === '') {
        line.quantityPcs = null
        line.isValid = false
        line.validationMessage = null
      }
      else if (!Number.isInteger(parseFloat(rawValue)) || isNaN(parsed)) {
        line.quantityPcs = null
        line.isValid = false
        line.validationMessage = 'epr.filing.validation.mustBeInteger'
      }
      else if (parsed <= 0) {
        line.quantityPcs = null
        line.isValid = false
        line.validationMessage = 'epr.filing.validation.mustBePositive'
      }
      else {
        line.quantityPcs = parsed
        line.isValid = true
        line.validationMessage = null
      }
      // Compute row values client-side for immediate real-time feedback (AC 2)
      if (line.isValid && line.quantityPcs !== null && line.feeRateHufPerKg !== null) {
        const totalWeightGrams = line.baseWeightGrams * line.quantityPcs
        const totalWeightKg = totalWeightGrams / 1000
        line.totalWeightGrams = totalWeightGrams
        line.totalWeightKg = totalWeightKg
        line.feeAmountHuf = Math.round(totalWeightKg * line.feeRateHufPerKg)
      }
      else {
        line.totalWeightGrams = null
        line.totalWeightKg = null
        line.feeAmountHuf = null
      }
      // Clear authoritative server result whenever user modifies any quantity
      this.serverResult = null
    },

    async calculate() {
      const valid = this.lines.filter(l => l.isValid && l.quantityPcs !== null && l.quantityPcs > 0)
      if (valid.length === 0) return
      this.isCalculating = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const result = await $fetch<FilingCalculationResponse>('/api/v1/epr/filing/calculate', {
          method: 'POST',
          body: { lines: valid.map(l => ({ templateId: l.templateId, quantityPcs: l.quantityPcs })) },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.serverResult = result
        // Merge backend results into lines
        for (const r of result.lines) {
          const line = this.lines.find(l => l.templateId === r.templateId)
          if (line) {
            line.totalWeightGrams = r.totalWeightGrams
            line.totalWeightKg = r.totalWeightKg
            line.feeAmountHuf = r.feeAmountHuf
          }
        }
      }
      catch (e: unknown) {
        this.error = e instanceof Error ? e.message : String(e)
        throw e
      }
      finally {
        this.isCalculating = false
      }
    },

    async exportOkirkapu(from: string, to: string, taxNumber: string) {
      this.isExporting = true
      this.exportError = null
      try {
        const config = useRuntimeConfig()
        const blob = await $fetch<Blob>('/api/v1/epr/filing/okirkapu-export', {
          method: 'POST',
          body: { from, to, taxNumber },
          responseType: 'blob',
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        if (import.meta.client) {
          const quarter = Math.ceil((new Date(from).getMonth() + 1) / 3)
          const year = new Date(from).getFullYear()
          const url = URL.createObjectURL(blob)
          const anchor = document.createElement('a')
          anchor.href = url
          anchor.download = `okir-kg-kgyf-ne-${year}-Q${quarter}.zip`
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
      this.lines = []
      this.serverResult = null
      this.isCalculating = false
      this.isExporting = false
      this.error = null
      this.exportError = null
    },
  },
})
