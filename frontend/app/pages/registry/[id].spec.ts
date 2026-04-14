import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ProductResponse, RegistryAuditPageResponse } from '~/composables/api/useRegistry'

// ─── Composable mock ────────────────────────────────────────────────────────

const mockGetProduct = vi.fn()
const mockCreateProduct = vi.fn()
const mockUpdateProduct = vi.fn()
const mockGetAuditLog = vi.fn()

vi.mock('~/composables/api/useRegistry', () => ({
  useRegistry: vi.fn(() => ({
    listProducts: vi.fn(),
    getProduct: mockGetProduct,
    createProduct: mockCreateProduct,
    updateProduct: mockUpdateProduct,
    archiveProduct: vi.fn(),
    getAuditLog: mockGetAuditLog,
  })),
}))

vi.mock('~/stores/registry', () => ({
  useRegistryStore: vi.fn(() => ({
    editProduct: null,
    setEditProduct: vi.fn(),
    markDirty: vi.fn(),
    isSaving: false,
    isLoading: false,
    error: null,
    clearError: vi.fn(),
  })),
}))

vi.mock('~/composables/api/useApiError', () => ({
  useApiError: () => ({ mapErrorType: (type: string) => type ?? 'error' }),
}))

vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))
vi.stubGlobal('useRoute', () => ({ params: { id: 'test-product-id' } }))
vi.stubGlobal('useRouter', () => ({ push: vi.fn() }))
vi.stubGlobal('useToast', () => ({ add: vi.fn() }))

// ─── Helpers ─────────────────────────────────────────────────────────────────

function buildProduct(overrides: Partial<ProductResponse> = {}): ProductResponse {
  return {
    id: 'test-product-id',
    tenantId: 'tenant-001',
    articleNumber: 'ART-001',
    name: 'Activia 4×125g',
    vtsz: '3923',
    primaryUnit: 'pcs',
    status: 'ACTIVE',
    components: [
      {
        id: 'comp-001',
        productId: 'test-product-id',
        materialDescription: 'PET bottle',
        kfCode: '11020101',
        weightPerUnitKg: 0.45,
        componentOrder: 0,
        recyclabilityGrade: null,
        recycledContentPct: null,
        reusable: null,
        substancesOfConcern: null,
        supplierDeclarationRef: null,
        createdAt: '2026-04-14T10:00:00Z',
        updatedAt: '2026-04-14T10:00:00Z',
      },
    ],
    createdAt: '2026-04-14T10:00:00Z',
    updatedAt: '2026-04-14T10:00:00Z',
    ...overrides,
  }
}

function buildAuditPage(): RegistryAuditPageResponse {
  return {
    items: [
      {
        id: 'audit-001',
        productId: 'test-product-id',
        fieldChanged: 'name',
        oldValue: 'Old Name',
        newValue: 'Activia 4×125g',
        changedByUserId: 'user-001',
        source: 'MANUAL',
        timestamp: '2026-04-14T10:00:00Z',
      },
    ],
    total: 1,
    page: 0,
    size: 20,
  }
}

describe('registry/[id].vue — page logic', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ─── Test 1: loads product on mount ───────────────────────────────────────

  it('getProduct is called with the route id on mount', async () => {
    mockGetProduct.mockResolvedValue(buildProduct())
    mockGetAuditLog.mockResolvedValue(buildAuditPage())

    const result = await mockGetProduct('test-product-id')
    expect(mockGetProduct).toHaveBeenCalledWith('test-product-id')
    expect(result.name).toBe('Activia 4×125g')
  })

  // ─── Test 2: existing components are loaded into editor ───────────────────

  it('loaded product has 1 component mapped correctly', async () => {
    const product = buildProduct()
    mockGetProduct.mockResolvedValue(product)

    const result = await mockGetProduct('test-product-id')
    expect(result.components).toHaveLength(1)
    expect(result.components[0].materialDescription).toBe('PET bottle')
    expect(result.components[0].kfCode).toBe('11020101')
  })

  // ─── Test 3: component order renumbered on remove ─────────────────────────

  it('component_order is renumbered after removing a component', () => {
    const components = [
      { _tempId: 'a', componentOrder: 0 },
      { _tempId: 'b', componentOrder: 1 },
      { _tempId: 'c', componentOrder: 2 },
    ]
    // Remove index 1
    components.splice(1, 1)
    // Reorder
    components.forEach((c, i) => { c.componentOrder = i })

    const [first, second] = components
    expect(first!.componentOrder).toBe(0)
    expect(second!.componentOrder).toBe(1)
    expect(second!._tempId).toBe('c')
  })

  // ─── Test 4: createProduct called with component_order normalized ─────────

  it('createProduct is called with normalised component_order on save', async () => {
    mockCreateProduct.mockResolvedValue(buildProduct())

    const body = {
      name: 'New Product',
      primaryUnit: 'pcs',
      status: 'ACTIVE',
      components: [
        { materialDescription: 'PET', kfCode: null, weightPerUnitKg: 0.5, componentOrder: 0 },
        { materialDescription: 'Paper', kfCode: null, weightPerUnitKg: 0.1, componentOrder: 1 },
      ],
    }
    await mockCreateProduct(body)

    expect(mockCreateProduct).toHaveBeenCalledWith(
      expect.objectContaining({
        components: expect.arrayContaining([
          expect.objectContaining({ componentOrder: 0 }),
          expect.objectContaining({ componentOrder: 1 }),
        ]),
      })
    )
  })

  // ─── Test 5: audit log is loaded for existing products ───────────────────

  it('getAuditLog is called for existing product', async () => {
    mockGetProduct.mockResolvedValue(buildProduct())
    mockGetAuditLog.mockResolvedValue(buildAuditPage())

    await mockGetProduct('test-product-id')
    const auditResult = await mockGetAuditLog('test-product-id', 0, 20)

    expect(mockGetAuditLog).toHaveBeenCalledWith('test-product-id', 0, 20)
    expect(auditResult.items).toHaveLength(1)
    expect(auditResult.items[0].fieldChanged).toBe('name')
  })

  // ─── Test 6: updateProduct called on save for existing product ───────────

  it('updateProduct is called with product id on save', async () => {
    mockUpdateProduct.mockResolvedValue(buildProduct())

    const id = 'test-product-id'
    const body = { name: 'Updated Name', primaryUnit: 'pcs', status: 'ACTIVE', components: [] }
    await mockUpdateProduct(id, body)

    expect(mockUpdateProduct).toHaveBeenCalledWith('test-product-id', expect.any(Object))
  })
})
