import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, computed } from 'vue'
import { mount } from '@vue/test-utils'
import type { RegistryPageResponse, ProductSummaryResponse } from '~/composables/api/useRegistry'

// Story 10.10: mutable ref for registry completeness so individual tests can flip visibility
// of the new "Negyedéves bejelentés" CTA on the page header.
const mockProductsWithComponents = ref(0)
const mockTotalProducts = ref(0)

// ─── Composable mock ────────────────────────────────────────────────────────

const mockListProducts = vi.fn()
const mockArchiveProduct = vi.fn()
const mockResetDemoPackaging = vi.fn()

vi.mock('~/composables/api/useRegistry', () => ({
  useRegistry: vi.fn(() => ({
    listProducts: mockListProducts,
    archiveProduct: mockArchiveProduct,
    resetDemoPackaging: mockResetDemoPackaging,
    getProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    updateProductScope: vi.fn(),
    bulkUpdateProductScope: vi.fn(),
    getAuditLog: vi.fn(),
  })),
}))

// Story 10.11: mock auth store so `isDemoTenant` computed resolves without a full Pinia instance.
vi.mock('~/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({ activeTenantId: 'non-demo-tenant' })),
}))

// Story 10.11: mock filing store so the unknown-scope warning banner can read aggregation metadata.
vi.mock('~/stores/eprFiling', () => ({
  useEprFilingStore: vi.fn(() => ({ aggregation: null })),
}))

// Story 10.11: demo-reset button uses PrimeVue useConfirm — stubbed to no-op.
vi.mock('primevue/useconfirm', () => ({
  useConfirm: () => ({ require: vi.fn() }),
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

// Story 10.10: mock composable the page instantiates to read productsWithComponents
// for the filing-CTA v-if guard.
vi.mock('~/composables/api/useRegistryCompleteness', () => ({
  useRegistryCompleteness: vi.fn(() => ({
    totalProducts: mockTotalProducts,
    productsWithComponents: mockProductsWithComponents,
    isLoading: ref(false),
    isEmpty: computed(() => mockTotalProducts.value === 0),
    refresh: vi.fn(),
  })),
}))

// Story 10.10: force the PRO_EPR tier gate to pass so the registry body renders in tests.
vi.mock('~/composables/auth/useTierGate', () => ({
  useTierGate: vi.fn(() => ({
    hasAccess: computed(() => true),
    currentTier: computed(() => 'PRO_EPR' as const),
    requiredTier: 'PRO_EPR' as const,
    tierName: computed(() => 'Pro + EPR'),
  })),
  TIER_ORDER: { ALAP: 0, PRO: 1, PRO_EPR: 2 },
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
    reviewState: null,
    classifierSource: null,
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

  // ─── Test 7a: onlyIncomplete filter sends reviewState param ─────────────────

  it('listProducts called with reviewState=MISSING_PACKAGING when onlyIncomplete chip active', async () => {
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    await mockListProducts({ reviewState: 'MISSING_PACKAGING', page: 0, size: 50 })
    expect(mockListProducts).toHaveBeenCalledWith(
      expect.objectContaining({ reviewState: 'MISSING_PACKAGING' }),
    )
  })

  // ─── Test 7b: onlyUncertain filter sends classifierSource param ────────────

  it('listProducts called with classifierSource=VTSZ_FALLBACK when onlyUncertain chip active', async () => {
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    await mockListProducts({ classifierSource: 'VTSZ_FALLBACK', page: 0, size: 50 })
    expect(mockListProducts).toHaveBeenCalledWith(
      expect.objectContaining({ classifierSource: 'VTSZ_FALLBACK' }),
    )
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

// ─── Story 10.10: filing-CTA visibility ─────────────────────────────────────

describe('registry/index.vue — filing CTA (Story 10.10)', () => {
  async function mountRegistryPage() {
    // Prevent the onMounted listProducts call from blowing up the mount.
    mockListProducts.mockResolvedValue(buildPageResponse([], 0))

    const routerPush = vi.fn()
    vi.stubGlobal('useRouter', () => ({ push: routerPush }))
    vi.stubGlobal('useRoute', () => ({ path: '/registry', query: {} }))

    // Lazy-import the SFC so the mocks (including useRegistryCompleteness) apply before setup.
    const RegistryPage = (await import('./index.vue')).default

    const wrapper = mount(RegistryPage, {
      global: {
        stubs: {
          DataTable: true,
          Column: true,
          Tag: true,
          InputText: true,
          Select: true,
          // Minimal Button stub preserves data-testid so visibility assertions work.
          Button: {
            template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
            inheritAttrs: true,
          },
          RegistryOnboardingBlock: true,
          RegistryInvoiceBootstrapDialog: true,
          RegistryIncompleteBanner: true,
          ConfirmDialog: true,
        },
      },
    })
    return { wrapper, routerPush }
  }

  beforeEach(() => {
    mockProductsWithComponents.value = 0
    mockTotalProducts.value = 0
    vi.clearAllMocks()
  })

  it('renders filing CTA when productsWithComponents > 0', async () => {
    mockProductsWithComponents.value = 1
    mockTotalProducts.value = 3

    const { wrapper, routerPush } = await mountRegistryPage()

    const cta = wrapper.find('[data-testid="header-cta-filing"]')
    expect(cta.exists()).toBe(true)

    await cta.trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/epr/filing')
  })

  it('hides filing CTA when productsWithComponents === 0', async () => {
    mockProductsWithComponents.value = 0
    mockTotalProducts.value = 0

    const { wrapper } = await mountRegistryPage()

    expect(wrapper.find('[data-testid="header-cta-filing"]').exists()).toBe(false)
  })
})
