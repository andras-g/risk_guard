import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, defineComponent, h, Suspense } from 'vue'
import PublicCompanyPage from './[taxNumber].vue'
import type { PublicCompanyResponse } from '~/types/api'

/**
 * Component tests for the Public Company SEO page ([taxNumber].vue).
 * Tests: renders company name/taxNumber, renders JSON-LD script tag,
 * renders CTA button, renders not-found state on 404.
 *
 * Co-located per architecture rules (Story 3.11, Task 6.2).
 */

// --- Mock Skeleton component ---
const SkeletonStub = {
  template: '<div data-testid="stub-skeleton"></div>',
  props: ['width', 'height', 'class'],
}

// --- Test state ---
let mockCompanyData: PublicCompanyResponse | null = null
let mockErrorData: Error | null = null

// --- Mock useAsyncData — returns resolved Promise with refs ---
vi.stubGlobal('useAsyncData', () => {
  const data = ref(mockCompanyData)
  const error = ref(mockErrorData)
  return Promise.resolve({ data, error, pending: ref(false), refresh: vi.fn() })
})

// --- Mock route ---
vi.stubGlobal('useRoute', () => ({
  params: { taxNumber: '12345678' },
}))

// --- Mock useRuntimeConfig ---
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

// --- Mock useI18n ---
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

// --- Mock useHead ---
const mockUseHead = vi.fn()
vi.stubGlobal('useHead', mockUseHead)

// --- Mock definePageMeta ---
const mockDefinePageMeta = vi.fn()
vi.stubGlobal('definePageMeta', mockDefinePageMeta)

// --- Mock $fetch ---
vi.stubGlobal('$fetch', vi.fn())

// --- Mock NuxtLink ---
const NuxtLinkStub = {
  template: '<a :href="to" :data-testid="$attrs[\'data-testid\']"><slot /></a>',
  props: ['to'],
}

// Wrap async component in Suspense for proper rendering
function mountWithSuspense(companyData: PublicCompanyResponse | null, error: Error | null = null) {
  mockCompanyData = companyData
  mockErrorData = error

  const wrapper = defineComponent({
    setup() {
      return () => h(Suspense, null, {
        default: () => h(PublicCompanyPage),
        fallback: () => h('div', 'Loading...'),
      })
    },
  })

  return mount(wrapper, {
    global: {
      stubs: {
        Skeleton: SkeletonStub,
        NuxtLink: NuxtLinkStub,
      },
    },
  })
}

// Helper to wait for Suspense to resolve — uses flushPromises() from @vue/test-utils
// instead of fragile triple nextTick() (review finding [LOW])
async function flushSuspense() {
  await flushPromises()
}

describe('Public Company Page — company found', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCompanyData = null
    mockErrorData = null
  })

  it('renders company name when data is available', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: '1234 Budapest, Test utca 1.',
    })
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-name"]').text()).toContain('Test Company Kft.')
  })

  it('renders tax number', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-tax-number"]').text()).toContain('12345678')
  })

  it('renders address when available', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: '1234 Budapest, Test utca 1.',
    })
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-address"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="company-address"]').text()).toContain('1234 Budapest, Test utca 1.')
  })

  it('hides address when not available', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-address"]').exists()).toBe(false)
  })

  it('renders generic indicator (not a verdict)', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    const indicator = wrapper.find('[data-testid="generic-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.text()).toContain('screening.company.genericIndicator')
  })

  it('renders CTA button linking to login with redirect', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    const ctaButton = wrapper.find('[data-testid="cta-button"]')
    expect(ctaButton.exists()).toBe(true)
    expect(ctaButton.text()).toContain('screening.company.cta')
    expect(ctaButton.attributes('href')).toBe('/auth/login?redirect=/screening/12345678')
  })

  it('renders CTA section', async () => {
    const wrapper = mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    expect(wrapper.find('[data-testid="cta-section"]').exists()).toBe(true)
  })

  it('uses public layout via definePageMeta', async () => {
    mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    expect(mockDefinePageMeta).toHaveBeenCalledWith({ layout: 'public' })
  })

  it('calls useHead for JSON-LD and meta', async () => {
    mountWithSuspense({
      taxNumber: '12345678',
      companyName: 'Test Company Kft.',
      address: null,
    })
    await flushSuspense()

    // useHead is called at least once (for meta + for JSON-LD)
    expect(mockUseHead).toHaveBeenCalled()
  })
})

describe('Public Company Page — not found (404)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCompanyData = null
    mockErrorData = null
  })

  it('renders not-found state when error occurs', async () => {
    const wrapper = mountWithSuspense(null, new Error('404'))
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="public-company-page"]').exists()).toBe(false)
  })

  it('renders not-found message', async () => {
    const wrapper = mountWithSuspense(null, new Error('404'))
    await flushSuspense()

    expect(wrapper.text()).toContain('screening.company.notFound')
  })

  it('renders search-now link on not-found', async () => {
    const wrapper = mountWithSuspense(null, new Error('404'))
    await flushSuspense()

    const searchLink = wrapper.find('[data-testid="search-now-link"]')
    expect(searchLink.exists()).toBe(true)
    expect(searchLink.text()).toContain('screening.actions.searchNow')
  })
})

describe('Public Company Page — loading state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders loading skeletons when no data and no error', async () => {
    const wrapper = mountWithSuspense(null, null)
    await flushSuspense()

    expect(wrapper.find('[data-testid="company-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="stub-skeleton"]').length).toBeGreaterThan(0)
  })
})
