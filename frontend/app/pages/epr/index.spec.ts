import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import EprPage from './index.vue'

// Stub PrimeVue
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const ConfirmDialogStub = { template: '<div />' }

// Stub child components
const MaterialInventoryBlockStub = { template: '<div data-testid="material-inventory-block" />', props: ['entries', 'isLoading'] }
const MaterialFormDialogStub = { template: '<div data-testid="material-form-dialog" />', props: ['visible', 'editTemplate'] }
const CopyQuarterDialogStub = { template: '<div data-testid="copy-quarter-dialog" />', props: ['visible'] }
const SidePanelStub = { template: '<div data-testid="epr-side-panel" />', props: ['totalCount', 'filingReadyCount', 'oneTimeCount'] }

vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string | number>) => {
    if (params) return key.replace(/{(\w+)}/g, (_, k) => String(params[k] ?? k))
    return key
  },
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

vi.stubGlobal('useAuthStore', () => ({
  tier: 'PRO_EPR',
}))

// Stub composables
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: vi.fn() }),
}))
vi.mock('primevue/useconfirm', () => ({
  useConfirm: () => ({ require: vi.fn() }),
}))
vi.mock('~/composables/auth/useTierGate', () => ({
  useTierGate: (tier: string) => ({
    hasAccess: ref(true),
    currentTier: ref('PRO_EPR'),
    requiredTier: tier,
    tierName: ref('PRO EPR'),
  }),
}))
vi.mock('~/composables/api/useApiError', () => ({
  useApiError: () => ({
    mapErrorType: (type: string) => type || 'Error',
  }),
}))

const mockFetchMaterials = vi.fn().mockResolvedValue(undefined)

vi.mock('~/stores/epr', () => ({
  useEprStore: () => ({
    materials: [],
    isLoading: false,
    error: null,
    totalCount: 0,
    verifiedCount: 0,
    oneTimeCount: 0,
    fetchMaterials: mockFetchMaterials,
    addMaterial: vi.fn(),
    updateMaterial: vi.fn(),
    deleteMaterial: vi.fn(),
    toggleRecurring: vi.fn(),
    copyFromQuarter: vi.fn(),
  }),
}))

vi.mock('~/stores/eprWizard', () => ({
  useEprWizardStore: () => ({
    activeStep: null,
    traversalPath: [],
    availableOptions: [],
    resolvedResult: null,
    isLoading: false,
    error: null,
    targetTemplateId: null,
    configVersion: null,
    lastConfirmSuccess: false,
    lastCloseReason: null,
    isActive: false,
    breadcrumb: [],
    currentStepIndex: 0,
    startWizard: vi.fn(),
    selectOption: vi.fn(),
    resolveResult: vi.fn(),
    confirmAndLink: vi.fn(),
    cancelWizard: vi.fn(),
    $reset: vi.fn(),
  }),
}))

const WizardStepperStub = { template: '<div data-testid="wizard-stepper" />' }

function mountPage() {
  return mount(EprPage, {
    global: {
      stubs: {
        Button: ButtonStub,
        ConfirmDialog: ConfirmDialogStub,
        EprMaterialInventoryBlock: MaterialInventoryBlockStub,
        EprMaterialFormDialog: MaterialFormDialogStub,
        EprCopyQuarterDialog: CopyQuarterDialogStub,
        EprSidePanel: SidePanelStub,
        EprWizardStepper: WizardStepperStub,
      },
    },
  })
}

describe('EPR Page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders page title', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('epr.materialLibrary.title')
  })

  it('renders add material button', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="add-material-button"]').exists()).toBe(true)
  })

  it('renders copy from quarter button', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="copy-quarter-button"]').exists()).toBe(true)
  })

  it('fetches materials on mount', () => {
    mountPage()
    expect(mockFetchMaterials).toHaveBeenCalledOnce()
  })
})
