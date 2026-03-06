<template>
  <div class="min-h-screen flex flex-col bg-slate-950 text-slate-50">
    <!-- Top Bar -->
    <header class="h-16 border-b border-slate-800 bg-slate-900/50 backdrop-blur flex items-center justify-between px-6 sticky top-0 z-50">
      <div class="flex items-center gap-8">
        <div class="text-xl font-bold bg-gradient-to-r from-indigo-400 to-indigo-600 bg-clip-text text-transparent">
          RiskGuard
        </div>
        
        <nav class="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
          <NuxtLink to="/dashboard" class="hover:text-white transition-colors" active-class="text-white">Dashboard</NuxtLink>
          <NuxtLink to="/screening" class="hover:text-white transition-colors" active-class="text-white">Screening</NuxtLink>
          <NuxtLink to="/watchlist" class="hover:text-white transition-colors" active-class="text-white">Watchlist</NuxtLink>
          <NuxtLink to="/epr" class="hover:text-white transition-colors" active-class="text-white">EPR</NuxtLink>
        </nav>
      </div>

      <div class="flex items-center gap-4">
        <!-- Tenant Switcher Integrated Here -->
        <IdentityTenantSwitcher />
        
        <div class="h-8 w-px bg-slate-800 mx-2" />
        
        <div class="flex items-center gap-3">
          <div class="text-right hidden sm:block">
            <div class="text-sm font-medium">{{ authStore.name }}</div>
            <div class="text-xs text-slate-500">{{ authStore.role }}</div>
          </div>
          <Button icon="pi pi-user" severity="secondary" rounded variant="text" />
        </div>
      </div>
    </header>

    <!-- Page Content -->
    <main class="flex-1 p-6">
      <div class="max-auto max-w-7xl">
        <slot />
      </div>
    </main>

    <!-- Context Guard (Safety Interstitial) -->
    <IdentityContextGuard />
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'

const authStore = useAuthStore()
</script>
