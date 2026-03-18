/**
 * useDateFull — locale-aware full date+time formatting composable.
 *
 * Formats dates as full localized date+time strings using Intl.DateTimeFormat.
 * Examples:
 *   - Hungarian: "2026. március 17. 14:30"
 *   - English:   "March 17, 2026, 2:30 PM"
 *
 * Stub created by Story 3.1 — will be expanded when full date display is needed.
 */
export function useDateFull() {
  const { locale } = useI18n()

  function formatFull(date: string | Date | null | undefined): string {
    if (!date) return ''
    const d = typeof date === 'string' ? new Date(date) : date
    if (isNaN(d.getTime())) return ''
    return new Intl.DateTimeFormat(locale.value, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  }

  return { formatFull }
}
