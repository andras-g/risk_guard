<template>
  <div class="flex flex-col gap-2">
    <div class="flex items-center gap-2">
      <button
        type="button"
        :class="[
          'px-3 py-1 text-xs font-medium rounded border transition-colors',
          formatted
            ? 'bg-indigo-600 text-white border-indigo-600'
            : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-400 hover:text-indigo-600'
        ]"
        :title="formatError ?? undefined"
        data-testid="format-toggle"
        @click="toggleFormat"
      >
        { } Format
      </button>
      <span v-if="formatError" class="text-xs text-red-500">{{ formatError }}</span>
    </div>
    <textarea
      aria-label="JSON editor"
      :value="modelValue"
      :readonly="readonly"
      class="w-full h-96 border border-slate-200 rounded p-3 font-mono text-sm resize-y focus:outline-none focus:ring-2 focus:ring-indigo-400 bg-white text-slate-800"
      spellcheck="false"
      data-testid="json-editor"
      @input="onInput"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ modelValue: string; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const formatted = ref(false)
const formatError = ref<string | null>(null)

function toggleFormat() {
  formatError.value = null
  if (formatted.value) {
    formatted.value = false
    return
  }
  try {
    const pretty = JSON.stringify(JSON.parse(props.modelValue), null, 2)
    emit('update:modelValue', pretty)
    formatted.value = true
  } catch {
    formatError.value = 'Invalid JSON — fix syntax errors before formatting'
  }
}

function onInput(e: Event) {
  formatted.value = false
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
}
</script>
