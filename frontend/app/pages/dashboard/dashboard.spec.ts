import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DashboardPage from './index.vue'

// ── PrimeVue component stubs ──────────────────────────────────────────────────
vi.mock('primevue/skeleton', () => ({
  default: { template: '<div data-testid="skeleton" />', props: ['height', 'width'] },
}))

vi.mock('primevue/tag', () => ({
  default: {
    template: '<span :data-testid="\'tag-\' + severity">{{ value }}</span>',
    props: ['value', 'severity'],
    inheritAttrs: true,
  },
}))

// ── Nuxt globals ──────────────────────────────────────────────────────────────
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
  locale: { value: 'en' },
}))

vi.stubGlobal('useDateRelative', () => ({
  formatRelative: () => '2 days ago',
}))

vi.stubGlobal('useStatusColor', () => ({
  statusColorClass: (status: string | null) => status === 'AT_RISK' ? 'border-red-700 text-red-700' : 'border-slate-400 text-slate-400',
  statusIconClass: () => 'pi pi-minus-circle',
  statusI18nKey: (status: string | null) => {
    if (status === 'AT_RISK') return 'screening.verdict.atRisk'
    if (status === 'RELIABLE') return 'screening.verdict.reliable'
    if (status === 'TAX_SUSPENDED') return 'screening.verdict.taxSuspended'
    if (status === 'INCOMPLETE') return 'screening.verdict.incomplete'
    return 'screening.verdict.unavailable'
  },
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({ push: mockRouterPush }))

vi.stubGlobal('definePageMeta', vi.fn())
vi.stubGlobal('NuxtLink', {
  template: '<a :href="to" :data-testid="$attrs[\'data-testid\']"><slot /></a>',
  props: ['to'],
})

// ── Store mock state (mutable per test) ───────────────────────────────────────
let mockWatchlistEntries: unknown[] = []
let mockWatchlistLoading = false
let mockAlerts: unknown[] = []
let mockAlertsLoading = false
let mockCurrentVerdict: unknown = null
let mockIsSearching = false
let mockSearchError: string | null = null
let mockUserRole = 'SME_OWNER'

const mockFetchEntries = vi.fn()
const mockFetchAlerts = vi.fn()
const mockClearSearch = vi.fn()

const mockAddEntry = vi.fn()

vi.mock('~/stores/watchlist', () => ({
  useWatchlistStore: () => ({
    get entries() { return mockWatchlistEntries },
    get isLoading() { return mockWatchlistLoading },
    fetchEntries: mockFetchEntries,
    addEntry: mockAddEntry,
  }),
}))

vi.mock('~/stores/portfolio', () => ({
  usePortfolioStore: () => ({
    get alerts() { return mockAlerts },
    get isLoading() { return mockAlertsLoading },
    fetchAlerts: mockFetchAlerts,
  }),
}))

vi.mock('~/stores/screening', () => ({
  useScreeningStore: () => ({
    get currentVerdict() { return mockCurrentVerdict },
    get isSearching() { return mockIsSearching },
    get searchError() { return mockSearchError },
    clearSearch: mockClearSearch,
  }),
}))

vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    get isAccountant() { return mockUserRole === 'ACCOUNTANT' },
  }),
}))

// ── Child component stubs ─────────────────────────────────────────────────────
const DashboardStatBarStub = {
  name: 'DashboardStatBar',
  template: '<div data-testid="stat-bar-stub" :data-loading="isLoading" :data-count="entries.length" />',
  props: ['entries', 'isLoading'],
}

const DashboardNeedsAttentionStub = {
  name: 'DashboardNeedsAttention',
  template: '<div data-testid="needs-attention-stub" :data-loading="isLoading" :data-count="entries.length" />',
  props: ['entries', 'isLoading'],
}

const DashboardAlertFeedStub = {
  name: 'DashboardAlertFeed',
  template: '<div data-testid="alert-feed-stub" :data-loading="isLoading" :data-count="alerts.length" />',
  props: ['alerts', 'isLoading'],
}

const ScreeningSearchBarStub = {
  name: 'ScreeningSearchBar',
  template: '<div data-testid="search-bar-stub" />',
  expose: ['focus'],
  setup() {
    return { focus: vi.fn() }
  },
}

const WatchlistOnboardingHeroStub = {
  name: 'WatchlistOnboardingHero',
  template: '<div data-testid="onboarding-hero-stub" />',
  emits: ['focus-search'],
}

const ScreeningSkeletonVerdictCardStub = {
  name: 'ScreeningSkeletonVerdictCard',
  template: '<div data-testid="skeleton-verdict-stub" />',
  props: ['visible'],
}

function mountPage() {
  return mount(DashboardPage, {
    global: {
      stubs: {
        DashboardStatBar: DashboardStatBarStub,
        DashboardNeedsAttention: DashboardNeedsAttentionStub,
        DashboardAlertFeed: DashboardAlertFeedStub,
        ScreeningSearchBar: ScreeningSearchBarStub,
        ScreeningSkeletonVerdictCard: ScreeningSkeletonVerdictCardStub,
        WatchlistOnboardingHero: WatchlistOnboardingHeroStub,
        ClientOnly: { template: '<slot />' },
      },
    },
  })
}

// ── Sample data ───────────────────────────────────────────────────────────────
const sampleEntries = [
  { id: '1', taxNumber: '11111111', companyName: 'Alpha', currentVerdictStatus: 'RELIABLE', lastCheckedAt: '2026-04-01T10:00:00Z' },
  { id: '2', taxNumber: '22222222', companyName: 'Beta', currentVerdictStatus: 'AT_RISK', lastCheckedAt: '2026-04-01T09:00:00Z' },
  { id: '3', taxNumber: '33333333', companyName: 'Gamma', currentVerdictStatus: 'UNAVAILABLE', lastCheckedAt: '2026-03-25T10:00:00Z' },
]

const sampleAlerts = [
  { alertId: 'a1', taxNumber: '22222222', companyName: 'Beta', previousStatus: 'RELIABLE', newStatus: 'AT_RISK', changedAt: '2026-04-01T08:00:00Z', tenantId: 't1', tenantName: 'T1', sha256Hash: null, verdictId: null },
]

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('dashboard/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockWatchlistEntries = []
    mockWatchlistLoading = false
    mockAlerts = []
    mockAlertsLoading = false
    mockCurrentVerdict = null
    mockIsSearching = false
    mockSearchError = null
    mockUserRole = 'SME_OWNER'
    mockFetchEntries.mockResolvedValue(undefined)
    mockFetchAlerts.mockResolvedValue(undefined)
  })

  // ── AC 1/2/3: store fetches called on mount ────────────────────────────────

  it('calls clearSearch, fetchEntries, and fetchAlerts on mount for SME_OWNER', async () => {
    mountPage()
    await flushPromises()
    expect(mockClearSearch).toHaveBeenCalledOnce()
    expect(mockFetchEntries).toHaveBeenCalledOnce()
    expect(mockFetchAlerts).toHaveBeenCalledWith(7)
  })

  // ── AC 1: stat bar receives watchlist data ─────────────────────────────────

  it('passes watchlist entries to stat bar', async () => {
    mockWatchlistEntries = sampleEntries
    const wrapper = mountPage()
    await flushPromises()
    const statBar = wrapper.find('[data-testid="stat-bar-stub"]')
    expect(statBar.attributes('data-count')).toBe('3')
  })

  // ── AC 2: needs attention list ordering ────────────────────────────────────

  it('passes watchlist entries to needs attention component', async () => {
    mockWatchlistEntries = sampleEntries
    const wrapper = mountPage()
    await flushPromises()
    const attention = wrapper.find('[data-testid="needs-attention-stub"]')
    expect(attention.attributes('data-count')).toBe('3')
  })

  // ── AC 3: alert feed receives portfolio data ───────────────────────────────

  it('passes alerts to alert feed', async () => {
    mockAlerts = sampleAlerts
    mockWatchlistEntries = sampleEntries // entries needed to show live dashboard branch
    const wrapper = mountPage()
    await flushPromises()
    const feed = wrapper.find('[data-testid="alert-feed-stub"]')
    expect(feed.attributes('data-count')).toBe('1')
  })

  // ── AC 4: search bar always present ───────────────────────────────────────

  it('renders search bar regardless of loading state', () => {
    mockWatchlistLoading = true
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="search-bar-stub"]').exists()).toBe(true)
  })

  // ── AC 5: loading state propagated to child components ────────────────────

  it('passes watchlistLoading=true to stat bar, needs attention, and alert feed when loading', () => {
    mockWatchlistLoading = true
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stat-bar-stub"]').attributes('data-loading')).toBe('true')
    expect(wrapper.find('[data-testid="needs-attention-stub"]').attributes('data-loading')).toBe('true')
    expect(wrapper.find('[data-testid="alert-feed-stub"]').attributes('data-loading')).toBe('true')
  })

  it('passes alertsLoading=true to alert feed when loading', () => {
    mockAlertsLoading = true
    mockWatchlistEntries = sampleEntries // entries needed to show live dashboard branch
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="alert-feed-stub"]').attributes('data-loading')).toBe('true')
  })

  // ── AC 1: onboarding hero vs live dashboard ───────────────────────────────

  it('shows onboarding hero when entries empty and not loading', () => {
    mockWatchlistEntries = []
    mockWatchlistLoading = false
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="onboarding-hero-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="stat-bar-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="needs-attention-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="alert-feed-stub"]').exists()).toBe(false)
  })

  it('hides onboarding hero and shows live dashboard when entries exist', async () => {
    mockWatchlistEntries = sampleEntries
    mockWatchlistLoading = false
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('[data-testid="onboarding-hero-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="stat-bar-stub"]').exists()).toBe(true)
  })

  it('hides onboarding hero while loading (shows skeleton placeholders)', () => {
    mockWatchlistEntries = []
    mockWatchlistLoading = true
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="onboarding-hero-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="stat-bar-stub"]').exists()).toBe(true)
  })

  // ── Accountant redirect ────────────────────────────────────────────────────

  it('redirects ACCOUNTANT to /flight-control on mount', async () => {
    mockUserRole = 'ACCOUNTANT'
    mountPage()
    await flushPromises()
    expect(mockRouterPush).toHaveBeenCalledWith('/flight-control')
  })

  it('does NOT redirect SME_OWNER to /flight-control', async () => {
    mockUserRole = 'SME_OWNER'
    mountPage()
    await flushPromises()
    expect(mockRouterPush).not.toHaveBeenCalledWith('/flight-control')
  })

  it('does NOT call fetchEntries or fetchAlerts for accountant', async () => {
    mockUserRole = 'ACCOUNTANT'
    mountPage()
    await flushPromises()
    expect(mockFetchEntries).not.toHaveBeenCalled()
    expect(mockFetchAlerts).not.toHaveBeenCalled()
  })
})

// ── WatchlistOnboardingHero unit tests ───────────────────────────────────────

import WatchlistOnboardingHero from '~/components/dashboard/WatchlistOnboardingHero.vue'

describe('WatchlistOnboardingHero', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAddEntry.mockResolvedValue({ duplicate: false })
  })

  const mountHero = () =>
    mount(WatchlistOnboardingHero, {
      global: {
        stubs: {
          DashboardStatBar: DashboardStatBarStub,
          WatchlistAddDialog: {
            name: 'WatchlistAddDialog',
            template: '<div data-testid="add-dialog-stub" :data-visible="visible" />',
            props: ['visible'],
            emits: ['update:visible', 'submit'],
          },
          Button: {
            template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
            inheritAttrs: false,
          },
        },
      },
    })

  it('renders muted stat bar with empty entries', () => {
    const wrapper = mountHero()
    expect(wrapper.find('[data-testid="stat-bar-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="stat-bar-stub"]').attributes('data-count')).toBe('0')
    expect(wrapper.find('[data-testid="stat-bar-stub"]').attributes('data-loading')).toBe('false')
  })

  it('renders onboarding title and subtitle', () => {
    const wrapper = mountHero()
    expect(wrapper.find('[data-testid="onboarding-title"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="onboarding-subtitle"]').exists()).toBe(true)
  })

  it('"Add Your First Partner" button opens WatchlistAddDialog', async () => {
    const wrapper = mountHero()
    const addBtn = wrapper.find('[data-testid="add-first-partner-btn"]')
    expect(addBtn.exists()).toBe(true)
    await addBtn.trigger('click')
    expect(wrapper.find('[data-testid="add-dialog-stub"]').attributes('data-visible')).toBe('true')
  })

  it('"Search by Tax Number" button emits focus-search', async () => {
    const wrapper = mountHero()
    const searchBtn = wrapper.find('[data-testid="search-by-tax-btn"]')
    expect(searchBtn.exists()).toBe(true)
    await searchBtn.trigger('click')
    expect(wrapper.emitted('focus-search')).toBeTruthy()
  })

  it('renders 3 how-it-works steps', () => {
    const wrapper = mountHero()
    const steps = wrapper.findAll('[data-testid="how-it-works-step"]')
    expect(steps).toHaveLength(3)
  })

  it('each step shows icon, title, and body i18n key', () => {
    const wrapper = mountHero()
    const steps = wrapper.findAll('[data-testid="how-it-works-step"]')

    expect(steps[0]?.text()).toContain('dashboard.howItWorksStep1Title')
    expect(steps[0]?.text()).toContain('dashboard.howItWorksStep1Body')
    expect(steps[0]?.find('.pi-plus-circle').exists()).toBe(true)

    expect(steps[1]?.text()).toContain('dashboard.howItWorksStep2Title')
    expect(steps[1]?.text()).toContain('dashboard.howItWorksStep2Body')
    expect(steps[1]?.find('.pi-sync').exists()).toBe(true)

    expect(steps[2]?.text()).toContain('dashboard.howItWorksStep3Title')
    expect(steps[2]?.text()).toContain('dashboard.howItWorksStep3Body')
    expect(steps[2]?.find('.pi-bell').exists()).toBe(true)
  })

  it('calls addEntry with submitted data and closes dialog on successful submit (AC 3)', async () => {
    mockAddEntry.mockResolvedValue({ duplicate: false })
    const wrapper = mountHero()
    // open the dialog first
    await wrapper.find('[data-testid="add-first-partner-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="add-dialog-stub"]').attributes('data-visible')).toBe('true')
    // simulate dialog emitting submit
    await wrapper.findComponent({ name: 'WatchlistAddDialog' }).vm.$emit('submit', '12345678', 'Test Co', 'RELIABLE')
    await flushPromises()
    expect(mockAddEntry).toHaveBeenCalledWith('12345678', 'Test Co', 'RELIABLE')
    expect(wrapper.find('[data-testid="add-dialog-stub"]').attributes('data-visible')).toBe('false')
  })
})

// ── DashboardStatBar unit tests ───────────────────────────────────────────────

import DashboardStatBar from '~/components/dashboard/DashboardStatBar.vue'

describe('DashboardStatBar', () => {
  const mountStatBar = (entries: unknown[], isLoading = false) =>
    mount(DashboardStatBar, {
      props: { entries, isLoading },
      global: {
        stubs: {
          Skeleton: { template: '<div data-testid="skeleton" />', props: ['width', 'height'] },
        },
      },
    })

  it('shows 3 skeletons when loading', () => {
    const wrapper = mountStatBar([], true)
    expect(wrapper.findAll('[data-testid="skeleton"]')).toHaveLength(3)
  })

  it('computes reliable count correctly', () => {
    const entries = [
      { currentVerdictStatus: 'RELIABLE' },
      { currentVerdictStatus: 'RELIABLE' },
      { currentVerdictStatus: 'AT_RISK' },
    ]
    const wrapper = mountStatBar(entries)
    expect(wrapper.find('[data-testid="stat-reliable"]').text()).toContain('2')
  })

  it('computes atRisk count: AT_RISK + TAX_SUSPENDED + INCOMPLETE', () => {
    const entries = [
      { currentVerdictStatus: 'AT_RISK' },
      { currentVerdictStatus: 'TAX_SUSPENDED' },
      { currentVerdictStatus: 'INCOMPLETE' },
      { currentVerdictStatus: 'RELIABLE' },
    ]
    const wrapper = mountStatBar(entries)
    expect(wrapper.find('[data-testid="stat-at-risk"]').text()).toContain('3')
  })

  it('computes stale count: UNAVAILABLE only', () => {
    const entries = [
      { currentVerdictStatus: 'UNAVAILABLE' },
      { currentVerdictStatus: 'UNAVAILABLE' },
      { currentVerdictStatus: 'AT_RISK' },
    ]
    const wrapper = mountStatBar(entries)
    expect(wrapper.find('[data-testid="stat-stale"]').text()).toContain('2')
  })
})

// ── DashboardNeedsAttention unit tests ───────────────────────────────────────

import DashboardNeedsAttention from '~/components/dashboard/DashboardNeedsAttention.vue'

describe('DashboardNeedsAttention', () => {
  const mountAttention = (entries: unknown[], isLoading = false) =>
    mount(DashboardNeedsAttention, {
      props: { entries, isLoading },
      global: {
        stubs: {
          Skeleton: { template: '<div data-testid="skeleton" />', props: ['width', 'height'] },
          Tag: { template: '<span :data-severity="severity">{{ value }}</span>', props: ['value', 'severity'] },
          NuxtLink: { template: '<a :href="to"><slot /></a>', props: ['to'] },
        },
      },
    })

  it('shows 3 skeleton rows when loading', () => {
    const wrapper = mountAttention([], true)
    expect(wrapper.findAll('[data-testid="skeleton"]')).toHaveLength(3)
  })

  it('filters to at-risk statuses only', () => {
    const entries = [
      { id: '1', taxNumber: '11111111', companyName: 'A', currentVerdictStatus: 'RELIABLE', lastCheckedAt: null },
      { id: '2', taxNumber: '22222222', companyName: 'B', currentVerdictStatus: 'AT_RISK', lastCheckedAt: null },
      { id: '3', taxNumber: '33333333', companyName: 'C', currentVerdictStatus: 'UNAVAILABLE', lastCheckedAt: null },
    ]
    const wrapper = mountAttention(entries)
    expect(wrapper.findAll('[data-testid="attention-row"]')).toHaveLength(2)
  })

  it('sorts AT_RISK before INCOMPLETE before UNAVAILABLE', () => {
    const entries = [
      { id: '1', taxNumber: '11111111', companyName: 'Unavailable Co', currentVerdictStatus: 'UNAVAILABLE', lastCheckedAt: null },
      { id: '2', taxNumber: '22222222', companyName: 'Incomplete Co', currentVerdictStatus: 'INCOMPLETE', lastCheckedAt: null },
      { id: '3', taxNumber: '33333333', companyName: 'AtRisk Co', currentVerdictStatus: 'AT_RISK', lastCheckedAt: null },
    ]
    const wrapper = mountAttention(entries)
    const rows = wrapper.findAll('[data-testid="attention-row"]')
    expect(rows[0].text()).toContain('AtRisk Co')
    expect(rows[1].text()).toContain('Incomplete Co')
    expect(rows[2].text()).toContain('Unavailable Co')
  })

  it('caps at 10 entries', () => {
    const entries = Array.from({ length: 15 }, (_, i) => ({
      id: String(i),
      taxNumber: String(i).padStart(8, '0'),
      companyName: `Co ${i}`,
      currentVerdictStatus: 'AT_RISK',
      lastCheckedAt: null,
    }))
    const wrapper = mountAttention(entries)
    expect(wrapper.findAll('[data-testid="attention-row"]')).toHaveLength(10)
  })

  it('shows no-attention placeholder when no entries need attention', () => {
    const entries = [
      { id: '1', taxNumber: '11111111', companyName: 'A', currentVerdictStatus: 'RELIABLE', lastCheckedAt: null },
    ]
    const wrapper = mountAttention(entries)
    expect(wrapper.find('[data-testid="no-attention"]').exists()).toBe(true)
  })

  it('uses danger severity for AT_RISK', () => {
    const entries = [{ id: '1', taxNumber: '11111111', companyName: 'A', currentVerdictStatus: 'AT_RISK', lastCheckedAt: null }]
    const wrapper = mountAttention(entries)
    expect(wrapper.find('[data-severity="danger"]').exists()).toBe(true)
  })

  it('uses warn severity for INCOMPLETE', () => {
    const entries = [{ id: '1', taxNumber: '11111111', companyName: 'A', currentVerdictStatus: 'INCOMPLETE', lastCheckedAt: null }]
    const wrapper = mountAttention(entries)
    expect(wrapper.find('[data-severity="warn"]').exists()).toBe(true)
  })
})

// ── DashboardAlertFeed unit tests ────────────────────────────────────────────

import DashboardAlertFeed from '~/components/dashboard/DashboardAlertFeed.vue'

describe('DashboardAlertFeed', () => {
  const mountFeed = (alerts: unknown[], isLoading = false) =>
    mount(DashboardAlertFeed, {
      props: { alerts, isLoading },
      global: {
        stubs: {
          Skeleton: { template: '<div data-testid="skeleton" />', props: ['width', 'height'] },
        },
      },
    })

  it('shows skeleton rows when loading', () => {
    const wrapper = mountFeed([], true)
    expect(wrapper.findAll('[data-testid="skeleton"]')).toHaveLength(3)
  })

  it('shows empty state when no alerts', () => {
    const wrapper = mountFeed([])
    expect(wrapper.find('[data-testid="no-alerts"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="no-alerts"]').text()).toContain('dashboard.noRecentChanges')
  })

  it('renders up to 5 alert items', () => {
    const alerts = Array.from({ length: 8 }, (_, i) => ({
      alertId: `a${i}`,
      taxNumber: String(i).padStart(8, '0'),
      companyName: `Co ${i}`,
      previousStatus: 'RELIABLE',
      newStatus: 'AT_RISK',
      changedAt: '2026-04-01T08:00:00Z',
      tenantId: 't1',
      tenantName: 'T1',
      sha256Hash: null,
      verdictId: null,
    }))
    const wrapper = mountFeed(alerts)
    expect(wrapper.findAll('[data-testid="alert-item"]')).toHaveLength(5)
  })

  it('renders company name and status transition for each alert', () => {
    const alerts = [{
      alertId: 'a1',
      taxNumber: '11111111',
      companyName: 'Test Corp',
      previousStatus: 'RELIABLE',
      newStatus: 'AT_RISK',
      changedAt: '2026-04-01T08:00:00Z',
      tenantId: 't1',
      tenantName: 'T1',
      sha256Hash: null,
      verdictId: null,
    }]
    const wrapper = mountFeed(alerts)
    const item = wrapper.find('[data-testid="alert-item"]')
    expect(item.text()).toContain('Test Corp')
    // i18n keys used via t() stub
    expect(item.text()).toContain('screening.verdict.reliable')
    expect(item.text()).toContain('screening.verdict.atRisk')
  })
})
