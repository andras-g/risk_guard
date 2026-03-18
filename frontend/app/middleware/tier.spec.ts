import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock auth store
let mockTier: string | null = 'ALAP'

vi.stubGlobal('useAuthStore', () => ({
  tier: mockTier
}))

// We need to mock defineNuxtRouteMiddleware to capture the middleware function
let middlewareFn: (to: any) => void

vi.stubGlobal('defineNuxtRouteMiddleware', (fn: (to: any) => void) => {
  middlewareFn = fn
  return fn
})

// Import the middleware (this triggers defineNuxtRouteMiddleware which captures the fn)
// Must be imported after mocks are set up
await import('./tier')

function createRouteMeta(requiredTier?: string): any {
  return {
    meta: {
      requiredTier,
      tierDenied: undefined,
      tierRequired: undefined
    }
  }
}

describe('tier middleware', () => {
  beforeEach(() => {
    mockTier = 'ALAP'
  })

  it('no-op when requiredTier is undefined', () => {
    const to = createRouteMeta(undefined)
    middlewareFn(to)
    expect(to.meta.tierDenied).toBeUndefined()
    expect(to.meta.tierRequired).toBeUndefined()
  })

  it('sets tierDenied when user tier is insufficient', () => {
    mockTier = 'ALAP'
    const to = createRouteMeta('PRO')
    middlewareFn(to)
    expect(to.meta.tierDenied).toBe(true)
    expect(to.meta.tierRequired).toBe('PRO')
  })

  it('does not set tierDenied when user tier is sufficient', () => {
    mockTier = 'PRO'
    const to = createRouteMeta('PRO')
    middlewareFn(to)
    expect(to.meta.tierDenied).toBeUndefined()
  })

  it('PRO_EPR user passes PRO-gated route', () => {
    mockTier = 'PRO_EPR'
    const to = createRouteMeta('PRO')
    middlewareFn(to)
    expect(to.meta.tierDenied).toBeUndefined()
  })

  it('denies access when tier is null (fail-closed)', () => {
    mockTier = null
    const to = createRouteMeta('ALAP')
    middlewareFn(to)
    expect(to.meta.tierDenied).toBe(true)
    expect(to.meta.tierRequired).toBe('ALAP')
  })

  it('ALAP user passes ALAP-gated route', () => {
    mockTier = 'ALAP'
    const to = createRouteMeta('ALAP')
    middlewareFn(to)
    expect(to.meta.tierDenied).toBeUndefined()
  })
})
