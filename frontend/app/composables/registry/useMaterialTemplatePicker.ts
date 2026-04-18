import { useApi } from '~/composables/api/useApi'
import type { MaterialTemplateResponse } from '~/types/epr'

/**
 * Story 10.1 — Registry-scoped picker over the internal material-template library.
 *
 * <p>After 10.1 removes the standalone Anyagkönyvtár page, {@code epr_material_templates}
 * survives only as a building-block table referenced via {@code material_template_id}
 * on {@code product_packaging_components}. This composable is the only frontend entry
 * point to material-template data outside of {@code components/registry/*}.
 *
 * <p>Picker-isolation is enforced at build time by the ESLint rules added in Task 10
 * (no-restricted-imports + no-restricted-syntax on the URL literal). Importing this
 * composable from anywhere outside {@code components/registry/*} or
 * {@code composables/registry/*} will fail the lint step.
 *
 * <p>Backend contract: the existing {@code /api/v1/epr/materials} CRUD endpoints
 * (Story 4.1) are reused unchanged. No new backend routes are required for the picker.
 */
export interface MaterialTemplateDraft {
  name: string
  baseWeightGrams?: number
  kfCode?: string | null
  materialClassification?: string | null
}

export function useMaterialTemplatePicker() {
  const { apiFetch } = useApi()

  async function list(page: number = 0, size: number = 50): Promise<MaterialTemplateResponse[]> {
    const clampedPage = Math.max(0, Math.floor(page))
    const clampedSize = Math.min(Math.max(1, Math.floor(size)), 200)
    const all = await apiFetch<MaterialTemplateResponse[]>('/api/v1/epr/materials')
    const start = clampedPage * clampedSize
    return all.slice(start, start + clampedSize)
  }

  async function search(term: string): Promise<MaterialTemplateResponse[]> {
    const normalised = term.trim().toLowerCase()
    if (!normalised) return []
    const all = await apiFetch<MaterialTemplateResponse[]>('/api/v1/epr/materials')
    return all.filter((t) => t.name.toLowerCase().includes(normalised))
  }

  async function createDraft(draft: MaterialTemplateDraft): Promise<MaterialTemplateResponse> {
    if (!draft.name || !draft.name.trim()) {
      throw new Error('useMaterialTemplatePicker.createDraft: name is required')
    }
    // Backend requires baseWeightGrams > 0. Draft picker flow accepts a minimal placeholder
    // so the template can be saved before the user has weighed the material; users refine
    // later via the full template editor.
    const baseWeightGrams = draft.baseWeightGrams && draft.baseWeightGrams > 0
      ? draft.baseWeightGrams
      : 1
    return await apiFetch<MaterialTemplateResponse>('/api/v1/epr/materials', {
      method: 'POST',
      body: {
        name: draft.name.trim(),
        baseWeightGrams,
        recurring: true,
        ...(draft.kfCode !== undefined && { kfCode: draft.kfCode }),
        ...(draft.materialClassification !== undefined && { materialClassification: draft.materialClassification })
      }
    })
  }

  return { list, search, createDraft }
}
