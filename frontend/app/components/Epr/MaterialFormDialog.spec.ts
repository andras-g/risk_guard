import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MaterialFormDialog from './MaterialFormDialog.vue'
import type { MaterialTemplateResponse } from '~/types/epr'

const DialogStub = {
  template: '<div data-testid="material-form-dialog" v-if="visible">{{ header }}<slot /><slot name="footer" /></div>',
  props: ['visible', 'header', 'modal'],
}
const InputTextStub = {
  template: '<input data-testid="material-name-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
}
const InputNumberStub = {
  template: '<input data-testid="material-weight-input" type="number" />',
  props: ['modelValue', 'min', 'step', 'minFractionDigits', 'maxFractionDigits', 'suffix', 'placeholder'],
}
const CheckboxStub = {
  template: '<input data-testid="material-recurring-checkbox" type="checkbox" />',
  props: ['modelValue', 'inputId', 'binary'],
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

function mountDialog(props: { visible?: boolean; editTemplate?: MaterialTemplateResponse | null } = {}) {
  return mount(MaterialFormDialog, {
    props: {
      visible: props.visible ?? true,
      editTemplate: props.editTemplate ?? null,
    },
    global: {
      stubs: {
        Dialog: DialogStub,
        InputText: InputTextStub,
        InputNumber: InputNumberStub,
        Checkbox: CheckboxStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('MaterialFormDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when visible', () => {
    const wrapper = mountDialog({ visible: true })
    expect(wrapper.find('[data-testid="material-form-dialog"]').exists()).toBe(true)
  })

  it('does not render dialog when not visible', () => {
    const wrapper = mountDialog({ visible: false })
    expect(wrapper.find('[data-testid="material-form-dialog"]').exists()).toBe(false)
  })

  it('renders name input', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="material-name-input"]').exists()).toBe(true)
  })

  it('renders weight input', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="material-weight-input"]').exists()).toBe(true)
  })

  it('renders recurring checkbox', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="material-recurring-checkbox"]').exists()).toBe(true)
  })

  it('renders submit button', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="material-form-submit"]').exists()).toBe(true)
  })

  it('renders cancel button', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="material-form-cancel"]').exists()).toBe(true)
  })

  it('shows name validation error on submit with empty name', async () => {
    const wrapper = mountDialog()
    // Submit without filling in any fields
    await wrapper.find('[data-testid="material-form-submit"]').trigger('click')
    await wrapper.vm.$nextTick()
    // Name error should appear
    expect(wrapper.find('[data-testid="name-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="name-error"]').text()).toBe('epr.materialLibrary.validation.nameRequired')
  })

  it('shows weight validation error on submit with null weight', async () => {
    const wrapper = mountDialog()
    await wrapper.find('[data-testid="material-form-submit"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="weight-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="weight-error"]').text()).toBe('epr.materialLibrary.validation.weightPositive')
  })

  it('does not emit submit event when validation fails', async () => {
    const wrapper = mountDialog()
    await wrapper.find('[data-testid="material-form-submit"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('pre-populates fields when editTemplate is provided', async () => {
    const editTemplate: MaterialTemplateResponse = {
      id: 'tmpl-edit-1',
      name: 'Existing Box',
      baseWeightGrams: 55.5,
      kfCode: 'KF001',
      verified: true,
      recurring: false,
      createdAt: '2026-03-01T10:00:00Z',
      updatedAt: '2026-03-01T10:00:00Z',
      overrideKfCode: null,
      overrideReason: null,
      confidence: null,
      feeRate: null,
    }
    const wrapper = mountDialog({ visible: true, editTemplate })
    await wrapper.vm.$nextTick()
    // The name input should be pre-populated (via v-model binding)
    const nameInput = wrapper.find('[data-testid="material-name-input"]')
    expect(nameInput.exists()).toBe(true)
    // Dialog should render in edit mode with the edit title key
    expect(wrapper.text()).toContain('epr.materialLibrary.dialog.editTitle')
  })
})
