<template>
  <Drawer
    v-model:visible="drawerVisible"
    position="left"
    :header="$t('common.nav.dashboard')"
    class="w-72"
    data-testid="mobile-drawer"
    @hide="layoutStore.closeMobileDrawer()"
  >
    <template #header>
      <div class="text-lg font-bold bg-gradient-to-r from-indigo-400 to-indigo-600 bg-clip-text text-transparent">
        {{ $t('common.app.name') }}
      </div>
    </template>

    <!-- Navigation -->
    <nav
      :aria-label="$t('common.a11y.mobileNav')"
      class="flex flex-col gap-1"
      data-testid="drawer-nav"
    >
      <NuxtLink
        v-for="item in mainNavItems"
        :key="item.to"
        :to="item.to"
        class="flex items-center gap-3 px-3 h-11 rounded-md text-sm font-medium transition-colors"
        :class="[
          isActive(item.to)
            ? 'bg-slate-100 text-authority'
            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
        ]"
        :aria-current="isActive(item.to) ? 'page' : undefined"
        :data-testid="`drawer-nav-${item.key}`"
        @click="handleNavigation(item.to)"
      >
        <i :class="['pi', item.icon, 'text-base']" />
        <span>{{ $t(`common.nav.${item.key}`) }}</span>
      </NuxtLink>

      <!-- Admin section (role-gated) -->
      <Divider v-if="isAdmin" />
      <NuxtLink
        v-if="isAdmin"
        to="/admin"
        class="flex items-center gap-3 px-3 h-11 rounded-md text-sm font-medium transition-colors"
        :class="[
          isActive('/admin')
            ? 'bg-slate-100 text-authority'
            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
        ]"
        :aria-current="isActive('/admin') ? 'page' : undefined"
        data-testid="drawer-nav-admin"
        @click="handleNavigation('/admin')"
      >
        <i class="pi pi-cog text-base" />
        <span>{{ $t('common.nav.admin') }}</span>
      </NuxtLink>
    </nav>

    <!-- Locale switcher -->
    <div
      class="mt-4 px-3"
      data-testid="drawer-locale-switcher"
    >
      <CommonLocaleSwitcher />
    </div>

    <!-- User info at bottom -->
    <template #footer>
      <div
        class="flex items-center gap-3 px-3 py-2 border-t border-slate-200"
        data-testid="drawer-user-info"
      >
        <Avatar
          :label="userInitials"
          shape="circle"
          size="small"
        />
        <div class="flex-1 min-w-0">
          <div class="text-sm font-medium text-slate-900 truncate">
            {{ userName }}
          </div>
          <div class="text-xs text-slate-500 truncate">
            {{ userRole }}
          </div>
        </div>
      </div>
    </template>
  </Drawer>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useLayoutStore } from '~/stores/layout'
import { useAuthStore } from '~/stores/auth'
import { useTierGate } from '~/composables/auth/useTierGate'

const { t: $t } = useI18n()
const route = useRoute()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()

const { mobileDrawerOpen } = storeToRefs(layoutStore)
const { name: userName, role: userRole } = storeToRefs(authStore)
const { hasAccess: hasProEpr } = useTierGate('PRO_EPR')

const drawerVisible = computed({
  get: () => mobileDrawerOpen.value,
  set: (val: boolean) => {
    if (!val) layoutStore.closeMobileDrawer()
  }
})

const isAdmin = computed(() => ['SME_ADMIN', 'ACCOUNTANT', 'PLATFORM_ADMIN'].includes(userRole.value ?? ''))

const userInitials = computed(() => {
  const n = userName.value
  if (!n) return '?'
  return n.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
})

const mainNavItems = computed(() => [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  // Story 10.10: quarterly EPR filing entry, PRO_EPR tier-gated. Pre-existing registry gap deferred to Epic 11.
  ...(hasProEpr.value ? [{ key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }] : []),
])

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}

function handleNavigation(to: string) {
  navigateTo(to)
  layoutStore.closeMobileDrawer()
}
</script>
