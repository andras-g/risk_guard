import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LandingSocialProof from './LandingSocialProof.vue'

describe('LandingSocialProof', () => {
  function mountComponent() {
    return mount(LandingSocialProof)
  }

  it('renders the trust signals section', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('[data-testid="social-proof"]').exists()).toBe(true)
  })

  it('renders "trusted by" text from i18n', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('landing.socialProof.trustedBy')
  })

  it('renders partner count badge from i18n', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('landing.socialProof.partnerCount')
  })

  it('renders trust badges from i18n keys', () => {
    const wrapper = mountComponent()
    const text = wrapper.text()
    expect(text).toContain('landing.socialProof.badge.secure')
    expect(text).toContain('landing.socialProof.badge.realtime')
    expect(text).toContain('landing.socialProof.badge.compliant')
  })

  it('all visible text uses i18n keys (no raw text)', () => {
    const wrapper = mountComponent()
    // All text nodes should be i18n key strings (from vitest.setup.ts t() stub)
    const text = wrapper.text()
    // Every text segment should start with 'landing.' since all content is i18n-based
    const segments = text.split(/\s+/).filter(Boolean)
    segments.forEach((segment) => {
      expect(segment).toMatch(/^landing\./)
    })
  })
})
