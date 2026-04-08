<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { activeTenantId, mandates, isAccountant, homeTenantId, name: userName } = storeToRefs(authStore)

/** Mandates list with the accountant's own (home) tenant prepended. */
const dropdownOptions = computed(() => {
  const options = [...mandates.value]
  if (homeTenantId.value && !options.some(m => m.id === homeTenantId.value)) {
    options.unshift({
      id: homeTenantId.value,
      name: `${userName.value ?? t('identity.tenantSwitcher.ownAccount')}`,
    })
  }
  return options
})

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
      router.push('/flight-control')
    } catch {
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
      :options="dropdownOptions"
      option-label="name"
      option-value="id"
      filter
      :placeholder="$t('identity.tenantSwitcher.placeholder')"
      class="w-full md:w-64"
      @change="onTenantChange"
    />
  </div>
</template>
