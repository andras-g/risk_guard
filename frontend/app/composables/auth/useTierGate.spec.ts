import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useTierGate } from './useTierGate'

// Mock auth store state
let mockTier: string | null = 'ALAP'
let mockIsAuthenticated = true

vi.stubGlobal('useAuthStore', () => ({
  tier: mockTier,
  isAuthenticated: mockIsAuthenticated
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key
}))

describe('useTierGate', () => {
  beforeEach(() => {
    mockTier = 'ALAP'
    mockIsAuthenticated = true
  })

  it('ALAP user denied PRO access', () => {
    mockTier = 'ALAP'
    const { hasAccess, currentTier } = useTierGate('PRO')
    expect(hasAccess.value).toBe(false)
    expect(currentTier.value).toBe('ALAP')
  })

  it('PRO user granted PRO access', () => {
    mockTier = 'PRO'
    const { hasAccess } = useTierGate('PRO')
    expect(hasAccess.value).toBe(true)
  })

  it('PRO_EPR user granted PRO access (higher tier satisfies lower)', () => {
    mockTier = 'PRO_EPR'
    const { hasAccess } = useTierGate('PRO')
    expect(hasAccess.value).toBe(true)
  })

  it('PRO_EPR user granted all tiers', () => {
    mockTier = 'PRO_EPR'
    expect(useTierGate('ALAP').hasAccess.value).toBe(true)
    expect(useTierGate('PRO').hasAccess.value).toBe(true)
    expect(useTierGate('PRO_EPR').hasAccess.value).toBe(true)
  })

  it('null tier = denied (fail-closed)', () => {
    mockTier = null
    const { hasAccess } = useTierGate('ALAP')
    expect(hasAccess.value).toBe(false)
  })

  it('unauthenticated user = denied (fail-closed)', () => {
    mockTier = null
    mockIsAuthenticated = false
    const { hasAccess } = useTierGate('ALAP')
    expect(hasAccess.value).toBe(false)
  })

  it('returns correct tierName i18n key', () => {
    const { tierName } = useTierGate('PRO')
    expect(tierName.value).toBe('common.tiers.PRO')
  })

  it('returns the required tier', () => {
    const gate = useTierGate('PRO_EPR')
    expect(gate.requiredTier).toBe('PRO_EPR')
  })
})
