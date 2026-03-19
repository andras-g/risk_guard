<template>
  <div>
    <NuxtLoadingIndicator color="#0ea5e9" />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <!-- Single global Toast instance — do NOT add <Toast /> in page components -->
    <Toast />
  </div>
</template>

<script setup lang="ts">
// Global app initialization
</script>
