/**
 * Composable for locale-aware API calls.
 *
 * Wraps `$fetch` to automatically include the `Accept-Language` header
 * matching the current i18n locale. This ensures the backend's
 * {@code AcceptHeaderLocaleResolver} resolves the correct locale for
 * server-generated content (emails, exports, audit descriptions).
 *
 * Usage:
 * ```ts
 * const { apiFetch } = useApi()
 * const data = await apiFetch('/api/v1/screening/search', { method: 'POST', body: { taxNumber } })
 * ```
 */
export function useApi() {
  const { locale } = useI18n()
  const config = useRuntimeConfig()

  /**
   * Locale-aware fetch wrapper. Adds `Accept-Language` header
   * with the current i18n locale to every request.
   */
  function apiFetch<T>(url: string, options: Record<string, any> = {}): Promise<T> {
    const headers = {
      'Accept-Language': locale.value,
      ...(options.headers || {}),
    }

    return $fetch<T>(url, {
      ...options,
      headers,
      baseURL: options.baseURL || (config.public.apiBase as string),
      credentials: options.credentials || 'include',
    })
  }

  return { apiFetch }
}
