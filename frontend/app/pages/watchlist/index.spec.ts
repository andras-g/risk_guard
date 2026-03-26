import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WatchlistPage from './index.vue'
import type { WatchlistEntryResponse } from '~/types/api'

// Mock PrimeVue composables (they require a PrimeVue instance when called directly)
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))

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

vi.mock('~/stores/watchlist', () => ({
  useWatchlistStore: () => ({
    entries: mockEntries,
    isLoading: false,
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

// Stub child components
const WatchlistTableStub = {
  template: '<div data-testid="stub-watchlist-table" />',
  props: ['entries', 'isLoading', 'selection'],
  emits: ['remove', 'update:selection'],
}
const WatchlistAddDialogStub = {
  template: '<div data-testid="stub-add-dialog" />',
  props: ['visible'],
  emits: ['update:visible', 'submit'],
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
})
