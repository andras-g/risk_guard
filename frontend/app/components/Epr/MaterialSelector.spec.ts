import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MaterialSelector from './MaterialSelector.vue'
import type { WizardOption } from '~/types/epr'

const mockOptions: WizardOption[] = [
  { code: '01', label: 'Paper and cardboard', description: null },
  { code: '02', label: 'Plastic', description: 'Including PET, HDPE' },
  { code: '03', label: 'Wood', description: null },
]

describe('MaterialSelector', () => {
  it('renders option cards for each option', () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: mockOptions, selectedCode: null, isLoading: false },
    })
    expect(wrapper.findAll('[data-testid^="material-option-"]')).toHaveLength(3)
  })

  it('shows loading skeleton when isLoading is true', () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: [], selectedCode: null, isLoading: true },
    })
    expect(wrapper.find('[data-testid="material-selector-skeleton"]').exists()).toBe(true)
  })

  it('highlights the selected option with emerald border', () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: mockOptions, selectedCode: '02', isLoading: false },
    })
    const selected = wrapper.find('[data-testid="material-option-02"]')
    expect(selected.classes()).toContain('border-[#15803D]')
  })

  it('emits select event when an option is clicked', async () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: mockOptions, selectedCode: null, isLoading: false },
    })
    await wrapper.find('[data-testid="material-option-01"]').trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual([mockOptions[0]])
  })

  it('shows description when present', () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: mockOptions, selectedCode: null, isLoading: false },
    })
    expect(wrapper.text()).toContain('Including PET, HDPE')
  })

  it('shows option code', () => {
    const wrapper = mount(MaterialSelector, {
      props: { options: mockOptions, selectedCode: null, isLoading: false },
    })
    expect(wrapper.text()).toContain('01')
    expect(wrapper.text()).toContain('02')
    expect(wrapper.text()).toContain('03')
  })
})
