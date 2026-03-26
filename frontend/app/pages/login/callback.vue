<script setup lang="ts">
definePageMeta({ layout: 'public' })

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

onMounted(async () => {
  // Token is now set as an HttpOnly cookie by the backend (not in query params).
  // Initialize auth from the cookie via the /me endpoint.
  await authStore.initializeAuth()
  
  if (authStore.isAuthenticated) {
    router.push('/dashboard')
  } else {
    // Auth cookie may not have been set — redirect to login with error
    console.error('Authentication failed: no valid session after OAuth2 callback')
    router.push('/auth/login?error=auth-failed')
  }
})
</script>

<template>
  <div class="flex flex-col items-center justify-center min-h-screen bg-surface-50">
    <ProgressSpinner />
    <p class="mt-4 text-surface-600">
      {{ t('common.states.loading') }}
    </p>
  </div>
</template>
