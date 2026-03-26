<script setup lang="ts">
definePageMeta({ layout: 'public' })

import authConfig from '~/risk-guard-tokens.json'
const { t, te } = useI18n()
const config = useRuntimeConfig()

// In Spring Boot OAuth2 Login, the actual redirect to provider is handled by the backend at /oauth2/authorization/{registrationId}
// We need to point to the backend's absolute URL for this
const loginWithGoogle = () => {
  window.location.href = `${config.public.apiBase}${authConfig.oauth2.google}`
}

const loginWithMicrosoft = () => {
  window.location.href = `${config.public.apiBase}${authConfig.oauth2.microsoft}`
}

const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

// Email/password login state
const loginEmail = ref('')
const loginPassword = ref('')
const isLoggingIn = ref(false)

// Error type to i18n key mapping
const loginErrorMap: Record<string, string> = {
  'urn:riskguard:error:invalid-credentials': 'auth.login.error.invalidCredentials',
  'urn:riskguard:error:too-many-attempts': 'auth.login.error.tooManyAttempts',
}

async function handleEmailLogin() {
  if (!loginEmail.value || !loginPassword.value || isLoggingIn.value) return

  isLoggingIn.value = true
  try {
    await $fetch('/api/public/auth/login', {
      method: 'POST',
      body: {
        email: loginEmail.value,
        password: loginPassword.value,
      },
      baseURL: config.public.apiBase as string,
      credentials: 'include',
    })

    // Login successful — cookie is set by backend
    await authStore.initializeAuth()
    navigateTo('/dashboard')
  } catch (error: any) {
    const errorType = error?.data?.type || ''
    const i18nKey = loginErrorMap[errorType] || 'auth.login.error.generic'

    toast.add({
      severity: 'error',
      summary: t('auth.login.error.title'),
      detail: t(i18nKey),
      life: 5000,
    })
  } finally {
    isLoggingIn.value = false
  }
}

onMounted(async () => {
  // 1. Re-check authentication from cookie (this might have been handled by the middleware already)
  if (!authStore.isAuthenticated) {
    await authStore.initializeAuth()
  }

  // 2. Handle error from query param (e.g. if the backend redirected here with an error)
  if (route.query.error) {
    const errorKey = String(route.query.error);
    const i18nKey = `auth.login.error.${errorKey}`;
    
    toast.add({
      severity: 'error',
      summary: t('auth.login.error.title'),
      detail: te(i18nKey) ? t(i18nKey) : t('auth.login.error.generic'),
      life: 5000
    })
  }

  // 3. If authenticated, redirect to dashboard
  if (authStore.isAuthenticated) {
    navigateTo('/dashboard')
  }
})
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-surface-50">
    <div class="w-full max-w-md p-8 bg-surface-0 border border-surface-200 rounded-xl shadow-lg">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-surface-900 mb-2">
          {{ t('auth.login.title') }}
        </h1>
        <p class="text-surface-600">
          {{ t('auth.login.subtitle') }}
        </p>
      </div>

      <!-- SSO Buttons -->
      <div class="flex flex-col gap-4">
        <Button 
          icon="pi pi-google" 
          :label="t('auth.login.google')" 
          class="w-full p-button-outlined" 
          severity="secondary"
          @click="loginWithGoogle"
        />
        
        <Button 
          icon="pi pi-microsoft" 
          :label="t('auth.login.microsoft')" 
          class="w-full p-button-outlined" 
          severity="secondary"
          @click="loginWithMicrosoft"
        />
      </div>

      <!-- Divider (AC #1) -->
      <Divider align="center" class="my-6">
        <span class="text-surface-500 text-sm">{{ t('auth.login.or') }}</span>
      </Divider>

      <!-- Email/Password Login Form (AC #4, #5) -->
      <form class="flex flex-col gap-4" @submit.prevent="handleEmailLogin">
        <div class="flex flex-col gap-2">
          <label for="login-email">{{ t('auth.login.emailLabel') }}</label>
          <InputText
            id="login-email"
            v-model="loginEmail"
            type="email"
            :placeholder="t('auth.login.emailLabel')"
            autocomplete="email"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="login-password">{{ t('auth.login.passwordLabel') }}</label>
          <InputText
            id="login-password"
            v-model="loginPassword"
            type="password"
            :placeholder="t('auth.login.passwordLabel')"
            autocomplete="current-password"
          />
        </div>

        <Button
          type="submit"
          :label="t('auth.login.emailSubmit')"
          class="w-full mt-2"
          :disabled="!loginEmail || !loginPassword || isLoggingIn"
          :loading="isLoggingIn"
        />
      </form>

      <!-- Register link (AC #1) -->
      <div class="text-center mt-6">
        <NuxtLink to="/auth/register" class="text-primary-500 hover:text-primary-700">
          {{ t('auth.login.noAccount') }}
        </NuxtLink>
      </div>
    </div>
  </div>
</template>
