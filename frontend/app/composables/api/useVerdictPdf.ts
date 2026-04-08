import { useToast } from 'primevue/usetoast'
import type { VerdictResponse, SnapshotProvenanceResponse } from '~/types/api'
import { registerInterFont } from '~/composables/formatting/usePdfFont'

const HASH_UNAVAILABLE_SENTINEL = 'HASH_UNAVAILABLE'

export function useVerdictPdf() {
  const isGenerating = ref(false)
  const toast = useToast()

  async function exportVerdict(
    verdict: VerdictResponse,
    provenance: SnapshotProvenanceResponse | null,
    t: (key: string, params?: Record<string, unknown>) => string,
  ): Promise<void> {
    isGenerating.value = true
    try {
      const file = await buildPdf(verdict, provenance, t)
      await dispatch(file, verdict.taxNumber, t)
    }
    catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      toast.add({
        severity: 'error',
        summary: t('screening.actions.exportPdfError', { message }),
        life: 5000,
      })
    }
    finally {
      isGenerating.value = false
    }
  }

  return { isGenerating, exportVerdict }
}

async function buildPdf(
  verdict: VerdictResponse,
  provenance: SnapshotProvenanceResponse | null,
  t: (key: string, params?: Record<string, unknown>) => string,
): Promise<File> {
  const { default: jsPDF } = await import('jspdf')
  const doc = new jsPDF({ orientation: 'portrait' })
  await registerInterFont(doc)
  const today = new Date().toISOString().slice(0, 10)

  const pageHeight = doc.internal.pageSize.height
  const contentBottom = pageHeight - 25

  function checkPageBreak(currentY: number, needed: number = 7): number {
    if (currentY + needed > contentBottom) {
      doc.addPage()
      return 20
    }
    return currentY
  }

  // ─── Header ───────────────────────────────────────────────────────────────
  doc.setFontSize(14)
  doc.text(`RiskGuard \u2014 ${t('screening.pdf.reportTitle')}`, 14, 15)
  doc.setFontSize(10)
  doc.text(`${t('screening.pdf.generated')}: ${today}`, 14, 22)

  // ─── Company Block ────────────────────────────────────────────────────────
  let y = 32
  doc.setFontSize(10)
  doc.text(`${t('screening.verdict.companyLabel')}: ${verdict.companyName ?? '\u2014'}`, 14, y)
  y += 7
  doc.text(`${t('screening.verdict.taxNumber')}: ${verdict.taxNumber}`, 14, y)
  y += 10

  // ─── Verdict Status Block ─────────────────────────────────────────────────
  doc.setFontSize(10)
  doc.text(`${t('screening.verdict.status')}: ${verdictStatusLabel(verdict.status, t)}`, 14, y)
  y += 10

  // ─── Risk Signals Block ───────────────────────────────────────────────────
  doc.setFontSize(10)
  y = checkPageBreak(y)
  doc.text(`${t('screening.riskSignals.title')}:`, 14, y)
  y += 7
  doc.setFontSize(8)
  if (verdict.riskSignals && verdict.riskSignals.length > 0) {
    for (const signal of verdict.riskSignals) {
      y = checkPageBreak(y)
      doc.text(`\u2022 ${riskSignalLabel(signal, t)}`, 18, y)
      y += 6
    }
  }
  else {
    doc.text(t('screening.riskSignals.noSignals'), 18, y)
    y += 6
  }
  y += 4

  // ─── Data Sources Block ───────────────────────────────────────────────────
  if (provenance && provenance.sources && provenance.sources.length > 0) {
    doc.setFontSize(10)
    y = checkPageBreak(y)
    doc.text(`${t('screening.provenance.title')}:`, 14, y)
    y += 7
    doc.setFontSize(8)
    for (const source of provenance.sources) {
      y = checkPageBreak(y)
      const status = source.available
        ? t('screening.provenance.sourceAvailable')
        : t('screening.provenance.sourceUnavailable')
      const checked = source.checkedAt ?? '\u2014'
      doc.text(`\u2022 ${source.sourceName}: ${status} (${checked})`, 18, y)
      y += 6
    }
    y += 4
  }

  // ─── SHA-256 Block ────────────────────────────────────────────────────────
  doc.setFontSize(10)
  y = checkPageBreak(y)
  doc.text(`SHA-256: ${sha256Display(verdict.sha256Hash)}`, 14, y)

  // ─── Footer Disclaimer ────────────────────────────────────────────────────
  const disclaimerText = t('screening.disclaimer.text')
  const pageCount: number = (doc as any).internal.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(8)
    const lines = doc.splitTextToSize(disclaimerText, 182) as string[]
    doc.text(lines, 14, doc.internal.pageSize.height - 15)
  }

  const filename = `riskguard-atvilágítás-${verdict.taxNumber}-${today}.pdf`
  const blob = doc.output('blob')
  return new File([blob], filename, { type: 'application/pdf' })
}

function sha256Display(hash: string | null): string {
  if (!hash || hash === HASH_UNAVAILABLE_SENTINEL) return 'N/A'
  return hash
}

function verdictStatusLabel(
  status: string,
  t: (key: string) => string,
): string {
  switch (status) {
    case 'RELIABLE': return t('screening.verdict.reliable')
    case 'AT_RISK': return t('screening.verdict.atRisk')
    case 'TAX_SUSPENDED': return t('screening.verdict.taxSuspended')
    case 'INCOMPLETE': return t('screening.verdict.incomplete')
    case 'UNAVAILABLE': return t('screening.verdict.unavailable')
    default: return status
  }
}

function riskSignalLabel(
  signal: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (signal.startsWith('SOURCE_UNAVAILABLE:')) {
    const name = signal.split(':')[1] ?? ''
    return t('screening.riskSignals.SOURCE_UNAVAILABLE', { name })
  }
  return t(`screening.riskSignals.${signal}`)
}

async function dispatch(
  file: File,
  taxNumber: string,
  t: (key: string) => string,
): Promise<void> {
  if (
    typeof navigator !== 'undefined'
    && typeof navigator.share === 'function'
    && typeof navigator.canShare === 'function'
    && navigator.canShare({ files: [file] })
  ) {
    try {
      await navigator.share({
        files: [file],
        title: `RiskGuard \u2014 ${t('screening.pdf.reportTitle')}`,
        text: taxNumber,
      })
      return
    }
    catch (err) {
      if (err instanceof Error && err.name === 'AbortError') return
      throw err
    }
  }

  // Fallback: programmatic download
  const url = URL.createObjectURL(file)
  const a = document.createElement('a')
  a.href = url
  a.download = file.name
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
