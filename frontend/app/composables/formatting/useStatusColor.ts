/**
 * Composable for mapping verdict status to UX color and icon classes.
 * Follows UX Spec SS7.2 feedback patterns:
 * - AT_RISK → Crimson (red-700)
 * - RELIABLE → Emerald (green-700)
 * - STALE / INCOMPLETE → Amber (amber-600)
 * - UNAVAILABLE / unknown → Slate (slate-400)
 *
 * Reusable across PortfolioPulse (Story 3.9), Flight Control (Story 3.10), etc.
 */
export function useStatusColor() {
  /**
   * Get Tailwind border + text color classes for a verdict status.
   */
  function statusColorClass(status: string | null): string {
    switch (status) {
      case 'AT_RISK': return 'border-red-700 text-red-700'
      case 'RELIABLE': return 'border-green-700 text-green-700'
      case 'STALE':
      case 'INCOMPLETE': return 'border-amber-600 text-amber-600'
      default: return 'border-slate-400 text-slate-400'
    }
  }

  /**
   * Get PrimeVue icon class for a verdict status.
   */
  function statusIconClass(status: string | null): string {
    switch (status) {
      case 'AT_RISK': return 'pi pi-exclamation-triangle'
      case 'RELIABLE': return 'pi pi-check-circle'
      case 'STALE':
      case 'INCOMPLETE': return 'pi pi-clock'
      default: return 'pi pi-minus-circle'
    }
  }

  /**
   * Get the i18n key suffix for a verdict status enum value.
   * Maps backend enum names to screening.verdict.* i18n keys.
   */
  function statusI18nKey(status: string | null): string {
    switch (status) {
      case 'AT_RISK': return 'screening.verdict.atRisk'
      case 'RELIABLE': return 'screening.verdict.reliable'
      case 'STALE': return 'screening.verdict.stale'
      case 'INCOMPLETE': return 'screening.verdict.incomplete'
      case 'UNAVAILABLE': return 'screening.verdict.unavailable'
      case 'TAX_SUSPENDED': return 'screening.verdict.taxSuspended'
      default: return 'screening.verdict.unavailable'
    }
  }

  return { statusColorClass, statusIconClass, statusI18nKey }
}
