<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useHealthStore } from '~/stores/health'

const props = defineProps<{
  dataSourceMode?: string
}>()

const { t } = useI18n()
const toast = useToast()
const config = useRuntimeConfig()
const healthStore = useHealthStore()
const credentialUrl = `${config.public.apiBase}/api/v1/admin/datasources/credentials`

const navAdapter = computed(() =>
  healthStore.adapters.find(a => a.adapterName === 'nav-online-szamla')
)
const credentialStatus = computed(() => navAdapter.value?.credentialStatus ?? 'NOT_CONFIGURED')

const form = reactive({
  login: '',
  password: '',
  signingKey: '',
  exchangeKey: '',
  taxNumber: '',
})

const saving = ref(false)
const deleting = ref(false)

const canSave = computed(() =>
  form.login.trim() !== '' &&
  form.password.trim() !== '' &&
  form.signingKey.trim() !== '' &&
  form.exchangeKey.trim() !== '' &&
  form.taxNumber.trim() !== ''
)

function resetForm() {
  form.login = ''
  form.password = ''
  form.signingKey = ''
  form.exchangeKey = ''
  form.taxNumber = ''
}

async function saveCredentials() {
  saving.value = true
  try {
    const res = await fetch(credentialUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(form),
    })
    if (res.ok) {
      toast.add({ severity: 'success', summary: t('admin.navCredentials.saveSuccess'), life: 3000 })
      resetForm()
      await healthStore.fetchHealth()
    } else {
      const text = await res.text()
      toast.add({ severity: 'error', summary: t('admin.navCredentials.saveError'), detail: text, life: 6000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: t('admin.navCredentials.saveError'), life: 4000 })
  } finally {
    saving.value = false
  }
}

async function deleteCredentials() {
  deleting.value = true
  try {
    const res = await fetch(credentialUrl, {
      method: 'DELETE',
      credentials: 'include',
    })
    if (res.ok) {
      toast.add({ severity: 'success', summary: t('admin.navCredentials.deleteSuccess'), life: 3000 })
      await healthStore.fetchHealth()
    } else {
      toast.add({ severity: 'error', summary: t('admin.navCredentials.deleteError'), life: 4000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: t('admin.navCredentials.deleteError'), life: 4000 })
  } finally {
    deleting.value = false
  }
}

function credentialStatusClass(status: string): string {
  switch (status) {
    case 'VALID': return 'bg-emerald-100 text-emerald-800'
    case 'EXPIRED': return 'bg-amber-100 text-amber-800'
    case 'INVALID': return 'bg-red-100 text-red-800'
    default: return 'bg-slate-100 text-slate-600'
  }
}
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white p-6 flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h2 class="text-lg font-semibold text-slate-800">
        {{ t('admin.navCredentials.title') }}
      </h2>
      <span
        class="px-2 py-0.5 rounded-full text-xs font-medium"
        :class="credentialStatusClass(credentialStatus)"
        data-testid="credential-status-badge"
      >
        {{ t('admin.navCredentials.status.' + credentialStatus.toLowerCase()) }}
      </span>
    </div>

    <p class="text-sm text-slate-500">{{ t('admin.navCredentials.description') }}</p>

    <Message v-if="props.dataSourceMode === 'DEMO'" severity="info" :closable="false" data-testid="demo-mode-info">
      {{ t('admin.navCredentials.demoModeInfo') }}
    </Message>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="flex flex-col gap-1">
        <label for="nav-login" class="text-sm font-medium text-slate-700">{{ t('admin.navCredentials.login') }}</label>
        <input
          id="nav-login"
          v-model="form.login"
          type="password"
          autocomplete="off"
          class="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="input-login"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="nav-password" class="text-sm font-medium text-slate-700">{{ t('admin.navCredentials.password') }}</label>
        <input
          id="nav-password"
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          class="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="input-password"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="nav-signing-key" class="text-sm font-medium text-slate-700">{{ t('admin.navCredentials.signingKey') }}</label>
        <input
          id="nav-signing-key"
          v-model="form.signingKey"
          type="password"
          autocomplete="off"
          class="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="input-signing-key"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="nav-exchange-key" class="text-sm font-medium text-slate-700">{{ t('admin.navCredentials.exchangeKey') }}</label>
        <input
          id="nav-exchange-key"
          v-model="form.exchangeKey"
          type="password"
          autocomplete="off"
          class="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="input-exchange-key"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="nav-tax-number" class="text-sm font-medium text-slate-700">{{ t('admin.navCredentials.taxNumber') }}</label>
        <input
          id="nav-tax-number"
          v-model="form.taxNumber"
          type="text"
          autocomplete="off"
          class="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          data-testid="input-tax-number"
        />
      </div>
    </div>

    <div class="flex items-center gap-3 pt-1">
      <Button
        :label="t('admin.navCredentials.saveButton')"
        icon="pi pi-check"
        :loading="saving"
        :disabled="!canSave"
        data-testid="btn-save"
        @click="saveCredentials"
      />
      <Button
        v-if="credentialStatus !== 'NOT_CONFIGURED'"
        :label="t('admin.navCredentials.deleteButton')"
        icon="pi pi-trash"
        severity="danger"
        variant="outlined"
        :loading="deleting"
        data-testid="btn-delete"
        @click="deleteCredentials"
      />
    </div>
  </div>
</template>
