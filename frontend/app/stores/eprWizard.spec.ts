import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/**
 * Story 10.2 — unit coverage for the resolve-only extensions on useEprWizardStore:
 * isResolveOnlyMode, lastResolvedKfCode, startResolveOnly(), resolveAndClose(), and
 * the _resetWizardState clear of isResolveOnlyMode.
 *
 * Mocks: $fetch (global) + useRuntimeConfig (global). Tests run against a real Pinia
 * instance so state transitions reflect the production store logic exactly.
 */

const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)
vi.stubGlobal('useRuntimeConfig', () => ({ public: { apiBase: 'http://localhost:8080' } }))

// useEprStore is imported by eprWizard.ts; stub it so the resolve-only path does not
// touch a real store instance.
vi.mock('~/stores/epr', () => ({
  useEprStore: () => ({ fetchMaterials: vi.fn() }),
}))

describe('useEprWizardStore — Story 10.2 resolve-only mode', () => {
  let store: ReturnType<typeof import('./eprWizard').useEprWizardStore>

  beforeEach(async () => {
    setActivePinia(createPinia())
    mockFetch.mockReset()
    const { useEprWizardStore } = await import('./eprWizard')
    store = useEprWizardStore()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('state defaults: isResolveOnlyMode=false, lastResolvedKfCode=null', () => {
    expect(store.isResolveOnlyMode).toBe(false)
    expect(store.lastResolvedKfCode).toBeNull()
  })

  it('startResolveOnly sets isResolveOnlyMode=true and targetTemplateId=null; POSTs /wizard/start once', async () => {
    mockFetch.mockResolvedValueOnce({
      configVersion: 1,
      options: [{ code: '11', label: 'Packaging', description: null }],
    })

    await store.startResolveOnly()

    expect(store.isResolveOnlyMode).toBe(true)
    expect(store.targetTemplateId).toBeNull()
    expect(store.activeStep).toBe('1')
    expect(store.configVersion).toBe(1)
    expect(store.availableOptions).toHaveLength(1)
    expect(mockFetch).toHaveBeenCalledTimes(1)
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/epr/wizard/start',
      expect.objectContaining({ credentials: 'include' })
    )
  })

  it('resolveAndClose writes lastResolvedKfCode + clears working state + does NOT POST /wizard/confirm', () => {
    store.isResolveOnlyMode = true
    store.resolvedResult = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      materialClassification: 'Paper and cardboard',
      traversalPath: [],
      legislationRef: 'test',
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    }
    store.activeStep = '4'

    store.resolveAndClose()

    expect(store.lastResolvedKfCode).toEqual({
      kfCode: '11010101',
      materialClassification: 'Paper and cardboard',
      feeRate: 20.44,
    })
    // Working state cleared (via _resetWizardState)
    expect(store.activeStep).toBeNull()
    expect(store.isResolveOnlyMode).toBe(false)
    // No network round-trip on this path
    expect(mockFetch).not.toHaveBeenCalledWith(
      '/api/v1/epr/wizard/confirm',
      expect.anything()
    )
  })

  it('resolveAndClose uses override values when isOverrideActive=true', () => {
    store.isResolveOnlyMode = true
    store.resolvedResult = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      materialClassification: 'Paper and cardboard',
      traversalPath: [],
      legislationRef: 'test',
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    }
    store.isOverrideActive = true
    store.overrideKfCode = '22020202'
    store.overrideClassification = 'Plastic — mixed'
    store.overrideFeeRate = 99.99

    store.resolveAndClose()

    expect(store.lastResolvedKfCode).toEqual({
      kfCode: '22020202',
      materialClassification: 'Plastic — mixed',
      feeRate: 99.99,
    })
  })

  it('resolveAndClose is a no-op when isResolveOnlyMode=false', () => {
    store.resolvedResult = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      materialClassification: 'Paper and cardboard',
      traversalPath: [],
      legislationRef: 'test',
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    }

    store.resolveAndClose()

    expect(store.lastResolvedKfCode).toBeNull()
  })

  it('resolveAndClose is a no-op when resolvedResult is null', () => {
    store.isResolveOnlyMode = true
    store.resolvedResult = null

    store.resolveAndClose()

    expect(store.lastResolvedKfCode).toBeNull()
  })

  it('cancelWizard resets all state including isResolveOnlyMode and lastResolvedKfCode', () => {
    store.isResolveOnlyMode = true
    store.lastResolvedKfCode = { kfCode: '11010101', materialClassification: 'Paper', feeRate: 20 }
    store.activeStep = '4'

    store.cancelWizard()

    expect(store.isResolveOnlyMode).toBe(false)
    expect(store.lastResolvedKfCode).toBeNull()
    expect(store.activeStep).toBeNull()
  })
})
