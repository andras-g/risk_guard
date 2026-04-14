<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import SelectButton from 'primevue/selectbutton'
import DatePicker from 'primevue/datepicker'
import Badge from 'primevue/badge'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useBootstrap } from '~/composables/api/useBootstrap'
import { useBootstrapStore } from '~/stores/bootstrap'
import type { BootstrapCandidateResponse } from '~/composables/api/useBootstrap'
import BootstrapApproveDialog from '~/components/registry/BootstrapApproveDialog.vue'

definePageMeta({ requiresAuth: true })

const { t } = useI18n()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const toast = useToast()

const bootstrapStore = useBootstrapStore()
const { triggerBootstrap, listCandidates, rejectCandidate } = useBootstrap()

// ─── Date range ───────────────────────────────────────────────────────────────

const toDate = ref(new Date())
const fromDate = ref(new Date(new Date().setMonth(new Date().getMonth() - 12)))

function toIso(d: Date): string {
  return d.toISOString().slice(0, 10)
}

// ─── Fetch candidates ─────────────────────────────────────────────────────────

async function fetchCandidates() {
  bootstrapStore.setLoading(true)
  bootstrapStore.setError(null)
  try {
    const result = await listCandidates(null, 0, 200)
    bootstrapStore.setCandidates(result.items, result.total)
  }
  catch {
    bootstrapStore.setError(t('registry.bootstrap.errors.fetchFailed'))
  }
  finally {
    bootstrapStore.setLoading(false)
  }
}

// ─── Trigger bootstrap ────────────────────────────────────────────────────────

async function onTrigger() {
  bootstrapStore.setTriggerState('loading')
  try {
    const result = await triggerBootstrap(toIso(fromDate.value), toIso(toDate.value))
    toast.add({
      severity: 'success',
      summary: t('registry.bootstrap.triggerSuccess', { created: result.created, skipped: result.skipped }),
      life: 4000,
    })
    bootstrapStore.setTriggerState('done')
    await fetchCandidates()
  }
  catch {
    bootstrapStore.setTriggerState('error')
    toast.add({ severity: 'error', summary: t('registry.bootstrap.errors.triggerFailed'), life: 4000 })
  }
}

// ─── Status filter ────────────────────────────────────────────────────────────

const statusFilterValue = ref<string>('ALL')
const statusFilterOptions = computed(() => [
  { label: t('registry.bootstrap.filter.all'), value: 'ALL' },
  { label: t('registry.bootstrap.status.PENDING'), value: 'PENDING' },
  { label: t('registry.bootstrap.status.APPROVED'), value: 'APPROVED' },
  { label: t('registry.bootstrap.filter.rejected'), value: 'REJECTED' },
  { label: t('registry.bootstrap.status.NEEDS_MANUAL_ENTRY'), value: 'NEEDS_MANUAL_ENTRY' },
])

const filteredCandidates = computed(() => {
  const val = statusFilterValue.value
  if (val === 'ALL') return bootstrapStore.candidates
  if (val === 'REJECTED') {
    return bootstrapStore.candidates.filter(c => c.status === 'REJECTED_NOT_OWN_PACKAGING')
  }
  return bootstrapStore.candidates.filter(c => c.status === val)
})

// ─── Status tag severity ──────────────────────────────────────────────────────

function tagSeverity(status: BootstrapCandidateResponse['status']): string {
  switch (status) {
    case 'PENDING': return 'warn'
    case 'APPROVED': return 'success'
    case 'REJECTED_NOT_OWN_PACKAGING': return 'danger'
    case 'NEEDS_MANUAL_ENTRY': return 'secondary'
    default: return 'secondary'
  }
}

function statusLabel(status: BootstrapCandidateResponse['status']): string {
  return t(`registry.bootstrap.status.${status}`)
}

// ─── Approve dialog ───────────────────────────────────────────────────────────

const approveDialogVisible = ref(false)
const selectedCandidateForApprove = ref<BootstrapCandidateResponse | null>(null)

function openApproveDialog(candidate: BootstrapCandidateResponse) {
  selectedCandidateForApprove.value = candidate
  approveDialogVisible.value = true
}

function onApproved(updated: BootstrapCandidateResponse) {
  bootstrapStore.updateCandidate(updated)
  toast.add({ severity: 'success', summary: t('registry.bootstrap.approveSuccess'), life: 3000 })
}

// ─── Reject / mark manual ────────────────────────────────────────────────────

async function onReject(candidate: BootstrapCandidateResponse) {
  try {
    await rejectCandidate(candidate.id, 'NOT_OWN_PACKAGING')
    bootstrapStore.updateCandidate({ ...candidate, status: 'REJECTED_NOT_OWN_PACKAGING' })
    toast.add({ severity: 'info', summary: t('registry.bootstrap.rejectSuccess'), life: 3000 })
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
  }
}

async function onMarkManual(candidate: BootstrapCandidateResponse) {
  try {
    await rejectCandidate(candidate.id, 'NEEDS_MANUAL')
    bootstrapStore.updateCandidate({ ...candidate, status: 'NEEDS_MANUAL_ENTRY' })
    toast.add({ severity: 'info', summary: t('registry.bootstrap.markManualSuccess'), life: 3000 })
  }
  catch {
    toast.add({ severity: 'error', summary: t('common.states.error'), life: 3000 })
  }
}

// ─── DataTable selection ──────────────────────────────────────────────────────

const selectedRow = ref<BootstrapCandidateResponse | null>(null)

// ─── Keyboard shortcuts ───────────────────────────────────────────────────────

const tableWrapper = ref<HTMLElement | null>(null)

function onTableKeydown(event: KeyboardEvent) {
  const tag = (document.activeElement as HTMLElement)?.tagName?.toLowerCase()
  if (['input', 'select', 'textarea', 'button'].includes(tag ?? '')) return

  if (!selectedRow.value || selectedRow.value.status !== 'PENDING') return

  if (event.key === 'a') {
    openApproveDialog(selectedRow.value)
    event.preventDefault()
  }
  else if (event.key === 'r') {
    onReject(selectedRow.value)
    event.preventDefault()
  }
  else if (event.key === 'm') {
    onMarkManual(selectedRow.value)
    event.preventDefault()
  }
}

onMounted(() => fetchCandidates())
</script>

<template>
  <!-- Tier gate -->
  <div
    v-if="!hasAccess"
    class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto"
    data-testid="bootstrap-tier-gate"
  >
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('registry.bootstrap.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <div v-else class="p-4 flex flex-col gap-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ t('registry.bootstrap.title') }}</h1>
    </div>

    <!-- Date range + trigger -->
    <div class="flex flex-wrap gap-3 items-end">
      <div class="flex flex-col gap-1">
        <label for="bootstrap-date-from" class="text-sm font-medium">{{ t('registry.bootstrap.dateFrom') }}</label>
        <DatePicker v-model="fromDate" input-id="bootstrap-date-from" date-format="yy-mm-dd" show-icon />
      </div>
      <div class="flex flex-col gap-1">
        <label for="bootstrap-date-to" class="text-sm font-medium">{{ t('registry.bootstrap.dateTo') }}</label>
        <DatePicker v-model="toDate" input-id="bootstrap-date-to" date-format="yy-mm-dd" show-icon />
      </div>
      <Button
        :label="bootstrapStore.triggerState === 'done'
          ? t('registry.bootstrap.reFetch')
          : t('registry.bootstrap.fetchInvoices')"
        :loading="bootstrapStore.triggerState === 'loading'"
        icon="pi pi-download"
        @click="onTrigger"
      />
    </div>

    <!-- Status filter -->
    <SelectButton
      v-model="statusFilterValue"
      :options="statusFilterOptions"
      option-label="label"
      option-value="value"
      :allow-empty="false"
      data-testid="status-filter"
    />

    <!-- Empty state -->
    <div
      v-if="!bootstrapStore.isLoading && filteredCandidates.length === 0"
      class="flex flex-col items-center justify-center py-12 gap-3 text-center"
      data-testid="empty-state"
      aria-live="polite"
    >
      <i class="pi pi-inbox text-5xl text-gray-400" aria-hidden="true" />
      <p class="text-gray-500">{{ t('registry.bootstrap.empty') }}</p>
    </div>

    <!-- Triage table -->
    <!-- eslint-disable-next-line vuejs-accessibility/no-static-element-interactions -- keyboard-shortcut region wrapping a DataTable (a/r/m keys); role="region" landmark is the correct semantic -->
    <div
      v-else
      ref="tableWrapper"
      tabindex="0"
      role="region"
      class="bootstrap-table-wrapper outline-none"
      :aria-label="t('registry.bootstrap.tableAriaLabel')"
      @keydown.stop="onTableKeydown"
    >
      <!-- Keyboard hint -->
      <p class="text-xs text-gray-400 mb-2">{{ t('registry.bootstrap.keyboardHint') }}</p>

      <DataTable
        v-model:selection="selectedRow"
        :value="filteredCandidates"
        :loading="bootstrapStore.isLoading"
        selection-mode="single"
        data-key="id"
        data-testid="candidates-table"
      >
        <Column :header="t('registry.bootstrap.columns.productName')" field="productName" />
        <Column :header="t('registry.bootstrap.columns.vtsz')" field="vtsz" />
        <Column :header="t('registry.bootstrap.columns.frequency')">
          <template #body="{ data }">
            <Badge :value="data.frequency" />
          </template>
        </Column>
        <Column :header="t('registry.bootstrap.columns.quantity')">
          <template #body="{ data }">
            {{ data.totalQuantity }} {{ data.unitOfMeasure }}
          </template>
        </Column>
        <Column :header="t('registry.bootstrap.columns.suggestedKfCode')">
          <template #body="{ data }">
            <span :class="data.suggestedKfCode ? '' : 'text-gray-400 italic'">
              {{ data.suggestedKfCode || t('registry.bootstrap.noSuggestion') }}
            </span>
          </template>
        </Column>
        <Column :header="t('registry.bootstrap.columns.status')">
          <template #body="{ data }">
            <Tag :severity="tagSeverity(data.status)" :value="statusLabel(data.status)" />
          </template>
        </Column>
        <Column :header="t('registry.bootstrap.columns.actions')">
          <template #body="{ data }">
            <div v-if="data.status === 'PENDING'" class="flex gap-2">
              <Button
                :label="t('registry.bootstrap.actions.approve')"
                size="small"
                data-testid="approve-btn"
                @click="openApproveDialog(data)"
              />
              <Button
                icon="pi pi-ban"
                :aria-label="t('registry.bootstrap.actions.reject')"
                size="small"
                text
                severity="danger"
                data-testid="reject-btn"
                @click="onReject(data)"
              />
              <Button
                icon="pi pi-pencil"
                :aria-label="t('registry.bootstrap.actions.markManual')"
                size="small"
                text
                severity="secondary"
                data-testid="mark-manual-btn"
                @click="onMarkManual(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Approve dialog -->
    <BootstrapApproveDialog
      :candidate="selectedCandidateForApprove"
      :visible="approveDialogVisible"
      @close="approveDialogVisible = false"
      @approved="onApproved"
    />
  </div>
</template>
