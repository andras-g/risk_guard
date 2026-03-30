import type {
  AuditHashVerifyResponse,
  AuditHistoryEntryResponse,
  AuditHistoryPageResponse,
} from '~/types/api'
import { useApi } from '~/composables/api/useApi'

export interface AuditHistoryFilters {
  startDate: string | null
  endDate: string | null
  taxNumber: string
  checkSource: 'MANUAL' | 'AUTOMATED' | null
}

/**
 * Composable for the Audit History page (Story 5.1a).
 * Provides server-side paginated, filtered audit trail data and hash verification.
 *
 * Usage:
 * ```ts
 * const audit = useAuditHistory()
 * await audit.fetchPage()
 * ```
 */
export function useAuditHistory() {
  const { apiFetch } = useApi()

  const entries = ref<AuditHistoryEntryResponse[]>([])
  const totalElements = ref(0)
  const page = ref(0)
  const pageSize = ref(20)
  const loading = ref(false)

  const filters = ref<AuditHistoryFilters>({
    startDate: null,
    endDate: null,
    taxNumber: '',
    checkSource: null,
  })

  async function fetchPage() {
    loading.value = true
    try {
      const params: Record<string, string | number> = {
        page: page.value,
        size: pageSize.value,
      }
      if (filters.value.startDate) params.startDate = filters.value.startDate
      if (filters.value.endDate) params.endDate = filters.value.endDate
      if (filters.value.taxNumber) params.taxNumber = filters.value.taxNumber
      if (filters.value.checkSource) params.checkSource = filters.value.checkSource

      const response = await apiFetch<AuditHistoryPageResponse>(
        '/api/v1/screening/audit-history',
        { params },
      )
      entries.value = response.content
      totalElements.value = response.totalElements
    }
    finally {
      loading.value = false
    }
  }

  async function verifyHash(auditId: string): Promise<AuditHashVerifyResponse> {
    return apiFetch<AuditHashVerifyResponse>(
      `/api/v1/screening/audit-history/${auditId}/verify-hash`,
    )
  }

  /**
   * Update a filter field and reset page to 0 so pagination restarts from the beginning.
   */
  function setFilter<K extends keyof AuditHistoryFilters>(key: K, value: AuditHistoryFilters[K]) {
    filters.value[key] = value
    page.value = 0
  }

  function resetFilters() {
    filters.value = { startDate: null, endDate: null, taxNumber: '', checkSource: null }
    page.value = 0
  }

  return {
    entries,
    totalElements,
    page,
    pageSize,
    loading,
    filters,
    fetchPage,
    verifyHash,
    setFilter,
    resetFilters,
  }
}
