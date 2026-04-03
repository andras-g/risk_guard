import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import NavCredentialManager from './NavCredentialManager.vue'

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

// Mock PrimeVue useToast (must use vi.mock, not vi.stubGlobal, because it's a module import)
const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

// Mock health store using a getter so adapters can be changed per test
const mockFetchHealth = vi.fn()
let mockAdapters: { adapterName: string; credentialStatus: string; dataSourceMode: string }[] = []

vi.mock('~/stores/health', () => ({
  useHealthStore: () => ({
    get adapters() { return mockAdapters },
    fetchHealth: mockFetchHealth,
  }),
}))

const ButtonStub = {
  template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="loading" @click="$emit(\'click\')">{{ label }}</button>',
  props: ['label', 'loading', 'icon', 'severity', 'variant'],
  emits: ['click'],
}

function mountComponent(credentialStatus = 'NOT_CONFIGURED') {
  mockAdapters = [{ adapterName: 'nav-online-szamla', credentialStatus, dataSourceMode: 'TEST' }]
  return mount(NavCredentialManager, {
    global: {
      stubs: { Button: ButtonStub },
    },
  })
}

async function fillForm(wrapper: ReturnType<typeof mount>) {
  await wrapper.find('[data-testid="input-login"]').setValue('testLogin')
  await wrapper.find('[data-testid="input-password"]').setValue('testPass')
  await wrapper.find('[data-testid="input-signing-key"]').setValue('sigKey')
  await wrapper.find('[data-testid="input-exchange-key"]').setValue('exKey')
  await wrapper.find('[data-testid="input-tax-number"]').setValue('12345678')
}

describe('NavCredentialManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchHealth.mockResolvedValue(undefined)
    mockAdapters = []
  })

  it('renders five input fields', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('[data-testid="input-login"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="input-password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="input-signing-key"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="input-exchange-key"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="input-tax-number"]').exists()).toBe(true)
  })

  it('renders save button', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('[data-testid="btn-save"]').exists()).toBe(true)
  })

  it('does not show delete button when NOT_CONFIGURED', () => {
    const wrapper = mountComponent('NOT_CONFIGURED')
    expect(wrapper.find('[data-testid="btn-delete"]').exists()).toBe(false)
  })

  it('shows delete button when credentials are VALID', () => {
    const wrapper = mountComponent('VALID')
    expect(wrapper.find('[data-testid="btn-delete"]').exists()).toBe(true)
  })

  it('shows delete button when credentials are EXPIRED', () => {
    const wrapper = mountComponent('EXPIRED')
    expect(wrapper.find('[data-testid="btn-delete"]').exists()).toBe(true)
  })

  it('shows credential status badge', () => {
    const wrapper = mountComponent('VALID')
    const badge = wrapper.find('[data-testid="credential-status-badge"]')
    expect(badge.exists()).toBe(true)
  })

  it('calls PUT /api/v1/admin/datasources/credentials on save', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountComponent()
    await fillForm(wrapper)

    await wrapper.find('[data-testid="btn-save"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/datasources/credentials',
      expect.objectContaining({ method: 'PUT' })
    )
  })

  it('shows success toast after successful save', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
    const wrapper = mountComponent()
    await fillForm(wrapper)

    await wrapper.find('[data-testid="btn-save"]').trigger('click')
    await flushPromises()

    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }))
  })

  it('shows error toast when save returns non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, text: async () => 'Validation failed' }))
    const wrapper = mountComponent()
    await fillForm(wrapper)

    await wrapper.find('[data-testid="btn-save"]').trigger('click')
    await flushPromises()

    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }))
  })

  it('calls DELETE /api/v1/admin/datasources/credentials on delete', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountComponent('VALID')

    await wrapper.find('[data-testid="btn-delete"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/datasources/credentials',
      expect.objectContaining({ method: 'DELETE' })
    )
  })

  it('refreshes health store after successful save', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
    const wrapper = mountComponent()
    await fillForm(wrapper)

    await wrapper.find('[data-testid="btn-save"]').trigger('click')
    await flushPromises()

    expect(mockFetchHealth).toHaveBeenCalled()
  })

  it('clears form fields after successful save', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
    const wrapper = mountComponent()

    const loginInput = wrapper.find('[data-testid="input-login"]')
    await loginInput.setValue('testLogin')
    await wrapper.find('[data-testid="input-password"]').setValue('testPass')
    await wrapper.find('[data-testid="input-signing-key"]').setValue('sigKey')
    await wrapper.find('[data-testid="input-exchange-key"]').setValue('exKey')
    await wrapper.find('[data-testid="input-tax-number"]').setValue('12345678')

    await wrapper.find('[data-testid="btn-save"]').trigger('click')
    await flushPromises()

    expect((loginInput.element as HTMLInputElement).value).toBe('')
  })

  it('disables save button when form fields are empty', () => {
    const wrapper = mountComponent()
    const saveBtn = wrapper.find('[data-testid="btn-save"]')
    expect((saveBtn.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('has for/id attributes linking labels to inputs', () => {
    const wrapper = mountComponent()
    const loginLabel = wrapper.find('label[for="nav-login"]')
    const loginInput = wrapper.find('#nav-login')
    expect(loginLabel.exists()).toBe(true)
    expect(loginInput.exists()).toBe(true)
  })
})
