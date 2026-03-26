import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import IndexPage from './index.vue'

/**
 * Integration tests for the Landing Page (pages/index.vue).
 * Verifies: public layout, component composition, auth redirect,
 * SEO meta, i18n keys, and JSON-LD schema.
 *
 * Co-located per architecture rules (Story 3.0b, Task 9).
 */

// --- Mock auth store ---
const mockIsAuthenticated = { value: false }
const mockInitializeAuth = vi.fn()

vi.stubGlobal('useAuthStore', () => ({
  isAuthenticated: mockIsAuthenticated.value,
  initializeAuth: mockInitializeAuth
}))

// --- Mock $fetch for health check ---
const mockFetch = vi.fn().mockResolvedValue({ status: 'UP' })
vi.stubGlobal('$fetch', mockFetch)

// --- Mock navigateTo ---
const mockNavigateTo = vi.fn()
vi.stubGlobal('navigateTo', mockNavigateTo)

// --- Mock useSeoMeta ---
const mockSeoMeta = vi.fn()
vi.stubGlobal('useSeoMeta', mockSeoMeta)

// --- Mock useHead ---
const mockUseHead = vi.fn()
vi.stubGlobal('useHead', mockUseHead)

// --- Mock definePageMeta ---
const mockDefinePageMeta = vi.fn()
vi.stubGlobal('definePageMeta', mockDefinePageMeta)

// Child component stubs
const LandingSearchBarStub = {
  template: '<div data-testid="stub-search-bar"></div>',
  props: ['serviceUnavailable']
}
const LandingFeatureCardsStub = {
  template: '<div data-testid="stub-feature-cards"></div>'
}
const LandingSocialProofStub = {
  template: '<div data-testid="stub-social-proof"></div>'
}

describe('Landing Page (index.vue)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockIsAuthenticated.value = false
    mockFetch.mockResolvedValue({ status: 'UP' })
  })

  function mountPage() {
    return mount(IndexPage, {
      global: {
        stubs: {
          LandingSearchBar: LandingSearchBarStub,
          LandingFeatureCards: LandingFeatureCardsStub,
          LandingSocialProof: LandingSocialProofStub
        }
      }
    })
  }

  it('renders the landing page with hero headline', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('landing.hero.headline')
  })

  it('renders the hero tagline', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('landing.hero.tagline')
  })

  it('renders LandingSearchBar component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-search-bar"]').exists()).toBe(true)
  })

  it('renders LandingFeatureCards component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-feature-cards"]').exists()).toBe(true)
  })

  it('renders LandingSocialProof component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-social-proof"]').exists()).toBe(true)
  })

  it('renders footer disclaimer text from i18n', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('landing.disclaimer')
  })

  it('calls useSeoMeta with reactive getter functions for locale-aware values', () => {
    mountPage()
    expect(mockSeoMeta).toHaveBeenCalledTimes(1)
    const arg = mockSeoMeta.mock.calls[0][0]
    // Reactive getters: title, description, ogTitle, ogDescription should be functions
    expect(typeof arg.title).toBe('function')
    expect(typeof arg.description).toBe('function')
    expect(typeof arg.ogTitle).toBe('function')
    expect(typeof arg.ogDescription).toBe('function')
    // When invoked, they should return i18n keys (from vitest.setup.ts t() stub)
    expect(arg.title()).toBe('landing.seo.title')
    expect(arg.description()).toBe('landing.seo.description')
    expect(arg.ogTitle()).toBe('landing.seo.title')
    expect(arg.ogDescription()).toBe('landing.seo.description')
  })

  it('calls useHead with JSON-LD Organization schema', () => {
    mountPage()
    expect(mockUseHead).toHaveBeenCalledWith(
      expect.objectContaining({
        script: expect.arrayContaining([
          expect.objectContaining({
            type: 'application/ld+json'
          })
        ])
      })
    )
  })

  it('JSON-LD contains Organization type', () => {
    mountPage()
    const headCall = mockUseHead.mock.calls[0][0]
    // innerHTML is now a computed ref — extract its value
    const innerHTML = typeof headCall.script[0].innerHTML === 'object' && headCall.script[0].innerHTML?.value
      ? headCall.script[0].innerHTML.value
      : headCall.script[0].innerHTML
    const jsonLd = JSON.parse(innerHTML)
    expect(jsonLd['@type']).toBe('Organization')
    expect(jsonLd.name).toBe('RiskGuard')
  })

  it('does NOT redirect from component — middleware handles auth redirect', async () => {
    // Auth redirect for "/" is now handled by auth.global middleware,
    // not by the component's onMounted. Verify no navigateTo call from component.
    mockIsAuthenticated.value = true
    mountPage()
    await nextTick()

    expect(mockNavigateTo).not.toHaveBeenCalled()
  })

  it('does NOT redirect unauthenticated users', async () => {
    mockIsAuthenticated.value = false
    mockInitializeAuth.mockResolvedValue(undefined)
    mountPage()
    await nextTick()

    // navigateTo should only be called for auth redirect, not for unauthenticated users
    // The health check may trigger $fetch but navigateTo should not be called
    const navigateCalls = mockNavigateTo.mock.calls.filter(
      (call: string[]) => call[0] === '/dashboard'
    )
    expect(navigateCalls).toHaveLength(0)
  })

  it('passes serviceUnavailable=true to search bar when health check fails', async () => {
    mockFetch.mockRejectedValue(new Error('Connection refused'))
    const wrapper = mountPage()
    // Combined onMounted: await initializeAuth() → await $fetch() → catch sets ref.
    // Need enough microtask ticks for the full async chain to resolve.
    await nextTick()
    await nextTick()
    await nextTick()

    const searchBar = wrapper.findComponent(LandingSearchBarStub)
    expect(searchBar.props('serviceUnavailable')).toBe(true)
  })

  it('passes serviceUnavailable=false to search bar when health check succeeds', async () => {
    mockFetch.mockResolvedValue({ status: 'UP' })
    const wrapper = mountPage()

    // serviceUnavailable defaults to false
    const searchBar = wrapper.findComponent(LandingSearchBarStub)
    expect(searchBar.props('serviceUnavailable')).toBe(false)
  })

  it('uses public layout via definePageMeta', () => {
    mountPage()
    expect(mockDefinePageMeta).toHaveBeenCalledWith({ layout: 'public' })
  })
})
