/**
 * E2E Tests: Application Shell (Sidebar, Top Bar, Mobile Navigation)
 *
 * Smoke tests verifying the authenticated shell renders correctly:
 * 1. Desktop sidebar with navigation links
 * 2. Mobile hamburger drawer with navigation
 *
 * Prerequisites:
 *   - Backend running with SPRING_PROFILES_ACTIVE=test
 *   - Frontend running (Nuxt dev or preview)
 *   - Test user seeded via R__e2e_test_data.sql
 */
import { test, expect } from '@playwright/test'
import { loginAsTestUser } from './auth.setup'

test.describe('Application Shell', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTestUser(page)
  })

  test('shell renders with sidebar on desktop', async ({ page }) => {
    // Set desktop viewport
    await page.setViewportSize({ width: 1280, height: 800 })

    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)

    // Sidebar should be visible on desktop
    const sidebar = page.locator('[data-testid="app-sidebar"]')
    await expect(sidebar).toBeVisible({ timeout: 10_000 })

    // Sidebar has at least 4 navigation links (Dashboard, Screening, Watchlist, EPR)
    const navItems = sidebar.locator('a')
    await expect(navItems).toHaveCount(4, { timeout: 5_000 })

    // Top bar should be visible
    const topbar = page.locator('[data-testid="app-topbar"]')
    await expect(topbar).toBeVisible()

    // Breadcrumb should be visible on desktop
    const breadcrumb = page.locator('[data-testid="app-breadcrumb"]')
    await expect(breadcrumb).toBeVisible()

    // Content area should render page content (the slot)
    const mainContent = page.locator('main')
    await expect(mainContent).toBeVisible()
  })

  test('mobile drawer opens and navigates', async ({ page }) => {
    // Set mobile viewport (iPhone-like)
    await page.setViewportSize({ width: 375, height: 812 })

    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)

    // Sidebar should NOT be visible on mobile
    const sidebar = page.locator('[data-testid="app-sidebar"]')
    await expect(sidebar).not.toBeVisible()

    // Hamburger button should be visible on mobile
    const hamburger = page.locator('[data-testid="hamburger-button"]')
    await expect(hamburger).toBeVisible({ timeout: 10_000 })

    // Click hamburger to open drawer
    await hamburger.click()

    // Drawer should be visible with navigation items
    const drawer = page.locator('[data-testid="mobile-drawer"]')
    await expect(drawer).toBeVisible({ timeout: 5_000 })

    const drawerNav = page.locator('[data-testid="drawer-nav"]')
    await expect(drawerNav).toBeVisible()

    // Click a nav link (screening) — should navigate and close the drawer
    const screeningLink = page.locator('[data-testid="drawer-nav-screening"]')
    await expect(screeningLink).toBeVisible()
    await screeningLink.click()

    // URL should change to /screening
    await expect(page).toHaveURL(/\/screening/, { timeout: 10_000 })
  })
})
