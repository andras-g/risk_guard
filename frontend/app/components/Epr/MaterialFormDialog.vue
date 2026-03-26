<script setup lang="ts">
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import type { MaterialTemplateResponse } from '~/types/epr'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  editTemplate?: MaterialTemplateResponse | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'submit': [data: { name: string; baseWeightGrams: number; recurring: boolean }]
}>()

const name = ref('')
const baseWeightGrams = ref<number | null>(null)
const recurring = ref(true)

// Validation state — real-time @blur per UX Spec §7.2 "MOHU Gate" pattern
const nameError = ref('')
const weightError = ref('')
const submitted = ref(false)
const nameTouched = ref(false)
const weightTouched = ref(false)

const isEditMode = computed(() => !!props.editTemplate)
const dialogTitle = computed(() =>
  isEditMode.value
    ? t('epr.materialLibrary.dialog.editTitle')
    : t('epr.materialLibrary.dialog.addTitle'),
)

// Reset form when dialog opens or editTemplate changes — immediate so fields are populated
// when mounted with visible=true (e.g., in tests)
watch(() => props.visible, (newVal) => {
  if (newVal) {
    submitted.value = false
    nameTouched.value = false
    weightTouched.value = false
    nameError.value = ''
    weightError.value = ''
    if (props.editTemplate) {
      name.value = props.editTemplate.name
      baseWeightGrams.value = props.editTemplate.baseWeightGrams
      recurring.value = props.editTemplate.recurring
    }
    else {
      name.value = ''
      baseWeightGrams.value = null
      recurring.value = true
    }
  }
}, { immediate: true })

function validateName(): string {
  if (!name.value || name.value.trim() === '') {
    return t('epr.materialLibrary.validation.nameRequired')
  }
  return ''
}

function validateWeight(): string {
  if (baseWeightGrams.value === null || baseWeightGrams.value <= 0) {
    return t('epr.materialLibrary.validation.weightPositive')
  }
  return ''
}

function onNameBlur() {
  nameTouched.value = true
  nameError.value = validateName()
}

function onWeightBlur() {
  weightTouched.value = true
  weightError.value = validateWeight()
}

// Also validate name on input for instant feedback after first blur (MOHU Gate pattern)
watch(name, () => {
  if (nameTouched.value) {
    nameError.value = validateName()
  }
})

watch(baseWeightGrams, () => {
  if (weightTouched.value) {
    weightError.value = validateWeight()
  }
})

function validate(): boolean {
  nameError.value = validateName()
  weightError.value = validateWeight()
  return !nameError.value && !weightError.value
}

function handleSubmit() {
  submitted.value = true
  if (!validate()) return

  // Round to 2 decimal places to mitigate JS floating-point edge cases
  // (e.g., 0.1 + 0.2 = 0.30000000000000004). Backend @Positive BigDecimal
  // provides final validation — if rejected, useApiError() maps RFC 7807 response.
  const safeWeight = Math.round(baseWeightGrams.value! * 100) / 100

  emit('submit', {
    name: name.value.trim(),
    baseWeightGrams: safeWeight,
    recurring: recurring.value,
  })
  emit('update:visible', false)
}
</script>

<template>
  <Dialog
    :visible="visible"
    :header="dialogTitle"
    modal
    class="w-full max-w-[560px]"
    data-testid="material-form-dialog"
    @update:visible="emit('update:visible', $event)"
  >
    <div class="flex flex-col gap-5 p-2">
      <!-- Name -->
      <div class="flex flex-col gap-1">
        <label for="material-name" class="text-sm font-medium text-slate-700">
          {{ t('epr.materialLibrary.dialog.nameLabel') }}
        </label>
        <InputText
          id="material-name"
          v-model="name"
          :class="{
            'p-invalid': (nameTouched || submitted) && nameError,
            'border-emerald-600': nameTouched && !nameError && name.trim() !== '',
          }"
          :placeholder="t('epr.materialLibrary.dialog.namePlaceholder')"
          data-testid="material-name-input"
          @blur="onNameBlur"
        />
        <small v-if="(nameTouched || submitted) && nameError" class="text-[#B91C1C]" data-testid="name-error">{{ nameError }}</small>
      </div>

      <!-- Base Weight -->
      <div class="flex flex-col gap-1">
        <label for="material-weight" class="text-sm font-medium text-slate-700">
          {{ t('epr.materialLibrary.dialog.weightLabel') }}
        </label>
        <InputNumber
          id="material-weight"
          v-model="baseWeightGrams"
          :class="{
            'p-invalid': (weightTouched || submitted) && weightError,
            'border-emerald-600': weightTouched && !weightError && baseWeightGrams !== null && baseWeightGrams > 0,
          }"
          :min="0.01"
          :step="0.01"
          :min-fraction-digits="2"
          :max-fraction-digits="2"
          suffix=" g"
          :placeholder="t('epr.materialLibrary.dialog.weightPlaceholder')"
          data-testid="material-weight-input"
          @blur="onWeightBlur"
        />
        <small v-if="(weightTouched || submitted) && weightError" class="text-[#B91C1C]" data-testid="weight-error">{{ weightError }}</small>
      </div>

      <!-- Recurring -->
      <div class="flex items-center gap-2">
        <Checkbox
          v-model="recurring"
          input-id="material-recurring"
          :binary="true"
          data-testid="material-recurring-checkbox"
        />
        <label
          for="material-recurring"
          class="text-sm text-slate-700"
          v-tooltip="{ value: t('epr.materialLibrary.dialog.recurringTooltip'), escape: true }"
        >
          {{ t('epr.materialLibrary.dialog.recurringLabel') }}
        </label>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button
          :label="t('common.actions.cancel')"
          severity="secondary"
          text
          data-testid="material-form-cancel"
          @click="emit('update:visible', false)"
        />
        <Button
          :label="isEditMode ? t('common.actions.save') : t('epr.materialLibrary.addButton')"
          data-testid="material-form-submit"
          @click="handleSubmit"
        />
      </div>
    </template>
  </Dialog>
</template>
