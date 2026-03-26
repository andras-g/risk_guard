import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import LoginPage from './login.vue'

/**
 * Unit tests for the Login page (pages/auth/login.vue).
 * Verifies: SSO buttons, email/password form, divider, error handling,
 * register link, and i18n key usage.
 *
 * Co-located per architecture rules (Story 3.2, Task 6.5).
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
let mockIsAuthenticated = false
vi.stubGlobal('useAuthStore', () => ({
  initializeAuth: mockInitializeAuth,
  isAuthenticated: mockIsAuthenticated,
}))

vi.stubGlobal('useRoute', () => ({
  path: '/auth/login',
  query: {},
  matched: [],
}))

const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

// Stub NuxtLink
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
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot />{{ label }}</button>',
  props: ['label', 'type', 'disabled', 'loading', 'icon', 'severity', 'class'],
  emits: ['click'],
}

const DividerStub = {
  template: '<div data-testid="divider"><slot /></div>',
  props: ['align'],
}

function mountPage(): VueWrapper {
  return mount(LoginPage, {
    global: {
      stubs: {
        NuxtLink: NuxtLinkStub,
        InputText: InputTextStub,
        Button: ButtonStub,
        Divider: DividerStub,
      },
    },
  })
}

describe('Login Page — rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    mockIsAuthenticated = false
  })

  it('renders page title and subtitle using i18n keys', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('auth.login.title')
    expect(wrapper.text()).toContain('auth.login.subtitle')
  })

  it('renders SSO buttons with i18n labels', () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('auth.login.google')
    expect(wrapper.text()).toContain('auth.login.microsoft')
  })

  it('renders the "or" divider', () => {
    const wrapper = mountPage()
    const divider = wrapper.find('[data-testid="divider"]')
    expect(divider.exists()).toBe(true)
    expect(divider.text()).toContain('auth.login.or')
  })

  it('renders email/password form with i18n labels', () => {
    const wrapper = mountPage()
    expect(wrapper.find('#login-email').exists()).toBe(true)
    expect(wrapper.find('#login-password').exists()).toBe(true)
    expect(wrapper.text()).toContain('auth.login.emailLabel')
    expect(wrapper.text()).toContain('auth.login.passwordLabel')
    expect(wrapper.text()).toContain('auth.login.emailSubmit')
  })

  it('renders "Register" link to /auth/register', () => {
    const wrapper = mountPage()
    const link = wrapper.find('a[href="/auth/register"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('auth.login.noAccount')
  })

  it('uses public layout via definePageMeta', () => {
    mountPage()
    expect(mockDefinePageMeta).toHaveBeenCalledWith({ layout: 'public' })
  })
})

describe('Login Page — email/password login', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    mockIsAuthenticated = false
  })

  it('submits email login and navigates on success', async () => {
    mockFetch.mockResolvedValue({ email: 'test@example.com' })
    mockInitializeAuth.mockResolvedValue(undefined)

    const wrapper = mountPage()
    await wrapper.find('#login-email').setValue('test@example.com')
    await wrapper.find('#login-password').setValue('P@ssword1')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/public/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: {
          email: 'test@example.com',
          password: 'P@ssword1',
        },
      })
    )
    expect(mockInitializeAuth).toHaveBeenCalled()
    expect(mockNavigateTo).toHaveBeenCalledWith('/dashboard')
  })

  it('shows generic error on login failure (401)', async () => {
    mockFetch.mockRejectedValue({
      data: { type: 'urn:riskguard:error:invalid-credentials' },
    })

    const wrapper = mountPage()
    await wrapper.find('#login-email').setValue('test@example.com')
    await wrapper.find('#login-password').setValue('wrong')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'auth.login.error.invalidCredentials',
      })
    )
  })

  it('shows "too many attempts" error on 429', async () => {
    mockFetch.mockRejectedValue({
      data: { type: 'urn:riskguard:error:too-many-attempts' },
    })

    const wrapper = mountPage()
    await wrapper.find('#login-email').setValue('test@example.com')
    await wrapper.find('#login-password').setValue('wrong')
    await nextTick()

    await wrapper.find('form').trigger('submit')
    await nextTick()
    await nextTick()

    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'auth.login.error.tooManyAttempts',
      })
    )
  })
})
