import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import LandingSearchBar from './LandingSearchBar.vue'

// Mock navigateTo globally (already in vitest.setup.ts but ensure it's reset)
const mockNavigateTo = vi.fn()
vi.stubGlobal('navigateTo', mockNavigateTo)

describe('LandingSearchBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountComponent(props: Record<string, unknown> = {}) {
    return mount(LandingSearchBar, {
      props,
      global: {
        stubs: {
          InputText: {
            template: '<input :id="id" :value="modelValue" :placeholder="placeholder" :disabled="disabled" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" @input="$emit(\'input\', $event)" />',
            props: ['modelValue', 'placeholder', 'disabled', 'class', 'id', 'ariaDescribedby', 'ariaInvalid']
          },
          Button: {
            template: '<button :disabled="disabled" type="submit">{{ label }}</button>',
            props: ['label', 'disabled', 'icon', 'type']
          }
        }
      }
    })
  }

  it('renders search input and CTA button', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('input').exists()).toBe(true)
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('renders with i18n placeholder text', () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')
    // vitest.setup.ts stubs useI18n().t to return the key
    expect(input.attributes('placeholder')).toBe('landing.hero.searchPlaceholder')
  })

  it('renders CTA button with i18n label', () => {
    const wrapper = mountComponent()
    const button = wrapper.find('button')
    expect(button.text()).toBe('landing.hero.cta')
  })

  it('formats 8-digit tax number as ####-####', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    // Simulate typing 8 digits — the @input handler calls formatTaxNumber
    await input.setValue('12345678')
    await input.trigger('input')
    await nextTick()

    // The component should format as 1234-5678 via the shared formatTaxNumber utility
    expect(wrapper.find('input').element.value).toBe('1234-5678')
  })

  it('formats 11-digit tax number as ####-####-###', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    await input.setValue('12345678901')
    await input.trigger('input')
    await nextTick()

    expect(wrapper.find('input').element.value).toBe('1234-5678-901')
  })

  it('shows validation error on invalid input submit', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    await input.setValue('123')
    await input.trigger('input')
    await wrapper.find('form').trigger('submit')

    // Should show error message (i18n key)
    expect(wrapper.text()).toContain('landing.search.invalidTaxNumber')
  })

  it('navigates to screening on valid 8-digit submit', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    await input.setValue('12345678')
    await input.trigger('input')
    await wrapper.find('form').trigger('submit')

    expect(mockNavigateTo).toHaveBeenCalledWith('/screening/12345678')
  })

  it('navigates to screening on valid 11-digit submit', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    await input.setValue('12345678901')
    await input.trigger('input')
    await wrapper.find('form').trigger('submit')

    expect(mockNavigateTo).toHaveBeenCalledWith('/screening/12345678901')
  })

  it('does NOT navigate on invalid input', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    await input.setValue('abc')
    await input.trigger('input')
    await wrapper.find('form').trigger('submit')

    expect(mockNavigateTo).not.toHaveBeenCalled()
  })

  it('disables input and shows service unavailable message when serviceUnavailable prop is true', () => {
    const wrapper = mountComponent({ serviceUnavailable: true })
    const input = wrapper.find('input')

    expect(input.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('landing.search.serviceUnavailable')
  })

  it('does NOT show service unavailable message when serviceUnavailable is false', () => {
    const wrapper = mountComponent({ serviceUnavailable: false })
    expect(wrapper.text()).not.toContain('landing.search.serviceUnavailable')
  })

  it('has responsive classes for mobile layout', () => {
    const wrapper = mountComponent()
    const html = wrapper.html()
    // The form should have responsive classes that change layout at md breakpoint
    // Mobile: stacked (flex-col), Desktop: inline (md:flex-row)
    expect(html).toContain('flex-col')
    expect(html).toContain('md:flex-row')
  })

  it('has accessible form with role="search" and aria-label', () => {
    const wrapper = mountComponent()
    const form = wrapper.find('form')
    expect(form.attributes('role')).toBe('search')
    expect(form.attributes('aria-label')).toBe('landing.hero.searchAriaLabel')
  })

  it('has a visually-hidden label linked to the input', () => {
    const wrapper = mountComponent()
    const label = wrapper.find('label[for="landing-tax-number"]')
    expect(label.exists()).toBe(true)
    expect(label.classes()).toContain('sr-only')
    const input = wrapper.find('input')
    expect(input.attributes('id')).toBe('landing-tax-number')
  })

  it('links validation error to input via aria-describedby', async () => {
    const wrapper = mountComponent()
    const input = wrapper.find('input')

    // Before error: no aria-describedby
    expect(input.attributes('aria-describedby')).toBeUndefined()

    // Trigger validation error
    await input.setValue('123')
    await input.trigger('input')
    await wrapper.find('form').trigger('submit')

    // After error: aria-describedby links to error element
    expect(wrapper.find('input').attributes('aria-describedby')).toBe('landing-tax-error')
    expect(wrapper.find('#landing-tax-error').exists()).toBe(true)
  })
})
