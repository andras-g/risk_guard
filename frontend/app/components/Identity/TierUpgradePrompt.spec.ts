import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TierUpgradePrompt from './TierUpgradePrompt.vue'

vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) return `${key}:${JSON.stringify(params)}`
    return key
  }
}))

const ButtonStub = { template: '<button :data-label="$attrs.label" data-testid="tier-upgrade-cta"><slot /></button>' }

function mountPrompt(requiredTier: string = 'PRO', featureName: string = 'common.nav.watchlist') {
  return mount(TierUpgradePrompt, {
    props: { requiredTier, featureName },
    global: {
      stubs: { Button: ButtonStub }
    }
  })
}

describe('TierUpgradePrompt', () => {
  it('renders the upgrade title via i18n', () => {
    const wrapper = mountPrompt()
    expect(wrapper.find('h2').text()).toBe('common.tierGate.title')
  })

  it('renders the feature name via i18n', () => {
    const wrapper = mountPrompt('PRO', 'common.nav.watchlist')
    expect(wrapper.find('[data-testid="feature-name"]').text()).toBe('common.nav.watchlist')
  })

  it('renders tier description with tier name', () => {
    const wrapper = mountPrompt('PRO')
    const desc = wrapper.find('[data-testid="tier-description"]').text()
    expect(desc).toContain('common.tierGate.description')
    expect(desc).toContain('common.tiers.PRO')
  })

  it('CTA button is present and has correct label', () => {
    const wrapper = mountPrompt()
    const cta = wrapper.find('[data-testid="tier-upgrade-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.attributes('data-label')).toBe('common.tierGate.cta')
  })

  it('CTA button is focusable', () => {
    const wrapper = mountPrompt()
    const cta = wrapper.find('[data-testid="tier-upgrade-cta"]')
    expect(cta.element.tagName.toLowerCase()).toBe('button')
  })

  it('has role="region" with aria-labelledby for screen readers', () => {
    const wrapper = mountPrompt()
    const region = wrapper.find('[role="region"]')
    expect(region.exists()).toBe(true)
    expect(region.attributes('aria-labelledby')).toBe('tier-gate-title')
    expect(wrapper.find('#tier-gate-title').exists()).toBe(true)
  })

  it('renders for PRO_EPR requirement', () => {
    const wrapper = mountPrompt('PRO_EPR')
    const desc = wrapper.find('[data-testid="tier-description"]').text()
    expect(desc).toContain('common.tiers.PRO_EPR')
  })

  it('has proper heading hierarchy (h2)', () => {
    const wrapper = mountPrompt()
    expect(wrapper.find('h2').exists()).toBe(true)
  })
})
