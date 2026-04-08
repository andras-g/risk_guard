import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useVerdictPdf } from './useVerdictPdf'
import type { VerdictResponse, SnapshotProvenanceResponse } from '~/types/api'

/**
 * Unit tests for useVerdictPdf composable.
 * Tests: filename format, sha256 hash inclusion/fallback, disclaimer inclusion,
 * error toast on failure.
 * Co-located per architecture rules.
 */

// ─── PrimeVue useToast mock ────────────────────────────────────────────────────

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

// ─── jsPDF mock ────────────────────────────────────────────────────────────────

let capturedTexts: Array<{ text: string | string[], x: number, y: number }> = []

const mockDoc = {
  setFontSize: vi.fn(),
  setFont: vi.fn(),
  addFileToVFS: vi.fn(),
  addFont: vi.fn(),
  text: vi.fn((text: string | string[], x: number, y: number) => {
    capturedTexts.push({ text, x, y })
  }),
  splitTextToSize: vi.fn((text: string) => [text]),
  output: vi.fn(() => new Blob(['%PDF-mock'])),
  setPage: vi.fn(),
  addPage: vi.fn(),
  internal: {
    getNumberOfPages: vi.fn(() => 1),
    pageSize: { height: 297 },
  },
}

vi.mock('jspdf', () => ({
  default: vi.fn(() => mockDoc),
}))

vi.mock('~/composables/formatting/usePdfFont', () => ({
  registerInterFont: vi.fn().mockResolvedValue(undefined),
}))

// ─── Navigator mock ─────────────────────────────────────────────────────────────

Object.defineProperty(globalThis, 'navigator', {
  value: {
    canShare: undefined,
  },
  writable: true,
})

// ─── URL / document mocks ──────────────────────────────────────────────────────

const mockCreateObjectURL = vi.fn(() => 'blob:mock-url')
const mockRevokeObjectURL = vi.fn()
Object.defineProperty(globalThis, 'URL', {
  value: {
    createObjectURL: mockCreateObjectURL,
    revokeObjectURL: mockRevokeObjectURL,
  },
  writable: true,
})

const mockClick = vi.fn()
const mockAnchor = {
  href: '',
  download: '',
  click: mockClick,
}
vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
  if (tag === 'a') return mockAnchor as unknown as HTMLElement
  return document.createElement(tag)
})
vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockAnchor as unknown as Node)
vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockAnchor as unknown as Node)

// ─── Helpers ──────────────────────────────────────────────────────────────────

function t(key: string, params?: Record<string, unknown>): string {
  const map: Record<string, string> = {
    'screening.pdf.reportTitle': 'Screening Report',
    'screening.pdf.generated': 'Generated',
    'screening.verdict.companyLabel': 'Company Name',
    'screening.verdict.taxNumber': 'Tax Number',
    'screening.verdict.status': 'Status',
    'screening.verdict.reliable': 'Reliable',
    'screening.verdict.atRisk': 'At Risk',
    'screening.verdict.taxSuspended': 'Tax Suspended',
    'screening.verdict.incomplete': 'Incomplete',
    'screening.verdict.unavailable': 'Unavailable',
    'screening.riskSignals.title': 'Risk Signals',
    'screening.riskSignals.noSignals': 'No risk signals detected',
    'screening.provenance.title': 'Data Sources',
    'screening.provenance.sourceAvailable': 'Available',
    'screening.provenance.sourceUnavailable': 'Unavailable',
    'screening.disclaimer.text': 'This is the disclaimer text for audit purposes.',
    'screening.actions.exportPdfError': 'PDF generation failed: {message}',
  }
  let result = map[key] ?? key
  if (params) {
    result = result.replace(/{(\w+)}/g, (_, k) => String(params[k] ?? k))
  }
  return result
}

function buildVerdict(overrides: Partial<VerdictResponse> = {}): VerdictResponse {
  return {
    id: 'verdict-1',
    snapshotId: 'snapshot-1',
    taxNumber: '12345678',
    status: 'RELIABLE',
    confidence: 'FRESH',
    createdAt: '2026-04-03T10:00:00Z',
    riskSignals: [],
    cached: false,
    companyName: 'Test Kft.',
    sha256Hash: 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1',
    ...overrides,
  }
}

function buildProvenance(overrides: Partial<SnapshotProvenanceResponse> = {}): SnapshotProvenanceResponse {
  return {
    snapshotId: 'snapshot-1',
    taxNumber: '12345678',
    checkedAt: '2026-04-03T10:00:00Z',
    sources: [
      { sourceName: 'nav-debt', available: true, checkedAt: '2026-04-03T10:00:00Z', sourceUrl: null },
    ],
    ...overrides,
  }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  capturedTexts = []
  vi.clearAllMocks()
  mockDoc.output.mockReturnValue(new Blob(['%PDF-mock']))
  mockDoc.splitTextToSize.mockImplementation((text: string) => [text])
  mockDoc.internal.getNumberOfPages.mockReturnValue(1)
})

describe('useVerdictPdf — filename format', () => {
  it('generates correct filename with taxNumber and date', async () => {
    const { exportVerdict } = useVerdictPdf()
    const verdict = buildVerdict({ taxNumber: '12345678' })

    await exportVerdict(verdict, null, t)

    const today = new Date().toISOString().slice(0, 10)
    expect(mockAnchor.download).toBe(`riskguard-atvilágítás-12345678-${today}.pdf`)
  })
})

describe('useVerdictPdf — sha256 hash handling', () => {
  it('includes real sha256 hash in PDF text', async () => {
    const hash = 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1'
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict({ sha256Hash: hash }), null, t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).toContain(hash)
  })

  it('falls back to "N/A" when sha256Hash is null', async () => {
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict({ sha256Hash: null }), null, t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).toContain('N/A')
    expect(allText).not.toContain('abc123')
  })

  it('falls back to "N/A" when sha256Hash is "HASH_UNAVAILABLE"', async () => {
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict({ sha256Hash: 'HASH_UNAVAILABLE' }), null, t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).toContain('N/A')
    expect(allText).not.toContain('HASH_UNAVAILABLE')
  })
})

describe('useVerdictPdf — disclaimer', () => {
  it('includes disclaimer text in PDF footer', async () => {
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict(), null, t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).toContain('This is the disclaimer text for audit purposes.')
  })
})

describe('useVerdictPdf — isGenerating state', () => {
  it('isGenerating is false before and after exportVerdict resolves', async () => {
    const { isGenerating, exportVerdict } = useVerdictPdf()
    expect(isGenerating.value).toBe(false)
    await exportVerdict(buildVerdict(), null, t)
    expect(isGenerating.value).toBe(false)
  })
})

describe('useVerdictPdf — error handling', () => {
  it('shows error toast when PDF generation fails', async () => {
    mockDoc.output.mockImplementationOnce(() => {
      throw new Error('jsPDF internal error')
    })
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict(), null, t)

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: expect.stringContaining('jsPDF internal error'),
      }),
    )
  })

  it('isGenerating resets to false after an error', async () => {
    mockDoc.output.mockImplementationOnce(() => {
      throw new Error('boom')
    })
    const { isGenerating, exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict(), null, t)
    expect(isGenerating.value).toBe(false)
  })
})

describe('useVerdictPdf — provenance data sources', () => {
  it('includes data source entries when provenance is provided', async () => {
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict(), buildProvenance(), t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).toContain('nav-debt')
  })

  it('skips data sources block when provenance is null', async () => {
    const { exportVerdict } = useVerdictPdf()
    await exportVerdict(buildVerdict(), null, t)

    const allText = capturedTexts.map(c => (Array.isArray(c.text) ? c.text.join('') : c.text)).join(' ')
    expect(allText).not.toContain('Data Sources')
  })
})
