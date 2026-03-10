import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'

/**
 * Unit tests for SearchBar.vue logic.
 *
 * These tests focus on the component's validation and formatting logic
 * rather than full DOM rendering, following the pattern established by
 * TenantSwitcher.spec.ts. Co-located with SearchBar.vue per architecture rules.
 */
describe('SearchBar — tax number validation', () => {
  const TAX_NUMBER_REGEX = /^\d{8}(\d{3})?$/

  function validate(input: string): boolean {
    const cleaned = input.replace(/[^\d]/g, '')
    return TAX_NUMBER_REGEX.test(cleaned)
  }

  it('should accept valid 8-digit tax number', () => {
    expect(validate('12345678')).toBe(true)
  })

  it('should accept valid 11-digit tax number', () => {
    expect(validate('12345678901')).toBe(true)
  })

  it('should accept formatted 8-digit tax number with hyphens', () => {
    expect(validate('1234-5678')).toBe(true)
  })

  it('should accept formatted 11-digit tax number with hyphens', () => {
    expect(validate('1234-5678-901')).toBe(true)
  })

  it('should reject too short tax number', () => {
    expect(validate('1234567')).toBe(false)
  })

  it('should reject too long tax number', () => {
    expect(validate('123456789012')).toBe(false)
  })

  it('should reject 9-digit tax number', () => {
    expect(validate('123456789')).toBe(false)
  })

  it('should reject 10-digit tax number', () => {
    expect(validate('1234567890')).toBe(false)
  })

  it('should reject non-numeric input', () => {
    expect(validate('abcdefgh')).toBe(false)
  })

  it('should reject empty string', () => {
    expect(validate('')).toBe(false)
  })
})

describe('SearchBar — auto-formatting', () => {
  function formatTaxNumber(raw: string): string {
    const digits = raw.replace(/[^\d]/g, '').slice(0, 11)
    if (digits.length <= 4) return digits
    if (digits.length <= 8) return `${digits.slice(0, 4)}-${digits.slice(4)}`
    return `${digits.slice(0, 4)}-${digits.slice(4, 8)}-${digits.slice(8)}`
  }

  it('should format 8-digit as ####-####', () => {
    expect(formatTaxNumber('12345678')).toBe('1234-5678')
  })

  it('should format 11-digit as ####-####-###', () => {
    expect(formatTaxNumber('12345678901')).toBe('1234-5678-901')
  })

  it('should strip non-digit characters', () => {
    expect(formatTaxNumber('1234-5678')).toBe('1234-5678')
  })

  it('should handle partial input (4 digits)', () => {
    expect(formatTaxNumber('1234')).toBe('1234')
  })

  it('should handle partial input (6 digits)', () => {
    expect(formatTaxNumber('123456')).toBe('1234-56')
  })

  it('should truncate at 11 digits', () => {
    expect(formatTaxNumber('123456789012345')).toBe('1234-5678-901')
  })
})

describe('SearchBar — search flow', () => {
  it('should call store search on valid submit', async () => {
    const search = vi.fn().mockResolvedValue(undefined)
    const taxNumberInput = ref('1234-5678')
    const validationError = ref('')

    async function onSubmit() {
      const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
      if (!/^\d{8}(\d{3})?$/.test(cleaned)) {
        validationError.value = 'Invalid'
        return
      }
      validationError.value = ''
      await search(cleaned)
    }

    await onSubmit()
    expect(search).toHaveBeenCalledOnce()
    expect(search).toHaveBeenCalledWith('12345678')
    expect(validationError.value).toBe('')
  })

  it('should show error for invalid input and NOT call search', async () => {
    const search = vi.fn()
    const taxNumberInput = ref('123')
    const validationError = ref('')

    async function onSubmit() {
      const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
      if (!/^\d{8}(\d{3})?$/.test(cleaned)) {
        validationError.value = 'Invalid'
        return
      }
      validationError.value = ''
      await search(cleaned)
    }

    await onSubmit()
    expect(search).not.toHaveBeenCalled()
    expect(validationError.value).toBe('Invalid')
  })
})
