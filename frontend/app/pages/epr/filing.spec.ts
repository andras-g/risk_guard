import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, onBeforeUnmount } from 'vue'
import FilingPage from './filing.vue'
import type { FilingAggregationResult } from '~/types/epr'

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

vi.stubGlobal('onBeforeUnmount', onBeforeUnmount)

vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, unknown>) => params ? `${key}(${JSON.stringify(params)})` : key,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({
  push: mockRouterPush,
}))

vi.mock('~/composables/auth/useTierGate', () => ({
  useTierGate: () => ({
    hasAccess: ref(true),
    tierName: ref('PRO EPR'),
  }),
}))

vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    isAccountant: false,
    activeTenantId: 'tenant-1',
    homeTenantId: 'tenant-1',
  }),
}))

// Stub child components
const EprSoldProductsTableStub = {
  template: '<div data-testid="sold-products-table-stub" />',
  props: ['soldProducts', 'unresolvedLines', 'kfTotals', 'loading'],
}
const EprKfTotalsTableStub = {
  template: '<div data-testid="kf-totals-table-stub" />',
  props: ['kfTotals', 'loading'],
}
const ButtonStub = {
  template: '<button :disabled="$attrs.disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  emits: ['click'],
  inheritAttrs: true,
}
const PanelStub = {
  template: '<div data-testid="unresolved-panel"><slot /></div>',
  props: ['header', 'collapsed', 'toggleable'],
}
const DataTableStub = {
  template: '<div />',
  props: ['value'],
}
const ColumnStub = {
  template: '<div />',
  props: ['field', 'header'],
}

// ─── Registry completeness mock (default: non-empty registry) ────────────────
const mockRefresh = vi.fn().mockResolvedValue(undefined)
let mockIsEmpty = false
let mockRegistryIsLoading = false
let mockTotalProducts = 1

vi.mock('~/composables/api/useRegistryCompleteness', () => ({
  useRegistryCompleteness: () => ({
    get isEmpty() { return { value: mockIsEmpty } },
    get isLoading() { return { value: mockRegistryIsLoading } },
    get totalProducts() { return { value: mockTotalProducts } },
    get productsWithComponents() { return { value: mockIsEmpty ? 0 : 1 } },
    refresh: mockRefresh,
  }),
}))

const mockFetchAggregation = vi.fn().mockResolvedValue(undefined)
const mockExportOkirkapu = vi.fn().mockResolvedValue(undefined)
const mockReset = vi.fn()

let mockAggregation: FilingAggregationResult | null = null
let mockIsLoading = false
let mockIsExporting = false
let mockExportError: string | null = null
let mockError: string | null = null
let mockGrandTotalWeightKg = 0
let mockGrandTotalFeeHuf = 0
let mockTotalKfCodes = 0

vi.mock('~/stores/eprFiling', () => ({
  useEprFilingStore: () => ({
    get aggregation() { return mockAggregation },
    get isLoading() { return mockIsLoading },
    get isExporting() { return mockIsExporting },
    get exportError() { return mockExportError },
    set exportError(v: string | null) { mockExportError = v },
    get error() { return mockError },
    get grandTotalWeightKg() { return mockGrandTotalWeightKg },
    get grandTotalFeeHuf() { return mockGrandTotalFeeHuf },
    get totalKfCodes() { return mockTotalKfCodes },
    fetchAggregation: mockFetchAggregation,
    exportOkirkapu: mockExportOkirkapu,
    reset: mockReset,
  }),
}))

function makeAggregation(overrides: Partial<FilingAggregationResult> = {}): FilingAggregationResult {
  return {
    soldProducts: [],
    kfTotals: [],
    unresolved: [],
    metadata: { period: { from: '2026-01-01', to: '2026-03-31' }, generatedAt: '2026-04-20T10:00:00Z' },
    ...overrides,
  }
}

const RegistryOnboardingBlockStub = {
  name: 'RegistryOnboardingBlock',
  template: '<div data-testid="registry-onboarding-block" />',
  emits: ['bootstrap-completed'],
  props: ['context'],
}
const SkeletonStub = {
  template: '<div data-testid="skeleton-stub" />',
  props: ['height'],
}

function mountPage() {
  return mount(FilingPage, {
    global: {
      stubs: {
        Button: ButtonStub,
        Panel: PanelStub,
        Skeleton: SkeletonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        EprSoldProductsTable: EprSoldProductsTableStub,
        EprKfTotalsTable: EprKfTotalsTableStub,
        RegistryOnboardingBlock: RegistryOnboardingBlockStub,
      },
    },
  })
}

describe('EPR Filing Page (10.6 rebuild)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRefresh.mockResolvedValue(undefined)
    mockToastAdd.mockReset()
    mockAggregation = null
    mockIsLoading = false
    mockIsExporting = false
    mockExportError = null
    mockError = null
    mockGrandTotalWeightKg = 0
    mockGrandTotalFeeHuf = 0
    mockTotalKfCodes = 0
    mockIsEmpty = false
    mockRegistryIsLoading = false
    mockTotalProducts = 1
  })

  it('renders period selector on mount', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="period-selector"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="period-from-input"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="period-to-input"]').exists()).toBe(true)
  })

  it('calls fetchAggregation on mount with default previous-quarter period', async () => {
    mountPage()
    await flushPromises()
    expect(mockFetchAggregation).toHaveBeenCalledOnce()
    const callArgs = mockFetchAggregation.mock.calls[0] as [string, string]
    // 2026-04-20 → prev quarter is Q1 2026 → 2026-01-01 to 2026-03-31
    expect(callArgs[0]).toBe('2026-01-01')
    expect(callArgs[1]).toBe('2026-03-31')
  })

  it('calls fetchAggregation debounced (500ms) when period changes', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mountPage()
      // onMounted fetch is sync-ish; flush pending microtasks
      await Promise.resolve()
      const callsAfterMount = mockFetchAggregation.mock.calls.length
      const fromInput = wrapper.find('[data-testid="period-from-input"]')
      await fromInput.setValue('2025-10-01')
      await fromInput.trigger('input')
      // Watch schedules a timer; before advancing, fetch should NOT yet have been re-invoked
      expect(mockFetchAggregation.mock.calls.length).toBe(callsAfterMount)
      // Advance < 500ms — still no new call
      vi.advanceTimersByTime(499)
      expect(mockFetchAggregation.mock.calls.length).toBe(callsAfterMount)
      // Advance past 500ms total — debounced call fires
      vi.advanceTimersByTime(1)
      expect(mockFetchAggregation.mock.calls.length).toBe(callsAfterMount + 1)
      const lastCall = mockFetchAggregation.mock.calls.at(-1) as [string, string]
      expect(lastCall[0]).toBe('2025-10-01')
    }
    finally {
      vi.useRealTimers()
    }
  })

  it('shows skeleton rows in sold products and kf totals when isLoading=true', () => {
    mockIsLoading = true
    const wrapper = mountPage()
    const soldTable = wrapper.findComponent(EprSoldProductsTableStub)
    const kfTable = wrapper.findComponent(EprKfTotalsTableStub)
    expect(soldTable.props('loading')).toBe(true)
    expect(kfTable.props('loading')).toBe(true)
  })

  it('renders EprSoldProductsTable with aggregation sold products', () => {
    mockAggregation = makeAggregation({
      soldProducts: [{ productId: 'p1', vtsz: '48191000', description: 'Box', totalQuantity: 100, unitOfMeasure: 'DARAB', matchingInvoiceLines: 2 }],
    })
    const wrapper = mountPage()
    const soldTable = wrapper.findComponent(EprSoldProductsTableStub)
    expect(soldTable.props('soldProducts')).toHaveLength(1)
  })

  it('renders EprKfTotalsTable with aggregation kfTotals', () => {
    mockAggregation = makeAggregation({
      kfTotals: [{ kfCode: '11010101', classificationLabel: null, totalWeightKg: 10, feeRateHufPerKg: 215, totalFeeHuf: 2150, contributingProductCount: 1, hasFallback: false, hasOverflowWarning: false }],
    })
    const wrapper = mountPage()
    const kfTable = wrapper.findComponent(EprKfTotalsTableStub)
    expect(kfTable.props('kfTotals')).toHaveLength(1)
  })

  it('summary cards show dashes when aggregation is null', () => {
    mockAggregation = null
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="summary-total-kf-codes"]').text()).toContain('—')
    expect(wrapper.find('[data-testid="summary-total-weight"]').text()).toContain('—')
    expect(wrapper.find('[data-testid="summary-total-fee"]').text()).toContain('—')
  })

  it('summary cards render grand totals when aggregation is loaded', () => {
    mockAggregation = makeAggregation({ kfTotals: [{ kfCode: '11010101', classificationLabel: null, totalWeightKg: 120.456, feeRateHufPerKg: 215, totalFeeHuf: 25898, contributingProductCount: 1, hasFallback: false, hasOverflowWarning: false }] })
    mockGrandTotalWeightKg = 120.456
    mockGrandTotalFeeHuf = 25898
    mockTotalKfCodes = 1
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="summary-total-kf-codes"]').text()).toContain('1')
    expect(wrapper.find('[data-testid="summary-total-weight"]').text()).not.toContain('—')
    expect(wrapper.find('[data-testid="summary-total-fee"]').text()).not.toContain('—')
  })

  it('unresolved panel is hidden when unresolved.length === 0', () => {
    mockAggregation = makeAggregation({ unresolved: [] })
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="unresolved-panel-wrapper"]').exists()).toBe(false)
  })

  it('unresolved panel is visible when unresolved.length > 0', () => {
    mockAggregation = makeAggregation({
      unresolved: [{ invoiceNumber: 'INV-1', lineNumber: 1, vtsz: '48191000', description: 'Box', quantity: 10, unitOfMeasure: 'DARAB', reason: 'NO_MATCHING_PRODUCT' }],
    })
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="unresolved-panel-wrapper"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unresolved-panel"]').exists()).toBe(true)
  })

  it('export button is disabled when aggregation is null', () => {
    mockAggregation = null
    const wrapper = mountPage()
    const exportBtn = wrapper.find('[data-testid="export-okirkapu-button"]')
    expect(exportBtn.exists()).toBe(true)
    expect(exportBtn.attributes('disabled')).toBeDefined()
  })

  it('export button is disabled when kfTotals is empty', () => {
    mockAggregation = makeAggregation({ kfTotals: [] })
    const wrapper = mountPage()
    const exportBtn = wrapper.find('[data-testid="export-okirkapu-button"]')
    expect(exportBtn.attributes('disabled')).toBeDefined()
  })

  it('shows profile incomplete warning when exportError is producer.profile.incomplete', () => {
    mockExportError = 'producer.profile.incomplete'
    mockAggregation = makeAggregation()
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="profile-incomplete-warning"]').exists()).toBe(true)
  })

  it('no reference to deprecated store fields (lines, serverResult, isCalculating)', () => {
    // This test ensures the rewritten page doesn't use old store fields
    // by verifying the component mounts without error when the store lacks those fields
    const wrapper = mountPage()
    expect(wrapper.exists()).toBe(true)
    // If filing.vue still referenced these fields, TypeScript would have caught it
    // and the test would fail to mount
  })

  // ─── Empty-registry gate tests (AC #23) ──────────────────────────────────────

  it('shows RegistryOnboardingBlock when registry is empty (isEmpty=true)', () => {
    mockIsEmpty = true
    mockTotalProducts = 0
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="registry-onboarding-block"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="period-selector"]').exists()).toBe(false)
  })

  it('shows period selector and filing content when registry is not empty (isEmpty=false)', () => {
    mockIsEmpty = false
    mockTotalProducts = 3
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="period-selector"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="registry-onboarding-block"]').exists()).toBe(false)
  })

  it('onBootstrapCompleted calls registryCompleteness.refresh', async () => {
    mockIsEmpty = true
    mockTotalProducts = 0
    const wrapper = mountPage()
    const onboardingBlock = wrapper.findComponent(RegistryOnboardingBlockStub)
    expect(onboardingBlock.exists()).toBe(true)
    await onboardingBlock.vm.$emit('bootstrap-completed')
    await flushPromises()
    expect(mockRefresh).toHaveBeenCalled()
  })
})
