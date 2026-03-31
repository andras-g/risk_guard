import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DataSourceHealthDashboard from './DataSourceHealthDashboard.vue'
import type { AdapterHealth } from '~/stores/health'

// Stub PrimeVue components
const CardStub = {
  template: `<div data-testid="adapter-card"><slot name="title" /><slot name="content" /></div>`,
}
const ProgressBarStub = {
  template: '<div data-testid="progress-bar" :data-value="$props.value" />',
  props: ['value', 'showValue'],
}
const SkeletonStub = {
  template: '<div data-testid="skeleton" />',
  props: ['height'],
}

// Stub useI18n
vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params) return key.replace(/\{(\w+)\}/g, (_, k) => params[k] ?? k)
    return key
  },
}))

// Stub useDateRelative
vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string) => `relative(${date})`,
  }),
}))

function buildAdapter(overrides: Partial<AdapterHealth> = {}): AdapterHealth {
  return {
    adapterName: 'demo',
    circuitBreakerState: 'CLOSED',
    successRatePct: 100,
    failureCount: 0,
    lastSuccessAt: '2026-03-31T10:00:00Z',
    lastFailureAt: null,
    mtbfHours: null,
    dataSourceMode: 'DEMO',
    credentialStatus: 'NOT_CONFIGURED',
    ...overrides,
  }
}

function mountDashboard(props: { adapters?: AdapterHealth[]; loading?: boolean } = {}) {
  return mount(DataSourceHealthDashboard, {
    props: {
      adapters: props.adapters ?? [buildAdapter()],
      loading: props.loading ?? false,
    },
    global: {
      stubs: {
        Card: CardStub,
        ProgressBar: ProgressBarStub,
        Skeleton: SkeletonStub,
      },
    },
  })
}

describe('DataSourceHealthDashboard', () => {
  it('renders a card for each adapter', () => {
    const adapters = [
      buildAdapter({ adapterName: 'demo' }),
      buildAdapter({ adapterName: 'nav-online-szamla', dataSourceMode: 'LIVE' }),
    ]
    const wrapper = mountDashboard({ adapters })

    const cards = wrapper.findAll('[data-testid="adapter-card"]')
    expect(cards).toHaveLength(2)
  })

  it('shows skeleton placeholders when loading', () => {
    const wrapper = mountDashboard({ loading: true })

    expect(wrapper.findAll('[data-testid="skeleton"]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('[data-testid="adapter-card"]')).toHaveLength(0)
  })

  it('shows Demo Mode badge when mode is DEMO', () => {
    const wrapper = mountDashboard({ adapters: [buildAdapter({ dataSourceMode: 'DEMO' })] })

    expect(wrapper.text()).toContain('admin.datasources.states.demoMode')
  })

  it('does not show Demo Mode badge when mode is LIVE', () => {
    const wrapper = mountDashboard({ adapters: [buildAdapter({ dataSourceMode: 'LIVE' })] })

    expect(wrapper.text()).not.toContain('admin.datasources.states.demoMode')
  })

  it('ARIA-live region updates when adapter state changes between renders', async () => {
    const adapter = buildAdapter({ circuitBreakerState: 'CLOSED' })
    const wrapper = mountDashboard({ adapters: [adapter] })

    // Trigger a state change
    await wrapper.setProps({
      adapters: [{ ...adapter, circuitBreakerState: 'OPEN' }],
    })

    const ariaRegion = wrapper.find('[aria-live="polite"]')
    expect(ariaRegion.exists()).toBe(true)
    // The t() mock returns the i18n key when params are present (key has no placeholders)
    expect(ariaRegion.text()).not.toBe('')
  })

  it('ARIA-live region is empty on first render (no previous state to compare)', () => {
    const wrapper = mountDashboard()

    const ariaRegion = wrapper.find('[aria-live="polite"]')
    expect(ariaRegion.text()).toBe('')
  })

  it('renders last success as relative time', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ lastSuccessAt: '2026-03-31T10:00:00Z' })],
    })

    expect(wrapper.text()).toContain('relative(2026-03-31T10:00:00Z)')
  })

  it('shows dash when lastSuccessAt is null', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ lastSuccessAt: null })],
    })

    expect(wrapper.text()).toContain('—')
  })

  it('shows NOT_CONFIGURED label for credential status', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ credentialStatus: 'NOT_CONFIGURED' })],
    })

    expect(wrapper.text()).toContain('admin.datasources.states.notConfigured')
  })
})
