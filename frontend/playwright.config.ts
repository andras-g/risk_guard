import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright E2E configuration for Risk Guard.
 *
 * Tests run against a live backend (Spring Boot + Testcontainers PostgreSQL)
 * and a live frontend (Nuxt dev server). Authentication uses the test bypass
 * endpoint (`POST /api/test/auth/login`) which is only active under
 * `@Profile("test")` / `SPRING_PROFILES_ACTIVE=test`.
 *
 * Local dev:  npx playwright test
 * CI:         The GitHub Actions workflow starts backend+frontend before running tests.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',

  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Single worker in CI for stability; parallel locally */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter */
  reporter: process.env.CI ? 'github' : 'html',

  /* Shared settings for all projects */
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /* Timeout per test (30s default, 60s for E2E flows) */
  timeout: 60_000,

  /* Expect timeout */
  expect: {
    timeout: 10_000,
  },

  /*
   * Auto-start frontend dev server before running tests (local dev only).
   * The backend must be started separately with SPRING_PROFILES_ACTIVE=test.
   * In CI, both servers are started by the GitHub Actions workflow — this is
   * skipped when PLAYWRIGHT_BASE_URL is set (see `url` check).
   *
   * To enable: uncomment the webServer block below.
   */
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:3000',
        reuseExistingServer: true,
        timeout: 60_000,
      },
})
