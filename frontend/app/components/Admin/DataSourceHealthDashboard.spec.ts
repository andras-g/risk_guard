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
const InputSwitchStub = {
  template: '<input type="checkbox" data-testid="quarantine-toggle" :checked="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
  props: ['modelValue', 'disabled', 'inputId'],
  emits: ['update:modelValue'],
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

function mountDashboard(props: {
  adapters?: AdapterHealth[]
  loading?: boolean
  quarantining?: Record<string, boolean>
  canQuarantine?: boolean
} = {}) {
  return mount(DataSourceHealthDashboard, {
    props: {
      adapters: props.adapters ?? [buildAdapter()],
      loading: props.loading ?? false,
      quarantining: props.quarantining ?? {},
      ...(props.canQuarantine !== undefined ? { canQuarantine: props.canQuarantine } : {}),
    },
    global: {
      stubs: {
        Card: CardStub,
        InputSwitch: InputSwitchStub,
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

  it('renders a quarantine toggle per adapter card', () => {
    const wrapper = mountDashboard({ adapters: [buildAdapter()] })

    expect(wrapper.findAll('[data-testid="quarantine-toggle"]')).toHaveLength(1)
  })

  it('quarantine toggle is disabled when quarantining[adapterName] is true', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ adapterName: 'demo' })],
      quarantining: { demo: true },
    })

    const toggle = wrapper.find('[data-testid="quarantine-toggle"]')
    expect((toggle.element as HTMLInputElement).disabled).toBe(true)
  })

  it('quarantine toggle is enabled when quarantining[adapterName] is false', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ adapterName: 'demo' })],
      quarantining: { demo: false },
    })

    const toggle = wrapper.find('[data-testid="quarantine-toggle"]')
    expect((toggle.element as HTMLInputElement).disabled).toBe(false)
  })

  it('quarantine toggle emits quarantine event on click', async () => {
    const wrapper = mountDashboard({ adapters: [buildAdapter({ adapterName: 'demo' })] })

    const toggle = wrapper.find('[data-testid="quarantine-toggle"]')
    await toggle.trigger('change')

    const emitted = wrapper.emitted('quarantine')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0]).toBe('demo')
  })

  it('hides quarantine toggle when canQuarantine is false', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ adapterName: 'demo' })],
      canQuarantine: false,
    })

    expect(wrapper.find('[data-testid="quarantine-toggle"]').exists()).toBe(false)
  })

  it('FORCED_OPEN state shows orange badge with Quarantined label', () => {
    const wrapper = mountDashboard({
      adapters: [buildAdapter({ circuitBreakerState: 'FORCED_OPEN' })],
    })

    expect(wrapper.text()).toContain('admin.datasources.states.quarantined')
    // orange badge class
    expect(wrapper.html()).toContain('bg-orange-100')
  })
})
