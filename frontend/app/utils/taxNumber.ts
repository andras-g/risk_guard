import { z } from 'zod'

/**
 * Zod schema for Hungarian tax number validation.
 * Accepts 8-digit (adószám) or 11-digit (adóazonosító jel) formats.
 * Validation runs on cleaned (digits-only) input.
 */
export const taxNumberSchema = z.string().regex(
  /^\d{8}(\d{3})?$/,
  'screening.search.invalidTaxNumber'
)

/**
 * Auto-format the tax number with visual masking:
 * 8-digit: 1234-5678
 * 11-digit: 1234-5678-901
 */
export function formatTaxNumber(raw: string): string {
  const digits = raw.replace(/[^\d]/g, '').slice(0, 11)
  if (digits.length <= 4) return digits
  if (digits.length <= 8) return `${digits.slice(0, 4)}-${digits.slice(4)}`
  return `${digits.slice(0, 4)}-${digits.slice(4, 8)}-${digits.slice(8)}`
}
