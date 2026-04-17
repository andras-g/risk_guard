<script setup lang="ts">
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import KfCodeInput from '~/components/registry/KfCodeInput.vue'
import type { BootstrapCandidateResponse, BootstrapApproveRequestBody } from '~/composables/api/useBootstrap'

const { t } = useI18n()

const props = defineProps<{
  candidate: BootstrapCandidateResponse | null
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'approved', candidate: BootstrapCandidateResponse): void
}>()

// ─── Form state ───────────────────────────────────────────────────────────────

const name = ref('')
const vtsz = ref<string | null>(null)
const articleNumber = ref<string | null>(null)
const primaryUnit = ref('')
const productStatus = ref<'ACTIVE' | 'ARCHIVED' | 'DRAFT'>('ACTIVE')

interface SuggestedComponent {
  layer?: string
  kfCode?: string
  description?: string
  weightEstimateKg?: number
  unitsPerProduct?: number
  score?: number
}

interface ComponentRow {
  materialDescription: string
  kfCode: string | null
  weightPerUnitKg: number | null
  unitsPerProduct: number
  _lowWeightConfidence: boolean
}

const components = ref<ComponentRow[]>([])

const statusOptions = computed(() => [
  { label: t('registry.status.ACTIVE'), value: 'ACTIVE' as const },
  { label: t('registry.status.DRAFT'), value: 'DRAFT' as const },
])

// ─── Validation state ─────────────────────────────────────────────────────────

const nameTouched = ref(false)
const nameError = ref('')

function validateName(): string {
  if (!name.value || name.value.trim() === '') {
    return t('registry.form.validation.nameRequired')
  }
  return ''
}

function onNameBlur() {
  nameTouched.value = true
  nameError.value = validateName()
}

watch(name, () => {
  if (nameTouched.value) nameError.value = validateName()
})

const isSubmitting = ref(false)
const submitError = ref<string | null>(null)

// ─── Populate from candidate ──────────────────────────────────────────────────

watch(() => props.candidate, (c) => {
  if (!c) return
  name.value = c.productName || ''
  vtsz.value = c.vtsz || null
  articleNumber.value = null
  primaryUnit.value = c.unitOfMeasure || 'db'
  productStatus.value = 'ACTIVE'
  nameTouched.value = false
  nameError.value = ''
  submitError.value = null

  // Multi-component pre-population from suggestedComponents JSON (Story 9.6)
  let parsed: SuggestedComponent[] = []
  if (c.suggestedComponents) {
    try { parsed = JSON.parse(c.suggestedComponents) } catch { /* ignore */ }
  }

  if (parsed.length > 0) {
    components.value = parsed.map((s, i) => ({
      materialDescription: s.description || c.productName,
      kfCode: s.kfCode || null,
      weightPerUnitKg: s.weightEstimateKg ?? null,
      unitsPerProduct: s.unitsPerProduct ?? 1,
      _lowWeightConfidence: s.weightEstimateKg != null && (s.score == null || s.score < 0.7),
    }))
  }
  else if (c.suggestedKfCode) {
    components.value = [{
      materialDescription: c.productName,
      kfCode: c.suggestedKfCode,
      weightPerUnitKg: null,
      unitsPerProduct: 1,
      _lowWeightConfidence: false,
    }]
  }
  else {
    components.value = [{
      materialDescription: '',
      kfCode: null,
      weightPerUnitKg: null,
      unitsPerProduct: 1,
      _lowWeightConfidence: false,
    }]
  }
}, { immediate: true })

// ─── Component rows ───────────────────────────────────────────────────────────

function addComponent() {
  components.value.push({ materialDescription: '', kfCode: null, weightPerUnitKg: null, unitsPerProduct: 1, _lowWeightConfidence: false })
}

function removeComponent(idx: number) {
  if (components.value.length > 1) {
    components.value.splice(idx, 1)
  }
}

// ─── Submit ───────────────────────────────────────────────────────────────────

const { approveCandidate } = useBootstrap()

async function onConfirm() {
  nameError.value = validateName()
  if (nameError.value) {
    nameTouched.value = true
    return
  }

  if (!props.candidate) return

  const body: BootstrapApproveRequestBody = {
    articleNumber: articleNumber.value || null,
    name: name.value.trim(),
    vtsz: vtsz.value || null,
    primaryUnit: primaryUnit.value,
    status: productStatus.value,
    components: components.value.map((c, i) => ({
      materialDescription: c.materialDescription,
      kfCode: c.kfCode,
      weightPerUnitKg: c.weightPerUnitKg ?? 0,
      componentOrder: i,
      unitsPerProduct: c.unitsPerProduct,
    })),
  }

  isSubmitting.value = true
  submitError.value = null
  try {
    const updated = await approveCandidate(props.candidate.id, body)
    emit('approved', updated)
    emit('close')
  }
  catch {
    submitError.value = t('registry.bootstrap.approveError')
  }
  finally {
    isSubmitting.value = false
  }
}

function onClose() {
  emit('close')
}
</script>

<template>
  <Dialog
    :visible="visible"
    :header="t('registry.bootstrap.approveDialog.title')"
    :closable="true"
    modal
    :style="{ width: '720px' }"
    @hide="onClose"
  >
    <div class="flex flex-col gap-4">
      <!-- Product name -->
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium" for="ba-name">{{ t('registry.form.name') }}</label>
        <InputText
          id="ba-name"
          v-model="name"
          :aria-invalid="!!nameError"
          :aria-describedby="nameError ? 'ba-name-error' : undefined"
          @blur="onNameBlur"
        />
        <small v-if="nameError" id="ba-name-error" role="alert" class="text-red-500">
          {{ nameError }}
        </small>
      </div>

      <!-- Article number (optional) -->
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium" for="ba-article">{{ t('registry.form.articleNumber') }}</label>
        <InputText id="ba-article" v-model="articleNumber" />
      </div>

      <!-- VTSZ -->
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium" for="ba-vtsz">{{ t('registry.form.vtsz') }}</label>
        <InputText id="ba-vtsz" v-model="vtsz" />
      </div>

      <!-- Primary unit -->
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium" for="ba-unit">{{ t('registry.form.primaryUnit') }}</label>
        <InputText id="ba-unit" v-model="primaryUnit" />
      </div>

      <!-- Status -->
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium" for="ba-status">{{ t('registry.form.status') }}</label>
        <Select
          id="ba-status"
          v-model="productStatus"
          :options="statusOptions"
          option-label="label"
          option-value="value"
        />
      </div>

      <!-- Packaging components -->
      <div class="flex flex-col gap-2">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ t('registry.form.components') }}</span>
          <Button
            :label="t('registry.form.addComponent')"
            icon="pi pi-plus"
            size="small"
            text
            @click="addComponent"
          />
        </div>
        <div
          v-for="(comp, idx) in components"
          :key="idx"
          class="border rounded p-3 flex flex-col gap-2"
        >
          <!-- Row 1: Material description + KF code -->
          <div class="grid grid-cols-2 gap-2">
            <div class="flex flex-col gap-1">
              <label :for="`ba-mat-${idx}`" class="text-xs font-medium">
                {{ t('registry.form.materialDescription') }}
              </label>
              <InputText :id="`ba-mat-${idx}`" v-model="comp.materialDescription" />
            </div>
            <div class="flex flex-col gap-1">
              <label :for="`ba-kf-${idx}`" class="text-xs font-medium">
                {{ t('registry.form.kfCode') }}
              </label>
              <KfCodeInput :id="`ba-kf-${idx}`" v-model="comp.kfCode" />
            </div>
          </div>
          <!-- Row 2: Weight + Units + Remove -->
          <div class="flex gap-2 items-end">
            <div class="flex flex-col gap-1 flex-1">
              <label :for="`ba-wt-${idx}`" class="text-xs font-medium">
                {{ t('registry.form.weightPerUnitKg') }}
              </label>
              <InputNumber
                :id="`ba-wt-${idx}`"
                v-model="comp.weightPerUnitKg"
                :min="0"
                :min-fraction-digits="0"
                :max-fraction-digits="6"
                :class="{ 'italic': comp._lowWeightConfidence }"
                v-tooltip.top="comp._lowWeightConfidence ? t('registry.classify.weightEstimateTooltip') : undefined"
              />
            </div>
            <div class="flex flex-col gap-1 flex-1">
              <label :for="`ba-up-${idx}`" class="text-xs font-medium">
                {{ t('registry.form.unitsPerProduct') }}
              </label>
              <InputNumber
                :id="`ba-up-${idx}`"
                v-model="comp.unitsPerProduct"
                :min="1"
                :show-buttons="true"
              />
            </div>
            <Button
              v-if="components.length > 1"
              icon="pi pi-trash"
              :aria-label="t('registry.form.removeComponent')"
              severity="secondary"
              size="small"
              text
              @click="removeComponent(idx)"
            />
          </div>
        </div>
      </div>

      <!-- Submit error -->
      <small v-if="submitError" role="alert" class="text-red-500">{{ submitError }}</small>
    </div>

    <template #footer>
      <Button :label="t('common.actions.cancel')" severity="secondary" text @click="onClose" />
      <Button
        :label="t('registry.bootstrap.actions.approve')"
        :loading="isSubmitting"
        data-testid="approve-confirm"
        @click="onConfirm"
      />
    </template>
  </Dialog>
</template>
