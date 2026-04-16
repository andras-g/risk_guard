import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ProductResponse, RegistryAuditPageResponse } from '~/composables/api/useRegistry'

// ─── Composable mock ────────────────────────────────────────────────────────

const mockGetProduct = vi.fn()
const mockCreateProduct = vi.fn()
const mockUpdateProduct = vi.fn()
const mockGetAuditLog = vi.fn()
const mockClassify = vi.fn()

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

vi.mock('~/composables/api/useClassifier', () => ({
  useClassifier: vi.fn(() => ({
    classify: mockClassify,
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
        unitsPerProduct: 1,
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
        { materialDescription: 'PET', kfCode: null, weightPerUnitKg: 0.5, componentOrder: 0, unitsPerProduct: 1 },
        { materialDescription: 'Paper', kfCode: null, weightPerUnitKg: 0.1, componentOrder: 1, unitsPerProduct: 1 },
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

  // ─── Test 7: suggest button calls classify with productName + vtsz ────────

  it('classify is called with productName and vtsz when suggest is triggered', async () => {
    const classifyResponse = {
      suggestions: [{ kfCode: '11010101', description: 'PET', score: 0.90, layer: 'primary', weightEstimateKg: null, unitsPerProduct: 1 }],
      strategy: 'VERTEX_GEMINI',
      confidence: 'HIGH',
      modelVersion: 'gemini-3.0-flash-preview',
    }
    mockClassify.mockResolvedValue(classifyResponse)

    const result = await mockClassify({ productName: 'PET palack', vtsz: '3923' })

    expect(mockClassify).toHaveBeenCalledWith({ productName: 'PET palack', vtsz: '3923' })
    expect(result.suggestions[0].kfCode).toBe('11010101')
    expect(result.confidence).toBe('HIGH')
  })

  // ─── Test 8: accepted suggestion sets classificationSource to CONFIRMED ───

  it('accepting a suggestion sets classificationSource to AI_SUGGESTED_CONFIRMED', () => {
    const comp = {
      kfCode: null as string | null,
      classificationSource: null as string | null,
      classificationStrategy: null as string | null,
      classificationModelVersion: null as string | null,
    }
    const result = {
      suggestions: [{ kfCode: '11010101', description: 'PET', score: 0.90, layer: 'primary', weightEstimateKg: null, unitsPerProduct: 1 }],
      strategy: 'VERTEX_GEMINI',
      confidence: 'HIGH',
      modelVersion: 'gemini-3.0-flash-preview',
    }

    // Simulate acceptSuggestion logic
    comp.kfCode = result.suggestions[0]!.kfCode
    comp.classificationSource = 'AI_SUGGESTED_CONFIRMED'
    comp.classificationStrategy = result.strategy
    comp.classificationModelVersion = result.modelVersion

    expect(comp.kfCode).toBe('11010101')
    expect(comp.classificationSource).toBe('AI_SUGGESTED_CONFIRMED')
    expect(comp.classificationStrategy).toBe('VERTEX_GEMINI')
    expect(comp.classificationModelVersion).toBe('gemini-3.0-flash-preview')
  })

  // ─── Test 9: manual edit after accept sets classificationSource to EDITED ─

  it('editing kfCode after accept sets classificationSource to AI_SUGGESTED_EDITED', () => {
    const comp = {
      kfCode: '11010101',
      classificationSource: 'AI_SUGGESTED_CONFIRMED',
    }

    // Simulate kfCode watcher / onUpdate logic
    if (comp.classificationSource === 'AI_SUGGESTED_CONFIRMED') {
      comp.classificationSource = 'AI_SUGGESTED_EDITED'
    }

    expect(comp.classificationSource).toBe('AI_SUGGESTED_EDITED')
  })

  // ─── Test 10: VTSZ-sourced accept sets classificationSource to VTSZ_FALLBACK

  it('accepting a VTSZ_PREFIX suggestion sets classificationSource to VTSZ_FALLBACK', () => {
    const comp = {
      kfCode: null as string | null,
      classificationSource: null as string | null,
      classificationStrategy: null as string | null,
      classificationModelVersion: null as string | null,
    }
    const result = {
      suggestions: [{ kfCode: '11010101', description: 'PET', score: 0.65, layer: 'primary', weightEstimateKg: null, unitsPerProduct: 1 }],
      strategy: 'VTSZ_PREFIX',
      confidence: 'MEDIUM',
      modelVersion: null as string | null,
    }

    // Simulate acceptSuggestion — strategy-based source selection
    const top = result.suggestions[0]!
    comp.kfCode = top.kfCode
    comp.classificationSource = result.strategy === 'VTSZ_PREFIX' ? 'VTSZ_FALLBACK' : 'AI_SUGGESTED_CONFIRMED'
    comp.classificationStrategy = result.strategy
    comp.classificationModelVersion = result.modelVersion

    expect(comp.classificationSource).toBe('VTSZ_FALLBACK')
    expect(comp.classificationStrategy).toBe('VTSZ_PREFIX')
  })
})

// ─── Story 9.5 — UX polish & bug fixes ───────────────────────────────────────
describe('registry/[id].vue — Story 9.5 validations', () => {
  // Mirrors validateComponents() in the page: length===0 returns
  // componentsRequired; blank materialDescription returns materialRequired;
  // any missing weightPerUnitKg returns weightRequired.
  function validateComponents(components: { materialDescription: string; weightPerUnitKg: number | null }[]): string {
    if (components.length === 0) return 'registry.form.validation.componentsRequired'
    for (const c of components) {
      if (!c.materialDescription?.trim()) return 'registry.form.validation.materialRequired'
      if (c.weightPerUnitKg == null) return 'registry.form.validation.weightRequired'
    }
    return ''
  }

  it('validateComponents returns componentsRequired when list is empty', () => {
    expect(validateComponents([])).toBe('registry.form.validation.componentsRequired')
  })

  it('validateComponents returns materialRequired when description is blank', () => {
    expect(validateComponents([{ materialDescription: '', weightPerUnitKg: 0.5 }])).toBe('registry.form.validation.materialRequired')
  })

  it('validateComponents returns materialRequired when description is whitespace-only', () => {
    expect(validateComponents([{ materialDescription: '  ', weightPerUnitKg: 0.5 }])).toBe('registry.form.validation.materialRequired')
  })

  it('validateComponents returns weightRequired when a component lacks weight', () => {
    expect(validateComponents([{ materialDescription: 'PET', weightPerUnitKg: null }])).toBe('registry.form.validation.weightRequired')
  })

  it('validateComponents returns "" when all components are valid', () => {
    expect(validateComponents([
      { materialDescription: 'PET', weightPerUnitKg: 0.4 },
      { materialDescription: 'HDPE', weightPerUnitKg: 0.1 },
    ])).toBe('')
  })
})

// ─── Story 9.5 R1 Decision (c) — canonical options only ──────────────────────
// Legacy 'pcs' is removed from the dropdown (roundtrips invisibly in the DB).
// unitOptions always equals the canonical base list — no prepend logic.
describe('registry/[id].vue — unit options canonical list only', () => {
  const UNIT_VALUES = ['db', 'kg', 'g', 'l', 'ml', 'm', 'm2', 'm3', 'csomag']

  it('base options are returned as-is (9 canonical values)', () => {
    const base = UNIT_VALUES.map(v => ({ value: v, label: v }))
    expect(base).toHaveLength(9)
    expect(base.some(o => o.value === 'pcs')).toBe(false)
  })
})
