import { useApi } from '~/composables/api/useApi'

export interface BootstrapTriggerRequest {
  periodFrom?: string | null
  periodTo?: string | null
}

export interface BootstrapJobCreatedResponse {
  jobId: string
  location: string
}

export type BootstrapJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'FAILED_PARTIAL' | 'CANCELLED'

export interface BootstrapJobStatusResponse {
  jobId: string
  status: BootstrapJobStatus
  periodFrom: string
  periodTo: string
  totalPairs: number
  classifiedPairs: number
  vtszFallbackPairs: number
  unresolvedPairs: number
  createdProducts: number
  deletedProducts: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

const BASE = '/api/v1/registry/bootstrap-from-invoices'

export function useInvoiceBootstrap() {
  const { apiFetch } = useApi()

  function triggerBootstrap(periodFrom?: string | null, periodTo?: string | null): Promise<BootstrapJobCreatedResponse> {
    return apiFetch<BootstrapJobCreatedResponse>(BASE, {
      method: 'POST',
      body: { periodFrom: periodFrom ?? null, periodTo: periodTo ?? null },
    })
  }

  function getJobStatus(jobId: string): Promise<BootstrapJobStatusResponse> {
    return apiFetch<BootstrapJobStatusResponse>(`${BASE}/${jobId}`)
  }

  function cancelJob(jobId: string): Promise<void> {
    return apiFetch<void>(`${BASE}/${jobId}`, { method: 'DELETE' })
  }

  return { triggerBootstrap, getJobStatus, cancelJob }
}
