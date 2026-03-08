import authConfig from '../risk-guard-tokens.json'

export default defineNuxtRouteMiddleware(async (to, from) => {
  const authStore = useAuthStore()

  // Short-circuit on public routes BEFORE calling initializeAuth() to avoid
  // unnecessary /me HTTP requests on unauthenticated navigation to public pages.
  const isPublicRoute = authConfig.publicRoutes.some(route => to.path.startsWith(route))

  // Re-hydrate store from cookie if necessary — only for non-public routes.
  // Graceful degradation: if the /me request fails due to a network error (e.g., temporary
  // connectivity loss), we treat the user as unauthenticated and redirect to login rather than
  // crashing the app. An HTTP error (401/403) or a valid-but-empty response are also treated
  // as unauthenticated — no retry is attempted since retrying on auth failure would loop.
  if (!authStore.isAuthenticated && !isPublicRoute) {
    try {
      await authStore.initializeAuth()
    } catch {
      // Network error, server unreachable, or unexpected error.
      // Treat as unauthenticated — redirect to login will follow below.
    }
  }

  if (!authStore.isAuthenticated && !isPublicRoute) {
    return navigateTo('/auth/login')
  }

  // If authenticated and trying to access login, redirect to home
  if (authStore.isAuthenticated && to.path === '/auth/login') {
    return navigateTo('/')
  }
})
