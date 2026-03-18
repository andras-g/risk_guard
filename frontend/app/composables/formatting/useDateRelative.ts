/**
 * useDateRelative — locale-aware relative time formatting composable.
 *
 * Returns human-readable relative timestamps using the native Intl.RelativeTimeFormat API.
 * Examples:
 *   - Hungarian: "2 perccel ezelőtt" / "3 napja"
 *   - English:   "2 minutes ago"     / "3 days ago"
 *
 * Story 2.4: Used by ProvenanceSidebar to display "Last Checked" times for data sources.
 */

const UNITS: { unit: Intl.RelativeTimeFormatUnit; ms: number }[] = [
  { unit: 'year', ms: 365 * 24 * 60 * 60 * 1000 },
  { unit: 'month', ms: 30 * 24 * 60 * 60 * 1000 },
  { unit: 'day', ms: 24 * 60 * 60 * 1000 },
  { unit: 'hour', ms: 60 * 60 * 1000 },
  { unit: 'minute', ms: 60 * 1000 },
  { unit: 'second', ms: 1000 },
]

export function useDateRelative() {
  const { locale } = useI18n()

  /**
   * Format an ISO date string or Date object as a locale-aware relative time string.
   *
   * @param date - ISO 8601 date string or Date object; null/undefined returns empty string
   * @returns relative time string in the current locale (e.g., "2 perccel ezelőtt")
   */
  function formatRelative(date: string | Date | null | undefined): string {
    if (!date) return ''

    const target = typeof date === 'string' ? new Date(date) : date
    if (isNaN(target.getTime())) return ''

    const now = new Date()
    const diffMs = target.getTime() - now.getTime()
    const absDiffMs = Math.abs(diffMs)

    // Find the most appropriate unit
    for (const { unit, ms } of UNITS) {
      if (absDiffMs >= ms || unit === 'second') {
        const value = Math.round(diffMs / ms)
        const formatter = new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' })
        return formatter.format(value, unit)
      }
    }

    return ''
  }

  /**
   * Format an ISO date string or Date object as a locale-aware relative time string.
   * Returns a computed ref that re-evaluates when the **locale** changes.
   *
   * NOTE: This computed ref does NOT auto-tick with the passage of time. The relative
   * string (e.g. "2 minutes ago") is computed once and stays fixed until the locale
   * changes or the component re-renders. For use cases requiring live-updating relative
   * times, set up an `setInterval` and call `formatRelative()` directly instead.
   *
   * @param date - ISO 8601 date string or Date object; null/undefined returns empty string
   * @returns computed ref with the relative time string (locale-reactive, not time-reactive)
   */
  function useRelativeTime(date: string | Date | null | undefined) {
    return computed(() => formatRelative(date))
  }

  return {
    formatRelative,
    useRelativeTime,
  }
}
