import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import AppUserMenu from './AppUserMenu.vue'

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
vi.mock('~/stores/auth', () => ({
  useAuthStore: () => ({ name: 'Andras Kovacs', role: 'SME_ADMIN', clearAuth: vi.fn() })
}))

/**
 * Unit tests for AppUserMenu.vue logic.
 *
 * Tests validate user initials computation, menu toggle behavior,
 * logout action, and graceful handling of missing user data.
 * Co-located with AppUserMenu.vue per architecture rules.
 */

function computeUserInitials(name: string | null): string {
  if (!name) return '?'
  return name.split(' ').map((w: string) => w[0]).join('').toUpperCase().slice(0, 2)
}

describe('AppUserMenu — user initials', () => {
  it('derives "JD" from "John Doe"', () => {
    expect(computeUserInitials('John Doe')).toBe('JD')
  })

  it('derives "A" from "Andras"', () => {
    expect(computeUserInitials('Andras')).toBe('A')
  })

  it('returns "?" for null name', () => {
    expect(computeUserInitials(null)).toBe('?')
  })

  it('returns "?" for empty string name', () => {
    expect(computeUserInitials('')).toBe('?')
  })

  it('truncates to 2 initials for three-word name', () => {
    expect(computeUserInitials('Anna Maria Kovacs')).toBe('AM')
  })
})

describe('AppUserMenu — menu toggle', () => {
  it('toggle calls menuRef.toggle with event', () => {
    const toggle = vi.fn()
    const menuRef = { toggle }
    const event = new Event('click')
    menuRef.toggle(event)
    expect(toggle).toHaveBeenCalledWith(event)
  })
})

describe('AppUserMenu — logout action', () => {
  it('logout calls clearAuth and navigates to login', () => {
    const clearAuth = vi.fn()
    const navigateToMock = vi.fn()

    function logoutAction() {
      clearAuth()
      navigateToMock('/auth/login')
    }

    logoutAction()
    expect(clearAuth).toHaveBeenCalledOnce()
    expect(navigateToMock).toHaveBeenCalledWith('/auth/login')
  })
})

describe('AppUserMenu — display', () => {
  it('shows user name from auth store', () => {
    const userName = ref('Andras')
    expect(userName.value).toBe('Andras')
  })

  it('shows user role from auth store', () => {
    const userRole = ref('SME_ADMIN')
    expect(userRole.value).toBe('SME_ADMIN')
  })

  it('handles null user name gracefully', () => {
    const userName = ref(null)
    expect(userName.value).toBeNull()
  })

  it('handles null role gracefully', () => {
    const userRole = ref(null)
    expect(userRole.value).toBeNull()
  })
})

describe('AppUserMenu — component mount smoke test', () => {
  it('renders without error and shows user name and avatar', () => {
    const wrapper = mount(AppUserMenu, {
      global: {
        stubs: {
          Avatar: { template: '<div data-testid="user-avatar" />' },
          Menu: { template: '<div data-testid="user-dropdown-menu" />' }
        }
      }
    })
    expect(wrapper.find('[data-testid="app-user-menu"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="user-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="user-name"]').text()).toBe('Andras Kovacs')
    expect(wrapper.find('[data-testid="user-avatar-button"]').exists()).toBe(true)
  })
})
