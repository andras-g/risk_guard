import { defineStore } from 'pinia'
import type {
  MaterialTemplateResponse,
  MaterialTemplateRequest,
  RecurringToggleRequest,
  CopyQuarterRequest,
} from '~/types/epr'

interface EprState {
  materials: MaterialTemplateResponse[]
  isLoading: boolean
  error: string | null
}

/**
 * EPR Material Library store — manages material template CRUD state.
 *
 * Uses `$fetch` directly instead of the `useApi()` composable because Pinia
 * options-style actions run outside Vue's setup context, where composables
 * like `useI18n()` and `useRuntimeConfig()` are not available.
 * The global `$fetch` interceptor (api-locale.ts plugin) handles
 * Accept-Language headers, credentials, and 401 redirects automatically.
 */
export const useEprStore = defineStore('epr', {
  state: (): EprState => ({
    materials: [],
    isLoading: false,
    error: null,
  }),

  getters: {
    totalCount: (state): number => state.materials.length,
    verifiedCount: (state): number => state.materials.filter(m => m.verified).length,
    oneTimeCount: (state): number => state.materials.filter(m => !m.recurring).length,
  },

  actions: {
    async fetchMaterials() {
      this.isLoading = true
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<MaterialTemplateResponse[]>('/api/v1/epr/materials', {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.materials = data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
      finally {
        this.isLoading = false
      }
    },

    async addMaterial(request: MaterialTemplateRequest): Promise<MaterialTemplateResponse> {
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<MaterialTemplateResponse>('/api/v1/epr/materials', {
          method: 'POST',
          body: request,
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        await this.fetchMaterials()
        return data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async updateMaterial(id: string, request: MaterialTemplateRequest): Promise<MaterialTemplateResponse> {
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<MaterialTemplateResponse>(`/api/v1/epr/materials/${id}`, {
          method: 'PUT',
          body: request,
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        await this.fetchMaterials()
        return data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async deleteMaterial(id: string) {
      this.error = null
      try {
        const config = useRuntimeConfig()
        await $fetch(`/api/v1/epr/materials/${id}`, {
          method: 'DELETE',
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        this.materials = this.materials.filter(m => m.id !== id)
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async toggleRecurring(id: string, request: RecurringToggleRequest): Promise<MaterialTemplateResponse> {
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<MaterialTemplateResponse>(`/api/v1/epr/materials/${id}/recurring`, {
          method: 'PATCH',
          body: request,
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        // Update in-place to avoid full table reload blink
        const index = this.materials.findIndex(m => m.id === id)
        if (index !== -1) {
          this.materials[index] = data
        }
        return data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },

    async copyFromQuarter(request: CopyQuarterRequest): Promise<MaterialTemplateResponse[]> {
      this.error = null
      try {
        const config = useRuntimeConfig()
        const data = await $fetch<MaterialTemplateResponse[]>('/api/v1/epr/materials/copy-from-quarter', {
          method: 'POST',
          body: request,
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        await this.fetchMaterials()
        return data
      }
      catch (error: unknown) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      }
    },
  },
})
