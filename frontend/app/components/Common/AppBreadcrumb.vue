<template>
  <nav
    :aria-label="$t('common.a11y.breadcrumb')"
    data-testid="app-breadcrumb"
    class="flex items-center gap-2 text-sm select-none"
  >
    <!-- Home -->
    <NuxtLink
      :to="homeRoute"
      class="text-slate-400 hover:text-white transition-colors"
    >
      <i
        class="pi pi-home"
        aria-hidden="true"
      />
      <span class="sr-only">{{ $t('common.a11y.home') }}</span>
    </NuxtLink>

    <template
      v-for="(item, index) in items"
      :key="index"
    >
      <!-- Separator -->
      <i
        class="pi pi-chevron-right text-xs text-slate-600"
        aria-hidden="true"
      />

      <!-- Segment -->
      <NuxtLink
        v-if="item.url"
        :to="item.url"
        class="text-slate-400 hover:text-white transition-colors"
      >
        {{ item.label }}
      </NuxtLink>
      <span
        v-else
        :title="item.label"
        class="text-white font-medium select-none cursor-default truncate max-w-[16rem]"
      >
        {{ item.label }}
      </span>
    </template>
  </nav>
</template>

<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { useRegistryStore } from '~/stores/registry'

const { t: $t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const registryStore = useRegistryStore()

const homeRoute = computed(() => authStore.isAccountant ? '/flight-control' : '/dashboard')

/** Reactive current path — useRoute() is not reactive outside NuxtPage, so read from router ref. */
const currentPath = computed(() => router.currentRoute.value.path)

const ROUTE_LABELS: Record<string, string> = {
  dashboard: 'common.breadcrumb.dashboard',
  screening: 'common.breadcrumb.screening',
  watchlist: 'common.breadcrumb.watchlist',
  epr: 'common.breadcrumb.epr',
  filing: 'common.breadcrumb.filing',
  'flight-control': 'common.breadcrumb.flightControl',
  admin: 'common.breadcrumb.admin',
  registry: 'common.breadcrumb.registry',
  new: 'common.breadcrumb.new'
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Route segments whose top-level index page no longer exists. A parent segment of such a
 * route renders as a non-clickable label so users never land on a deleted page.
 * Story 10.1 removed `/epr` (Anyagkönyvtár); `/epr/filing` still exists until Story 10.6/10.7
 * rebuilds it.
 */
const OBSOLETE_PARENT_SEGMENTS = new Set<string>(['epr'])

const items = computed(() => {
  const segments = currentPath.value.split('/').filter(Boolean)
  if (segments.length === 0) return []

  return segments.map((segment, index) => {
    const prevSegment = index > 0 ? segments[index - 1] : ''
    const isRegistryUuid = UUID_PATTERN.test(segment) && prevSegment === 'registry'

    let label: string
    if (isRegistryUuid) {
      const editing = registryStore.editProduct
      label = editing && editing.id === segment && editing.name
        ? editing.name
        : $t('common.breadcrumb.edit')
    }
    else {
      const i18nKey = ROUTE_LABELS[segment]
      label = i18nKey ? $t(i18nKey) : segment
    }

    const isLast = index === segments.length - 1
    const isObsoleteParent = !isLast && OBSOLETE_PARENT_SEGMENTS.has(segment)

    return {
      label,
      url: isLast || isObsoleteParent
        ? undefined
        : '/' + segments.slice(0, index + 1).join('/')
    }
  })
})
</script>
