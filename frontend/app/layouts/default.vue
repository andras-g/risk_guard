<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <CommonSkipLink />

    <!-- Sidebar (desktop/tablet) -->
    <CommonAppSidebar />

    <!-- Main content area — offset by sidebar width -->
    <div
      :class="[
        'flex flex-col min-h-screen transition-all',
        sidebarExpanded ? 'md:ml-60' : 'md:ml-16'
      ]"
    >
      <!-- Global quarantine banner (SME_ADMIN only, dismissible) — AC#5 -->
      <div
        v-if="quarantinedAdapters.length > 0 && !bannerDismissed"
        role="alert"
        class="bg-amber-50 border-b border-amber-200 px-4 py-2 text-sm text-amber-800 flex items-center gap-2"
      >
        <span class="font-semibold">{{ t('admin.datasources.banner.title') }}:</span>
        <span v-for="a in quarantinedAdapters" :key="a.adapterName">
          {{ t('admin.datasources.banner.underMaintenance', { name: a.adapterName }) }}
        </span>
        <button
          class="ml-auto text-amber-600 hover:text-amber-800 focus:outline-none"
          :aria-label="t('common.actions.dismiss')"
          @click="bannerDismissed = true"
        >
          ✕
        </button>
      </div>

      <!-- Top Bar -->
      <CommonAppTopBar />

      <!-- Page Content -->
      <main
        id="main-content"
        tabindex="-1"
        class="flex-1 p-3 sm:p-4 lg:p-6"
      >
        <div class="mx-auto max-w-7xl">
          <slot />
        </div>
      </main>
    </div>

    <!-- Mobile Drawer -->
    <CommonAppMobileDrawer />

    <!-- Context Guard (Safety Interstitial) -->
    <IdentityContextGuard />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useHealthStore } from '~/stores/health'
import { useIdentityStore } from '~/stores/identity'
import { useLayoutStore } from '~/stores/layout'

const { t } = useI18n()
const layoutStore = useLayoutStore()
const { sidebarExpanded } = storeToRefs(layoutStore)

const identityStore = useIdentityStore()
const healthStore = useHealthStore()

const quarantinedAdapters = computed(() =>
  healthStore.adapters.filter(a => a.circuitBreakerState === 'FORCED_OPEN')
)
const bannerDismissed = ref(false)

onMounted(() => {
  layoutStore.initFromStorage()
  if (identityStore.user?.role === 'SME_ADMIN') {
    healthStore.fetchHealth()
  }
})
</script>
