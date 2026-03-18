import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useDateShort } from './useDateShort'

/**
 * Unit tests for useDateShort composable.
 *
 * Verifies locale-aware short date formatting via Intl.DateTimeFormat.
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

describe('useDateShort — formatting', () => {
  it('should format a date string in Hungarian locale', () => {
    mockLocale = 'hu'
    const { formatShort } = useDateShort()
    const result = formatShort('2026-03-17T10:00:00Z')
    // Hungarian format: "2026. 03. 17." (exact format depends on runtime)
    expect(result).toContain('2026')
    expect(result).toContain('03')
    expect(result).toContain('17')
  })

  it('should format a date string in English locale', () => {
    mockLocale = 'en'
    const { formatShort } = useDateShort()
    const result = formatShort('2026-03-17T10:00:00Z')
    expect(result).toContain('2026')
    expect(result).toContain('03')
    expect(result).toContain('17')
  })

  it('should accept a Date object', () => {
    const { formatShort } = useDateShort()
    const result = formatShort(new Date('2026-01-15'))
    expect(result).toContain('2026')
  })

  it('should return empty string for null input', () => {
    const { formatShort } = useDateShort()
    expect(formatShort(null)).toBe('')
  })

  it('should return empty string for undefined input', () => {
    const { formatShort } = useDateShort()
    expect(formatShort(undefined)).toBe('')
  })

  it('should return empty string for invalid date string', () => {
    const { formatShort } = useDateShort()
    expect(formatShort('not-a-date')).toBe('')
  })
})
