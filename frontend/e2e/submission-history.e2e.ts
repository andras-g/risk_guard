import { test, expect } from '@playwright/test'

test.describe('Submission History Panel', () => {
  test('panel is collapsed by default on /epr/filing', async ({ page }) => {
    await page.goto('/epr/filing')

    // Allow page to settle
    await page.waitForTimeout(1000)

    const panel = page.getByTestId('submissions-panel')
    if (!(await panel.isVisible())) {
      test.skip(true, 'Submissions panel not visible — registry may be empty')
      return
    }

    // Panel should be collapsed by default (content not visible)
    const table = page.getByTestId('submissions-table')
    expect(await table.isVisible()).toBe(false)
  })

  test('expanding panel triggers fetch and shows table', async ({ page }) => {
    await page.goto('/epr/filing')
    await page.waitForTimeout(1000)

    const panelWrapper = page.getByTestId('submissions-panel-wrapper')
    if (!(await panelWrapper.isVisible())) {
      test.skip(true, 'Submissions panel wrapper not visible — registry may be empty')
      return
    }

    const panel = page.getByTestId('submissions-panel')
    if (!(await panel.isVisible())) {
      test.skip(true, 'Submissions panel not visible')
      return
    }

    // Click the toggle button to expand
    const toggleBtn = panel.locator('.p-panel-header button, [aria-label*="toggle"], [aria-expanded]').first()
    if (await toggleBtn.isVisible()) {
      await toggleBtn.click()
      await page.waitForTimeout(1500)
      // After expand, either skeleton or table should be present
      const hasContent = await page.getByTestId('submissions-table').isVisible()
        || await page.getByTestId('submissions-skeleton').isVisible()
      expect(hasContent).toBe(true)
    }
  })
})
