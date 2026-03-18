import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n, useI18n as realUseI18n } from 'vue-i18n'
import { defineComponent, nextTick } from 'vue'

/**
 * Integration test for locale switching.
 *
 * Uses a real vue-i18n instance (not a mock) to verify that:
 * - Components render Hungarian text by default
 * - After switching to English, all $t() calls return English values
 * - After switching back to Hungarian, all $t() calls return Hungarian values
 *
 * Note: We import the real useI18n from vue-i18n and use it explicitly in the
 * test component, bypassing the global mock from vitest.setup.ts.
 */

// Minimal message fixture matching our app namespace structure
const messages = {
  hu: {
    common: {
      actions: { search: 'Keresés' },
      nav: { dashboard: 'Irányítópult' },
      placeholder: { comingSoon: 'Hamarosan elérhető' },
    },
    landing: {
      hero: { headline: 'Partnerkockázat átvilágítás másodpercek alatt' },
    },
  },
  en: {
    common: {
      actions: { search: 'Search' },
      nav: { dashboard: 'Dashboard' },
      placeholder: { comingSoon: 'Coming soon' },
    },
    landing: {
      hero: { headline: 'Partner risk screening in seconds' },
    },
  },
}

function createTestI18n(locale = 'hu') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages,
  })
}

// Test component uses the REAL useI18n from vue-i18n (not the global mock)
const TestLocaleComponent = defineComponent({
  setup() {
    const { t, locale } = realUseI18n()
    return { t, locale }
  },
  template: `
    <div>
      <span data-testid="headline">{{ t('landing.hero.headline') }}</span>
      <span data-testid="search">{{ t('common.actions.search') }}</span>
      <span data-testid="dashboard">{{ t('common.nav.dashboard') }}</span>
      <span data-testid="coming-soon">{{ t('common.placeholder.comingSoon') }}</span>
    </div>
  `,
})

describe('Locale switching — integration', () => {
  let i18n: ReturnType<typeof createTestI18n>

  beforeEach(() => {
    i18n = createTestI18n('hu')
  })

  it('should render Hungarian text when locale is hu', () => {
    const wrapper = mount(TestLocaleComponent, { global: { plugins: [i18n] } })
    expect(wrapper.find('[data-testid="headline"]').text()).toBe('Partnerkockázat átvilágítás másodpercek alatt')
    expect(wrapper.find('[data-testid="search"]').text()).toBe('Keresés')
    expect(wrapper.find('[data-testid="dashboard"]').text()).toBe('Irányítópult')
    expect(wrapper.find('[data-testid="coming-soon"]').text()).toBe('Hamarosan elérhető')
  })

  it('should render English text after switching locale to en', async () => {
    const wrapper = mount(TestLocaleComponent, { global: { plugins: [i18n] } })

    // Switch locale
    i18n.global.locale.value = 'en'
    await nextTick()

    expect(wrapper.find('[data-testid="headline"]').text()).toBe('Partner risk screening in seconds')
    expect(wrapper.find('[data-testid="search"]').text()).toBe('Search')
    expect(wrapper.find('[data-testid="dashboard"]').text()).toBe('Dashboard')
    expect(wrapper.find('[data-testid="coming-soon"]').text()).toBe('Coming soon')
  })

  it('should render Hungarian text after switching back from en to hu', async () => {
    const wrapper = mount(TestLocaleComponent, { global: { plugins: [i18n] } })

    // Switch to English
    i18n.global.locale.value = 'en'
    await nextTick()

    // Switch back to Hungarian
    i18n.global.locale.value = 'hu'
    await nextTick()

    expect(wrapper.find('[data-testid="headline"]').text()).toBe('Partnerkockázat átvilágítás másodpercek alatt')
    expect(wrapper.find('[data-testid="search"]').text()).toBe('Keresés')
  })
})
