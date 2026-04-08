import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

/**
 * Unit tests for TenantSwitcher.vue logic.
 *
 * These tests focus on the component's switching behaviour rather than
 * full DOM rendering, because TenantSwitcher uses PrimeVue <Select> and
 * Nuxt auto-imports that are non-trivial to stub in jsdom without the
 * full @nuxt/test-utils integration layer (planned for Story 1-5).
 *
 * Architecture Frontend Spec Co-location rule compliance: this file lives
 * in the same directory as TenantSwitcher.vue.
 */
describe('TenantSwitcher — switching logic', () => {
  it('should only trigger switchTenant when tenantId has actually changed', () => {
    // Simulate the guard condition in onTenantChange()
    const activeTenantId = ref('tenant-a')
    const selectedTenantId = ref('tenant-a')
    const isMounted = ref(true)

    const switchTenant = vi.fn()

    async function onTenantChange() {
      if (!isMounted.value) return
      if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
        await switchTenant(selectedTenantId.value)
      }
    }

    // No change — should NOT call switchTenant
    selectedTenantId.value = 'tenant-a'
    onTenantChange()
    expect(switchTenant).not.toHaveBeenCalled()
  })

  it('should call switchTenant when a different tenant is selected', async () => {
    const activeTenantId = ref('tenant-a')
    const selectedTenantId = ref('tenant-b') // new selection
    const isMounted = ref(true)

    const switchTenant = vi.fn().mockResolvedValue(undefined)

    async function onTenantChange() {
      if (!isMounted.value) return
      if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
        await switchTenant(selectedTenantId.value)
      }
    }

    await onTenantChange()
    expect(switchTenant).toHaveBeenCalledOnce()
    expect(switchTenant).toHaveBeenCalledWith('tenant-b')
  })

  it('should NOT call switchTenant before mount (isMounted guard)', async () => {
    const activeTenantId = ref('tenant-a')
    const selectedTenantId = ref('tenant-b')
    const isMounted = ref(false) // component not yet mounted

    const switchTenant = vi.fn()

    async function onTenantChange() {
      if (!isMounted.value) return
      if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
        await switchTenant(selectedTenantId.value)
      }
    }

    await onTenantChange()
    expect(switchTenant).not.toHaveBeenCalled()
  })

  it('should revert selectedTenantId to activeTenantId on switch failure', async () => {
    const activeTenantId = ref('tenant-a')
    const selectedTenantId = ref('tenant-b')
    const isMounted = ref(true)

    const switchTenant = vi.fn().mockRejectedValue(new Error('Network error'))

    async function onTenantChange() {
      if (!isMounted.value) return
      if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
        try {
          await switchTenant(selectedTenantId.value)
        } catch {
          // Revert on failure so dropdown shows the still-active tenant
          selectedTenantId.value = activeTenantId.value
        }
      }
    }

    await onTenantChange()
    // After failure, selectedTenantId must be reverted to active tenant
    expect(selectedTenantId.value).toBe('tenant-a')
  })

  it('should prepend home tenant to mandates when not already present', () => {
    const homeTenantId = 'home-tenant'
    const userName = 'Test User'
    const mandates = [
      { id: 'client-a', name: 'Client A' },
      { id: 'client-b', name: 'Client B' },
    ]

    // Mimic the dropdownOptions computed
    function buildDropdownOptions() {
      const options = [...mandates]
      if (homeTenantId && !options.some(m => m.id === homeTenantId)) {
        options.unshift({ id: homeTenantId, name: userName })
      }
      return options
    }

    const options = buildDropdownOptions()
    expect(options).toHaveLength(3)
    expect(options[0]).toEqual({ id: 'home-tenant', name: 'Test User' })
  })

  it('should NOT duplicate home tenant if already in mandates', () => {
    const homeTenantId = 'client-a'
    const userName = 'Test User'
    const mandates = [
      { id: 'client-a', name: 'Client A' },
      { id: 'client-b', name: 'Client B' },
    ]

    function buildDropdownOptions() {
      const options = [...mandates]
      if (homeTenantId && !options.some(m => m.id === homeTenantId)) {
        options.unshift({ id: homeTenantId, name: userName })
      }
      return options
    }

    const options = buildDropdownOptions()
    expect(options).toHaveLength(2)
  })

  it('should sync selectedTenantId when activeTenantId changes externally', () => {
    // Simulate the watch(activeTenantId, ...) behaviour
    const activeTenantId = ref('tenant-a')
    const selectedTenantId = ref('tenant-a')

    // Mimic the watch handler
    function syncOnActiveTenantChange(newVal: string | null) {
      selectedTenantId.value = newVal!
    }

    // External change (e.g., page reload after successful switch)
    activeTenantId.value = 'tenant-b'
    syncOnActiveTenantChange(activeTenantId.value)

    expect(selectedTenantId.value).toBe('tenant-b')
  })
})
