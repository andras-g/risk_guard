import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, computed, readonly } from 'vue'

/**
 * Unit tests for useGuestSession composable (Story 3.12).
 * Tests: fingerprint persistence, usage tracking, limit detection.
 * Co-located per architecture rules.
 *
 * Uses real Vue reactivity (ref/computed/readonly) instead of plain object mocks
 * to ensure reactive state updates propagate correctly in assertions.
 */

// Mock Nuxt auto-imports with real Vue reactivity primitives
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

vi.stubGlobal('ref', ref)
vi.stubGlobal('computed', computed)
vi.stubGlobal('readonly', readonly)

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value }),
    removeItem: vi.fn((key: string) => { delete store[key] }),
    clear: vi.fn(() => { store = {} }),
  }
})()
Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock })

// Mock crypto.randomUUID
Object.defineProperty(globalThis, 'crypto', {
  value: { randomUUID: vi.fn(() => 'mock-uuid-12345') },
})

// Mock import.meta.server
vi.stubGlobal('import', { meta: { server: false } })

// Mock $fetch
vi.stubGlobal('$fetch', vi.fn())

beforeEach(() => {
  localStorageMock.clear()
  vi.mocked($fetch).mockReset()
  vi.mocked(crypto.randomUUID).mockReturnValue('mock-uuid-12345' as `${string}-${string}-${string}-${string}-${string}`)
})

// Import after mocks are set up
const { useGuestSession } = await import('./useGuestSession')

describe('useGuestSession', () => {
  describe('fingerprint persistence', () => {
    it('should generate and persist a new fingerprint when none exists', () => {
      const { getSessionFingerprint } = useGuestSession()
      const fingerprint = getSessionFingerprint()

      expect(fingerprint).toBe('mock-uuid-12345')
      expect(localStorageMock.setItem).toHaveBeenCalledWith('rg_guest_token', 'mock-uuid-12345')
    })

    it('should reuse existing fingerprint from localStorage', () => {
      localStorageMock.setItem('rg_guest_token', 'existing-fingerprint')
      localStorageMock.getItem.mockReturnValueOnce('existing-fingerprint')

      const { getSessionFingerprint } = useGuestSession()
      const fingerprint = getSessionFingerprint()

      expect(fingerprint).toBe('existing-fingerprint')
    })
  })

  describe('usage tracking', () => {
    it('should update usage stats after successful search', async () => {
      const mockResponse = {
        id: 'verdict-123',
        snapshotId: 'snap-123',
        taxNumber: '12345678',
        status: 'RELIABLE',
        confidence: 'FRESH',
        createdAt: '2026-03-20T10:00:00Z',
        riskSignals: [],
        cached: false,
        companyName: 'Test Kft.',
        companiesUsed: 3,
        companiesLimit: 10,
        dailyChecksUsed: 1,
        dailyChecksLimit: 3,
      }
      vi.mocked($fetch).mockResolvedValueOnce(mockResponse)

      const { guestSearch, usageStats } = useGuestSession()
      const result = await guestSearch('12345678')

      expect(result).not.toBeNull()
      expect(usageStats.value.companiesUsed).toBe(3)
      expect(usageStats.value.companiesLimit).toBe(10)
      expect(usageStats.value.dailyChecksUsed).toBe(1)
      expect(usageStats.value.dailyChecksLimit).toBe(3)
    })
  })

  describe('limit detection', () => {
    it('should detect COMPANY_LIMIT_REACHED from 429 response', async () => {
      const fetchError = {
        statusCode: 429,
        data: {
          error: 'COMPANY_LIMIT_REACHED',
          companiesUsed: 10,
          companiesLimit: 10,
        },
      }
      vi.mocked($fetch).mockRejectedValueOnce(fetchError)

      const { guestSearch, limitError } = useGuestSession()
      const result = await guestSearch('12345678')

      expect(result).toBeNull()
      expect(limitError.value).toBe('COMPANY_LIMIT_REACHED')
    })

    it('should detect DAILY_LIMIT_REACHED from 429 response', async () => {
      const fetchError = {
        statusCode: 429,
        data: {
          error: 'DAILY_LIMIT_REACHED',
          dailyChecksUsed: 3,
          dailyChecksLimit: 3,
        },
      }
      vi.mocked($fetch).mockRejectedValueOnce(fetchError)

      const { guestSearch, limitError } = useGuestSession()
      const result = await guestSearch('12345678')

      expect(result).toBeNull()
      expect(limitError.value).toBe('DAILY_LIMIT_REACHED')
    })

    it('should set searchError for non-429 errors', async () => {
      vi.mocked($fetch).mockRejectedValueOnce(new Error('Network error'))

      const { guestSearch, searchError, limitError } = useGuestSession()
      const result = await guestSearch('12345678')

      expect(result).toBeNull()
      expect(searchError.value).toBe('Network error')
      expect(limitError.value).toBeNull()
    })
  })
})
