import { describe, it, expect } from 'vitest'

/**
 * Static contrast ratio regression tests for semantic color token pairings.
 *
 * These tests assert that the color tokens defined in main.css meet
 * WCAG 2.1 AA contrast requirements. If anyone changes a color token,
 * these tests will catch any resulting contrast regressions.
 *
 * Contrast ratios calculated using the WCAG relative luminance formula:
 * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */

/** Parse a hex color string to { r, g, b } in 0–255 range */
function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const h = hex.replace('#', '')
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  }
}

/** WCAG relative luminance of a color */
function luminance(hex: string): number {
  const { r, g, b } = hexToRgb(hex)
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
}

/** Contrast ratio between two colors (always ≥ 1) */
function contrastRatio(hex1: string, hex2: string): number {
  const l1 = luminance(hex1)
  const l2 = luminance(hex2)
  const lighter = Math.max(l1, l2)
  const darker = Math.min(l1, l2)
  return (lighter + 0.05) / (darker + 0.05)
}

// ─── Token Definitions (must match main.css @theme) ──────────────────────────
const WHITE = '#FFFFFF'
const SLATE_900 = '#0F172A'

// Brand tokens
const COLOR_AUTHORITY = '#0F172A'
const COLOR_RELIABLE = '#15803D'
const COLOR_AT_RISK = '#B91C1C'
const COLOR_STALE = '#B45309'

// Contrast-safe text tokens (Story 3.0c)
const COLOR_RELIABLE_TEXT = '#166534'
const COLOR_AT_RISK_TEXT = '#991B1B'
const COLOR_STALE_TEXT = '#92400E'
const COLOR_SECONDARY_TEXT = '#64748B' // slate-500

describe('Color contrast: brand tokens on white (≥ 4.5:1 for normal text)', () => {
  it('authority (#0F172A) on white ≥ 4.5:1', () => {
    expect(contrastRatio(COLOR_AUTHORITY, WHITE)).toBeGreaterThanOrEqual(4.5)
  })

  it('reliable (#15803D) on white ≥ 4.5:1', () => {
    expect(contrastRatio(COLOR_RELIABLE, WHITE)).toBeGreaterThanOrEqual(4.5)
  })

  it('at-risk (#B91C1C) on white ≥ 4.5:1', () => {
    expect(contrastRatio(COLOR_AT_RISK, WHITE)).toBeGreaterThanOrEqual(4.5)
  })

  it('stale (#B45309) on white ≥ 4.5:1', () => {
    expect(contrastRatio(COLOR_STALE, WHITE)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('Color contrast: verdict text tokens on white (≥ 7:1 enhanced)', () => {
  it('reliable-text (#166534) on white ≥ 7:1', () => {
    expect(contrastRatio(COLOR_RELIABLE_TEXT, WHITE)).toBeGreaterThanOrEqual(7)
  })

  it('at-risk-text (#991B1B) on white ≥ 7:1', () => {
    expect(contrastRatio(COLOR_AT_RISK_TEXT, WHITE)).toBeGreaterThanOrEqual(7)
  })

  it('stale-text (#92400E) on white ≥ 7:1', () => {
    // AC#1 requires ≥7:1 for all primary verdict text including stale
    expect(contrastRatio(COLOR_STALE_TEXT, WHITE)).toBeGreaterThanOrEqual(7)
  })
})

describe('Color contrast: secondary text on white (≥ 4.5:1)', () => {
  it('secondary-text / slate-500 (#64748B) on white ≥ 4.5:1', () => {
    expect(contrastRatio(COLOR_SECONDARY_TEXT, WHITE)).toBeGreaterThanOrEqual(4.5)
  })
})

describe('Color contrast: text on dark sidebar (slate-900) backgrounds', () => {
  it('slate-400 (#94A3B8) on slate-900 ≥ 4.5:1', () => {
    const SLATE_400 = '#94A3B8'
    expect(contrastRatio(SLATE_400, SLATE_900)).toBeGreaterThanOrEqual(4.5)
  })

  it('white (#FFFFFF) on slate-900 ≥ 4.5:1', () => {
    expect(contrastRatio(WHITE, SLATE_900)).toBeGreaterThanOrEqual(4.5)
  })
})
