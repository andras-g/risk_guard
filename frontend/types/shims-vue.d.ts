/**
 * TypeScript shim for *.vue SFC imports.
 *
 * The Vite/Vitest runtime handles .vue files via @vitejs/plugin-vue, but the
 * TypeScript language server needs this declaration to resolve `import X from './X.vue'`
 * in spec files and any non-Nuxt TypeScript context.
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, any>
  export default component
}
