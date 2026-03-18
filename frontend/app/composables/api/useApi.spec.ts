import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useApi } from './useApi'

/**
 * Unit tests for useApi composable.
 *
 * Verifies that the Accept-Language header is injected on every API call,
 * reflecting the current i18n locale.
 * Co-located per architecture rules.
 */

let mockLocale = 'hu'

vi.stubGlobal('useI18n', () => ({
  locale: { value: mockLocale },
  t: (key: string) => key,
}))

vi.stubGlobal('$fetch', vi.fn())
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

beforeEach(() => {
  mockLocale = 'hu'
  vi.mocked($fetch).mockReset()
})

describe('useApi — Accept-Language header', () => {
  it('should add Accept-Language: hu header when locale is hu', async () => {
    const { apiFetch } = useApi()
    await apiFetch('/api/v1/test')
    expect($fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      headers: expect.objectContaining({ 'Accept-Language': 'hu' }),
      baseURL: 'http://localhost:8080',
      credentials: 'include',
    }))
  })

  it('should add Accept-Language: en header when locale is en', async () => {
    mockLocale = 'en'
    const { apiFetch } = useApi()
    await apiFetch('/api/v1/test')
    expect($fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      headers: expect.objectContaining({ 'Accept-Language': 'en' }),
    }))
  })

  it('should preserve custom headers while adding Accept-Language', async () => {
    const { apiFetch } = useApi()
    await apiFetch('/api/v1/test', {
      headers: { 'X-Custom': 'value' },
    })
    expect($fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      headers: { 'Accept-Language': 'hu', 'X-Custom': 'value' },
    }))
  })

  it('should use default baseURL and credentials', async () => {
    const { apiFetch } = useApi()
    await apiFetch('/api/v1/test')
    expect($fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      baseURL: 'http://localhost:8080',
      credentials: 'include',
    }))
  })

  it('should allow overriding baseURL and credentials', async () => {
    const { apiFetch } = useApi()
    await apiFetch('/api/v1/test', {
      baseURL: 'http://custom:9090',
      credentials: 'omit',
    })
    expect($fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      baseURL: 'http://custom:9090',
      credentials: 'omit',
    }))
  })
})
