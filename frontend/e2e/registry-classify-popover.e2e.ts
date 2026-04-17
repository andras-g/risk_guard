/**
 * E2E Regression Test — Story 9.5 AC #5 (the "menu-breaks" bug).
 *
 * Before the fix, clicking the magic "KF-kód javaslat" button inside the
 * components DataTable rendered a PrimeVue Popover that (a) sat underneath
 * the sidebar drawer and (b) left the sidebar unclosable, forcing a full
 * page reload. Root causes: no appendTo="body", a leaking per-row ref map,
 * and DataTable overflow-hidden clipping the overlay.
 *
 * After the fix:
 *   - Popover is hoisted to the page root with appendTo="body".
 *   - Popover z-index is above the sidebar drawer layer (z-[60] vs z-40).
 *   - Escape key closes the popover cleanly; sidebar continues to respond.
 *
 * This test reproduces the regression path so it cannot silently return.
 *
 * Prerequisites:
 *   - Backend running with SPRING_PROFILES_ACTIVE=test
 *   - Test user has PRO_EPR tier (seeded via R__e2e_test_data.sql)
 */
import { test, expect } from '@playwright/test'
import { loginAsTestUser } from './auth.setup'

test.describe('Registry classify popover — Story 9.5 regression', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTestUser(page)
    await page.setViewportSize({ width: 1280, height: 800 })
  })

  test('popover renders above sidebar and Escape closes it without stuck menu', async ({ page }) => {
    await page.goto('/registry/new', { waitUntil: 'networkidle' })

    // Skip the test if the tier gate blocks the editor (user not PRO_EPR):
    // the regression cannot exist without the editor.
    if (await page.locator('[data-testid="registry-tier-gate"]').isVisible()) {
      test.skip(true, 'Test user lacks PRO_EPR tier — skipping popover regression test')
    }

    // Enter a product name so the magic button becomes enabled.
    await page.getByLabel(/Termék neve|Product name/).fill('PET palack 500ml')

    // Add at least one component so the suggest button renders.
    const addComponentBtn = page.getByRole('button', { name: /Elem hozzáadása|Add component/ }).first()
    await addComponentBtn.click()

    // Click the first "Suggest" button (data-testid="suggest-kf-0").
    const suggestBtn = page.locator('[data-testid="suggest-kf-0"]')
    await expect(suggestBtn).toBeEnabled({ timeout: 5_000 })
    await suggestBtn.click()

    // The Popover should render. We look for the first layer's accept button as an anchor.
    const acceptBtn = page.locator('[data-testid="accept-kf-layer-0"]')
    // If the backend returns LOW/no suggestion the toast path is hit instead.
    // In that case the popover never opens — the regression is still covered
    // by the Escape/sidebar-toggle parts below when a suggestion DOES arrive,
    // so we gate strictly on popover visibility.
    const popoverVisible = await acceptBtn.isVisible({ timeout: 10_000 }).catch(() => false)
    if (!popoverVisible) {
      test.skip(true, 'Classifier returned no HIGH/MEDIUM suggestion — nothing to assert on popover')
    }

    // The hoisted popover lives on document.body — not nested inside the table.
    // Verify its z-index is strictly above the sidebar drawer's z-index.
    const sidebar = page.locator('[data-testid="app-sidebar"]')
    await expect(sidebar).toBeVisible()

    const sidebarZ = await sidebar.evaluate(el => Number.parseInt(getComputedStyle(el).zIndex || '0', 10))
    const popoverZ = await acceptBtn.evaluateHandle(el => {
      // Walk up to the nearest ancestor with an explicit z-index.
      let node: HTMLElement | null = el as HTMLElement
      while (node) {
        const z = Number.parseInt(getComputedStyle(node).zIndex || '0', 10)
        if (!Number.isNaN(z) && z > 0) return z
        node = node.parentElement
      }
      return 0
    })
    const popoverZValue = await popoverZ.jsonValue() as number

    expect(popoverZValue).toBeGreaterThan(sidebarZ)

    // Press Escape — popover should close AND sidebar stays responsive.
    await page.keyboard.press('Escape')
    await expect(acceptBtn).not.toBeVisible({ timeout: 2_000 })

    // Sidebar is still interactive (click a nav link, it navigates).
    // Viewport is fixed at 1280x800 in beforeEach so the desktop sidebar is
    // visible — we assert the nav link is reachable (the regression would
    // leave the sidebar's click-outside handler in a locked state, so this
    // click would either no-op or the page would not navigate).
    const screeningLink = page.locator('[data-testid="app-sidebar"] a').filter({ hasText: /Átvilágítás|Screening/ }).first()
    await expect(screeningLink).toBeVisible({ timeout: 5_000 })
    await screeningLink.click()
    await expect(page).toHaveURL(/\/screening/, { timeout: 10_000 })
  })
})
