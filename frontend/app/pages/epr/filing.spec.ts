import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import FilingPage from './filing.vue'

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

const mockFetchAutoFill = vi.fn().mockResolvedValue(undefined)
const mockFetchRegisteredTaxNumber = vi.fn().mockResolvedValue('')
const mockAutoFillResponse = ref<any>(null)

vi.mock('~/composables/api/useInvoiceAutoFill', () => ({
  useInvoiceAutoFill: vi.fn(() => ({
    fetchAutoFill: mockFetchAutoFill,
    fetchRegisteredTaxNumber: mockFetchRegisteredTaxNumber,
    pending: ref(false),
    response: mockAutoFillResponse,
    error: ref(null),
    startOfCurrentQuarter: () => new Date(2026, 0, 1),
    endOfCurrentQuarter: () => new Date(2026, 2, 31),
  })),
}))

// Stub PrimeVue components
const ButtonStub = {
  template: '<button :disabled="$attrs.disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const TagStub = {
  template: '<span :data-testid="$attrs[\'data-testid\']" class="tag-stub">{{ value }}</span>',
  props: ['severity', 'value'],
  inheritAttrs: true,
}
const DataTableStub = {
  template: `<div :data-testid="$attrs['data-testid'] || 'filing-table'">
    <div v-for="row in value" :key="row.templateId || row.vtszCode" class="filing-row">
      <span class="row-name">{{ row.name || row.description }}</span>
    </div>
  </div>`,
  props: ['value', 'selection', 'filters', 'globalFilterFields', 'paginator', 'rows', 'rowsPerPageOptions', 'selectionMode', 'sortField', 'sortOrder', 'stripedRows'],
  inheritAttrs: true,
}
const ColumnStub = {
  template: '<div />',
  props: ['field', 'header', 'sortable', 'selectionMode', 'headerStyle'],
}
const InputNumberStub = {
  template: '<input :data-testid="$attrs[\'data-testid\']" @input="$emit(\'input\', { value: $event.target.value })" />',
  inheritAttrs: true,
  emits: ['input'],
}
const InputTextStub = {
  template: '<input :data-testid="$attrs[\'data-testid\']" />',
  inheritAttrs: true,
}
const IconFieldStub = {
  template: '<div class="icon-field-stub"><slot /></div>',
}
const InputIconStub = {
  template: '<i class="input-icon-stub" />',
  inheritAttrs: true,
}

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({
  push: mockRouterPush,
  back: vi.fn(),
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
    role: 'SME_ADMIN',
  }),
}))

const mockFetchMaterials = vi.fn().mockResolvedValue(undefined)
const mockCalculate = vi.fn().mockResolvedValue(undefined)
const mockExportOkirkapu = vi.fn().mockResolvedValue(undefined)
const mockUpdateQuantity = vi.fn()
const mockInitFromTemplates = vi.fn()

let mockEprMaterials: any[] = []
let mockFilingLines: any[] = []
let mockServerResult: any = null
let mockHasValidLines = false
let mockIsCalculating = false
let mockIsExporting = false
let mockValidLines: any[] = []
let mockExportError: string | null = null

vi.mock('~/stores/epr', () => ({
  useEprStore: () => ({
    get materials() { return mockEprMaterials },
    isLoading: false,
    error: null,
    fetchMaterials: mockFetchMaterials,
  }),
}))

vi.mock('~/stores/eprFiling', () => ({
  useEprFilingStore: () => ({
    get lines() { return mockFilingLines },
    get serverResult() { return mockServerResult },
    get hasValidLines() { return mockHasValidLines },
    get isCalculating() { return mockIsCalculating },
    get isExporting() { return mockIsExporting },
    get validLines() { return mockValidLines },
    get grandTotalWeightKg() { return mockServerResult?.grandTotalWeightKg ?? 0 },
    get grandTotalFeeHuf() { return mockServerResult?.grandTotalFeeHuf ?? 0 },
    get exportError() { return mockExportError },
    set exportError(v: string | null) { mockExportError = v },
    error: null,
    initFromTemplates: mockInitFromTemplates,
    updateQuantity: mockUpdateQuantity,
    calculate: mockCalculate,
    exportOkirkapu: mockExportOkirkapu,
  }),
}))

function mountPage() {
  return mount(FilingPage, {
    global: {
      stubs: {
        Button: ButtonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        InputNumber: InputNumberStub,
        InputText: InputTextStub,
        IconField: IconFieldStub,
        InputIcon: InputIconStub,
        Tag: TagStub,
      },
    },
  })
}

describe('EPR Filing Page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockToastAdd.mockReset()
    mockAutoFillResponse.value = null
    mockEprMaterials = []
    mockFilingLines = []
    mockServerResult = null
    mockHasValidLines = false
    mockIsCalculating = false
    mockIsExporting = false
    mockValidLines = []
    mockExportError = null
  })

  it('renders empty state when no verified templates', () => {
    mockFilingLines = []
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('epr.filing.emptyState')
  })

  it('shows verified templates in table', () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: null, totalWeightGrams: null, totalWeightKg: null,
        feeAmountHuf: null, isValid: false, validationMessage: null,
      },
      {
        templateId: '2', name: 'Foil B', kfCode: '21020202',
        baseWeightGrams: 5, feeRateHufPerKg: 130,
        quantityPcs: null, totalWeightGrams: null, totalWeightKg: null,
        feeAmountHuf: null, isValid: false, validationMessage: null,
      },
    ]
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="filing-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Box A')
    expect(wrapper.text()).toContain('Foil B')
  })

  it('calculate button is disabled when no valid quantities', () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: null, totalWeightGrams: null, totalWeightKg: null,
        feeAmountHuf: null, isValid: false, validationMessage: null,
      },
    ]
    mockHasValidLines = false
    const wrapper = mountPage()
    const calcButton = wrapper.find('[data-testid="calculate-button"]')
    expect(calcButton.exists()).toBe(true)
    expect(calcButton.attributes('disabled')).toBeDefined()
  })

  it('shows Filing Summary with dashes before calculation', () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: null, totalWeightGrams: null, totalWeightKg: null,
        feeAmountHuf: null, isValid: false, validationMessage: null,
      },
    ]
    mockServerResult = null
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="filing-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="summary-total-lines"]').text()).toContain('—')
  })

  it('shows Filing Summary with totals after successful calculation', () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120,
        feeAmountHuf: 25800, isValid: true, validationMessage: null,
      },
    ]
    mockHasValidLines = true
    mockValidLines = mockFilingLines
    mockServerResult = {
      lines: [{ templateId: '1', name: 'Box A', kfCode: '11010101', quantityPcs: 1000, baseWeightGrams: 120, totalWeightGrams: 120000, totalWeightKg: 120, feeRateHufPerKg: 215, feeAmountHuf: 25800 }],
      grandTotalWeightKg: 120,
      grandTotalFeeHuf: 25800,
      configVersion: 1,
    }
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="filing-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="summary-total-lines"]').text()).toContain('1')
  })

  // ─── OKIRkapu export tests ────────────────────────────────────────────────

  it('Export OKIRkapu button has correct label', () => {
    mockFilingLines = [{ templateId: '1', name: 'Box A', kfCode: '11010101', baseWeightGrams: 120, feeRateHufPerKg: 215, quantityPcs: null, totalWeightGrams: null, totalWeightKg: null, feeAmountHuf: null, isValid: false, validationMessage: null }]
    const wrapper = mountPage()
    const exportBtn = wrapper.find('[data-testid="export-okirkapu-button"]')
    expect(exportBtn.exists()).toBe(true)
  })

  it('OKIRkapu export panel always visible (not gated on serverResult)', () => {
    mockFilingLines = [{ templateId: '1', name: 'Box A', kfCode: '11010101', baseWeightGrams: 120, feeRateHufPerKg: 215, quantityPcs: null, totalWeightGrams: null, totalWeightKg: null, feeAmountHuf: null, isValid: false, validationMessage: null }]
    mockServerResult = null
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="okirkapu-export-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="export-okirkapu-button"]').exists()).toBe(true)
  })

  it('shows profile incomplete warning when exportError is producer.profile.incomplete', () => {
    mockExportError = 'producer.profile.incomplete'
    mockFilingLines = [{ templateId: '1', name: 'Box A', kfCode: '11010101', baseWeightGrams: 120, feeRateHufPerKg: 215, quantityPcs: null, totalWeightGrams: null, totalWeightKg: null, feeAmountHuf: null, isValid: false, validationMessage: null }]
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="profile-incomplete-warning"]').exists()).toBe(true)
  })

  it('does not show old MOHU export button', () => {
    mockFilingLines = [{ templateId: '1', name: 'Box A', kfCode: '11010101', baseWeightGrams: 120, feeRateHufPerKg: 215, quantityPcs: null, totalWeightGrams: null, totalWeightKg: null, feeAmountHuf: null, isValid: false, validationMessage: null }]
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="export-mohu-button"]').exists()).toBe(false)
  })

  it('Export error shows error toast when exportOkirkapu() throws', async () => {
    mockFilingLines = [{ templateId: '1', name: 'Box A', kfCode: '11010101', baseWeightGrams: 120, feeRateHufPerKg: 215, quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120, feeAmountHuf: 25800, isValid: true, validationMessage: null }]
    mockHasValidLines = true
    const mockErr = new Error('Server error')
    mockExportOkirkapu.mockRejectedValueOnce(mockErr)

    const wrapper = mountPage()
    // Tax number is readonly and auto-filled from profile. Set it via component internals.
    const vm = wrapper.vm as any
    vm.exportTaxNumber = '12345678'
    await wrapper.vm.$nextTick()

    const exportBtn = wrapper.find('[data-testid="export-okirkapu-button"]')
    await exportBtn.trigger('click')
    await flushPromises()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error' }),
    )
  })

  // ─── Invoice Auto-Fill section tests ──────────────────────────────────────

  it('renders the autofill panel', () => {
    mockFilingLines = []
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="autofill-panel"]').exists()).toBe(true)
  })

  it('shows invoice products when autoFillResponse has lines', () => {
    mockAutoFillResponse.value = {
      navAvailable: true,
      dataSourceMode: 'LIVE',
      lines: [
        {
          vtszCode: '48191000',
          description: 'Karton csomagolás',
          suggestedKfCode: '11010101',
          aggregatedQuantity: 500,
          unitOfMeasure: 'DARAB',
          hasExistingTemplate: true,
          existingTemplateId: 'tmpl-1',
        },
        {
          vtszCode: '39233000',
          description: 'PET palack',
          suggestedKfCode: null,
          aggregatedQuantity: 5000,
          unitOfMeasure: 'DARAB',
          hasExistingTemplate: false,
          existingTemplateId: null,
        },
      ],
    }
    mockFilingLines = []
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="autofill-results-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Karton csomagolás')
    expect(wrapper.text()).toContain('PET palack')
  })

  it('shows NAV unavailable message when navAvailable is false', () => {
    mockAutoFillResponse.value = { navAvailable: false, dataSourceMode: 'LIVE', lines: [] }
    mockFilingLines = []
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="autofill-nav-unavailable"]').exists()).toBe(true)
  })

  it('shows empty message when response has no lines', () => {
    mockAutoFillResponse.value = { navAvailable: true, dataSourceMode: 'LIVE', lines: [] }
    mockFilingLines = []
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="autofill-empty"]').exists()).toBe(true)
  })

  it('unmatched lines from apply are shown as warning tags', async () => {
    mockAutoFillResponse.value = {
      navAvailable: true,
      dataSourceMode: 'LIVE',
      lines: [
        {
          vtszCode: '39233000',
          description: 'PET csomagolás',
          suggestedKfCode: '11020101',
          aggregatedQuantity: 80,
          unitOfMeasure: 'DARAB',
          hasExistingTemplate: false,
          existingTemplateId: null,
        },
      ],
    }
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: null, totalWeightGrams: null, totalWeightKg: null,
        feeAmountHuf: null, isValid: false, validationMessage: null,
      },
    ]
    const wrapper = mountPage()

    // Simulate selecting and applying autofill lines by calling onAutoFillApply directly
    // (since DataTable selection is stubbed out)
    const vm = wrapper.vm as any
    vm.onAutoFillApply([
      {
        vtszCode: '39233000',
        description: 'PET csomagolás',
        suggestedKfCode: '11020101',
        aggregatedQuantity: 80,
        unitOfMeasure: 'DARAB',
        hasExistingTemplate: false,
        existingTemplateId: null,
      },
    ])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="unmatched-lines"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unmatched-tag"]').exists()).toBe(true)
  })
})
