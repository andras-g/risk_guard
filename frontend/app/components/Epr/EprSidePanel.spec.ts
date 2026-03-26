import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import EprSidePanel from './EprSidePanel.vue'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

function mountPanel(props: { totalCount?: number; filingReadyCount?: number; oneTimeCount?: number } = {}) {
  return mount(EprSidePanel, {
    props: {
      totalCount: props.totalCount ?? 10,
      filingReadyCount: props.filingReadyCount ?? 3,
      oneTimeCount: props.oneTimeCount ?? 2,
    },
  })
}

describe('EprSidePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the side panel container', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="epr-side-panel"]').exists()).toBe(true)
  })

  it('displays the summary title', () => {
    const wrapper = mountPanel()
    expect(wrapper.text()).toContain('epr.materialLibrary.sidePanel.title')
  })

  it('displays total count', () => {
    const wrapper = mountPanel({ totalCount: 15 })
    expect(wrapper.find('[data-testid="total-count"]').text()).toBe('15')
  })

  it('displays filing-ready count', () => {
    const wrapper = mountPanel({ filingReadyCount: 7 })
    expect(wrapper.find('[data-testid="verified-count"]').text()).toBe('7')
  })

  it('displays one-time count', () => {
    const wrapper = mountPanel({ oneTimeCount: 4 })
    expect(wrapper.find('[data-testid="one-time-count"]').text()).toBe('4')
  })

  it('displays zero counts correctly', () => {
    const wrapper = mountPanel({ totalCount: 0, filingReadyCount: 0, oneTimeCount: 0 })
    expect(wrapper.find('[data-testid="total-count"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="verified-count"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="one-time-count"]').text()).toBe('0')
  })

  it('renders all label keys', () => {
    const wrapper = mountPanel()
    const text = wrapper.text()
    expect(text).toContain('epr.materialLibrary.sidePanel.total')
    expect(text).toContain('epr.materialLibrary.sidePanel.filingReady')
    expect(text).toContain('epr.materialLibrary.sidePanel.oneTime')
  })
})
