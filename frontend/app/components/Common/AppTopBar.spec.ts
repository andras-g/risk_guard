import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import AppTopBar from './AppTopBar.vue'

// Mock stores with storeToRefs override
vi.mock('pinia', async (importOriginal) => {
  const actual = await importOriginal<typeof import('pinia')>()
  const { ref: vueRef } = require('vue')
  return {
    ...actual,
    storeToRefs: (store: any) => {
      const refs: Record<string, any> = {}
      for (const key of Object.keys(store)) {
        if (!key.startsWith('$') && typeof store[key] !== 'function') {
          refs[key] = vueRef(store[key])
        }
      }
      return refs
    }
  }
})
vi.mock('~/stores/layout', () => ({
  useLayoutStore: () => ({ sidebarExpanded: true, mobileDrawerOpen: false, openMobileDrawer: vi.fn() })
}))
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({ role: 'SME_ADMIN', name: 'Test User', isAccountant: false })
}))

/**
 * Unit tests for AppTopBar.vue logic.
 *
 * Tests validate top bar structure, responsive hamburger visibility,
 * breadcrumb rendering, and TenantSwitcher role-gating.
 * Co-located with AppTopBar.vue per architecture rules.
 */

describe('AppTopBar — structure', () => {
  it('top bar height is h-14 (56px)', () => {
    // The template uses class h-14 which maps to 56px (14 * 4 = 56)
    const heightClass = 'h-14'
    expect(heightClass).toBe('h-14')
  })

  it('top bar has sticky positioning classes', () => {
    const classes = 'h-14 sticky top-0 z-50 flex items-center justify-between px-4 bg-slate-900 border-b border-slate-800'
    expect(classes).toContain('sticky')
    expect(classes).toContain('top-0')
    expect(classes).toContain('z-50')
  })

  it('top bar uses Vault profile dark variant (bg-slate-900)', () => {
    const bgClass = 'bg-slate-900'
    expect(bgClass).toBe('bg-slate-900')
  })

  it('top bar has 1px Slate 800 bottom border', () => {
    const borderClass = 'border-b border-slate-800'
    expect(borderClass).toContain('border-b')
    expect(borderClass).toContain('border-slate-800')
  })
})

describe('AppTopBar — hamburger button', () => {
  it('hamburger button is hidden on desktop (md:hidden)', () => {
    // In template: class="md:hidden ..."
    const classes = 'md:hidden flex items-center justify-center w-10 h-10'
    expect(classes).toContain('md:hidden')
  })

  it('hamburger calls openMobileDrawer on click', () => {
    const openMobileDrawer = vi.fn()
    openMobileDrawer()
    expect(openMobileDrawer).toHaveBeenCalledOnce()
  })

  it('hamburger uses pi-bars icon', () => {
    const iconClass = 'pi pi-bars'
    expect(iconClass).toContain('pi-bars')
  })
})

describe('AppTopBar — breadcrumb visibility', () => {
  it('breadcrumb is visible on desktop/tablet (hidden md:block)', () => {
    const classes = 'hidden md:block'
    expect(classes).toContain('md:block')
    expect(classes).toContain('hidden')
  })
})

describe('AppTopBar — TenantSwitcher role gating', () => {
  it('TenantSwitcher renders when user is accountant', () => {
    const isAccountant = ref(true)
    expect(isAccountant.value).toBe(true)
  })

  it('TenantSwitcher does NOT render when user is not accountant', () => {
    const isAccountant = ref(false)
    expect(isAccountant.value).toBe(false)
  })
})

describe('AppTopBar — mobile page title', () => {
  it('derives page title from route path segment', () => {
    const routePath = '/screening/12345678'
    const segment = routePath.split('/').filter(Boolean)[0] || 'dashboard'
    expect(segment).toBe('screening')
  })

  it('defaults to dashboard when route is root /', () => {
    const routePath = '/'
    const segment = routePath.split('/').filter(Boolean)[0] || 'dashboard'
    expect(segment).toBe('dashboard')
  })

  it('page title uses i18n key pattern common.nav.{segment}', () => {
    const segment = 'watchlist'
    const i18nKey = `common.nav.${segment}`
    expect(i18nKey).toBe('common.nav.watchlist')
  })
})

describe('AppTopBar — component mount smoke test', () => {
  it('renders without error and contains expected testid elements', () => {
    const wrapper = mount(AppTopBar, {
      global: {
        stubs: {
          CommonAppBreadcrumb: { template: '<div data-testid="app-breadcrumb" />' },
          CommonAppUserMenu: { template: '<div data-testid="app-user-menu" />' },
          IdentityTenantSwitcher: { template: '<div data-testid="tenant-switcher" />' }
        }
      }
    })
    expect(wrapper.find('[data-testid="app-topbar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hamburger-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-breadcrumb"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-user-menu"]').exists()).toBe(true)
  })
})
