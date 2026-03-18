<template>
  <header
    class="h-14 sticky top-0 z-50 flex items-center justify-between px-4 bg-slate-900 border-b border-slate-800"
    data-testid="app-topbar"
  >
    <!-- Left side -->
    <div class="flex items-center gap-3">
      <!-- Mobile hamburger -->
      <button
        :aria-label="$t('common.a11y.openMenu')"
        :aria-expanded="mobileDrawerOpen"
        class="md:hidden flex items-center justify-center w-10 h-10 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/50 transition-colors"
        data-testid="hamburger-button"
        @click="layoutStore.openMobileDrawer()"
      >
        <i class="pi pi-bars text-lg" />
      </button>

      <!-- Desktop / tablet breadcrumb -->
      <div class="hidden md:block">
        <CommonAppBreadcrumb />
      </div>

      <!-- Mobile page title -->
      <span
        class="md:hidden text-sm font-semibold text-white"
        data-testid="mobile-page-title"
      >
        {{ currentPageTitle }}
      </span>
    </div>

    <!-- Right side -->
    <div class="flex items-center gap-3">
      <!-- Tenant Switcher (accountant role only) -->
      <IdentityTenantSwitcher v-if="isAccountant" />

      <div
        v-if="isAccountant"
        class="h-6 w-px bg-slate-700"
      />

      <!-- Locale switcher -->
      <CommonLocaleSwitcher />

      <!-- User menu -->
      <CommonAppUserMenu />
    </div>
  </header>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useLayoutStore } from '~/stores/layout'
import { useAuthStore } from '~/stores/auth'

const { t: $t } = useI18n()
const route = useRoute()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()

const { mobileDrawerOpen } = storeToRefs(layoutStore)
const { isAccountant } = storeToRefs(authStore)

const currentPageTitle = computed(() => {
  const segment = route.path.split('/').filter(Boolean)[0] || 'dashboard'
  return $t(`common.nav.${segment}`) || segment
})
</script>
