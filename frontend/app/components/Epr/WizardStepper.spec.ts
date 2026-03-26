import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WizardStepper from './WizardStepper.vue'

// Mock PrimeVue components
vi.mock('primevue/stepper', () => ({
  default: { name: 'Stepper', template: '<div><slot /></div>', props: ['value', 'linear'] },
}))
vi.mock('primevue/steplist', () => ({
  default: { name: 'StepList', template: '<div><slot /></div>' },
}))
vi.mock('primevue/steppanels', () => ({
  default: { name: 'StepPanels', template: '<div><slot /></div>' },
}))
vi.mock('primevue/step', () => ({
  default: { name: 'Step', template: '<div><slot /></div>', props: ['value'] },
}))
vi.mock('primevue/steppanel', () => ({
  default: {
    name: 'StepPanel',
    template: '<div><slot :activateCallback="() => {}" /></div>',
    props: ['value'],
  },
}))
vi.mock('primevue/button', () => ({
  default: {
    name: 'Button',
    template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')">{{ label }}</button>',
    props: ['label', 'severity', 'outlined', 'text', 'size', 'loading'],
    emits: ['click'],
    inheritAttrs: true,
  },
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockCancelWizard = vi.fn()
const mockConfirmAndLink = vi.fn()
const mockSelectOption = vi.fn()
const mockGoBack = vi.fn()

const mockRetryLink = vi.fn()
const mockCloseWithoutLinking = vi.fn()

const createMockStore = (overrides = {}) => ({
  activeStep: '1',
  traversalPath: [],
  availableOptions: [{ code: '11', label: 'Packaging', description: null }],
  resolvedResult: null,
  isLoading: false,
  error: null,
  targetTemplateId: null,
  configVersion: 1,
  lastConfirmSuccess: false,
  linkFailed: false,
  lastCalculationId: null,
  lastCloseReason: null,
  isActive: true,
  breadcrumb: [],
  currentStepIndex: 0,
  isOverrideActive: false,
  overrideKfCode: null,
  overrideFeeRate: null,
  overrideClassification: null,
  startWizard: vi.fn(),
  selectOption: mockSelectOption,
  resolveResult: vi.fn(),
  confirmAndLink: mockConfirmAndLink,
  cancelWizard: mockCancelWizard,
  goBack: mockGoBack,
  refreshOptions: vi.fn(),
  retryLink: mockRetryLink,
  closeWithoutLinking: mockCloseWithoutLinking,
  $reset: vi.fn(),
  ...overrides,
})

let mockStore = createMockStore()

vi.mock('~/stores/eprWizard', () => ({
  useEprWizardStore: () => mockStore,
}))

describe('WizardStepper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore = createMockStore()
  })

  it('renders the wizard stepper container', () => {
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-stepper"]').exists()).toBe(true)
  })

  it('shows breadcrumb when traversal path has entries', () => {
    mockStore = createMockStore({
      activeStep: '2',
      traversalPath: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
      breadcrumb: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
    })
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-breadcrumb"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Packaging')
  })

  it('does not show breadcrumb when traversal path is empty', () => {
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-breadcrumb"]').exists()).toBe(false)
  })

  it('shows result card with formatted KF code when resolved', () => {
    mockStore = createMockStore({
      activeStep: '4',
      resolvedResult: {
        kfCode: '11010101',
        feeCode: '1101',
        feeRate: 20.44,
        currency: 'HUF',
        materialClassification: 'Paper and cardboard',
        traversalPath: [
          { level: 'product_stream', code: '11', label: 'Packaging' },
          { level: 'material_stream', code: '01', label: 'Paper' },
        ],
        legislationRef: 'test',
      },
    })
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-result-card"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('11 01 01 01')
    expect(wrapper.text()).toContain('20.44')
    expect(wrapper.text()).toContain('Paper and cardboard')
  })

  it('renders cancel button', () => {
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-cancel-inline"]').exists()).toBe(true)
  })

  it('does not show back button on step 1 with empty traversal path', () => {
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-back-button"]').exists()).toBe(false)
  })

  it('shows back button when traversal path has entries', () => {
    mockStore = createMockStore({
      activeStep: '2',
      traversalPath: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
      breadcrumb: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
    })
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-back-button"]').exists()).toBe(true)
  })

  it('calls goBack on back button click', async () => {
    mockStore = createMockStore({
      activeStep: '2',
      traversalPath: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
      breadcrumb: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
    })
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true },
      },
    })
    await wrapper.find('[data-testid="wizard-back-button"]').trigger('click')
    expect(mockGoBack).toHaveBeenCalled()
  })

  it('does not show back button on step 4 (result)', () => {
    mockStore = createMockStore({
      activeStep: '4',
      resolvedResult: {
        kfCode: '11010101',
        feeCode: '1101',
        feeRate: 20.44,
        currency: 'HUF',
        materialClassification: 'Paper and cardboard',
        traversalPath: [],
        legislationRef: 'test',
        confidenceScore: 'HIGH',
        confidenceReason: 'full_traversal',
      },
    })
    const wrapper = mount(WizardStepper, {
      global: {
        stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
      },
    })
    expect(wrapper.find('[data-testid="wizard-back-button"]').exists()).toBe(false)
  })

  describe('link failure state', () => {
    const resolvedResult = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      materialClassification: 'Paper and cardboard',
      traversalPath: [
        { level: 'product_stream', code: '11', label: 'Packaging' },
        { level: 'material_stream', code: '01', label: 'Paper' },
      ],
      legislationRef: 'test',
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    }

    it('shows link-failed warning banner when linkFailed is true', () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: true,
        lastCalculationId: 'calc-123',
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      expect(wrapper.find('[data-testid="wizard-link-failed-banner"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('epr.wizard.linkFailed')
    })

    it('does not show link-failed banner when linkFailed is false', () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: false,
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      expect(wrapper.find('[data-testid="wizard-link-failed-banner"]').exists()).toBe(false)
    })

    it('shows retry and close-without-linking buttons when linkFailed', () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: true,
        lastCalculationId: 'calc-123',
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      expect(wrapper.find('[data-testid="wizard-retry-link-button"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="wizard-close-without-linking-button"]').exists()).toBe(true)
    })

    it('hides original action buttons when linkFailed', () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: true,
        lastCalculationId: 'calc-123',
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      expect(wrapper.find('[data-testid="wizard-confirm-button"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="wizard-override-button"]').exists()).toBe(false)
    })

    it('calls retryLink on retry button click', async () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: true,
        lastCalculationId: 'calc-123',
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      await wrapper.find('[data-testid="wizard-retry-link-button"]').trigger('click')
      expect(mockRetryLink).toHaveBeenCalled()
    })

    it('calls closeWithoutLinking on close button click', async () => {
      mockStore = createMockStore({
        activeStep: '4',
        linkFailed: true,
        lastCalculationId: 'calc-123',
        resolvedResult,
      })
      const wrapper = mount(WizardStepper, {
        global: {
          stubs: { EprMaterialSelector: true, EprConfidenceBadge: true },
        },
      })
      await wrapper.find('[data-testid="wizard-close-without-linking-button"]').trigger('click')
      expect(mockCloseWithoutLinking).toHaveBeenCalled()
    })
  })
})
