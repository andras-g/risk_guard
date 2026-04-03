<template>
  <aside
    :class="[
      'hidden md:flex flex-col fixed inset-y-0 left-0 z-40 bg-slate-900 transition-all',
      sidebarExpanded ? 'w-60' : 'w-16'
    ]"
    data-testid="app-sidebar"
  >
    <!-- Logo -->
    <div class="h-14 flex items-center px-4 border-b border-slate-800 shrink-0">
      <div
        class="text-xl font-bold bg-gradient-to-r from-indigo-400 to-indigo-600 bg-clip-text text-transparent"
        data-testid="sidebar-logo"
      >
        <span v-if="sidebarExpanded">{{ $t('common.app.name') }}</span>
        <span v-else>{{ $t('common.app.shortName') }}</span>
      </div>
    </div>

    <!-- Navigation -->
    <nav
      :aria-label="$t('common.a11y.sidebarNav')"
      class="flex-1 py-2 overflow-y-auto"
      data-testid="sidebar-nav"
    >
      <!-- Main section -->
      <ul class="space-y-0.5 px-2">
        <li
          v-for="item in mainNavItems"
          :key="item.key"
        >
          <!-- Conditionally render: accountantOnly items are ONLY shown to ACCOUNTANTs (AC8) -->
          <NuxtLink
            v-if="!item.accountantOnly || isAccountant"
            v-tooltip.right="!sidebarExpanded ? navLabel(item) : undefined"
            :to="item.to"
            :class="[
              'flex items-center h-10 rounded-md transition-colors',
              sidebarExpanded ? 'px-3 gap-2' : 'justify-center',
              isActive(item.to)
                ? 'bg-slate-800 border-l-3 border-indigo-600 text-white'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            ]"
            :aria-current="isActive(item.to) ? 'page' : undefined"
            :data-testid="`nav-item-${item.key}`"
          >
            <i :class="['pi', item.icon, 'text-base']" />
            <span
              v-if="sidebarExpanded"
              class="text-sm font-medium flex-1"
            >{{ navLabel(item) }}</span>
            <Badge
              v-if="item.showBadge && watchlistCount > 0 && sidebarExpanded"
              :value="watchlistCount"
              severity="info"
              :data-testid="`badge-${item.key}`"
            />
          </NuxtLink>
        </li>
      </ul>

      <!-- Divider -->
      <Divider class="my-2 border-slate-800" />

      <!-- Admin section (role-gated) -->
      <ul
        v-if="isAdmin"
        class="space-y-0.5 px-2"
        data-testid="admin-nav-section"
      >
        <li>
          <NuxtLink
            v-tooltip.right="!sidebarExpanded ? $t('common.nav.admin') : undefined"
            to="/admin"
            :class="[
              'flex items-center h-10 rounded-md transition-colors',
              sidebarExpanded ? 'px-3 gap-2' : 'justify-center',
              isActive('/admin')
                ? 'bg-slate-800 border-l-3 border-indigo-600 text-white'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            ]"
            :aria-current="isActive('/admin') ? 'page' : undefined"
            data-testid="nav-item-admin"
          >
            <i class="pi pi-cog text-base" />
            <span
              v-if="sidebarExpanded"
              class="text-sm font-medium"
            >{{ $t('common.nav.admin') }}</span>
          </NuxtLink>
        </li>
      </ul>
    </nav>

    <!-- Collapse toggle -->
    <div class="border-t border-slate-800 p-2 shrink-0">
      <button
        :aria-label="sidebarExpanded ? $t('common.sidebar.collapse') : $t('common.sidebar.expand')"
        :aria-expanded="sidebarExpanded"
        class="flex items-center justify-center w-full h-10 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/50 transition-colors"
        data-testid="sidebar-toggle"
        @click="layoutStore.toggleSidebar()"
      >
        <i :class="['pi', sidebarExpanded ? 'pi-chevron-left' : 'pi-chevron-right', 'text-base']" />
        <span
          v-if="sidebarExpanded"
          class="ml-2 text-sm font-medium"
        >{{ $t('common.sidebar.collapse') }}</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import Badge from 'primevue/badge'
import Divider from 'primevue/divider'
import { useLayoutStore } from '~/stores/layout'
import { useAuthStore } from '~/stores/auth'
import { useWatchlistStore } from '~/stores/watchlist'

const { t: $t } = useI18n()
const router = useRouter()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()
const watchlistStore = useWatchlistStore()

const { sidebarExpanded } = storeToRefs(layoutStore)
const { role, isAccountant } = storeToRefs(authStore)

const isAdmin = computed(() => role.value === 'SME_ADMIN')
const watchlistCount = computed(() => watchlistStore.count)

const mainNavItems = computed(() => {
  const items = [
    // For accountants, flightControl IS their dashboard — hide the regular entry to avoid
    // two sidebar links pointing to the same /flight-control URL (which makes both unclickable).
    ...(!isAccountant.value ? [{ key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' }] : []),
    { key: 'flightControl', to: '/flight-control', icon: 'pi-objects-column', accountantOnly: true },
    { key: 'screening', to: '/screening', icon: 'pi-search' },
    { key: 'watchlist', to: '/watchlist', icon: 'pi-eye', showBadge: true },
    { key: 'epr', to: '/epr', icon: 'pi-file-export' },
  ]
  return items
})

// Fetch watchlist count on mount for sidebar badge
onMounted(async () => {
  await watchlistStore.fetchCount()
})

/** Resolve the display label for a nav item — accountantOnly items use notification namespace. */
function navLabel(item: { key: string, accountantOnly?: boolean }): string {
  // For accountants, flightControl IS their "Dashboard" — use the dashboard label
  if (item.accountantOnly && isAccountant.value) {
    return $t('common.nav.dashboard')
  }
  if (item.accountantOnly) {
    return $t('notification.flightControl.navLabel')
  }
  return $t(`common.nav.${item.key}`)
}

/** Reactive current path — useRoute() is not reactive outside NuxtPage, so read from router ref. */
const currentPath = computed(() => router.currentRoute.value.path)

function isActive(path: string): boolean {
  return currentPath.value === path || currentPath.value.startsWith(path + '/')
}
</script>
