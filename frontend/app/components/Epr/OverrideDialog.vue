<script setup lang="ts">
import type { KfCodeEntry } from '~/types/epr'
import { useEprWizardStore } from '~/stores/eprWizard'

const { t } = useI18n()
const wizardStore = useEprWizardStore()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

interface KfCodeGroup {
  label: string
  items: KfCodeEntry[]
}

const selectedEntry = ref<KfCodeEntry | null>(null)
const reason = ref('')
const filteredGroups = ref<KfCodeGroup[]>([])
const MAX_REASON_LENGTH = 500

// Lazy load KF-codes when dialog opens
watch(() => props.visible, async (isVisible) => {
  if (isVisible) {
    await wizardStore.fetchAllKfCodes()
  }
})

function formatKfCode(code: string): string {
  return code.replace(/(\d{2})(\d{2})(\d{2})(\d{2})/, '$1 $2 $3 $4')
}

/**
 * Groups a flat list of KfCodeEntry into product-stream groups for PrimeVue AutoComplete.
 */
function groupByProductStream(entries: KfCodeEntry[]): KfCodeGroup[] {
  const groups = new Map<string, KfCodeEntry[]>()
  for (const entry of entries) {
    const group = groups.get(entry.productStreamLabel) ?? []
    group.push(entry)
    groups.set(entry.productStreamLabel, group)
  }
  return Array.from(groups.entries()).map(([label, items]) => ({ label, items }))
}

function searchKfCodes(event: { query: string }) {
  const query = event.query.toLowerCase().replace(/\s/g, '')
  if (!wizardStore.allKfCodes) {
    filteredGroups.value = []
    return
  }
  const matched = wizardStore.allKfCodes.filter((entry) => {
    return entry.kfCode.includes(query)
      || entry.classification.toLowerCase().includes(query)
      || entry.feeCode.includes(query)
  })
  filteredGroups.value = groupByProductStream(matched)
}

function applyOverride() {
  if (!selectedEntry.value) return
  wizardStore.applyOverride(selectedEntry.value, reason.value || undefined)
  closeDialog()
}

function closeDialog() {
  selectedEntry.value = null
  reason.value = ''
  emit('update:visible', false)
}
</script>

<template>
  <Dialog
    :visible="visible"
    :header="t('epr.wizard.override.title')"
    modal
    closable
    data-testid="override-dialog"
    :style="{ width: '600px' }"
    :breakpoints="{ '768px': '100vw' }"
    @update:visible="closeDialog"
  >
    <div class="flex flex-col gap-4">
      <!-- AutoComplete for KF-code search -->
      <div>
        <label for="override-kf-code-search" class="block text-sm font-medium mb-1">
          {{ t('epr.wizard.override.searchPlaceholder') }}
        </label>
        <AutoComplete
          v-model="selectedEntry"
          input-id="override-kf-code-search"
          :suggestions="filteredGroups"
          option-group-label="label"
          option-group-children="items"
          option-label="classification"
          :placeholder="t('epr.wizard.override.searchPlaceholder')"
          force-selection
          dropdown
          data-testid="override-autocomplete"
          class="w-full"
          @complete="searchKfCodes"
        >
          <template #option="{ option }">
            <div class="flex flex-col gap-0.5 py-1" data-testid="override-option">
              <div class="flex items-center justify-between gap-2">
                <span class="font-mono text-sm font-semibold text-slate-800">{{ formatKfCode(option.kfCode) }}</span>
                <span class="text-sm font-semibold whitespace-nowrap">{{ option.feeRate }} Ft/kg</span>
              </div>
              <div class="text-sm text-slate-600 truncate">{{ option.classification }}</div>
            </div>
          </template>
          <template #optiongroup="{ option }">
            <div class="font-semibold text-sm text-surface-600 py-1">
              {{ option.label }}
            </div>
          </template>
        </AutoComplete>
      </div>

      <!-- Selected entry preview -->
      <div
        v-if="selectedEntry"
        class="bg-surface-50 border border-surface-200 rounded-lg p-3"
        data-testid="override-preview"
      >
        <div class="flex items-center justify-between">
          <span class="font-mono text-lg">{{ formatKfCode(selectedEntry.kfCode) }}</span>
          <span class="font-semibold">{{ selectedEntry.feeRate }} Ft/kg</span>
        </div>
        <div class="text-sm text-surface-600 mt-1">{{ selectedEntry.classification }}</div>
      </div>

      <!-- Reason textarea -->
      <div>
        <label for="override-reason-input" class="block text-sm font-medium mb-1">
          {{ t('epr.wizard.override.reasonLabel') }}
        </label>
        <Textarea
          id="override-reason-input"
          v-model="reason"
          :placeholder="t('epr.wizard.override.reasonPlaceholder')"
          :maxlength="MAX_REASON_LENGTH"
          rows="3"
          class="w-full"
          data-testid="override-reason"
        />
        <div class="text-xs text-surface-400 text-right mt-1">
          {{ reason.length }}/{{ MAX_REASON_LENGTH }}
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button
          :label="t('epr.wizard.override.cancel')"
          severity="secondary"
          outlined
          data-testid="override-cancel"
          @click="closeDialog"
        />
        <Button
          :label="t('epr.wizard.override.apply')"
          :disabled="!selectedEntry"
          data-testid="override-apply"
          @click="applyOverride"
        />
      </div>
    </template>
  </Dialog>
</template>
