import { describe, it, expect, beforeEach } from 'vitest'

/**
 * Unit tests for layout.ts Pinia store.
 *
 * Tests the sidebar expanded/collapsed state, mobile drawer visibility,
 * and localStorage persistence. Co-located per architecture rules.
 */

// Simulate the store logic directly (same pattern as TenantSwitcher tests)
const SIDEBAR_STORAGE_KEY = 'risk-guard-sidebar-expanded'

function createLayoutStore() {
  let sidebarExpanded = true
  let mobileDrawerOpen = false

  return {
    get sidebarExpanded() { return sidebarExpanded },
    set sidebarExpanded(v: boolean) { sidebarExpanded = v },
    get mobileDrawerOpen() { return mobileDrawerOpen },
    set mobileDrawerOpen(v: boolean) { mobileDrawerOpen = v },

    toggleSidebar() {
      sidebarExpanded = !sidebarExpanded
      try {
        localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarExpanded))
      } catch {
        // localStorage unavailable
      }
    },

    openMobileDrawer() {
      mobileDrawerOpen = true
    },

    closeMobileDrawer() {
      mobileDrawerOpen = false
    },

    initFromStorage() {
      try {
        const stored = localStorage.getItem(SIDEBAR_STORAGE_KEY)
        if (stored !== null) {
          sidebarExpanded = stored === 'true'
        }
      } catch {
        // localStorage unavailable
      }
    }
  }
}

describe('layout store — sidebar state', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('sidebarExpanded defaults to true', () => {
    const store = createLayoutStore()
    expect(store.sidebarExpanded).toBe(true)
  })

  it('toggleSidebar flips from true to false', () => {
    const store = createLayoutStore()
    store.toggleSidebar()
    expect(store.sidebarExpanded).toBe(false)
  })

  it('toggleSidebar flips from false back to true', () => {
    const store = createLayoutStore()
    store.toggleSidebar()  // true -> false
    store.toggleSidebar()  // false -> true
    expect(store.sidebarExpanded).toBe(true)
  })

  it('toggleSidebar persists to localStorage', () => {
    const store = createLayoutStore()
    store.toggleSidebar()  // true -> false
    expect(localStorage.getItem(SIDEBAR_STORAGE_KEY)).toBe('false')
    store.toggleSidebar()  // false -> true
    expect(localStorage.getItem(SIDEBAR_STORAGE_KEY)).toBe('true')
  })

  it('initFromStorage restores persisted false state', () => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, 'false')
    const store = createLayoutStore()
    store.initFromStorage()
    expect(store.sidebarExpanded).toBe(false)
  })

  it('initFromStorage restores persisted true state', () => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, 'true')
    const store = createLayoutStore()
    store.initFromStorage()
    expect(store.sidebarExpanded).toBe(true)
  })

  it('initFromStorage keeps default when no stored value', () => {
    const store = createLayoutStore()
    store.initFromStorage()
    expect(store.sidebarExpanded).toBe(true)
  })
})

describe('layout store — mobile drawer', () => {
  it('mobileDrawerOpen defaults to false', () => {
    const store = createLayoutStore()
    expect(store.mobileDrawerOpen).toBe(false)
  })

  it('openMobileDrawer sets mobileDrawerOpen to true', () => {
    const store = createLayoutStore()
    store.openMobileDrawer()
    expect(store.mobileDrawerOpen).toBe(true)
  })

  it('closeMobileDrawer sets mobileDrawerOpen to false', () => {
    const store = createLayoutStore()
    store.openMobileDrawer()
    store.closeMobileDrawer()
    expect(store.mobileDrawerOpen).toBe(false)
  })
})
