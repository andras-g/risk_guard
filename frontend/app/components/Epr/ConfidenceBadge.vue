<script setup lang="ts">
const { t } = useI18n()

const props = defineProps<{
  confidence: string
  showReason?: boolean
  reason?: string
}>()

const severity = computed(() => {
  return props.confidence === 'HIGH' ? 'success' : 'warn'
})

const label = computed(() => {
  switch (props.confidence) {
    case 'HIGH': return t('epr.wizard.confidence.high')
    case 'MEDIUM': return t('epr.wizard.confidence.medium')
    case 'LOW': return t('epr.wizard.confidence.low')
    default: return props.confidence
  }
})

const icon = computed(() => {
  return props.confidence === 'LOW' ? 'pi pi-exclamation-triangle' : undefined
})
</script>

<template>
  <span data-testid="confidence-badge" class="inline-flex items-center gap-1">
    <Tag :severity="severity" :value="label" :icon="icon" data-testid="confidence-tag" />
    <span
      v-if="showReason && reason"
      class="text-sm text-surface-500"
      data-testid="confidence-reason"
    >
      {{ t(`epr.wizard.confidence.reason.${reason}`) }}
    </span>
  </span>
</template>
