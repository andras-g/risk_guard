import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EprKfTotalsTable from './EprKfTotalsTable.vue'
import type { KfCodeTotal } from '~/types/epr'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

const SkeletonStub = { template: '<div class="skeleton-stub" />', props: ['width', 'height'] }
// Stub that renders default slot (so Column content renders) and exposes test-id
const DataTableStub = {
  template: '<div :data-testid="$attrs[\'data-testid\'] || \'dt\'"><slot /></div>',
  props: ['value', 'sortField', 'sortOrder'],
  inheritAttrs: true,
}
const ColumnStub = {
  template: '<div class="col-stub" />',
  props: ['field', 'header', 'sortable'],
}

function makeKfTotal(overrides: Partial<KfCodeTotal> = {}): KfCodeTotal {
  return {
    kfCode: '15161700',
    classificationLabel: 'Papír csomagolás',
    totalWeightKg: 120.456,
    feeRateHufPerKg: 215,
    totalFeeHuf: 25898,
    contributingProductCount: 3,
    hasFallback: false,
    hasOverflowWarning: false,
    ...overrides,
  }
}

function mountTable(props: { kfTotals?: KfCodeTotal[], loading?: boolean }) {
  return mount(EprKfTotalsTable, {
    props: {
      kfTotals: props.kfTotals ?? [],
      loading: props.loading ?? false,
    },
    global: {
      stubs: {
        Skeleton: SkeletonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
      },
    },
  })
}

describe('EprKfTotalsTable', () => {
  it('renders data table when not loading', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal()] })
    expect(wrapper.find('[data-testid="kf-totals-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kf-totals-skeleton"]').exists()).toBe(false)
  })

  it('renders skeleton when loading is true', () => {
    const wrapper = mountTable({ loading: true })
    expect(wrapper.find('[data-testid="kf-totals-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kf-totals-table"]').exists()).toBe(false)
  })

  it('renders kfCode formatted as "15 16 17 00" (space every 2 chars)', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal({ kfCode: '15161700' })] })
    const vm = wrapper.vm as unknown as { formatKfCode: (code: string) => string }
    expect(vm.formatKfCode('15161700')).toBe('15 16 17 00')
  })

  it('renders totalWeightKg with 3 decimal places', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal({ totalWeightKg: 42.789 })] })
    const vm = wrapper.vm as unknown as { formatWeight: (value: number) => string }
    expect(vm.formatWeight(42.789)).toBe('42.789 kg')
  })

  it('renders totalFeeHuf locale-formatted', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal({ totalFeeHuf: 25898 })] })
    const vm = wrapper.vm as unknown as { formatHuf: (value: number) => string }
    expect(vm.formatHuf(25898)).toContain('Ft')
    expect(vm.formatHuf(25898)).toContain('25')
  })

  it('hasFallback icon shown when hasFallback is true (data reaches DataTable)', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal({ hasFallback: true })] })
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as KfCodeTotal[])[0].hasFallback).toBe(true)
  })

  it('hasOverflowWarning icon shown when hasOverflowWarning is true (data reaches DataTable)', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal({ hasOverflowWarning: true })] })
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as KfCodeTotal[])[0].hasOverflowWarning).toBe(true)
  })

  it('DataTable is sorted by totalFeeHuf DESC', () => {
    const wrapper = mountTable({ kfTotals: [makeKfTotal()] })
    const dt = wrapper.findComponent(DataTableStub)
    expect(dt.props('sortField')).toBe('totalFeeHuf')
    expect(dt.props('sortOrder')).toBe(-1)
  })
})
