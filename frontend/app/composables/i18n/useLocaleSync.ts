/**
 * Composable that synchronises the i18n locale with the user's stored preference.
 *
 * - On login: reads `preferredLanguage` from the auth store (populated by /me API)
 *   and calls `setLocale()` to switch the UI.
 * - On explicit locale switch: persists the new locale to the backend via PATCH
 *   so it is remembered across sessions and devices.
 * - For guests (not authenticated): no API call — @nuxtjs/i18n cookie handles persistence.
 */
import { useAuthStore } from '~/stores/auth'

export function useLocaleSync() {
  const { setLocale, locale } = useI18n()
  const authStore = useAuthStore()
  const config = useRuntimeConfig()

  /**
   * Sync locale from user profile after login.
   * Call this after `authStore.fetchMe()` succeeds.
   */
  async function syncLocaleFromProfile(): Promise<void> {
    const preferred = authStore.preferredLanguage
    if (preferred && preferred !== locale.value) {
      await setLocale(preferred)
    }
  }

  /**
   * Persist locale preference to backend and switch locale.
   * For authenticated users: PATCH /api/v1/identity/me/language.
   * For guests: setLocale() alone (cookie handled by @nuxtjs/i18n).
   */
  async function changeLocale(newLocale: string): Promise<void> {
    await setLocale(newLocale)

    if (authStore.isAuthenticated) {
      try {
        await $fetch('/api/v1/identity/me/language', {
          method: 'PATCH',
          body: { language: newLocale },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        })
        authStore.preferredLanguage = newLocale
      } catch {
        // Best-effort — locale is already switched in the UI via cookie.
        // If the backend is unreachable, the user still gets the correct
        // locale this session. It will re-sync on next login.
        console.warn('Failed to persist locale preference to backend')
      }
    }
  }

  return {
    syncLocaleFromProfile,
    changeLocale,
  }
}
