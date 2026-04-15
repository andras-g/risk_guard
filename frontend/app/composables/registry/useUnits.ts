/**
 * Hungarian-first unit options for the Nyilvántartás (Registry) primaryUnit field.
 *
 * Story 9.5 AC #7: new products default to `db` (darab); existing `pcs` rows are
 * preserved as a read-only option at the callsite.
 */
export const UNIT_VALUES = ['db', 'kg', 'g', 'l', 'ml', 'm', 'm2', 'm3', 'csomag'] as const
export type UnitValue = typeof UNIT_VALUES[number]
export const DEFAULT_UNIT: UnitValue = 'db'

export function useUnits() {
  const { t } = useI18n()
  const options = computed(() =>
    UNIT_VALUES.map(value => ({ value, label: t(`registry.units.${value}`) })),
  )
  return { options }
}
