import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useInvoiceBootstrap } from './useInvoiceBootstrap'

const mockApiFetch = vi.fn()

vi.stubGlobal('useI18n', () => ({ locale: { value: 'hu' } }))
vi.stubGlobal('useRuntimeConfig', () => ({ public: { apiBase: '' } }))
vi.stubGlobal('$fetch', mockApiFetch)

describe('useInvoiceBootstrap', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('triggerBootstrap posts to bootstrap endpoint with period', async () => {
    mockApiFetch.mockResolvedValueOnce({ jobId: 'abc-123', location: '/api/v1/registry/bootstrap-from-invoices/abc-123' })
    const { triggerBootstrap } = useInvoiceBootstrap()

    const result = await triggerBootstrap('2026-01-01', '2026-03-31')

    expect(mockApiFetch).toHaveBeenCalledWith(
      expect.stringContaining('bootstrap-from-invoices'),
      expect.objectContaining({ method: 'POST', body: { periodFrom: '2026-01-01', periodTo: '2026-03-31' } }),
    )
    expect(result.jobId).toBe('abc-123')
  })

  it('getJobStatus fetches job status by id', async () => {
    mockApiFetch.mockResolvedValueOnce({ jobId: 'abc-123', status: 'RUNNING', totalPairs: 10, classifiedPairs: 5 })
    const { getJobStatus } = useInvoiceBootstrap()

    const result = await getJobStatus('abc-123')

    expect(mockApiFetch).toHaveBeenCalledWith(
      expect.stringContaining('abc-123'),
      expect.any(Object),
    )
    expect(result.status).toBe('RUNNING')
  })

  it('cancelJob sends DELETE request', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)
    const { cancelJob } = useInvoiceBootstrap()

    await cancelJob('abc-123')

    expect(mockApiFetch).toHaveBeenCalledWith(
      expect.stringContaining('abc-123'),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
