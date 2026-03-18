import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import LocaleSwitcher from './LocaleSwitcher.vue'

/**
 * Component tests for LocaleSwitcher.vue.
 *
 * Verifies locale label rendering, toggle behavior, ARIA label,
 * and i18n key usage. Co-located per architecture rules.
 */

const changeLocaleMock = vi.fn()
let currentLocale = 'hu'

vi.mock('~/composables/i18n/useLocaleSync', () => ({
  useLocaleSync: () => ({
    changeLocale: changeLocaleMock,
  }),
}))

beforeEach(() => {
  currentLocale = 'hu'
  changeLocaleMock.mockReset()
  vi.stubGlobal('useI18n', () => ({
    locale: { value: currentLocale },
    t: (key: string) => key,
  }))
})

function mountSwitcher(): VueWrapper {
  return mount(LocaleSwitcher)
}

describe('LocaleSwitcher — rendering', () => {
  it('should render the current locale label in uppercase', () => {
    const wrapper = mountSwitcher()
    expect(wrapper.text()).toBe('HU')
  })

  it('should render "EN" when locale is English', () => {
    currentLocale = 'en'
    vi.stubGlobal('useI18n', () => ({
      locale: { value: currentLocale },
      t: (key: string) => key,
    }))
    const wrapper = mountSwitcher()
    expect(wrapper.text()).toBe('EN')
  })

  it('should have the locale-switcher test id', () => {
    const wrapper = mountSwitcher()
    expect(wrapper.find('[data-testid="locale-switcher"]').exists()).toBe(true)
  })
})

describe('LocaleSwitcher — toggle behavior', () => {
  it('should call changeLocale with "en" when current locale is "hu"', async () => {
    const wrapper = mountSwitcher()
    await wrapper.find('button').trigger('click')
    expect(changeLocaleMock).toHaveBeenCalledWith('en')
  })

  it('should call changeLocale with "hu" when current locale is "en"', async () => {
    currentLocale = 'en'
    vi.stubGlobal('useI18n', () => ({
      locale: { value: currentLocale },
      t: (key: string) => key,
    }))
    const wrapper = mountSwitcher()
    await wrapper.find('button').trigger('click')
    expect(changeLocaleMock).toHaveBeenCalledWith('hu')
  })
})

describe('LocaleSwitcher — accessibility', () => {
  it('should have aria-label using i18n key', () => {
    const wrapper = mountSwitcher()
    const btn = wrapper.find('button')
    expect(btn.attributes('aria-label')).toBe('common.locale.switchTo')
  })
})
