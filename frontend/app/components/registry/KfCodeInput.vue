<script setup lang="ts">
import InputText from 'primevue/inputtext'

const { t } = useI18n()

const props = defineProps<{
  modelValue: string | null | undefined
  id?: string
  required?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
}>()

const touched = ref(false)
const error = ref('')

/** Display value with spaces every 2 digits: 11010101 → "11 01 01 01" */
function formatKfCode(code: string): string {
  const digits = code.replace(/\s/g, '')
  if (digits.length !== 8) return code
  return `${digits.slice(0, 2)} ${digits.slice(2, 4)} ${digits.slice(4, 6)} ${digits.slice(6, 8)}`
}

const displayValue = computed(() => {
  if (!props.modelValue) return ''
  return formatKfCode(props.modelValue)
})

function validate(value: string): string {
  if (!value || value.length === 0) {
    return props.required ? t('registry.form.validation.kfCodeRequired') : ''
  }
  if (!/^\d{8}$/.test(value)) {
    return t('registry.form.validation.kfCodeInvalid')
  }
  return ''
}

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  // Strip spaces and non-digit characters
  const digits = target.value.replace(/\D/g, '')
  // Emit bare digits (max 8)
  const clamped = digits.slice(0, 8)
  emit('update:modelValue', clamped || null)
  // Re-format display
  target.value = clamped.length === 8 ? formatKfCode(clamped) : clamped
  if (touched.value) {
    error.value = validate(clamped)
  }
}

function onBlur() {
  touched.value = true
  error.value = validate(props.modelValue ?? '')
}

watch(() => props.modelValue, (val) => {
  if (touched.value) {
    error.value = validate(val ?? '')
  }
})
</script>

<template>
  <div class="flex flex-col gap-1">
    <InputText
      :id="id"
      :value="displayValue"
      :aria-describedby="error ? `${id}-error` : undefined"
      :aria-invalid="!!error"
      inputmode="numeric"
      placeholder="11 01 01 01"
      @input="onInput"
      @blur="onBlur"
    />
    <small
      v-if="error"
      :id="`${id}-error`"
      role="alert"
      class="text-red-500"
    >
      {{ error }}
    </small>
  </div>
</template>
