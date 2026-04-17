/**
 * Composable for mapping RFC 7807 error types to i18n keys.
 * Used to display user-friendly error messages from backend ProblemDetail responses.
 */
export function useApiError() {
  const { t } = useI18n()

  const ERROR_TYPE_MAP: Record<string, string> = {
    'urn:riskguard:error:tier-upgrade-required': 'common.errors.tierUpgradeRequired',
    'urn:riskguard:error:email-already-registered': 'auth.register.error.emailExists',
    'urn:riskguard:error:email-exists-sso': 'auth.register.error.emailExistsSso',
    'urn:riskguard:error:invalid-credentials': 'auth.login.error.invalidCredentials',
    'urn:riskguard:error:too-many-attempts': 'auth.login.error.tooManyAttempts',
    'urn:riskguard:error:registry-components-required': 'registry.form.validation.componentsRequired',
    'urn:riskguard:error:registry-validation-failed': 'registry.form.validation.failed',
  }

  function mapErrorType(type: string | undefined): string {
    if (!type) return t('common.states.error')
    const key = ERROR_TYPE_MAP[type]
    return key ? t(key) : t('common.states.error')
  }

  /**
   * Extracts a user-readable summary from a ProblemDetail response.
   * Falls back to the generic i18n error message when the response
   * does not contain a structured {@code detail} field.
   */
  function mapErrorDetail(err: unknown): string {
    const e = err as { data?: { type?: string; detail?: string } }
    const mapped = mapErrorType(e?.data?.type)
    // If the backend returned a specific detail string (e.g. field-level
    // validation messages), append it so the user knows what to fix.
    if (e?.data?.detail && e.data.detail !== 'Validation failed') {
      return `${mapped}: ${e.data.detail}`
    }
    return mapped
  }

  return { mapErrorType, mapErrorDetail }
}
