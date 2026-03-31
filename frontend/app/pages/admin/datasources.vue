<script setup lang="ts">
import Button from 'primevue/button'
import { useHealthStore } from '~/stores/health'
import { useIdentityStore } from '~/stores/identity'
import { useDateRelative } from '~/composables/formatting/useDateRelative'

const { t } = useI18n()
const { formatRelative } = useDateRelative()
const router = useRouter()
const healthStore = useHealthStore()
const identityStore = useIdentityStore()

const quarantining = computed(() => healthStore.quarantining)

async function handleQuarantine(adapterName: string, quarantined: boolean) {
  await healthStore.quarantineAdapter(adapterName, quarantined)
}

// 30-second polling handle
let pollInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  if (identityStore.user?.role !== 'SME_ADMIN') {
    router.replace('/dashboard')
    return
  }
  healthStore.fetchHealth()
  if (pollInterval === null) {
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
        <p class="text-slate-500 mt-1">{{ t('admin.datasources.subtitle') }}</p>
      </div>

      <div class="flex flex-col items-end gap-1">
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

    <!-- Dashboard component -->
    <DataSourceHealthDashboard
      :adapters="healthStore.adapters"
      :loading="healthStore.loading"
      :quarantining="quarantining"
      @quarantine="handleQuarantine"
    />
  </div>
</template>
