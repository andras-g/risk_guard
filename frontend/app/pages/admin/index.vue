<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuthStore } from '~/stores/auth'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const ADMIN_ROLES = ['PLATFORM_ADMIN', 'SME_ADMIN', 'ACCOUNTANT']

const isPlatformAdmin = computed(() => authStore.role === 'PLATFORM_ADMIN')

onMounted(() => {
  if (!ADMIN_ROLES.includes(authStore.role ?? '')) {
    router.replace('/dashboard')
  }
})
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
    <h1 class="text-2xl font-bold text-slate-800">
      {{ t('common.nav.admin') }}
    </h1>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <!-- Data Sources: visible to all admin roles -->
      <NuxtLink to="/admin/datasources" class="block group" data-testid="admin-card-datasources">
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

      <!-- EPR Config: PLATFORM_ADMIN only -->
      <NuxtLink v-if="isPlatformAdmin" to="/admin/epr-config" class="block group" data-testid="admin-card-epr-config">
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

      <!-- AI Classifier Usage: PLATFORM_ADMIN only -->
      <NuxtLink v-if="isPlatformAdmin" to="/admin/ai-usage" class="block group" data-testid="admin-card-ai-usage">
        <div class="border rounded-lg p-5 bg-white shadow-sm hover:shadow-md hover:border-indigo-300 transition-all">
          <div class="flex items-center gap-3 mb-2">
            <span class="pi pi-chart-bar text-indigo-600 text-xl" />
            <h2 class="text-lg font-semibold text-slate-800 group-hover:text-indigo-700">
              {{ t('admin.classifier.title') }}
            </h2>
          </div>
          <p class="text-sm text-slate-500">
            {{ t('admin.classifier.subtitle') }}
          </p>
        </div>
      </NuxtLink>

      <!-- GDPR Audit Search: PLATFORM_ADMIN only -->
      <NuxtLink v-if="isPlatformAdmin" to="/admin/audit-search" class="block group" data-testid="admin-card-audit-search">
        <div class="border rounded-lg p-5 bg-white shadow-sm hover:shadow-md hover:border-indigo-300 transition-all">
          <div class="flex items-center gap-3 mb-2">
            <span class="pi pi-shield text-indigo-600 text-xl" />
            <h2 class="text-lg font-semibold text-slate-800 group-hover:text-indigo-700">
              {{ t('admin.auditSearch.title') }}
            </h2>
          </div>
          <p class="text-sm text-slate-500">
            {{ t('admin.auditSearch.subtitle') }}
          </p>
        </div>
      </NuxtLink>
    </div>
  </div>
</template>
