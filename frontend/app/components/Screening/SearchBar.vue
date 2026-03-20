<script setup lang="ts">
import { useScreeningStore } from '~/stores/screening'
import { useAuthStore } from '~/stores/auth'
import { useGuestSession } from '~/composables/auth/useGuestSession'
import { formatTaxNumber, taxNumberSchema } from '~/utils/taxNumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import ProgressBar from 'primevue/progressbar'

const { t } = useI18n()
const screeningStore = useScreeningStore()
const authStore = useAuthStore()
const guestSession = useGuestSession()

const taxNumberInput = ref('')
const validationError = ref('')

const isGuest = computed(() => !authStore.isAuthenticated)
const isLoading = computed(() =>
  isGuest.value ? guestSession.isSearching.value : screeningStore.isSearching
)

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

  if (isGuest.value) {
    await guestSession.guestSearch(cleaned)
  } else {
    await screeningStore.search(cleaned)
  }
}

const guestProgressPercent = computed(() => {
  const stats = guestSession.usageStats.value
  if (stats.companiesLimit === 0) return 0
  return Math.round((stats.companiesUsed / stats.companiesLimit) * 100)
})
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
          :disabled="isLoading"
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
        :loading="isLoading"
        :disabled="isLoading"
        icon="pi pi-search"
      />
    </div>

    <!-- Guest progress indicator (AC #3) -->
    <div
      v-if="isGuest"
      class="flex flex-col gap-1"
      data-testid="guest-progress"
    >
      <div class="flex items-center justify-between text-sm text-surface-500">
        <span>
          {{ t('screening.guest.progressIndicator', {
            used: guestSession.usageStats.value.companiesUsed,
            limit: guestSession.usageStats.value.companiesLimit
          }) }}
        </span>
      </div>
      <ProgressBar
        :value="guestProgressPercent"
        :show-value="false"
        class="h-2"
      />
    </div>

    <!-- Guest limit messages (AC #4, #5) -->
    <div
      v-if="isGuest && guestSession.limitError.value === 'DAILY_LIMIT_REACHED'"
      class="p-3 bg-orange-50 border border-orange-200 rounded-lg text-center"
      role="alert"
      data-testid="daily-limit-message"
    >
      <p class="text-orange-700 font-medium mb-2">
        {{ t('screening.guest.dailyLimitReached') }}
      </p>
      <NuxtLink
        to="/auth/register"
        class="inline-block px-4 py-2 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 transition-colors"
      >
        {{ t('screening.guest.signUpCta') }}
      </NuxtLink>
    </div>

    <div
      v-if="isGuest && guestSession.limitError.value === 'COMPANY_LIMIT_REACHED'"
      class="p-3 bg-orange-50 border border-orange-200 rounded-lg text-center"
      role="alert"
      data-testid="company-limit-message"
    >
      <p class="text-orange-700 font-medium mb-2">
        {{ t('screening.guest.companyLimitReached') }}
      </p>
      <NuxtLink
        to="/auth/register"
        class="inline-block px-4 py-2 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 transition-colors"
      >
        {{ t('screening.guest.signUpCta') }}
      </NuxtLink>
    </div>

    <!-- Guest search error -->
    <div
      v-if="isGuest && guestSession.searchError.value"
      class="text-at-risk text-sm"
      role="alert"
      data-testid="guest-search-error"
    >
      {{ t('screening.verdict.searchFailed') }}
    </div>
  </form>
</template>
