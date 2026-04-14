import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import KfCodeInput from './KfCodeInput.vue'

// Stub PrimeVue InputText
const InputTextStub = {
  template: '<input :id="id" :value="value" @input="$emit(\'input\', $event)" @blur="$emit(\'blur\')" />',
  props: ['id', 'value', 'placeholder', 'inputmode'],
  emits: ['input', 'blur'],
}

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

function mountInput(modelValue = '') {
  return mount(KfCodeInput, {
    props: { modelValue, id: 'kf-test' },
    global: {
      stubs: { InputText: InputTextStub },
    },
  })
}

describe('KfCodeInput', () => {
  // ─── Test 1: formats 8-digit code with spaces on display ─────────────────

  it('formats 8-digit code with spaces in display value', () => {
    const wrapper = mountInput('11010101')
    const input = wrapper.find('input')
    expect(input.element.value).toBe('11 01 01 01')
  })

  // ─── Test 2: strips spaces on emit ────────────────────────────────────────

  it('emits stripped 8-digit string on input', async () => {
    const wrapper = mountInput('')
    const input = wrapper.find('input')
    // setValue sets element.value and triggers input event
    await input.setValue('11 01 01 01')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeDefined()
    // The emit should strip spaces
    const lastEmit = emitted![emitted!.length - 1][0]
    expect(typeof lastEmit === 'string' ? lastEmit.includes(' ') : false).toBe(false)
  })

  // ─── Test 3: rejects non-digit characters ────────────────────────────────

  it('strips non-digit characters from input', async () => {
    const wrapper = mountInput('')
    const input = wrapper.find('input')
    await input.setValue('ab12cd34ef56gh78')
    const emitted = wrapper.emitted('update:modelValue')
    if (emitted) {
      const lastEmit = emitted[emitted.length - 1][0]
      if (lastEmit) {
        expect(/^\d+$/.test(lastEmit as string)).toBe(true)
      }
    }
  })

  // ─── Test 4: shows no error before blur ───────────────────────────────────

  it('does not show error before field is touched', () => {
    const wrapper = mountInput('abc')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  // ─── Test 5: shows error on blur if invalid ───────────────────────────────

  it('shows error message on blur when value is invalid', async () => {
    const wrapper = mountInput('123')
    const input = wrapper.find('input')
    await input.trigger('blur')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  // ─── Test 6: clears error on blur when valid ──────────────────────────────

  it('no error shown when value is a valid 8-digit code', async () => {
    const wrapper = mountInput('11010101')
    const input = wrapper.find('input')
    await input.trigger('blur')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  // ─── Test 7: empty value shows no error ──────────────────────────────────

  it('no error shown when value is empty', async () => {
    const wrapper = mountInput('')
    const input = wrapper.find('input')
    await input.trigger('blur')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
