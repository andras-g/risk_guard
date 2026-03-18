import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useLocaleSync } from './useLocaleSync'

/**
 * Unit tests for useLocaleSync composable.
 *
 * Verifies: locale sync from user profile on login,
 * locale persistence via API for authenticated users,
 * and guest mode (no API call).
 * Co-located per architecture rules.
 */

const setLocaleMock = vi.fn()
let mockLocaleValue = 'hu'
let mockAuthState = {
  preferredLanguage: 'hu' as string | null,
  isAuthenticated: false,
}

vi.stubGlobal('useI18n', () => ({
  setLocale: setLocaleMock,
  locale: { value: mockLocaleValue },
  t: (key: string) => key,
}))

vi.mock('~/stores/auth', () => ({
  useAuthStore: () => mockAuthState,
}))

vi.stubGlobal('$fetch', vi.fn())
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

beforeEach(() => {
  mockLocaleValue = 'hu'
  mockAuthState = {
    preferredLanguage: 'hu',
    isAuthenticated: false,
  }
  setLocaleMock.mockReset()
  vi.mocked($fetch).mockReset()
})

describe('useLocaleSync — syncLocaleFromProfile', () => {
  it('should call setLocale when user preferred language differs from current locale', async () => {
    mockAuthState.preferredLanguage = 'en'
    const { syncLocaleFromProfile } = useLocaleSync()
    await syncLocaleFromProfile()
    expect(setLocaleMock).toHaveBeenCalledWith('en')
  })

  it('should NOT call setLocale when preferred language matches current locale', async () => {
    mockAuthState.preferredLanguage = 'hu'
    mockLocaleValue = 'hu'
    const { syncLocaleFromProfile } = useLocaleSync()
    await syncLocaleFromProfile()
    expect(setLocaleMock).not.toHaveBeenCalled()
  })

  it('should NOT call setLocale when preferredLanguage is null', async () => {
    mockAuthState.preferredLanguage = null
    const { syncLocaleFromProfile } = useLocaleSync()
    await syncLocaleFromProfile()
    expect(setLocaleMock).not.toHaveBeenCalled()
  })
})

describe('useLocaleSync — changeLocale (authenticated)', () => {
  it('should call setLocale and PATCH the backend when authenticated', async () => {
    mockAuthState.isAuthenticated = true
    const { changeLocale } = useLocaleSync()
    await changeLocale('en')
    expect(setLocaleMock).toHaveBeenCalledWith('en')
    expect($fetch).toHaveBeenCalledWith('/api/v1/identity/me/language', {
      method: 'PATCH',
      body: { language: 'en' },
      baseURL: 'http://localhost:8080',
      credentials: 'include',
    })
  })

  it('should update auth store preferredLanguage on successful PATCH', async () => {
    mockAuthState.isAuthenticated = true
    mockAuthState.preferredLanguage = 'hu'
    const { changeLocale } = useLocaleSync()
    await changeLocale('en')
    expect(mockAuthState.preferredLanguage).toBe('en')
  })

  it('should NOT throw when PATCH fails (best-effort)', async () => {
    mockAuthState.isAuthenticated = true
    vi.mocked($fetch).mockRejectedValueOnce(new Error('Network error'))
    const { changeLocale } = useLocaleSync()
    await expect(changeLocale('en')).resolves.not.toThrow()
    expect(setLocaleMock).toHaveBeenCalledWith('en')
  })
})

describe('useLocaleSync — changeLocale (guest)', () => {
  it('should call setLocale but NOT call $fetch when not authenticated', async () => {
    mockAuthState.isAuthenticated = false
    const { changeLocale } = useLocaleSync()
    await changeLocale('en')
    expect(setLocaleMock).toHaveBeenCalledWith('en')
    expect($fetch).not.toHaveBeenCalled()
  })
})
