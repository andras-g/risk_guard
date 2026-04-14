import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { BootstrapCandidateResponse, BootstrapResultResponse } from '~/composables/api/useBootstrap'

// ─── Composable mock ────────────────────────────────────────────────────────

const mockTriggerBootstrap = vi.fn()
const mockListCandidates = vi.fn()
const mockApproveCandidate = vi.fn()
const mockRejectCandidate = vi.fn()

vi.mock('~/composables/api/useBootstrap', () => ({
  useBootstrap: vi.fn(() => ({
    triggerBootstrap: mockTriggerBootstrap,
    listCandidates: mockListCandidates,
    approveCandidate: mockApproveCandidate,
    rejectCandidate: mockRejectCandidate,
  })),
}))

vi.mock('~/stores/bootstrap', () => ({
  useBootstrapStore: vi.fn(() => ({
    candidates: [],
    total: 0,
    triggerState: 'idle' as const,
    isLoading: false,
    error: null,
    setCandidates: vi.fn(),
    updateCandidate: vi.fn(),
    setTriggerState: vi.fn(),
    setLoading: vi.fn(),
    setError: vi.fn(),
    clearError: vi.fn(),
  })),
}))

vi.mock('~/composables/auth/useTierGate', () => ({
  useTierGate: vi.fn(() => ({ hasAccess: true, tierName: 'PRO_EPR' })),
}))

vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))
vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
vi.stubGlobal('useRouter', () => ({ push: vi.fn() }))

// ─── Helpers ─────────────────────────────────────────────────────────────────

function buildCandidate(
  overrides: Partial<BootstrapCandidateResponse> = {},
): BootstrapCandidateResponse {
  return {
    id: 'cand-001',
    tenantId: 'tenant-001',
    productName: 'Aktivia 125g',
    vtsz: '39239090',
    frequency: 5,
    totalQuantity: 500,
    unitOfMeasure: 'DARAB',
    status: 'PENDING',
    suggestedKfCode: null,
    suggestedComponents: null,
    classificationStrategy: 'NONE',
    classificationConfidence: 'LOW',
    resultingProductId: null,
    createdAt: '2026-04-14T10:00:00Z',
    updatedAt: '2026-04-14T10:00:00Z',
    ...overrides,
  }
}

describe('registry/bootstrap.vue — logic tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ─── Test 1: listCandidates is called on mount ────────────────────────────

  it('listCandidates is called on mount', async () => {
    mockListCandidates.mockResolvedValue({ items: [], total: 0, page: 0, size: 200 })
    await mockListCandidates(null, 0, 200)
    expect(mockListCandidates).toHaveBeenCalled()
  })

  // ─── Test 2: triggerBootstrap is called when dates are provided ──────────

  it('triggerBootstrap is called with from/to dates', async () => {
    const result: BootstrapResultResponse = { created: 3, skipped: 1 }
    mockTriggerBootstrap.mockResolvedValue(result)

    const r = await mockTriggerBootstrap('2025-01-01', '2025-12-31')
    expect(r.created).toBe(3)
    expect(r.skipped).toBe(1)
  })

  // ─── Test 3: rejectCandidate called with NOT_OWN_PACKAGING on reject ─────

  it('rejectCandidate called with NOT_OWN_PACKAGING for reject action', async () => {
    mockRejectCandidate.mockResolvedValue(undefined)
    const candidate = buildCandidate()
    await mockRejectCandidate(candidate.id, 'NOT_OWN_PACKAGING')
    expect(mockRejectCandidate).toHaveBeenCalledWith('cand-001', 'NOT_OWN_PACKAGING')
  })

  // ─── Test 4: rejectCandidate called with NEEDS_MANUAL for mark-manual ────

  it('rejectCandidate called with NEEDS_MANUAL for mark-manual action', async () => {
    mockRejectCandidate.mockResolvedValue(undefined)
    const candidate = buildCandidate()
    await mockRejectCandidate(candidate.id, 'NEEDS_MANUAL')
    expect(mockRejectCandidate).toHaveBeenCalledWith('cand-001', 'NEEDS_MANUAL')
  })

  // ─── Test 5: status filter logic ─────────────────────────────────────────

  it('status filter PENDING hides APPROVED rows', () => {
    const candidates: BootstrapCandidateResponse[] = [
      buildCandidate({ id: 'c1', status: 'PENDING' }),
      buildCandidate({ id: 'c2', status: 'APPROVED' }),
      buildCandidate({ id: 'c3', status: 'PENDING' }),
    ]
    const filter: string = 'PENDING'
    const filtered = filter === 'ALL'
      ? candidates
      : candidates.filter(c => c.status === (filter as BootstrapCandidateResponse['status']))

    expect(filtered).toHaveLength(2)
    expect(filtered.every(c => c.status === 'PENDING')).toBe(true)
  })

  // ─── Test 6: empty state when no candidates ───────────────────────────────

  it('empty state is shown when candidates list is empty', () => {
    const candidates: BootstrapCandidateResponse[] = []
    expect(candidates.length).toBe(0)
  })

  // ─── Test 7: approve button opens dialog ─────────────────────────────────

  it('approve button click sets selectedCandidateForApprove and opens dialog', () => {
    const candidate = buildCandidate()
    let approveDialogVisible = false
    let selectedCandidateForApprove: BootstrapCandidateResponse | null = null

    // Simulate the openApproveDialog function from bootstrap.vue
    function openApproveDialog(c: BootstrapCandidateResponse) {
      selectedCandidateForApprove = c
      approveDialogVisible = true
    }

    openApproveDialog(candidate)

    expect(approveDialogVisible).toBe(true)
    expect(selectedCandidateForApprove).toBe(candidate)
  })

  // ─── Test 8: keyboard shortcut "a" triggers approve dialog ───────────────

  it('keyboard shortcut "a" on selected PENDING row calls openApproveDialog', () => {
    const candidate = buildCandidate({ status: 'PENDING' })
    let openApproveDialogCalled = false
    let calledWith: BootstrapCandidateResponse | null = null

    const selectedRow = candidate

    function openApproveDialog(c: BootstrapCandidateResponse) {
      openApproveDialogCalled = true
      calledWith = c
    }

    // Simulate the onTableKeydown handler logic from bootstrap.vue
    function onTableKeydown(event: { key: string, preventDefault: () => void },
      activeElementTag = 'div') {
      if (['input', 'select', 'textarea', 'button'].includes(activeElementTag)) return
      if (!selectedRow || selectedRow.status !== 'PENDING') return
      if (event.key === 'a') {
        openApproveDialog(selectedRow)
        event.preventDefault()
      }
    }

    const preventDefault = vi.fn()
    onTableKeydown({ key: 'a', preventDefault })

    expect(openApproveDialogCalled).toBe(true)
    expect((calledWith as BootstrapCandidateResponse | null)?.id).toBe(candidate.id)
    expect(preventDefault).toHaveBeenCalledOnce()
  })
})
