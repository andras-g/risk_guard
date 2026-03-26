import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfidenceBadge from './ConfidenceBadge.vue'

vi.mock('primevue/tag', () => ({
  default: {
    name: 'Tag',
    template: '<span data-testid="confidence-tag" :data-severity="severity">{{ value }}</span>',
    props: ['severity', 'value', 'icon'],
  },
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

describe('ConfidenceBadge', () => {
  it('renders HIGH confidence badge', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'HIGH' },
    })
    expect(wrapper.find('[data-testid="confidence-badge"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="confidence-tag"]').exists()).toBe(true)
    // Verify the i18n key appears somewhere in rendered HTML
    expect(wrapper.html()).toContain('epr.wizard.confidence.high')
  })

  it('renders MEDIUM confidence badge', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'MEDIUM' },
    })
    // Verify text rendered somewhere in the component
    const html = wrapper.html()
    expect(html).toContain('epr.wizard.confidence.medium')
  })

  it('renders LOW confidence badge', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'LOW' },
    })
    const html = wrapper.html()
    expect(html).toContain('epr.wizard.confidence.low')
  })

  it('shows reason when showReason is true and reason is provided', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'LOW', showReason: true, reason: 'composite_material' },
    })
    expect(wrapper.find('[data-testid="confidence-reason"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('epr.wizard.confidence.reason.composite_material')
  })

  it('hides reason when showReason is false', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'LOW', showReason: false, reason: 'composite_material' },
    })
    expect(wrapper.find('[data-testid="confidence-reason"]').exists()).toBe(false)
  })

  it('hides reason when reason is not provided', () => {
    const wrapper = mount(ConfidenceBadge, {
      props: { confidence: 'HIGH', showReason: true },
    })
    expect(wrapper.find('[data-testid="confidence-reason"]').exists()).toBe(false)
  })
})
