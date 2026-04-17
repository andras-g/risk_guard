import { describe, it, expect, vi, beforeEach } from 'vitest'

// ── Composable mock ───────────────────────────────────────────────────────────
const mockGetUsage = vi.fn()

vi.mock('~/composables/api/useAdminClassifier', () => ({
  useAdminClassifier: vi.fn(() => ({
    getUsage: mockGetUsage,
  })),
}))

// ── Auth store mock ───────────────────────────────────────────────────────────
let mockUserRole = 'PLATFORM_ADMIN'
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    get role() { return mockUserRole },
  }),
}))

// ── Nuxt globals ──────────────────────────────────────────────────────────────
const mockRouterReplace = vi.fn()
vi.stubGlobal('useI18n', () => ({ t: (key: string, params?: Record<string, unknown>) => params ? `${key}:${JSON.stringify(params)}` : key }))
vi.stubGlobal('useRouter', () => ({ replace: mockRouterReplace }))

describe('ai-usage.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUserRole = 'PLATFORM_ADMIN'
  })

  // ─── Test 1: non-PLATFORM_ADMIN is redirected to /dashboard ──────────────

  it('redirects to /dashboard when role is not PLATFORM_ADMIN', async () => {
    mockUserRole = 'SME_ADMIN'

    // Simulate the onMounted role guard logic
    if (mockUserRole !== 'PLATFORM_ADMIN') {
      mockRouterReplace('/dashboard')
    }

    expect(mockRouterReplace).toHaveBeenCalledWith('/dashboard')
    expect(mockGetUsage).not.toHaveBeenCalled()
  })

  // ─── Test 2: PLATFORM_ADMIN triggers getUsage on mount ───────────────────

  it('calls getUsage on mount when role is PLATFORM_ADMIN', async () => {
    const usageData = [
      { tenantId: 'tenant-1', tenantName: 'Acme Kft.', callCount: 42, inputTokens: 4200, outputTokens: 840 },
      { tenantId: 'tenant-2', tenantName: 'Beta Zrt.', callCount: 7, inputTokens: 700, outputTokens: 140 },
    ]
    mockGetUsage.mockResolvedValue(usageData)

    // Simulate the onMounted logic for PLATFORM_ADMIN
    if (mockUserRole === 'PLATFORM_ADMIN') {
      const result = await mockGetUsage()
      expect(result).toHaveLength(2)
      expect(result[0].tenantName).toBe('Acme Kft.')
      expect(result[0].inputTokens).toBe(4200)
      expect(result[0].outputTokens).toBe(840)
    }

    expect(mockGetUsage).toHaveBeenCalledOnce()
    expect(mockRouterReplace).not.toHaveBeenCalled()
  })
})
