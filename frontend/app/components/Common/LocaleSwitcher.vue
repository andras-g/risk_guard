<script setup lang="ts">
import { useLocaleSync } from '~/composables/i18n/useLocaleSync'

const { locale, t } = useI18n()
const { changeLocale } = useLocaleSync()

const otherLocale = computed(() => (locale.value === 'hu' ? 'en' : 'hu'))
const currentLabel = computed(() => locale.value.toUpperCase())

async function toggleLocale() {
  await changeLocale(otherLocale.value)
}
</script>

<template>
  <button
    :aria-label="t('common.locale.switchTo')"
    :title="t('common.locale.switchTo')"
    class="flex items-center justify-center h-8 px-2 rounded-md text-xs font-semibold text-slate-300 hover:text-white hover:bg-slate-800/50 transition-colors"
    data-testid="locale-switcher"
    @click="toggleLocale"
  >
    {{ currentLabel }}
  </button>
</template>
