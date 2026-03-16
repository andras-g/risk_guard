<script setup lang="ts">
import { z } from 'zod'
import { useScreeningStore } from '~/stores/screening'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'

const { t } = useI18n()
const screeningStore = useScreeningStore()

const taxNumberInput = ref('')
const validationError = ref('')

/**
 * Zod schema for Hungarian tax number validation (AC1).
 * Accepts 8-digit (adószám) or 11-digit (adóazonosító jel) formats.
 * Validation runs on cleaned (digits-only) input.
 */
const hungarianTaxNumberSchema = z.string().regex(
  /^\d{8}(\d{3})?$/,
  'screening.search.invalidTaxNumber'
)

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
  const result = hungarianTaxNumberSchema.safeParse(cleaned)
  if (!result.success) {
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
          class="text-at-risk mt-1 block"
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
