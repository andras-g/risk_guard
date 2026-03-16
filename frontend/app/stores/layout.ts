import { defineStore } from 'pinia'

const SIDEBAR_STORAGE_KEY = 'risk-guard-sidebar-expanded'

export const useLayoutStore = defineStore('layout', {
  state: () => ({
    sidebarExpanded: true,
    mobileDrawerOpen: false
  }),

  actions: {
    toggleSidebar() {
      this.sidebarExpanded = !this.sidebarExpanded
      try {
        localStorage.setItem(SIDEBAR_STORAGE_KEY, String(this.sidebarExpanded))
      } catch {
        // localStorage unavailable (SSR or quota exceeded) — preference is transient
      }
    },

    openMobileDrawer() {
      this.mobileDrawerOpen = true
    },

    closeMobileDrawer() {
      this.mobileDrawerOpen = false
    },

    initFromStorage() {
      try {
        const stored = localStorage.getItem(SIDEBAR_STORAGE_KEY)
        if (stored !== null) {
          this.sidebarExpanded = stored === 'true'
        }
      } catch {
        // localStorage unavailable — use default (expanded)
      }
    }
  }
})
