import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { nextTick } from 'vue'

/**
 * Story 10.2 AC #13 — KfCodeWizardDialog spec.
 * Covers:
 *  (a) Opens: mount with visible=true → startResolveOnly called + isResolveOnlyMode true.
 *  (b) Walks 3 steps: selectOption drives activeStep 1→2→3→4.
 *  (c) Emits 'resolved' on confirm via store.resolveAndClose().
 *  (d) Emits 'update:visible(false)' on close + cancelWizard called.
 *  (e) Row-deletion is a parent-side concern — covered in [id].spec.ts AC #19.
 */

// Mock $fetch to satisfy startResolveOnly's /wizard/start POST.
const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)
vi.stubGlobal('useRuntimeConfig', () => ({ public: { apiBase: 'http://localhost:8080' } }))
vi.stubGlobal('useI18n', () => ({ t: (k: string) => k, locale: { value: 'hu' } }))

// useEprStore is imported transitively via eprWizard.ts.
vi.mock('~/stores/epr', () => ({
  useEprStore: () => ({ fetchMaterials: vi.fn() }),
}))

// PrimeVue Dialog stub: render the slot when visible so we can query it.
vi.mock('primevue/dialog', () => ({
  default: {
    name: 'Dialog',
    template: '<div v-if="visible" data-testid="kf-wizard-dialog"><slot /></div>',
    props: ['visible', 'modal', 'appendTo', 'header', 'pt', 'style', 'breakpoints'],
    emits: ['update:visible', 'hide'],
  },
}))

// Stub WizardStepper — the dialog wrapper's behaviour is independent of the
// stepper's internals. We inspect store interactions directly.
vi.mock('./WizardStepper.vue', () => ({
  default: { name: 'WizardStepper', template: '<div data-testid="stepper-stub" />' },
}))

import KfCodeWizardDialog from './KfCodeWizardDialog.vue'
import { useEprWizardStore } from '~/stores/eprWizard'

describe('KfCodeWizardDialog — Story 10.2', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockFetch.mockReset()
    mockFetch.mockResolvedValue({
      configVersion: 1,
      options: [{ code: '11', label: 'Packaging', description: null }],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // ─── (a) Opens ───────────────────────────────────────────────────────────
  it('calls startResolveOnly on visible: false → true and sets isResolveOnlyMode', async () => {
    const store = useEprWizardStore()
    const wrapper = mount(KfCodeWizardDialog, { props: { visible: false } })

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(store.isResolveOnlyMode).toBe(true)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/epr/wizard/start', expect.any(Object))
  })

  // ─── (b) Walks 3 steps ───────────────────────────────────────────────────
  it('activeStep advances 1→2→3→4 via store.selectOption chain', async () => {
    const store = useEprWizardStore()
    const wrapper = mount(KfCodeWizardDialog, { props: { visible: false } })
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(store.activeStep).toBe('1')

    // Simulate backend step responses — exactly as selectOption would receive.
    mockFetch.mockResolvedValueOnce({
      options: [{ code: '01', label: 'Paper', description: null }],
      nextLevel: 'material_stream',
      breadcrumb: [{ level: 'product_stream', code: '11', label: 'Packaging' }],
    })
    await store.selectOption({ level: 'product_stream', code: '11', label: 'Packaging' })
    expect(store.activeStep).toBe('2')

    mockFetch.mockResolvedValueOnce({
      options: [{ code: '01', label: 'Corrugated', description: null }],
      nextLevel: 'group',
      breadcrumb: [
        { level: 'product_stream', code: '11', label: 'Packaging' },
        { level: 'material_stream', code: '01', label: 'Paper' },
      ],
    })
    await store.selectOption({ level: 'material_stream', code: '01', label: 'Paper' })
    expect(store.activeStep).toBe('3')

    mockFetch.mockResolvedValueOnce({
      options: [{ code: '01', label: 'Household', description: null }],
      nextLevel: 'subgroup',
      breadcrumb: [
        { level: 'product_stream', code: '11', label: 'Packaging' },
        { level: 'material_stream', code: '01', label: 'Paper' },
        { level: 'group', code: '01', label: 'Corrugated' },
      ],
    })
    await store.selectOption({ level: 'group', code: '01', label: 'Corrugated' })
    expect(store.activeStep).toBe('3') // group→subgroup reuses step 3

    // Subgroup selection advances to 4 via resolveResult (no /step call, direct /resolve).
    mockFetch.mockResolvedValueOnce({
      kfCode: '11010101',
      feeRate: 20.44,
      materialClassification: 'Paper',
      traversalPath: [],
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    })
    await store.selectOption({ level: 'subgroup', code: '01', label: 'Household' })
    expect(store.activeStep).toBe('4')
  })

  // ─── (c) Emits 'resolved' when store.resolveAndClose writes lastResolvedKfCode ──
  it('emits resolved(payload) when lastResolvedKfCode is written', async () => {
    const store = useEprWizardStore()
    const wrapper = mount(KfCodeWizardDialog, { props: { visible: false } })
    await wrapper.setProps({ visible: true })
    await flushPromises()

    // Simulate a full resolution ready-to-commit.
    store.isResolveOnlyMode = true
    store.resolvedResult = {
      kfCode: '11010101',
      feeCode: '1101',
      feeRate: 20.44,
      currency: 'HUF',
      materialClassification: 'Paper',
      traversalPath: [],
      legislationRef: 'test',
      confidenceScore: 'HIGH',
      confidenceReason: 'full_traversal',
    }

    store.resolveAndClose()
    await nextTick()
    await nextTick() // lastResolvedKfCode → $reset → second tick for emit chain

    const resolved = wrapper.emitted('resolved')
    expect(resolved).toBeTruthy()
    expect(resolved![0]).toEqual([{ kfCode: '11010101', materialClassification: 'Paper', feeRate: 20.44 }])
    const visibleUpdate = wrapper.emitted('update:visible')
    expect(visibleUpdate).toBeTruthy()
    expect(visibleUpdate![visibleUpdate!.length - 1]).toEqual([false])
  })

  // ─── (d) Emits update:visible(false) on dialog close + calls cancelWizard ────
  it('emits update:visible(false) + calls cancelWizard when dialog emits update:visible(false)', async () => {
    const store = useEprWizardStore()
    const wrapper = mount(KfCodeWizardDialog, { props: { visible: false } })
    await wrapper.setProps({ visible: true })
    await flushPromises()

    store.isResolveOnlyMode = true
    store.activeStep = '1'

    const dialog = wrapper.findComponent({ name: 'Dialog' })
    dialog.vm.$emit('update:visible', false)
    await nextTick()

    const visibleUpdate = wrapper.emitted('update:visible')
    expect(visibleUpdate).toBeTruthy()
    expect(visibleUpdate![0]).toEqual([false])
    // cancelWizard → $reset clears isResolveOnlyMode and activeStep.
    expect(store.isResolveOnlyMode).toBe(false)
    expect(store.activeStep).toBeNull()
  })

  // ─── In-wizard Cancel button must close the dialog (review finding) ──────
  // When the user clicks the stepper's Cancel button (either Step 4 footer Cancel
  // or the inline bottom Cancel), the store's cancelWizard()/$reset() flips
  // isActive=false — the dialog watches this and emits update:visible(false)
  // so the parent closes the dialog.
  it('emits update:visible(false) when the wizard becomes inactive while visible', async () => {
    const store = useEprWizardStore()
    const wrapper = mount(KfCodeWizardDialog, { props: { visible: false } })
    await wrapper.setProps({ visible: true })
    await flushPromises()

    // startResolveOnly populated activeStep='1', so isActive=true.
    expect(store.isActive).toBe(true)

    // User clicks an in-wizard Cancel button — cancelWizard() calls $reset().
    store.cancelWizard()
    await nextTick()

    expect(store.isActive).toBe(false)
    const visibleUpdate = wrapper.emitted('update:visible')
    expect(visibleUpdate).toBeTruthy()
    expect(visibleUpdate![visibleUpdate!.length - 1]).toEqual([false])
  })
})
