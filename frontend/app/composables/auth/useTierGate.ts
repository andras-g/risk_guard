export type Tier = 'ALAP' | 'PRO' | 'PRO_EPR'

/** Tier hierarchy ordinal map. Shared by tier middleware and composables. */
export const TIER_ORDER: Record<Tier, number> = {
  ALAP: 0,
  PRO: 1,
  PRO_EPR: 2
}

/**
 * Composable for checking whether the current user's tier satisfies a required tier.
 * Mirrors the backend Tier.satisfies() ordinal comparison logic.
 * Uses the identity store as the authoritative source for tier data (decoded from JWT).
 *
 * @param requiredTier - The minimum tier required to access a feature.
 * @returns Reactive object with access status, current tier, required tier, and tier display name.
 */
export function useTierGate(requiredTier: Tier) {
  const { t } = useI18n()

  const currentTier = computed<Tier | null>(() => {
    // Auth store is populated by fetchMe() and setToken() — authoritative source for tier
    const authStore = useAuthStore()
    return (authStore.tier as Tier | null) ?? null
  })

  const hasAccess = computed(() => {
    const tier = currentTier.value
    if (!tier) return false // Fail-closed: no tier = no access
    return TIER_ORDER[tier] >= TIER_ORDER[requiredTier]
  })

  const tierName = computed(() => {
    return t(`common.tiers.${requiredTier}`)
  })

  return {
    hasAccess,
    currentTier,
    requiredTier,
    tierName
  }
}
