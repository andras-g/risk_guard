import type { GuestSearchResponse } from '~/types/api'
import authConfig from '~/risk-guard-tokens.json'

const GUEST_TOKEN_KEY = 'rg_guest_token'

export interface GuestUsageStats {
  companiesUsed: number
  companiesLimit: number
  dailyChecksUsed: number
  dailyChecksLimit: number
}

export type GuestLimitError = 'COMPANY_LIMIT_REACHED' | 'DAILY_LIMIT_REACHED'

export interface GuestLimitResponse {
  error: GuestLimitError
  companiesUsed?: number
  companiesLimit?: number
  dailyChecksUsed?: number
  dailyChecksLimit?: number
}

// ─── Module-level singleton state ────────────────────────────────────────────
// Shared across all components that call useGuestSession() — ensures usage stats,
// limit errors, and search state stay in sync (e.g., SearchBar + header badge).
const _isSearching = ref(false)
const _searchError = ref<string | null>(null)
const _limitError = ref<GuestLimitError | null>(null)
const _currentVerdict = ref<GuestSearchResponse | null>(null)
const _usageStats = ref<GuestUsageStats>({
  companiesUsed: 0,
  companiesLimit: authConfig.guest.maxCompanies,
  dailyChecksUsed: 0,
  dailyChecksLimit: authConfig.guest.maxDailyChecks,
})

/**
 * Composable for managing guest (unauthenticated) search sessions.
 *
 * Uses module-level singleton state — all callers share the same reactive refs.
 *
 * Handles:
 * - Session fingerprint persistence in localStorage
 * - Guest search API calls
 * - Usage tracking (companies used, daily checks)
 * - Rate limit detection and messaging
 */
export function useGuestSession() {
  const config = useRuntimeConfig()

  /**
   * Get or create a guest session fingerprint.
   * Uses crypto.randomUUID() stored in localStorage as the guest token.
   * This is NOT browser fingerprinting — it's a simple random identifier
   * for session continuity.
   */
  function getSessionFingerprint(): string {
    if (import.meta.server) {
      // Server-side: return a placeholder (guest search is client-only)
      return 'ssr-placeholder'
    }

    let token = localStorage.getItem(GUEST_TOKEN_KEY)
    if (!token) {
      token = crypto.randomUUID()
      localStorage.setItem(GUEST_TOKEN_KEY, token)
    }
    return token
  }

  /**
   * Perform a guest search. Returns the verdict + usage stats on success,
   * or sets limitError/searchError on failure.
   */
  async function guestSearch(taxNumber: string): Promise<GuestSearchResponse | null> {
    _isSearching.value = true
    _searchError.value = null
    _limitError.value = null
    _currentVerdict.value = null

    const fingerprint = getSessionFingerprint()

    try {
      const data = await $fetch<GuestSearchResponse>('/api/v1/public/guest/search', {
        method: 'POST',
        body: {
          taxNumber,
          sessionFingerprint: fingerprint,
        },
        baseURL: config.public.apiBase as string,
      })

      _currentVerdict.value = data
      _usageStats.value = {
        companiesUsed: data.companiesUsed,
        companiesLimit: data.companiesLimit,
        dailyChecksUsed: data.dailyChecksUsed,
        dailyChecksLimit: data.dailyChecksLimit,
      }

      return data
    } catch (error: unknown) {
      // Check for 429 rate limit responses
      if (error && typeof error === 'object' && 'statusCode' in error) {
        const fetchError = error as { statusCode: number; data?: GuestLimitResponse }
        if (fetchError.statusCode === 429 && fetchError.data?.error) {
          _limitError.value = fetchError.data.error

          // Update usage stats from the error response
          if (fetchError.data.companiesUsed !== undefined) {
            _usageStats.value.companiesUsed = fetchError.data.companiesUsed
          }
          if (fetchError.data.companiesLimit !== undefined) {
            _usageStats.value.companiesLimit = fetchError.data.companiesLimit
          }
          if (fetchError.data.dailyChecksUsed !== undefined) {
            _usageStats.value.dailyChecksUsed = fetchError.data.dailyChecksUsed
          }
          if (fetchError.data.dailyChecksLimit !== undefined) {
            _usageStats.value.dailyChecksLimit = fetchError.data.dailyChecksLimit
          }
          return null
        }
      }

      _searchError.value = error instanceof Error ? error.message : String(error)
      return null
    } finally {
      _isSearching.value = false
    }
  }

  /**
   * Clear the guest session state (but keep the fingerprint for session continuity).
   */
  function clearGuestSearch() {
    _currentVerdict.value = null
    _searchError.value = null
    _limitError.value = null
  }

  /**
   * Whether the guest has remaining companies they can search.
   */
  const hasRemainingCompanies = computed(() =>
    _usageStats.value.companiesUsed < _usageStats.value.companiesLimit
  )

  /**
   * Whether the guest has remaining daily checks.
   */
  const hasRemainingDailyChecks = computed(() =>
    _usageStats.value.dailyChecksUsed < _usageStats.value.dailyChecksLimit
  )

  return {
    isSearching: readonly(_isSearching),
    searchError: readonly(_searchError),
    limitError: readonly(_limitError),
    currentVerdict: readonly(_currentVerdict),
    usageStats: readonly(_usageStats),
    hasRemainingCompanies,
    hasRemainingDailyChecks,
    guestSearch,
    clearGuestSearch,
    getSessionFingerprint,
  }
}
