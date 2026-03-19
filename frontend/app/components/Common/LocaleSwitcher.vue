<script setup lang="ts">
import { useLocaleSync } from '~/composables/i18n/useLocaleSync'

const props = withDefaults(defineProps<{
  /** Visual variant: 'dark' for sidebar (light text on dark bg), 'light' for public pages (dark text on light bg) */
  variant?: 'dark' | 'light'
}>(), { variant: 'dark' })

const { locale, setLocale, t } = useI18n()
const { changeLocale } = useLocaleSync()

const currentLabel = computed(() => locale.value.toUpperCase())

async function toggleLocale() {
  const next = locale.value === 'hu' ? 'en' : 'hu'
  // In dark variant (authenticated sidebar): persist to backend via useLocaleSync
  // In light variant (public pages): just switch locale locally (no auth, no backend call)
  if (props.variant === 'dark') {
    await changeLocale(next)
  } else {
    await setLocale(next)
  }
}

const variantClasses = computed(() =>
  props.variant === 'dark'
    ? 'text-xs font-semibold text-slate-300 hover:text-white hover:bg-slate-800/50'
    : 'text-sm font-bold text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-300',
)
</script>

<template>
  <button
    :aria-label="t('common.locale.switchTo')"
    :title="t('common.locale.switchTo')"
    :class="['flex items-center justify-center h-8 px-2 rounded-md transition-colors', variantClasses]"
    data-testid="locale-switcher"
    @click="toggleLocale"
  >
    {{ currentLabel }}
  </button>
</template>
