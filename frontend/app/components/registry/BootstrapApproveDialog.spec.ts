import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BootstrapApproveDialog from './BootstrapApproveDialog.vue'
import type { BootstrapCandidateResponse } from '~/composables/api/useBootstrap'

// ─── Composable mocks ────────────────────────────────────────────────────────

const mockApproveCandidate = vi.fn()

vi.stubGlobal('useBootstrap', () => ({
  approveCandidate: mockApproveCandidate,
  triggerBootstrap: vi.fn(),
  listCandidates: vi.fn(),
  rejectCandidate: vi.fn(),
}))

vi.stubGlobal('useI18n', () => ({ t: (key: string) => key }))
vi.stubGlobal('useToast', () => ({ add: vi.fn() }))

// ─── Component stubs ──────────────────────────────────────────────────────────

const DialogStub = {
  template: '<div data-testid="approve-dialog" v-if="visible"><slot /><slot name="footer" /></div>',
  props: ['visible', 'header', 'modal', 'closable', 'style'],
  emits: ['hide'],
}
const InputTextStub = {
  template: '<input :data-testid="id" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', $event.target.value)" @blur="$emit(\'blur\')" />',
  props: ['id', 'modelValue', 'ariaInvalid', 'ariaDescribedby'],
  emits: ['update:modelValue', 'blur'],
}
const InputNumberStub = {
  template: '<input type="number" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  props: ['modelValue', 'min', 'minFractionDigits', 'maxFractionDigits'],
  emits: ['update:modelValue'],
}
const SelectStub = {
  template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="o in (options ?? [])" :key="o.value" :value="o.value">{{ o.label }}</option></select>',
  props: ['modelValue', 'options', 'optionLabel', 'optionValue'],
  emits: ['update:modelValue'],
}
const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
  inheritAttrs: true,
  emits: ['click'],
}
const KfCodeInputStub = {
  template: '<input data-testid="kf-input" :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  props: ['id', 'modelValue'],
  emits: ['update:modelValue'],
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function buildCandidate(overrides: Partial<BootstrapCandidateResponse> = {}): BootstrapCandidateResponse {
  return {
    id: 'cand-001',
    tenantId: 'tenant-001',
    productName: 'Aktivia 125g',
    vtsz: '39239090',
    frequency: 5,
    totalQuantity: 500,
    unitOfMeasure: 'DARAB',
    status: 'PENDING',
    suggestedKfCode: null,
    suggestedComponents: null,
    classificationStrategy: 'NONE',
    classificationConfidence: 'LOW',
    resultingProductId: null,
    createdAt: '2026-04-14T10:00:00Z',
    updatedAt: '2026-04-14T10:00:00Z',
    ...overrides,
  }
}

function mountDialog(candidate: BootstrapCandidateResponse | null = null, visible = true) {
  return mount(BootstrapApproveDialog, {
    props: { candidate, visible },
    global: {
      stubs: {
        Dialog: DialogStub,
        InputText: InputTextStub,
        InputNumber: InputNumberStub,
        Select: SelectStub,
        Button: ButtonStub,
        KfCodeInput: KfCodeInputStub,
      },
    },
  })
}

describe('BootstrapApproveDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ─── Test 1: pre-populates name/vtsz from candidate ─────────────────────

  it('pre-populates name input from candidate productName', async () => {
    const candidate = buildCandidate({ productName: 'Termék A', vtsz: '12345678' })
    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    const nameInput = wrapper.find('[data-testid="ba-name"]')
    expect(nameInput.exists()).toBe(true)
    expect((nameInput.element as HTMLInputElement).value).toBe('Termék A')
  })

  // ─── Test 2: pre-populates kfCode from suggestedKfCode ──────────────────

  it('pre-populates kfCode component row when suggestedKfCode is set', async () => {
    const candidate = buildCandidate({ suggestedKfCode: '11010101' })
    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    const kfInput = wrapper.find('[data-testid="kf-input"]')
    expect(kfInput.exists()).toBe(true)
    expect((kfInput.element as HTMLInputElement).value).toBe('11010101')
  })

  // ─── Test 3: blank component row when no suggestion ─────────────────────

  it('renders blank kfCode input when suggestedKfCode is null', async () => {
    const candidate = buildCandidate({ suggestedKfCode: null })
    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    const kfInput = wrapper.find('[data-testid="kf-input"]')
    expect(kfInput.exists()).toBe(true)
    expect((kfInput.element as HTMLInputElement).value).toBe('')
  })

  // ─── Test 4: multi-component pre-population from suggestedComponents JSON ──

  it('pre-populates multiple component rows from suggestedComponents JSON', async () => {
    const suggestedComponents = JSON.stringify([
      { layer: 'primary', kfCode: '11010101', description: 'PET palack', weightEstimateKg: 0.025, unitsPerProduct: 1, score: 0.90 },
      { layer: 'secondary', kfCode: '41010201', description: 'Karton multipack', weightEstimateKg: 0.050, unitsPerProduct: 6, score: 0.70 },
      { layer: 'tertiary', kfCode: '51010101', description: 'Sztreccs fólia', weightEstimateKg: 0.300, unitsPerProduct: 480, score: 0.55 },
    ])
    const candidate = buildCandidate({ suggestedComponents })
    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    // Should have 3 component rows (one per packaging layer)
    const kfInputs = wrapper.findAll('[data-testid="kf-input"]')
    expect(kfInputs).toHaveLength(3)

    expect((kfInputs[0]!.element as HTMLInputElement).value).toBe('11010101')
    expect((kfInputs[1]!.element as HTMLInputElement).value).toBe('41010201')
    expect((kfInputs[2]!.element as HTMLInputElement).value).toBe('51010101')

    // Material descriptions should be pre-filled from JSON
    const matInputs = wrapper.findAll('[data-testid^="ba-mat-"]')
    expect(matInputs).toHaveLength(3)
    expect((matInputs[0]!.element as HTMLInputElement).value).toBe('PET palack')
    expect((matInputs[1]!.element as HTMLInputElement).value).toBe('Karton multipack')
    expect((matInputs[2]!.element as HTMLInputElement).value).toBe('Sztreccs fólia')
  })

  it('falls back to single component when suggestedComponents is invalid JSON', async () => {
    const candidate = buildCandidate({
      suggestedKfCode: '11010101',
      suggestedComponents: '<<not valid json>>',
    })
    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    const kfInputs = wrapper.findAll('[data-testid="kf-input"]')
    expect(kfInputs).toHaveLength(1)
    expect((kfInputs[0]!.element as HTMLInputElement).value).toBe('11010101')
  })

  // ─── Test 5: confirm button calls approveCandidate with correct body ─────

  it('confirm button calls approveCandidate with pre-populated candidate data', async () => {
    const candidate = buildCandidate()
    mockApproveCandidate.mockResolvedValue({ ...candidate, status: 'APPROVED' })

    const wrapper = mountDialog(candidate)
    await wrapper.vm.$nextTick()

    await wrapper.find('[data-testid="approve-confirm"]').trigger('click')
    await wrapper.vm.$nextTick()

    expect(mockApproveCandidate).toHaveBeenCalledWith(
      candidate.id,
      expect.objectContaining({
        name: 'Aktivia 125g',
        primaryUnit: 'DARAB',
        components: expect.arrayContaining([
          expect.objectContaining({ unitsPerProduct: 1 }),
        ]),
      }),
    )
  })
})
