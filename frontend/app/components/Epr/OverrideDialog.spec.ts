import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import OverrideDialog from './OverrideDialog.vue'

vi.mock('primevue/dialog', () => ({
  default: {
    name: 'Dialog',
    template: `<div v-if="visible" data-testid="override-dialog">
      <div class="dialog-body"><slot /></div>
      <div class="dialog-footer"><slot name="footer" /></div>
    </div>`,
    props: ['visible', 'header', 'modal', 'closable', 'style', 'breakpoints'],
    emits: ['update:visible'],
  },
}))
vi.mock('primevue/autocomplete', () => ({
  default: {
    name: 'AutoComplete',
    template: '<input data-testid="override-autocomplete" />',
    props: ['modelValue', 'suggestions', 'field', 'placeholder', 'forceSelection', 'dropdown', 'optionGroupLabel', 'optionGroupChildren'],
    emits: ['update:modelValue', 'complete'],
  },
}))
vi.mock('primevue/textarea', () => ({
  default: {
    name: 'Textarea',
    template: '<textarea data-testid="override-reason" />',
    props: ['modelValue', 'placeholder', 'maxlength', 'rows'],
    emits: ['update:modelValue'],
  },
}))
vi.mock('primevue/button', () => ({
  default: {
    name: 'Button',
    template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
    props: ['label', 'severity', 'outlined', 'disabled'],
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

const mockFetchAllKfCodes = vi.fn()
const mockApplyOverride = vi.fn()

const createMockStore = (overrides = {}) => ({
  allKfCodes: [
    { kfCode: '11010101', feeCode: '1101', feeRate: 20.44, currency: 'HUF', classification: 'Paper', productStreamLabel: 'Packaging' },
  ],
  isOverrideActive: false,
  fetchAllKfCodes: mockFetchAllKfCodes,
  applyOverride: mockApplyOverride,
  clearOverride: vi.fn(),
  ...overrides,
})

let mockStore = createMockStore()

vi.mock('~/stores/eprWizard', () => ({
  useEprWizardStore: () => mockStore,
}))

describe('OverrideDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore = createMockStore()
  })

  it('renders dialog when visible is true', () => {
    const wrapper = mount(OverrideDialog, {
      props: { visible: true },
    })
    expect(wrapper.find('[data-testid="override-dialog"]').exists()).toBe(true)
  })

  it('component mounts without errors when visible is false', () => {
    const wrapper = mount(OverrideDialog, {
      props: { visible: false },
    })
    // The Dialog mock wraps content conditionally; component should mount cleanly
    expect(wrapper.exists()).toBe(true)
  })

  it('renders autocomplete and reason textarea when visible', () => {
    const wrapper = mount(OverrideDialog, {
      props: { visible: true },
    })
    expect(wrapper.find('[data-testid="override-autocomplete"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="override-reason"]').exists()).toBe(true)
  })

  it('calls applyOverride when component method is invoked with a selected entry', async () => {
    mockStore = createMockStore()
    const wrapper = mount(OverrideDialog, {
      props: { visible: true },
    })

    // Simulate selecting a KF-code entry by setting the component's internal state
    const vm = wrapper.vm as any
    vm.selectedEntry = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      classification: 'Paper',
      productStreamLabel: 'Packaging',
    }
    await nextTick()

    // Invoke the applyOverride method directly (buttons are auto-imported and not fully rendered in mocks)
    vm.applyOverride()
    await nextTick()

    expect(mockApplyOverride).toHaveBeenCalledWith(
      expect.objectContaining({ kfCode: '11010101' }),
      undefined,
    )
  })

  it('does not call applyOverride when no entry is selected', async () => {
    mockStore = createMockStore()
    const wrapper = mount(OverrideDialog, {
      props: { visible: true },
    })

    // No entry selected — applyOverride should be a no-op
    const vm = wrapper.vm as any
    expect(vm.selectedEntry).toBeNull()
    vm.applyOverride()
    await nextTick()

    expect(mockApplyOverride).not.toHaveBeenCalled()
  })

  it('searchKfCodes returns grouped results by product stream', async () => {
    mockStore = createMockStore({
      allKfCodes: [
        { kfCode: '11010101', feeCode: '1101', feeRate: 20.44, currency: 'HUF', classification: 'Paper', productStreamLabel: 'Packaging' },
        { kfCode: '21010101', feeCode: '2101', feeRate: 5.00, currency: 'HUF', classification: 'Large Household', productStreamLabel: 'EEE' },
      ],
    })
    const wrapper = mount(OverrideDialog, {
      props: { visible: true },
    })
    const vm = wrapper.vm as any
    // Trigger search with empty query to match all
    vm.searchKfCodes({ query: '' })
    await nextTick()
    // filteredGroups should be grouped by productStreamLabel
    expect(vm.filteredGroups).toHaveLength(2)
    expect(vm.filteredGroups[0].label).toBe('Packaging')
    expect(vm.filteredGroups[0].items).toHaveLength(1)
    expect(vm.filteredGroups[1].label).toBe('EEE')
  })

  it('fetches kf codes when dialog becomes visible', async () => {
    const wrapper = mount(OverrideDialog, {
      props: { visible: false },
    })
    await wrapper.setProps({ visible: true })
    await nextTick()
    await flushPromises()
    expect(mockFetchAllKfCodes).toHaveBeenCalled()
  })
})
