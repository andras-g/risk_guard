import { vi, expect } from 'vitest'
import * as matchers from 'vitest-axe/matchers'

// Register vitest-axe custom matchers (toHaveNoViolations) for axe-core a11y testing
expect.extend(matchers)

// Stub Nuxt auto-imports (ref, computed, watch, onMounted, etc.) for non-SSR unit test env.
// In a full @nuxt/test-utils setup these are available globally; in vitest + jsdom we mock them.
import { ref, computed, watch, onMounted, reactive } from 'vue'

vi.stubGlobal('ref', ref)
vi.stubGlobal('computed', computed)
vi.stubGlobal('watch', watch)
vi.stubGlobal('onMounted', onMounted)
vi.stubGlobal('reactive', reactive)

// Stub Nuxt router composables used in components
vi.stubGlobal('navigateTo', vi.fn())
vi.stubGlobal('useRuntimeConfig', () => ({
  public: { apiBase: 'http://localhost:8080' }
}))
vi.stubGlobal('useCookie', vi.fn(() => ({ value: null })))
vi.stubGlobal('$fetch', vi.fn())

// Stub useRoute for breadcrumb and navigation components
vi.stubGlobal('useRoute', vi.fn(() => ({
  path: '/dashboard',
  matched: [{ path: '/dashboard', name: 'dashboard' }]
})))

// Stub useRouter for navigation
vi.stubGlobal('useRouter', vi.fn(() => ({
  push: vi.fn(),
  currentRoute: { value: { path: '/dashboard' } }
})))

// Stub useNuxtApp for $t i18n helper
vi.stubGlobal('useNuxtApp', vi.fn(() => ({
  $t: (key: string) => key
})))

// Stub useI18n for components using @nuxtjs/i18n composable
vi.stubGlobal('useI18n', vi.fn(() => ({
  t: (key: string) => key,
  te: () => true,
  locale: { value: 'hu' }
})))

// Stub useAuthStore — individual tests override via vi.mock() as needed
vi.stubGlobal('useAuthStore', vi.fn())
