<script setup lang="ts">
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'

defineProps<{
  context: 'filing' | 'registry'
}>()

const emit = defineEmits<{
  'bootstrap-completed': []
}>()

const { t } = useI18n()
const router = useRouter()

const showBootstrapDialog = ref(false)
const showHelpModal = ref(false)

function onCompleted() {
  showBootstrapDialog.value = false
  emit('bootstrap-completed')
}
</script>

<template>
  <div
    class="flex flex-col items-center justify-center py-16 gap-4 text-center max-w-lg mx-auto"
    data-testid="registry-onboarding-block"
  >
    <i class="pi pi-inbox text-5xl text-slate-400" aria-hidden="true" />
    <h2 class="text-xl font-semibold text-slate-800">
      {{ t('registry.onboarding.title') }}
    </h2>
    <p class="text-sm text-slate-500 max-w-md">
      {{ t(`registry.onboarding.body.${context}`) }}
    </p>
    <div class="flex flex-wrap gap-3 justify-center">
      <Button
        :label="t('registry.onboarding.ctaBootstrap')"
        icon="pi pi-cloud-download"
        data-testid="onboarding-cta-bootstrap"
        @click="showBootstrapDialog = true"
      />
      <Button
        :label="t('registry.onboarding.ctaManual')"
        icon="pi pi-plus"
        severity="secondary"
        data-testid="onboarding-cta-manual"
        @click="router.push('/registry/new')"
      />
    </div>
    <button
      class="text-sm text-indigo-600 hover:underline mt-1"
      data-testid="onboarding-help-link"
      type="button"
      @click="showHelpModal = true"
    >
      {{ t('registry.onboarding.helpLink') }}
    </button>
  </div>

  <!-- Bootstrap dialog (owned by this component) -->
  <RegistryInvoiceBootstrapDialog
    v-model:visible="showBootstrapDialog"
    @completed="onCompleted"
  />

  <!-- Help modal -->
  <Dialog
    v-model:visible="showHelpModal"
    :header="t('registry.onboarding.helpModal.title')"
    modal
    append-to="body"
    :style="{ width: '480px' }"
    data-testid="onboarding-help-modal"
  >
    <p class="text-sm text-slate-700 whitespace-pre-line">
      {{ t('registry.onboarding.helpModal.body') }}
    </p>
    <template #footer>
      <Button
        :label="t('registry.onboarding.helpModal.close')"
        @click="showHelpModal = false"
      />
    </template>
  </Dialog>
</template>
