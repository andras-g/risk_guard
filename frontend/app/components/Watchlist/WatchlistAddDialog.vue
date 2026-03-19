<script setup lang="ts">
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import type { VerdictResponse } from '~/types/api'
import { useScreeningStore } from '~/stores/screening'

const { t } = useI18n()
const screeningStore = useScreeningStore()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [taxNumber: string, companyName: string | null, verdictStatus: string | null]
}>()

const taxNumber = ref('')
const error = ref<string | null>(null)
const isSearching = ref(false)
const searchResult = ref<VerdictResponse | null>(null)

const TAX_NUMBER_PATTERN = /^\d{8}(\d{3})?$/

function validate(): boolean {
  const cleaned = taxNumber.value.replace(/[\s-]/g, '')
  if (!TAX_NUMBER_PATTERN.test(cleaned)) {
    error.value = t('screening.search.invalidTaxNumber')
    return false
  }
  error.value = null
  return true
}

async function handleSearch() {
  if (!validate()) return

  isSearching.value = true
  searchResult.value = null
  error.value = null

  try {
    const cleaned = taxNumber.value.replace(/[\s-]/g, '')
    await screeningStore.search(cleaned)
    searchResult.value = screeningStore.currentVerdict
  }
  catch (err: unknown) {
    // Distinguish 404 (partner not found) from network/auth/server errors
    const status = (err as any)?.response?.status ?? (err as any)?.statusCode
    if (status === 404) {
      error.value = t('notification.watchlist.addDialog.notFound')
    }
    else {
      error.value = t('screening.verdict.searchFailed')
    }
  }
  finally {
    isSearching.value = false
  }
}

function handleConfirmAdd() {
  if (!searchResult.value) return
  emit('submit', searchResult.value.taxNumber, searchResult.value.companyName ?? null, searchResult.value.status ?? null)
  resetAndClose()
}

function resetAndClose() {
  taxNumber.value = ''
  error.value = null
  searchResult.value = null
  isSearching.value = false
  emit('update:visible', false)
}

function verdictSeverity(status: string): 'success' | 'danger' | 'warn' | 'secondary' {
  switch (status) {
    case 'RELIABLE': return 'success'
    case 'AT_RISK': return 'danger'
    case 'TAX_SUSPENDED': return 'warn'
    default: return 'secondary'
  }
}

function verdictLabel(status: string): string {
  switch (status) {
    case 'RELIABLE': return t('screening.verdict.reliable')
    case 'AT_RISK': return t('screening.verdict.atRisk')
    case 'TAX_SUSPENDED': return t('screening.verdict.taxSuspended')
    case 'INCOMPLETE': return t('screening.verdict.incomplete')
    case 'UNAVAILABLE': return t('screening.verdict.unavailable')
    default: return status
  }
}
</script>

<template>
  <Dialog
    :visible="props.visible"
    :header="t('notification.watchlist.addDialog.title')"
    modal
    :closable="true"
    :style="{ width: '30rem' }"
    data-testid="watchlist-add-dialog"
    @update:visible="emit('update:visible', $event)"
  >
    <div class="space-y-4">
      <!-- Tax number input + search -->
      <div>
        <label
          for="watchlist-tax-input"
          class="block text-sm font-medium text-slate-700 mb-1"
        >
          {{ t('screening.verdict.taxNumber') }}
        </label>
        <div class="flex gap-2">
          <InputText
            id="watchlist-tax-input"
            v-model="taxNumber"
            :placeholder="t('screening.search.placeholder')"
            :class="{ 'p-invalid': error }"
            :disabled="!!searchResult"
            class="flex-1 font-mono"
            data-testid="watchlist-tax-input"
            @keyup.enter="handleSearch"
          />
          <Button
            v-if="!searchResult"
            :label="t('screening.search.submit')"
            icon="pi pi-search"
            :loading="isSearching"
            :disabled="!taxNumber.trim() || isSearching"
            data-testid="watchlist-search-button"
            @click="handleSearch"
          />
          <Button
            v-else
            icon="pi pi-times"
            severity="secondary"
            text
            :aria-label="t('notification.watchlist.addDialog.changePartner')"
            data-testid="watchlist-clear-search"
            @click="searchResult = null; error = null"
          />
        </div>
        <small
          v-if="error"
          class="text-red-500 text-xs mt-1 block"
          data-testid="watchlist-tax-error"
        >
          {{ error }}
        </small>
      </div>

      <!-- Search result preview -->
      <div
        v-if="searchResult"
        class="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-2"
        data-testid="watchlist-search-result"
      >
        <div class="flex items-center justify-between">
          <span class="text-base font-semibold text-slate-800">
            {{ searchResult.companyName || searchResult.taxNumber }}
          </span>
          <Tag
            :value="verdictLabel(searchResult.status)"
            :severity="verdictSeverity(searchResult.status)"
          />
        </div>
        <div
          v-if="searchResult.companyName"
          class="text-sm text-slate-500 font-mono"
        >
          {{ searchResult.taxNumber }}
        </div>
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('notification.watchlist.addDialog.cancel')"
        severity="secondary"
        text
        data-testid="watchlist-add-cancel"
        @click="resetAndClose"
      />
      <Button
        :label="t('notification.watchlist.addDialog.submit')"
        icon="pi pi-plus"
        :disabled="!searchResult"
        data-testid="watchlist-add-submit"
        @click="handleConfirmAdd"
      />
    </template>
  </Dialog>
</template>
