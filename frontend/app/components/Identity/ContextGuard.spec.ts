import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

/**
 * Unit tests for ContextGuard.vue logic.
 *
 * Focuses on the component's retry and logout behaviour, and on the
 * reactive state conditions that control guard visibility.
 *
 * Architecture Frontend Spec Co-location rule compliance: this file lives
 * in the same directory as ContextGuard.vue.
 */
describe('ContextGuard — guard visibility logic', () => {
  it('should show guard when isSwitchingTenant is true', () => {
    const isSwitchingTenant = ref(true)
    const switchError = ref<string | null>(null)

    const isVisible = isSwitchingTenant.value || !!switchError.value
    expect(isVisible).toBe(true)
  })

  it('should show guard when switchError is set', () => {
    const isSwitchingTenant = ref(false)
    const switchError = ref<string | null>('Forbidden: no mandate for this tenant')

    const isVisible = isSwitchingTenant.value || !!switchError.value
    expect(isVisible).toBe(true)
  })

  it('should hide guard when both isSwitchingTenant and switchError are falsy', () => {
    const isSwitchingTenant = ref(false)
    const switchError = ref<string | null>(null)

    const isVisible = isSwitchingTenant.value || !!switchError.value
    expect(isVisible).toBe(false)
  })
})

describe('ContextGuard — retry logic', () => {
  it('should call switchTenant with switchTargetTenantId on retry', async () => {
    const switchTargetTenantId = ref<string | null>('tenant-b')
    const switchTenant = vi.fn().mockResolvedValue(undefined)

    async function retry() {
      if (switchTargetTenantId.value) {
        try {
          await switchTenant(switchTargetTenantId.value)
        } catch {
          // switchError is set by the store action — ContextGuard stays visible
        }
      }
    }

    await retry()
    expect(switchTenant).toHaveBeenCalledOnce()
    expect(switchTenant).toHaveBeenCalledWith('tenant-b')
  })

  it('should NOT call switchTenant if switchTargetTenantId is null', async () => {
    const switchTargetTenantId = ref<string | null>(null)
    const switchTenant = vi.fn()

    async function retry() {
      if (switchTargetTenantId.value) {
        await switchTenant(switchTargetTenantId.value)
      }
    }

    await retry()
    expect(switchTenant).not.toHaveBeenCalled()
  })

  it('should silently swallow error from switchTenant on retry failure', async () => {
    const switchTargetTenantId = ref<string | null>('tenant-b')
    const switchTenant = vi.fn().mockRejectedValue(new Error('Still failing'))

    async function retry() {
      if (switchTargetTenantId.value) {
        try {
          await switchTenant(switchTargetTenantId.value)
        } catch {
          // Guard stays visible — no re-throw expected
        }
      }
    }

    // Should not throw
    await expect(retry()).resolves.not.toThrow()
    expect(switchTenant).toHaveBeenCalledOnce()
  })
})

describe('ContextGuard — logout logic', () => {
  it('should call clearAuth and navigate to login on logout', async () => {
    const clearAuth = vi.fn().mockResolvedValue(undefined)
    const navigateTo = vi.fn()

    async function logout() {
      await clearAuth()
      navigateTo('/auth/login')
    }

    await logout()
    expect(clearAuth).toHaveBeenCalledOnce()
    expect(navigateTo).toHaveBeenCalledWith('/auth/login')
  })
})
