/**
 * useDateShort — locale-aware short date formatting composable.
 *
 * Formats dates as short localized strings using Intl.DateTimeFormat.
 * Examples:
 *   - Hungarian: "2026. 03. 17."
 *   - English:   "3/17/2026"
 *
 * Stub created by Story 3.1 — will be expanded when date display is needed.
 */
export function useDateShort() {
  const { locale } = useI18n()

  function formatShort(date: string | Date | null | undefined): string {
    if (!date) return ''
    const d = typeof date === 'string' ? new Date(date) : date
    if (isNaN(d.getTime())) return ''
    return new Intl.DateTimeFormat(locale.value, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d)
  }

  return { formatShort }
}
