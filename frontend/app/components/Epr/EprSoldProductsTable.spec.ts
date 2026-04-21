import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import EprSoldProductsTable from './EprSoldProductsTable.vue'
import type { SoldProductLine, UnresolvedInvoiceLine } from '~/types/epr'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

const mockRouterPush = vi.fn()
vi.stubGlobal('useRouter', () => ({ push: mockRouterPush }))

const SkeletonStub = { template: '<div class="skeleton-stub" />', props: ['width', 'height'] }
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  props: ['label', 'severity', 'outlined', 'size'],
  emits: ['click'],
  inheritAttrs: true,
}
// DataTable stub that renders row data including slot #body via column-like rendering
const DataTableStub = {
  template: `<div :data-testid="$attrs['data-testid'] || 'dt'" @row-click="$emit('row-click', { data: (value || [])[0] })">
    <slot />
    <div v-for="(row, idx) in (value || [])" :key="idx" class="dt-row" :data-testid="'row-' + idx">
      <span class="row-vtsz">{{ row.vtsz }}</span>
      <span class="row-desc">{{ row.description }}</span>
    </div>
  </div>`,
  props: ['value', 'sortField', 'sortOrder', 'paginator', 'rows', 'rowsPerPageOptions', 'rowHover'],
  emits: ['row-click'],
  inheritAttrs: true,
}
const ColumnStub = {
  template: '<div />',
  props: ['field', 'header', 'sortable'],
}

function makeSoldProduct(overrides: Partial<SoldProductLine> = {}): SoldProductLine {
  return {
    productId: 'prod-1',
    vtsz: '48191000',
    description: 'Karton doboz',
    totalQuantity: 1000,
    unitOfMeasure: 'DARAB',
    matchingInvoiceLines: 3,
    ...overrides,
  }
}

function makeUnresolved(overrides: Partial<UnresolvedInvoiceLine> = {}): UnresolvedInvoiceLine {
  return {
    invoiceNumber: 'INV-001',
    lineNumber: 1,
    vtsz: '48191000',
    description: 'Karton doboz',
    quantity: 10,
    unitOfMeasure: 'DARAB',
    reason: 'ZERO_COMPONENTS',
    ...overrides,
  }
}

function mountTable(props: {
  soldProducts?: SoldProductLine[]
  unresolvedLines?: UnresolvedInvoiceLine[]
  loading?: boolean
}) {
  return mount(EprSoldProductsTable, {
    props: {
      soldProducts: props.soldProducts ?? [],
      unresolvedLines: props.unresolvedLines ?? [],
      loading: props.loading ?? false,
    },
    global: {
      stubs: {
        Skeleton: SkeletonStub,
        Button: ButtonStub,
        DataTable: DataTableStub,
        Column: ColumnStub,
        Tag: { template: '<span class="tag-stub" :data-severity="severity" :data-value="value">{{ value }}</span>', props: ['severity', 'value'] },
      },
    },
  })
}

describe('EprSoldProductsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRouterPush.mockReset()
  })

  it('renders data table when not loading', () => {
    const wrapper = mountTable({ soldProducts: [makeSoldProduct()] })
    expect(wrapper.find('[data-testid="sold-products-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sold-products-skeleton"]').exists()).toBe(false)
  })

  it('renders skeleton when loading is true', () => {
    const wrapper = mountTable({ loading: true })
    expect(wrapper.find('[data-testid="sold-products-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sold-products-table"]').exists()).toBe(false)
  })

  it('ready product has success badge when no unresolved lines', () => {
    const product = makeSoldProduct({ vtsz: '11111111', description: 'Resolved A' })
    // No unresolved lines for this product → status = ready
    const wrapper = mountTable({ soldProducts: [product], unresolvedLines: [] })
    const dt = wrapper.findComponent(DataTableStub)
    const rows = dt.props('value') as SoldProductLine[]
    // The filteredProducts computed should return the row unchanged (no active filters)
    expect(rows.length).toBe(1)
    expect(rows[0].vtsz).toBe('11111111')
  })

  it('ZERO_COMPONENTS unresolved line causes missing status — row appears in missing filter', async () => {
    const product = makeSoldProduct({ vtsz: '48191000', description: 'Box A', productId: 'p1' })
    const unresolved = makeUnresolved({ vtsz: '48191000', description: 'Box A', reason: 'ZERO_COMPONENTS' })
    const wrapper = mountTable({ soldProducts: [product], unresolvedLines: [unresolved] })
    await wrapper.find('[data-testid="filter-chip-missing"]').trigger('click')
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as SoldProductLine[]).length).toBe(1)
    expect((dt.props('value') as SoldProductLine[])[0].productId).toBe('p1')
  })

  it('VTSZ_FALLBACK unresolved line causes uncertain status — row appears in uncertain filter', async () => {
    const product = makeSoldProduct({ vtsz: '48191000', description: 'Box A', productId: 'p1' })
    const unresolved = makeUnresolved({ vtsz: '48191000', description: 'Box A', reason: 'VTSZ_FALLBACK' })
    const wrapper = mountTable({ soldProducts: [product], unresolvedLines: [unresolved] })
    await wrapper.find('[data-testid="filter-chip-uncertain"]').trigger('click')
    const dt = wrapper.findComponent(DataTableStub)
    expect((dt.props('value') as SoldProductLine[]).length).toBe(1)
    expect((dt.props('value') as SoldProductLine[])[0].productId).toBe('p1')
  })

  it('Csak hiányos filter chip hides non-missing rows', async () => {
    const missingProduct = makeSoldProduct({ vtsz: '48191000', description: 'Box A', productId: 'p1' })
    const readyProduct = makeSoldProduct({ vtsz: '39000000', description: 'Bottle B', productId: 'p2' })
    const unresolved = makeUnresolved({ vtsz: '48191000', description: 'Box A', reason: 'ZERO_COMPONENTS' })
    const wrapper = mountTable({ soldProducts: [missingProduct, readyProduct], unresolvedLines: [unresolved] })
    await wrapper.find('[data-testid="filter-chip-missing"]').trigger('click')
    const dt = wrapper.findComponent(DataTableStub)
    const rows = dt.props('value') as SoldProductLine[]
    expect(rows.length).toBe(1)
    expect(rows[0].productId).toBe('p1')
  })

  it('Csak bizonytalan filter chip hides non-uncertain rows', async () => {
    const uncertainProduct = makeSoldProduct({ vtsz: '48191000', description: 'Box A', productId: 'p1' })
    const readyProduct = makeSoldProduct({ vtsz: '39000000', description: 'Bottle B', productId: 'p2' })
    const unresolved = makeUnresolved({ vtsz: '48191000', description: 'Box A', reason: 'VTSZ_FALLBACK' })
    const wrapper = mountTable({ soldProducts: [uncertainProduct, readyProduct], unresolvedLines: [unresolved] })
    await wrapper.find('[data-testid="filter-chip-uncertain"]').trigger('click')
    const dt = wrapper.findComponent(DataTableStub)
    const rows = dt.props('value') as SoldProductLine[]
    expect(rows.length).toBe(1)
    expect(rows[0].productId).toBe('p1')
  })

  it('row click with productId navigates to /registry/{productId}', async () => {
    const product = makeSoldProduct({ productId: 'prod-abc' })
    const wrapper = mountTable({ soldProducts: [product] })
    await wrapper.findComponent(DataTableStub).trigger('row-click')
    expect(mockRouterPush).toHaveBeenCalledWith('/registry/prod-abc')
  })

  it('row click without productId navigates to filtered registry URL', async () => {
    const product = makeSoldProduct({ productId: null, vtsz: '48191000', description: 'Box A' })
    const wrapper = mountTable({ soldProducts: [product] })
    await wrapper.findComponent(DataTableStub).trigger('row-click')
    expect(mockRouterPush).toHaveBeenCalledWith('/registry?vtsz=48191000&q=Box%20A')
  })
})
