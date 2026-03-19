import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { axe } from 'vitest-axe'
import { axeOptions } from './axe-config'
import AppSidebar from '~/components/Common/AppSidebar.vue'
import AppBreadcrumb from '~/components/Common/AppBreadcrumb.vue'
import SkipLink from '~/components/Common/SkipLink.vue'

/**
 * Axe-core a11y integration test for the authenticated shell components.
 * Covers: AC#3 (skip-link), AC#4 (landmarks, labels), AC#6 (axe scan).
 */

// Mock stores
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
  useLayoutStore: () => ({ sidebarExpanded: true, mobileDrawerOpen: false, toggleSidebar: vi.fn() })
}))
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({ role: 'SME_ADMIN', name: 'Test User', isAccountant: false })
}))
vi.mock('~/stores/watchlist', () => ({
  useWatchlistStore: () => ({ count: 0, entries: [], isLoading: false, error: null, fetchCount: vi.fn() })
}))

describe('Authenticated shell — axe-core a11y scan', () => {
  it('AppSidebar passes axe scan', async () => {
    const NuxtLinkStub = {
      template: '<a :href="to" :aria-current="$attrs[\'aria-current\']"><slot /></a>',
      props: ['to'],
      inheritAttrs: true
    }

    const wrapper = mount(AppSidebar, {
      global: {
        stubs: {
          NuxtLink: NuxtLinkStub,
          Divider: { template: '<hr />' },
          Badge: { template: '<span class="badge-stub"><slot /></span>', props: ['value', 'severity'] }
        },
        directives: { tooltip: () => {} },
        mocks: { $t: (key: string) => key }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })

  it('AppBreadcrumb passes axe scan', async () => {
    const NuxtLinkStub = {
      template: '<a :href="to"><slot /></a>',
      props: ['to']
    }

    const wrapper = mount(AppBreadcrumb, {
      global: {
        stubs: { NuxtLink: NuxtLinkStub }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })

  it('SkipLink passes axe scan', async () => {
    const wrapper = mount(SkipLink, {
      global: {
        mocks: { $t: (key: string) => key }
      }
    })

    const results = await axe(wrapper.element, axeOptions)
    expect(results).toHaveNoViolations()
  })
})
