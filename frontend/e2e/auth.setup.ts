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
 * Uses the Nuxt dev proxy (localhost:3000/api/... → localhost:8080/api/...)
 * so the cookie is set on the same origin as the SPA. This avoids cross-origin
 * cookie issues with SameSite=Lax.
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000'

  // Navigate to the frontend first to establish the browser context origin.
  await page.goto(baseURL, { waitUntil: 'load', timeout: 30_000 })

  // Login via the Nuxt dev proxy — the request goes through localhost:3000/api/...
  // which proxies to localhost:8080/api/... The Set-Cookie from the backend is
  // forwarded by the proxy and stored for localhost:3000 (same origin as the SPA).
  const result = await page.evaluate(async ({ email, role }) => {
    const res = await fetch('/api/test/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, role }),
      credentials: 'include',
    })
    return { ok: res.ok, status: res.status, body: await res.text() }
  }, { email: E2E_USER.email, role: E2E_USER.role })

  if (!result.ok) {
    throw new Error(
      `Test auth login failed (${result.status}): ${result.body}. ` +
      `Is the backend running with SPRING_PROFILES_ACTIVE=test?`
    )
  }

  // Verify the auth cookie was stored in the browser context
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'auth_token')
  if (!authCookie) {
    const cookieNames = cookies.map(c => c.name).join(', ')
    throw new Error(
      `Auth cookie 'auth_token' not found after login. ` +
      `Available cookies: [${cookieNames}]. Login response was ${result.status}.`
    )
  }
}
