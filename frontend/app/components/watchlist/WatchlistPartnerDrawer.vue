<script setup lang="ts">
import type { WatchlistEntryResponse } from '~/types/api'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import { useDateShort } from '~/composables/formatting/useDateShort'

const props = defineProps<{
  entry: WatchlistEntryResponse | null
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  remove: []
  hide: []
}>()

const { t } = useI18n()
const { formatRelative } = useDateRelative()
const { formatShort } = useDateShort()

const drawerVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// PrimeVue Tag severity per status — same map as DashboardNeedsAttention
const STATUS_SEVERITY: Record<string, string> = {
  AT_RISK: 'danger',
  TAX_SUSPENDED: 'danger',
  INCOMPLETE: 'warn',
  UNAVAILABLE: 'secondary',
  RELIABLE: 'success',
}

function verdictSeverity(status: string | null): string {
  return STATUS_SEVERITY[status ?? ''] ?? 'secondary'
}

function verdictLabel(status: string | null): string {
  if (!status) return '—'
  switch (status) {
    case 'RELIABLE': return t('screening.verdict.reliable')
    case 'AT_RISK': return t('screening.verdict.atRisk')
    case 'TAX_SUSPENDED': return t('screening.verdict.taxSuspended')
    case 'INCOMPLETE': return t('screening.verdict.incomplete')
    case 'UNAVAILABLE': return t('screening.verdict.unavailable')
    default: return status
  }
}

function watchlistSince(createdAt: string): string {
  return formatShort(createdAt)
}

function onDrawerHide() {
  emit('hide')
}

function onRemove() {
  emit('remove')
}

function goToScreening() {
  if (!props.entry) return
  navigateTo(`/screening/${props.entry.taxNumber}`)
  emit('update:visible', false)
}
</script>

<template>
  <Drawer
    v-model:visible="drawerVisible"
    position="right"
    style="width: 480px"
    data-testid="partner-drawer"
    @hide="onDrawerHide()"
  >
    <template #header>
      <span class="text-lg font-bold text-slate-800">{{ t('notification.watchlist.drawerTitle') }}</span>
    </template>

    <div
      v-if="entry"
      class="flex flex-col gap-6 p-2"
    >
      <!-- Identity Section -->
      <div class="flex flex-col gap-3">
        <h2 class="text-xl font-bold text-slate-800">
          {{ entry.companyName || '—' }}
        </h2>
        <span class="font-mono text-slate-600 text-sm">{{ entry.taxNumber }}</span>
        <Tag
          :severity="verdictSeverity(entry.currentVerdictStatus)"
          :value="verdictLabel(entry.currentVerdictStatus)"
          data-testid="drawer-verdict-badge"
        />
        <div class="flex flex-col gap-1 text-sm text-slate-500">
          <span data-testid="drawer-watchlist-since">
            {{ t('notification.watchlist.watchlistSince') }}: {{ watchlistSince(entry.createdAt) }}
          </span>
          <span data-testid="drawer-last-screened">
            {{ t('notification.watchlist.lastScreened') }}:
            {{ entry.lastCheckedAt ? formatRelative(entry.lastCheckedAt) : t('notification.watchlist.neverScreened') }}
          </span>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col gap-3">
        <Button
          :label="t('notification.watchlist.viewScreening')"
          icon="pi pi-arrow-right"
          icon-pos="right"
          class="w-full"
          data-testid="drawer-view-screening"
          @click="goToScreening()"
        />

        <AuditDispatcher
          :entries="[entry]"
          :selected-entries="[entry]"
          data-testid="drawer-export-pdf"
        />

        <NuxtLink :to="`/audit-history?taxNumber=${entry.taxNumber}`">
          <Button
            :label="t('notification.watchlist.viewAuditHistory')"
            icon="pi pi-history"
            outlined
            class="w-full"
            data-testid="drawer-view-audit-history"
          />
        </NuxtLink>

        <Button
          :label="t('notification.watchlist.removeButton')"
          text
          severity="danger"
          class="w-full"
          data-testid="drawer-remove-btn"
          @click="onRemove()"
        />
      </div>
    </div>
  </Drawer>
</template>
