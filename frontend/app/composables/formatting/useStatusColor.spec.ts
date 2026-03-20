import { describe, it, expect } from 'vitest'
import { useStatusColor } from './useStatusColor'

describe('useStatusColor', () => {
  const { statusColorClass, statusIconClass, statusI18nKey } = useStatusColor()

  describe('statusColorClass', () => {
    it('returns crimson for AT_RISK', () => {
      expect(statusColorClass('AT_RISK')).toBe('border-red-700 text-red-700')
    })

    it('returns emerald for RELIABLE', () => {
      expect(statusColorClass('RELIABLE')).toBe('border-green-700 text-green-700')
    })

    it('returns amber for STALE', () => {
      expect(statusColorClass('STALE')).toBe('border-amber-600 text-amber-600')
    })

    it('returns amber for INCOMPLETE', () => {
      expect(statusColorClass('INCOMPLETE')).toBe('border-amber-600 text-amber-600')
    })

    it('returns slate for null', () => {
      expect(statusColorClass(null)).toBe('border-slate-400 text-slate-400')
    })

    it('returns slate for unknown status', () => {
      expect(statusColorClass('UNKNOWN')).toBe('border-slate-400 text-slate-400')
    })

    it('returns slate for UNAVAILABLE', () => {
      expect(statusColorClass('UNAVAILABLE')).toBe('border-slate-400 text-slate-400')
    })

    it('returns slate for TAX_SUSPENDED', () => {
      expect(statusColorClass('TAX_SUSPENDED')).toBe('border-slate-400 text-slate-400')
    })
  })

  describe('statusIconClass', () => {
    it('returns exclamation-triangle for AT_RISK', () => {
      expect(statusIconClass('AT_RISK')).toBe('pi pi-exclamation-triangle')
    })

    it('returns check-circle for RELIABLE', () => {
      expect(statusIconClass('RELIABLE')).toBe('pi pi-check-circle')
    })

    it('returns clock for STALE', () => {
      expect(statusIconClass('STALE')).toBe('pi pi-clock')
    })

    it('returns clock for INCOMPLETE', () => {
      expect(statusIconClass('INCOMPLETE')).toBe('pi pi-clock')
    })

    it('returns minus-circle for null', () => {
      expect(statusIconClass(null)).toBe('pi pi-minus-circle')
    })

    it('returns minus-circle for UNAVAILABLE', () => {
      expect(statusIconClass('UNAVAILABLE')).toBe('pi pi-minus-circle')
    })

    it('returns minus-circle for TAX_SUSPENDED', () => {
      expect(statusIconClass('TAX_SUSPENDED')).toBe('pi pi-minus-circle')
    })
  })

  describe('statusI18nKey', () => {
    it('returns atRisk key for AT_RISK', () => {
      expect(statusI18nKey('AT_RISK')).toBe('screening.verdict.atRisk')
    })

    it('returns reliable key for RELIABLE', () => {
      expect(statusI18nKey('RELIABLE')).toBe('screening.verdict.reliable')
    })

    it('returns stale key for STALE', () => {
      expect(statusI18nKey('STALE')).toBe('screening.verdict.stale')
    })

    it('returns incomplete key for INCOMPLETE', () => {
      expect(statusI18nKey('INCOMPLETE')).toBe('screening.verdict.incomplete')
    })

    it('returns unavailable key for null', () => {
      expect(statusI18nKey(null)).toBe('screening.verdict.unavailable')
    })

    it('returns unavailable key for unknown status', () => {
      expect(statusI18nKey('SOMETHING_ELSE')).toBe('screening.verdict.unavailable')
    })
  })
})
