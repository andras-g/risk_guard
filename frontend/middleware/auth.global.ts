import authConfig from '~/risk-guard-tokens.json'

export default defineNuxtRouteMiddleware(async (to, from) => {
  const authStore = useAuthStore()
  
  // Re-hydrate store from cookie if necessary
  if (!authStore.isAuthenticated) {
    await authStore.initializeAuth()
  }

  // Public routes that don't require authentication
  const isPublicRoute = authConfig.publicRoutes.some(route => to.path.startsWith(route))

  if (!authStore.isAuthenticated && !isPublicRoute) {
    return navigateTo('/auth/login')
  }

  // If authenticated and trying to access login, redirect to home
  if (authStore.isAuthenticated && to.path === '/auth/login') {
    return navigateTo('/')
  }
})
