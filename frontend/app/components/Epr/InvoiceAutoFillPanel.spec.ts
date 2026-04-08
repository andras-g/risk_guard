import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import InvoiceAutoFillPanel from './InvoiceAutoFillPanel.vue'
import type { InvoiceAutoFillLineDto } from '~/composables/api/useInvoiceAutoFill'

// ─── Composable mock ────────────────────────────────────────────────────────

const mockFetchAutoFill = vi.fn()
const mockFetchRegisteredTaxNumber = vi.fn().mockResolvedValue('')
const mockPending = ref(false)
const mockResponse = ref<{ lines: InvoiceAutoFillLineDto[]; navAvailable: boolean; dataSourceMode: string } | null>(null)

vi.mock('~/composables/api/useInvoiceAutoFill', () => ({
  useInvoiceAutoFill: vi.fn(() => ({
    fetchAutoFill: mockFetchAutoFill,
    fetchRegisteredTaxNumber: mockFetchRegisteredTaxNumber,
    pending: mockPending,
    response: mockResponse,
    error: ref(null),
    registeredTaxNumber: ref(''),
    startOfCurrentQuarter: () => new Date(2026, 0, 1),
    endOfCurrentQuarter: () => new Date(2026, 2, 31),
  })),
}))

// ─── PrimeVue stubs ─────────────────────────────────────────────────────────

const DatePickerStub = {
  template: '<input :data-testid="$attrs[\'data-testid\']" type="date" />',
  props: ['modelValue', 'dateFormat'],
  inheritAttrs: true,
}
const InputTextStub = {
  template: '<input :data-testid="$attrs[\'data-testid\']" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
  inheritAttrs: true,
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="$attrs.disabled || $attrs.loading" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const MessageStub = {
  template: '<div :data-testid="$attrs[\'data-testid\']" class="p-message"><slot /></div>',
  props: ['severity'],
  inheritAttrs: true,
}
const SkeletonStub = {
  template: '<div :data-testid="$attrs[\'data-testid\']" class="skeleton-stub" />',
  inheritAttrs: true,
}
const DataTableStub = {
  template: '<div data-testid="autofill-results-table"><slot /><slot name="header" /></div>',
  props: ['value', 'selection', 'selectionMode', 'dataKey'],
  provide() {
    return { dtEntries: (this as any).value }
  },
}
const ColumnStub = {
  template: '<div><slot name="body" :data="entryData" /></div>',
  props: ['field', 'header', 'selectionMode', 'headerStyle'],
  inject: { dtEntries: { default: () => [] } },
  computed: {
    entryData(): any {
      return (this as any).dtEntries?.[0] ?? {}
    },
  },
}
const TagStub = {
  template: '<span :data-testid="$attrs[\'data-testid\']" class="tag">{{ value }}</span>',
  props: ['severity', 'value'],
  inheritAttrs: true,
}
const RouterLinkStub = {
  template: '<a href="#"><slot /></a>',
  props: ['to'],
}

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

// ─── Mount helper ────────────────────────────────────────────────────────────

function mountPanel() {
  return mount(InvoiceAutoFillPanel, {
    global: {
      stubs: {
        DatePicker: DatePickerStub,
        InputText: InputTextStub,
        Button: ButtonStub,
        Message: MessageStub,
        Skeleton: SkeletonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        Tag: TagStub,
        RouterLink: RouterLinkStub,
      },
    },
  })
}

function buildLine(overrides: Partial<InvoiceAutoFillLineDto> = {}): InvoiceAutoFillLineDto {
  return {
    vtszCode: '48191000',
    description: 'Karton csomagolás',
    suggestedKfCode: '11010101',
    aggregatedQuantity: 150,
    unitOfMeasure: 'DARAB',
    hasExistingTemplate: false,
    existingTemplateId: null,
    ...overrides,
  }
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('InvoiceAutoFillPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPending.value = false
    mockResponse.value = null
    mockFetchAutoFill.mockResolvedValue(undefined)
  })

  it('renders tax number input', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-tax-number"]').exists()).toBe(true)
  })

  it('renders from and to date pickers', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-from"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="autofill-to"]').exists()).toBe(true)
  })

  it('fetch button is disabled when taxNumber is empty', () => {
    const wrapper = mountPanel()
    const btn = wrapper.find('[data-testid="autofill-fetch-button"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('shows skeleton rows while pending', () => {
    mockPending.value = true
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-skeleton"]').exists()).toBe(true)
  })

  it('does not show skeleton when not pending', () => {
    mockPending.value = false
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-skeleton"]').exists()).toBe(false)
  })

  it('shows NAV unavailable warning when navAvailable is false', () => {
    mockResponse.value = { lines: [], navAvailable: false, dataSourceMode: 'live' }
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-nav-unavailable"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('epr.autofill.navUnavailable')
  })

  it('shows results table when lines are present', () => {
    mockResponse.value = {
      lines: [buildLine()],
      navAvailable: true,
      dataSourceMode: 'demo',
    }
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-results-table"]').exists()).toBe(true)
  })

  it('shows empty message when response has no lines', () => {
    mockResponse.value = { lines: [], navAvailable: true, dataSourceMode: 'demo' }
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('epr.autofill.noResults')
  })

  it('shows template-matched badge for lines with existing template', () => {
    mockResponse.value = {
      lines: [buildLine({ hasExistingTemplate: true, existingTemplateId: 'tmpl-1' })],
      navAvailable: true,
      dataSourceMode: 'demo',
    }
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="template-matched-badge"]').exists()).toBe(true)
  })

  it('apply button is disabled when no lines are selected', () => {
    mockResponse.value = {
      lines: [buildLine()],
      navAvailable: true,
      dataSourceMode: 'demo',
    }
    const wrapper = mountPanel()
    const applyBtn = wrapper.find('[data-testid="autofill-apply-button"]')
    expect(applyBtn.exists()).toBe(true)
    expect(applyBtn.attributes('disabled')).toBeDefined()
  })

  it('calls fetchAutoFill when fetch button is clicked with taxNumber', async () => {
    const wrapper = mountPanel()
    const input = wrapper.find('[data-testid="autofill-tax-number"]')
    await input.setValue('12345678')
    await wrapper.find('[data-testid="autofill-fetch-button"]').trigger('click')
    await flushPromises()
    expect(mockFetchAutoFill).toHaveBeenCalledOnce()
  })

  it('does not show NAV warning when navAvailable is true', () => {
    mockResponse.value = { lines: [buildLine()], navAvailable: true, dataSourceMode: 'demo' }
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="autofill-nav-unavailable"]').exists()).toBe(false)
  })
})
