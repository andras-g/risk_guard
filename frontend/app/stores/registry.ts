import { defineStore } from 'pinia'
import type {
  ProductResponse,
  RegistryListFilter,
} from '~/composables/api/useRegistry'

interface RegistryState {
  // List page state
  listFilter: RegistryListFilter
  listPage: number
  listPageSize: number
  // Edit page state
  editProduct: ProductResponse | null
  isDirty: boolean
  isSaving: boolean
  isLoading: boolean
  error: string | null
}

export const useRegistryStore = defineStore('registry', {
  state: (): RegistryState => ({
    listFilter: {},
    listPage: 0,
    listPageSize: 50,
    editProduct: null,
    isDirty: false,
    isSaving: false,
    isLoading: false,
    error: null,
  }),

  actions: {
    setListFilter(filter: RegistryListFilter) {
      this.listFilter = filter
      this.listPage = 0
    },

    setListPage(page: number) {
      this.listPage = page
    },

    setEditProduct(product: ProductResponse | null) {
      this.editProduct = product
      this.isDirty = false
    },

    markDirty() {
      this.isDirty = true
    },

    clearError() {
      this.error = null
    },
  },
})
