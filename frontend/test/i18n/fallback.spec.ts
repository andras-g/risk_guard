import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n, useI18n as realUseI18n } from 'vue-i18n'
import { defineComponent } from 'vue'

/**
 * Integration test for i18n fallback behavior.
 *
 * Verifies that when a key is missing in Hungarian,
 * the English fallback value is returned without errors.
 * AC #6: Missing translation key → English fallback displayed.
 *
 * Note: We import the real useI18n from vue-i18n to bypass the global mock.
 */

const messages = {
  hu: {
    // Intentionally missing "onlyInEn" key
    common: {
      actions: { search: 'Keresés' },
    },
  },
  en: {
    common: {
      actions: { search: 'Search' },
    },
    // Key exists only in English
    onlyInEn: 'This key exists only in English',
  },
}

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'hu',
    fallbackLocale: 'en',
    messages,
    missingWarn: false, // Suppress warnings in test output
    fallbackWarn: false,
  })
}

const TestFallbackComponent = defineComponent({
  setup() {
    const { t } = realUseI18n()
    return { t }
  },
  template: `
    <div>
      <span data-testid="existing">{{ t('common.actions.search') }}</span>
      <span data-testid="fallback">{{ t('onlyInEn') }}</span>
    </div>
  `,
})

describe('i18n fallback — missing key', () => {
  it('should display Hungarian value for existing key', () => {
    const i18n = createTestI18n()
    const wrapper = mount(TestFallbackComponent, { global: { plugins: [i18n] } })
    expect(wrapper.find('[data-testid="existing"]').text()).toBe('Keresés')
  })

  it('should display English fallback when key is missing in Hungarian', () => {
    const i18n = createTestI18n()
    const wrapper = mount(TestFallbackComponent, { global: { plugins: [i18n] } })
    expect(wrapper.find('[data-testid="fallback"]').text()).toBe('This key exists only in English')
  })

  it('should NOT display raw key string for fallback content', () => {
    const i18n = createTestI18n()
    const wrapper = mount(TestFallbackComponent, { global: { plugins: [i18n] } })
    const fallbackText = wrapper.find('[data-testid="fallback"]').text()
    expect(fallbackText).not.toBe('onlyInEn')
    expect(fallbackText).toBe('This key exists only in English')
  })
})
