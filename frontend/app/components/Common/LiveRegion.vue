<script setup lang="ts">
const props = withDefaults(defineProps<{
  mode?: 'polite' | 'assertive'
  srOnly?: boolean
}>(), {
  mode: 'polite',
  srOnly: false,
})
</script>

<template>
  <div
    :aria-live="props.mode"
    aria-atomic="true"
    :class="{ 'sr-only': props.srOnly }"
    data-testid="live-region"
  >
    <slot />
  </div>
</template>
