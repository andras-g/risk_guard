import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WatchlistPage from './index.vue'
import type { WatchlistEntryResponse } from '~/types/api'

// Module-level mock handles — must be defined before vi.mock() hoisting
const confirmRequireMock = vi.fn()

// Mock PrimeVue composables (they require a PrimeVue instance when called directly)
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: confirmRequireMock }) }))

// Mock watchlist store
const mockEntries: WatchlistEntryResponse[] = [
  {
    id: 'entry-1',
    taxNumber: '12345678',
    companyName: 'Test Company Kft.',
    label: null,
    currentVerdictStatus: 'RELIABLE',
    lastCheckedAt: '2026-03-26T10:00:00Z',
    createdAt: '2026-03-26T08:00:00Z',
    latestSha256Hash: 'a'.repeat(64),
  },
]

const mockFetchEntries = vi.fn()
const mockAddEntry = vi.fn()
const mockRemoveEntry = vi.fn()

let mockIsLoading = false

vi.mock('~/stores/watchlist', () => ({
  useWatchlistStore: () => ({
    get entries() { return mockEntries },
    get isLoading() { return mockIsLoading },
    fetchEntries: mockFetchEntries,
    addEntry: mockAddEntry,
    removeEntry: mockRemoveEntry,
  }),
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) return key.replace(/{(\w+)}/g, (_, k) => params[k] ?? k)
    return key
  },
}))

vi.stubGlobal('navigateTo', vi.fn())

// Stub child components
const WatchlistTableStub = {
  template: '<div data-testid="stub-watchlist-table" />',
  props: ['entries', 'isLoading', 'selection'],
  emits: ['remove', 'update:selection', 'row-select'],
}
const WatchlistAddDialogStub = {
  template: '<div data-testid="stub-add-dialog" />',
  props: ['visible'],
  emits: ['update:visible', 'submit'],
}
const WatchlistPartnerDrawerStub = {
  template: '<div data-testid="stub-partner-drawer" />',
  props: ['entry', 'visible'],
  emits: ['update:visible', 'remove', 'hide'],
}
const AuditDispatcherStub = {
  template: '<div data-testid="stub-audit-dispatcher" />',
  props: ['entries', 'selectedEntries'],
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  props: ['label', 'icon'],
  inheritAttrs: true,
  emits: ['click'],
}
const ConfirmDialogStub = { template: '<div />' }

function mountPage() {
  return mount(WatchlistPage, {
    global: {
      stubs: {
        WatchlistTable: WatchlistTableStub,
        WatchlistAddDialog: WatchlistAddDialogStub,
        WatchlistPartnerDrawer: WatchlistPartnerDrawerStub,
        AuditDispatcher: AuditDispatcherStub,
        Button: ButtonStub,
        ConfirmDialog: ConfirmDialogStub,
      },
    },
  })
}

describe('Watchlist Page (index.vue)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockIsLoading = false
    mockFetchEntries.mockResolvedValue(undefined)
  })

  it('renders the watchlist page title', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('notification.watchlist.title')
  })

  it('renders WatchlistTable component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-watchlist-table"]').exists()).toBe(true)
  })

  it('renders AuditDispatcher component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-audit-dispatcher"]').exists()).toBe(true)
  })

  it('passes entries to AuditDispatcher', () => {
    const wrapper = mountPage()
    const dispatcher = wrapper.findComponent(AuditDispatcherStub)
    expect(dispatcher.props('entries')).toEqual(mockEntries)
  })

  it('passes selectedEntries to AuditDispatcher (initially empty)', () => {
    const wrapper = mountPage()
    const dispatcher = wrapper.findComponent(AuditDispatcherStub)
    expect(dispatcher.props('selectedEntries')).toEqual([])
  })

  it('passes selection prop to WatchlistTable (initially empty)', () => {
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)
    expect(table.props('selection')).toEqual([])
  })

  it('passes entries from store to WatchlistTable', () => {
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)
    expect(table.props('entries')).toEqual(mockEntries)
  })

  it('renders add partner button', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="add-partner-button"]').exists()).toBe(true)
  })

  it('renders WatchlistPartnerDrawer component', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="stub-partner-drawer"]').exists()).toBe(true)
  })

  it('drawer is initially hidden (visible=false)', () => {
    const wrapper = mountPage()
    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('visible')).toBe(false)
  })

  it('drawer entry is initially null', () => {
    const wrapper = mountPage()
    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('entry')).toBeNull()
  })

  it('row-select from WatchlistTable opens drawer with correct entry', async () => {
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)

    await table.vm.$emit('row-select', mockEntries[0])
    await wrapper.vm.$nextTick()

    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('visible')).toBe(true)
    expect(drawer.props('entry')).toEqual(mockEntries[0])
  })

  it('row-select is ignored when store is loading', async () => {
    mockIsLoading = true
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)

    await table.vm.$emit('row-select', mockEntries[0])
    await wrapper.vm.$nextTick()

    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('visible')).toBe(false)
  })

  it('drawer @remove opens confirm dialog (drawer stays open until accepted)', async () => {
    mockRemoveEntry.mockResolvedValue(undefined)
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)

    // Open drawer with entry
    await table.vm.$emit('row-select', mockEntries[0])
    await wrapper.vm.$nextTick()

    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('visible')).toBe(true)

    // Emit remove from drawer — should open confirm dialog, drawer stays open
    await drawer.vm.$emit('remove')
    await wrapper.vm.$nextTick()

    expect(confirmRequireMock).toHaveBeenCalled()
    expect(drawer.props('visible')).toBe(true)

    // Simulate user accepting the confirmation — drawer closes
    const options = confirmRequireMock.mock.calls[0]![0]
    await options.accept()
    await wrapper.vm.$nextTick()

    expect(drawer.props('visible')).toBe(false)
  })

  it('drawer @hide clears selectedPartner (entry becomes null)', async () => {
    const wrapper = mountPage()
    const table = wrapper.findComponent(WatchlistTableStub)

    // Open drawer with entry
    await table.vm.$emit('row-select', mockEntries[0])
    await wrapper.vm.$nextTick()

    const drawer = wrapper.findComponent(WatchlistPartnerDrawerStub)
    expect(drawer.props('entry')).toEqual(mockEntries[0])

    // Emit hide from drawer — should clear selectedPartner
    await drawer.vm.$emit('hide')
    await wrapper.vm.$nextTick()

    expect(drawer.props('entry')).toBeNull()
  })
})
