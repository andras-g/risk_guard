import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import RegisterPage from './register.vue'

/**
 * Unit tests for the Registration page (pages/auth/register.vue).
 * Verifies: form fields, password strength indicator, form submission,
 * error handling, i18n key usage, and accessibility.
 *
 * Co-located per architecture rules (Story 3.2, Task 5.6).
 */

// --- Mocks ---
const mockNavigateTo = vi.fn()
vi.stubGlobal('navigateTo', mockNavigateTo)

const mockDefinePageMeta = vi.fn()
vi.stubGlobal('definePageMeta', mockDefinePageMeta)

const mockToastAdd = vi.fn()
vi.stubGlobal('useToast', () => ({
  add: mockToastAdd,
}))

const mockInitializeAuth = vi.fn()
vi.stubGlobal('useAuthStore', () => ({
  initializeAuth: mockInitializeAuth,
  isAuthenticated: false,
}))

const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

// Stub NuxtLink as a simple <a> for test env
const NuxtLinkStub = {
  template: '<a :href="to"><slot /></a>',
  props: ['to'],
}

// Stub PrimeVue components
const InputTextStub = {
  template: '<input :id="$attrs.id" :type="$attrs.type || \'text\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  props: ['modelValue'],
  emits: ['update:modelValue'],
}

const ButtonStub = {
  template: '<button :type="type" :disabled="disabled"><slot />{{ label }}</button>',
  props: ['label', 'type', 'disabled', 'loading'],
}

function mountPage(): VueWrapper {
  return mount(RegisterPage, {
    global: {
      stubs: {
        NuxtLink: NuxtLinkStub,
        InputText: InputTextStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('Register Page — rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
  })

  it('renders the registration form with all fields', () => {
    const wrapper = mountPage()
    expect(wrapper.find('#register-name').exists()).toBe(true)
    expect(wrapper.find('#register-email').exists()).toBe(true)
    expect(wrapper.find('#register-password').exists()).toBe(true)
    expect(wrapper.find('#register-confirm-password').exists()).toBe(true)
  })

  it('renders page title and subtitle using i18n keys', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('auth.register.title')
    expect(wrapper.text()).toContain('auth.register.subtitle')
  })

  it('renders labels using i18n keys', () => {
    const wrapper = mountPage()
    const labels = wrapper.findAll('label')
    const labelTexts = labels.map(l => l.text())
    expect(labelTexts).toContain('auth.register.name')
    expect(labelTexts).toContain('auth.register.email')
    expect(labelTexts).toContain('auth.register.password')
    expect(labelTexts).toContain('auth.register.confirmPassword')
  })

  it('renders submit button with i18n key', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('auth.register.submit')
  })

  it('renders link to login page using i18n key', () => {
    const wrapper = mountPage()
    const link = wrapper.find('a[href="/auth/login"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('auth.register.hasAccount')
  })

  it('uses public layout via definePageMeta', () => {
    mountPage()
    expect(mockDefinePageMeta).toHaveBeenCalledWith({ layout: 'public' })
  })
})

describe('Register Page — password strength indicator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not show password strength indicator when password is empty', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).not.toContain('auth.register.passwordStrength.title')
  })

  it('shows password strength indicator when password is typed', async () => {
    const wrapper = mountPage()
    const passwordInput = wrapper.find('#register-password')
    await passwordInput.setValue('a')
    await nextTick()
    expect(wrapper.text()).toContain('auth.register.passwordStrength.title')
    expect(wrapper.text()).toContain('auth.register.passwordStrength.minLength')
    expect(wrapper.text()).toContain('auth.register.passwordStrength.uppercase')
    expect(wrapper.text()).toContain('auth.register.passwordStrength.digit')
    expect(wrapper.text()).toContain('auth.register.passwordStrength.special')
  })
})

describe('Register Page — confirm password', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows password mismatch error when passwords differ', async () => {
    const wrapper = mountPage()
    await wrapper.find('#register-password').setValue('P@ssword1')
    await wrapper.find('#register-confirm-password').setValue('different')
    await nextTick()
    expect(wrapper.text()).toContain('auth.register.error.passwordMismatch')
  })
})

describe('Register Page — form submission', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
  })

  it('submits successfully and navigates to /', async () => {
    mockFetch.mockResolvedValue({ id: '123', email: 'test@example.com' })
    mockInitializeAuth.mockResolvedValue(undefined)

    const wrapper = mountPage()
    await wrapper.find('#register-name').setValue('Test User')
    await wrapper.find('#register-email').setValue('test@example.com')
    await wrapper.find('#register-password').setValue('P@ssword1')
    await wrapper.find('#register-confirm-password').setValue('P@ssword1')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/public/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: {
          email: 'test@example.com',
          password: 'P@ssword1',
          confirmPassword: 'P@ssword1',
          name: 'Test User',
        },
      })
    )
    expect(mockInitializeAuth).toHaveBeenCalled()
    expect(mockNavigateTo).toHaveBeenCalledWith('/dashboard')
  })

  it('shows error toast on duplicate email', async () => {
    mockFetch.mockRejectedValue({
      data: { type: 'urn:riskguard:error:email-already-registered' },
    })

    const wrapper = mountPage()
    await wrapper.find('#register-name').setValue('Test User')
    await wrapper.find('#register-email').setValue('test@example.com')
    await wrapper.find('#register-password').setValue('P@ssword1')
    await wrapper.find('#register-confirm-password').setValue('P@ssword1')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'auth.register.error.emailExists',
      })
    )
  })

  it('shows SSO suggestion error when email exists via SSO', async () => {
    mockFetch.mockRejectedValue({
      data: { type: 'urn:riskguard:error:email-exists-sso' },
    })

    const wrapper = mountPage()
    await wrapper.find('#register-name').setValue('Test User')
    await wrapper.find('#register-email').setValue('test@example.com')
    await wrapper.find('#register-password').setValue('P@ssword1')
    await wrapper.find('#register-confirm-password').setValue('P@ssword1')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'auth.register.error.emailExistsSso',
      })
    )
  })
})
