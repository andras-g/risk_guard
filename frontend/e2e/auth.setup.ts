/**
 * Shared E2E authentication helper.
 *
 * Calls the test auth bypass endpoint to obtain an HttpOnly auth_token cookie,
 * then stores the authenticated browser context for use by all E2E tests.
 */
import { type Page } from '@playwright/test'

/** Deterministic E2E test user — matches the Flyway seed in R__e2e_test_data.sql */
export const E2E_USER = {
  email: 'e2e@riskguard.hu',
  role: 'SME_ADMIN',
  tenantId: '00000000-0000-4000-a000-000000000001',
  userId: '00000000-0000-4000-a000-000000000002',
} as const

/**
 * Authenticates via the test bypass endpoint and sets the auth_token cookie
 * on the Playwright page context. The backend must be running with
 * SPRING_PROFILES_ACTIVE=test for this endpoint to exist.
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  const apiBase = process.env.PLAYWRIGHT_API_BASE ?? 'http://localhost:8080'

  const response = await page.request.post(`${apiBase}/api/test/auth/login`, {
    data: {
      email: E2E_USER.email,
      role: E2E_USER.role,
    },
  })

  if (!response.ok()) {
    const body = await response.text()
    throw new Error(
      `Test auth login failed (${response.status()}): ${body}. ` +
      `Is the backend running with SPRING_PROFILES_ACTIVE=test?`
    )
  }

  // The response contains a Set-Cookie header with the auth_token HttpOnly cookie.
  // Playwright automatically stores cookies from API responses in the browser context,
  // so subsequent page.goto() calls will include the cookie.
}
