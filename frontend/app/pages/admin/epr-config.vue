<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import Button from 'primevue/button'
import Panel from 'primevue/panel'
import { useToast } from 'primevue/usetoast'
import { useIdentityStore } from '~/stores/identity'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const identityStore = useIdentityStore()
const config = useRuntimeConfig()

const configData = ref('')
const version = ref(0)
const activatedAt = ref('')
const validationResult = ref<{ valid: boolean; errors: string[] } | null>(null)
const validating = ref(false)
const publishing = ref(false)

watch(configData, () => { validationResult.value = null })

onMounted(async () => {
  if (identityStore.user?.role !== 'SME_ADMIN') {
    router.replace('/')
    return
  }
  await loadConfig()
})

async function loadConfig() {
  try {
    const data = await $fetch<{ version: number; configData: string; activatedAt: string }>(
      '/api/v1/admin/epr/config',
      { baseURL: config.public.apiBase as string, credentials: 'include' }
    )
    version.value = data.version
    configData.value = data.configData
    activatedAt.value = data.activatedAt
  } catch {
    toast.add({ severity: 'error', summary: t('admin.eprConfig.errors.loadFailed'), life: 4000 })
  }
}

async function handleValidate() {
  validating.value = true
  validationResult.value = null
  try {
    const result = await $fetch<{ valid: boolean; errors: string[] }>(
      '/api/v1/admin/epr/config/validate',
      {
        method: 'POST',
        baseURL: config.public.apiBase as string,
        credentials: 'include',
        body: { configData: configData.value },
      }
    )
    validationResult.value = result
  } catch {
    toast.add({ severity: 'error', summary: t('admin.eprConfig.errors.validateFailed'), life: 4000 })
  } finally {
    validating.value = false
  }
}

async function handlePublish() {
  publishing.value = true
  try {
    const result = await $fetch<{ version: number; activatedAt: string }>(
      '/api/v1/admin/epr/config/publish',
      {
        method: 'POST',
        baseURL: config.public.apiBase as string,
        credentials: 'include',
        body: { configData: configData.value },
      }
    )
    version.value = result.version
    activatedAt.value = result.activatedAt
    validationResult.value = null
    toast.add({
      severity: 'success',
      summary: t('admin.eprConfig.publishSuccess', { version: result.version }),
      life: 4000,
    })
  } catch {
    toast.add({ severity: 'error', summary: t('admin.eprConfig.errors.publishFailed'), life: 4000 })
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6 p-6 max-w-7xl mx-auto">
    <!-- Breadcrumb -->
    <nav class="text-sm text-slate-500 flex items-center gap-1">
      <NuxtLink to="/admin" class="hover:text-slate-700">
        {{ t('common.nav.admin') }}
      </NuxtLink>
      <span>/</span>
      <span class="text-slate-800">{{ t('admin.eprConfig.title') }}</span>
    </nav>

    <!-- Page header -->
    <div class="flex items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">
          {{ t('admin.eprConfig.title') }}
        </h1>
        <p class="text-slate-500 mt-1">{{ t('admin.eprConfig.subtitle') }}</p>
      </div>

      <div class="flex flex-col items-end gap-1 shrink-0">
        <span v-if="version > 0" class="text-sm font-semibold text-indigo-700">
          {{ t('admin.eprConfig.currentVersion', { version }) }}
        </span>
        <span v-if="activatedAt" class="text-xs text-slate-400">
          {{ t('admin.eprConfig.activatedAt', { date: activatedAt }) }}
        </span>
      </div>
    </div>

    <!-- Monaco editor -->
    <MonacoEditor v-model="configData" :readonly="false" />

    <!-- Action buttons -->
    <div class="flex gap-3">
      <Button
        :label="t('admin.eprConfig.validate')"
        icon="pi pi-check-circle"
        variant="outlined"
        :loading="validating"
        :disabled="!configData"
        data-testid="validate-btn"
        @click="handleValidate"
      />
      <Button
        :label="t('admin.eprConfig.publish')"
        icon="pi pi-upload"
        :loading="publishing"
        :disabled="validationResult?.valid !== true"
        data-testid="publish-btn"
        @click="handlePublish"
      />
    </div>

    <!-- Validation result -->
    <Panel
      v-if="validationResult !== null"
      :header="validationResult.valid
        ? t('admin.eprConfig.validationPassed', { count: 5 })
        : t('admin.eprConfig.validationFailed', { count: validationResult.errors.length })"
      :pt="{ header: { class: validationResult.valid ? 'text-green-700 bg-green-50' : 'text-red-700 bg-red-50' } }"
      data-testid="validation-panel"
    >
      <ul v-if="!validationResult.valid" class="list-disc pl-4 space-y-1">
        <li
          v-for="(error, idx) in validationResult.errors"
          :key="idx"
          class="text-red-700 text-sm"
        >
          {{ error }}
        </li>
      </ul>
    </Panel>
  </div>
</template>
