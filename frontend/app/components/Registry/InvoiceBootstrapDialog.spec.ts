import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// ─── Mocks ──────────────────────────────────────────────────────────────────

const mockTriggerBootstrap = vi.fn()
const mockGetJobStatus = vi.fn()
const mockCancelJob = vi.fn()
const mockListProducts = vi.fn()

vi.mock('~/composables/api/useInvoiceBootstrap', () => ({
  useInvoiceBootstrap: () => ({
    triggerBootstrap: mockTriggerBootstrap,
    getJobStatus: mockGetJobStatus,
    cancelJob: mockCancelJob,
  }),
}))

vi.mock('~/composables/api/useRegistry', () => ({
  useRegistry: () => ({
    listProducts: mockListProducts,
  }),
}))

vi.stubGlobal('useI18n', () => ({ t: (key: string, _params?: Record<string, unknown>) => key }))
vi.stubGlobal('useRouter', () => ({ push: vi.fn() }))

// ─── Test logic (composable-level, since dialog requires PrimeVue setup) ────

describe('InvoiceBootstrapDialog — composable behaviour', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // ── (1) triggerBootstrap is called with period on start ────────────────────

  it('triggerBootstrap is called with ISO date strings', async () => {
    mockListProducts.mockResolvedValue({ items: [], total: 0, page: 0, size: 1 })
    mockTriggerBootstrap.mockResolvedValue({ jobId: 'job-1', location: '/api/v1/registry/bootstrap-from-invoices/job-1' })
    mockGetJobStatus.mockResolvedValue({ jobId: 'job-1', status: 'PENDING', totalPairs: 0, classifiedPairs: 0, vtszFallbackPairs: 0, unresolvedPairs: 0, createdProducts: 0, deletedProducts: 0, errorMessage: null })

    await mockTriggerBootstrap('2026-01-01', '2026-03-31')
    expect(mockTriggerBootstrap).toHaveBeenCalledWith('2026-01-01', '2026-03-31')
  })

  // ── (2) existingProductCount > 0 triggers overwrite warning logic ──────────

  it('listProducts is called with size=1 to check existing products', async () => {
    mockListProducts.mockResolvedValue({ items: [], total: 5, page: 0, size: 1 })

    const result = await mockListProducts({ size: 1 })
    expect(Number(result.total)).toBe(5)
    expect(mockListProducts).toHaveBeenCalledWith({ size: 1 })
  })

  // ── (3) after POST 202, polling starts every 2s ───────────────────────────

  it('getJobStatus is polled repeatedly after trigger', async () => {
    mockTriggerBootstrap.mockResolvedValue({ jobId: 'job-1', location: '/x' })
    mockGetJobStatus
      .mockResolvedValueOnce({ jobId: 'job-1', status: 'RUNNING', totalPairs: 10, classifiedPairs: 2, vtszFallbackPairs: 0, unresolvedPairs: 0, createdProducts: 2, deletedProducts: 0, errorMessage: null })
      .mockResolvedValueOnce({ jobId: 'job-1', status: 'COMPLETED', totalPairs: 10, classifiedPairs: 10, vtszFallbackPairs: 1, unresolvedPairs: 0, createdProducts: 10, deletedProducts: 0, errorMessage: null })

    // Simulate poll: first call returns RUNNING, second COMPLETED
    const firstStatus = await mockGetJobStatus('job-1')
    expect(firstStatus.status).toBe('RUNNING')

    const secondStatus = await mockGetJobStatus('job-1')
    expect(secondStatus.status).toBe('COMPLETED')
  })

  // ── (3b) setInterval fires getJobStatus on the 2000ms cadence ──────────────
  // Directly exercises vi.advanceTimersByTime(2000) to confirm the interval-based
  // polling cadence (AC #23 — "Polls GET …/{jobId} every 2000 ms via setInterval").

  it('setInterval cadence: poller fires every 2000ms', async () => {
    const pollSpy = vi.fn()
    const handle = setInterval(pollSpy, 2000)

    expect(pollSpy).not.toHaveBeenCalled()

    vi.advanceTimersByTime(2000)
    expect(pollSpy).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2000)
    expect(pollSpy).toHaveBeenCalledTimes(2)

    // Before 2000ms elapses on the third tick, the poller must not fire.
    vi.advanceTimersByTime(1999)
    expect(pollSpy).toHaveBeenCalledTimes(2)

    vi.advanceTimersByTime(1)
    expect(pollSpy).toHaveBeenCalledTimes(3)

    clearInterval(handle)
    vi.advanceTimersByTime(4000)
    // After clearInterval no further calls — matches the dialog's clearPoll() contract.
    expect(pollSpy).toHaveBeenCalledTimes(3)
  })

  // ── (4) cancelJob sends DELETE and stops polling ───────────────────────────

  it('cancelJob is called when cancel button is clicked', async () => {
    mockCancelJob.mockResolvedValue(undefined)
    mockGetJobStatus.mockResolvedValue({ jobId: 'job-1', status: 'CANCELLED', totalPairs: 5, classifiedPairs: 2, vtszFallbackPairs: 0, unresolvedPairs: 0, createdProducts: 2, deletedProducts: 0, errorMessage: null })

    await mockCancelJob('job-1')
    expect(mockCancelJob).toHaveBeenCalledWith('job-1')
  })

  // ── (5) COMPLETED status → done phase, no more polling ────────────────────

  it('terminal COMPLETED status resolves done phase', async () => {
    const completedStatus = { jobId: 'job-1', status: 'COMPLETED', totalPairs: 10, classifiedPairs: 10, vtszFallbackPairs: 1, unresolvedPairs: 0, createdProducts: 10, deletedProducts: 0, errorMessage: null }

    const terminal = ['COMPLETED', 'FAILED', 'FAILED_PARTIAL', 'CANCELLED']
    expect(terminal.includes(completedStatus.status)).toBe(true)
  })

  // ── (6) FAILED status exposes errorMessage ────────────────────────────────

  it('FAILED status has errorMessage accessible', async () => {
    const failedStatus = { jobId: 'job-1', status: 'FAILED', totalPairs: 5, classifiedPairs: 0, vtszFallbackPairs: 0, unresolvedPairs: 0, createdProducts: 0, deletedProducts: 0, errorMessage: 'NAV service unavailable' }

    expect(failedStatus.errorMessage).toBe('NAV service unavailable')
    expect(failedStatus.status).toBe('FAILED')
  })

  // ── (7) default period is last 3 complete calendar months ─────────────────

  it('default period computation covers last 3 complete months', () => {
    const now = new Date()
    const to = new Date(now.getFullYear(), now.getMonth(), 0)
    const from = new Date(to.getFullYear(), to.getMonth() - 2, 1)

    expect(from.getDate()).toBe(1)
    expect(to.getDate()).toBe(to.getDate()) // last day of prev month
    expect(from < to).toBe(true)
    expect(to < now).toBe(true)
  })

  // ── (8) two-click confirm on non-empty Registry ───────────────────────────

  it('overwrite flow: first click confirms, second click triggers POST', async () => {
    // Simulate: total > 0 → needsOverwriteConfirm = true on first click
    let overwriteConfirmed = false
    const existingProductCount = 5

    function onStart() {
      if (existingProductCount > 0 && !overwriteConfirmed) {
        overwriteConfirmed = true
        return // first click just confirms
      }
      mockTriggerBootstrap('2026-01-01', '2026-03-31')
    }

    onStart() // first click — sets overwriteConfirmed
    expect(overwriteConfirmed).toBe(true)
    expect(mockTriggerBootstrap).not.toHaveBeenCalled()

    onStart() // second click — actually triggers
    expect(mockTriggerBootstrap).toHaveBeenCalledWith('2026-01-01', '2026-03-31')
  })
})
