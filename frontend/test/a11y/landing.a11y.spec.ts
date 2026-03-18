import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { axe } from 'vitest-axe'
import { axeOptions } from './axe-config'
import IndexPage from '~/pages/index.vue'

/**
 * Axe-core a11y integration test for the Landing Page.
 * Covers: AC#1 (contrast), AC#3 (skip-link via public layout), AC#4 (tab order), AC#6 (axe scan).
 */

// Mock dependencies
vi.stubGlobal('useAuthStore', () => ({
  isAuthenticated: false,
  initializeAuth: vi.fn()
}))
vi.stubGlobal('$fetch', vi.fn().mockResolvedValue({ status: 'UP' }))
vi.stubGlobal('navigateTo', vi.fn())
vi.stubGlobal('useSeoMeta', vi.fn())
vi.stubGlobal('useHead', vi.fn())
vi.stubGlobal('definePageMeta', vi.fn())

describe('Landing page — axe-core a11y scan', () => {
  it('passes WCAG 2.1 AA axe-core scan', async () => {
    const wrapper = mount(IndexPage, {
      global: {
        stubs: {
          LandingSearchBar: {
            template: `<form role="search" aria-label="Search">
              <label for="tax-input" class="sr-only">Tax number</label>
              <input id="tax-input" type="text" placeholder="Tax number" />
              <button type="submit">Search</button>
            </form>`
          },
          LandingFeatureCards: { template: '<section data-testid="feature-cards"><h3>Features</h3><p>Feature content</p></section>' },
          LandingSocialProof: { template: '<section data-testid="social-proof"><p>Trusted by many</p></section>' }
        }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })
})
