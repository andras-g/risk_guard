import { ref } from 'vue'
import type { ProvenanceLine } from '~/types/epr'
import { useApi } from '~/composables/api/useApi'

export function useEprFilingProvenance() {
  const { apiFetch } = useApi()
  const rows = ref<ProvenanceLine[]>([])
  const totalElements = ref(0)
  const isLoading = ref(false)
  const isCsvExporting = ref(false)

  async function fetch(from: string, to: string, page: number, size: number): Promise<void> {
    isLoading.value = true
    try {
      const data = await apiFetch<{ content: ProvenanceLine[]; totalElements: number }>(
        `/api/v1/epr/filing/aggregation/provenance?from=${from}&to=${to}&page=${page}&size=${size}`,
      )
      rows.value = data.content
      totalElements.value = data.totalElements
    }
    catch (err) {
      console.error('[useEprFilingProvenance] Failed to fetch provenance', err)
    }
    finally {
      isLoading.value = false
    }
  }

  async function exportCsv(from: string, to: string): Promise<void> {
    isCsvExporting.value = true
    try {
      const config = useRuntimeConfig()
      const blob = await $fetch<Blob>(
        `/api/v1/epr/filing/aggregation/provenance.csv?from=${from}&to=${to}`,
        {
          responseType: 'blob',
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        },
      )
      if (import.meta.client) {
        const url = URL.createObjectURL(blob)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = `provenance-${from}-${to}.csv`
        document.body.appendChild(anchor)
        anchor.click()
        document.body.removeChild(anchor)
        setTimeout(() => URL.revokeObjectURL(url), 100)
      }
    }
    catch (err) {
      console.error('[useEprFilingProvenance] CSV export failed', err)
    }
    finally {
      isCsvExporting.value = false
    }
  }

  function invalidate(): void {
    rows.value = []
    totalElements.value = 0
  }

  return { rows, totalElements, isLoading, isCsvExporting, fetch, exportCsv, invalidate }
}
