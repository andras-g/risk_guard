<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const { activeTenantId } = storeToRefs(authStore)

const isTransitioning = ref(false)
const error = ref<string | null>(null)

async function retry() {
  error.value = null
  if (activeTenantId.value) {
    try {
      isTransitioning.value = true
      await authStore.switchTenant(activeTenantId.value)
    } catch (e) {
      error.value = t('identity.contextGuard.switchFailed')
    } finally {
      isTransitioning.value = false
    }
  }
}

function logout() {
  authStore.clearAuth()
  navigateTo('/auth/login')
}
</script>

<template>
  <div
    v-if="isTransitioning || error"
    class="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/80 backdrop-blur-sm"
  >
    <div class="max-w-md w-full p-8 bg-slate-800 border border-slate-700 rounded-lg shadow-xl text-center">
      <div v-if="isTransitioning">
        <ProgressSpinner />
        <p class="mt-4 text-slate-200 font-medium">
          {{ $t('identity.contextGuard.switching') }}
        </p>
      </div>
      
      <div v-else-if="error">
        <i class="pi pi-exclamation-triangle text-red-500 text-4xl mb-4" />
        <h2 class="text-xl font-bold text-white mb-2">
          {{ $t('identity.contextGuard.errorTitle') }}
        </h2>
        <p class="text-slate-400 mb-6">
          {{ error }}
        </p>
        
        <div class="flex flex-col gap-3">
          <Button
            :label="$t('common.actions.retry')"
            class="w-full"
            @click="retry"
          />
          <Button
            :label="$t('auth.logout')"
            severity="secondary"
            variant="text"
            class="w-full"
            @click="logout"
          />
        </div>
      </div>
    </div>
  </div>
</template>
