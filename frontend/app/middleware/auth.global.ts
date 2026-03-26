import authConfig from '../risk-guard-tokens.json'

/**
 * Public routes that don't require authentication.
 * Exact matches and prefix matches are both supported:
 * - "/" matches only the landing page (exact)
 * - "/auth/login" matches "/auth/login" and "/auth/login?redirect=..." (prefix)
 * - "/company" matches "/company/12345678" (prefix for SEO pages)
 */
function isPublic(path: string): boolean {
  return authConfig.publicRoutes.some(route =>
    route === '/' ? path === '/' : path.startsWith(route),
  )
}

/**
 * Global auth guard — protects non-public routes.
 *
 * On first load of a protected route, calls initializeAuth() to validate the
 * session via /me. On subsequent navigations, the store is already hydrated
 * so no HTTP call is needed.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const authStore = useAuthStore()

  // Public routes — no auth check needed
  if (isPublic(to.path)) {
    // If already authenticated, redirect away from landing/login to dashboard
    if (authStore.isAuthenticated && (to.path === '/' || to.path === '/auth/login')) {
      return navigateTo('/dashboard')
    }
    return
  }

  // Protected route — initialize auth if not yet done
  if (!authStore.isAuthenticated) {
    try {
      await authStore.initializeAuth()
    } catch {
      // Auth initialization failed — treat as unauthenticated
    }
  }

  // After initialization, check auth state
  if (!authStore.isAuthenticated) {
    return navigateTo('/auth/login')
  }

  // Handle post-tenant-switch redirect: after switchTenant() reloads the page,
  // navigate to the stored target instead of staying on the current URL.
  // This is consumed once and removed from sessionStorage immediately.
  if (import.meta.client) {
    const redirect = sessionStorage.getItem('postSwitchRedirect')
    if (redirect) {
      sessionStorage.removeItem('postSwitchRedirect')
      return navigateTo(redirect)
    }
  }
})
