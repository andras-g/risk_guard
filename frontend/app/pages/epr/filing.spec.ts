import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import FilingPage from './filing.vue'

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

// Stub PrimeVue components
const ButtonStub = {
  template: '<button :disabled="$attrs.disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const DataTableStub = {
  template: `<div data-testid="filing-table">
    <div v-for="row in value" :key="row.templateId" class="filing-row">
      <span class="row-name">{{ row.name }}</span>
    </div>
  </div>`,
  props: ['value'],
}
const ColumnStub = {
  template: '<div />',
  props: ['field', 'header'],
}
const InputNumberStub = {
  template: '<input :data-testid="$attrs[\'data-testid\']" @input="$emit(\'input\', { value: $event.target.value })" />',
  inheritAttrs: true,
  emits: ['input'],
}

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

vi.stubGlobal('useRouter', () => ({
  push: vi.fn(),
}))

vi.mock('~/composables/auth/useTierGate', () => ({
  useTierGate: () => ({
    hasAccess: ref(true),
    tierName: ref('PRO EPR'),
  }),
}))

const mockFetchMaterials = vi.fn().mockResolvedValue(undefined)
const mockCalculate = vi.fn().mockResolvedValue(undefined)
const mockExportMohu = vi.fn().mockResolvedValue(undefined)
const mockUpdateQuantity = vi.fn()
const mockInitFromTemplates = vi.fn()

let mockEprMaterials: any[] = []
let mockFilingLines: any[] = []
let mockServerResult: any = null
let mockHasValidLines = false
let mockIsCalculating = false
let mockIsExporting = false
let mockValidLines: any[] = []

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
    isLoading: false,
    error: null,
    initFromTemplates: mockInitFromTemplates,
    updateQuantity: mockUpdateQuantity,
    calculate: mockCalculate,
    exportMohu: mockExportMohu,
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
      },
    },
  })
}

describe('EPR Filing Page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockToastAdd.mockReset()
    mockEprMaterials = []
    mockFilingLines = []
    mockServerResult = null
    mockHasValidLines = false
    mockIsCalculating = false
    mockIsExporting = false
    mockValidLines = []
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

  // ─── Export for MOHU tests ─────────────────────────────────────────────────

  it('Export for MOHU button hidden when serverResult is null', () => {
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
    expect(wrapper.find('[data-testid="export-mohu-button"]').exists()).toBe(false)
  })

  it('Export for MOHU button visible when serverResult is populated', () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120,
        feeAmountHuf: 25800, isValid: true, validationMessage: null,
      },
    ]
    mockHasValidLines = true
    mockServerResult = {
      lines: [{ templateId: '1', name: 'Box A', kfCode: '11010101', quantityPcs: 1000, baseWeightGrams: 120, totalWeightGrams: 120000, totalWeightKg: 120, feeRateHufPerKg: 215, feeAmountHuf: 25800 }],
      grandTotalWeightKg: 120,
      grandTotalFeeHuf: 25800,
      configVersion: 1,
    }
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="export-mohu-button"]').exists()).toBe(true)
  })

  it('Export for MOHU button calls exportMohu() action on click', async () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120,
        feeAmountHuf: 25800, isValid: true, validationMessage: null,
      },
    ]
    mockHasValidLines = true
    mockServerResult = {
      lines: [{ templateId: '1', name: 'Box A', kfCode: '11010101', quantityPcs: 1000, baseWeightGrams: 120, totalWeightGrams: 120000, totalWeightKg: 120, feeRateHufPerKg: 215, feeAmountHuf: 25800 }],
      grandTotalWeightKg: 120,
      grandTotalFeeHuf: 25800,
      configVersion: 1,
    }
    const wrapper = mountPage()
    const exportBtn = wrapper.find('[data-testid="export-mohu-button"]')
    expect(exportBtn.exists()).toBe(true)
    await exportBtn.trigger('click')
    expect(mockExportMohu).toHaveBeenCalledOnce()
  })

  it('Export error shows toast when exportMohu() throws', async () => {
    mockFilingLines = [
      {
        templateId: '1', name: 'Box A', kfCode: '11010101',
        baseWeightGrams: 120, feeRateHufPerKg: 215,
        quantityPcs: 1000, totalWeightGrams: 120000, totalWeightKg: 120,
        feeAmountHuf: 25800, isValid: true, validationMessage: null,
      },
    ]
    mockHasValidLines = true
    mockServerResult = {
      lines: [{ templateId: '1', name: 'Box A', kfCode: '11010101', quantityPcs: 1000, baseWeightGrams: 120, totalWeightGrams: 120000, totalWeightKg: 120, feeRateHufPerKg: 215, feeAmountHuf: 25800 }],
      grandTotalWeightKg: 120,
      grandTotalFeeHuf: 25800,
      configVersion: 1,
    }
    mockExportMohu.mockRejectedValueOnce(new Error('Config version mismatch'))

    const wrapper = mountPage()
    const exportBtn = wrapper.find('[data-testid="export-mohu-button"]')
    await exportBtn.trigger('click')
    await wrapper.vm.$nextTick()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error' }),
    )
  })
})
