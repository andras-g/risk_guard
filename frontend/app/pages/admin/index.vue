<script setup lang="ts">
const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.admin') }}
    </h1>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <NuxtLink to="/admin/datasources" class="block group">
        <div class="border rounded-lg p-5 bg-white shadow-sm hover:shadow-md hover:border-indigo-300 transition-all">
          <div class="flex items-center gap-3 mb-2">
            <span class="pi pi-server text-indigo-600 text-xl" />
            <h2 class="text-lg font-semibold text-slate-800 group-hover:text-indigo-700">
              {{ t('admin.datasources.title') }}
            </h2>
          </div>
          <p class="text-sm text-slate-500">
            {{ t('admin.datasources.subtitle') }}
          </p>
        </div>
      </NuxtLink>

      <NuxtLink to="/admin/epr-config" class="block group">
        <div class="border rounded-lg p-5 bg-white shadow-sm hover:shadow-md hover:border-indigo-300 transition-all">
          <div class="flex items-center gap-3 mb-2">
            <span class="pi pi-file-edit text-indigo-600 text-xl" />
            <h2 class="text-lg font-semibold text-slate-800 group-hover:text-indigo-700">
              {{ t('admin.eprConfig.title') }}
            </h2>
          </div>
          <p class="text-sm text-slate-500">
            {{ t('admin.eprConfig.subtitle') }}
          </p>
        </div>
      </NuxtLink>
    </div>
  </div>
</template>
