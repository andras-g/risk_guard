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
 * Strategy: call the login endpoint through the Nuxt dev proxy (same-origin)
 * so the browser stores the cookie for the frontend origin. With apiBase=''
 * and Nitro devProxy, all API calls go through localhost:3000, eliminating
 * cross-origin cookie issues entirely.
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000'

  // Call the login endpoint via the Nuxt dev proxy (same-origin request).
  // The proxy forwards to localhost:8080, and the Set-Cookie from the
  // backend response is stored for localhost:3000 (same origin as the SPA).
  const response = await page.request.post(`${baseURL}/api/test/auth/login`, {
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

  // Verify the cookie was stored — Playwright's API context should process Set-Cookie.
  const cookies = await page.context().cookies()
  const authCookie = cookies.find(c => c.name === 'auth_token')
  if (!authCookie) {
    // Fallback: extract token from response headers and inject manually
    const allHeaders = await response.headersArray()
    const setCookieHeaders = allHeaders.filter(h => h.name.toLowerCase() === 'set-cookie')
    const tokenHeader = setCookieHeaders.find(h => h.value.includes('auth_token='))
    const tokenMatch = tokenHeader?.value.match(/auth_token=([^;]+)/)
    if (!tokenMatch) {
      throw new Error(
        `auth_token not in cookies and not in Set-Cookie headers. ` +
        `Response headers: ${JSON.stringify(allHeaders.map(h => `${h.name}: ${h.value.substring(0, 50)}`))}`
      )
    }
    // Manually inject the cookie for the frontend origin
    await page.context().addCookies([{
      name: 'auth_token',
      value: tokenMatch[1],
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
    }])
  }
}
