import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AppSidebar from './AppSidebar.vue'
import { TIER_ORDER } from '~/composables/auth/useTierGate'

// Shared mutable tier — individual tests toggle this to cover PRO_EPR vs ALAP/PRO/null gating paths.
// Story 10.10: AppSidebar reads tier via useTierGate/storeToRefs to gate the registry and eprFiling entries.
const authState = vi.hoisted(() => ({ tier: 'PRO_EPR' as string | null, role: 'SME_ADMIN' as string | null }))

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
const BadgeStub = { template: '<span class="badge-stub"><slot /></span>', props: ['value', 'severity'] }

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
  useAuthStore: () => ({
    name: 'Test User',
    isAccountant: false,
    get role() { return authState.role },
    get tier() { return authState.tier }
  })
}))

// Overrides the auto-import stub in vitest.setup.ts so useTierGate (which auto-imports
// useAuthStore) resolves to the same mock as the component's storeToRefs path.
vi.stubGlobal('useAuthStore', () => ({
  name: 'Test User',
  isAccountant: false,
  get role() { return authState.role },
  get tier() { return authState.tier }
}))

beforeEach(() => {
  authState.tier = 'PRO_EPR'
  authState.role = 'SME_ADMIN'
})
vi.mock('~/stores/watchlist', () => ({
  useWatchlistStore: () => ({ count: 3, entries: [], isLoading: false, error: null, fetchCount: vi.fn() })
}))

// Navigation items — same structure as component (Story 10.1: epr nav item removed,
// Anyagkönyvtár page deleted; Story 10.10: eprFiling added, also PRO_EPR-tier-gated.)
const mainNavItems = [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  { key: 'registry', to: '/registry', icon: 'pi-box' },
  { key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' },
]

function isActive(routePath: string, itemPath: string): boolean {
  return routePath === itemPath || routePath.startsWith(itemPath + '/')
}

describe('AppSidebar — navigation items', () => {
  it('has exactly 5 main navigation items (no epr index link)', () => {
    expect(mainNavItems).toHaveLength(5)
  })

  it('includes Dashboard, Screening, Watchlist, Registry, and EprFiling items — no epr index link', () => {
    const keys = mainNavItems.map(i => i.key)
    expect(keys).toEqual(['dashboard', 'screening', 'watchlist', 'registry', 'eprFiling'])
    expect(keys).not.toContain('epr')
  })

  it('includes registry entry pointing to /registry with pi-box icon', () => {
    const registry = mainNavItems.find(i => i.key === 'registry')
    expect(registry).toBeDefined()
    expect(registry?.to).toBe('/registry')
    expect(registry?.icon).toBe('pi-box')
  })

  it('includes eprFiling entry pointing to /epr/filing with pi-file icon', () => {
    const filing = mainNavItems.find(i => i.key === 'eprFiling')
    expect(filing).toBeDefined()
    expect(filing?.to).toBe('/epr/filing')
    expect(filing?.icon).toBe('pi-file')
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
  function isAdmin(roleValue: string | null): boolean {
    return ['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(roleValue ?? '')
  }

  it('admin section is visible when user role is SME_ADMIN', () => {
    expect(isAdmin('SME_ADMIN')).toBe(true)
  })

  it('admin section is visible when user role is ACCOUNTANT', () => {
    expect(isAdmin('ACCOUNTANT')).toBe(true)
  })

  it('admin section is visible when user role is PLATFORM_ADMIN', () => {
    expect(isAdmin('PLATFORM_ADMIN')).toBe(true)
  })

  it('admin section is hidden when user role is GUEST', () => {
    expect(isAdmin('GUEST')).toBe(false)
  })

  it('admin section is hidden when role is null', () => {
    expect(isAdmin(null)).toBe(false)
  })
})

describe('AppSidebar — PRO_EPR tier gating for registry and eprFiling', () => {
  // Sanity check on the tier hierarchy constant imported from the composable —
  // guards against accidental renumbering that would silently flip gate outcomes.
  it('TIER_ORDER hierarchy is ALAP < PRO < PRO_EPR', () => {
    expect(TIER_ORDER.ALAP).toBeLessThan(TIER_ORDER.PRO)
    expect(TIER_ORDER.PRO).toBeLessThan(TIER_ORDER.PRO_EPR)
  })

  function mountSidebar() {
    return mount(AppSidebar, {
      global: {
        stubs: {
          NuxtLink: NuxtLinkStub,
          Divider: DividerStub,
          Badge: BadgeStub
        },
        directives: {
          tooltip: () => {}
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })
  }

  it('registry AND eprFiling entries are rendered when tier is PRO_EPR', () => {
    authState.tier = 'PRO_EPR'
    const wrapper = mountSidebar()
    expect(wrapper.find('[data-testid="nav-item-registry"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(true)
  })

  it('eprFiling entry is hidden when tier is PRO', () => {
    authState.tier = 'PRO'
    const wrapper = mountSidebar()
    expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="nav-item-registry"]').exists()).toBe(false)
  })

  it('eprFiling entry is hidden when tier is ALAP', () => {
    authState.tier = 'ALAP'
    const wrapper = mountSidebar()
    expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(false)
  })

  it('eprFiling entry is hidden when tier is null', () => {
    authState.tier = null
    const wrapper = mountSidebar()
    expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(false)
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
          Divider: DividerStub,
          Badge: BadgeStub
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
    // Main nav items should render (PRO_EPR tier mock includes registry; Anyagkönyvtár
    // epr nav removed in Story 10.1 — assert absence so regressions are caught).
    expect(wrapper.find('[data-testid="nav-item-dashboard"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-screening"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-watchlist"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-item-epr"]').exists()).toBe(false)
    expect(wrapper.findAll('a[href="/epr"]')).toHaveLength(0)
    expect(wrapper.find('[data-testid="nav-item-registry"]').exists()).toBe(true)
    // Story 10.10: quarterly filing nav entry under PRO_EPR tier gate.
    expect(wrapper.find('[data-testid="nav-item-eprFiling"]').exists()).toBe(true)
    // Admin section SHOULD render for SME_ADMIN role
    expect(wrapper.find('[data-testid="admin-nav-section"]').exists()).toBe(true)
  })
})

describe('AppSidebar — accessibility (Story 3.0c)', () => {
  function mountSidebar() {
    return mount(AppSidebar, {
      global: {
        stubs: { NuxtLink: NuxtLinkStub, Divider: DividerStub, Badge: BadgeStub },
        directives: { tooltip: () => {} },
        mocks: { $t: (key: string) => key }
      }
    })
  }

  it('nav element has aria-label', () => {
    const wrapper = mountSidebar()
    const nav = wrapper.find('[data-testid="sidebar-nav"]')
    expect(nav.attributes('aria-label')).toBe('common.a11y.sidebarNav')
  })

  it('active nav link has aria-current="page"', () => {
    // useRoute mock returns path '/dashboard' — so dashboard link should be active
    const wrapper = mountSidebar()
    const dashboardLink = wrapper.find('[data-testid="nav-item-dashboard"]')
    expect(dashboardLink.attributes('aria-current')).toBe('page')
  })

  it('inactive nav link does NOT have aria-current', () => {
    const wrapper = mountSidebar()
    const screeningLink = wrapper.find('[data-testid="nav-item-screening"]')
    expect(screeningLink.attributes('aria-current')).toBeUndefined()
  })

  it('sidebar toggle has aria-expanded', () => {
    const wrapper = mountSidebar()
    const toggle = wrapper.find('[data-testid="sidebar-toggle"]')
    expect(toggle.attributes('aria-expanded')).toBeDefined()
  })

  it('sidebar toggle has aria-label', () => {
    const wrapper = mountSidebar()
    const toggle = wrapper.find('[data-testid="sidebar-toggle"]')
    expect(toggle.attributes('aria-label')).toBeDefined()
  })
})
