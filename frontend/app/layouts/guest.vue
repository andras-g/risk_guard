<script setup lang="ts">
const { t: $t } = useI18n()
</script>

<template>
  <div class="min-h-screen flex flex-col bg-white text-slate-900">
    <CommonSkipLink />

    <!-- Header with guest banner -->
    <header class="border-b border-slate-200">
      <div class="h-16 flex items-center justify-between px-6">
        <NuxtLink
          to="/"
          class="text-xl font-bold bg-gradient-to-r from-indigo-400 to-indigo-600 bg-clip-text text-transparent"
        >
          {{ $t('common.app.name') }}
        </NuxtLink>

        <div class="flex items-center gap-4">
          <NuxtLink
            to="/auth/login"
            class="text-sm font-medium text-slate-600 hover:text-slate-900 transition-colors"
          >
            {{ $t('common.actions.login') }}
          </NuxtLink>
          <NuxtLink
            to="/auth/register"
            class="text-sm font-medium px-4 py-2 rounded-md bg-authority text-white hover:bg-authority/90 transition-colors"
          >
            {{ $t('common.actions.register') }}
          </NuxtLink>
        </div>
      </div>

      <!-- Guest mode banner -->
      <div
        class="bg-stale/10 border-t border-stale/20 px-6 py-2 text-center text-sm text-stale"
        data-testid="guest-banner"
      >
        <i class="pi pi-info-circle mr-1" />
        {{ $t('common.layout.guestBanner') }}
      </div>
    </header>

    <!-- Content -->
    <main
      id="main-content"
      tabindex="-1"
      class="flex-1"
    >
      <div class="mx-auto max-w-7xl p-6">
        <slot />
      </div>
    </main>

    <!-- Footer placeholder -->
    <footer class="py-8 text-center text-sm text-secondary-text border-t border-slate-200">
      <p>{{ $t('common.app.copyright') }}</p>
    </footer>
  </div>
</template>
