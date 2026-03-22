import authConfig from '../../risk-guard-tokens.json'

/**
 * Module-level singleton: stores the in-flight refresh Promise.
 * All concurrent 401 handlers await the same Promise instead of making duplicate requests.
 * After the Promise resolves/rejects, it is cleared so the next expiry cycle triggers a fresh refresh.
 */
let refreshPromise: Promise<boolean> | null = null

/**
 * Composable for silent token refresh with request deduplication.
 *
 * When a 401 is received, this composable attempts to silently refresh
 * the session by calling POST /api/public/auth/refresh (which sends
 * the HttpOnly refresh_token cookie). If successful, the server rotates
 * the refresh token and issues a new access token — both as HttpOnly cookies.
 *
 * Deduplication: if multiple concurrent requests all receive 401, only ONE
 * refresh call is made. All callers await the same Promise.
 *
 * Story 3.13 — AC #2, #6, #9
 */
export function useTokenRefresh() {
  const config = useRuntimeConfig()

  /**
   * Attempt a silent token refresh.
   * Returns true if refresh succeeded, false if it failed.
   * Deduplicates concurrent calls via a shared Promise.
   */
  async function attemptRefresh(): Promise<boolean> {
    // If a refresh is already in-flight, return the existing Promise
    if (refreshPromise) {
      return refreshPromise
    }

    refreshPromise = doRefresh()

    try {
      return await refreshPromise
    } finally {
      // Clear the shared Promise after resolution so next expiry triggers a fresh refresh
      refreshPromise = null
    }
  }

  async function doRefresh(): Promise<boolean> {
    try {
      await $fetch(authConfig.endpoints.refresh, {
        method: 'POST',
        baseURL: config.public.apiBase as string,
        credentials: 'include',
      })
      return true
    } catch {
      return false
    }
  }

  /**
   * Check if the error response indicates a revoked token family (potential compromise).
   */
  function isTokenFamilyRevoked(response: Response | undefined): boolean {
    // The server returns errorCode: 'TOKEN_FAMILY_REVOKED' in the ProblemDetail body.
    // We check the response body if available.
    // For ofetch, the parsed _data may contain the error code.
    try {
      const data = (response as any)?._data
      if (data?.errorCode === 'TOKEN_FAMILY_REVOKED') {
        return true
      }
    } catch {
      // Ignore parse errors
    }
    return false
  }

  return {
    attemptRefresh,
    isTokenFamilyRevoked,
  }
}

/**
 * Reset the module-level refresh promise. Exposed for testing only.
 */
export function _resetRefreshPromise(): void {
  refreshPromise = null
}
