<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'

const { t } = useI18n()
const screeningStore = useScreeningStore()

const taxNumberInput = ref('')
const validationError = ref('')

// Hungarian tax number regex: 8-digit or 11-digit (after stripping hyphens/spaces)
const TAX_NUMBER_REGEX = /^\d{8}(\d{3})?$/

/**
 * Auto-format the tax number with visual masking:
 * 8-digit: 1234-5678
 * 11-digit: 1234-5678-901
 */
function formatTaxNumber(raw: string): string {
  const digits = raw.replace(/[^\d]/g, '').slice(0, 11)
  if (digits.length <= 4) return digits
  if (digits.length <= 8) return `${digits.slice(0, 4)}-${digits.slice(4)}`
  return `${digits.slice(0, 4)}-${digits.slice(4, 8)}-${digits.slice(8)}`
}

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  const raw = target.value
  const formatted = formatTaxNumber(raw)
  taxNumberInput.value = formatted
  validationError.value = ''
}

function validate(): boolean {
  const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
  if (!TAX_NUMBER_REGEX.test(cleaned)) {
    validationError.value = t('screening.search.invalidTaxNumber')
    return false
  }
  validationError.value = ''
  return true
}

async function onSubmit() {
  if (!validate()) return
  const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
  await screeningStore.search(cleaned)
}
</script>

<template>
  <form
    class="flex flex-col gap-3 w-full max-w-lg"
    @submit.prevent="onSubmit"
  >
    <div class="flex gap-2">
      <div class="flex-1">
        <InputText
          :model-value="taxNumberInput"
          :placeholder="t('screening.search.placeholder')"
          class="w-full"
          :class="{ 'p-invalid': validationError }"
          :disabled="screeningStore.isSearching"
          @input="onInput"
        />
        <small
          v-if="validationError"
          class="text-red-500 mt-1 block"
        >
          {{ validationError }}
        </small>
      </div>
      <Button
        type="submit"
        :label="t('screening.search.submit')"
        :loading="screeningStore.isSearching"
        :disabled="screeningStore.isSearching"
        icon="pi pi-search"
      />
    </div>
  </form>
</template>
