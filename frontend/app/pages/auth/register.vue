<script setup lang="ts">
definePageMeta({ layout: 'public' })

const { t } = useI18n()
const config = useRuntimeConfig()
const toast = useToast()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const displayName = ref('')
const isSubmitting = ref(false)

// Password strength requirements (AC #9)
const passwordChecks = computed(() => ({
  minLength: password.value.length >= 8,
  uppercase: /[A-Z]/.test(password.value),
  digit: /\d/.test(password.value),
  special: /[^a-zA-Z0-9]/.test(password.value),
}))

const isPasswordValid = computed(() =>
  Object.values(passwordChecks.value).every(Boolean)
)

const passwordsMatch = computed(() =>
  password.value === confirmPassword.value && confirmPassword.value.length > 0
)

// Basic email format check — prevents enabling submit on obviously invalid input (e.g. "abc")
// while still deferring full RFC 5321 validation to the backend @Email annotation.
const isEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value))

const isFormValid = computed(() =>
  isEmailValid.value &&
  displayName.value.length > 0 &&
  isPasswordValid.value &&
  passwordsMatch.value
)

// Error type to i18n key mapping (AC #3, #7)
const errorTypeMap: Record<string, string> = {
  'urn:riskguard:error:email-already-registered': 'auth.register.error.emailExists',
  'urn:riskguard:error:email-exists-sso': 'auth.register.error.emailExistsSso',
}

async function handleRegister() {
  if (!isFormValid.value || isSubmitting.value) return

  isSubmitting.value = true
  try {
    await $fetch('/api/public/auth/register', {
      method: 'POST',
      body: {
        email: email.value,
        password: password.value,
        confirmPassword: confirmPassword.value,
        name: displayName.value,
      },
      baseURL: config.public.apiBase as string,
      credentials: 'include',
    })

    // Registration successful — cookie is set by backend
    await authStore.initializeAuth()
    navigateTo('/dashboard')
  } catch (error: any) {
    const errorType = error?.data?.type || ''
    const i18nKey = errorTypeMap[errorType] || 'auth.register.error.generic'

    toast.add({
      severity: 'error',
      summary: t('auth.register.error.title'),
      detail: t(i18nKey),
      life: 5000,
    })
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-surface-50">
    <div class="w-full max-w-md p-8 bg-surface-0 border border-surface-200 rounded-xl shadow-lg">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-surface-900 mb-2">
          {{ t('auth.register.title') }}
        </h1>
        <p class="text-surface-600">
          {{ t('auth.register.subtitle') }}
        </p>
      </div>

      <form class="flex flex-col gap-4" @submit.prevent="handleRegister">
        <!-- Display Name -->
        <div class="flex flex-col gap-2">
          <label for="register-name">{{ t('auth.register.name') }}</label>
          <InputText
            id="register-name"
            v-model="displayName"
            :placeholder="t('auth.register.name')"
            autocomplete="name"
          />
        </div>

        <!-- Email -->
        <div class="flex flex-col gap-2">
          <label for="register-email">{{ t('auth.register.email') }}</label>
          <InputText
            id="register-email"
            v-model="email"
            type="email"
            :placeholder="t('auth.register.email')"
            autocomplete="email"
          />
        </div>

        <!-- Password -->
        <div class="flex flex-col gap-2">
          <label for="register-password">{{ t('auth.register.password') }}</label>
          <InputText
            id="register-password"
            v-model="password"
            type="password"
            :placeholder="t('auth.register.password')"
            autocomplete="new-password"
          />
        </div>

        <!-- Password Strength Indicator (AC #9) -->
        <div v-if="password.length > 0" class="text-sm" role="status" aria-live="assertive" aria-atomic="true" :aria-label="t('auth.register.passwordStrength.title')">
          <p class="font-medium text-surface-700 mb-1">{{ t('auth.register.passwordStrength.title') }}</p>
          <ul class="list-none p-0 m-0 flex flex-col gap-1">
            <li :class="passwordChecks.minLength ? 'text-green-600' : 'text-surface-500'">
              {{ passwordChecks.minLength ? '✓' : '○' }} {{ t('auth.register.passwordStrength.minLength') }}
            </li>
            <li :class="passwordChecks.uppercase ? 'text-green-600' : 'text-surface-500'">
              {{ passwordChecks.uppercase ? '✓' : '○' }} {{ t('auth.register.passwordStrength.uppercase') }}
            </li>
            <li :class="passwordChecks.digit ? 'text-green-600' : 'text-surface-500'">
              {{ passwordChecks.digit ? '✓' : '○' }} {{ t('auth.register.passwordStrength.digit') }}
            </li>
            <li :class="passwordChecks.special ? 'text-green-600' : 'text-surface-500'">
              {{ passwordChecks.special ? '✓' : '○' }} {{ t('auth.register.passwordStrength.special') }}
            </li>
          </ul>
        </div>

        <!-- Confirm Password -->
        <div class="flex flex-col gap-2">
          <label for="register-confirm-password">{{ t('auth.register.confirmPassword') }}</label>
          <InputText
            id="register-confirm-password"
            v-model="confirmPassword"
            type="password"
            :placeholder="t('auth.register.confirmPassword')"
            autocomplete="off"
          />
          <small v-if="confirmPassword.length > 0 && !passwordsMatch" class="text-red-500">
            {{ t('auth.register.error.passwordMismatch') }}
          </small>
        </div>

        <!-- Submit Button -->
        <Button
          type="submit"
          :label="t('auth.register.submit')"
          class="w-full mt-2"
          :disabled="!isFormValid || isSubmitting"
          :loading="isSubmitting"
        />
      </form>

      <!-- Link to login -->
      <div class="text-center mt-6">
        <NuxtLink to="/auth/login" class="text-primary-500 hover:text-primary-700">
          {{ t('auth.register.hasAccount') }}
        </NuxtLink>
      </div>
    </div>
  </div>
</template>
