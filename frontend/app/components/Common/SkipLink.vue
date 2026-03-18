<script setup lang="ts">
const { t } = useI18n()

function skipToMain() {
  const main = document.getElementById('main-content')
  if (main) {
    main.focus()
  }
}
</script>

<template>
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[10000] focus:px-4 focus:py-2 focus:rounded-md focus:bg-authority focus:text-white focus:text-sm focus:font-medium focus:shadow-lg"
    data-testid="skip-link"
    @click.prevent="skipToMain"
  >
    {{ t('common.a11y.skipToMain') }}
  </a>
</template>
