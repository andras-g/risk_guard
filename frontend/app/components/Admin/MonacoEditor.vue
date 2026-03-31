<template>
  <ClientOnly>
    <div ref="editorContainer" class="h-96 border border-surface-200 rounded" />
    <template #fallback>
      <Skeleton height="24rem" />
    </template>
  </ClientOnly>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import Skeleton from 'primevue/skeleton'

const props = defineProps<{ modelValue: string; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const editorContainer = ref<HTMLElement | null>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let monacoEditor: any | null = null

onMounted(async () => {
  const monaco = await import('monaco-editor')
  self.MonacoEnvironment = {
    getWorker: () => new Worker(
      new URL('monaco-editor/esm/vs/editor/editor.worker', import.meta.url)
    ),
  }
  monacoEditor = monaco.editor.create(editorContainer.value!, {
    value: props.modelValue,
    language: 'json',
    theme: 'vs-light',
    automaticLayout: true,
    readOnly: props.readonly ?? false,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 13,
  })
  monacoEditor.onDidChangeModelContent(() => {
    emit('update:modelValue', monacoEditor!.getValue())
  })
})

watch(() => props.modelValue, (val) => {
  if (monacoEditor && monacoEditor.getValue() !== val) {
    monacoEditor.setValue(val)
  }
})

onBeforeUnmount(() => {
  monacoEditor?.dispose()
})
</script>
