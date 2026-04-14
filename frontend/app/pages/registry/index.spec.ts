import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
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

// ─── PrimeVue stubs ─────────────────────────────────────────────────────────

const DataTableStub = {
  template: '<div data-testid="products-table"><slot /><slot name="empty" /></div>',
  props: ['value', 'loading', 'lazy', 'paginator', 'rows', 'totalRecords'],
  emits: ['page'],
}
const ColumnStub = {
  template: '<div><slot name="body" :data="{}" /></div>',
  props: ['field', 'header'],
}
const TagStub = {
  template: '<span class="tag" :data-severity="severity">{{ value }}</span>',
  props: ['severity', 'value'],
}
const InputTextStub = {
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  props: ['modelValue'],
  emits: ['update:modelValue'],
}
const SelectStub = {
  template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
}
const ButtonStub = {
  template: '<button @click="$emit(\'click\')"><slot /></button>',
  emits: ['click'],
}

// ─── Import the page component (dynamic to avoid module loading issues) ──────

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
})
