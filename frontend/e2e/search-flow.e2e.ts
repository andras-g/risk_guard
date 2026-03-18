/**
 * E2E Test: Login → Dashboard → Tax Number Search → Verdict Display
 *
 * Verifies the full happy-path user flow for partner screening:
 * 1. Authenticate via test bypass (no OAuth2 redirect)
 * 2. Navigate to /dashboard (auth middleware accepts the cookie)
 * 3. Enter a valid Hungarian tax number
 * 4. Click "Start screening"
 * 5. Verify skeleton loading UI appears
 * 6. Verify verdict response renders with expected status
 *
 * Prerequisites:
 *   - Backend running with SPRING_PROFILES_ACTIVE=test (enables auth bypass + test seed data)
 *   - Frontend running (Nuxt dev or preview)
 *   - Test user seeded via R__e2e_test_data.sql
 */
import { test, expect } from '@playwright/test'
import { loginAsTestUser } from './auth.setup'

// Canonical test tax number — valid 8-digit Hungarian format
const TEST_TAX_NUMBER = '12345678'

test.describe('Search Flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTestUser(page)
  })

  test('authenticated user can search a tax number and see verdict result', async ({ page }) => {
    // Navigate to dashboard — auth middleware will validate the cookie via GET /me
    await page.goto('/dashboard')

    // Verify we landed on the dashboard (not redirected to login)
    await expect(page).toHaveURL(/\/dashboard/)

    // Find the search input by its placeholder (works across locales since we look for the input element)
    const searchInput = page.locator('input[type="text"]').first()
    await expect(searchInput).toBeVisible()

    // Type the tax number
    await searchInput.fill(TEST_TAX_NUMBER)

    // Click the submit button (identified by type="submit" within the form)
    const submitButton = page.locator('button[type="submit"]')
    await expect(submitButton).toBeVisible()
    await submitButton.click()

    // Verify skeleton loading UI appears while search is in progress.
    // Uses data-testid on the SkeletonVerdictCard wrapper.
    const skeletonCard = page.locator('[data-testid="skeleton-verdict-card"]')
    // The skeleton should appear (even briefly) — use a short timeout since it may disappear fast
    await expect(skeletonCard).toBeVisible({ timeout: 5_000 })

    // Verify navigation to the verdict detail page happened.
    // This is the critical assertion — the previous version matched the tax number text
    // inside the still-visible search input on the dashboard, masking the navigation bug.
    await expect(page).toHaveURL(new RegExp(`/screening/${TEST_TAX_NUMBER}`), { timeout: 30_000 })

    // Verify the verdict card is rendered on the detail page.
    const verdictCard = page.locator('[data-testid="verdict-card"]')
    await expect(verdictCard).toBeVisible({ timeout: 10_000 })

    // Verify the status badge is displayed (icon + label block inside the verdict card).
    const statusBadge = page.locator('[data-testid="verdict-status-badge"]')
    await expect(statusBadge).toBeVisible({ timeout: 10_000 })

    // Verify the tax number appears inside the verdict card, not just in the search input.
    const taxNumberDisplay = page.locator('[data-testid="tax-number"]')
    await expect(taxNumberDisplay).toBeVisible()
    await expect(taxNumberDisplay).toHaveText(TEST_TAX_NUMBER)
  })

  test('search input validates tax number format', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)

    const searchInput = page.locator('input[type="text"]').first()
    await searchInput.fill('123') // Too short — invalid

    const submitButton = page.locator('button[type="submit"]')
    await submitButton.click()

    // Validation error should appear below input
    const errorText = page.locator('[data-testid="validation-error"]')
    await expect(errorText).toBeVisible({ timeout: 5_000 })
  })

  test('unauthenticated user is redirected to login', async ({ page }) => {
    // Navigate directly without logging in — clear any cookies first
    await page.context().clearCookies()
    await page.goto('/dashboard')

    // Should be redirected to login page
    await expect(page).toHaveURL(/\/auth\/login/, { timeout: 10_000 })
  })
})
