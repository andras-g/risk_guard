import { ref } from 'vue'
import type { EprSubmissionSummary } from '~/types/epr'
import { useApi } from '~/composables/api/useApi'

export function useEprSubmissions() {
  const { apiFetch } = useApi()
  const rows = ref<EprSubmissionSummary[]>([])
  const totalElements = ref(0)
  const isLoading = ref(false)
  const isDownloading = ref(false)

  async function fetch(page: number, size: number): Promise<void> {
    // Coalesce concurrent fetches — a second call while the first is in-flight is a no-op.
    // Prevents out-of-order response overwrites and duplicate requests from rapid paging.
    if (isLoading.value) return
    isLoading.value = true
    try {
      const data = await apiFetch<{ content: EprSubmissionSummary[]; totalElements: number }>(
        `/api/v1/epr/submissions?page=${page}&size=${size}`,
      )
      rows.value = data.content
      totalElements.value = data.totalElements
    }
    catch (err) {
      console.error('[useEprSubmissions] Failed to fetch submissions', err)
    }
    finally {
      isLoading.value = false
    }
  }

  async function downloadXml(id: string, fileName: string | null): Promise<void> {
    // Guard against double-click: skip if a download is already in flight.
    if (isDownloading.value) return
    isDownloading.value = true
    try {
      const blob = await apiFetch<Blob>(
        `/api/v1/epr/submissions/${id}/download`,
        { responseType: 'blob' },
      )
      if (import.meta.client) {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = fileName ?? `okirkapu-${id}.xml`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        setTimeout(() => URL.revokeObjectURL(url), 100)
      }
    }
    catch (err) {
      console.error('[useEprSubmissions] downloadXml failed', err)
    }
    finally {
      isDownloading.value = false
    }
  }

  function invalidate(): void {
    rows.value = []
    totalElements.value = 0
  }

  return { rows, totalElements, isLoading, isDownloading, fetch, downloadXml, invalidate }
}
