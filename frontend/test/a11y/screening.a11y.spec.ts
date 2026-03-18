import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { axe } from 'vitest-axe'
import { axeOptions } from './axe-config'
import VerdictCard from '~/components/Screening/VerdictCard.vue'

/**
 * Axe-core a11y integration test for the Screening Verdict display.
 * Covers: AC#1 (verdict contrast), AC#2 (icon pairing), AC#5 (ARIA-live), AC#6 (axe scan).
 */

// Mock stores
vi.mock('~/stores/screening', () => ({
  useScreeningStore: () => ({
    currentVerdict: null,
    currentProvenance: null,
    isSearching: false,
    searchError: null,
    search: vi.fn()
  })
}))

const mockVerdict = {
  taxNumber: '12345678',
  companyName: 'Test Kft.',
  status: 'RELIABLE',
  confidence: 'HIGH',
  riskSignals: [],
  cached: false,
  sha256Hash: 'abc123def456abc123def456abc123def456abc123def456abc123def456abc1',
  checkedAt: '2026-03-16T12:00:00Z'
}

describe('Screening verdict — axe-core a11y scan', () => {
  it('VerdictCard (RELIABLE status) passes axe scan', async () => {
    const wrapper = mount(VerdictCard, {
      props: { verdict: mockVerdict as any },
      global: {
        stubs: {
          Tag: { template: '<span><slot /></span>' },
          Button: { template: '<button :disabled="disabled"><slot />{{ label }}</button>', props: ['label', 'disabled', 'icon', 'severity', 'title'] }
        }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })

  it('VerdictCard (AT_RISK status) passes axe scan', async () => {
    const atRiskVerdict = { ...mockVerdict, status: 'AT_RISK', riskSignals: ['PUBLIC_DEBT_DETECTED'] }
    const wrapper = mount(VerdictCard, {
      props: { verdict: atRiskVerdict as any },
      global: {
        stubs: {
          Tag: { template: '<span><slot /></span>' },
          Button: { template: '<button :disabled="disabled"><slot />{{ label }}</button>', props: ['label', 'disabled', 'icon', 'severity', 'title'] }
        }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })

  it('VerdictCard (INCOMPLETE status) passes axe scan', async () => {
    const incompleteVerdict = { ...mockVerdict, status: 'INCOMPLETE', confidence: 'LOW' }
    const wrapper = mount(VerdictCard, {
      props: { verdict: incompleteVerdict as any },
      global: {
        stubs: {
          Tag: { template: '<span><slot /></span>' },
          Button: { template: '<button :disabled="disabled"><slot />{{ label }}</button>', props: ['label', 'disabled', 'icon', 'severity', 'title'] }
        }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })
})
