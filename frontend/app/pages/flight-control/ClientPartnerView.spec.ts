import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import ClientPartnerView from './[clientId].vue'
import type { WatchlistEntryResponse } from '~/types/api'

// --- Stubs & Mocks ---

const mockFetchClientPartners = vi.fn()
const mockPartners = ref<WatchlistEntryResponse[]>([])
const mockIsLoading = ref(false)
const mockError = ref<'forbidden' | 'unknown' | null>(null)

vi.mock('~/composables/api/useClientPartners', () => ({
  useClientPartners: () => ({
    partners: mockPartners,
    isLoading: mockIsLoading,
    error: mockError,
    fetchClientPartners: mockFetchClientPartners,
  }),
}))

const mockFetchSummary = vi.fn()
const mockTenants = ref([
  { tenantId: 'tenant-abc', tenantName: 'Kovacs és Társa Kft' },
])
vi.mock('~/stores/flightControl', () => ({
  useFlightControlStore: () => ({
    get tenants() { return mockTenants.value },
    fetchSummary: mockFetchSummary,
  }),
}))

vi.mock('~/composables/api/useApiError', () => ({
  useApiError: () => ({
    mapErrorType: (type: string | undefined) => type ?? 'generic-error',
  }),
}))

const mockIsAccountant = ref(true)
const mockSwitchTenant = vi.fn()
const mockActiveTenantId = ref<string | null>('tenant-xyz')

vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    isAccountant: mockIsAccountant,
    activeTenantId: mockActiveTenantId,
    switchTenant: mockSwitchTenant,
  }),
}))

vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string) => `relative(${date})`,
  }),
}))

vi.mock('pinia', () => ({
  storeToRefs: (store: Record<string, unknown>) => store,
  defineStore: vi.fn(),
}))

vi.mock('@primevue/core/api', () => ({
  FilterMatchMode: { CONTAINS: 'contains' },
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) return `${key}(${Object.values(params).join(',')})`
    return key
  },
}))

vi.stubGlobal('definePageMeta', vi.fn())

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({
  push: mockRouterPush,
}))

const mockClientId = ref('tenant-abc')
vi.stubGlobal('useRoute', () => ({
  params: { clientId: mockClientId.value },
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockToastAdd = vi.fn()
vi.stubGlobal('useToast', () => ({ add: mockToastAdd }))

// --- Test Data ---

function buildPartner(overrides: Partial<WatchlistEntryResponse> = {}): WatchlistEntryResponse {
  return {
    id: 'entry-1',
    taxNumber: '12345678',
    companyName: 'Alpha Kft',
    label: null,
    currentVerdictStatus: 'RELIABLE',
    lastCheckedAt: new Date('2026-03-20T10:00:00Z') as any,
    createdAt: new Date('2026-01-01T00:00:00Z') as any,
    latestSha256Hash: null,
    previousVerdictStatus: null,
    ...overrides,
  }
}

function mountPage() {
  return mount(ClientPartnerView, {
    global: {
      stubs: {
        NuxtLink: { template: '<a :href="to"><slot /></a>', props: ['to'] },
        InputText: { template: '<input />', props: ['modelValue', 'placeholder'] },
        Select: { template: '<div />', props: ['modelValue', 'options', 'optionLabel', 'optionValue'] },
        Tag: { template: '<span>{{ value }}</span>', props: ['value', 'severity'] },
        Dialog: {
          template: '<div v-if="visible"><slot /><slot name="footer" /></div>',
          props: ['visible', 'header', 'modal', 'style'],
          emits: ['update:visible'],
        },
        Button: { template: '<button @click="$emit(\'click\')">{{ label }}</button>', props: ['label', 'icon', 'class'], emits: ['click'] },
      },
    },
  })
}

// --- Tests ---

describe('ClientPartnerView Page ([clientId].vue)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockPartners.value = []
    mockIsLoading.value = false
    mockError.value = null
    mockIsAccountant.value = true
    mockTenants.value = [{ tenantId: 'tenant-abc', tenantName: 'Kovacs és Társa Kft' }]
    mockSwitchTenant.mockResolvedValue(undefined)
    mockFetchClientPartners.mockResolvedValue(undefined)
    mockFetchSummary.mockResolvedValue(undefined)
  })

  it('renders breadcrumb with client name from store', async () => {
    mockPartners.value = [buildPartner()]
    const wrapper = mountPage()
    await flushPromises()

    const breadcrumb = wrapper.find('[data-testid="breadcrumb-client-name"]')
    expect(breadcrumb.exists()).toBe(true)
    expect(breadcrumb.text()).toContain('Kovacs és Társa Kft')
  })

  it('shows read-only amber banner when partners loaded', async () => {
    mockPartners.value = [buildPartner()]
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="read-only-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="read-only-banner"]').text()).toContain('notification.flightControl.readOnlyBanner')
  })

  it('renders partner table with partner rows', async () => {
    mockPartners.value = [
      buildPartner({ taxNumber: '12345678', companyName: 'Alpha Kft', currentVerdictStatus: 'RELIABLE' }),
      buildPartner({ id: 'entry-2', taxNumber: '99887766', companyName: 'Beta Bt', currentVerdictStatus: 'AT_RISK' }),
    ]
    const wrapper = mountPage()
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="partner-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Alpha Kft')
    expect(rows[1].text()).toContain('Beta Bt')
  })

  it('renders stat bar with correct counts', async () => {
    mockPartners.value = [
      buildPartner({ currentVerdictStatus: 'RELIABLE' }),
      buildPartner({ id: 'e2', currentVerdictStatus: 'AT_RISK' }),
      buildPartner({ id: 'e3', currentVerdictStatus: 'UNAVAILABLE' }),
    ]
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="stat-reliable"]').text()).toBe('1')
    expect(wrapper.find('[data-testid="stat-at-risk"]').text()).toBe('1')
    expect(wrapper.find('[data-testid="stat-stale"]').text()).toBe('1')
  })

  it('shows forbidden error state when error is forbidden (AC 4)', async () => {
    mockError.value = 'forbidden'
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="forbidden-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="forbidden-error"]').text()).toContain('notification.flightControl.forbiddenError')
    // Partner table should not be visible
    expect(wrapper.find('[data-testid="partner-table"]').exists()).toBe(false)
  })

  it('redirects non-accountant to dashboard', async () => {
    mockIsAccountant.value = false
    mountPage()
    await nextTick()

    expect(mockRouterPush).toHaveBeenCalledWith('/dashboard')
  })

  it('calls fetchClientPartners with clientId on mount', async () => {
    mountPage()
    await flushPromises()

    expect(mockFetchClientPartners).toHaveBeenCalledWith('tenant-abc')
  })

  it('"Switch to Client →" triggers switchTenant with clientId and sets postSwitchRedirect', async () => {
    mockPartners.value = [buildPartner()]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    await page.handleSwitchToClient()

    expect(sessionStorage.getItem('postSwitchRedirect')).toBe('/dashboard')
    expect(mockSwitchTenant).toHaveBeenCalledWith('tenant-abc')
  })

  it('"View →" opens confirmation modal', async () => {
    mockPartners.value = [buildPartner({ taxNumber: '12345678' })]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    page.handleViewPartner(buildPartner({ taxNumber: '12345678' }))
    await nextTick()

    expect(wrapper.find('[data-testid="switch-confirm-modal"]').exists()).toBe(true)
  })

  it('confirming "View →" triggers switchTenant with postSwitchRedirect to screening', async () => {
    mockPartners.value = [buildPartner({ taxNumber: '12345678' })]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    page.handleViewPartner(buildPartner({ taxNumber: '12345678' }))
    await nextTick()
    await page.confirmViewPartner()

    expect(sessionStorage.getItem('postSwitchRedirect')).toBe('/screening/12345678')
    expect(mockSwitchTenant).toHaveBeenCalledWith('tenant-abc')
  })

  it('filters partners by name search', async () => {
    mockPartners.value = [
      buildPartner({ companyName: 'Alpha Kft', taxNumber: '11111111' }),
      buildPartner({ id: 'e2', companyName: 'Beta Bt', taxNumber: '22222222' }),
    ]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    page.nameSearch = 'alpha'
    await nextTick()

    expect(page.filteredPartners).toHaveLength(1)
    expect(page.filteredPartners[0].companyName).toBe('Alpha Kft')
  })

  it('filters partners by status (at-risk)', async () => {
    mockPartners.value = [
      buildPartner({ currentVerdictStatus: 'RELIABLE' }),
      buildPartner({ id: 'e2', currentVerdictStatus: 'AT_RISK' }),
    ]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    page.statusFilter = 'at-risk'
    await nextTick()

    expect(page.filteredPartners).toHaveLength(1)
    expect(page.filteredPartners[0].currentVerdictStatus).toBe('AT_RISK')
  })

  it('renders mobile cards for partners (AC 7)', async () => {
    mockPartners.value = [buildPartner(), buildPartner({ id: 'e2', taxNumber: '99887766' })]
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="partner-mobile-cards"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="partner-mobile-card"]')).toHaveLength(2)
  })

  it('calls fetchSummary on mount when tenants list is empty (direct navigation fix)', async () => {
    mockTenants.value = []
    mountPage()
    await flushPromises()

    expect(mockFetchSummary).toHaveBeenCalledOnce()
  })

  it('does not call fetchSummary on mount when tenants are already loaded', async () => {
    // mockTenants.value is populated in beforeEach
    mountPage()
    await flushPromises()

    expect(mockFetchSummary).not.toHaveBeenCalled()
  })

  it('"Switch to Client →" clears sessionStorage and shows toast on switchTenant failure', async () => {
    mockSwitchTenant.mockRejectedValue({ data: { type: 'urn:riskguard:error:tier-upgrade-required' } })
    mockPartners.value = [buildPartner()]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    await page.handleSwitchToClient()

    expect(sessionStorage.getItem('postSwitchRedirect')).toBeNull()
    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }))
  })

  it('"View →" confirm clears sessionStorage and shows toast on switchTenant failure', async () => {
    mockSwitchTenant.mockRejectedValue(new Error('network'))
    mockPartners.value = [buildPartner({ taxNumber: '12345678' })]
    const wrapper = mountPage()
    await flushPromises()

    const page = wrapper.vm as any
    page.handleViewPartner(buildPartner({ taxNumber: '12345678' }))
    await nextTick()
    await page.confirmViewPartner()

    expect(sessionStorage.getItem('postSwitchRedirect')).toBeNull()
    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }))
  })
})
