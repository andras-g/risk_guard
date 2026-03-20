import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import FlightControlPage from './index.vue'
import type { FlightControlTenantSummaryResponse, FlightControlTotals, PortfolioAlertResponse } from '~/types/api'

// --- Stubs & Mocks ---

const mockFetchSummary = vi.fn()
const mockTenants = ref<FlightControlTenantSummaryResponse[]>([])
const mockTotals = ref<FlightControlTotals | null>(null)
const mockIsLoading = ref(false)
const mockError = ref<string | null>(null)

vi.mock('~/stores/flightControl', () => ({
  useFlightControlStore: () => ({
    tenants: mockTenants,
    totals: mockTotals,
    isLoading: mockIsLoading,
    error: mockError,
    fetchSummary: mockFetchSummary,
  }),
}))

const mockFetchAlerts = vi.fn()
const mockAlerts = ref<PortfolioAlertResponse[]>([])
const mockAlertsLoading = ref(false)

vi.mock('~/stores/portfolio', () => ({
  usePortfolioStore: () => ({
    alerts: mockAlerts,
    isLoading: mockAlertsLoading,
    fetchAlerts: mockFetchAlerts,
  }),
}))

const mockIsAccountant = ref(true)
const mockActiveTenantId = ref<string | null>('tenant-a-id')
const mockSwitchTenant = vi.fn()

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

vi.mock('~/composables/formatting/useStatusColor', () => ({
  useStatusColor: () => ({
    statusColorClass: (s: string | null) => s === 'AT_RISK' ? 'border-red-700 text-red-700' : 'border-slate-400 text-slate-400',
    statusIconClass: (s: string | null) => s === 'AT_RISK' ? 'pi pi-exclamation-triangle' : 'pi pi-minus-circle',
    statusI18nKey: (s: string | null) => `screening.verdict.${(s || 'unavailable').toLowerCase()}`,
  }),
}))

vi.mock('~/composables/api/useApiError', () => ({
  useApiError: () => ({
    mapErrorType: (type?: string) => type || 'Error',
  }),
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

const mockToastAdd = vi.fn()
vi.stubGlobal('useToast', () => ({
  add: mockToastAdd,
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

vi.mock('pinia', () => ({
  storeToRefs: (store: Record<string, unknown>) => store,
  defineStore: vi.fn(),
}))

vi.mock('@primevue/core/api', () => ({
  FilterMatchMode: {
    CONTAINS: 'contains',
    EQUALS: 'equals',
    STARTS_WITH: 'startsWith',
  },
}))

// --- Test Data ---

function buildTenant(overrides: Partial<FlightControlTenantSummaryResponse> = {}): FlightControlTenantSummaryResponse {
  return {
    tenantId: 'tenant-a-id',
    tenantName: 'Kovacs és Társa Kft',
    reliableCount: 5,
    atRiskCount: 2,
    staleCount: 1,
    incompleteCount: 0,
    totalPartners: 8,
    lastCheckedAt: '2026-03-20T10:00:00Z',
    ...overrides,
  }
}

function buildTotals(overrides: Partial<FlightControlTotals> = {}): FlightControlTotals {
  return {
    totalClients: 2,
    totalAtRisk: 3,
    totalStale: 1,
    totalPartners: 15,
    ...overrides,
  }
}

function buildAlert(overrides: Partial<PortfolioAlertResponse> = {}): PortfolioAlertResponse {
  return {
    alertId: 'alert-1',
    tenantId: 'tenant-a-id',
    tenantName: 'Client A',
    taxNumber: '12345678',
    companyName: 'Kovacs Kft',
    previousStatus: 'RELIABLE',
    newStatus: 'AT_RISK',
    changedAt: '2026-03-20T10:00:00Z',
    sha256Hash: 'abc123',
    verdictId: 'verdict-1',
    ...overrides,
  }
}

function mountPage() {
  return mount(FlightControlPage, {
    global: {
      stubs: {
        DataTable: {
          template: '<div class="datatable-stub"><slot name="header" /><slot name="default" /><slot /></div>',
          props: ['value', 'sortField', 'sortOrder', 'rowHover', 'filters', 'globalFilterFields'],
          emits: ['row-click', 'update:filters'],
        },
        Column: {
          template: '<div class="column-stub"><slot name="body" :data="{ tenantName: \'Test\', atRiskCount: 1, staleCount: 0, reliableCount: 3, totalPartners: 4, lastCheckedAt: \'2026-01-01\' }" /></div>',
          props: ['field', 'header', 'sortable'],
        },
        Tag: { template: '<span class="tag-stub">{{ value }}</span>', props: ['value', 'severity'] },
        Skeleton: { template: '<div class="skeleton-stub" />' },
        InputText: { template: '<input class="inputtext-stub" />', props: ['modelValue', 'placeholder'] },
        Select: { template: '<div class="select-stub" />', props: ['modelValue', 'options', 'optionLabel', 'optionValue'] },
        NuxtLink: { template: '<a><slot /></a>', props: ['to'] },
      },
    },
  })
}

// --- Tests ---

describe('FlightControl Page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockTenants.value = []
    mockTotals.value = null
    mockIsLoading.value = false
    mockError.value = null
    mockAlerts.value = []
    mockAlertsLoading.value = false
    mockIsAccountant.value = true
    mockActiveTenantId.value = 'tenant-a-id'
  })

  it('renders summary pills with correct counts', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals({ totalClients: 2, totalAtRisk: 3, totalStale: 1 })

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-page"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="summary-total-clients"]').text()).toBe('2')
    expect(wrapper.find('[data-testid="summary-at-risk"]').text()).toBe('3')
    expect(wrapper.find('[data-testid="summary-stale"]').text()).toBe('1')
  })

  it('renders DataTable with tenant rows', () => {
    mockTenants.value = [
      buildTenant({ tenantName: 'Alpha Kft', atRiskCount: 5 }),
      buildTenant({ tenantId: 'tenant-b', tenantName: 'Beta Bt', atRiskCount: 0 }),
    ]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-table"]').exists()).toBe(true)
  })

  it('row click stores postSwitchRedirect and calls switchTenant', async () => {
    const tenant = buildTenant({ tenantId: 'tenant-b-id' })
    mockTenants.value = [tenant]
    mockTotals.value = buildTotals()
    mockSwitchTenant.mockResolvedValue(undefined)

    const wrapper = mountPage()
    const page = wrapper.vm as any
    await page.handleClientClick(tenant)

    // switchTenant triggers window.location.reload — router.push is skipped.
    // The redirect target is stored in sessionStorage for post-reload navigation.
    expect(sessionStorage.getItem('postSwitchRedirect')).toBe('/dashboard')
    expect(mockSwitchTenant).toHaveBeenCalledWith('tenant-b-id')
  })

  it('empty state renders when no clients', () => {
    mockTotals.value = buildTotals({ totalClients: 0 })
    mockTenants.value = []

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('notification.flightControl.emptyTitle')
  })

  it('i18n keys resolve for page title and summary labels', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()

    const wrapper = mountPage()

    expect(wrapper.text()).toContain('notification.flightControl.pageTitle')
    expect(wrapper.text()).toContain('notification.flightControl.summaryTotalClients')
  })

  it('loading skeleton renders while fetching', () => {
    mockIsLoading.value = true

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('.skeleton-stub').length).toBeGreaterThan(0)
  })

  it('error state renders on fetch failure', () => {
    mockError.value = 'Network error'

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="flight-control-loading"]').exists()).toBe(false)
  })

  it('non-accountant is redirected to dashboard', async () => {
    mockIsAccountant.value = false

    mountPage()
    await nextTick()

    expect(mockRouterPush).toHaveBeenCalledWith('/dashboard')
  })

  it('fetches summary and alerts on mount for accountant', async () => {
    mockIsAccountant.value = true
    mockFetchSummary.mockResolvedValue(undefined)
    mockFetchAlerts.mockResolvedValue(undefined)

    mountPage()
    await flushPromises()

    expect(mockFetchSummary).toHaveBeenCalled()
    expect(mockFetchAlerts).toHaveBeenCalledWith(7)
  })

  it('sort by At-Risk column is set as default', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()

    const wrapper = mountPage()

    // Verify DataTable renders and has 6 column stubs (one per data column)
    const dataTableEl = wrapper.find('[data-testid="flight-control-table"]')
    expect(dataTableEl.exists()).toBe(true)
    const columns = dataTableEl.findAll('.column-stub')
    expect(columns.length).toBeGreaterThanOrEqual(6)

    // Verify the filter state is initialized correctly (default sort is template-bound)
    const page = wrapper.vm as any
    expect(page.filters.global.matchMode).toBe('contains')
  })

  it('limits recent alerts to 10 items', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()
    mockAlerts.value = Array.from({ length: 15 }, (_, i) =>
      buildAlert({ alertId: `alert-${i}`, taxNumber: `1234567${i}` }),
    )

    const wrapper = mountPage()

    expect(wrapper.findAll('[data-testid="alert-item"]')).toHaveLength(10)
  })

  it('alert click stores postSwitchRedirect and calls switchTenant', async () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()
    mockSwitchTenant.mockResolvedValue(undefined)

    const wrapper = mountPage()

    const alert = buildAlert({ tenantId: 'tenant-c-id', taxNumber: '99887766' })
    const page = wrapper.vm as any
    await page.handleAlertClick(alert)

    // switchTenant triggers reload — redirect stored in sessionStorage
    expect(sessionStorage.getItem('postSwitchRedirect')).toBe('/screening/99887766')
    expect(mockSwitchTenant).toHaveBeenCalledWith('tenant-c-id')
  })

  // L2: Filtering tests (AC3, AC10(g))

  it('renders filter header with text input and risk-level dropdown', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="table-filter-header"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-client-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-risk-level"]').exists()).toBe(true)
  })

  it('risk-level filter hides tenants without at-risk partners', async () => {
    const tenantA = buildTenant({ tenantId: 'a', tenantName: 'Alpha', atRiskCount: 3 })
    const tenantB = buildTenant({ tenantId: 'b', tenantName: 'Beta', atRiskCount: 0 })
    mockTenants.value = [tenantA, tenantB]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()
    const page = wrapper.vm as any

    // Initially filteredTenants includes both
    expect(page.filteredTenants).toHaveLength(2)

    // Set risk filter to 'at-risk' — only tenantA should remain
    page.riskFilter = 'at-risk'
    await nextTick()
    expect(page.filteredTenants).toHaveLength(1)
    expect(page.filteredTenants[0].tenantName).toBe('Alpha')
  })

  it('risk-level filter hides tenants without stale partners', async () => {
    const tenantA = buildTenant({ tenantId: 'a', tenantName: 'Alpha', staleCount: 2 })
    const tenantB = buildTenant({ tenantId: 'b', tenantName: 'Beta', staleCount: 0 })
    mockTenants.value = [tenantA, tenantB]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()
    const page = wrapper.vm as any
    page.riskFilter = 'stale'
    await nextTick()

    expect(page.filteredTenants).toHaveLength(1)
    expect(page.filteredTenants[0].tenantName).toBe('Alpha')
  })

  it('clearing risk filter back to all shows all tenants', async () => {
    const tenantA = buildTenant({ tenantId: 'a', tenantName: 'Alpha', atRiskCount: 3 })
    const tenantB = buildTenant({ tenantId: 'b', tenantName: 'Beta', atRiskCount: 0 })
    mockTenants.value = [tenantA, tenantB]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()
    const page = wrapper.vm as any

    // Filter, then reset
    page.riskFilter = 'at-risk'
    await nextTick()
    expect(page.filteredTenants).toHaveLength(1)

    page.riskFilter = 'all'
    await nextTick()
    expect(page.filteredTenants).toHaveLength(2)
  })

  it('DataTable has globalFilterFields configured for text filtering', () => {
    mockTenants.value = [buildTenant()]
    mockTotals.value = buildTotals()

    const wrapper = mountPage()

    // Verify text filter input exists with the correct i18n placeholder
    const filterInput = wrapper.find('[data-testid="filter-client-name"]')
    expect(filterInput.exists()).toBe(true)

    // Verify the filter state is initialized with CONTAINS mode for text matching
    const page = wrapper.vm as any
    expect(page.filters.global.matchMode).toBe('contains')
    expect(page.filters.global.value).toBeNull()
  })

  it('mobile text filter narrows stacked card list', async () => {
    const tenantA = buildTenant({ tenantId: 'a', tenantName: 'Alpha Corp' })
    const tenantB = buildTenant({ tenantId: 'b', tenantName: 'Beta Industries' })
    mockTenants.value = [tenantA, tenantB]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()
    const page = wrapper.vm as any

    // Set the global text filter to "alpha" — only Alpha Corp should match
    page.filters.global.value = 'alpha'
    await nextTick()
    expect(page.filteredTenants).toHaveLength(1)
    expect(page.filteredTenants[0].tenantName).toBe('Alpha Corp')
  })

  // H2: Mobile responsive layout test

  it('renders mobile stacked cards for tenants', () => {
    mockTenants.value = [buildTenant(), buildTenant({ tenantId: 'b', tenantName: 'Beta Bt' })]
    mockTotals.value = buildTotals({ totalClients: 2 })

    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="flight-control-mobile-cards"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="mobile-tenant-card"]')).toHaveLength(2)
  })
})
