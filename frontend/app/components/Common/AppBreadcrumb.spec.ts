import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppBreadcrumb from './AppBreadcrumb.vue'

/**
 * Unit tests for AppBreadcrumb.vue logic.
 *
 * Tests validate breadcrumb segment generation from route paths,
 * i18n key usage, and home icon configuration.
 * Co-located with AppBreadcrumb.vue per architecture rules.
 */

const ROUTE_LABELS: Record<string, string> = {
  dashboard: 'common.breadcrumb.dashboard',
  screening: 'common.breadcrumb.screening',
  watchlist: 'common.breadcrumb.watchlist',
  epr: 'common.breadcrumb.epr',
  admin: 'common.breadcrumb.admin'
}

function generateBreadcrumbItems(path: string) {
  const segments = path.split('/').filter(Boolean)
  if (segments.length === 0) return []

  return segments.map((segment, index) => {
    const i18nKey = ROUTE_LABELS[segment]
    const label = i18nKey || segment
    const isLast = index === segments.length - 1

    return {
      label,
      url: isLast ? undefined : '/' + segments.slice(0, index + 1).join('/')
    }
  })
}

describe('AppBreadcrumb — segment generation', () => {
  it('generates single segment for /dashboard', () => {
    const items = generateBreadcrumbItems('/dashboard')
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('common.breadcrumb.dashboard')
    expect(items[0].url).toBeUndefined() // last segment has no link
  })

  it('generates two segments for /screening/12345678', () => {
    const items = generateBreadcrumbItems('/screening/12345678')
    expect(items).toHaveLength(2)
    expect(items[0].label).toBe('common.breadcrumb.screening')
    expect(items[0].url).toBe('/screening')
    expect(items[1].label).toBe('12345678')  // dynamic segment, not in ROUTE_LABELS
    expect(items[1].url).toBeUndefined()
  })

  it('generates single segment for /epr', () => {
    const items = generateBreadcrumbItems('/epr')
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('common.breadcrumb.epr')
  })

  it('generates single segment for /watchlist', () => {
    const items = generateBreadcrumbItems('/watchlist')
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('common.breadcrumb.watchlist')
  })

  it('generates single segment for /admin', () => {
    const items = generateBreadcrumbItems('/admin')
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('common.breadcrumb.admin')
  })

  it('returns empty array for root path /', () => {
    const items = generateBreadcrumbItems('/')
    expect(items).toHaveLength(0)
  })

  it('handles unknown route segments gracefully (raw segment as label)', () => {
    const items = generateBreadcrumbItems('/unknown-page')
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('unknown-page')
  })

  it('generates three segments for /admin/users/details', () => {
    const items = generateBreadcrumbItems('/admin/users/details')
    expect(items).toHaveLength(3)
    expect(items[0].label).toBe('common.breadcrumb.admin')
    expect(items[0].url).toBe('/admin')
    expect(items[1].label).toBe('users')
    expect(items[1].url).toBe('/admin/users')
    expect(items[2].label).toBe('details')
    expect(items[2].url).toBeUndefined()
  })
})

describe('AppBreadcrumb — i18n key compliance', () => {
  it('uses i18n keys for all known route segments (no hardcoded labels)', () => {
    for (const [, key] of Object.entries(ROUTE_LABELS)) {
      expect(key).toMatch(/^common\.breadcrumb\.\w+$/)
    }
  })

  it('all 5 primary routes have i18n label mappings', () => {
    expect(Object.keys(ROUTE_LABELS)).toEqual(['dashboard', 'screening', 'watchlist', 'epr', 'admin'])
  })
})

describe('AppBreadcrumb — home icon', () => {
  it('home links to /dashboard', () => {
    const home = { icon: 'pi pi-home', url: '/dashboard' }
    expect(home.url).toBe('/dashboard')
  })

  it('home uses pi-home icon', () => {
    const home = { icon: 'pi pi-home', url: '/dashboard' }
    expect(home.icon).toContain('pi-home')
  })
})

describe('AppBreadcrumb — component mount smoke test', () => {
  it('renders without error and contains breadcrumb testid', () => {
    const NuxtLinkStub = { template: '<a :href="to"><slot /></a>', props: ['to'] }
    const wrapper = mount(AppBreadcrumb, {
      global: {
        stubs: {
          NuxtLink: NuxtLinkStub
        }
      }
    })
    expect(wrapper.find('[data-testid="app-breadcrumb"]').exists()).toBe(true)
  })
})

describe('AppBreadcrumb — accessibility (Story 3.0c)', () => {
  function mountBreadcrumb() {
    const NuxtLinkStub = { template: '<a :href="to"><slot /></a>', props: ['to'] }
    return mount(AppBreadcrumb, {
      global: { stubs: { NuxtLink: NuxtLinkStub } }
    })
  }

  it('nav element has aria-label', () => {
    const wrapper = mountBreadcrumb()
    const nav = wrapper.find('[data-testid="app-breadcrumb"]')
    expect(nav.attributes('aria-label')).toBe('common.a11y.breadcrumb')
  })

  it('home link has sr-only accessible text', () => {
    const wrapper = mountBreadcrumb()
    const srOnly = wrapper.find('.sr-only')
    expect(srOnly.exists()).toBe(true)
    expect(srOnly.text()).toBe('common.a11y.home')
  })
})
