import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useDateRelative } from './useDateRelative'

/**
 * Unit tests for useDateRelative composable.
 * Tests locale-aware relative time formatting using Intl.RelativeTimeFormat.
 * Co-located per architecture rules.
 */

// Stub useI18n returning English locale
vi.stubGlobal('useI18n', () => ({
  locale: { value: 'en' },
  t: (key: string) => key,
}))

describe('useDateRelative — formatRelative', () => {
  const NOW = new Date('2026-03-13T12:00:00Z')

  beforeEach(() => {
    // Fix "now" for deterministic tests
    vi.setSystemTime(NOW)
  })

  it('should return empty string for null input', () => {
    const { formatRelative } = useDateRelative()
    expect(formatRelative(null)).toBe('')
  })

  it('should return empty string for undefined input', () => {
    const { formatRelative } = useDateRelative()
    expect(formatRelative(undefined)).toBe('')
  })

  it('should return empty string for invalid date string', () => {
    const { formatRelative } = useDateRelative()
    expect(formatRelative('not-a-date')).toBe('')
  })

  it('should format seconds ago correctly', () => {
    const { formatRelative } = useDateRelative()
    const thirtySecondsAgo = new Date(NOW.getTime() - 30 * 1000).toISOString()
    const result = formatRelative(thirtySecondsAgo)
    // Intl.RelativeTimeFormat with numeric: auto for 'second' returns "30 seconds ago"
    expect(result).toContain('second')
  })

  it('should format minutes ago correctly', () => {
    const { formatRelative } = useDateRelative()
    const twoMinutesAgo = new Date(NOW.getTime() - 2 * 60 * 1000).toISOString()
    const result = formatRelative(twoMinutesAgo)
    expect(result).toContain('minute')
    expect(result).toContain('2')
  })

  it('should format hours ago correctly', () => {
    const { formatRelative } = useDateRelative()
    const threeHoursAgo = new Date(NOW.getTime() - 3 * 60 * 60 * 1000).toISOString()
    const result = formatRelative(threeHoursAgo)
    expect(result).toContain('hour')
    expect(result).toContain('3')
  })

  it('should format days ago correctly', () => {
    const { formatRelative } = useDateRelative()
    const twoDaysAgo = new Date(NOW.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString()
    const result = formatRelative(twoDaysAgo)
    expect(result).toContain('day')
  })

  it('should accept a Date object directly', () => {
    const { formatRelative } = useDateRelative()
    const oneHourAgo = new Date(NOW.getTime() - 60 * 60 * 1000)
    const result = formatRelative(oneHourAgo)
    expect(result).toContain('hour')
  })

  it('should format future dates correctly (positive relative time)', () => {
    const { formatRelative } = useDateRelative()
    const inTwoHours = new Date(NOW.getTime() + 2 * 60 * 60 * 1000).toISOString()
    const result = formatRelative(inTwoHours)
    expect(result).toContain('hour')
    // Should indicate future, not past
    expect(result).not.toContain('ago')
  })
})

describe('useDateRelative — useRelativeTime (computed ref)', () => {
  const NOW = new Date('2026-03-13T12:00:00Z')

  beforeEach(() => {
    vi.setSystemTime(NOW)
  })

  it('should return a computed ref with the relative time', () => {
    const { useRelativeTime } = useDateRelative()
    const oneHourAgo = new Date(NOW.getTime() - 60 * 60 * 1000).toISOString()
    const computed = useRelativeTime(oneHourAgo)
    expect(computed.value).toContain('hour')
  })

  it('should return empty string for null via computed ref', () => {
    const { useRelativeTime } = useDateRelative()
    const computed = useRelativeTime(null)
    expect(computed.value).toBe('')
  })
})
