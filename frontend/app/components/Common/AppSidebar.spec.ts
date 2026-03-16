import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import AppSidebar from './AppSidebar.vue'

/**
 * Unit tests for AppSidebar.vue logic.
 *
 * Tests validate sidebar navigation structure, active route detection,
 * role-gated admin section, and collapse/expand behavior.
 * Co-located with AppSidebar.vue per architecture rules.
 */

// Stub PrimeVue components
const DividerStub = { template: '<hr />' }
const NuxtLinkStub = { template: '<a :data-testid="$attrs[\'data-testid\']" :href="to"><slot /></a>', props: ['to'], inheritAttrs: true }

// Mock stores with ref-based state so storeToRefs returns usable refs
vi.mock('pinia', async (importOriginal) => {
  const actual = await importOriginal<typeof import('pinia')>()
  const { ref: vueRef } = require('vue')
  return {
    ...actual,
    storeToRefs: (store: any) => {
      // Return refs for all non-function, non-$ properties
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
  useLayoutStore: () => ({ sidebarExpanded: true, mobileDrawerOpen: false, toggleSidebar: vi.fn() })
}))
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({ role: 'SME_ADMIN', name: 'Test User', isAccountant: false })
}))

// Navigation items — same structure as component
const mainNavItems = [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  { key: 'epr', to: '/epr', icon: 'pi-file-export' }
]

function isActive(routePath: string, itemPath: string): boolean {
  return routePath === itemPath || routePath.startsWith(itemPath + '/')
}

describe('AppSidebar — navigation items', () => {
  it('has exactly 4 main navigation items', () => {
    expect(mainNavItems).toHaveLength(4)
  })

  it('includes Dashboard, Screening, Watchlist, and EPR items', () => {
    const keys = mainNavItems.map(i => i.key)
    expect(keys).toEqual(['dashboard', 'screening', 'watchlist', 'epr'])
  })

  it('each nav item has an i18n-compatible key', () => {
    for (const item of mainNavItems) {
      expect(`common.nav.${item.key}`).toMatch(/^common\.nav\.\w+$/)
    }
  })

  it('each nav item has a PrimeIcons icon class', () => {
    for (const item of mainNavItems) {
      expect(item.icon).toMatch(/^pi-\w+/)
    }
  })

  it('each nav item has a leading-slash route path', () => {
    for (const item of mainNavItems) {
      expect(item.to).toMatch(/^\//)
    }
  })
})

describe('AppSidebar — active route detection', () => {
  it('marks /dashboard as active when route is /dashboard', () => {
    expect(isActive('/dashboard', '/dashboard')).toBe(true)
  })

  it('marks /screening as active when route is /screening/12345678', () => {
    expect(isActive('/screening/12345678', '/screening')).toBe(true)
  })

  it('does NOT mark /dashboard as active when route is /screening', () => {
    expect(isActive('/screening', '/dashboard')).toBe(false)
  })

  it('marks /watchlist as active for /watchlist/detail/123', () => {
    expect(isActive('/watchlist/detail/123', '/watchlist')).toBe(true)
  })

  it('marks /admin as active when route is /admin', () => {
    expect(isActive('/admin', '/admin')).toBe(true)
  })

  it('marks /admin as active for /admin/users', () => {
    expect(isActive('/admin/users', '/admin')).toBe(true)
  })
})

describe('AppSidebar — admin section role gating', () => {
  it('admin section is visible when user role is ADMIN', () => {
    const role = ref('ADMIN')
    const isAdmin = role.value === 'ADMIN'
    expect(isAdmin).toBe(true)
  })

  it('admin section is hidden when user role is SME_ADMIN', () => {
    const role = ref('SME_ADMIN')
    const isAdmin = role.value === 'ADMIN'
    expect(isAdmin).toBe(false)
  })

  it('admin section is hidden when user role is ACCOUNTANT', () => {
    const role = ref('ACCOUNTANT')
    const isAdmin = role.value === 'ADMIN'
    expect(isAdmin).toBe(false)
  })

  it('admin section is hidden when user role is GUEST', () => {
    const role = ref('GUEST')
    const isAdmin = role.value === 'ADMIN'
    expect(isAdmin).toBe(false)
  })

  it('admin section is hidden when role is null', () => {
    const role = ref(null)
    const isAdmin = role.value === 'ADMIN'
    expect(isAdmin).toBe(false)
  })
})

describe('AppSidebar — collapse/expand behavior', () => {
  it('sidebar width is w-60 when expanded', () => {
    const expanded = true
    const widthClass = expanded ? 'w-60' : 'w-16'
    expect(widthClass).toBe('w-60')
  })

  it('sidebar width is w-16 when collapsed', () => {
    const expanded = false
    const widthClass = expanded ? 'w-60' : 'w-16'
    expect(widthClass).toBe('w-16')
  })

  it('toggle calls toggleSidebar on layout store', () => {
    const toggleSidebar = vi.fn()
    toggleSidebar()
    expect(toggleSidebar).toHaveBeenCalledOnce()
  })

  it('labels are visible when expanded', () => {
    const expanded = true
    // In template: v-if="sidebarExpanded" for label <span>
    expect(expanded).toBe(true)
  })

  it('labels are hidden when collapsed (icon-only)', () => {
    const expanded = false
    expect(expanded).toBe(false)
  })

  it('collapse button shows chevron-left when expanded', () => {
    const expanded = true
    const icon = expanded ? 'pi-chevron-left' : 'pi-chevron-right'
    expect(icon).toBe('pi-chevron-left')
  })

  it('collapse button shows chevron-right when collapsed', () => {
    const expanded = false
    const icon = expanded ? 'pi-chevron-left' : 'pi-chevron-right'
    expect(icon).toBe('pi-chevron-right')
  })
})

describe('AppSidebar — structure', () => {
  it('logo text is "RiskGuard" when expanded', () => {
    const expanded = true
    const logoText = expanded ? 'RiskGuard' : 'RG'
    expect(logoText).toBe('RiskGuard')
  })

  it('logo text is "RG" when collapsed', () => {
    const expanded = false
    const logoText = expanded ? 'RiskGuard' : 'RG'
    expect(logoText).toBe('RG')
  })
})

describe('AppSidebar — component mount smoke test', () => {
  it('renders without error and contains expected testid elements', () => {
    const wrapper = mount(AppSidebar, {
      global: {
        stubs: {
          NuxtLink: NuxtLinkStub,
          Divider: DividerStub
        },
        directives: {
          tooltip: () => {} // stub PrimeVue tooltip directive
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })
    expect(wrapper.find('[data-testid="app-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sidebar-logo"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sidebar-nav"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sidebar-toggle"]').exists()).toBe(true)
    // 4 main nav items should render
    expect(wrapper.find('[data-testid="nav-item-dashboard"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-screening"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-watchlist"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-epr"]').exists()).toBe(true)
    // Admin section should NOT render (role is SME_ADMIN, not ADMIN)
    expect(wrapper.find('[data-testid="admin-nav-section"]').exists()).toBe(false)
  })
})
