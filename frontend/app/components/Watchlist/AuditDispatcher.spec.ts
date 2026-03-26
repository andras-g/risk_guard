import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import AuditDispatcher from './AuditDispatcher.vue'
import type { WatchlistEntryResponse } from '~/types/api'

// Mock useWatchlistPdfExport to prevent actual jsPDF execution
const mockGenerateAndDispatch = vi.fn()
const mockIsGenerating = ref(false)

vi.mock('~/composables/useWatchlistPdfExport', () => ({
  useWatchlistPdfExport: () => ({
    isGenerating: mockIsGenerating,
    generateAndDispatch: mockGenerateAndDispatch,
  }),
}))

// Stub global composables
vi.stubGlobal('useWatchlistPdfExport', () => ({
  isGenerating: mockIsGenerating,
  generateAndDispatch: mockGenerateAndDispatch,
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

vi.stubGlobal('useToast', () => ({
  add: vi.fn(),
}))

const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  props: ['label', 'icon', 'loading', 'disabled'],
  inheritAttrs: true,
  emits: ['click'],
}

function buildEntry(overrides: Partial<WatchlistEntryResponse> = {}): WatchlistEntryResponse {
  return {
    id: 'entry-1',
    taxNumber: '12345678',
    companyName: 'Test Company Kft.',
    label: null,
    currentVerdictStatus: 'RELIABLE',
    lastCheckedAt: '2026-03-26T10:00:00Z',
    createdAt: '2026-03-26T08:00:00Z',
    ...overrides,
  }
}

function mountDispatcher(props: {
  entries?: WatchlistEntryResponse[]
  selectedEntries?: WatchlistEntryResponse[]
} = {}) {
  return mount(AuditDispatcher, {
    props: {
      entries: props.entries ?? [buildEntry()],
      selectedEntries: props.selectedEntries ?? [],
    },
    global: {
      stubs: {
        Button: ButtonStub,
      },
    },
  })
}

describe('AuditDispatcher', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockIsGenerating.value = false
    // Reset window.innerWidth to simulate desktop (≥768px) so label is 'Export PDF' in tests
    Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: 1024 })
  })

  it('renders Export PDF button', () => {
    const wrapper = mountDispatcher()
    expect(wrapper.find('[data-testid="export-pdf-button"]').exists()).toBe(true)
  })

  it('button label includes entry count when entries are selected', () => {
    const entries = [buildEntry(), buildEntry({ id: 'entry-2', taxNumber: '87654321' })]
    const wrapper = mountDispatcher({ entries, selectedEntries: [entries[0]] })
    const button = wrapper.find('[data-testid="export-pdf-button"]')
    expect(button.exists()).toBe(true)
    // ButtonStub renders label as child text — check via the component prop
    const buttonComponent = wrapper.findComponent(ButtonStub)
    expect(buttonComponent.props('label')).toContain('(1)')
  })

  it('button label shows all when no entries are selected', () => {
    const wrapper = mountDispatcher({ entries: [buildEntry()], selectedEntries: [] })
    const buttonComponent = wrapper.findComponent(ButtonStub)
    expect(buttonComponent.props('label')).toContain('notification.watchlist.export.all')
  })

  it('button is disabled when entries array is empty', () => {
    const wrapper = mountDispatcher({ entries: [], selectedEntries: [] })
    const button = wrapper.find('[data-testid="export-pdf-button"]')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('button is not disabled when entries exist', () => {
    const wrapper = mountDispatcher({ entries: [buildEntry()], selectedEntries: [] })
    const button = wrapper.find('[data-testid="export-pdf-button"]')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('button is disabled while isGenerating to prevent concurrent builds', () => {
    mockIsGenerating.value = true
    const wrapper = mountDispatcher({ entries: [buildEntry()], selectedEntries: [] })
    const button = wrapper.find('[data-testid="export-pdf-button"]')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('calls generateAndDispatch with selected entries when button clicked', async () => {
    const entries = [buildEntry()]
    mockGenerateAndDispatch.mockResolvedValue(undefined)
    const wrapper = mountDispatcher({ entries, selectedEntries: entries })

    await wrapper.find('[data-testid="export-pdf-button"]').trigger('click')
    await wrapper.vm.$nextTick()

    expect(mockGenerateAndDispatch).toHaveBeenCalledWith(entries)
  })

  it('calls generateAndDispatch with all entries when none selected', async () => {
    const entries = [buildEntry()]
    mockGenerateAndDispatch.mockResolvedValue(undefined)
    const wrapper = mountDispatcher({ entries, selectedEntries: [] })

    await wrapper.find('[data-testid="export-pdf-button"]').trigger('click')
    await wrapper.vm.$nextTick()

    expect(mockGenerateAndDispatch).toHaveBeenCalledWith(entries)
  })
})
