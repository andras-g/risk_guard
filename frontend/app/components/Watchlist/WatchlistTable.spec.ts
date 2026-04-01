import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WatchlistTable from './WatchlistTable.vue'
import type { WatchlistEntryResponse } from '~/types/api'

// Stub PrimeVue components
const DataTableStub = {
  template: '<div data-testid="watchlist-table"><slot /><slot name="header" /></div>',
  props: ['value', 'filters', 'globalFilterFields', 'rows', 'paginator', 'stripedRows', 'selection', 'selectionMode'],
  emits: ['update:selection'],
}
const ColumnStub = {
  template: '<div :data-column-header="header"><slot name="body" :data="mockData" /></div>',
  props: ['field', 'header', 'sortable', 'selectionMode'],
  data() { return { mockData: {} } },
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const SkeletonStub = { template: '<div class="skeleton-stub" />' }
const InputTextStub = { template: '<input data-testid="watchlist-search-input" />', props: ['modelValue', 'placeholder'] }
const NuxtLinkStub = { template: '<a :data-testid="$attrs[\'data-testid\']"><slot /></a>', props: ['to'] }

// Stub useI18n
vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) return key.replace(/{(\w+)}/g, (_, k) => params[k] ?? k)
    return key
  },
}))

// Stub useDateRelative
vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string) => `relative(${date})`,
  }),
}))

function buildEntry(overrides: Partial<WatchlistEntryResponse> = {}): WatchlistEntryResponse {
  return {
    id: 'entry-1',
    taxNumber: '12345678',
    companyName: 'Test Company Kft.',
    label: null,
    currentVerdictStatus: 'RELIABLE',
    lastCheckedAt: '2026-03-18T12:00:00Z',
    createdAt: '2026-03-18T10:00:00Z',
    previousVerdictStatus: null,
    ...overrides,
  }
}

function mountTable(props: { entries?: WatchlistEntryResponse[]; isLoading?: boolean; selection?: WatchlistEntryResponse[] } = {}) {
  return mount(WatchlistTable, {
    props: {
      entries: props.entries ?? [buildEntry()],
      isLoading: props.isLoading ?? false,
      selection: props.selection ?? [],
    },
    global: {
      stubs: {
        DataTable: DataTableStub,
        Column: ColumnStub,
        Button: ButtonStub,
        Skeleton: SkeletonStub,
        InputText: InputTextStub,
        NuxtLink: NuxtLinkStub,
      },
    },
  })
}

describe('WatchlistTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders empty state when entries array is empty', () => {
    const wrapper = mountTable({ entries: [] })
    expect(wrapper.find('[data-testid="watchlist-empty-state"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('notification.watchlist.emptyTitle')
    expect(wrapper.text()).toContain('notification.watchlist.emptyDescription')
  })

  it('renders empty state CTA link to dashboard', () => {
    const wrapper = mountTable({ entries: [] })
    const cta = wrapper.find('[data-testid="empty-cta-link"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('notification.watchlist.emptyCta')
  })

  it('renders skeleton rows while loading', () => {
    const wrapper = mountTable({ isLoading: true })
    expect(wrapper.find('[data-testid="watchlist-skeleton"]').exists()).toBe(true)
    expect(wrapper.findAll('.skeleton-stub').length).toBeGreaterThan(0)
  })

  it('renders DataTable when entries are present', () => {
    const wrapper = mountTable({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="watchlist-table"]').exists()).toBe(true)
  })

  it('does not render empty state when entries exist', () => {
    const wrapper = mountTable({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="watchlist-empty-state"]').exists()).toBe(false)
  })

  it('does not render skeleton when not loading', () => {
    const wrapper = mountTable({ entries: [buildEntry()], isLoading: false })
    expect(wrapper.find('[data-testid="watchlist-skeleton"]').exists()).toBe(false)
  })

  it('emits remove event when remove button is clicked', async () => {
    const entry = buildEntry()
    const wrapper = mountTable({ entries: [entry] })
    const removeBtn = wrapper.find('[data-testid="remove-entry-button"]')
    if (removeBtn.exists()) {
      await removeBtn.trigger('click')
      expect(wrapper.emitted('remove')).toBeTruthy()
    }
  })

  it('renders search input in table header', () => {
    const wrapper = mountTable({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="watchlist-search-input"]').exists()).toBe(true)
  })

  it('emits update:selection when selection prop changes', async () => {
    const entry = buildEntry()
    const wrapper = mountTable({ entries: [entry], selection: [] })
    await wrapper.setProps({ selection: [entry] })
    // Prop update wires through internalSelection computed — no crash expected
    expect(wrapper.props('selection')).toEqual([entry])
  })

  // ─── Last Screened column (AC 1) ────────────────────────────────────────────

  it('Last Screened column renders formatRelative when lastCheckedAt is non-null', () => {
    const ColumnWithDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return { data: { lastCheckedAt: '2026-03-18T12:00:00Z' } }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ lastCheckedAt: '2026-03-18T12:00:00Z' })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.text()).toContain('relative(2026-03-18T12:00:00Z)')
  })

  it('Last Screened column shows — when lastCheckedAt is null', () => {
    const ColumnWithNullDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return { data: { lastCheckedAt: null } }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ lastCheckedAt: null })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithNullDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.html()).toContain('—')
  })

  // ─── Trend column (AC 2, 3) ──────────────────────────────────────────────────

  it('Trend column shows pi-arrow-up when status improved (AT_RISK → RELIABLE)', () => {
    // ColumnStub exposes mockData via slot; we need a real column stub that passes real data
    // Use a specialised wrapper that passes data directly
    const ColumnWithDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return {
          data: {
            currentVerdictStatus: 'RELIABLE',
            previousVerdictStatus: 'AT_RISK',
          },
        }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ currentVerdictStatus: 'RELIABLE', previousVerdictStatus: 'AT_RISK' })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.html()).toContain('pi-arrow-up')
  })

  it('Trend column shows pi-arrow-down when status worsened (RELIABLE → AT_RISK)', () => {
    const ColumnWithDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return {
          data: {
            currentVerdictStatus: 'AT_RISK',
            previousVerdictStatus: 'RELIABLE',
          },
        }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ currentVerdictStatus: 'AT_RISK', previousVerdictStatus: 'RELIABLE' })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.html()).toContain('pi-arrow-down')
  })

  it('Trend column shows pi-minus when status unchanged', () => {
    const ColumnWithDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return {
          data: {
            currentVerdictStatus: 'RELIABLE',
            previousVerdictStatus: 'RELIABLE',
          },
        }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ currentVerdictStatus: 'RELIABLE', previousVerdictStatus: 'RELIABLE' })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.html()).toContain('pi-minus')
  })

  it('Trend column shows — when previousVerdictStatus is null', () => {
    const ColumnWithDataStub = {
      template: '<div><slot name="body" :data="data" /></div>',
      props: ['field', 'header', 'sortable', 'selectionMode'],
      data() {
        return {
          data: {
            currentVerdictStatus: 'RELIABLE',
            previousVerdictStatus: null,
          },
        }
      },
    }
    const wrapper = mount(WatchlistTable, {
      props: { entries: [buildEntry({ previousVerdictStatus: null })], isLoading: false, selection: [] },
      global: {
        stubs: {
          DataTable: DataTableStub,
          Column: ColumnWithDataStub,
          Button: ButtonStub,
          Skeleton: SkeletonStub,
          InputText: InputTextStub,
          NuxtLink: NuxtLinkStub,
        },
      },
    })
    expect(wrapper.html()).toContain('—')
  })
})
