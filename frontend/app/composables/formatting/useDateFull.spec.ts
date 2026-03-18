import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useDateFull } from './useDateFull'

/**
 * Unit tests for useDateFull composable.
 *
 * Verifies locale-aware full date+time formatting via Intl.DateTimeFormat.
 * Co-located per architecture rules.
 */

let mockLocale = 'hu'

vi.stubGlobal('useI18n', () => ({
  locale: { value: mockLocale },
  t: (key: string) => key,
}))

beforeEach(() => {
  mockLocale = 'hu'
})

describe('useDateFull — formatting', () => {
  it('should format a date string with time in Hungarian locale', () => {
    mockLocale = 'hu'
    const { formatFull } = useDateFull()
    const result = formatFull('2026-03-17T14:30:00Z')
    expect(result).toContain('2026')
    // Hungarian uses long month name: "március"
    expect(result.toLowerCase()).toMatch(/márc/)
  })

  it('should format a date string with time in English locale', () => {
    mockLocale = 'en'
    const { formatFull } = useDateFull()
    const result = formatFull('2026-03-17T14:30:00Z')
    expect(result).toContain('2026')
    expect(result).toMatch(/Mar/)
  })

  it('should accept a Date object', () => {
    const { formatFull } = useDateFull()
    const result = formatFull(new Date('2026-06-15T08:00:00Z'))
    expect(result).toContain('2026')
  })

  it('should return empty string for null input', () => {
    const { formatFull } = useDateFull()
    expect(formatFull(null)).toBe('')
  })

  it('should return empty string for undefined input', () => {
    const { formatFull } = useDateFull()
    expect(formatFull(undefined)).toBe('')
  })

  it('should return empty string for invalid date string', () => {
    const { formatFull } = useDateFull()
    expect(formatFull('invalid')).toBe('')
  })
})
