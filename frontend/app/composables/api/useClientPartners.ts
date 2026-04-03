import type { WatchlistEntryResponse } from '~/types/api'

/**
 * Composable for fetching a client tenant's watchlisted partners (read-only cross-tenant view).
 *
 * Calls GET /api/v1/portfolio/clients/{clientTenantId}/partners.
 * Sets error to 'forbidden' on 403, 'unknown' on other errors.
 */
export function useClientPartners() {
  const partners = ref<WatchlistEntryResponse[]>([])
  const isLoading = ref(false)
  const error = ref<'forbidden' | 'unknown' | null>(null)

  async function fetchClientPartners(clientTenantId: string): Promise<void> {
    isLoading.value = true
    error.value = null
    partners.value = []
    try {
      const config = useRuntimeConfig()
      const data = await $fetch<WatchlistEntryResponse[]>(
        `/api/v1/portfolio/clients/${clientTenantId}/partners`,
        { baseURL: config.public.apiBase as string, credentials: 'include' },
      )
      partners.value = data
    }
    catch (err: unknown) {
      const fetchErr = err as { status?: number; statusCode?: number }
      const status = fetchErr.status ?? fetchErr.statusCode
      if (status === 403) {
        error.value = 'forbidden'
      }
      else {
        error.value = 'unknown'
      }
    }
    finally {
      isLoading.value = false
    }
  }

  return { partners, isLoading, error, fetchClientPartners }
}
