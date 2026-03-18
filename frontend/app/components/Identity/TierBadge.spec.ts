import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import TierBadge from './TierBadge.vue'

let mockTier: string | null = 'ALAP'
let mockIsAuthenticated = true

vi.stubGlobal('useAuthStore', () => ({
  tier: mockTier,
  isAuthenticated: mockIsAuthenticated
}))

vi.stubGlobal('useI18n', () => ({
  t: (key: string) => key
}))

function mountBadge() {
  return mount(TierBadge)
}

describe('TierBadge', () => {
  beforeEach(() => {
    mockTier = 'ALAP'
    mockIsAuthenticated = true
  })

  it('renders tier name via i18n for ALAP', () => {
    mockTier = 'ALAP'
    const wrapper = mountBadge()
    expect(wrapper.find('[data-testid="tier-badge"]').text()).toBe('common.tiers.ALAP')
  })

  it('renders tier name via i18n for PRO', () => {
    mockTier = 'PRO'
    const wrapper = mountBadge()
    expect(wrapper.find('[data-testid="tier-badge"]').text()).toBe('common.tiers.PRO')
  })

  it('renders tier name via i18n for PRO_EPR', () => {
    mockTier = 'PRO_EPR'
    const wrapper = mountBadge()
    expect(wrapper.find('[data-testid="tier-badge"]').text()).toBe('common.tiers.PRO_EPR')
  })

  it('applies slate color for ALAP tier', () => {
    mockTier = 'ALAP'
    const wrapper = mountBadge()
    const badge = wrapper.find('[data-testid="tier-badge"]')
    expect(badge.classes()).toContain('bg-slate-500')
    expect(badge.classes()).toContain('text-white')
  })

  it('applies indigo color for PRO tier', () => {
    mockTier = 'PRO'
    const wrapper = mountBadge()
    const badge = wrapper.find('[data-testid="tier-badge"]')
    expect(badge.classes()).toContain('bg-indigo-600')
    expect(badge.classes()).toContain('text-white')
  })

  it('applies emerald color for PRO_EPR tier', () => {
    mockTier = 'PRO_EPR'
    const wrapper = mountBadge()
    const badge = wrapper.find('[data-testid="tier-badge"]')
    expect(badge.classes()).toContain('bg-emerald-600')
    expect(badge.classes()).toContain('text-white')
  })

  it('hides badge when not authenticated', () => {
    mockIsAuthenticated = false
    const wrapper = mountBadge()
    expect(wrapper.find('[data-testid="tier-badge"]').exists()).toBe(false)
  })
})
