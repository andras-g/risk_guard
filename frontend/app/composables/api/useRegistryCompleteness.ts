import { ref, computed } from 'vue'
import { useApi } from '~/composables/api/useApi'

export function useRegistryCompleteness() {
  const { apiFetch } = useApi()
  const totalProducts = ref(0)
  const productsWithComponents = ref(0)
  const isLoading = ref(false)
  const isEmpty = computed(() => productsWithComponents.value === 0)

  async function refresh(): Promise<void> {
    isLoading.value = true
    try {
      const data = await apiFetch<{ totalProducts: number; productsWithComponents: number }>(
        '/api/v1/registry/summary',
      )
      totalProducts.value = data.totalProducts
      productsWithComponents.value = data.productsWithComponents
    }
    catch (err) {
      console.error('[useRegistryCompleteness] Failed to fetch summary', err)
    }
    finally {
      isLoading.value = false
    }
  }

  return { isEmpty, totalProducts, productsWithComponents, isLoading, refresh }
}
