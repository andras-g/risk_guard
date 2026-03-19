<script setup lang="ts">
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import type { WatchlistEntryResponse } from '~/types/api'
import { useWatchlistStore } from '~/stores/watchlist'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const watchlistStore = useWatchlistStore()

const showAddDialog = ref(false)

// Fetch entries on mount
onMounted(async () => {
  await watchlistStore.fetchEntries()
})

async function handleAddPartner(taxNumber: string, companyName: string | null, verdictStatus: string | null) {
  try {
    const result = await watchlistStore.addEntry(taxNumber, companyName, verdictStatus)
    if (result.duplicate) {
      toast.add({
        severity: 'warn',
        summary: t('notification.watchlist.duplicateToast'),
        life: 3000,
      })
    }
    else {
      toast.add({
        severity: 'success',
        summary: t('notification.watchlist.addedToast'),
        life: 3000,
      })
    }
  }
  catch {
    toast.add({
      severity: 'error',
      summary: t('common.states.error'),
      life: 3000,
    })
  }
}

function handleRemove(entry: WatchlistEntryResponse) {
  confirm.require({
    message: t('notification.watchlist.confirmRemove', { companyName: entry.companyName || entry.taxNumber }),
    header: t('notification.watchlist.removeButton'),
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: t('notification.watchlist.confirmYes'),
    rejectLabel: t('notification.watchlist.confirmNo'),
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await watchlistStore.removeEntry(entry.id)
        toast.add({
          severity: 'success',
          summary: t('notification.watchlist.removedToast'),
          life: 3000,
        })
      }
      catch {
        toast.add({
          severity: 'error',
          summary: t('common.states.error'),
          life: 3000,
        })
      }
    },
  })
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-6">
    <!-- Page Header -->
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-800">
        {{ t('notification.watchlist.title') }}
      </h1>
      <Button
        :label="t('notification.watchlist.addButton')"
        icon="pi pi-plus"
        data-testid="add-partner-button"
        @click="showAddDialog = true"
      />
    </div>

    <!-- Watchlist Table -->
    <WatchlistTable
      :entries="watchlistStore.entries"
      :is-loading="watchlistStore.isLoading"
      @remove="handleRemove"
    />

    <!-- Add Dialog -->
    <WatchlistAddDialog
      v-model:visible="showAddDialog"
      @submit="handleAddPartner"
    />

    <!-- Confirm Dialog (required by useConfirm) -->
    <ConfirmDialog />
  </div>
</template>
