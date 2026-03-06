<script setup lang="ts">
import authConfig from '../../risk-guard-tokens.json'
const { t, te } = useI18n()
const config = useRuntimeConfig()

// In Spring Boot OAuth2 Login, the actual redirect to provider is handled by the backend at /oauth2/authorization/{registrationId}
// We need to point to the backend's absolute URL for this
const loginWithGoogle = () => {
  window.location.href = `${config.public.apiBase.replace('/api/v1', '')}${authConfig.oauth2.google}`
}

const loginWithMicrosoft = () => {
  window.location.href = `${config.public.apiBase.replace('/api/v1', '')}${authConfig.oauth2.microsoft}`
}

const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

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

  // 3. If authenticated, redirect to home
  if (authStore.isAuthenticated) {
    navigateTo('/')
  }
})
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-surface-50">
    <Toast />
    <div class="w-full max-w-md p-8 bg-surface-0 border border-surface-200 rounded-xl shadow-lg">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-surface-900 mb-2">
          {{ t('auth.login.title') }}
        </h1>
        <p class="text-surface-600">
          {{ t('auth.login.subtitle') }}
        </p>
      </div>

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
    </div>
  </div>
</template>
