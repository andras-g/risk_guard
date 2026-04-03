<template>
  <div>
    <NuxtLoadingIndicator color="#0ea5e9" />
    <NuxtLayout>
      <NuxtPage :page-key="tenantKey" />
    </NuxtLayout>
    <!-- Single global Toast instance — do NOT add <Toast /> in page components -->
    <Toast />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'

const authStore = useAuthStore()
const { activeTenantId } = storeToRefs(authStore)

// Force page re-mount when the active tenant changes so onMounted re-fetches data.
const tenantKey = computed(() => activeTenantId.value ?? 'default')
</script>
