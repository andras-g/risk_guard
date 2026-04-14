import { useApi } from '~/composables/api/useApi'

export interface BootstrapCandidateResponse {
  id: string
  tenantId: string
  productName: string
  vtsz: string | null
  frequency: number
  totalQuantity: number
  unitOfMeasure: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED_NOT_OWN_PACKAGING' | 'NEEDS_MANUAL_ENTRY'
  suggestedKfCode: string | null
  suggestedComponents: string | null
  classificationStrategy: string | null
  classificationConfidence: string | null
  resultingProductId: string | null
  createdAt: string
  updatedAt: string
}

export interface BootstrapCandidatesPageResponse {
  items: BootstrapCandidateResponse[]
  total: number
  page: number
  size: number
}

export interface BootstrapResultResponse {
  created: number
  skipped: number
}

export interface BootstrapApproveRequestBody {
  articleNumber?: string | null
  name: string
  vtsz?: string | null
  primaryUnit: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  components: Array<{
    id?: string | null
    materialDescription: string
    kfCode: string | null
    weightPerUnitKg: number
    componentOrder: number
    recyclabilityGrade?: string | null
    recycledContentPct?: number | null
    reusable?: boolean | null
    substancesOfConcern?: unknown | null
    supplierDeclarationRef?: string | null
  }>
}

export function useBootstrap() {
  const { apiFetch } = useApi()

  function triggerBootstrap(from: string, to: string): Promise<BootstrapResultResponse> {
    return apiFetch<BootstrapResultResponse>('/api/v1/registry/bootstrap', {
      method: 'POST',
      body: { from, to },
    })
  }

  function listCandidates(
    status?: string | null,
    page = 0,
    size = 50,
  ): Promise<BootstrapCandidatesPageResponse> {
    const params: Record<string, string | number> = { page, size }
    if (status) params.status = status
    return apiFetch<BootstrapCandidatesPageResponse>('/api/v1/registry/bootstrap/candidates', {
      params,
    })
  }

  function approveCandidate(
    id: string,
    body: BootstrapApproveRequestBody,
  ): Promise<BootstrapCandidateResponse> {
    return apiFetch<BootstrapCandidateResponse>(
      `/api/v1/registry/bootstrap/candidates/${id}/approve`,
      { method: 'POST', body },
    )
  }

  function rejectCandidate(
    id: string,
    rejectionReason: 'NOT_OWN_PACKAGING' | 'NEEDS_MANUAL',
  ): Promise<void> {
    return apiFetch<void>(`/api/v1/registry/bootstrap/candidates/${id}/reject`, {
      method: 'POST',
      body: { rejectionReason },
    })
  }

  return { triggerBootstrap, listCandidates, approveCandidate, rejectCandidate }
}
