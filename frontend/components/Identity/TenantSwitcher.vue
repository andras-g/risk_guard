<script setup lang="ts">
import { useIdentityStore } from '~/stores/identity'

const identityStore = useIdentityStore()
const { mandates, user } = storeToRefs(identityStore)

const selectedTenantId = ref(user.value?.activeTenantId)

watch(() => user.value?.activeTenantId, (newVal) => {
  selectedTenantId.value = newVal
})

async function onTenantChange() {
  if (selectedTenantId.value && selectedTenantId.value !== user.value?.activeTenantId) {
    await identityStore.switchTenant(selectedTenantId.value)
  }
}
</script>

<template>
  <div v-if="user?.role === 'ACCOUNTANT'" class="flex items-center gap-2">
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
