import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WatchlistAddDialog from './WatchlistAddDialog.vue'

// Stub PrimeVue components
const DialogStub = {
  template: '<div data-testid="watchlist-add-dialog" v-if="visible"><slot /><slot name="footer" /></div>',
  props: ['visible', 'header', 'modal', 'closable', 'style'],
  emits: ['update:visible'],
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const InputTextStub = {
  template: '<input :value="modelValue" :data-testid="$attrs[\'data-testid\']" :disabled="$attrs.disabled" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup.enter="$emit(\'keyup\', $event)" />',
  props: ['modelValue', 'placeholder', 'class', 'disabled'],
  emits: ['update:modelValue', 'keyup'],
}
const TagStub = {
  template: '<span class="tag-stub">{{ value }}</span>',
  props: ['value', 'severity'],
}

// Mock screening store
const mockScreeningStore = {
  search: vi.fn(),
  currentVerdict: null,
}
vi.mock('~/stores/screening', () => ({
  useScreeningStore: () => mockScreeningStore,
}))

// Stub useI18n
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

// Stub useRuntimeConfig
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

function mountDialog(props: { visible?: boolean } = {}) {
  return mount(WatchlistAddDialog, {
    props: {
      visible: props.visible ?? true,
    },
    global: {
      stubs: {
        Dialog: DialogStub,
        Button: ButtonStub,
        InputText: InputTextStub,
        Tag: TagStub,
      },
    },
  })
}

describe('WatchlistAddDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when visible', () => {
    const wrapper = mountDialog({ visible: true })
    expect(wrapper.find('[data-testid="watchlist-add-dialog"]').exists()).toBe(true)
  })

  it('does not render dialog when not visible', () => {
    const wrapper = mountDialog({ visible: false })
    expect(wrapper.find('[data-testid="watchlist-add-dialog"]').exists()).toBe(false)
  })

  it('renders tax number input', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="watchlist-tax-input"]').exists()).toBe(true)
  })

  it('renders search button', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-testid="watchlist-search-button"]').exists()).toBe(true)
  })

  it('add button is disabled before search', () => {
    const wrapper = mountDialog()
    const submit = wrapper.find('[data-testid="watchlist-add-submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
  })

  it('shows validation error for invalid tax number on search', async () => {
    const wrapper = mountDialog()
    const input = wrapper.find('[data-testid="watchlist-tax-input"]')
    await input.setValue('123')
    const searchBtn = wrapper.find('[data-testid="watchlist-search-button"]')
    await searchBtn.trigger('click')
    expect(wrapper.find('[data-testid="watchlist-tax-error"]').exists()).toBe(true)
  })

  it('emits update:visible false on cancel', async () => {
    const wrapper = mountDialog()
    const cancel = wrapper.find('[data-testid="watchlist-add-cancel"]')
    await cancel.trigger('click')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })

  it('shows generic search failed message for non-404 errors', async () => {
    mockScreeningStore.search.mockRejectedValueOnce({ response: { status: 500 } })
    const wrapper = mountDialog()
    const input = wrapper.find('[data-testid="watchlist-tax-input"]')
    await input.setValue('12345678')
    const searchBtn = wrapper.find('[data-testid="watchlist-search-button"]')
    await searchBtn.trigger('click')
    // Wait for async handler
    await new Promise(r => setTimeout(r, 10))
    const errorEl = wrapper.find('[data-testid="watchlist-tax-error"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('screening.verdict.searchFailed')
  })

  it('shows not-found message for 404 errors', async () => {
    mockScreeningStore.search.mockRejectedValueOnce({ response: { status: 404 } })
    const wrapper = mountDialog()
    const input = wrapper.find('[data-testid="watchlist-tax-input"]')
    await input.setValue('12345678')
    const searchBtn = wrapper.find('[data-testid="watchlist-search-button"]')
    await searchBtn.trigger('click')
    // Wait for async handler
    await new Promise(r => setTimeout(r, 10))
    const errorEl = wrapper.find('[data-testid="watchlist-tax-error"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('notification.watchlist.addDialog.notFound')
  })
})
