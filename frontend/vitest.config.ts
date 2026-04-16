import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

/**
 * Vitest configuration for unit tests.
 * - Tests live co-located with their components (*.spec.ts in same directory as *.vue)
 * - Uses @vue/test-utils for component mounting
 * - Pinia stores are mocked via vi.mock() per-test
 *
 * Note: Full SSR/Nuxt integration tests (middleware, useCookie, navigateTo etc.) require
 * Playwright or @nuxt/test-utils — those are planned for Story 1-5 CI/CD infrastructure.
 * These unit tests cover component rendering logic and user interaction in isolation.
 */
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    // Resolve Nuxt auto-imports used in components (ref, computed, etc.)
    setupFiles: ['./vitest.setup.ts'],
    pool: 'forks',
    poolOptions: {
      forks: { minForks: 1, maxForks: 3 },
    },
  },
  resolve: {
    alias: {
      '~': resolve(__dirname, './app'),
      '@': resolve(__dirname, './app'),
    },
  },
})
