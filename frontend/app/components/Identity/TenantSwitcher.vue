<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { storeToRefs } from 'pinia'

const authStore = useAuthStore()
const { activeTenantId, mandates, isAccountant } = storeToRefs(authStore)

const selectedTenantId = ref(activeTenantId.value)

// Guard against PrimeVue Select @change firing on initial mount when v-model
// is set programmatically. Only process changes after the component is mounted.
const isMounted = ref(false)
onMounted(() => { isMounted.value = true })

watch(activeTenantId, (newVal) => {
  selectedTenantId.value = newVal
})

async function onTenantChange() {
  if (!isMounted.value) return
  if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
    try {
      await authStore.switchTenant(selectedTenantId.value)
    } catch {
      // ContextGuard handles the error display and retry/logout options.
      // Revert selectedTenantId so the dropdown shows the still-active tenant.
      selectedTenantId.value = activeTenantId.value
    }
  }
}
</script>

<template>
  <div
    v-if="authStore.isAuthenticated && isAccountant"
    class="flex items-center gap-2"
  >
    <label
      id="tenant-switcher-label"
      for="tenant-switcher-select"
      class="text-sm font-medium text-slate-400"
    >
      {{ $t('identity.tenantSwitcher.label') }}:
    </label>
    <Select
      v-model="selectedTenantId"
      input-id="tenant-switcher-select"
      aria-labelledby="tenant-switcher-label"
      :options="mandates"
      option-label="name"
      option-value="id"
      filter
      :placeholder="$t('identity.tenantSwitcher.placeholder')"
      class="w-full md:w-64"
      @change="onTenantChange"
    />
  </div>
</template>
