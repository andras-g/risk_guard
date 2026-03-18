import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LandingFeatureCards from './LandingFeatureCards.vue'

describe('LandingFeatureCards', () => {
  function mountComponent() {
    return mount(LandingFeatureCards)
  }

  it('renders exactly 3 feature cards', () => {
    const wrapper = mountComponent()
    const cards = wrapper.findAll('[data-testid="feature-card"]')
    expect(cards).toHaveLength(3)
  })

  it('renders verdicts card with i18n title and description', () => {
    const wrapper = mountComponent()
    const text = wrapper.text()
    expect(text).toContain('landing.features.verdicts.title')
    expect(text).toContain('landing.features.verdicts.description')
  })

  it('renders speed card with i18n title and description', () => {
    const wrapper = mountComponent()
    const text = wrapper.text()
    expect(text).toContain('landing.features.speed.title')
    expect(text).toContain('landing.features.speed.description')
  })

  it('renders evidence card with i18n title and description', () => {
    const wrapper = mountComponent()
    const text = wrapper.text()
    expect(text).toContain('landing.features.evidence.title')
    expect(text).toContain('landing.features.evidence.description')
  })

  it('each card has Landing Hero Card styling (rounded-2xl, shadow-lg)', () => {
    const wrapper = mountComponent()
    const cards = wrapper.findAll('[data-testid="feature-card"]')
    cards.forEach((card) => {
      expect(card.classes()).toContain('rounded-2xl')
      expect(card.classes()).toContain('shadow-lg')
    })
  })

  it('each card contains an SVG icon', () => {
    const wrapper = mountComponent()
    const cards = wrapper.findAll('[data-testid="feature-card"]')
    cards.forEach((card) => {
      expect(card.find('svg').exists()).toBe(true)
    })
  })

  it('uses CSS Grid with responsive auto-fill layout', () => {
    const wrapper = mountComponent()
    const grid = wrapper.find('[data-testid="feature-grid"]')
    expect(grid.exists()).toBe(true)
    // Tailwind grid class + inline template-columns for auto-fill responsive layout
    expect(grid.classes()).toContain('grid')
    expect(grid.classes()).toContain('gap-8')
    const style = grid.attributes('style') || ''
    expect(style).toContain('auto-fill')
  })
})
