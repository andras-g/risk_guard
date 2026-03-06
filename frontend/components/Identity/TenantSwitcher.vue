<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { storeToRefs } from 'pinia'

const authStore = useAuthStore()
const { activeTenantId } = storeToRefs(authStore)

// In a real implementation, mandates would come from /me or a separate endpoint
const mandates = ref<{ id: string, name: string }[]>([])

const selectedTenantId = ref(activeTenantId.value)

watch(activeTenantId, (newVal) => {
  selectedTenantId.value = newVal
})

async function onTenantChange() {
  if (selectedTenantId.value && selectedTenantId.value !== activeTenantId.value) {
    try {
      await authStore.switchTenant(selectedTenantId.value)
    } catch (e) {
      // Revert selectedTenantId on failure
      selectedTenantId.value = activeTenantId.value
    }
  }
}
</script>

<template>
  <div v-if="authStore.isAuthenticated" class="flex items-center gap-2">
    <span class="text-sm font-medium text-slate-400">
      {{ $t('identity.tenantSwitcher.label') }}:
    </span>
    <Select
      v-model="selectedTenantId"
      :options="mandates"
      optionLabel="name"
      optionValue="id"
      :placeholder="$t('identity.tenantSwitcher.placeholder')"
      class="w-full md:w-64"
      @change="onTenantChange"
    />
  </div>
</template>
