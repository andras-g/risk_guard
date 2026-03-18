<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { formatTaxNumber, taxNumberSchema } from '~/utils/taxNumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'

const { t } = useI18n()
const screeningStore = useScreeningStore()

const taxNumberInput = ref('')
const validationError = ref('')

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  const raw = target.value
  const formatted = formatTaxNumber(raw)
  taxNumberInput.value = formatted
  validationError.value = ''
}

function validate(): boolean {
  const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
  const result = taxNumberSchema.safeParse(cleaned)
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
    role="search"
    :aria-label="t('screening.search.ariaLabel')"
    class="flex flex-col gap-3 w-full max-w-lg"
    @submit.prevent="onSubmit"
  >
    <div class="flex gap-2">
      <div class="flex-1">
        <label
          for="screening-tax-number"
          class="sr-only"
        >
          {{ t('screening.search.placeholder') }}
        </label>
        <InputText
          id="screening-tax-number"
          :model-value="taxNumberInput"
          :placeholder="t('screening.search.placeholder')"
          class="w-full"
          :class="{ 'p-invalid': validationError }"
          :disabled="screeningStore.isSearching"
          :aria-describedby="validationError ? 'screening-tax-error' : undefined"
          :aria-invalid="!!validationError"
          @input="onInput"
        />
        <small
          v-if="validationError"
          id="screening-tax-error"
          class="text-at-risk mt-1 block"
          role="alert"
          data-testid="validation-error"
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
