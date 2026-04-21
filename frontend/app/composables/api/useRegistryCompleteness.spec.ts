import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockApiFetch = vi.fn()
vi.mock('~/composables/api/useApi', () => ({
  useApi: () => ({ apiFetch: mockApiFetch }),
}))

import { useRegistryCompleteness } from './useRegistryCompleteness'

describe('useRegistryCompleteness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('isEmpty is true when productsWithComponents is 0', () => {
    const { isEmpty } = useRegistryCompleteness()
    expect(isEmpty.value).toBe(true)
  })

  it('isEmpty is false when productsWithComponents > 0', async () => {
    mockApiFetch.mockResolvedValueOnce({ totalProducts: 3, productsWithComponents: 2 })
    const { isEmpty, refresh } = useRegistryCompleteness()
    await refresh()
    expect(isEmpty.value).toBe(false)
  })

  it('refresh sets isLoading to true during fetch and false after', async () => {
    const loadingStates: boolean[] = []
    let isLoadingRef: { value: boolean } | null = null
    mockApiFetch.mockImplementationOnce(async () => {
      // Sample isLoading from inside the in-flight fetch — proves the contract.
      loadingStates.push(isLoadingRef!.value)
      return { totalProducts: 1, productsWithComponents: 1 }
    })
    const { isLoading, refresh } = useRegistryCompleteness()
    isLoadingRef = isLoading
    loadingStates.push(isLoading.value)
    const promise = refresh()
    loadingStates.push(isLoading.value)
    await promise
    loadingStates.push(isLoading.value)
    expect(loadingStates).toEqual([false, true, true, false])
  })

  it('successful fetch populates totalProducts and productsWithComponents', async () => {
    mockApiFetch.mockResolvedValueOnce({ totalProducts: 5, productsWithComponents: 3 })
    const { totalProducts, productsWithComponents, refresh } = useRegistryCompleteness()
    await refresh()
    expect(totalProducts.value).toBe(5)
    expect(productsWithComponents.value).toBe(3)
  })

  it('failed fetch leaves values at 0 and does not throw', async () => {
    mockApiFetch.mockRejectedValueOnce(new Error('network error'))
    const { totalProducts, productsWithComponents, refresh } = useRegistryCompleteness()
    await expect(refresh()).resolves.toBeUndefined()
    expect(totalProducts.value).toBe(0)
    expect(productsWithComponents.value).toBe(0)
  })
})
