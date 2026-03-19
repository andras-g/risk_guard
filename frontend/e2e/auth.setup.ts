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
 * Strategy: call the backend directly via Playwright's API context, extract
 * the auth_token from the Set-Cookie response header, then inject it into
 * the browser context for the frontend origin. This avoids all cross-origin
 * and proxy cookie forwarding issues.
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  const apiBase = process.env.PLAYWRIGHT_API_BASE ?? 'http://localhost:8080'
  const frontendBase = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000'
  const frontendUrl = new URL(frontendBase)

  // Call the backend directly via Playwright's API context
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

  // Extract the auth_token value from the Set-Cookie response header.
  // Playwright's page.request stores the cookie in the API context but NOT in the
  // browser's cookie jar. We extract and inject it manually for both origins.
  const allHeaders = await response.headersArray()
  const setCookieHeader = allHeaders.find(h => h.name.toLowerCase() === 'set-cookie')
  const setCookieValue = setCookieHeader?.value ?? ''
  const tokenMatch = setCookieValue.match(/auth_token=([^;]+)/)
  if (!tokenMatch) {
    throw new Error(
      `No auth_token cookie in login response. Headers: ${JSON.stringify(allHeaders.map(h => h.name))}`
    )
  }

  // Inject the cookie into the browser context for BOTH origins:
  // - localhost (covers both :3000 and :8080 since cookies are not port-scoped)
  // This ensures the cookie is sent on both same-origin and cross-origin requests.
  await page.context().addCookies([{
    name: 'auth_token',
    value: tokenMatch[1],
    domain: 'localhost',
    path: '/',
    httpOnly: true,
    sameSite: 'None',
    secure: false,
  }])
}
