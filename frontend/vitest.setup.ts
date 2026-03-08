import { vi } from 'vitest'

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

// Stub useAuthStore — individual tests override via vi.mock() as needed
vi.stubGlobal('useAuthStore', vi.fn())
