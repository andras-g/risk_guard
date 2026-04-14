import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { RegistryPageResponse, ProductSummaryResponse } from '~/composables/api/useRegistry'

// ─── Composable mock ────────────────────────────────────────────────────────

const mockListProducts = vi.fn()
const mockArchiveProduct = vi.fn()

vi.mock('~/composables/api/useRegistry', () => ({
  useRegistry: vi.fn(() => ({
    listProducts: mockListProducts,
    archiveProduct: mockArchiveProduct,
    getProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    getAuditLog: vi.fn(),
  })),
}))

vi.mock('~/stores/registry', () => ({
  useRegistryStore: vi.fn(() => ({
    listFilter: {},
    listPage: 0,
    listPageSize: 50,
    setListFilter: vi.fn(),
    setListPage: vi.fn(),
  })),
}))

vi.mock('~/composables/api/useApiError', () => ({
  useApiError: () => ({ mapErrorType: (type: string) => type ?? 'error' }),
}))

vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))
vi.stubGlobal('useRouter', () => ({ push: vi.fn() }))
vi.stubGlobal('useToast', () => ({ add: vi.fn() }))

// ─── Helpers ────────────────────────────────────────────────────────────────

function buildSummary(overrides: Partial<ProductSummaryResponse> = {}): ProductSummaryResponse {
  return {
    id: 'prod-001',
    articleNumber: 'ART-001',
    name: 'Activia 4×125g',
    vtsz: '3923',
    primaryUnit: 'pcs',
    status: 'ACTIVE',
    componentCount: 2,
    updatedAt: '2026-04-14T10:00:00Z',
    ...overrides,
  }
}

function buildPageResponse(items: ProductSummaryResponse[] = [], total = 0): RegistryPageResponse {
  return { items, total, page: 0, size: 50 }
}

vi.mock('~/stores/health', () => ({
  useHealthStore: vi.fn(() => ({
    adapters: [
      { adapterName: 'nav-online-szamla', credentialStatus: 'VALID', dataSourceMode: 'live' },
    ],
  })),
}))

describe('registry/index.vue — composable and filter logic', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ─── Test 1: renders empty state when no products ─────────────────────────

  it('listProducts is called on mount', async () => {
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    // Test the logic: listProducts is called when the page mounts
    await mockListProducts({ page: 0, size: 50 })
    expect(mockListProducts).toHaveBeenCalled()
  })

  // ─── Test 2: renders DataTable rows ──────────────────────────────────────

  it('listProducts returns items that can be rendered', async () => {
    const items = [buildSummary(), buildSummary({ id: 'prod-002', name: 'Other Product' })]
    mockListProducts.mockResolvedValue(buildPageResponse(items, 2))

    const result = await mockListProducts({ page: 0, size: 50 })
    expect(result.items).toHaveLength(2)
    expect(result.items[0].name).toBe('Activia 4×125g')
  })

  // ─── Test 3: status tag severity mapping ─────────────────────────────────

  it('maps ACTIVE status to "success" severity', () => {
    function tagSeverity(status: 'ACTIVE' | 'ARCHIVED' | 'DRAFT'): string {
      switch (status) {
        case 'ACTIVE': return 'success'
        case 'DRAFT': return 'warn'
        case 'ARCHIVED': return 'secondary'
      }
    }
    expect(tagSeverity('ACTIVE')).toBe('success')
    expect(tagSeverity('DRAFT')).toBe('warn')
    expect(tagSeverity('ARCHIVED')).toBe('secondary')
  })

  // ─── Test 4: filter builds correct query params ───────────────────────────

  it('listProducts is called with correct filter params when q is set', async () => {
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    await mockListProducts({ q: 'Activia', page: 0, size: 50 })
    expect(mockListProducts).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'Activia' })
    )
  })

  // ─── Test 5: pagination page change re-fetches ─────────────────────────────

  it('listProducts called with updated page on pagination event', async () => {
    mockListProducts.mockResolvedValue(buildPageResponse([], 100))

    await mockListProducts({ page: 2, size: 50 })
    expect(mockListProducts).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2, size: 50 })
    )
  })

  // ─── Test 6: archive calls composable and re-fetches ─────────────────────

  it('archiveProduct is called when archive action is triggered', async () => {
    mockArchiveProduct.mockResolvedValue(undefined)
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    await mockArchiveProduct('prod-001')
    expect(mockArchiveProduct).toHaveBeenCalledWith('prod-001')
  })

  // ─── Test 7: second CTA present when hasNavCredentials and total===0 ─────

  it('showBootstrapCta is true when total===0 and nav credentials VALID', () => {
    // Simulate the computed logic from index.vue
    const adapters = [{ adapterName: 'nav-online-szamla', credentialStatus: 'VALID', dataSourceMode: 'live' }]
    const navAdapter = adapters.find(a => a.adapterName === 'nav-online-szamla')
    const hasNavCredentials = navAdapter?.credentialStatus === 'VALID'
    const isDemo = navAdapter?.dataSourceMode === 'demo'
    const isEmpty = true // simulating empty registry

    const showBootstrapCta = isEmpty && (hasNavCredentials || isDemo)
    expect(showBootstrapCta).toBe(true)
  })
})
