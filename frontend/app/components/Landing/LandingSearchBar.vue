<script setup lang="ts">
import { formatTaxNumber, taxNumberSchema } from '~/utils/taxNumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'

const props = defineProps<{
  serviceUnavailable?: boolean
}>()

const { t } = useI18n()

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
    validationError.value = t('landing.search.invalidTaxNumber')
    return false
  }
  validationError.value = ''
  return true
}

function onSubmit() {
  if (props.serviceUnavailable) return
  if (!validate()) return
  const cleaned = taxNumberInput.value.replace(/[^\d]/g, '')
  // Navigate to screening — the auth middleware will redirect to login if needed,
  // preserving the target URL. Guest screening (without login) is planned for Story 3.12.
  navigateTo(`/screening/${cleaned}`)
}
</script>

<template>
  <form
    class="flex flex-col gap-4 w-full max-w-xl mx-auto"
    role="search"
    :aria-label="t('landing.hero.searchAriaLabel')"
    @submit.prevent="onSubmit"
  >
    <div class="flex flex-col md:flex-row gap-3">
      <div class="flex-1">
        <label
          for="landing-tax-number"
          class="sr-only"
        >
          {{ t('landing.hero.searchPlaceholder') }}
        </label>
        <InputText
          id="landing-tax-number"
          :model-value="taxNumberInput"
          :placeholder="t('landing.hero.searchPlaceholder')"
          class="w-full font-mono text-lg"
          :class="{ 'p-invalid': validationError }"
          :disabled="serviceUnavailable"
          :aria-describedby="validationError ? 'landing-tax-error' : undefined"
          :aria-invalid="!!validationError"
          @input="onInput"
        />
        <small
          v-if="validationError"
          id="landing-tax-error"
          class="text-at-risk mt-1 block"
          data-testid="validation-error"
          role="alert"
        >
          {{ validationError }}
        </small>
      </div>
      <Button
        type="submit"
        :label="t('landing.hero.cta')"
        :disabled="serviceUnavailable"
        icon="pi pi-search"
        class="w-full md:w-auto bg-authority text-white"
      />
    </div>

    <p
      v-if="serviceUnavailable"
      class="text-stale text-sm text-center"
      role="alert"
    >
      {{ t('landing.search.serviceUnavailable') }}
    </p>
  </form>
</template>
