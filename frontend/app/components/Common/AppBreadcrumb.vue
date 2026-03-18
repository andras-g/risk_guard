<template>
  <nav
    :aria-label="$t('common.a11y.breadcrumb')"
    data-testid="app-breadcrumb"
    class="flex items-center gap-2 text-sm"
  >
    <!-- Home -->
    <NuxtLink
      to="/dashboard"
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
        class="text-white font-medium"
      >
        {{ item.label }}
      </span>
    </template>
  </nav>
</template>

<script setup lang="ts">
const { t: $t } = useI18n()
const route = useRoute()

const ROUTE_LABELS: Record<string, string> = {
  dashboard: 'common.breadcrumb.dashboard',
  screening: 'common.breadcrumb.screening',
  watchlist: 'common.breadcrumb.watchlist',
  epr: 'common.breadcrumb.epr',
  admin: 'common.breadcrumb.admin'
}

const items = computed(() => {
  const segments = route.path.split('/').filter(Boolean)
  if (segments.length === 0) return []

  return segments.map((segment, index) => {
    const i18nKey = ROUTE_LABELS[segment]
    const label = i18nKey ? $t(i18nKey) : segment
    const isLast = index === segments.length - 1

    return {
      label,
      url: isLast ? undefined : '/' + segments.slice(0, index + 1).join('/')
    }
  })
})
</script>
