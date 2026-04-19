<script setup lang="ts">
import Dialog from 'primevue/dialog'
import { useEprWizardStore } from '~/stores/eprWizard'
import WizardStepper from './WizardStepper.vue'

/**
 * Story 10.2 — Registry "Browse" KF-code wizard dialog.
 *
 * Hosts EprWizardStepper in a PrimeVue Dialog running in resolve-only mode. The
 * dialog:
 *  • opens the wizard via `startResolveOnly()` when `visible` transitions false→true;
 *  • watches `lastResolvedKfCode` and re-emits the resolved trio to the parent;
 *  • calls `cancelWizard()` on close so state is clean for the next open.
 *
 * The wizard's Step 4 footer branches on `isResolveOnlyMode` to show [Cancel][Use
 * this code] (see WizardStepper.vue); clicking "Use this code" calls
 * `store.resolveAndClose()` which writes `lastResolvedKfCode` synchronously —
 * this component's watcher picks it up on the next tick and emits to the caller,
 * exactly mirroring the `lastConfirmSuccess` tick-ordering contract in
 * `stores/eprWizard.ts`.
 */

const { t } = useI18n()
const wizardStore = useEprWizardStore()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'resolved', payload: { kfCode: string, materialClassification: string, feeRate: number }): void
}>()

// Open the wizard every time the dialog becomes visible. `onMounted` would fire
// only once even if the parent keeps this wrapper mounted across opens (which it
// does — see pages/registry/[id].vue binding). `watch(props.visible)` is the
// correct hook for a wrapper that toggles visibility without remounting.
watch(() => props.visible, (isVisible) => {
  if (isVisible) {
    wizardStore.startResolveOnly()
  }
})

// Watch for resolve-only success: resolveAndClose() writes the payload just
// before _resetWizardState() clears working state. We read it, emit to parent,
// cancel the wizard (which $reset()s the store and clears lastResolvedKfCode),
// then close the dialog — in that exact order so the parent sees `resolved`
// before `update:visible(false)`.
watch(() => wizardStore.lastResolvedKfCode, (payload) => {
  if (!payload) return
  emit('resolved', payload)
  wizardStore.cancelWizard()
  emit('update:visible', false)
})

// Close the dialog when the wizard deactivates (e.g. user clicked an in-wizard
// Cancel button — both the Step 4 "Cancel" and the inline bottom Cancel call
// `wizardStore.cancelWizard()`, which $reset()s the store and sets
// `isActive=false`). Without this, the dialog stays open showing an empty
// stepper shell.
watch(() => wizardStore.isActive, (active, wasActive) => {
  if (!active && wasActive && props.visible) {
    emit('update:visible', false)
  }
})

function close() {
  emit('update:visible', false)
  wizardStore.cancelWizard()
}
</script>

<template>
  <Dialog
    :visible="visible"
    modal
    append-to="body"
    :header="t('registry.browse.title')"
    :pt="{ root: { class: 'z-[75]' } }"
    :style="{ width: '720px' }"
    :breakpoints="{ '768px': '100vw' }"
    data-testid="kf-wizard-dialog"
    @update:visible="(v: boolean) => { if (!v) close() }"
    @hide="close"
  >
    <WizardStepper />
  </Dialog>
</template>
