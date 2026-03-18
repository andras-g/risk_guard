import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import VerdictCard from './VerdictCard.vue'
import type { VerdictResponse } from '~/types/api'

/**
 * Component tests for VerdictCard.vue (TheShieldCard).
 * Tests shield color/icon mapping, risk signals, TAX_SUSPENDED badge,
 * Freshness Guard override, action buttons, cached indicator, and hash copy.
 *
 * Co-located per architecture rules.
 */

// Stub PrimeVue components
const ButtonStub = { template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="$attrs.disabled" :title="$attrs.title"><slot /></button>', inheritAttrs: true }
const TagStub = { template: '<span class="tag-stub"><slot /></span>' }

// Stub useI18n
vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) {
      return key.replace(/{(\w+)}/g, (_, k) => params[k] ?? k)
    }
    return key
  },
}))

// Stub risk-guard-tokens import
vi.mock('~/risk-guard-tokens.json', () => ({
  default: {
    freshness: { freshThresholdHours: 6, staleThresholdHours: 24, unavailableThresholdHours: 48 },
  },
}))

// Mock clipboard
const mockWriteText = vi.fn().mockResolvedValue(undefined)
Object.defineProperty(navigator, 'clipboard', {
  value: { writeText: mockWriteText },
  writable: true,
})

function buildVerdict(overrides: Partial<VerdictResponse> = {}): VerdictResponse {
  return {
    id: 'verdict-uuid-1',
    snapshotId: 'snapshot-uuid-1',
    taxNumber: '12345678',
    status: 'RELIABLE',
    confidence: 'FRESH',
    createdAt: '2026-03-13T12:00:00Z',
    riskSignals: [],
    cached: false,
    companyName: 'Test Company Kft.',
    sha256Hash: 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1',
    ...overrides,
  }
}

function mountCard(verdict: VerdictResponse) {
  return mount(VerdictCard, {
    props: { verdict },
    global: {
      stubs: {
        Button: ButtonStub,
        Tag: TagStub,
      },
    },
  })
}

describe('VerdictCard — shield icon and status colors', () => {
  it('should render Emerald shield for RELIABLE status', () => {
    const wrapper = mountCard(buildVerdict({ status: 'RELIABLE', confidence: 'FRESH' }))
    const card = wrapper.find('[data-testid="verdict-card"]')
    expect(card.classes()).toContain('border-emerald-700')
    expect(wrapper.find('[data-testid="shield-icon"]').classes()).toContain('pi-check-circle')
  })

  it('should render Crimson shield for AT_RISK status', () => {
    const wrapper = mountCard(buildVerdict({ status: 'AT_RISK', confidence: 'FRESH', riskSignals: ['PUBLIC_DEBT_DETECTED'] }))
    const card = wrapper.find('[data-testid="verdict-card"]')
    expect(card.classes()).toContain('border-rose-700')
    expect(wrapper.find('[data-testid="shield-icon"]').classes()).toContain('pi-times-circle')
  })

  it('should render Grey shield for INCOMPLETE status', () => {
    const wrapper = mountCard(buildVerdict({ status: 'INCOMPLETE', confidence: 'FRESH' }))
    const card = wrapper.find('[data-testid="verdict-card"]')
    expect(card.classes()).toContain('border-slate-400')
    expect(wrapper.find('[data-testid="shield-icon"]').classes()).toContain('pi-clock')
  })

  it('should render Amber shield for TAX_SUSPENDED status', () => {
    const wrapper = mountCard(buildVerdict({ status: 'TAX_SUSPENDED', confidence: 'FRESH', riskSignals: ['TAX_NUMBER_SUSPENDED'] }))
    const card = wrapper.find('[data-testid="verdict-card"]')
    expect(card.classes()).toContain('border-amber-600')
    expect(wrapper.find('[data-testid="shield-icon"]').classes()).toContain('pi-exclamation-triangle')
  })
})

describe('VerdictCard — tax number display', () => {
  it('should display tax number in monospace font', () => {
    const wrapper = mountCard(buildVerdict({ taxNumber: '12345678' }))
    const taxEl = wrapper.find('[data-testid="tax-number"]')
    expect(taxEl.exists()).toBe(true)
    expect(taxEl.classes()).toContain('font-mono')
    expect(taxEl.text()).toBe('12345678')
  })
})

describe('VerdictCard — SHA-256 hash', () => {
  it('should display truncated hash', () => {
    const hash = 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1'
    const wrapper = mountCard(buildVerdict({ sha256Hash: hash }))
    const hashEl = wrapper.find('[data-testid="truncated-hash"]')
    expect(hashEl.exists()).toBe(true)
    expect(hashEl.text()).toBe('abc123def456abc1...')
  })

  it('should copy full hash to clipboard on button click', async () => {
    const hash = 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1'
    const wrapper = mountCard(buildVerdict({ sha256Hash: hash }))
    await wrapper.find('[data-testid="copy-hash-button"]').trigger('click')
    expect(mockWriteText).toHaveBeenCalledWith(hash)
  })

  it('should have aria-label on copy hash button for screen reader accessibility', () => {
    const wrapper = mountCard(buildVerdict())
    const copyBtn = wrapper.find('[data-testid="copy-hash-button"]')
    expect(copyBtn.attributes('aria-label')).toContain('screening.actions.copyHash')
  })

  it('should have type="button" on copy hash button to prevent accidental form submission', () => {
    const wrapper = mountCard(buildVerdict())
    const copyBtn = wrapper.find('[data-testid="copy-hash-button"]')
    expect(copyBtn.attributes('type')).toBe('button')
  })

  it('should not render hash section when sha256Hash is null', () => {
    const wrapper = mountCard(buildVerdict({ sha256Hash: null }))
    expect(wrapper.find('[data-testid="truncated-hash"]').exists()).toBe(false)
  })
})

describe('VerdictCard — risk signals', () => {
  it('should display risk signals section with human-readable labels', () => {
    const wrapper = mountCard(buildVerdict({
      status: 'AT_RISK',
      riskSignals: ['PUBLIC_DEBT_DETECTED', 'INSOLVENCY_PROCEEDINGS_ACTIVE'],
    }))
    const signalsList = wrapper.find('[data-testid="risk-signals-list"]')
    expect(signalsList.exists()).toBe(true)
    expect(wrapper.html()).toContain('screening.riskSignals.PUBLIC_DEBT_DETECTED')
    expect(wrapper.html()).toContain('screening.riskSignals.INSOLVENCY_PROCEEDINGS_ACTIVE')
  })

  it('should handle SOURCE_UNAVAILABLE signal with dynamic name', () => {
    const wrapper = mountCard(buildVerdict({
      status: 'AT_RISK',
      riskSignals: ['SOURCE_UNAVAILABLE:nav-debt'],
    }))
    expect(wrapper.html()).toContain('nav-debt')
  })

  it('should show no risk signals indicator for RELIABLE verdict with correct message', () => {
    const wrapper = mountCard(buildVerdict({ status: 'RELIABLE', riskSignals: [] }))
    const el = wrapper.find('[data-testid="no-risk-signals"]')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('screening.riskSignals.noSignals')
  })

  it('should show no risk signals indicator for INCOMPLETE verdict with empty risk signals', () => {
    // Ensures the "no signals" message is not semantically wrong for non-RELIABLE statuses
    const wrapper = mountCard(buildVerdict({ status: 'INCOMPLETE', riskSignals: [] }))
    const el = wrapper.find('[data-testid="no-risk-signals"]')
    expect(el.exists()).toBe(true)
    // Should NOT show 'screening.verdict.reliable' for a non-RELIABLE status
    expect(el.text()).not.toContain('screening.verdict.reliable')
    expect(el.text()).toContain('screening.riskSignals.noSignals')
  })
})

describe('VerdictCard — cached indicator', () => {
  it('should show cached indicator when cached is true', () => {
    const wrapper = mountCard(buildVerdict({ cached: true, riskSignals: [] }))
    expect(wrapper.find('[data-testid="cached-indicator"]').exists()).toBe(true)
    expect(wrapper.html()).toContain('screening.verdict.cached')
  })

  it('should show "no signals for cached" when cached=true and riskSignals empty', () => {
    const wrapper = mountCard(buildVerdict({ cached: true, riskSignals: [] }))
    expect(wrapper.find('[data-testid="cached-no-signals"]').exists()).toBe(true)
    expect(wrapper.html()).toContain('screening.riskSignals.noSignalsForCached')
  })

  it('should not show cached indicator when cached is false', () => {
    const wrapper = mountCard(buildVerdict({ cached: false }))
    expect(wrapper.find('[data-testid="cached-indicator"]').exists()).toBe(false)
  })
})

describe('VerdictCard — Freshness Guard', () => {
  it('should force Grey Shield when confidence is UNAVAILABLE regardless of status', () => {
    const wrapper = mountCard(buildVerdict({
      status: 'RELIABLE',
      confidence: 'UNAVAILABLE',
    }))
    const card = wrapper.find('[data-testid="verdict-card"]')
    // Grey shield override takes precedence over RELIABLE emerald
    expect(card.classes()).toContain('border-slate-400')
    expect(wrapper.find('[data-testid="shield-icon"]').classes()).toContain('pi-clock')
  })

  it('should show stale warning banner when confidence is UNAVAILABLE', () => {
    const wrapper = mountCard(buildVerdict({ confidence: 'UNAVAILABLE' }))
    expect(wrapper.find('[data-testid="stale-warning-banner"]').exists()).toBe(true)
    expect(wrapper.html()).toContain('screening.verdict.staleWarning')
  })

  it('should not show stale warning banner when confidence is FRESH', () => {
    const wrapper = mountCard(buildVerdict({ confidence: 'FRESH' }))
    expect(wrapper.find('[data-testid="stale-warning-banner"]').exists()).toBe(false)
  })
})

describe('VerdictCard — TAX_SUSPENDED badge', () => {
  it('should show manual review badge for TAX_SUSPENDED status', () => {
    const wrapper = mountCard(buildVerdict({ status: 'TAX_SUSPENDED', confidence: 'FRESH' }))
    expect(wrapper.find('[data-testid="tax-suspended-badge"]').exists()).toBe(true)
    expect(wrapper.html()).toContain('screening.verdict.manualReviewRequired')
  })

  it('should not show suspended badge for other statuses', () => {
    const wrapper = mountCard(buildVerdict({ status: 'AT_RISK' }))
    expect(wrapper.find('[data-testid="tax-suspended-badge"]').exists()).toBe(false)
  })

  it('should not show suspended badge when stale override is active (UNAVAILABLE confidence)', () => {
    // When confidence is UNAVAILABLE, the stale override takes precedence and hides the suspended badge
    const wrapper = mountCard(buildVerdict({ status: 'TAX_SUSPENDED', confidence: 'UNAVAILABLE' }))
    expect(wrapper.find('[data-testid="tax-suspended-badge"]').exists()).toBe(false)
  })
})

describe('VerdictCard — HASH_UNAVAILABLE sentinel', () => {
  it('should show hash-unavailable element and hide copy button when sha256Hash is HASH_UNAVAILABLE', () => {
    const wrapper = mountCard(buildVerdict({ sha256Hash: 'HASH_UNAVAILABLE' }))

    // The unavailable state element should exist
    expect(wrapper.find('[data-testid="hash-unavailable"]').exists()).toBe(true)
    // The i18n key for unavailable should be rendered
    expect(wrapper.html()).toContain('screening.verdict.hashUnavailable')
    // Copy button should NOT be rendered (copying the sentinel string is meaningless)
    expect(wrapper.find('[data-testid="copy-hash-button"]').exists()).toBe(false)
    // The real hash element should NOT be present
    expect(wrapper.find('[data-testid="truncated-hash"]').exists()).toBe(false)
  })

  it('should show copy button and truncated-hash for a real 64-char hex hash', () => {
    const hash = 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1'
    const wrapper = mountCard(buildVerdict({ sha256Hash: hash }))

    // Real hash: truncated-hash element present, copy button present
    expect(wrapper.find('[data-testid="truncated-hash"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="copy-hash-button"]').exists()).toBe(true)
    // The unavailable element should NOT be present
    expect(wrapper.find('[data-testid="hash-unavailable"]').exists()).toBe(false)
  })

  it('should hide the entire hash section when sha256Hash is null', () => {
    const wrapper = mountCard(buildVerdict({ sha256Hash: null }))

    expect(wrapper.find('[data-testid="truncated-hash"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="hash-unavailable"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copy-hash-button"]').exists()).toBe(false)
  })
})

describe('VerdictCard — copyHash defensive guard', () => {
  it('should NOT copy sentinel to clipboard even if copyHash is called directly', async () => {
    // This tests the defensive isRealHash guard inside copyHash() itself —
    // separate from the v-if template guard that hides the button.
    mockWriteText.mockClear()
    const wrapper = mountCard(buildVerdict({ sha256Hash: 'HASH_UNAVAILABLE' }))

    // Directly invoke the component's exposed copyHash via vm — simulates programmatic call
    await (wrapper.vm as unknown as { copyHash: () => Promise<void> }).copyHash()

    // Then — clipboard.writeText must NOT have been called with the sentinel
    expect(mockWriteText).not.toHaveBeenCalled()
  })
})

describe('VerdictCard — action buttons', () => {
  it('should show disabled Export PDF button', () => {
    const wrapper = mountCard(buildVerdict())
    const pdfBtn = wrapper.find('[data-testid="export-pdf-button"]')
    expect(pdfBtn.exists()).toBe(true)
    expect(pdfBtn.attributes('disabled')).toBeDefined()
  })

  it('should show disabled Add to Watchlist button', () => {
    const wrapper = mountCard(buildVerdict())
    const watchlistBtn = wrapper.find('[data-testid="add-watchlist-button"]')
    expect(watchlistBtn.exists()).toBe(true)
    expect(watchlistBtn.attributes('disabled')).toBeDefined()
  })

  it('should show tooltip on Export PDF button', () => {
    const wrapper = mountCard(buildVerdict())
    const pdfBtn = wrapper.find('[data-testid="export-pdf-button"]')
    expect(pdfBtn.attributes('title')).toContain('screening.actions.exportPdfTooltip')
  })

  it('should show tooltip on Add to Watchlist button', () => {
    const wrapper = mountCard(buildVerdict())
    const watchlistBtn = wrapper.find('[data-testid="add-watchlist-button"]')
    expect(watchlistBtn.attributes('title')).toContain('screening.actions.addToWatchlistTooltip')
  })
})
