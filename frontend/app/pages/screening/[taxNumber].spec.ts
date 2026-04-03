import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
import VerdictDetailPage from './[taxNumber].vue'
import type { VerdictResponse, SnapshotProvenanceResponse } from '~/types/api'

/**
 * Component tests for the Verdict Detail page ([taxNumber].vue).
 * Tests desktop/mobile layout switching, liability disclaimer visibility,
 * back-to-dashboard navigation, resize listener cleanup, and loading states.
 *
 * Co-located per architecture rules (Story 2.4, Task 9.3).
 */

// --- Stubs for child components ---
const ScreeningVerdictCardStub = {
  template: '<div data-testid="stub-verdict-card"><slot /></div>',
  props: ['verdict'],
}
const ScreeningProvenanceSidebarStub = {
  template: '<div data-testid="stub-provenance-sidebar"><slot /></div>',
  props: ['provenance', 'confidence', 'mobile'],
}
const ScreeningSkeletonVerdictCardStub = {
  template: '<div data-testid="stub-skeleton-card"><slot /></div>',
  props: ['visible'],
}

// --- Mock screening store ---
const mockSearch = vi.fn()
const mockCurrentVerdict = ref<VerdictResponse | null>(null)
const mockCurrentProvenance = ref<SnapshotProvenanceResponse | null>(null)
const mockIsSearching = ref(false)
const mockSearchError = ref<string | null>(null)

vi.mock('~/stores/screening', () => ({
  useScreeningStore: () => ({
    search: mockSearch,
    currentVerdict: mockCurrentVerdict,
    currentProvenance: mockCurrentProvenance,
    isSearching: mockIsSearching,
    searchError: mockSearchError,
  }),
}))

// Stub pinia storeToRefs to return our mock refs directly
vi.mock('pinia', () => ({
  storeToRefs: (store: Record<string, unknown>) => ({
    currentVerdict: store.currentVerdict,
    currentProvenance: store.currentProvenance,
    isSearching: store.isSearching,
    searchError: store.searchError,
  }),
}))

// --- Mock router/route ---
const mockPush = vi.fn()
vi.stubGlobal('useRoute', () => ({
  params: { taxNumber: '12345678' },
}))
vi.stubGlobal('useRouter', () => ({
  push: mockPush,
}))

// Stub useI18n
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key,
}))

// --- Test data builders ---
function buildVerdict(overrides: Partial<VerdictResponse> = {}): VerdictResponse {
  return {
    id: 'verdict-uuid-1',
    snapshotId: 'snapshot-uuid-1',
    taxNumber: '12345678',
    status: 'RELIABLE',
    confidence: 'FRESH',
    createdAt: '2026-03-13T12:00:00Z',
    riskSignals: [],
    cached: false,
    companyName: 'Test Company Kft.',
    sha256Hash: 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1',
    ...overrides,
  }
}

function mountPage() {
  return mount(VerdictDetailPage, {
    global: {
      stubs: {
        ScreeningVerdictCard: ScreeningVerdictCardStub,
        ScreeningProvenanceSidebar: ScreeningProvenanceSidebarStub,
        ScreeningSkeletonVerdictCard: ScreeningSkeletonVerdictCardStub,
      },
    },
  })
}

describe('Verdict Detail Page — layout', () => {
  let addEventSpy: ReturnType<typeof vi.spyOn>
  let removeEventSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mockCurrentVerdict.value = null
    mockCurrentProvenance.value = null
    mockIsSearching.value = false
    mockSearchError.value = null
    mockSearch.mockReset()
    mockPush.mockReset()
    addEventSpy = vi.spyOn(window, 'addEventListener')
    removeEventSpy = vi.spyOn(window, 'removeEventListener')
  })

  afterEach(() => {
    addEventSpy.mockRestore()
    removeEventSpy.mockRestore()
  })

  it('should show desktop two-column layout when window width >= 768px', async () => {
    // Simulate desktop viewport
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    mockCurrentVerdict.value = buildVerdict()

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.find('[data-testid="desktop-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mobile-layout"]').exists()).toBe(false)
  })

  it('should show mobile single-column layout when window width < 768px', async () => {
    // Simulate mobile viewport
    Object.defineProperty(window, 'innerWidth', { value: 375, writable: true })
    mockCurrentVerdict.value = buildVerdict()

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.find('[data-testid="mobile-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="desktop-layout"]').exists()).toBe(false)
  })

  it('should show liability disclaimer when verdict is loaded', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    mockCurrentVerdict.value = buildVerdict()

    const wrapper = mountPage()
    await nextTick()

    const disclaimer = wrapper.find('[data-testid="liability-disclaimer"]')
    expect(disclaimer.exists()).toBe(true)
    expect(disclaimer.text()).toContain('screening.disclaimer.text')
  })

  it('should show "not found" state when no verdict is available', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    mockCurrentVerdict.value = null
    mockIsSearching.value = false

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.find('[data-testid="verdict-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="verdict-detail-layout"]').exists()).toBe(false)
  })

  it('should show search error state when searchError is set', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    mockCurrentVerdict.value = null
    mockIsSearching.value = false
    mockSearchError.value = 'Network error: connection refused'

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.find('[data-testid="verdict-search-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="verdict-not-found"]').exists()).toBe(false)
    expect(wrapper.html()).toContain('Network error: connection refused')
    expect(wrapper.html()).toContain('screening.verdict.searchFailed')
  })

  it('should show skeleton loading state when searching', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    mockIsSearching.value = true

    const wrapper = mountPage()
    await nextTick()

    expect(wrapper.find('[data-testid="stub-skeleton-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="verdict-detail-layout"]').exists()).toBe(false)
  })
})

describe('Verdict Detail Page — navigation', () => {
  beforeEach(() => {
    mockCurrentVerdict.value = buildVerdict()
    mockIsSearching.value = false
    mockSearchError.value = null
    mockSearch.mockReset()
    mockPush.mockReset()
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
  })

  it('should navigate back to screening when back button is clicked', async () => {
    const wrapper = mountPage()
    await nextTick()

    await wrapper.find('[data-testid="back-to-dashboard"]').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/screening')
  })

  it('should have back-to-dashboard button with correct label', async () => {
    const wrapper = mountPage()
    await nextTick()

    const backBtn = wrapper.find('[data-testid="back-to-dashboard"]')
    expect(backBtn.exists()).toBe(true)
    expect(backBtn.text()).toContain('screening.actions.backToDashboard')
  })
})

describe('Verdict Detail Page — resize listener cleanup', () => {
  let addEventSpy: ReturnType<typeof vi.spyOn>
  let removeEventSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mockCurrentVerdict.value = buildVerdict()
    mockIsSearching.value = false
    mockSearchError.value = null
    mockSearch.mockReset()
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
    addEventSpy = vi.spyOn(window, 'addEventListener')
    removeEventSpy = vi.spyOn(window, 'removeEventListener')
  })

  afterEach(() => {
    addEventSpy.mockRestore()
    removeEventSpy.mockRestore()
  })

  it('should add resize listener on mount', async () => {
    mountPage()
    await nextTick()

    expect(addEventSpy).toHaveBeenCalledWith('resize', expect.any(Function))
  })

  it('should remove resize listener on unmount to avoid memory leak', async () => {
    const wrapper = mountPage()
    await nextTick()

    wrapper.unmount()

    expect(removeEventSpy).toHaveBeenCalledWith('resize', expect.any(Function))
  })
})

describe('Verdict Detail Page — data fetching', () => {
  beforeEach(() => {
    mockCurrentVerdict.value = null
    mockIsSearching.value = false
    mockSearchError.value = null
    mockSearch.mockReset()
    Object.defineProperty(window, 'innerWidth', { value: 1024, writable: true })
  })

  it('should trigger search if no verdict in store (direct navigation)', async () => {
    mountPage()
    await nextTick()

    expect(mockSearch).toHaveBeenCalledWith('12345678')
  })

  it('should NOT trigger search if verdict already matches tax number', async () => {
    mockCurrentVerdict.value = buildVerdict({ taxNumber: '12345678' })

    mountPage()
    await nextTick()

    expect(mockSearch).not.toHaveBeenCalled()
  })
})
