<script setup lang="ts">
const { t: $t } = useI18n()
</script>

<template>
  <div class="min-h-screen flex flex-col bg-white text-slate-900">
    <CommonSkipLink />

    <!-- Header -->
    <header class="h-16 flex items-center justify-between px-6 border-b border-slate-200">
      <NuxtLink
        to="/"
        class="text-xl font-bold bg-gradient-to-r from-indigo-400 to-indigo-600 bg-clip-text text-transparent"
      >
        {{ $t('common.app.name') }}
      </NuxtLink>

      <nav
        :aria-label="$t('common.a11y.headerNav')"
        class="flex items-center gap-4"
      >
        <CommonLocaleSwitcher variant="light" />
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
      </nav>
    </header>

    <!-- Content (Gateway density: airy, relaxed) -->
    <main
      id="main-content"
      tabindex="-1"
      class="flex-1 leading-relaxed"
    >
      <slot />
    </main>

    <!-- Footer placeholder -->
    <footer class="py-8 text-center text-sm text-secondary-text border-t border-slate-200">
      <p>{{ $t('common.app.copyright') }}</p>
    </footer>
  </div>
</template>
