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

export type EprScope = 'FIRST_PLACER' | 'RESELLER' | 'UNKNOWN'

export interface ProductResponse {
  id: string
  tenantId: string
  articleNumber: string | null
  name: string
  vtsz: string | null
  primaryUnit: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'
  eprScope: EprScope
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
  reviewState: 'MISSING_PACKAGING' | null
  classifierSource: 'MANUAL' | 'MANUAL_WIZARD' | 'AI_SUGGESTED_CONFIRMED' | 'AI_SUGGESTED_EDITED' | 'VTSZ_FALLBACK' | 'NAV_BOOTSTRAP' | null
  componentCount: number
  updatedAt: string
  eprScope?: EprScope
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
  eprScope?: EprScope | null
  components: ComponentUpsertRequest[]
}

export interface BulkEprScopeResponse {
  updated: number
  skipped: number
}

export interface RegistryListFilter {
  q?: string | null
  vtsz?: string | null
  kfCode?: string | null
  status?: 'ACTIVE' | 'ARCHIVED' | 'DRAFT' | null
  reviewState?: 'MISSING_PACKAGING' | null
  classifierSource?: 'VTSZ_FALLBACK' | null
  onlyUnknownScope?: boolean | null
  page?: number
  size?: number
}

export function useRegistry() {
  const { apiFetch } = useApi()

  function listProducts(filter: RegistryListFilter = {}): Promise<RegistryPageResponse> {
    const params: Record<string, string | number | boolean> = {
      page: filter.page ?? 0,
      size: filter.size ?? 50,
    }
    if (filter.q) params.q = filter.q
    if (filter.vtsz) params.vtsz = filter.vtsz
    if (filter.kfCode) params.kfCode = filter.kfCode
    if (filter.status) params.status = filter.status
    if (filter.reviewState) params.reviewState = filter.reviewState
    if (filter.classifierSource) params.classifierSource = filter.classifierSource
    if (filter.onlyUnknownScope) params.onlyUnknownScope = true

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

  /** Story 10.11 AC #7 — PATCH a single product's epr_scope. */
  function updateProductScope(id: string, scope: EprScope): Promise<ProductResponse> {
    return apiFetch<ProductResponse>(`/api/v1/registry/products/${id}/epr-scope`, {
      method: 'PATCH',
      body: { scope },
    })
  }

  /** Story 10.11 AC #8 — bulk scope update. */
  function bulkUpdateProductScope(productIds: string[], scope: EprScope): Promise<BulkEprScopeResponse> {
    return apiFetch<BulkEprScopeResponse>('/api/v1/registry/products/bulk-epr-scope', {
      method: 'POST',
      body: { productIds, scope },
    })
  }

  /** Story 10.11 AC #15b — demo-only packaging reset (demo/e2e profile). */
  function resetDemoPackaging(): Promise<{ deletedComponents: number; affectedProducts: number }> {
    return apiFetch<{ deletedComponents: number; affectedProducts: number }>(
      '/api/v1/registry/demo/reset-packaging',
      { method: 'POST' }
    )
  }

  function getAuditLog(id: string, page = 0, size = 50): Promise<RegistryAuditPageResponse> {
    return apiFetch<RegistryAuditPageResponse>(`/api/v1/registry/${id}/audit-log`, {
      params: { page, size },
    })
  }

  return {
    listProducts, getProduct, createProduct, updateProduct, archiveProduct, getAuditLog,
    updateProductScope, bulkUpdateProductScope, resetDemoPackaging,
  }
}
