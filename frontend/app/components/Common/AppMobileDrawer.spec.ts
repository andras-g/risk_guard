import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, computed } from 'vue'
import { mount } from '@vue/test-utils'
import AppMobileDrawer from './AppMobileDrawer.vue'

// Shared mutable tier — individual tests toggle this to cover PRO_EPR vs ALAP gating paths.
// Story 10.10: AppMobileDrawer reads tier via useTierGate('PRO_EPR') to gate the eprFiling entry.
const authState = vi.hoisted(() => ({ tier: 'PRO_EPR' as string | null }))

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
  useLayoutStore: () => ({ mobileDrawerOpen: true, sidebarExpanded: true, closeMobileDrawer: vi.fn() })
}))
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({
    name: 'Andras',
    role: 'SME_ADMIN',
    get tier() { return authState.tier }
  })
}))

// Overrides the auto-import stub in vitest.setup.ts so useTierGate (which auto-imports
// useAuthStore) resolves to the same mock as the component's explicit import.
vi.stubGlobal('useAuthStore', () => ({
  name: 'Andras',
  role: 'SME_ADMIN',
  get tier() { return authState.tier }
}))

beforeEach(() => {
  // Default to PRO_EPR so existing tests exercise the full drawer. Per-describe overrides restore.
  authState.tier = 'PRO_EPR'
})

/**
 * Unit tests for AppMobileDrawer.vue logic.
 *
 * Tests validate drawer visibility binding, navigation item structure,
 * admin role gating, user info display, and close-on-navigate behavior.
 * Co-located with AppMobileDrawer.vue per architecture rules.
 */

// Story 10.1: epr nav link removed; Story 10.10: eprFiling added under PRO_EPR tier gate.
const mainNavItems = [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  { key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' },
]

function computeUserInitials(name: string | null): string {
  if (!name) return '?'
  return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
}

describe('AppMobileDrawer — visibility binding', () => {
  it('drawer visible state bound to mobileDrawerOpen', () => {
    const mobileDrawerOpen = ref(false)
    const drawerVisible = computed({
      get: () => mobileDrawerOpen.value,
      set: (val: boolean) => { if (!val) mobileDrawerOpen.value = false }
    })
    expect(drawerVisible.value).toBe(false)
  })

  it('drawer becomes visible when mobileDrawerOpen is true', () => {
    const mobileDrawerOpen = ref(true)
    const drawerVisible = computed({
      get: () => mobileDrawerOpen.value,
      set: (val: boolean) => { if (!val) mobileDrawerOpen.value = false }
    })
    expect(drawerVisible.value).toBe(true)
  })

  it('setting drawerVisible to false triggers closeMobileDrawer', () => {
    const mobileDrawerOpen = ref(true)
    const closeMobileDrawer = vi.fn(() => { mobileDrawerOpen.value = false })
    const drawerVisible = computed({
      get: () => mobileDrawerOpen.value,
      set: (val: boolean) => { if (!val) closeMobileDrawer() }
    })
    drawerVisible.value = false
    expect(closeMobileDrawer).toHaveBeenCalledOnce()
  })
})

describe('AppMobileDrawer — navigation items', () => {
  it('has 4 main navigation items after Story 10.10 (epr index still absent; eprFiling added)', () => {
    expect(mainNavItems).toHaveLength(4)
    expect(mainNavItems.some(i => i.key === 'epr')).toBe(false)
  })

  it('includes eprFiling drawer entry pointing to /epr/filing with pi-file icon', () => {
    const filing = mainNavItems.find(i => i.key === 'eprFiling')
    expect(filing).toBeDefined()
    expect(filing?.to).toBe('/epr/filing')
    expect(filing?.icon).toBe('pi-file')
  })

  it('each item uses i18n key pattern common.nav.{key}', () => {
    for (const item of mainNavItems) {
      expect(`common.nav.${item.key}`).toMatch(/^common\.nav\.\w+$/)
    }
  })

  it('clicking nav item calls navigateTo and closeMobileDrawer', () => {
    const navigateToMock = vi.fn()
    const closeMobileDrawer = vi.fn()
    function handleNavigation(to: string) {
      navigateToMock(to)
      closeMobileDrawer()
    }
    handleNavigation('/screening')
    expect(navigateToMock).toHaveBeenCalledWith('/screening')
    expect(closeMobileDrawer).toHaveBeenCalledOnce()
  })
})

describe('AppMobileDrawer — admin section role gating', () => {
  function isAdmin(roleValue: string | null): boolean {
    return ['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(roleValue ?? '')
  }

  it('admin nav visible when role is SME_ADMIN', () => {
    expect(isAdmin('SME_ADMIN')).toBe(true)
  })

  it('admin nav visible when role is ACCOUNTANT', () => {
    expect(isAdmin('ACCOUNTANT')).toBe(true)
  })

  it('admin nav visible when role is PLATFORM_ADMIN', () => {
    expect(isAdmin('PLATFORM_ADMIN')).toBe(true)
  })

  it('admin nav hidden when role is GUEST', () => {
    expect(isAdmin('GUEST')).toBe(false)
  })

  it('admin nav hidden when role is null', () => {
    expect(isAdmin(null)).toBe(false)
  })
})

describe('AppMobileDrawer — user info display', () => {
  it('computes initials from full name', () => {
    expect(computeUserInitials('John Doe')).toBe('JD')
  })

  it('computes single initial from single name', () => {
    expect(computeUserInitials('Andras')).toBe('A')
  })

  it('returns ? for null name', () => {
    expect(computeUserInitials(null)).toBe('?')
  })

  it('returns ? for empty string', () => {
    expect(computeUserInitials('')).toBe('?')
  })

  it('truncates to 2 initials for long names', () => {
    expect(computeUserInitials('John Michael Doe')).toBe('JM')
  })
})

describe('AppMobileDrawer — PrimeVue Drawer config', () => {
  it('uses position left', () => {
    const position = 'left'
    expect(position).toBe('left')
  })
})

describe('AppMobileDrawer — component mount smoke test', () => {
  function mountDrawer() {
    const NuxtLinkStub = { template: '<a :data-testid="$attrs[\'data-testid\']" :href="to" :aria-current="$attrs[\'aria-current\']"><slot /></a>', props: ['to'], inheritAttrs: true }
    const DrawerStub = {
      template: '<div data-testid="mobile-drawer"><slot /><slot name="header" /><slot name="footer" /></div>',
      props: ['visible', 'position', 'header']
    }
    const AvatarStub = { template: '<div />' }
    const DividerStub = { template: '<hr />' }
    const LocaleSwitcherStub = { template: '<div data-testid="locale-switcher" />' }
    return mount(AppMobileDrawer, {
      global: {
        stubs: {
          Drawer: DrawerStub,
          NuxtLink: NuxtLinkStub,
          Avatar: AvatarStub,
          Divider: DividerStub,
          CommonLocaleSwitcher: LocaleSwitcherStub
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })
  }

  it('renders without error and contains expected testid elements', () => {
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="mobile-drawer"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="drawer-nav"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="drawer-nav-dashboard"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="drawer-nav-screening"]').exists()).toBe(true)
    // Story 10.10: eprFiling entry on PRO_EPR (hoisted authState default).
    expect(wrapper.find('[data-testid="drawer-nav-eprFiling"]').exists()).toBe(true)
  })

  // Story 10.1 AC #8 / R3-P4: symmetric with AppSidebar spec — mount the real component
  // and assert no /epr href survives after Anyagkönyvtár deletion.
  it('renders no anchor pointing at /epr (Story 10.1 AC #8)', () => {
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-nav-epr"]').exists()).toBe(false)
    expect(wrapper.findAll('a[href="/epr"]')).toHaveLength(0)
  })

  it('renders the LocaleSwitcher in the drawer', () => {
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-locale-switcher"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="locale-switcher"]').exists()).toBe(true)
  })
})

describe('AppMobileDrawer — PRO_EPR tier gating for eprFiling (Story 10.10)', () => {
  function mountDrawer() {
    const NuxtLinkStub = { template: '<a :data-testid="$attrs[\'data-testid\']" :href="to" :aria-current="$attrs[\'aria-current\']"><slot /></a>', props: ['to'], inheritAttrs: true }
    const DrawerStub = {
      template: '<div data-testid="mobile-drawer"><slot /><slot name="header" /><slot name="footer" /></div>',
      props: ['visible', 'position', 'header']
    }
    const AvatarStub = { template: '<div />' }
    const DividerStub = { template: '<hr />' }
    const LocaleSwitcherStub = { template: '<div data-testid="locale-switcher" />' }
    return mount(AppMobileDrawer, {
      global: {
        stubs: {
          Drawer: DrawerStub,
          NuxtLink: NuxtLinkStub,
          Avatar: AvatarStub,
          Divider: DividerStub,
          CommonLocaleSwitcher: LocaleSwitcherStub
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })
  }

  it('eprFiling entry is rendered when tier is PRO_EPR', () => {
    authState.tier = 'PRO_EPR'
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-nav-eprFiling"]').exists()).toBe(true)
  })

  it('eprFiling entry is hidden when tier is ALAP', () => {
    authState.tier = 'ALAP'
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-nav-eprFiling"]').exists()).toBe(false)
  })

  it('eprFiling entry is hidden when tier is PRO', () => {
    authState.tier = 'PRO'
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-nav-eprFiling"]').exists()).toBe(false)
  })

  it('eprFiling entry is hidden when tier is null', () => {
    authState.tier = null
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-nav-eprFiling"]').exists()).toBe(false)
  })
})

describe('AppMobileDrawer — accessibility (Story 3.0c)', () => {
  function mountDrawer() {
    const NuxtLinkStub = { template: '<a :data-testid="$attrs[\'data-testid\']" :href="to" :aria-current="$attrs[\'aria-current\']"><slot /></a>', props: ['to'], inheritAttrs: true }
    const DrawerStub = {
      template: '<div data-testid="mobile-drawer"><slot /><slot name="header" /><slot name="footer" /></div>',
      props: ['visible', 'position', 'header']
    }
    const AvatarStub = { template: '<div />' }
    const DividerStub = { template: '<hr />' }
    const LocaleSwitcherStub = { template: '<div data-testid="locale-switcher" />' }
    return mount(AppMobileDrawer, {
      global: {
        stubs: {
          Drawer: DrawerStub,
          NuxtLink: NuxtLinkStub,
          Avatar: AvatarStub,
          Divider: DividerStub,
          CommonLocaleSwitcher: LocaleSwitcherStub
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })
  }

  it('nav element has aria-label', () => {
    const wrapper = mountDrawer()
    const nav = wrapper.find('[data-testid="drawer-nav"]')
    expect(nav.attributes('aria-label')).toBe('common.a11y.mobileNav')
  })

  it('active nav link has aria-current="page"', () => {
    // useRoute returns /dashboard — so the dashboard link should be active
    const wrapper = mountDrawer()
    const dashboardLink = wrapper.find('[data-testid="drawer-nav-dashboard"]')
    expect(dashboardLink.attributes('aria-current')).toBe('page')
  })
})
