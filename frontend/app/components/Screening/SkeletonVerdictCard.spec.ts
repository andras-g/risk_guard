import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SkeletonVerdictCard from './SkeletonVerdictCard.vue'

/**
 * Component tests for SkeletonVerdictCard.vue.
 *
 * Uses @vue/test-utils mount to verify actual DOM rendering, visibility toggling,
 * and source placeholder structure. Co-located per architecture rules.
 */

// Stub PrimeVue Skeleton component
const SkeletonStub = { template: '<div class="skeleton-stub" />', name: 'Skeleton' }

// Stub useI18n — returns the key itself as the translation
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key
}))

function mountCard(visible: boolean) {
  return mount(SkeletonVerdictCard, {
    props: { visible },
    global: {
      stubs: { Skeleton: SkeletonStub }
    }
  })
}

describe('SkeletonVerdictCard — visibility', () => {
  it('should render the card when visible prop is true', () => {
    const wrapper = mountCard(true)
    expect(wrapper.find('div').exists()).toBe(true)
    expect(wrapper.html()).toContain('skeleton-stub')
  })

  it('should not render anything when visible prop is false', () => {
    const wrapper = mountCard(false)
    expect(wrapper.html()).toBe('<!--v-if-->')
  })
})

describe('SkeletonVerdictCard — source placeholders', () => {
  it('should render exactly 3 source placeholder rows', () => {
    const wrapper = mountCard(true)
    const sourceRows = wrapper.findAll('.text-sm.text-slate-500')
    expect(sourceRows).toHaveLength(3)
  })

  it('should render NAV Debt, Legal Status, and Company Registry labels via i18n keys', () => {
    const wrapper = mountCard(true)
    const html = wrapper.html()
    expect(html).toContain('screening.sources.navDebt')
    expect(html).toContain('screening.sources.legalStatus')
    expect(html).toContain('screening.sources.companyRegistry')
  })

  it('should render icon prefix for each source', () => {
    const wrapper = mountCard(true)
    const sourceLabels = wrapper.findAll('.text-sm.text-slate-500')
    sourceLabels.forEach(label => {
      expect(label.find('i.pi-circle').exists()).toBe(true)
    })
  })
})
