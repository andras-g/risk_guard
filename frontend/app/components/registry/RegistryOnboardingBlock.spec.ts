import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// ─── Stubs ──────────────────────────────────────────────────────────────────

const mockPush = vi.fn()
vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))
vi.stubGlobal('useRouter', () => ({ push: mockPush }))

// Stub auto-imported RegistryInvoiceBootstrapDialog
const BootstrapDialogStub = {
  name: 'RegistryInvoiceBootstrapDialog',
  props: ['visible'],
  emits: ['update:visible', 'completed'],
  template: '<div data-testid="bootstrap-dialog-stub" />',
}

// Stub PrimeVue Dialog
const DialogStub = {
  name: 'Dialog',
  props: ['visible'],
  emits: ['update:visible'],
  template: '<div v-if="visible" data-testid="dialog-stub"><slot /><slot name="footer" /></div>',
}

// Stub PrimeVue Button
const ButtonStub = {
  name: 'Button',
  props: ['label', 'icon', 'severity'],
  template: '<button @click="$emit(\'click\')">{{ label }}</button>',
  emits: ['click'],
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

async function mountBlock(context: 'filing' | 'registry') {
  const { default: RegistryOnboardingBlock } = await import('./RegistryOnboardingBlock.vue')
  return mount(RegistryOnboardingBlock, {
    props: { context },
    global: {
      stubs: {
        RegistryInvoiceBootstrapDialog: BootstrapDialogStub,
        Dialog: DialogStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('RegistryOnboardingBlock', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── (1) Renders title and filing body ────────────────────────────────────────
  it('renders headline and filing body when context is filing', async () => {
    const wrapper = await mountBlock('filing')
    expect(wrapper.text()).toContain('registry.onboarding.title')
    expect(wrapper.text()).toContain('registry.onboarding.body.filing')
  })

  // ── (2) Renders title and registry body ─────────────────────────────────────
  it('renders headline and registry body when context is registry', async () => {
    const wrapper = await mountBlock('registry')
    expect(wrapper.text()).toContain('registry.onboarding.title')
    expect(wrapper.text()).toContain('registry.onboarding.body.registry')
  })

  // ── (3) Primary CTA click shows bootstrap dialog ─────────────────────────────
  it('primary CTA click makes bootstrap dialog visible', async () => {
    const wrapper = await mountBlock('filing')
    const primaryCta = wrapper.find('[data-testid="onboarding-cta-bootstrap"]')
    expect(primaryCta.exists()).toBe(true)
    await primaryCta.trigger('click')
    const dialogStub = wrapper.findComponent(BootstrapDialogStub)
    expect(dialogStub.props('visible')).toBe(true)
  })

  // ── (4) Secondary CTA navigates to /registry/new ─────────────────────────────
  it('secondary CTA click navigates to /registry/new', async () => {
    const wrapper = await mountBlock('registry')
    const manualCta = wrapper.find('[data-testid="onboarding-cta-manual"]')
    await manualCta.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/registry/new')
  })

  // ── (5) Help link click shows help modal ─────────────────────────────────────
  it('help link click opens help modal', async () => {
    const wrapper = await mountBlock('registry')
    const helpLink = wrapper.find('[data-testid="onboarding-help-link"]')
    expect(helpLink.exists()).toBe(true)
    await helpLink.trigger('click')
    const dialogStub = wrapper.findComponent(DialogStub)
    expect(dialogStub.props('visible')).toBe(true)
  })

  // ── (6) Emits bootstrap-completed when dialog emits completed ────────────────
  it('emits bootstrap-completed when RegistryInvoiceBootstrapDialog emits completed', async () => {
    const wrapper = await mountBlock('filing')
    // Open bootstrap dialog first
    await wrapper.find('[data-testid="onboarding-cta-bootstrap"]').trigger('click')
    // Simulate the dialog emitting completed
    const dialogStub = wrapper.findComponent(BootstrapDialogStub)
    await dialogStub.vm.$emit('completed', {})
    expect(wrapper.emitted('bootstrap-completed')).toBeTruthy()
    expect(wrapper.emitted('bootstrap-completed')!.length).toBe(1)
  })
})
