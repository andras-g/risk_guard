<script setup lang="ts">
import Button from 'primevue/button'
import type { WatchlistEntryResponse } from '~/types/api'

const props = defineProps<{
  entries: WatchlistEntryResponse[]
  selectedEntries: WatchlistEntryResponse[]
}>()

const { t } = useI18n()
const toast = useToast()
const { isGenerating, generateAndDispatch } = useWatchlistPdfExport()

const exportTargets = computed(() =>
  props.selectedEntries.length > 0 ? props.selectedEntries : props.entries,
)

const isMobile = computed(() =>
  typeof window !== 'undefined' && (window.innerWidth < 768 || (typeof navigator !== 'undefined' && !!navigator.share)),
)

const buttonLabel = computed(() => {
  const label = isMobile.value
    ? t('notification.watchlist.export.shareLabel')
    : t('notification.watchlist.export.downloadLabel')
  return props.selectedEntries.length > 0
    ? `${label} (${props.selectedEntries.length})`
    : `${label} (${t('notification.watchlist.export.all')})`
})

async function handleExport() {
  try {
    await generateAndDispatch(exportTargets.value)
  }
  catch {
    toast.add({
      severity: 'error',
      summary: t('notification.watchlist.exportError'),
      life: 4000,
    })
  }
}
</script>

<template>
  <Button
    :label="buttonLabel"
    icon="pi pi-file-pdf"
    :loading="isGenerating"
    :disabled="isGenerating || entries.length === 0"
    data-testid="export-pdf-button"
    @click="handleExport"
  />
</template>
