import type { WatchlistEntryResponse } from '~/types/api'

export function useWatchlistPdfExport() {
  const isGenerating = ref(false)

  async function generateAndDispatch(entries: WatchlistEntryResponse[]) {
    isGenerating.value = true
    try {
      const blob = await buildPdf(entries)
      await dispatch(blob)
    }
    finally {
      isGenerating.value = false
    }
  }

  return { isGenerating, generateAndDispatch }
}

async function buildPdf(entries: WatchlistEntryResponse[]): Promise<Blob> {
  const { default: jsPDF } = await import('jspdf')
  await import('jspdf-autotable')

  const doc = new jsPDF({ orientation: 'landscape' })
  const today = new Date().toISOString().slice(0, 10)

  doc.setFontSize(14)
  doc.text('RiskGuard \u2014 Partner Due Diligence Report', 14, 15)
  doc.setFontSize(10)
  doc.text(`Generated: ${today}`, 14, 22)

  const rows = entries.map(e => [
    e.companyName ?? e.taxNumber ?? '—',
    e.taxNumber ?? '—',
    verdictLabel(e.currentVerdictStatus),
    e.lastCheckedAt ?? '\u2014',
    e.latestSha256Hash ?? 'N/A',
  ])

  ;(doc as any).autoTable({
    startY: 28,
    head: [['Company Name', 'Tax Number', 'Verdict Status', 'Last Checked', 'SHA-256 Hash']],
    body: rows,
    columnStyles: {
      4: { cellWidth: 60, overflow: 'ellipsize' },
    },
    styles: { fontSize: 8 },
  })

  const pageCount: number = (doc as any).internal.getNumberOfPages()
  const footerText = 'This report is for informational purposes only and does not constitute legal advice.'
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(8)
    doc.text(footerText, 14, doc.internal.pageSize.height - 10)
  }

  return doc.output('blob')
}

async function dispatch(blob: Blob): Promise<void> {
  const filename = `riskguard-watchlist-${new Date().toISOString().slice(0, 10)}.pdf`
  const file = new File([blob], filename, { type: 'application/pdf' })

  if (typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: 'RiskGuard Due Diligence Report', text: 'Partner status report' })
    }
    catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        // User dismissed the share sheet — not an error
        return
      }
      throw err
    }
  }
  else {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 10000)
  }
}

function verdictLabel(status: string | null): string {
  if (!status) return '\u2014'
  switch (status) {
    case 'RELIABLE': return 'Reliable'
    case 'AT_RISK': return 'At Risk'
    case 'TAX_SUSPENDED': return 'Tax Suspended'
    case 'INCOMPLETE': return 'Incomplete'
    case 'UNAVAILABLE': return 'Unavailable'
    default: return status
  }
}
