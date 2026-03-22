import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useTokenRefresh, _resetRefreshPromise } from './useTokenRefresh'

// Mock useRuntimeConfig — must be declared before imports that use it
vi.mock('#app', () => ({
  useRuntimeConfig: () => ({
    public: {
      apiBase: 'http://localhost:8080',
    },
  }),
}))

// Track $fetch calls
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  _resetRefreshPromise()
  fetchMock = vi.fn()
  // @ts-expect-error — globalThis.$fetch is overridden
  globalThis.$fetch = fetchMock
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useTokenRefresh', () => {
  describe('attemptRefresh', () => {
    it('should return true on successful refresh', async () => {
      fetchMock.mockResolvedValueOnce(undefined) // 204 No Content

      const { attemptRefresh } = useTokenRefresh()
      const result = await attemptRefresh()

      expect(result).toBe(true)
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/public/auth/refresh',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
        }),
      )
    })

    it('should return false when refresh fails', async () => {
      fetchMock.mockRejectedValueOnce(new Error('Network error'))

      const { attemptRefresh } = useTokenRefresh()
      const result = await attemptRefresh()

      expect(result).toBe(false)
    })

    it('should deduplicate concurrent refresh calls', async () => {
      // Slow refresh that takes some time
      let resolveRefresh: (value: unknown) => void
      const slowPromise = new Promise((resolve) => {
        resolveRefresh = resolve
      })
      fetchMock.mockReturnValueOnce(slowPromise)

      const { attemptRefresh } = useTokenRefresh()

      // Fire 3 concurrent attempts
      const p1 = attemptRefresh()
      const p2 = attemptRefresh()
      const p3 = attemptRefresh()

      // Resolve the single $fetch call
      resolveRefresh!(undefined)

      const [r1, r2, r3] = await Promise.all([p1, p2, p3])

      // All should succeed
      expect(r1).toBe(true)
      expect(r2).toBe(true)
      expect(r3).toBe(true)

      // Only ONE $fetch call should have been made
      expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('should allow new refresh after previous one completes', async () => {
      fetchMock.mockResolvedValueOnce(undefined) // First refresh
      fetchMock.mockResolvedValueOnce(undefined) // Second refresh

      const { attemptRefresh } = useTokenRefresh()

      // First refresh
      const r1 = await attemptRefresh()
      expect(r1).toBe(true)

      // Second refresh — should make a new call since first completed
      const r2 = await attemptRefresh()
      expect(r2).toBe(true)

      expect(fetchMock).toHaveBeenCalledTimes(2)
    })
  })

  describe('isTokenFamilyRevoked', () => {
    it('should return true when response contains TOKEN_FAMILY_REVOKED error code', () => {
      const { isTokenFamilyRevoked } = useTokenRefresh()
      const mockResponse = { _data: { errorCode: 'TOKEN_FAMILY_REVOKED' } } as any

      expect(isTokenFamilyRevoked(mockResponse)).toBe(true)
    })

    it('should return false for other error responses', () => {
      const { isTokenFamilyRevoked } = useTokenRefresh()
      const mockResponse = { _data: { errorCode: 'SOME_OTHER_ERROR' } } as any

      expect(isTokenFamilyRevoked(mockResponse)).toBe(false)
    })

    it('should return false for undefined response', () => {
      const { isTokenFamilyRevoked } = useTokenRefresh()
      expect(isTokenFamilyRevoked(undefined)).toBe(false)
    })
  })
})
