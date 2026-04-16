import { describe, it, expect, vi } from 'vitest'
import { useApiError } from './useApiError'

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key
}))

describe('useApiError', () => {
  it('maps tier-upgrade-required type to i18n key', () => {
    const { mapErrorType } = useApiError()
    expect(mapErrorType('urn:riskguard:error:tier-upgrade-required')).toBe('common.errors.tierUpgradeRequired')
  })

  it('returns generic error for unknown type', () => {
    const { mapErrorType } = useApiError()
    expect(mapErrorType('urn:riskguard:error:unknown-type')).toBe('common.states.error')
  })

  it('returns generic error for undefined type', () => {
    const { mapErrorType } = useApiError()
    expect(mapErrorType(undefined)).toBe('common.states.error')
  })

  it('returns generic error for null type', () => {
    const { mapErrorType } = useApiError()
    expect(mapErrorType(null as unknown as string | undefined)).toBe('common.states.error')
  })

  it('returns generic error for empty string type', () => {
    const { mapErrorType } = useApiError()
    expect(mapErrorType('')).toBe('common.states.error')
  })

  describe('mapErrorDetail', () => {
    it('includes detail from ProblemDetail response', () => {
      const { mapErrorDetail } = useApiError()
      const err = { data: { type: 'urn:riskguard:error:registry-validation-failed', detail: 'components[0].materialDescription: must not be blank' } }
      expect(mapErrorDetail(err)).toBe('common.states.error: components[0].materialDescription: must not be blank')
    })

    it('returns mapped type without detail when detail is generic', () => {
      const { mapErrorDetail } = useApiError()
      const err = { data: { type: 'urn:riskguard:error:tier-upgrade-required', detail: 'Validation failed' } }
      expect(mapErrorDetail(err)).toBe('common.errors.tierUpgradeRequired')
    })

    it('returns generic error when err has no data', () => {
      const { mapErrorDetail } = useApiError()
      expect(mapErrorDetail({})).toBe('common.states.error')
    })

    it('returns generic error for null', () => {
      const { mapErrorDetail } = useApiError()
      expect(mapErrorDetail(null)).toBe('common.states.error')
    })
  })
})
