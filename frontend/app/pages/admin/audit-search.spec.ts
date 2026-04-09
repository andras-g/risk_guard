import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AuditSearchPage from './audit-search.vue'

// ── PrimeVue mocks ────────────────────────────────────────────────────────────
const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

vi.mock('primevue/button', () => ({
  default: {
    template: '<button :disabled="disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'label', 'icon'],
    emits: ['click'],
    inheritAttrs: true,
  },
}))

vi.mock('primevue/inputtext', () => ({
  default: {
    template: '<input :value="modelValue" :data-testid="$attrs[\'data-testid\']" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    inheritAttrs: true,
  },
}))

vi.mock('primevue/datatable', () => ({
  default: {
    template: '<div data-testid="data-table"><slot name="empty" /><slot /></div>',
    props: ['value', 'totalRecords', 'rows', 'first', 'lazy', 'paginator'],
    emits: ['page'],
  },
}))

vi.mock('primevue/column', () => ({
  default: {
    template: '<div />',
    props: ['field', 'header'],
  },
}))

vi.mock('primevue/skeleton', () => ({
  default: { template: '<div data-testid="skeleton" />', props: ['height'] },
}))

vi.mock('primevue/tag', () => ({
  default: {
    template: '<span>{{ value }}</span>',
    props: ['value', 'title'],
    inheritAttrs: true,
  },
}))

// ── Nuxt globals ──────────────────────────────────────────────────────────────
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockRouterReplace = vi.fn()
vi.stubGlobal('useRouter', () => ({ replace: mockRouterReplace }))

vi.stubGlobal('definePageMeta', vi.fn())
vi.stubGlobal('NuxtLink', { template: '<a><slot /></a>' })

// ── $fetch mock ───────────────────────────────────────────────────────────────
const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

// ── Auth store mock ───────────────────────────────────────────────────────────
let mockUserRole = 'PLATFORM_ADMIN'
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    get role() { return mockUserRole },
  }),
}))

const mountPage = () => mount(AuditSearchPage, {
  global: {
    stubs: {
      ClientOnly: { template: '<slot />' },
    },
  },
})

const samplePageResponse = {
  content: [{
    id: 'audit-1',
    tenantId: 'tenant-uuid',
    userId: 'user-uuid',
    taxNumber: '12345678',
    verdictStatus: 'RELIABLE',
    verdictConfidence: 'FRESH',
    searchedAt: '2026-03-31T10:00:00Z',
    sha256Hash: 'a'.repeat(64),
    dataSourceMode: 'DEMO',
    checkSource: 'MANUAL',
    sourceUrls: ['https://nav.gov.hu'],
    companyName: 'Test Co',
  }],
  totalElements: 1,
  page: 0,
  size: 20,
}

const emptyPageResponse = {
  content: [],
  totalElements: 0,
  page: 0,
  size: 20,
}

describe('audit-search.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUserRole = 'PLATFORM_ADMIN'
  })

  // ─── AC 6: search button disabled/enabled logic ────────────────────────────

  it('search button disabled when both fields empty', () => {
    const wrapper = mountPage()
    const btn = wrapper.find('[data-testid="search-btn"]')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('search button enabled when taxNumber filled', async () => {
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tax-number-input"]').setValue('12345678')
    expect(wrapper.find('[data-testid="search-btn"]').attributes('disabled')).toBeUndefined()
  })

  it('search button enabled when tenantId filled', async () => {
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tenant-id-input"]').setValue('some-uuid')
    expect(wrapper.find('[data-testid="search-btn"]').attributes('disabled')).toBeUndefined()
  })

  // ─── AC 2: search calls $fetch with taxNumber param ───────────────────────

  it('handleSearch calls $fetch with taxNumber param', async () => {
    mockFetch.mockResolvedValueOnce(emptyPageResponse)
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tax-number-input"]').setValue('12345678')
    await wrapper.find('[data-testid="search-btn"]').trigger('click')
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/screening/audit',
      expect.objectContaining({ params: expect.objectContaining({ taxNumber: '12345678' }) }),
    )
  })

  // ─── AC 3: search calls $fetch with tenantId param ────────────────────────

  it('handleSearch calls $fetch with tenantId param', async () => {
    mockFetch.mockResolvedValueOnce(emptyPageResponse)
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tenant-id-input"]').setValue('tenant-uuid')
    await wrapper.find('[data-testid="search-btn"]').trigger('click')
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/screening/audit',
      expect.objectContaining({ params: expect.objectContaining({ tenantId: 'tenant-uuid' }) }),
    )
  })

  // ─── AC 2: results DataTable renders content rows ─────────────────────────

  it('results DataTable renders content rows', async () => {
    mockFetch.mockResolvedValueOnce(samplePageResponse)
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tax-number-input"]').setValue('12345678')
    await wrapper.find('[data-testid="search-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="results-table"]').exists()).toBe(true)
  })

  // ─── AC 5: empty results shows noRecords message ──────────────────────────

  it('empty results shows noRecords message', async () => {
    mockFetch.mockResolvedValueOnce(emptyPageResponse)
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tax-number-input"]').setValue('12345678')
    await wrapper.find('[data-testid="search-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="results-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('admin.auditSearch.noRecords')
  })

  // ─── error from $fetch shows toast ───────────────────────────────────────

  it('error from $fetch shows toast', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'))
    const wrapper = mountPage()
    await wrapper.find('[data-testid="tax-number-input"]').setValue('12345678')
    await wrapper.find('[data-testid="search-btn"]').trigger('click')
    await flushPromises()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error' }),
    )
  })

  // ─── AC 1: non-platform-admin redirected ─────────────────────────────────

  it('redirects non-PLATFORM_ADMIN user to /dashboard', async () => {
    mockUserRole = 'ACCOUNTANT'
    mountPage()
    await flushPromises()
    expect(mockRouterReplace).toHaveBeenCalledWith('/dashboard')
  })
})
