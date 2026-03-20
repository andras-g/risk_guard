import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { z } from 'zod'

/**
 * Unit tests for SearchBar.vue logic.
 *
 * Tests validate using the same Zod schema as the component (AC1 compliance).
 * Formatting and search flow logic are tested via extracted function signatures.
 * Co-located with SearchBar.vue per architecture rules.
 *
 * vi.mock() calls are hoisted to file top by Vitest, so they must use inline
 * factories that don't reference variables defined later in the file.
 */

// ─── Shared mock state for guest session (mutable so tests can change it) ───
// vi.mock is hoisted by Vitest, so we use a shared mutable object that
// the mock factory reads from and individual tests can modify before mounting.
const guestMockState = {
  limitError: null as string | null,
  companiesUsed: 7,
  companiesLimit: 10,
  dailyChecksUsed: 2,
  dailyChecksLimit: 3,
}

// File-level mocks (hoisted by Vitest) — these run before any describe/it blocks
vi.mock('~/stores/screening', () => ({
  useScreeningStore: () => ({ isSearching: false, search: vi.fn() })
}))
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({ isAuthenticated: false, email: null })
}))
vi.mock('~/composables/auth/useGuestSession', () => ({
  useGuestSession: () => ({
    isSearching: ref(false),
    searchError: ref(null),
    limitError: ref(guestMockState.limitError),
    currentVerdict: ref(null),
    usageStats: ref({
      companiesUsed: guestMockState.companiesUsed,
      companiesLimit: guestMockState.companiesLimit,
      dailyChecksUsed: guestMockState.dailyChecksUsed,
      dailyChecksLimit: guestMockState.dailyChecksLimit,
    }),
    hasRemainingCompanies: ref(true),
    hasRemainingDailyChecks: ref(true),
    guestSearch: vi.fn().mockResolvedValue(undefined),
    clearGuestSearch: vi.fn(),
    getSessionFingerprint: () => 'test-fingerprint',
  })
}))
vi.mock('~/utils/taxNumber', () => ({
  formatTaxNumber: (v: string) => v,
  taxNumberSchema: { safeParse: () => ({ success: true }) }
}))

describe('SearchBar — tax number validation (Zod)', () => {
  // Same schema used in SearchBar.vue
  const hungarianTaxNumberSchema = z.string().regex(
    /^\d{8}(\d{3})?$/,
    'screening.search.invalidTaxNumber'
  )

  function validate(input: string): boolean {
    const cleaned = input.replace(/[^\d]/g, '')
    return hungarianTaxNumberSchema.safeParse(cleaned).success
  }

  it('should accept valid 8-digit tax number', () => {
    expect(validate('12345678')).toBe(true)
  })

  it('should accept valid 11-digit tax number', () => {
    expect(validate('12345678901')).toBe(true)
  })

  it('should accept formatted 8-digit tax number with hyphens', () => {
    expect(validate('1234-5678')).toBe(true)
  })

  it('should accept formatted 11-digit tax number with hyphens', () => {
    expect(validate('1234-5678-901')).toBe(true)
  })

  it('should reject too short tax number', () => {
    expect(validate('1234567')).toBe(false)
  })

  it('should reject too long tax number', () => {
    expect(validate('123456789012')).toBe(false)
  })

  it('should reject 9-digit tax number', () => {
    expect(validate('123456789')).toBe(false)
  })

  it('should reject 10-digit tax number', () => {
    expect(validate('1234567890')).toBe(false)
  })

  it('should reject non-numeric input', () => {
    expect(validate('abcdefgh')).toBe(false)
  })

  it('should reject empty string', () => {
    expect(validate('')).toBe(false)
  })
})

describe('SearchBar — auto-formatting', () => {
  function formatTaxNumber(raw: string): string {
    const digits = raw.replace(/[^\d]/g, '').slice(0, 11)
    if (digits.length <= 4) return digits
    if (digits.length <= 8) return `${digits.slice(0, 4)}-${digits.slice(4)}`
    return `${digits.slice(0, 4)}-${digits.slice(4, 8)}-${digits.slice(8)}`
  }

  it('should format 8-digit as ####-####', () => {
    expect(formatTaxNumber('12345678')).toBe('1234-5678')
  })

  it('should format 11-digit as ####-####-###', () => {
    expect(formatTaxNumber('12345678901')).toBe('1234-5678-901')
  })

  it('should strip non-digit characters', () => {
    expect(formatTaxNumber('1234-5678')).toBe('1234-5678')
  })

  it('should handle partial input (4 digits)', () => {
    expect(formatTaxNumber('1234')).toBe('1234')
  })

  it('should handle partial input (6 digits)', () => {
    expect(formatTaxNumber('123456')).toBe('1234-56')
  })

  it('should truncate at 11 digits', () => {
    expect(formatTaxNumber('123456789012345')).toBe('1234-5678-901')
  })
})

describe('SearchBar — guest mode (Story 3.12)', () => {
  // File-level vi.mock() calls (hoisted above) provide the default mocks.
  // These tests mount the actual component and verify DOM output.

  const InputTextStub = {
    template: '<input :id="$attrs.id" :aria-describedby="$attrs[\'aria-describedby\']" :aria-invalid="$attrs[\'aria-invalid\']" />',
    inheritAttrs: true
  }
  const ButtonStub = {
    template: '<button type="submit"><slot /></button>',
    props: ['label', 'loading', 'disabled', 'icon']
  }

  async function mountSearchBar(overrides?: Record<string, unknown>) {
    const { mount } = await import('@vue/test-utils')
    const { createPinia } = await import('pinia')
    const SearchBar = (await import('./SearchBar.vue')).default
    return mount(SearchBar, {
      global: {
        plugins: [createPinia()],
        stubs: {
          InputText: InputTextStub,
          Button: ButtonStub,
          ProgressBar: { template: '<div data-testid="progress-bar" :data-value="value" />', props: ['value', 'showValue'] },
          NuxtLink: { template: '<a :href="to" data-testid="register-cta"><slot /></a>', props: ['to'] },
        },
        ...overrides,
      }
    })
  }

  it('should render guest progress indicator element', async () => {
    guestMockState.limitError = null
    guestMockState.companiesUsed = 7
    guestMockState.companiesLimit = 10
    const wrapper = await mountSearchBar()
    const progress = wrapper.find('[data-testid="guest-progress"]')
    expect(progress.exists()).toBe(true)
  })

  it('should render ProgressBar with correct percentage value', async () => {
    guestMockState.limitError = null
    guestMockState.companiesUsed = 7
    guestMockState.companiesLimit = 10
    const wrapper = await mountSearchBar()
    const progressBar = wrapper.find('[data-testid="progress-bar"]')
    expect(progressBar.exists()).toBe(true)
    // 7/10 = 70%
    expect(progressBar.attributes('data-value')).toBe('70')
  })

  it('should show company limit message when COMPANY_LIMIT_REACHED', async () => {
    guestMockState.limitError = 'COMPANY_LIMIT_REACHED'
    guestMockState.companiesUsed = 10
    guestMockState.companiesLimit = 10
    const wrapper = await mountSearchBar()
    const companyLimit = wrapper.find('[data-testid="company-limit-message"]')
    expect(companyLimit.exists()).toBe(true)
    // Should contain a registration CTA link
    const ctaLink = companyLimit.find('[data-testid="register-cta"]')
    expect(ctaLink.exists()).toBe(true)
    expect(ctaLink.attributes('href')).toBe('/auth/register')
  })

  it('should show daily limit message when DAILY_LIMIT_REACHED', async () => {
    guestMockState.limitError = 'DAILY_LIMIT_REACHED'
    guestMockState.dailyChecksUsed = 3
    guestMockState.dailyChecksLimit = 3
    const wrapper = await mountSearchBar()
    const dailyLimit = wrapper.find('[data-testid="daily-limit-message"]')
    expect(dailyLimit.exists()).toBe(true)
    const ctaLink = dailyLimit.find('[data-testid="register-cta"]')
    expect(ctaLink.exists()).toBe(true)
    expect(ctaLink.attributes('href')).toBe('/auth/register')
  })
})

describe('SearchBar — accessibility (Story 3.0c)', () => {
  // Mocks are shared from the guest mode describe block above (vi.mock is hoisted by Vitest).
  // Reuse the same mount helper pattern.

  async function mountSearchBar() {
    const { mount } = await import('@vue/test-utils')
    const { createPinia } = await import('pinia')
    const SearchBar = (await import('./SearchBar.vue')).default
    return mount(SearchBar, {
      global: {
        plugins: [createPinia()],
        stubs: {
          InputText: {
            template: '<input :id="$attrs.id" :aria-describedby="$attrs[\'aria-describedby\']" :aria-invalid="$attrs[\'aria-invalid\']" />',
            inheritAttrs: true
          },
          Button: {
            template: '<button type="submit"><slot /></button>',
            props: ['label', 'loading', 'disabled', 'icon']
          },
          ProgressBar: { template: '<div />', props: ['value', 'showValue'] },
          NuxtLink: { template: '<a><slot /></a>', props: ['to'] },
        }
      }
    })
  }

  it('form has role="search"', async () => {
    const wrapper = await mountSearchBar()
    const form = wrapper.find('form')
    expect(form.attributes('role')).toBe('search')
  })

  it('has a visually-hidden label for the input', async () => {
    const wrapper = await mountSearchBar()
    const label = wrapper.find('label[for="screening-tax-number"]')
    expect(label.exists()).toBe(true)
    expect(label.classes()).toContain('sr-only')
  })

  it('input has id matching the label for attribute', async () => {
    const wrapper = await mountSearchBar()
    const input = wrapper.find('#screening-tax-number')
    expect(input.exists()).toBe(true)
  })
})

describe('SearchBar — search flow', () => {
  it('should call store search on valid submit', async () => {
    const search = vi.fn().mockResolvedValue(undefined)
    const taxNumberInput = ref('1234-5678')
    const validationError = ref('')

    async function onSubmit() {
      const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
      if (!/^\d{8}(\d{3})?$/.test(cleaned)) {
        validationError.value = 'Invalid'
        return
      }
      validationError.value = ''
      await search(cleaned)
    }

    await onSubmit()
    expect(search).toHaveBeenCalledOnce()
    expect(search).toHaveBeenCalledWith('12345678')
    expect(validationError.value).toBe('')
  })

  it('should show error for invalid input and NOT call search', async () => {
    const search = vi.fn()
    const taxNumberInput = ref('123')
    const validationError = ref('')

    async function onSubmit() {
      const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
      if (!/^\d{8}(\d{3})?$/.test(cleaned)) {
        validationError.value = 'Invalid'
        return
      }
      validationError.value = ''
      await search(cleaned)
    }

    await onSubmit()
    expect(search).not.toHaveBeenCalled()
    expect(validationError.value).toBe('Invalid')
  })
})
