import { describe, it, expect, vi } from 'vitest'
import { DEFAULT_UNIT, UNIT_VALUES, useUnits } from './useUnits'

vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))

describe('useUnits composable', () => {
  it('DEFAULT_UNIT is "db" (Hungarian-first)', () => {
    expect(DEFAULT_UNIT).toBe('db')
  })

  it('exposes the nine canonical Hungarian-first unit values', () => {
    expect([...UNIT_VALUES]).toEqual(['db', 'kg', 'g', 'l', 'ml', 'm', 'm2', 'm3', 'csomag'])
  })

  it('returns options with i18n-resolved labels under registry.units.*', () => {
    const { options } = useUnits()
    const values = options.value.map(o => o.value)
    const labels = options.value.map(o => o.label)
    expect(values).toEqual([...UNIT_VALUES])
    expect(labels).toEqual(UNIT_VALUES.map(v => `registry.units.${v}`))
  })
})
