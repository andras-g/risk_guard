import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import PortfolioPulse from './PortfolioPulse.vue'
import type { PortfolioAlertResponse } from '~/types/api'

// --- Stubs & Mocks ---

const mockFetchAlerts = vi.fn()
const mockAlerts = ref<PortfolioAlertResponse[]>([])
const mockIsLoading = ref(false)
const mockError = ref<string | null>(null)

vi.mock('~/stores/portfolio', () => ({
  usePortfolioStore: () => ({
    alerts: mockAlerts,
    isLoading: mockIsLoading,
    error: mockError,
    fetchAlerts: mockFetchAlerts,
  }),
}))

let mockUserData: { role: string; activeTenantId: string } | null = {
  role: 'ACCOUNTANT',
  activeTenantId: 'tenant-a-id',
}
const mockSwitchTenant = vi.fn()

// The real Pinia store unwraps refs, so useIdentityStore().user returns the plain object.
// We replicate that by returning a reactive wrapper that makes .user accessible directly.
vi.mock('~/stores/identity', () => ({
  useIdentityStore: () => ({
    get user() { return mockUserData },
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
  t: (key: string) => key,
}))

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({
  push: mockRouterPush,
}))

const mockToastAdd = vi.fn()
vi.stubGlobal('useToast', () => ({
  add: mockToastAdd,
}))

// Pinia storeToRefs replacement (returns the raw refs)
vi.mock('pinia', () => ({
  storeToRefs: (store: Record<string, unknown>) => store,
}))

// --- Test Data ---

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

function mountComponent() {
  return mount(PortfolioPulse, {
    global: {
      stubs: {
        Skeleton: { template: '<div class="skeleton-stub" />' },
      },
    },
  })
}

// --- Tests ---

describe('PortfolioPulse', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAlerts.value = []
    mockIsLoading.value = false
    mockError.value = null
    mockUserData = { role: 'ACCOUNTANT', activeTenantId: 'tenant-a-id' }
  })

  it('renders alert list for ACCOUNTANT', () => {
    mockAlerts.value = [
      buildAlert(),
      buildAlert({ alertId: 'alert-2', taxNumber: '99887766', companyName: 'Nagy Bt', newStatus: 'RELIABLE' }),
    ]

    const wrapper = mountComponent()

    expect(wrapper.find('[data-testid="portfolio-pulse"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="portfolio-pulse-item"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('Kovacs Kft')
    expect(wrapper.text()).toContain('Nagy Bt')
  })

  it('renders empty state when no alerts', () => {
    mockAlerts.value = []

    const wrapper = mountComponent()

    expect(wrapper.find('[data-testid="portfolio-pulse-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('notification.portfolio.emptyTitle')
    expect(wrapper.text()).toContain('notification.portfolio.emptyBody')
  })

  it('click triggers context switch and navigation for different tenant', async () => {
    const alert = buildAlert({ tenantId: 'tenant-b-id' })
    mockAlerts.value = [alert]
    mockSwitchTenant.mockResolvedValue(undefined)

    const wrapper = mountComponent()
    await wrapper.find('[data-testid="portfolio-pulse-item"]').trigger('click')

    expect(mockSwitchTenant).toHaveBeenCalledWith('tenant-b-id')
    expect(mockRouterPush).toHaveBeenCalledWith('/screening/12345678')
  })

  it('click navigates without tenant switch for same tenant', async () => {
    const alert = buildAlert({ tenantId: 'tenant-a-id' })
    mockAlerts.value = [alert]

    const wrapper = mountComponent()
    await wrapper.find('[data-testid="portfolio-pulse-item"]').trigger('click')

    expect(mockSwitchTenant).not.toHaveBeenCalled()
    expect(mockRouterPush).toHaveBeenCalledWith('/screening/12345678')
  })

  it('i18n keys resolve for title', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('notification.portfolio.title')
  })

  it('fetches alerts on mount', () => {
    mountComponent()
    expect(mockFetchAlerts).toHaveBeenCalled()
  })

  it('shows loading skeleton while fetching', () => {
    mockIsLoading.value = true

    const wrapper = mountComponent()

    expect(wrapper.findAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('limits displayed alerts to 20', () => {
    mockAlerts.value = Array.from({ length: 25 }, (_, i) =>
      buildAlert({ alertId: `alert-${i}`, taxNumber: `1234567${i}` }),
    )

    const wrapper = mountComponent()

    expect(wrapper.findAll('[data-testid="portfolio-pulse-item"]')).toHaveLength(20)
  })

  it('shows error state when fetchAlerts fails', () => {
    mockError.value = 'Network error'
    mockAlerts.value = []

    const wrapper = mountComponent()

    expect(wrapper.find('[data-testid="portfolio-pulse-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="portfolio-pulse-empty"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="portfolio-pulse-item"]')).toHaveLength(0)
  })

  it('retry button calls fetchAlerts again', async () => {
    mockError.value = 'Network error'
    mockAlerts.value = []

    const wrapper = mountComponent()
    await wrapper.find('[data-testid="portfolio-pulse-error"] button').trigger('click')

    expect(mockFetchAlerts).toHaveBeenCalledTimes(2) // once on mount, once on retry
  })
})
