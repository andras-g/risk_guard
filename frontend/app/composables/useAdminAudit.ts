import type { AdminAuditPageResponse } from '~/types/api'

/**
 * Composable for the GDPR Admin Audit Search page (Story 6.4).
 * Provides cross-tenant paginated audit log search.
 */
export function useAdminAudit() {
  const config = useRuntimeConfig()
  const results = ref<AdminAuditPageResponse | null>(null)
  const pending = ref(false)
  const error = ref<string | null>(null)

  async function search(taxNumber: string | null, tenantId: string | null, page = 0, size = 20) {
    pending.value = true
    error.value = null
    try {
      const params: Record<string, string | number> = { page, size }
      if (taxNumber) params.taxNumber = taxNumber
      if (tenantId) params.tenantId = tenantId
      results.value = await $fetch<AdminAuditPageResponse>(
        `${config.public.apiBase}/api/v1/admin/screening/audit`,
        { params, credentials: 'include' },
      )
    }
    catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e)
    }
    finally {
      pending.value = false
    }
  }

  return { results, pending, error, search }
}
