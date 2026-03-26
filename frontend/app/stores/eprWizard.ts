import { defineStore } from 'pinia'
import type {
  WizardOption,
  WizardSelection,
  WizardStartResponse,
  WizardStepResponse,
  WizardResolveResponse,
  WizardConfirmResponse,
  KfCodeEntry,
  KfCodeListResponse,
  RetryLinkResponse,
} from '~/types/epr'
import { useEprStore } from '~/stores/epr'

interface EprWizardState {
  activeStep: string | null
  traversalPath: WizardSelection[]
  availableOptions: WizardOption[]
  resolvedResult: WizardResolveResponse | null
  isLoading: boolean
  error: string | null
  targetTemplateId: string | null
  configVersion: number | null
  /**
   * Set to true after a successful confirmAndLink, before $reset clears other state.
   * The page reads this synchronously in its isActive watcher to distinguish
   * a successful confirm close from a cancel close. $reset() will clear it back to false,
   * but the watcher reads it before the reset propagates.
   */
  lastConfirmSuccess: boolean
  /**
   * True when confirm succeeded (calculation saved) but template link failed.
   * The wizard stays open to offer retry/close options.
   */
  linkFailed: boolean
  /**
   * The calculation ID from the last confirm response — used for retry-link calls.
   */
  lastCalculationId: string | null
  /**
   * Reason for closing the wizard — used by the page watcher to show differentiated toasts.
   * 'success' = confirm+link succeeded; 'unlinked' = closed without linking after link failure.
   */
  lastCloseReason: 'success' | 'unlinked' | null
  // ─── Override state ────────────────────────────────────────────────────
  overrideKfCode: string | null
  overrideReason: string | null
  overrideFeeRate: number | null
  overrideClassification: string | null
  allKfCodes: KfCodeEntry[] | null
  isOverrideActive: boolean
}

/**
 * EPR Wizard store — manages wizard state separately from material CRUD.
 *
 * Uses options-style to match existing `useEprStore` pattern.
 * The global `$fetch` interceptor handles Accept-Language headers automatically.
 */
export const useEprWizardStore = defineStore('eprWizard', {
  state: (): EprWizardState => ({
    activeStep: null,
    traversalPath: [],
    availableOptions: [],
    resolvedResult: null,
    isLoading: false,
    error: null,
    targetTemplateId: null,
    configVersion: null,
    lastConfirmSuccess: false,
    linkFailed: false,
    lastCalculationId: null,
    lastCloseReason: null,
    overrideKfCode: null,
    overrideReason: null,
    overrideFeeRate: null,
    overrideClassification: null,
    allKfCodes: null,
    isOverrideActive: false,
  }),

  getters: {
    isActive: (state): boolean => state.activeStep !== null,
    breadcrumb: (state): WizardSelection[] => state.traversalPath,
    currentStepIndex: (state): number => state.traversalPath.length,
  },

  actions: {
    /**
     * Re-fetch current step options from the backend (e.g., after locale change).
     * Replays the entire traversal from start to get fresh localized labels
     * for both the breadcrumb and the current step's option cards.
     */
    async refreshOptions() {
      if (!this.configVersion || !this.activeStep) return
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const savedPath = [...this.traversalPath]

        // Always re-fetch step 1 options (needed even if we're past step 1,
        // because we replay selections sequentially and need localized labels)
        const startData = await $fetch<WizardStartResponse>('/api/v1/epr/wizard/start', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })

        if (savedPath.length === 0) {
          // Currently on step 1 — just update the options
          this.availableOptions = startData.options
          return
        }

        // Replay each selection to get localized breadcrumb labels.
        // Only replay levels the backend handles (product_stream, material_stream, group).
        // Subgroup selections are handled client-side (no /step call).
        let currentPath: WizardSelection[] = []
        let lastStepData: WizardStepResponse | null = null
        const steppableLevels = ['product_stream', 'material_stream', 'group']

        for (const selection of savedPath) {
          const currentOptions = lastStepData ? lastStepData.options : startData.options
          const localizedOption = currentOptions.find(o => o.code === selection.code)
          const localizedSelection: WizardSelection = {
            level: selection.level,
            code: selection.code,
            label: localizedOption?.label ?? selection.label,
          }

          if (steppableLevels.includes(selection.level)) {
            lastStepData = await $fetch<WizardStepResponse>('/api/v1/epr/wizard/step', {
              method: 'POST',
              body: {
                configVersion: this.configVersion,
                traversalPath: currentPath,
                selection: localizedSelection,
              },
              baseURL: config.public.apiBase as string,
              credentials: 'include',
            })
            currentPath = lastStepData.breadcrumb
          }
          else {
            // Subgroup: add to path with localized label (from the last step's options)
            currentPath = [...currentPath, localizedSelection]
          }
        }

        // Update state with fully localized breadcrumb and options
        this.traversalPath = currentPath
        if (lastStepData) {
          this.availableOptions = lastStepData.options
        }

        // If on the result step, re-resolve with the localized path
        if (this.activeStep === '4' && this.resolvedResult) {
          await this.resolveResult()
        }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
      }
      finally {
        this.isLoading = false
      }
    },

    /**
     * Navigate back to the previous wizard step.
     * Pops the last selection from the traversal path and re-fetches
     * the options for the current level.
     */
    async goBack() {
      if (!this.configVersion || this.traversalPath.length === 0) return
      this.isLoading = true
      this.error = null
      this.resolvedResult = null
      try {
        const config = useRuntimeConfig()
        // Remove the last selection
        const previousPath = this.traversalPath.slice(0, -1)

        if (previousPath.length === 0) {
          // Going back to step 1 — re-fetch product streams
          const data = await $fetch<WizardStartResponse>('/api/v1/epr/wizard/start', {
            baseURL: config.public.apiBase as string,
            credentials: 'include',
          })
          this.traversalPath = []
          this.availableOptions = data.options
          this.activeStep = '1'
        }
        else {
          // Replay the last remaining selection to get its child options
          const lastSelection = previousPath[previousPath.length - 1]
          const parentPath = previousPath.slice(0, -1)
          const data = await $fetch<WizardStepResponse>('/api/v1/epr/wizard/step', {
            method: 'POST',
            body: {
              configVersion: this.configVersion,
              traversalPath: parentPath,
              selection: lastSelection,
            },
            baseURL: config.public.apiBase as string,
            credentials: 'include',
          })
          this.traversalPath = data.breadcrumb
          this.availableOptions = data.options

          const stepMap: Record<string, string> = {
            material_stream: '2',
            group: '3',
            subgroup: '3',
          }
          this.activeStep = data.nextLevel ? stepMap[data.nextLevel] || '3' : '4'
        }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    async startWizard(templateId?: string) {
      this.isLoading = true
      this.error = null
      this.targetTemplateId = templateId ?? null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<WizardStartResponse>('/api/v1/epr/wizard/start', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.configVersion = data.configVersion
        this.availableOptions = data.options
        this.activeStep = '1'
        this.traversalPath = []
        this.resolvedResult = null
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    async selectOption(selection: WizardSelection, _autoAdvanceDepth = 0) {
      if (!this.configVersion) return
      // Guard against infinite recursion when backend returns consecutive autoSelect:true responses.
      // Maximum 3 auto-advance levels (product_stream → material_stream → group → subgroup).
      const MAX_AUTO_ADVANCE_DEPTH = 3
      if (_autoAdvanceDepth > MAX_AUTO_ADVANCE_DEPTH) {
        console.warn('[eprWizard] Auto-advance depth limit reached — stopping recursion')
        return
      }
      this.isLoading = true
      this.error = null
      try {
        // Subgroup is the final level — no backend /step call needed.
        // Add to traversal path and resolve directly.
        if (selection.level === 'subgroup') {
          this.traversalPath = [...this.traversalPath, selection]
          this.availableOptions = []
          this.activeStep = '4'
          await this.resolveResult()
          return
        }

        const config = useRuntimeConfig()
        const data = await $fetch<WizardStepResponse>('/api/v1/epr/wizard/step', {
          method: 'POST',
          body: {
            configVersion: this.configVersion,
            traversalPath: this.traversalPath,
            selection,
          },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.traversalPath = data.breadcrumb
        this.availableOptions = data.options

        // Determine step number based on next level
        const stepMap: Record<string, string> = {
          material_stream: '2',
          group: '3',
          subgroup: '3', // combined step for UX
        }
        this.activeStep = data.nextLevel ? stepMap[data.nextLevel] || '3' : '4'

        // If auto-select is enabled and there's exactly one option, auto-advance
        if (data.autoSelect && data.options.length === 1) {
          const autoSelection: WizardSelection = {
            level: data.nextLevel || 'subgroup',
            code: data.options[0].code,
            label: data.options[0].label,
          }
          await this.selectOption(autoSelection, _autoAdvanceDepth + 1)
          return
        }

        // If traversal is complete (4 levels selected), auto-resolve
        if (this.traversalPath.length >= 4) {
          await this.resolveResult()
        }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        // Only clear loading at the outermost call depth to prevent flicker during auto-advance recursion
        if (_autoAdvanceDepth === 0) {
          this.isLoading = false
        }
      }
    },

    async resolveResult() {
      if (!this.configVersion) return
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<WizardResolveResponse>('/api/v1/epr/wizard/resolve', {
          method: 'POST',
          body: {
            configVersion: this.configVersion,
            traversalPath: this.traversalPath,
          },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.resolvedResult = data
        this.activeStep = '4'
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    async confirmAndLink() {
      if (!this.configVersion || !this.resolvedResult) return
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const response = await $fetch<WizardConfirmResponse>('/api/v1/epr/wizard/confirm', {
          method: 'POST',
          body: {
            configVersion: this.configVersion,
            traversalPath: this.traversalPath,
            kfCode: this.resolvedResult.kfCode,
            feeRate: this.resolvedResult.feeRate,
            materialClassification: this.resolvedResult.materialClassification,
            templateId: this.targetTemplateId,
            confidenceScore: this.resolvedResult.confidenceScore,
            overrideKfCode: this.overrideKfCode,
            overrideReason: this.overrideReason,
          },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        // Refresh material list to show updated KF code / verified status
        const eprStore = useEprStore()
        await eprStore.fetchMaterials()

        if (!this.targetTemplateId || response.templateUpdated) {
          // Success path: template linked (or no template to link)
          this.lastConfirmSuccess = true
          this.isLoading = false
          this._resetWizardState()
          return
        }
        else {
          // Failure path: calculation saved but template not updated
          this.linkFailed = true
          this.lastCalculationId = response.calculationId
          // Do NOT close wizard — show retry prompt
        }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    async retryLink() {
      if (!this.lastCalculationId || !this.targetTemplateId) return
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const response = await $fetch<RetryLinkResponse>('/api/v1/epr/wizard/retry-link', {
          method: 'POST',
          body: {
            calculationId: this.lastCalculationId,
            templateId: this.targetTemplateId,
          },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        if (response.templateUpdated) {
          const eprStore = useEprStore()
          await eprStore.fetchMaterials()
          this.lastConfirmSuccess = true
          this.isLoading = false
          this._resetWizardState()
          return
        }
        else {
          // Still failed — keep retry prompt visible
          this.error = 'Template link still failing'
        }
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    closeWithoutLinking() {
      this.lastCloseReason = 'unlinked'
      this._resetWizardState()
    },

    /**
     * Resets wizard state fields without clearing lastConfirmSuccess or lastCloseReason,
     * which are read by the page's isActive watcher before being cleared.
     */
    _resetWizardState() {
      this.activeStep = null
      this.traversalPath = []
      this.availableOptions = []
      this.resolvedResult = null
      this.error = null
      this.targetTemplateId = null
      this.configVersion = null
      this.overrideKfCode = null
      this.overrideReason = null
      this.overrideFeeRate = null
      this.overrideClassification = null
      this.allKfCodes = null
      this.isOverrideActive = false
      this.linkFailed = false
      this.lastCalculationId = null
    },

    async fetchAllKfCodes() {
      if (!this.configVersion) return
      // Return cached if already loaded
      if (this.allKfCodes) return
      this.isLoading = true
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<KfCodeListResponse>('/api/v1/epr/wizard/kf-codes', {
          params: { configVersion: this.configVersion },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.allKfCodes = data.entries
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    applyOverride(entry: KfCodeEntry, reason?: string) {
      this.overrideKfCode = entry.kfCode
      this.overrideFeeRate = entry.feeRate
      this.overrideClassification = entry.classification
      this.overrideReason = reason ?? null
      this.isOverrideActive = true
    },

    clearOverride() {
      this.overrideKfCode = null
      this.overrideReason = null
      this.overrideFeeRate = null
      this.overrideClassification = null
      this.isOverrideActive = false
    },

    cancelWizard() {
      this.$reset()
    },
  },
})
