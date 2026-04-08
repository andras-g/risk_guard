import type { jsPDF } from 'jspdf'

/**
 * Register the Inter font with a jsPDF document instance for proper Unicode
 * (Hungarian, etc.) character rendering. Loads Inter-Regular.ttf and
 * Inter-Bold.ttf from /fonts/ on demand and caches the ArrayBuffers.
 *
 * Must be called BEFORE any doc.text() calls.
 */

let fontCache: { regular: ArrayBuffer, bold: ArrayBuffer } | null = null

export async function registerInterFont(doc: jsPDF): Promise<void> {
  if (!fontCache) {
    const [regular, bold] = await Promise.all([
      fetch('/fonts/Inter-Regular.ttf').then(r => r.arrayBuffer()),
      fetch('/fonts/Inter-Bold.ttf').then(r => r.arrayBuffer()),
    ])
    fontCache = { regular, bold }
  }

  const regularFileName = 'Inter-Regular.ttf'
  const boldFileName = 'Inter-Bold.ttf'

  doc.addFileToVFS(regularFileName, arrayBufferToBase64(fontCache.regular))
  doc.addFont(regularFileName, 'Inter', 'normal')

  doc.addFileToVFS(boldFileName, arrayBufferToBase64(fontCache.bold))
  doc.addFont(boldFileName, 'Inter', 'bold')

  doc.setFont('Inter', 'normal')
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  const CHUNK = 8192
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return btoa(binary)
}
