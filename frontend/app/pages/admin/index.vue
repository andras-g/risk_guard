<script setup lang="ts">
const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.admin') }}
    </h1>
    <p class="text-slate-500">
      {{ t('common.placeholder.comingSoon') }}
    </p>
  </div>
</template>
