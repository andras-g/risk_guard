import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LiveRegion from './LiveRegion.vue'

/**
 * Unit tests for LiveRegion.vue — reusable ARIA-live region wrapper.
 * Co-located with LiveRegion.vue per architecture rules.
 */
describe('LiveRegion', () => {
  it('renders slot content', () => {
    const wrapper = mount(LiveRegion, {
      slots: { default: '<p>Verdict loaded</p>' },
    })
    expect(wrapper.text()).toContain('Verdict loaded')
  })

  it('has aria-live="polite" by default', () => {
    const wrapper = mount(LiveRegion)
    const div = wrapper.find('[data-testid="live-region"]')
    expect(div.attributes('aria-live')).toBe('polite')
  })

  it('has aria-atomic="true"', () => {
    const wrapper = mount(LiveRegion)
    const div = wrapper.find('[data-testid="live-region"]')
    expect(div.attributes('aria-atomic')).toBe('true')
  })

  it('can switch to aria-live="assertive" via prop', () => {
    const wrapper = mount(LiveRegion, {
      props: { mode: 'assertive' },
    })
    const div = wrapper.find('[data-testid="live-region"]')
    expect(div.attributes('aria-live')).toBe('assertive')
  })

  it('applies sr-only class when srOnly prop is true', () => {
    const wrapper = mount(LiveRegion, {
      props: { srOnly: true },
    })
    const div = wrapper.find('[data-testid="live-region"]')
    expect(div.classes()).toContain('sr-only')
  })

  it('does not apply sr-only class by default', () => {
    const wrapper = mount(LiveRegion)
    const div = wrapper.find('[data-testid="live-region"]')
    expect(div.classes()).not.toContain('sr-only')
  })
})
