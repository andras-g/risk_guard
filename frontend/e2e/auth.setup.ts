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
 *
 * Uses page.evaluate(fetch) instead of page.request.post() to ensure the
 * cookie is stored in the browser's cookie jar (same origin context as
 * subsequent $fetch calls from the SPA to the backend API).
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  const apiBase = process.env.PLAYWRIGHT_API_BASE ?? 'http://localhost:8080'

  // Navigate to the backend origin first so the browser context is on localhost:8080.
  // This ensures the Set-Cookie header from the login response is stored for this origin.
  await page.goto(`${apiBase}/actuator/health`, { waitUntil: 'load', timeout: 30_000 })

  // Perform the login request from within the browser context (not Playwright's API context).
  // This guarantees the HttpOnly cookie is stored in the browser's cookie jar.
  const result = await page.evaluate(async ({ email, role, base }) => {
    const res = await fetch(`${base}/api/test/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, role }),
      credentials: 'include',
    })
    return { ok: res.ok, status: res.status, body: await res.text() }
  }, { email: E2E_USER.email, role: E2E_USER.role, base: apiBase })

  if (!result.ok) {
    throw new Error(
      `Test auth login failed (${result.status}): ${result.body}. ` +
      `Is the backend running with SPRING_PROFILES_ACTIVE=test?`
    )
  }

  // The HttpOnly cookie is now stored in the browser for localhost.
  // When the SPA (localhost:3000) makes $fetch calls to localhost:8080
  // with credentials: 'include', the cookie will be sent (same-site).
}
