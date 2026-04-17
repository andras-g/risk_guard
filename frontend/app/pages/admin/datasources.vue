<script setup lang="ts">
import Button from 'primevue/button'
import { useHealthStore } from '~/stores/health'
import { useAuthStore } from '~/stores/auth'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const { t } = useI18n()
const { formatRelative } = useDateRelative()
const router = useRouter()
const healthStore = useHealthStore()
const authStore = useAuthStore()

const quarantining = computed(() => healthStore.quarantining)
const isPlatformAdmin = computed(() => authStore.role === 'PLATFORM_ADMIN')

async function handleQuarantine(adapterName: string, quarantined: boolean) {
  await healthStore.quarantineAdapter(adapterName, quarantined)
}

// 30-second polling handle
let pollInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  if (authStore.role !== 'PLATFORM_ADMIN' && authStore.role !== 'SME_ADMIN' && authStore.role !== 'ACCOUNTANT') {
    router.replace('/dashboard')
    return
  }
  // One fetch for all roles — NavCredentialManager needs adapters[0].dataSourceMode.
  // Only platform admins see the live dashboard, so only they get the 30s poll.
  healthStore.fetchHealth()
  if (isPlatformAdmin.value && pollInterval === null) {
    pollInterval = setInterval(() => {
      healthStore.fetchHealth()
    }, 30_000)
  }
})

onUnmounted(() => {
  if (pollInterval !== null) {
    clearInterval(pollInterval)
    pollInterval = null
  }
})
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-7xl mx-auto">
    <!-- Breadcrumb -->
    <nav class="text-sm text-slate-500 flex items-center gap-1">
      <NuxtLink to="/admin" class="hover:text-slate-700">
        {{ t('common.nav.admin') }}
      </NuxtLink>
      <span>/</span>
      <span class="text-slate-800">{{ t('admin.datasources.title') }}</span>
    </nav>

    <!-- Page header -->
    <div class="flex items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">
          {{ t('admin.datasources.title') }}
        </h1>
        <p class="text-slate-500 mt-1">
          {{ isPlatformAdmin ? t('admin.datasources.subtitle') : t('admin.datasources.subtitleCustomer') }}
        </p>
      </div>

      <div v-if="isPlatformAdmin" class="flex flex-col items-end gap-1">
        <Button
          :label="t('admin.datasources.refresh')"
          icon="pi pi-refresh"
          size="small"
          variant="outlined"
          :loading="healthStore.loading"
          @click="healthStore.fetchHealth()"
        />
        <span v-if="healthStore.lastUpdated" class="text-xs text-slate-400">
          {{ t('admin.datasources.lastUpdated') }}:
          {{ formatRelative(healthStore.lastUpdated) }}
        </span>
        <span class="text-xs text-slate-400">
          {{ t('admin.datasources.pollInterval') }}
        </span>
      </div>
    </div>

    <!-- Dashboard component (platform admins only — operator-level telemetry) -->
    <AdminDataSourceHealthDashboard
      v-if="isPlatformAdmin"
      :adapters="healthStore.adapters"
      :loading="healthStore.loading"
      :quarantining="quarantining"
      :can-quarantine="true"
      @quarantine="handleQuarantine"
    />

    <!-- NAV credential manager -->
    <AdminNavCredentialManager
      v-if="healthStore.adapters.length > 0"
      :data-source-mode="healthStore.adapters[0]?.dataSourceMode ?? ''"
    />
  </div>
</template>
