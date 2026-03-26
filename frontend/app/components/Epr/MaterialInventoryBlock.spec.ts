import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MaterialInventoryBlock from './MaterialInventoryBlock.vue'
import type { MaterialTemplateResponse } from '~/types/epr'

// Stub PrimeVue components
const DataTableStub = {
  template: '<div data-testid="epr-materials-table"><slot /><slot name="header" /></div>',
  props: ['value', 'filters', 'globalFilterFields', 'rows', 'paginator', 'stripedRows'],
  provide() {
    return { dtEntries: (this as any).value }
  },
}
/**
 * Column stub that injects mock data from the parent DataTable stub's `value` prop.
 * Uses the first entry in the array to render column body templates.
 */
const ColumnStub = {
  template: '<div><slot name="body" :data="entryData" /></div>',
  props: ['field', 'header', 'sortable'],
  inject: { dtEntries: { default: () => [] } },
  computed: {
    entryData(): any {
      return (this as any).dtEntries?.[0] ?? {}
    },
  },
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const SkeletonStub = { template: '<div class="skeleton-stub" />' }
const InputTextStub = { template: '<input data-testid="epr-search-input" />', props: ['modelValue', 'placeholder'] }
const InputSwitchStub = { template: '<input type="checkbox" data-testid="recurring-toggle" />', props: ['modelValue'] }
const SelectStub = { template: '<select data-testid="recurring-filter" />', props: ['modelValue', 'options', 'optionLabel', 'optionValue'] }

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string) => `relative(${date})`,
  }),
}))

function buildEntry(overrides: Partial<MaterialTemplateResponse> = {}): MaterialTemplateResponse {
  return {
    id: 'tmpl-1',
    name: 'Cardboard Box',
    baseWeightGrams: 120.5,
    kfCode: null,
    verified: false,
    recurring: true,
    createdAt: '2026-03-24T10:00:00Z',
    updatedAt: '2026-03-24T10:00:00Z',
    overrideKfCode: null,
    overrideReason: null,
    confidence: null,
    feeRate: null,
    ...overrides,
  }
}

const EprConfidenceBadgeStub = {
  template: '<span data-testid="confidence-badge-in-table">{{ confidence }}</span>',
  props: ['confidence'],
}

function mountBlock(props: { entries?: MaterialTemplateResponse[]; isLoading?: boolean } = {}) {
  return mount(MaterialInventoryBlock, {
    props: {
      entries: props.entries ?? [buildEntry()],
      isLoading: props.isLoading ?? false,
    },
    global: {
      stubs: {
        DataTable: DataTableStub,
        Column: ColumnStub,
        Button: ButtonStub,
        Skeleton: SkeletonStub,
        InputText: InputTextStub,
        InputSwitch: InputSwitchStub,
        Select: SelectStub,
        EprConfidenceBadge: EprConfidenceBadgeStub,
      },
    },
  })
}

describe('MaterialInventoryBlock', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders empty state when entries array is empty', () => {
    const wrapper = mountBlock({ entries: [] })
    expect(wrapper.find('[data-testid="epr-empty-state"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('epr.materialLibrary.empty')
  })

  it('renders skeleton rows while loading', () => {
    const wrapper = mountBlock({ isLoading: true })
    expect(wrapper.find('[data-testid="epr-skeleton"]').exists()).toBe(true)
    expect(wrapper.findAll('.skeleton-stub').length).toBeGreaterThan(0)
  })

  it('renders DataTable when entries are present', () => {
    const wrapper = mountBlock({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="epr-materials-table"]').exists()).toBe(true)
  })

  it('does not render empty state when entries exist', () => {
    const wrapper = mountBlock({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="epr-empty-state"]').exists()).toBe(false)
  })

  it('does not render skeleton when not loading', () => {
    const wrapper = mountBlock({ entries: [buildEntry()], isLoading: false })
    expect(wrapper.find('[data-testid="epr-skeleton"]').exists()).toBe(false)
  })

  it('renders search input in table header', () => {
    const wrapper = mountBlock({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="epr-search-input"]').exists()).toBe(true)
  })

  it('renders recurring filter in table header', () => {
    const wrapper = mountBlock({ entries: [buildEntry()] })
    expect(wrapper.find('[data-testid="recurring-filter"]').exists()).toBe(true)
  })

  it('renders Filing-Ready badge for verified templates', () => {
    const wrapper = mountBlock({
      entries: [buildEntry({ verified: true, kfCode: '11010101', confidence: 'HIGH' })],
    })
    expect(wrapper.text()).toContain('epr.materialLibrary.filingReady')
  })

  it('renders Unverified badge for unverified templates', () => {
    const wrapper = mountBlock({
      entries: [buildEntry({ verified: false, kfCode: null })],
    })
    expect(wrapper.text()).toContain('epr.materialLibrary.unverified')
  })

  it('renders ConfidenceBadge next to Filing-Ready for verified templates with confidence', () => {
    const wrapper = mountBlock({
      entries: [buildEntry({ verified: true, kfCode: '11010101', confidence: 'HIGH' })],
    })
    // EprConfidenceBadge is auto-stubbed by the test setup as a custom element
    expect(wrapper.find('[data-testid="confidence-badge-in-table"]').exists() || wrapper.findComponent({ name: 'EprConfidenceBadge' }).exists()).toBe(true)
  })
})
