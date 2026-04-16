import { useApi } from '~/composables/api/useApi'

export interface ComponentResponse {
  id: string
  productId: string
  materialDescription: string
  kfCode: string | null
  weightPerUnitKg: number
  componentOrder: number
  unitsPerProduct: number
  recyclabilityGrade: 'A' | 'B' | 'C' | 'D' | null
  recycledContentPct: number | null
  reusable: boolean | null
  substancesOfConcern: unknown | null
  supplierDeclarationRef: string | null
  createdAt: string
  updatedAt: string
}

export interface ProductResponse {
  id: string
  tenantId: string
  articleNumber: string | null
  name: string
  vtsz: string | null
  primaryUnit: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  components: ComponentResponse[]
  createdAt: string
  updatedAt: string
}

export interface ProductSummaryResponse {
  id: string
  articleNumber: string | null
  name: string
  vtsz: string | null
  primaryUnit: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  componentCount: number
  updatedAt: string
}

export interface RegistryPageResponse {
  items: ProductSummaryResponse[]
  total: number
  page: number
  size: number
}

export interface RegistryAuditEntryResponse {
  id: string
  productId: string
  fieldChanged: string
  oldValue: string | null
  newValue: string | null
  changedByUserId: string | null
  source: 'MANUAL' | 'AI_SUGGESTED_CONFIRMED' | 'AI_SUGGESTED_EDITED' | 'VTSZ_FALLBACK' | 'NAV_BOOTSTRAP'
  timestamp: string
}

export interface RegistryAuditPageResponse {
  items: RegistryAuditEntryResponse[]
  total: number
  page: number
  size: number
}

export interface ComponentUpsertRequest {
  id?: string | null
  materialDescription: string
  kfCode: string | null
  weightPerUnitKg: number
  componentOrder: number
  unitsPerProduct: number
  recyclabilityGrade?: 'A' | 'B' | 'C' | 'D' | null
  recycledContentPct?: number | null
  reusable?: boolean | null
  substancesOfConcern?: unknown | null
  supplierDeclarationRef?: string | null
}

export interface ProductUpsertRequest {
  articleNumber?: string | null
  name: string
  vtsz?: string | null
  primaryUnit: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  components: ComponentUpsertRequest[]
}

export interface RegistryListFilter {
  q?: string | null
  vtsz?: string | null
  kfCode?: string | null
  status?: 'ACTIVE' | 'ARCHIVED' | 'DRAFT' | null
  page?: number
  size?: number
}

export function useRegistry() {
  const { apiFetch } = useApi()

  function listProducts(filter: RegistryListFilter = {}): Promise<RegistryPageResponse> {
    const params: Record<string, string | number> = {
      page: filter.page ?? 0,
      size: filter.size ?? 50,
    }
    if (filter.q) params.q = filter.q
    if (filter.vtsz) params.vtsz = filter.vtsz
    if (filter.kfCode) params.kfCode = filter.kfCode
    if (filter.status) params.status = filter.status

    return apiFetch<RegistryPageResponse>('/api/v1/registry', { params })
  }

  function getProduct(id: string): Promise<ProductResponse> {
    return apiFetch<ProductResponse>(`/api/v1/registry/${id}`)
  }

  function createProduct(body: ProductUpsertRequest): Promise<ProductResponse> {
    return apiFetch<ProductResponse>('/api/v1/registry', { method: 'POST', body })
  }

  function updateProduct(id: string, body: ProductUpsertRequest): Promise<ProductResponse> {
    return apiFetch<ProductResponse>(`/api/v1/registry/${id}`, { method: 'PUT', body })
  }

  function archiveProduct(id: string): Promise<void> {
    return apiFetch<void>(`/api/v1/registry/${id}/archive`, { method: 'POST' })
  }

  function getAuditLog(id: string, page = 0, size = 50): Promise<RegistryAuditPageResponse> {
    return apiFetch<RegistryAuditPageResponse>(`/api/v1/registry/${id}/audit-log`, {
      params: { page, size },
    })
  }

  return { listProducts, getProduct, createProduct, updateProduct, archiveProduct, getAuditLog }
}
