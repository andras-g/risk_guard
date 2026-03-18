<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/auth'
import { useFocusTrap } from '~/composables/a11y/useFocusTrap'

const authStore = useAuthStore()
const { isSwitchingTenant, switchError, switchTargetTenantId } = storeToRefs(authStore)

// Focus trap: when overlay is visible, trap Tab within its interactive elements
const overlayRef = ref<HTMLElement | null>(null)
const isOverlayVisible = computed(() => !!(isSwitchingTenant.value || switchError.value))
useFocusTrap(overlayRef, isOverlayVisible)

async function retry() {
  if (switchTargetTenantId.value) {
    try {
      await authStore.switchTenant(switchTargetTenantId.value)
    } catch {
      // switchError is set by the store action — ContextGuard stays visible
    }
  }
}

async function logout() {
  await authStore.clearAuth()
  navigateTo('/auth/login')
}
</script>

<template>
  <div
    v-if="isSwitchingTenant || switchError"
    ref="overlayRef"
    role="dialog"
    :aria-label="$t('identity.contextGuard.errorTitle')"
    aria-modal="true"
    class="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/80 backdrop-blur-sm"
  >
    <div class="max-w-md w-full p-8 bg-slate-800 border border-slate-700 rounded-lg shadow-xl text-center">
      <div v-if="isSwitchingTenant && !switchError">
        <ProgressSpinner />
        <p class="mt-4 text-slate-200 font-medium">
          {{ $t('identity.contextGuard.switching') }}
        </p>
      </div>
      
      <div v-else-if="switchError">
        <i class="pi pi-exclamation-triangle text-red-500 text-4xl mb-4" />
        <h2 class="text-xl font-bold text-white mb-2">
          {{ $t('identity.contextGuard.errorTitle') }}
        </h2>
        <p class="text-slate-400 mb-6">
          {{ $t('identity.contextGuard.switchFailed') }}
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
