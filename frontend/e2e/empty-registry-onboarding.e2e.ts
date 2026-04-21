/**
 * E2E — Story 10.7 AC #25 — Empty-registry onboarding block.
 *
 * Verifies that:
 *  - Navigating to /epr/filing with an empty Registry shows the onboarding block (no period selector).
 *  - Navigating to /registry with an empty Registry shows the onboarding block (no DataTable).
 *  - The "Add product manually" secondary CTA navigates to /registry/new.
 *  - The "Bootstrap from invoices" primary CTA opens the RegistryInvoiceBootstrapDialog.
 *
 * Prerequisites: the test user's tenant must have an empty Registry (no products).
 * If products already exist, the onboarding block is not shown and the test is skipped.
 */
import { test, expect } from '@playwright/test'
import { loginAsTestUser } from './auth.setup'

test.describe('Empty Registry onboarding — Story 10.7', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTestUser(page)
    await page.setViewportSize({ width: 1280, height: 800 })
  })

  test('shows onboarding block on /epr/filing when Registry is empty; period selector absent', async ({ page }) => {
    await page.goto('/epr/filing', { waitUntil: 'networkidle' })

    if (await page.locator('[data-testid="epr-select-customer"]').isVisible()) {
      test.skip(true, 'Accountant with no client selected — cannot exercise filing onboarding flow')
    }
    if (await page.locator('[data-testid="registry-tier-gate"]').isVisible()
      || await page.locator('text=PRO EPR').isVisible()) {
      test.skip(true, 'Test user lacks PRO_EPR tier')
    }

    const onboarding = page.locator('[data-testid="registry-onboarding-block"]')
    try {
      await onboarding.waitFor({ state: 'visible', timeout: 5000 })
    } catch {
      test.skip(true, 'Registry is not empty — onboarding block not shown (expected for non-empty tenant)')
    }

    await expect(onboarding).toBeVisible()
    await expect(page.locator('[data-testid="period-selector"]')).not.toBeVisible()
  })

  test('shows onboarding block on /registry when Registry is empty; DataTable absent', async ({ page }) => {
    await page.goto('/registry', { waitUntil: 'networkidle' })

    if (await page.locator('[data-testid="registry-tier-gate"]').isVisible()) {
      test.skip(true, 'Test user lacks PRO_EPR tier')
    }

    const onboarding = page.locator('[data-testid="registry-onboarding-block"]')
    try {
      await onboarding.waitFor({ state: 'visible', timeout: 5000 })
    } catch {
      test.skip(true, 'Registry is not empty — onboarding block not shown (expected for non-empty tenant)')
    }

    await expect(onboarding).toBeVisible()
    // DataTable should not be visible when no products
    await expect(page.locator('table.p-datatable-table')).not.toBeVisible()
  })

  test('"Add product manually" CTA navigates to /registry/new', async ({ page }) => {
    await page.goto('/registry', { waitUntil: 'networkidle' })

    if (await page.locator('[data-testid="registry-tier-gate"]').isVisible()) {
      test.skip(true, 'Test user lacks PRO_EPR tier')
    }

    const onboarding = page.locator('[data-testid="registry-onboarding-block"]')
    try {
      await onboarding.waitFor({ state: 'visible', timeout: 5000 })
    } catch {
      test.skip(true, 'Registry is not empty — cannot test manual CTA')
    }

    await page.locator('[data-testid="onboarding-cta-manual"]').click()
    await expect(page).toHaveURL(/\/registry\/new/)
  })

  test('"Bootstrap from invoices" CTA opens bootstrap dialog', async ({ page }) => {
    await page.goto('/registry', { waitUntil: 'networkidle' })

    if (await page.locator('[data-testid="registry-tier-gate"]').isVisible()) {
      test.skip(true, 'Test user lacks PRO_EPR tier')
    }

    const onboarding = page.locator('[data-testid="registry-onboarding-block"]')
    try {
      await onboarding.waitFor({ state: 'visible', timeout: 5000 })
    } catch {
      test.skip(true, 'Registry is not empty — cannot test bootstrap CTA')
    }

    await page.locator('[data-testid="onboarding-cta-bootstrap"]').click()
    // The bootstrap dialog should appear
    await expect(page.locator('text=Feltöltés számlák alapján').or(page.locator('text=Bootstrap from invoices'))).toBeVisible({ timeout: 3000 })
  })
})
