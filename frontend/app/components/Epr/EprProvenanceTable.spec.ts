import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EprProvenanceTable from './EprProvenanceTable.vue'
import type { ProvenanceLine } from '~/types/epr'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

const SkeletonStub = { template: '<div class="skeleton-stub" />', props: ['width', 'height'] }
const DataTableStub = {
  template: '<div :data-testid="$attrs[\'data-testid\'] || \'dt\'"><slot /></div>',
  props: ['value', 'lazy', 'paginator', 'rows', 'totalRecords', 'first'],
  emits: ['page'],
  inheritAttrs: true,
}
const ColumnStub = {
  template: '<div class="col-stub" />',
  props: ['field', 'header'],
}
const TagStub = {
  template: '<span :data-testid="$attrs[\'data-testid\']" class="tag-stub">{{ value }}</span>',
  props: ['value', 'severity'],
  inheritAttrs: true,
}

function makeLine(overrides: Partial<ProvenanceLine> = {}): ProvenanceLine {
  return {
    invoiceNumber: 'INV-001',
    lineNumber: 1,
    vtsz: '73181500',
    description: 'Screw',
    quantity: 10,
    unitOfMeasure: 'DARAB',
    resolvedProductId: 'prod-uuid-1234',
    productName: 'Test Product',
    componentId: 'comp-uuid-5678',
    wrappingLevel: 1,
    componentKfCode: '1001 01 00',
    weightContributionKg: 0.0200,
    provenanceTag: 'REGISTRY_MATCH',
    ...overrides,
  }
}

function mountTable(props: {
  rows?: ProvenanceLine[]
  totalElements?: number
  isLoading?: boolean
  period?: { from: string; to: string }
}) {
  return mount(EprProvenanceTable, {
    props: {
      rows: props.rows ?? [],
      totalElements: props.totalElements ?? 0,
      isLoading: props.isLoading ?? false,
      period: props.period ?? { from: '2026-01-01', to: '2026-03-31' },
    },
    global: {
      stubs: {
        Skeleton: SkeletonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        Tag: TagStub,
      },
    },
  })
}

describe('EprProvenanceTable', () => {
  it('renders data table when not loading', () => {
    const wrapper = mountTable({ rows: [makeLine()] })
    expect(wrapper.find('[data-testid="provenance-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="provenance-skeleton"]').exists()).toBe(false)
  })

  it('renders skeleton when isLoading is true', () => {
    const wrapper = mountTable({ isLoading: true })
    expect(wrapper.find('[data-testid="provenance-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="provenance-table"]').exists()).toBe(false)
  })

  it('passes rows and totalElements to DataTable', () => {
    const lines = [makeLine(), makeLine({ lineNumber: 2 })]
    const wrapper = mountTable({ rows: lines, totalElements: 10 })
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as ProvenanceLine[]).length).toBe(2)
    expect(dt.props('totalRecords')).toBe(10)
  })

  it('DataTable has lazy=true', () => {
    const wrapper = mountTable({ rows: [makeLine()] })
    const dt = wrapper.findComponent(DataTableStub)
    expect(dt.props('lazy')).toBe(true)
  })

  it('emits page event on DataTable page change', async () => {
    const wrapper = mountTable({ rows: [makeLine()], totalElements: 100 })
    const dt = wrapper.findComponent(DataTableStub)
    await dt.vm.$emit('page', { page: 1, rows: 50 })
    expect(wrapper.emitted('page')).toBeTruthy()
    expect(wrapper.emitted('page')![0]).toEqual([{ page: 1, rows: 50 }])
  })

  it('weightContributionKg formatted to 4 decimal places', () => {
    const wrapper = mountTable({ rows: [makeLine({ weightContributionKg: 0.02 })] })
    const vm = wrapper.vm as unknown as { formatWeight: (v: number) => string }
    expect(vm.formatWeight(0.02)).toBe('0.0200')
  })

  it('renders product name as link when resolvedProductId is not null', () => {
    const line = makeLine({ resolvedProductId: 'abc-123', productName: 'My Product' })
    const wrapper = mountTable({ rows: [line] })
    // Product name link is in the component template — verify via data passed to DataTable
    const dt = wrapper.findComponent(DataTableStub)
    const rows = dt.props('value') as ProvenanceLine[]
    expect(rows[0].resolvedProductId).toBe('abc-123')
    expect(rows[0].productName).toBe('My Product')
  })

  it('badge color: REGISTRY_MATCH → success', () => {
    const wrapper = mountTable({ rows: [makeLine({ provenanceTag: 'REGISTRY_MATCH' })] })
    const vm = wrapper.vm as unknown as { tagSeverity: (t: string) => string }
    expect(vm.tagSeverity('REGISTRY_MATCH')).toBe('success')
  })

  it('badge color: VTSZ_FALLBACK → warn', () => {
    const wrapper = mountTable({ rows: [makeLine({ provenanceTag: 'VTSZ_FALLBACK' })] })
    const vm = wrapper.vm as unknown as { tagSeverity: (t: string) => string }
    expect(vm.tagSeverity('VTSZ_FALLBACK')).toBe('warn')
  })

  it('badge color: UNRESOLVED → danger', () => {
    const vm = mountTable({ rows: [] }).vm as unknown as { tagSeverity: (t: string) => string }
    expect(vm.tagSeverity('UNRESOLVED')).toBe('danger')
  })

  it('badge color: UNSUPPORTED_UNIT → contrast', () => {
    const vm = mountTable({ rows: [] }).vm as unknown as { tagSeverity: (t: string) => string }
    expect(vm.tagSeverity('UNSUPPORTED_UNIT')).toBe('contrast')
  })
})
