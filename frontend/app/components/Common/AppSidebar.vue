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
          :key="item.to"
        >
          <NuxtLink
            v-tooltip.right="!sidebarExpanded ? $t(`common.nav.${item.key}`) : undefined"
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
              class="text-sm font-medium"
            >{{ $t(`common.nav.${item.key}`) }}</span>
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
import { useLayoutStore } from '~/stores/layout'
import { useAuthStore } from '~/stores/auth'

const { t: $t } = useI18n()
const route = useRoute()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()

const { sidebarExpanded } = storeToRefs(layoutStore)
const { role } = storeToRefs(authStore)

const isAdmin = computed(() => role.value === 'ADMIN')

const mainNavItems = [
  { key: 'dashboard', to: '/dashboard', icon: 'pi-th-large' },
  { key: 'screening', to: '/screening', icon: 'pi-search' },
  { key: 'watchlist', to: '/watchlist', icon: 'pi-eye' },
  { key: 'epr', to: '/epr', icon: 'pi-file-export' }
]

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}
</script>
