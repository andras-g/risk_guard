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
  }

  function mapErrorType(type: string | undefined): string {
    if (!type) return t('common.states.error')
    const key = ERROR_TYPE_MAP[type]
    return key ? t(key) : t('common.states.error')
  }

  return { mapErrorType }
}
