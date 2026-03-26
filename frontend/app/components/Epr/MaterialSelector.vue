<script setup lang="ts">
import Skeleton from 'primevue/skeleton'
import type { WizardOption } from '~/types/epr'

defineProps<{
  options: WizardOption[]
  selectedCode: string | null
  isLoading: boolean
}>()

const emit = defineEmits<{
  select: [option: WizardOption]
}>()
</script>

<template>
  <!-- Loading skeleton -->
  <div
    v-if="isLoading"
    class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3"
    data-testid="material-selector-skeleton"
  >
    <Skeleton
      v-for="i in 4"
      :key="i"
      height="120px"
      border-radius="12px"
    />
  </div>

  <!-- Option cards grid -->
  <div
    v-else
    class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3"
    data-testid="material-selector-grid"
  >
    <button
      v-for="option in options"
      :key="option.code"
      type="button"
      class="flex flex-col items-start justify-center min-h-[120px] p-4 rounded-xl border-2 cursor-pointer transition-all duration-200 text-left"
      :class="[
        selectedCode === option.code
          ? 'border-[#15803D] bg-emerald-50 shadow-sm'
          : 'border-slate-200 bg-white hover:border-[#1e3a5f] hover:shadow-md',
      ]"
      :data-testid="`material-option-${option.code}`"
      @click="emit('select', option)"
    >
      <span class="text-sm font-mono text-slate-400 mb-1">{{ option.code }}</span>
      <span class="text-base font-medium text-slate-800">{{ option.label }}</span>
      <span
        v-if="option.description"
        class="text-sm text-slate-500 mt-1"
      >
        {{ option.description }}
      </span>
    </button>
  </div>
</template>
