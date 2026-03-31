import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import EprConfigPage from './epr-config.vue'

// ── Mock Monaco Editor ────────────────────────────────────────────────────────
vi.mock('~/components/Admin/MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: ['modelValue', 'readonly'],
    emits: ['update:modelValue'],
    template: '<textarea data-testid="monaco-editor" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))

// ── Mock PrimeVue ─────────────────────────────────────────────────────────────
const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd }),
}))

vi.mock('primevue/button', () => ({
  default: {
    template: '<button :disabled="disabled" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'loading', 'label', 'icon', 'variant'],
    emits: ['click'],
    inheritAttrs: true,
  },
}))

vi.mock('primevue/panel', () => ({
  default: {
    template: '<div :data-testid="$attrs[\'data-testid\']"><div class="panel-header">{{ header }}</div><slot /></div>',
    props: ['header', 'pt'],
    inheritAttrs: true,
  },
}))

// ── Nuxt globals ──────────────────────────────────────────────────────────────
vi.stubGlobal('useI18n', () => ({
  t: (key: string, params?: Record<string, unknown>) => {
    if (params) return `${key}:${JSON.stringify(params)}`
    return key
  },
}))

vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' },
}))

const mockRouterReplace = vi.fn()
vi.stubGlobal('useRouter', () => ({
  replace: mockRouterReplace,
}))

vi.stubGlobal('definePageMeta', vi.fn())
vi.stubGlobal('NuxtLink', { template: '<a><slot /></a>' })

// ── $fetch mock ───────────────────────────────────────────────────────────────
const mockFetch = vi.fn()
vi.stubGlobal('$fetch', mockFetch)

// ── Identity store mock ───────────────────────────────────────────────────────
let mockUserRole = 'SME_ADMIN'
vi.mock('~/stores/identity', () => ({
  useIdentityStore: () => ({
    get user() {
      return { role: mockUserRole }
    },
  }),
}))

const mountPage = () => mount(EprConfigPage, {
  global: {
    stubs: {
      ClientOnly: { template: '<slot />' },
      Skeleton: { template: '<div />' },
    },
  },
})

describe('epr-config.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUserRole = 'SME_ADMIN'
  })

  it('loads and displays config version from GET response', async () => {
    mockFetch.mockResolvedValueOnce({
      version: 1,
      configData: '{"fee_rates":{}}',
      activatedAt: '2026-01-01T00:00:00Z',
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/admin/epr/config',
      expect.objectContaining({ credentials: 'include' })
    )
    expect(wrapper.text()).toContain('1')
  })

  it('validate button calls POST /validate and shows success panel on valid response', async () => {
    mockFetch
      .mockResolvedValueOnce({ version: 1, configData: '{}', activatedAt: '2026-01-01T00:00:00Z' })
      .mockResolvedValueOnce({ valid: true, errors: [] })

    const wrapper = mountPage()
    await flushPromises()

    const validateBtn = wrapper.find('[data-testid="validate-btn"]')
    await validateBtn.trigger('click')
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/admin/epr/config/validate',
      expect.objectContaining({ method: 'POST', credentials: 'include' })
    )
    const panel = wrapper.find('[data-testid="validation-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('admin.eprConfig.validationPassed')
  })

  it('validate button shows error list on invalid response', async () => {
    mockFetch
      .mockResolvedValueOnce({ version: 1, configData: '{}', activatedAt: '2026-01-01T00:00:00Z' })
      .mockResolvedValueOnce({ valid: false, errors: ['Test 1 FAILED: expected kfCode=11010101, got null'] })

    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="validate-btn"]').trigger('click')
    await flushPromises()

    const panel = wrapper.find('[data-testid="validation-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('Test 1 FAILED')
  })

  it('publish button is disabled before validation passes', async () => {
    mockFetch.mockResolvedValueOnce({ version: 1, configData: '{}', activatedAt: '2026-01-01T00:00:00Z' })

    const wrapper = mountPage()
    await flushPromises()

    const publishBtn = wrapper.find('[data-testid="publish-btn"]')
    expect(publishBtn.attributes('disabled')).toBeDefined()
  })

  it('publish button calls POST /publish and shows success toast', async () => {
    mockFetch
      .mockResolvedValueOnce({ version: 1, configData: '{}', activatedAt: '2026-01-01T00:00:00Z' })
      .mockResolvedValueOnce({ valid: true, errors: [] })
      .mockResolvedValueOnce({ version: 2, activatedAt: '2026-03-31T10:00:00Z' })

    const wrapper = mountPage()
    await flushPromises()

    // Validate first to enable Publish
    await wrapper.find('[data-testid="validate-btn"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="publish-btn"]').trigger('click')
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/admin/epr/config/publish',
      expect.objectContaining({ method: 'POST', credentials: 'include' })
    )
    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success' })
    )
    // AC3: page header must reflect new version after publish
    expect(wrapper.text()).toContain('2')
  })

  it('redirects non-SME_ADMIN user to / without loading config', async () => {
    mockUserRole = 'ACCOUNTANT'

    mountPage()
    await flushPromises()

    expect(mockRouterReplace).toHaveBeenCalledWith('/')
    expect(mockFetch).not.toHaveBeenCalled()
  })
})
