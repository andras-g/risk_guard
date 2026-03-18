import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SkipLink from './SkipLink.vue'

/**
 * Unit tests for SkipLink.vue — accessible skip-to-content link.
 * Co-located with SkipLink.vue per architecture rules.
 */
describe('SkipLink', () => {
  function mountComponent() {
    return mount(SkipLink, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })
  }

  it('renders with sr-only class (visually hidden by default)', () => {
    const wrapper = mountComponent()
    const link = wrapper.find('[data-testid="skip-link"]')
    expect(link.exists()).toBe(true)
    expect(link.classes()).toContain('sr-only')
  })

  it('has focus classes that make it visible on focus', () => {
    const wrapper = mountComponent()
    const link = wrapper.find('[data-testid="skip-link"]')
    expect(link.classes()).toContain('focus:not-sr-only')
    expect(link.classes()).toContain('focus:fixed')
  })

  it('links to #main-content', () => {
    const wrapper = mountComponent()
    const link = wrapper.find('[data-testid="skip-link"]')
    expect(link.attributes('href')).toBe('#main-content')
  })

  it('uses i18n key for label text', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toBe('common.a11y.skipToMain')
  })

  it('moves focus to #main-content on click', async () => {
    const wrapper = mountComponent()
    const mockElement = { focus: vi.fn() }
    vi.spyOn(document, 'getElementById').mockReturnValue(mockElement as any)

    await wrapper.find('[data-testid="skip-link"]').trigger('click')

    expect(document.getElementById).toHaveBeenCalledWith('main-content')
    expect(mockElement.focus).toHaveBeenCalledOnce()

    vi.restoreAllMocks()
  })
})
