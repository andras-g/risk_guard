import { TIER_ORDER } from '~/composables/auth/useTierGate'
import type { Tier } from '~/composables/auth/useTierGate'

/**
 * Named route middleware for tier-based page gating.
 * Not global — only applied to pages that declare: definePageMeta({ middleware: 'tier', requiredTier: 'PRO' })
 *
 * Does NOT redirect. Instead, sets a reactive flag that the page layout
 * reads to render TierUpgradePrompt in place of locked content.
 */
export default defineNuxtRouteMiddleware((to) => {
  const requiredTier = to.meta.requiredTier as Tier | undefined
  if (!requiredTier) return

  const authStore = useAuthStore()
  const userTier = authStore.tier

  const userOrdinal = userTier ? (TIER_ORDER[userTier as Tier] ?? -1) : -1
  const requiredOrdinal = TIER_ORDER[requiredTier] ?? 0

  if (userOrdinal < requiredOrdinal) {
    // Set a reactive flag on route meta for layout to read
    to.meta.tierDenied = true
    to.meta.tierRequired = requiredTier
  }
})
