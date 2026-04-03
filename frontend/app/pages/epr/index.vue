<script setup lang="ts">
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useApiError } from '~/composables/api/useApiError'
import { useAuthStore } from '~/stores/auth'
import { useEprStore } from '~/stores/epr'
import { useEprWizardStore } from '~/stores/eprWizard'
import type { MaterialTemplateResponse } from '~/types/epr'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const { mapErrorType } = useApiError()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const authStore = useAuthStore()
const eprStore = useEprStore()
const wizardStore = useEprWizardStore()

// Accountant with home tenant active = no client selected yet
const needsClientSelection = computed(() =>
  authStore.isAccountant && authStore.activeTenantId === authStore.homeTenantId,
)

const showFormDialog = ref(false)
const showCopyDialog = ref(false)
const showOverrideDialog = ref(false)
const editingTemplate = ref<MaterialTemplateResponse | null>(null)

// Fetch materials on mount (only if tier allows and a client is selected)
onMounted(async () => {
  if (needsClientSelection.value) return
  if (hasAccess.value) {
    try {
      await eprStore.fetchMaterials()
    }
    catch {
      toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
    }
  }
})

function openAddDialog() {
  editingTemplate.value = null
  showFormDialog.value = true
}

function openEditDialog(entry: MaterialTemplateResponse) {
  editingTemplate.value = entry
  showFormDialog.value = true
}

async function handleFormSubmit(data: { name: string; baseWeightGrams: number; recurring: boolean }) {
  try {
    if (editingTemplate.value) {
      await eprStore.updateMaterial(editingTemplate.value.id, data)
      toast.add({ severity: 'success', summary: t('epr.materialLibrary.toast.updated'), life: 3000 })
    }
    else {
      await eprStore.addMaterial(data)
      toast.add({ severity: 'success', summary: t('epr.materialLibrary.toast.created'), life: 3000 })
    }
  }
  catch (error: unknown) {
    const msg = error && typeof error === 'object' && 'data' in error
      ? mapErrorType((error as any).data?.type)
      : t('common.states.error')
    toast.add({ severity: 'error', summary: msg, life: 3000 })
  }
}

function handleDelete(entry: MaterialTemplateResponse) {
  confirm.require({
    message: t('epr.materialLibrary.confirmDelete', { name: entry.name }),
    header: t('epr.materialLibrary.deleteButton'),
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: t('common.actions.delete'),
    rejectLabel: t('common.actions.cancel'),
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await eprStore.deleteMaterial(entry.id)
        toast.add({ severity: 'success', summary: t('epr.materialLibrary.toast.deleted'), life: 3000 })
      }
      catch {
        toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
      }
    },
  })
}

async function handleClassify(entry: MaterialTemplateResponse) {
  try {
    await wizardStore.startWizard(entry.id)
  }
  catch {
    toast.add({ severity: 'error', summary: t('epr.wizard.errorToast'), life: 3000 })
  }
}

// Watch for wizard closing — differentiate success toast from unlinked close (AC 1, AC 2)
// The store sets lastConfirmSuccess or lastCloseReason before clearing activeStep,
// so we can read them synchronously here to determine which toast to show.
watch(() => wizardStore.isActive, (isActive, wasActive) => {
  if (!isActive && wasActive) {
    if (wizardStore.lastConfirmSuccess) {
      toast.add({ severity: 'success', summary: t('epr.wizard.successToast'), life: 3000 })
      wizardStore.lastConfirmSuccess = false
    }
    else if (wizardStore.lastCloseReason === 'unlinked') {
      toast.add({ severity: 'info', summary: t('epr.wizard.closeUnlinkedToast'), life: 5000 })
      wizardStore.lastCloseReason = null
    }
  }
})

async function handleToggleRecurring(entry: MaterialTemplateResponse, recurring: boolean) {
  try {
    await eprStore.toggleRecurring(entry.id, { recurring })
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
  }
}

async function handleCopyFromQuarter(data: { sourceYear: number; sourceQuarter: number; includeNonRecurring: boolean }) {
  try {
    const copied = await eprStore.copyFromQuarter(data)
    if (copied.length === 0) {
      toast.add({
        severity: 'info',
        summary: t('epr.materialLibrary.toast.copiedEmpty', {
          quarter: data.sourceQuarter,
          year: data.sourceYear,
        }),
        life: 5000,
      })
    }
    else {
      toast.add({
        severity: 'success',
        summary: t('epr.materialLibrary.toast.copied', { count: copied.length }),
        life: 3000,
      })
    }
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
  }
}
</script>

<template>
  <!-- Accountant with no client selected -->
  <div
    v-if="needsClientSelection"
    class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto"
    data-testid="epr-select-customer"
  >
    <i class="pi pi-info-circle text-6xl text-indigo-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('epr.selectCustomer.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.selectCustomer.description') }}
    </p>
  </div>

  <!-- Tier Gate: Upgrade Prompt -->
  <div v-else-if="!hasAccess" class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto">
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('epr.materialLibrary.tierGate.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <!-- Material Library -->
  <div v-else class="w-full">
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- Main Content -->
      <div class="flex-1 min-w-0">
        <!-- Page Header -->
        <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
          <h1 class="text-2xl font-bold text-slate-800">
            {{ t('epr.materialLibrary.title') }}
          </h1>
          <div class="flex gap-2">
            <Button
              :label="t('epr.filing.newFilingButton')"
              icon="pi pi-calculator"
              severity="secondary"
              :disabled="eprStore.verifiedCount === 0"
              data-testid="new-filing-button"
              @click="$router.push('/epr/filing')"
            />
            <Button
              :label="t('epr.materialLibrary.copyButton')"
              icon="pi pi-copy"
              severity="secondary"
              outlined
              data-testid="copy-quarter-button"
              @click="showCopyDialog = true"
            />
            <Button
              :label="t('epr.materialLibrary.addButton')"
              icon="pi pi-plus"
              data-testid="add-material-button"
              @click="openAddDialog"
            />
          </div>
        </div>

        <!-- Mobile card layout — hidden when wizard is active (wizard replaces it on mobile per AC 5 / UX Spec §8.1) -->
        <div v-if="!wizardStore.isActive" class="block md:hidden">
          <div
            v-if="!eprStore.isLoading && eprStore.materials.length === 0"
            class="flex flex-col items-center justify-center py-12 text-center"
          >
            <i class="pi pi-box text-5xl text-slate-300 mb-3" aria-hidden="true" />
            <p class="text-slate-500">{{ t('epr.materialLibrary.empty') }}</p>
          </div>
          <div
            v-for="material in eprStore.materials"
            v-else
            :key="material.id"
            class="bg-white border border-slate-200 rounded-lg p-4 mb-3"
          >
            <div class="flex justify-between items-start mb-2">
              <span class="font-medium text-slate-800">{{ material.name }}</span>
              <div class="flex items-center gap-1.5">
                <span
                  :class="[
                    'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
                    material.verified ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                  ]"
                >
                  <i v-if="material.verified" class="pi pi-check-circle text-xs" />
                  {{ material.verified ? t('epr.materialLibrary.filingReady') : t('epr.materialLibrary.unverified') }}
                </span>
                <EprConfidenceBadge
                  v-if="material.verified && material.confidence"
                  :confidence="material.confidence"
                />
                <span
                  v-if="!material.recurring"
                  class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800"
                >
                  {{ t('epr.materialLibrary.oneTimeBadge') }}
                </span>
              </div>
            </div>
            <div class="text-sm text-slate-500 space-y-1">
              <div>{{ t('epr.materialLibrary.columns.baseWeight') }}: {{ material.baseWeightGrams }} g</div>
              <div>{{ t('epr.materialLibrary.columns.kfCode') }}: {{ material.kfCode || '—' }}</div>
            </div>
            <div class="flex gap-2 mt-3">
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                text
                size="small"
                @click="openEditDialog(material)"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                size="small"
                @click="handleDelete(material)"
              />
              <Button
                :icon="material.kfCode ? 'pi pi-refresh' : 'pi pi-tag'"
                :label="material.kfCode ? t('epr.wizard.reclassify') : t('epr.wizard.classify')"
                severity="secondary"
                text
                size="small"
                :data-testid="`classify-button-mobile-${material.id}`"
                @click="handleClassify(material)"
              />
            </div>
          </div>
        </div>

        <!-- Mobile wizard — replaces card list when active (AC 5, UX Spec §8.1) -->
        <div v-else class="block md:hidden mt-0" data-testid="epr-wizard-mobile">
          <EprWizardStepper @open-override="showOverrideDialog = true" />
        </div>

        <!-- Desktop DataTable -->
        <div class="hidden md:block">
          <EprMaterialInventoryBlock
            :entries="eprStore.materials"
            :is-loading="eprStore.isLoading"
            @edit="openEditDialog"
            @delete="handleDelete"
            @toggle-recurring="handleToggleRecurring"
            @classify="handleClassify"
          />
        </div>

        <!-- Wizard below DataTable on desktop (>1024px per UX Spec §8.2) -->
        <div
          v-if="wizardStore.isActive"
          class="hidden md:block mt-6"
          data-testid="epr-wizard-container"
        >
          <EprWizardStepper @open-override="showOverrideDialog = true" />
        </div>
      </div>

      <!-- Side Panel (desktop only) -->
      <EprSidePanel
        :total-count="eprStore.totalCount"
        :filing-ready-count="eprStore.verifiedCount"
        :one-time-count="eprStore.oneTimeCount"
      />
    </div>

    <!-- Dialogs -->
    <EprMaterialFormDialog
      :visible="showFormDialog"
      :edit-template="editingTemplate"
      @update:visible="showFormDialog = $event"
      @submit="handleFormSubmit"
    />

    <EprCopyQuarterDialog
      :visible="showCopyDialog"
      @update:visible="showCopyDialog = $event"
      @submit="handleCopyFromQuarter"
    />

    <EprOverrideDialog
      :visible="showOverrideDialog"
      @update:visible="showOverrideDialog = $event"
    />

    <ConfirmDialog />
  </div>
</template>
