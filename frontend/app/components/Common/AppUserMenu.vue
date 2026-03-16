<template>
  <div
    class="flex items-center gap-3"
    data-testid="app-user-menu"
  >
    <div class="text-right hidden sm:block">
      <div
        class="text-sm font-medium text-white"
        data-testid="user-name"
      >
        {{ userName }}
      </div>
      <div
        class="text-xs text-slate-400"
        data-testid="user-role"
      >
        {{ userRole }}
      </div>
    </div>

    <button
      class="flex items-center justify-center"
      data-testid="user-avatar-button"
      @click="toggleMenu"
    >
      <Avatar
        :label="userInitials"
        shape="circle"
        size="normal"
        data-testid="user-avatar"
      />
    </button>

    <Menu
      ref="menuRef"
      :model="menuItems"
      :popup="true"
      data-testid="user-dropdown-menu"
    />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/auth'

const authStore = useAuthStore()
const { name: userName, role: userRole } = storeToRefs(authStore)

const menuRef = ref()

const userInitials = computed(() => {
  const n = userName.value
  if (!n) return '?'
  return n.split(' ').map((w: string) => w[0]).join('').toUpperCase().slice(0, 2)
})

const { t: $t } = useI18n()

const menuItems = computed(() => [
  {
    label: $t('common.actions.logout'),
    icon: 'pi pi-sign-out',
    command: () => {
      authStore.clearAuth()
      navigateTo('/auth/login')
    }
  }
])

function toggleMenu(event: Event) {
  menuRef.value?.toggle(event)
}
</script>
