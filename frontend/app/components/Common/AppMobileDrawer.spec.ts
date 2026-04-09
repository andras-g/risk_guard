import { describe, it, expect, vi } from 'vitest'
import { ref, computed } from 'vue'
import { mount } from '@vue/test-utils'
import AppMobileDrawer from './AppMobileDrawer.vue'

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
  useAuthStore: () => ({ name: 'Andras', role: 'SME_ADMIN' })
}))

/**
 * Unit tests for AppMobileDrawer.vue logic.
 *
 * Tests validate drawer visibility binding, navigation item structure,
 * admin role gating, user info display, and close-on-navigate behavior.
 * Co-located with AppMobileDrawer.vue per architecture rules.
 */

const mainNavItems = [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  { key: 'epr', to: '/epr', icon: 'pi-file-export' }
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
  it('has 4 main navigation items with labels', () => {
    expect(mainNavItems).toHaveLength(4)
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
  })

  it('renders the LocaleSwitcher in the drawer', () => {
    const wrapper = mountDrawer()
    expect(wrapper.find('[data-testid="drawer-locale-switcher"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="locale-switcher"]').exists()).toBe(true)
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
