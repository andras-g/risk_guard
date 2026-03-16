<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <!-- Sidebar (desktop/tablet) -->
    <CommonAppSidebar />

    <!-- Main content area — offset by sidebar width -->
    <div
      :class="[
        'flex flex-col min-h-screen transition-all',
        sidebarExpanded ? 'md:ml-60' : 'md:ml-16'
      ]"
    >
      <!-- Top Bar -->
      <CommonAppTopBar />

      <!-- Page Content -->
      <main class="flex-1 p-3 sm:p-4 lg:p-6">
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
import { useLayoutStore } from '~/stores/layout'

const layoutStore = useLayoutStore()
const { sidebarExpanded } = storeToRefs(layoutStore)

onMounted(() => {
  layoutStore.initFromStorage()
})
</script>
