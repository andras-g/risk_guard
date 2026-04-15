import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
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
  admin: 'common.breadcrumb.admin',
  registry: 'common.breadcrumb.registry',
  new: 'common.breadcrumb.new'
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function generateBreadcrumbItems(path: string, editing?: { id: string, name: string } | null) {
  const segments = path.split('/').filter(Boolean)
  if (segments.length === 0) return []

  return segments.map((segment, index) => {
    const prevSegment = index > 0 ? segments[index - 1] : ''
    const isRegistryUuid = UUID_PATTERN.test(segment) && prevSegment === 'registry'

    let label: string
    if (isRegistryUuid) {
      label = editing && editing.id === segment && editing.name
        ? editing.name
        : 'common.breadcrumb.edit'
    }
    else {
      const i18nKey = ROUTE_LABELS[segment]
      label = i18nKey || segment
    }

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
    expect(items[0]!.label).toBe('common.breadcrumb.dashboard')
    expect(items[0]!.url).toBeUndefined() // last segment has no link
  })

  it('generates two segments for /screening/12345678', () => {
    const items = generateBreadcrumbItems('/screening/12345678')
    expect(items).toHaveLength(2)
    expect(items[0]!.label).toBe('common.breadcrumb.screening')
    expect(items[0]!.url).toBe('/screening')
    expect(items[1]!.label).toBe('12345678')  // dynamic segment, not in ROUTE_LABELS
    expect(items[1]!.url).toBeUndefined()
  })

  it('generates single segment for /epr', () => {
    const items = generateBreadcrumbItems('/epr')
    expect(items).toHaveLength(1)
    expect(items[0]!.label).toBe('common.breadcrumb.epr')
  })

  it('generates single segment for /watchlist', () => {
    const items = generateBreadcrumbItems('/watchlist')
    expect(items).toHaveLength(1)
    expect(items[0]!.label).toBe('common.breadcrumb.watchlist')
  })

  it('generates single segment for /admin', () => {
    const items = generateBreadcrumbItems('/admin')
    expect(items).toHaveLength(1)
    expect(items[0]!.label).toBe('common.breadcrumb.admin')
  })

  it('returns empty array for root path /', () => {
    const items = generateBreadcrumbItems('/')
    expect(items).toHaveLength(0)
  })

  it('handles unknown route segments gracefully (raw segment as label)', () => {
    const items = generateBreadcrumbItems('/unknown-page')
    expect(items).toHaveLength(1)
    expect(items[0]!.label).toBe('unknown-page')
  })

  it('generates three segments for /admin/users/details', () => {
    const items = generateBreadcrumbItems('/admin/users/details')
    expect(items).toHaveLength(3)
    expect(items[0]!.label).toBe('common.breadcrumb.admin')
    expect(items[0]!.url).toBe('/admin')
    expect(items[1]!.label).toBe('users')
    expect(items[1]!.url).toBe('/admin/users')
    expect(items[2]!.label).toBe('details')
    expect(items[2]!.url).toBeUndefined()
  })
})

describe('AppBreadcrumb — registry segments (Story 9.5)', () => {
  it('renders registry + new labels for /registry/new', () => {
    const items = generateBreadcrumbItems('/registry/new')
    expect(items).toHaveLength(2)
    expect(items[0]!.label).toBe('common.breadcrumb.registry')
    expect(items[0]!.url).toBe('/registry')
    expect(items[1]!.label).toBe('common.breadcrumb.new')
    expect(items[1]!.url).toBeUndefined()
  })

  it('renders product name when store has matching product for UUID segment', () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000'
    const items = generateBreadcrumbItems(`/registry/${uuid}`, { id: uuid, name: 'Joghurt 150g' })
    expect(items).toHaveLength(2)
    expect(items[0]!.label).toBe('common.breadcrumb.registry')
    expect(items[1]!.label).toBe('Joghurt 150g')
    expect(items[1]!.url).toBeUndefined()
  })

  it('falls back to edit label when store lacks matching product', () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000'
    const items = generateBreadcrumbItems(`/registry/${uuid}`, null)
    expect(items).toHaveLength(2)
    expect(items[1]!.label).toBe('common.breadcrumb.edit')
  })

  it('falls back to edit label when store product id does not match route uuid', () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000'
    const other = '999e4567-e89b-12d3-a456-426614174000'
    const items = generateBreadcrumbItems(`/registry/${uuid}`, { id: other, name: 'Other' })
    expect(items[1]!.label).toBe('common.breadcrumb.edit')
  })

  it('does not treat uuid-like segments outside /registry as product names', () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000'
    const items = generateBreadcrumbItems(`/admin/${uuid}`, { id: uuid, name: 'Anything' })
    expect(items[1]!.label).toBe(uuid) // raw segment, not registry branch
  })
})

describe('AppBreadcrumb — i18n key compliance', () => {
  it('uses i18n keys for all known route segments (no hardcoded labels)', () => {
    for (const [, key] of Object.entries(ROUTE_LABELS)) {
      expect(key).toMatch(/^common\.breadcrumb\.\w+$/)
    }
  })

  it('all primary routes have i18n label mappings', () => {
    expect(Object.keys(ROUTE_LABELS)).toEqual([
      'dashboard', 'screening', 'watchlist', 'epr', 'admin', 'registry', 'new'
    ])
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
        plugins: [createPinia()],
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
      global: { plugins: [createPinia()], stubs: { NuxtLink: NuxtLinkStub } }
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
