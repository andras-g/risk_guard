import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EprSubmissionsTable from './EprSubmissionsTable.vue'
import type { EprSubmissionSummary } from '~/types/epr'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string) => `relative(${date})`,
  }),
}))

const SkeletonStub = { template: '<div class="skeleton-stub" />', props: ['width', 'height'] }
const DataTableStub = {
  template: '<div :data-testid="$attrs[\'data-testid\'] || \'dt\'"><slot /><slot name="empty" /></div>',
  props: ['value', 'lazy', 'paginator', 'rows', 'totalRecords', 'first'],
  emits: ['page'],
  inheritAttrs: true,
  provide() { return { dtRows: this.value ?? [] } },
}
const ColumnStub = {
  template: '<div class="col-stub"><template v-for="row in dtRows" :key="row.id"><slot name="body" :data="row" /></template></div>',
  props: ['field', 'header', 'style'],
  inject: ['dtRows'],
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="disabled" @click="$emit(\'click\')">btn</button>',
  props: ['icon', 'text', 'rounded', 'disabled', 'ariaLabel', 'title'],
  emits: ['click'],
  inheritAttrs: true,
}

function makeSummary(overrides: Partial<EprSubmissionSummary> = {}): EprSubmissionSummary {
  return {
    id: 'sub-uuid-001',
    periodStart: '2026-01-01',
    periodEnd: '2026-03-31',
    totalWeightKg: 10.500,
    totalFeeHuf: 5000.00,
    exportedAt: '2026-04-01T10:00:00Z',
    fileName: 'okirkapu-test.xml',
    submittedByUserEmail: 'user@example.com',
    hasXmlContent: true,
    ...overrides,
  }
}

function mountTable(props: {
  rows?: EprSubmissionSummary[]
  totalElements?: number
  isLoading?: boolean
}) {
  return mount(EprSubmissionsTable, {
    props: {
      rows: props.rows ?? [],
      totalElements: props.totalElements ?? 0,
      isLoading: props.isLoading ?? false,
    },
    global: {
      stubs: {
        Skeleton: SkeletonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('EprSubmissionsTable', () => {
  it('renders data table when not loading', () => {
    const wrapper = mountTable({ rows: [makeSummary()] })
    expect(wrapper.find('[data-testid="submissions-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="submissions-skeleton"]').exists()).toBe(false)
  })

  it('renders skeleton when isLoading is true', () => {
    const wrapper = mountTable({ isLoading: true })
    expect(wrapper.find('[data-testid="submissions-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="submissions-table"]').exists()).toBe(false)
  })

  it('passes rows and totalElements to DataTable', () => {
    const rows = [makeSummary(), makeSummary({ id: 'sub-002' })]
    const wrapper = mountTable({ rows, totalElements: 5 })
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as EprSubmissionSummary[]).length).toBe(2)
    expect(dt.props('totalRecords')).toBe(5)
  })

  it('DataTable has lazy=true', () => {
    const wrapper = mountTable({ rows: [makeSummary()] })
    expect(wrapper.findComponent(DataTableStub).props('lazy')).toBe(true)
  })

  it('emits page event on DataTable page change', async () => {
    const wrapper = mountTable({ rows: [makeSummary()], totalElements: 100 })
    const dt = wrapper.findComponent(DataTableStub)
    await dt.vm.$emit('page', { page: 2, rows: 25 })
    expect(wrapper.emitted('page')).toBeTruthy()
    expect(wrapper.emitted('page')![0]).toEqual([{ page: 2, rows: 25 }])
  })

  it('shows deletedUser when submittedByUserEmail is null', () => {
    const wrapper = mountTable({ rows: [makeSummary({ submittedByUserEmail: null })] })
    expect(wrapper.text()).toContain('epr.submissions.deletedUser')
  })

  it('download button disabled when hasXmlContent=false', () => {
    const row = makeSummary({ hasXmlContent: false, id: 'no-xml' })
    const wrapper = mountTable({ rows: [row] })
    const btn = wrapper.find('[data-testid="submission-download-no-xml"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('download button enabled when hasXmlContent=true', () => {
    const row = makeSummary({ hasXmlContent: true, id: 'has-xml' })
    const wrapper = mountTable({ rows: [row] })
    const btn = wrapper.find('[data-testid="submission-download-has-xml"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('emits download event with id and fileName on button click', async () => {
    const row = makeSummary({ id: 'dl-123', fileName: 'report.xml', hasXmlContent: true })
    const wrapper = mountTable({ rows: [row] })
    const btn = wrapper.find('[data-testid="submission-download-dl-123"]')
    await btn.trigger('click')
    expect(wrapper.emitted('download')).toBeTruthy()
    expect(wrapper.emitted('download')![0]).toEqual([{ id: 'dl-123', fileName: 'report.xml' }])
  })

  it('shows empty message in #empty slot', () => {
    const wrapper = mountTable({ rows: [] })
    expect(wrapper.text()).toContain('epr.submissions.emptyMessage')
  })
})
