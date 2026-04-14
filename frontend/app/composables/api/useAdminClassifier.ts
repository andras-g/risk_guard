import { useApi } from '~/composables/api/useApi'

export interface ClassifierUsageSummaryResponse {
  tenantId: string
  tenantName: string
  callCount: number
  estimatedCostFt: number
}

/**
 * Composable for PLATFORM_ADMIN AI classifier usage endpoint.
 * Wraps GET /api/v1/admin/classifier/usage (Story 9.3 AC 13).
 */
export function useAdminClassifier() {
  const { apiFetch } = useApi()

  function getUsage(): Promise<ClassifierUsageSummaryResponse[]> {
    return apiFetch<ClassifierUsageSummaryResponse[]>('/api/v1/admin/classifier/usage')
  }

  return { getUsage }
}
