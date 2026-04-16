import { useApi } from '~/composables/api/useApi'

export interface KfSuggestionDto {
  kfCode: string
  description: string
  score: number
  layer: 'primary' | 'secondary' | 'tertiary'
  weightEstimateKg: number | null
  unitsPerProduct: number
}

export interface ClassifyResponse {
  suggestions: KfSuggestionDto[]
  strategy: string
  confidence: string
  modelVersion: string | null
}

/**
 * Composable for AI-assisted KF-code classification.
 * Wraps POST /api/v1/registry/classify (Story 9.3).
 */
export function useClassifier() {
  const { apiFetch } = useApi()

  function classify(body: { productName: string; vtsz?: string | null }): Promise<ClassifyResponse> {
    return apiFetch<ClassifyResponse>('/api/v1/registry/classify', { method: 'POST', body })
  }

  return { classify }
}
