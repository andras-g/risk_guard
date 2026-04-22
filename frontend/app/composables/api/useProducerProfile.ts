/**
 * Composable for the producer profile API endpoints.
 * Used in the settings page and by the export flow to check profile completeness.
 */

export interface ProducerProfileData {
  id?: string
  tenantId?: string
  legalName?: string
  addressCountryCode?: string
  addressCity?: string
  addressPostalCode?: string
  addressStreetName?: string
  addressStreetType?: string
  addressHouseNumber?: string
  kshStatisticalNumber?: string
  companyRegistrationNumber?: string
  contactName?: string
  contactTitle?: string
  contactCountryCode?: string
  contactPostalCode?: string
  contactCity?: string
  contactStreetName?: string
  contactPhone?: string
  contactEmail?: string
  okirClientId?: number | null
  isManufacturer?: boolean
  isIndividualPerformer?: boolean
  isSubcontractor?: boolean
  isConcessionaire?: boolean
  taxNumber?: string
}

export type EprScope = 'FIRST_PLACER' | 'RESELLER' | 'UNKNOWN'

export function useProducerProfile() {
  const profile = ref<ProducerProfileData | null>(null)
  const pending = ref(false)
  const saveError = ref<string | null>(null)
  const savedSuccessfully = ref(false)
  // Story 10.11 AC #19 — default epr_scope for the tenant
  const defaultEprScope = ref<EprScope>('UNKNOWN')

  async function fetchProfile(): Promise<void> {
    pending.value = true
    try {
      const config = useRuntimeConfig()
      const data = await $fetch<ProducerProfileData>('/api/v1/epr/producer-profile', {
        baseURL: config.public.apiBase as string,
        credentials: 'include',
      })
      profile.value = data ?? null
    }
    catch {
      profile.value = null
    }
    finally {
      pending.value = false
    }
  }

  async function saveProfile(data: ProducerProfileData): Promise<void> {
    pending.value = true
    saveError.value = null
    savedSuccessfully.value = false
    try {
      const config = useRuntimeConfig()
      const result = await $fetch<ProducerProfileData>('/api/v1/epr/producer-profile', {
        method: 'PUT',
        body: data,
        baseURL: config.public.apiBase as string,
        credentials: 'include',
      })
      profile.value = result
      savedSuccessfully.value = true
    }
    catch (err: unknown) {
      saveError.value = (err as { message?: string })?.message ?? 'Unknown error'
      throw err
    }
    finally {
      pending.value = false
    }
  }

  async function fetchDefaultScope(): Promise<void> {
    const config = useRuntimeConfig()
    try {
      const data = await $fetch<{ defaultScope: EprScope }>(
        '/api/v1/epr/producer-profile/default-epr-scope',
        { baseURL: config.public.apiBase as string, credentials: 'include' }
      )
      defaultEprScope.value = data?.defaultScope ?? 'UNKNOWN'
    }
    catch {
      defaultEprScope.value = 'UNKNOWN'
    }
  }

  async function updateDefaultScope(scope: EprScope): Promise<EprScope> {
    const config = useRuntimeConfig()
    const data = await $fetch<{ defaultScope: EprScope }>(
      '/api/v1/epr/producer-profile/default-epr-scope',
      {
        method: 'PATCH',
        body: { defaultScope: scope },
        baseURL: config.public.apiBase as string,
        credentials: 'include',
      }
    )
    defaultEprScope.value = data.defaultScope
    return data.defaultScope
  }

  return {
    profile,
    pending,
    saveError,
    savedSuccessfully,
    fetchProfile,
    saveProfile,
    defaultEprScope,
    fetchDefaultScope,
    updateDefaultScope,
  }
}
