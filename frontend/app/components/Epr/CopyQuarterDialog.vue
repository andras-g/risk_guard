<script setup lang="ts">
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'submit': [data: { sourceYear: number; sourceQuarter: number; includeNonRecurring: boolean }]
}>()

const includeNonRecurring = ref(false)
const selectedQuarter = ref<string | null>(null)

// Current quarter calculation — reactive to handle quarter boundary crossings
// (e.g., user opens dialog on Mar 31, leaves tab open past midnight into Apr 1)
const currentYear = computed(() => new Date().getFullYear())
const currentQuarter = computed(() => Math.ceil((new Date().getMonth() + 1) / 3))

const currentQuarterLabel = computed(() => `${currentYear.value} Q${currentQuarter.value}`)

// Generate available source quarters (last 8 quarters excluding current)
const quarterOptions = computed(() => {
  const options: { label: string; value: string }[] = []
  let y = currentYear.value
  let q = currentQuarter.value

  for (let i = 0; i < 8; i++) {
    q--
    if (q === 0) {
      q = 4
      y--
    }
    options.push({
      label: `${y} Q${q}`,
      value: `${y}-${q}`,
    })
  }
  return options
})

// Reset on open — immediate so the first option is pre-selected when mounted with visible=true
watch(() => props.visible, (newVal) => {
  if (newVal) {
    selectedQuarter.value = quarterOptions.value.length > 0 ? quarterOptions.value[0].value : null
    includeNonRecurring.value = false
  }
}, { immediate: true })

function handleCopy() {
  if (!selectedQuarter.value) return

  const [yearStr, quarterStr] = selectedQuarter.value.split('-')
  emit('submit', {
    sourceYear: parseInt(yearStr),
    sourceQuarter: parseInt(quarterStr),
    includeNonRecurring: includeNonRecurring.value,
  })
  emit('update:visible', false)
}
</script>

<template>
  <Dialog
    :visible="visible"
    :header="t('epr.materialLibrary.copyDialog.title')"
    modal
    class="w-full max-w-[480px]"
    :pt="{ header: { style: 'user-select: none; cursor: default' } }"
    data-testid="copy-quarter-dialog"
    @update:visible="emit('update:visible', $event)"
  >
    <div class="flex flex-col gap-5 p-2">
      <p class="text-sm text-slate-600">
        {{ t('epr.materialLibrary.copyDialog.description', { quarter: currentQuarterLabel }) }}
      </p>

      <!-- Source Quarter -->
      <div class="flex flex-col gap-1">
        <label for="source-quarter" class="text-sm font-medium text-slate-700">
          {{ t('epr.materialLibrary.copyDialog.sourceLabel') }}
        </label>
        <Select
          id="source-quarter"
          v-model="selectedQuarter"
          :options="quarterOptions"
          option-label="label"
          option-value="value"
          :placeholder="t('epr.materialLibrary.copyDialog.selectQuarter')"
          append-to="self"
          data-testid="source-quarter-select"
        />
      </div>

      <!-- Include Non-Recurring -->
      <div class="flex items-center gap-2">
        <Checkbox
          v-model="includeNonRecurring"
          input-id="include-non-recurring"
          :binary="true"
          data-testid="include-non-recurring-checkbox"
        />
        <label for="include-non-recurring" class="text-sm text-slate-700">
          {{ t('epr.materialLibrary.copyDialog.includeNonRecurring') }}
        </label>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button
          :label="t('common.actions.cancel')"
          severity="secondary"
          text
          @click="emit('update:visible', false)"
        />
        <Button
          :label="t('epr.materialLibrary.copyDialog.copyButton')"
          :disabled="!selectedQuarter"
          data-testid="copy-quarter-submit"
          @click="handleCopy"
        />
      </div>
    </template>
  </Dialog>
</template>
