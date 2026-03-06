<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

onMounted(() => {
  const token = route.query.token as string
  if (token) {
    authStore.setToken(token)
    // In Story 1.3 redirect to dashboard or home
    router.push('/')
  } else {
    // Show error
    console.error('No token provided in callback')
    router.push('/auth/login?error=no_token')
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
