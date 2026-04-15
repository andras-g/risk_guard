<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Popover from 'primevue/popover'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import { useTierGate } from '~/composables/auth/useTierGate'
import { useRegistry } from '~/composables/api/useRegistry'
import { useClassifier } from '~/composables/api/useClassifier'
import type { ClassifyResponse } from '~/composables/api/useClassifier'
import { useRegistryStore } from '~/stores/registry'
import { useApiError } from '~/composables/api/useApiError'
import { useUnits, DEFAULT_UNIT } from '~/composables/registry/useUnits'
import KfCodeInput from '~/components/registry/KfCodeInput.vue'
import type {
  ProductResponse,
  RegistryAuditEntryResponse,
} from '~/composables/api/useRegistry'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toast = useToast()
const { mapErrorType } = useApiError()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const { getProduct, createProduct, updateProduct, getAuditLog } = useRegistry()
const { classify } = useClassifier()
const registryStore = useRegistryStore()
const { options: unitOptionsBase } = useUnits()

const id = computed(() => route.params.id as string)
const isNew = computed(() => id.value === 'new')

// ─── Product state ─────────────────────────────────────────────────────────────
const product = ref<ProductResponse | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)

// ─── Form fields ───────────────────────────────────────────────────────────────
const articleNumber = ref<string>('')
const name = ref<string>('')
const vtsz = ref<string>('')
const primaryUnit = ref<string>(DEFAULT_UNIT)
const status = ref<'ACTIVE' | 'ARCHIVED' | 'DRAFT'>('ACTIVE')

// Decision 9.5-R1: option (c) — remove legacy values (e.g. 'pcs') from the
// dropdown entirely. The DB value roundtrips invisibly on save; the user cannot
// select 'pcs' on any new or existing product. Use the canonical list directly.
const unitOptions = computed(() => unitOptionsBase.value)

// ─── Validation ────────────────────────────────────────────────────────────────
const nameTouched = ref(false)
const nameError = ref('')
const componentsError = ref('')

function validateName(): string {
  if (!name.value?.trim()) return t('registry.form.validation.nameRequired')
  return ''
}

function validateComponents(): string {
  if (components.value.length === 0) {
    return t('registry.form.validation.componentsRequired')
  }
  for (const c of components.value) {
    if (c.weightPerUnitKg == null) {
      return t('registry.form.validation.weightRequired')
    }
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

// ─── Classifier (Story 9.3, hoisted Popover per 9.5 AC #5) ─────────────────────
// The Popover is a single instance at the page root (appendTo=body) to escape
// the DataTable's stacking + overflow context. We target the currently-active
// row by _tempId.
const classifyPopover = ref<InstanceType<typeof Popover> | null>(null)
const classifyLoading = ref<Record<string, boolean>>({})
const activeClassifyTempId = ref<string | null>(null)
const activeClassifySuggestion = ref<ClassifyResponse | null>(null)
// Element that opened the popover — used to restore focus on close (WCAG 2.4.3).
const classifyOpener = ref<HTMLElement | null>(null)

async function suggestKfCode(event: Event, comp: EditableComponent) {
  if (!name.value?.trim()) return
  classifyLoading.value[comp._tempId] = true
  const opener = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  try {
    const result = await classify({ productName: name.value, vtsz: vtsz.value || null })
    // Show only HIGH or MEDIUM suggestions (not LOW/empty)
    const topSuggestion = result.suggestions[0]
    if (topSuggestion && result.confidence !== 'LOW') {
      activeClassifyTempId.value = comp._tempId
      activeClassifySuggestion.value = result
      classifyOpener.value = opener
      classifyPopover.value?.show(event)
    }
    else {
      toast.add({
        severity: 'info',
        summary: t('registry.classify.noSuggestion'),
        life: 3000,
      })
    }
  }
  catch {
    toast.add({
      severity: 'warn',
      summary: t('registry.classify.error'),
      life: 3000,
    })
  }
  finally {
    classifyLoading.value[comp._tempId] = false
  }
}

function acceptSuggestion() {
  const tempId = activeClassifyTempId.value
  const result = activeClassifySuggestion.value
  if (!tempId || !result) return
  const comp = components.value.find(c => c._tempId === tempId)
  const top = result.suggestions[0]
  if (!comp || !top) return
  comp.kfCode = top.kfCode
  comp.classificationSource = result.strategy === 'VTSZ_PREFIX' ? 'VTSZ_FALLBACK' : 'AI_SUGGESTED_CONFIRMED'
  comp.classificationStrategy = result.strategy
  comp.classificationModelVersion = result.modelVersion ?? null
  closeClassifyPopover()
}

function closeClassifyPopover() {
  classifyPopover.value?.hide()
  resetClassifyState()
}

// Clears popover state and restores focus to the originating Suggest button.
// Called both from our explicit close paths and from PrimeVue's own @hide
// event (e.g. outside-click dismiss) so state can never leak when the user
// closes the overlay through any route.
function resetClassifyState() {
  activeClassifyTempId.value = null
  activeClassifySuggestion.value = null
  const opener = classifyOpener.value
  classifyOpener.value = null
  if (opener && typeof opener.focus === 'function') {
    opener.focus()
  }
}

function onClassifyKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && activeClassifyTempId.value) {
    closeClassifyPopover()
  }
}

// Close popover on route change so it never leaks between navigations.
// Watch `path` (not `fullPath`) so benign query/hash changes don't dismiss.
watch(() => route.path, () => {
  if (activeClassifyTempId.value) closeClassifyPopover()
})


// ─── Components ────────────────────────────────────────────────────────────────
interface EditableComponent {
  id: string | null
  materialDescription: string
  kfCode: string | null
  weightPerUnitKg: number | null
  componentOrder: number
  recyclabilityGrade: 'A' | 'B' | 'C' | 'D' | null
  recycledContentPct: number | null
  reusable: boolean | null
  supplierDeclarationRef: string | null
  // Story 9.3: AI classification provenance
  classificationSource: string | null
  classificationStrategy: string | null
  classificationModelVersion: string | null
  _tempId: string
}

const components = ref<EditableComponent[]>([])
const expandedRows = ref<Record<string, boolean>>({})

function newComponent(): EditableComponent {
  return {
    id: null,
    materialDescription: '',
    kfCode: null,
    weightPerUnitKg: null,
    componentOrder: components.value.length,
    recyclabilityGrade: null,
    recycledContentPct: null,
    reusable: null,
    supplierDeclarationRef: null,
    classificationSource: null,
    classificationStrategy: null,
    classificationModelVersion: null,
    _tempId: crypto.randomUUID(),
  }
}

function addComponent() {
  components.value.push(newComponent())
  if (componentsError.value) componentsError.value = ''
}

function removeComponent(index: number) {
  const removed = components.value.splice(index, 1)
  if (removed.length > 0) {
    const tempId = removed[0]._tempId
    delete classifyLoading.value[tempId]
    delete expandedRows.value[tempId]
  }
  reorderComponents()
  toast.add({
    severity: 'info',
    summary: t('registry.form.componentRemoved'),
    life: 2500,
  })
}

function moveUp(index: number) {
  if (index === 0) return
  const tmp = components.value[index - 1]
  components.value[index - 1] = components.value[index]
  components.value[index] = tmp
  reorderComponents()
}

function moveDown(index: number) {
  if (index >= components.value.length - 1) return
  const tmp = components.value[index + 1]
  components.value[index + 1] = components.value[index]
  components.value[index] = tmp
  reorderComponents()
}

function reorderComponents() {
  components.value.forEach((c, i) => { c.componentOrder = i })
}

// ─── Status options ────────────────────────────────────────────────────────────
const statusOptions = computed(() => [
  { label: t('registry.status.ACTIVE'), value: 'ACTIVE' as const },
  { label: t('registry.status.DRAFT'), value: 'DRAFT' as const },
  { label: t('registry.status.ARCHIVED'), value: 'ARCHIVED' as const },
])

const recyclabilityOptions = computed(() => [
  { label: 'A', value: 'A' as const },
  { label: 'B', value: 'B' as const },
  { label: 'C', value: 'C' as const },
  { label: 'D', value: 'D' as const },
])

const reusableOptions = computed(() => [
  { label: t('registry.form.reusableUnknown'), value: null },
  { label: t('registry.form.reusableYes'), value: true },
  { label: t('registry.form.reusableNo'), value: false },
])

// ─── Load ──────────────────────────────────────────────────────────────────────
async function loadProduct() {
  if (isNew.value) {
    components.value = []
    registryStore.setEditProduct(null)
    return
  }
  isLoading.value = true
  try {
    product.value = await getProduct(id.value)
    registryStore.setEditProduct(product.value)
    articleNumber.value = product.value.articleNumber ?? ''
    name.value = product.value.name
    vtsz.value = product.value.vtsz ?? ''
    primaryUnit.value = product.value.primaryUnit
    status.value = product.value.status
    components.value = product.value.components.map(c => ({
      id: c.id,
      materialDescription: c.materialDescription,
      kfCode: c.kfCode,
      weightPerUnitKg: c.weightPerUnitKg,
      componentOrder: c.componentOrder,
      recyclabilityGrade: c.recyclabilityGrade,
      recycledContentPct: c.recycledContentPct,
      reusable: c.reusable,
      supplierDeclarationRef: c.supplierDeclarationRef,
      classificationSource: null,
      classificationStrategy: null,
      classificationModelVersion: null,
      _tempId: c.id,
    }))
  }
  catch (err: unknown) {
    const e = err as { data?: { type?: string } }
    toast.add({ severity: 'error', summary: mapErrorType(e.data?.type), life: 4000 })
    router.push('/registry')
  }
  finally {
    isLoading.value = false
  }
}

// ─── Save ──────────────────────────────────────────────────────────────────────
async function save() {
  if (isSaving.value) return
  nameError.value = validateName()
  componentsError.value = validateComponents()
  if (nameError.value || componentsError.value) {
    nameTouched.value = true
    // componentsError is surfaced inline via <Message> banner above the
    // components section — do not use a toast (would disappear and leave
    // users guessing why the save failed).
    return
  }

  isSaving.value = true
  try {
    const body = {
      articleNumber: articleNumber.value || null,
      name: name.value,
      vtsz: vtsz.value || null,
      primaryUnit: primaryUnit.value,
      status: status.value,
      components: components.value.map(c => ({
        id: c.id,
        materialDescription: c.materialDescription,
        kfCode: c.kfCode,
        weightPerUnitKg: c.weightPerUnitKg as number,
        componentOrder: c.componentOrder,
        recyclabilityGrade: c.recyclabilityGrade,
        recycledContentPct: c.recycledContentPct,
        reusable: c.reusable,
        supplierDeclarationRef: c.supplierDeclarationRef,
        classificationSource: c.classificationSource,
        classificationStrategy: c.classificationStrategy,
        classificationModelVersion: c.classificationModelVersion,
      })),
    }

    let saved: ProductResponse
    if (isNew.value) {
      saved = await createProduct(body)
    }
    else {
      saved = await updateProduct(id.value, body)
    }

    toast.add({ severity: 'success', summary: t('registry.form.saved'), life: 3000 })
    if (isNew.value) {
      router.push(`/registry/${saved.id}`)
    }
    else {
      product.value = saved
      registryStore.setEditProduct(saved)
    }
  }
  catch (err: unknown) {
    const e = err as { data?: { type?: string } }
    toast.add({ severity: 'error', summary: mapErrorType(e.data?.type), life: 4000 })
  }
  finally {
    isSaving.value = false
  }
}

// ─── Audit log ─────────────────────────────────────────────────────────────────
const auditEntries = ref<RegistryAuditEntryResponse[]>([])
const auditTotal = ref(0)
const auditPage = ref(0)
const auditLoading = ref(false)

async function loadAuditLog() {
  if (isNew.value) return
  auditLoading.value = true
  try {
    const result = await getAuditLog(id.value, auditPage.value, 20)
    auditEntries.value = result.items
    auditTotal.value = Number(result.total)
  }
  catch {
    // Non-critical — audit drawer failure should not block editing
  }
  finally {
    auditLoading.value = false
  }
}

function auditSourceLabel(source: RegistryAuditEntryResponse['source']): string {
  return t(`registry.audit.source.${source}`)
}

onMounted(async () => {
  if (import.meta.client) window.addEventListener('keydown', onClassifyKeydown)
  await loadProduct()
  if (!isNew.value) loadAuditLog()
})

onBeforeUnmount(() => {
  if (import.meta.client) window.removeEventListener('keydown', onClassifyKeydown)
  registryStore.setEditProduct(null)
})
</script>

<template>
  <!-- Tier Gate: PRO_EPR required (AC 12) -->
  <div
    v-if="!hasAccess"
    class="flex flex-col items-center justify-center py-16 text-center max-w-lg mx-auto"
    data-testid="registry-tier-gate"
  >
    <i class="pi pi-lock text-6xl text-slate-300 mb-4" aria-hidden="true" />
    <h2 class="text-xl font-bold text-slate-800 mb-2">
      {{ t('registry.title') }}
    </h2>
    <p class="text-sm text-slate-500 mb-4">
      {{ t('epr.materialLibrary.tierGate.description', { tier: tierName }) }}
    </p>
  </div>

  <div v-else class="p-4 pb-24 flex flex-col gap-6 max-w-6xl mx-auto">
    <!-- Header -->
    <div class="flex items-center gap-3">
      <Button
        icon="pi pi-arrow-left"
        :aria-label="t('registry.actions.back')"
        text
        @click="router.push('/registry')"
      />
      <h1 class="text-2xl font-semibold">
        {{ isNew ? t('registry.form.titleCreate') : t('registry.form.titleEdit') }}
      </h1>
    </div>

    <!-- Mandatory fields legend -->
    <p class="text-sm text-slate-500" data-testid="registry-mandatory-legend">
      <span class="text-red-500" aria-hidden="true">*</span>
      {{ t('registry.form.mandatoryLegend') }}
    </p>

    <!-- Product fields -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div class="flex flex-col gap-1">
        <label for="reg-name" class="font-medium">{{ t('registry.form.name') }} *</label>
        <InputText
          id="reg-name"
          v-model="name"
          :aria-describedby="nameError ? 'reg-name-error' : undefined"
          :aria-invalid="!!nameError"
          @blur="onNameBlur"
        />
        <small v-if="nameError" id="reg-name-error" role="alert" class="text-red-500">{{ nameError }}</small>
      </div>

      <div class="flex flex-col gap-1">
        <label for="reg-article" class="font-medium">{{ t('registry.form.articleNumber') }}</label>
        <InputText id="reg-article" v-model="articleNumber" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="reg-vtsz" class="font-medium">{{ t('registry.form.vtsz') }}</label>
        <InputText id="reg-vtsz" v-model="vtsz" />
      </div>

      <div class="flex flex-col gap-1">
        <label for="reg-unit" class="font-medium">{{ t('registry.form.primaryUnit') }}</label>
        <Select
          id="reg-unit"
          v-model="primaryUnit"
          :options="unitOptions"
          option-label="label"
          option-value="value"
        />
      </div>

      <div class="flex flex-col gap-1">
        <label for="reg-status" class="font-medium">{{ t('registry.form.status') }}</label>
        <Select
          id="reg-status"
          v-model="status"
          :options="statusOptions"
          option-label="label"
          option-value="value"
        />
      </div>
    </div>

    <!-- Components -->
    <div class="flex flex-col gap-3">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('registry.form.components') }} *</h2>
        <Button
          :label="t('registry.form.addComponent')"
          icon="pi pi-plus"
          size="small"
          @click="addComponent"
        />
      </div>

      <!-- Inline validation banner (section-level, does not auto-dismiss like a toast) -->
      <Message
        v-if="componentsError"
        severity="error"
        :closable="false"
        data-testid="registry-components-error"
      >
        {{ componentsError }}
      </Message>

      <!-- Empty-state CTA shown when there are no components yet -->
      <div
        v-if="components.length === 0"
        class="border border-dashed rounded-lg p-6 flex flex-col items-center gap-3 text-center bg-slate-50"
        data-testid="registry-components-empty"
      >
        <p class="text-sm text-slate-600">{{ t('registry.form.noComponents.cta') }}</p>
        <Button
          :label="t('registry.form.addComponent')"
          icon="pi pi-plus"
          @click="addComponent"
        />
      </div>

      <DataTable
        v-else
        v-model:expanded-rows="expandedRows"
        :value="components"
        data-key="_tempId"
        responsive-layout="stack"
        breakpoint="1024px"
        class="border rounded-lg"
      >
        <Column :expander="true" style="width: 3rem">
          <template #header>
            <span class="text-xs text-slate-500">{{ t('registry.form.ppwrExpand') }}</span>
          </template>
        </Column>

        <Column :header="t('registry.form.componentOrder')" style="width: 80px">
          <template #body="{ index }">
            <div class="flex flex-col gap-1">
              <Button
                icon="pi pi-chevron-up"
                :aria-label="t('registry.form.moveUp')"
                size="small"
                text
                :disabled="index === 0"
                @click="moveUp(index)"
              />
              <Button
                icon="pi pi-chevron-down"
                :aria-label="t('registry.form.moveDown')"
                size="small"
                text
                :disabled="index === components.length - 1"
                @click="moveDown(index)"
              />
            </div>
          </template>
        </Column>

        <Column :header="t('registry.form.materialDescription')">
          <template #body="{ data }">
            <InputText
              v-model="data.materialDescription"
              class="w-full"
              :aria-label="t('registry.form.materialDescription')"
            />
          </template>
        </Column>

        <Column :header="t('registry.form.kfCode')" style="width: 220px">
          <template #body="{ data, index }">
            <div class="flex items-center gap-1">
              <KfCodeInput
                :id="`kf-${index}`"
                v-model="data.kfCode"
                @update:model-value="() => {
                  if (data.classificationSource === 'AI_SUGGESTED_CONFIRMED') {
                    data.classificationSource = 'AI_SUGGESTED_EDITED'
                  }
                }"
              />
              <!-- Tooltip must be on the wrapper span because browsers suppress
                   pointer events on :disabled elements, preventing v-tooltip
                   mouseenter from firing on the button itself (P7). -->
              <span
                v-tooltip.bottom="name ? t('registry.classify.tooltipEnabled') : t('registry.classify.tooltipDisabled')"
                class="inline-flex"
              >
                <Button
                  icon="pi pi-sparkles"
                  :label="t('registry.classify.suggestShort')"
                  :aria-label="name ? t('registry.classify.suggest') : t('registry.classify.tooltipDisabled')"
                  size="small"
                  :disabled="!name"
                  :loading="classifyLoading[data._tempId]"
                  :data-testid="`suggest-kf-${index}`"
                  @click="suggestKfCode($event, data)"
                />
              </span>
            </div>
          </template>
        </Column>

        <Column :header="t('registry.form.weightPerUnitKg')" style="width: 140px">
          <template #body="{ data }">
            <InputNumber
              v-model="data.weightPerUnitKg"
              :min="0"
              :fraction-digits="6"
              :use-grouping="false"
              :aria-label="t('registry.form.weightPerUnitKg')"
              class="w-full"
            />
          </template>
        </Column>

        <Column style="width: 60px">
          <template #body="{ index }">
            <Button
              icon="pi pi-trash"
              :aria-label="t('registry.form.removeComponent')"
              size="small"
              text
              severity="danger"
              @click="removeComponent(index)"
            />
          </template>
        </Column>

        <template #expansion="{ data }">
          <div class="bg-slate-50 p-4">
            <p class="text-xs uppercase tracking-wide text-slate-500 mb-3">
              {{ t('registry.form.ppwrExpand') }}
            </p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="flex flex-col gap-1">
                <label :for="`grade-${data._tempId}`">{{ t('registry.form.recyclabilityGrade') }}</label>
                <Select
                  :id="`grade-${data._tempId}`"
                  v-model="data.recyclabilityGrade"
                  :options="recyclabilityOptions"
                  option-label="label"
                  option-value="value"
                  :placeholder="t('registry.form.none')"
                />
              </div>
              <div class="flex flex-col gap-1">
                <label :for="`pct-${data._tempId}`">{{ t('registry.form.recycledContentPct') }}</label>
                <InputNumber
                  :id="`pct-${data._tempId}`"
                  v-model="data.recycledContentPct"
                  :min="0"
                  :max="100"
                  suffix="%"
                />
              </div>
              <div class="flex flex-col gap-1">
                <label :for="`reusable-${data._tempId}`">{{ t('registry.form.reusable') }}</label>
                <Select
                  :id="`reusable-${data._tempId}`"
                  v-model="data.reusable"
                  :options="reusableOptions"
                  option-label="label"
                  option-value="value"
                />
              </div>
              <div class="flex flex-col gap-1">
                <label :for="`decl-${data._tempId}`">{{ t('registry.form.supplierDeclarationRef') }}</label>
                <InputText :id="`decl-${data._tempId}`" v-model="data.supplierDeclarationRef" />
              </div>
            </div>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- Audit log (only on existing products) -->
    <div v-if="!isNew">
      <Accordion>
        <AccordionPanel value="audit">
          <AccordionHeader>{{ t('registry.audit.title') }}</AccordionHeader>
          <AccordionContent>
            <div v-if="auditLoading" class="py-4 text-center text-gray-400">{{ t('common.states.loading') }}</div>
            <div v-else-if="auditEntries.length === 0" class="py-4 text-center text-gray-400">
              {{ t('registry.audit.empty') }}
            </div>
            <DataTable
              v-else
              :value="auditEntries"
              data-key="id"
              class="text-sm"
            >
              <Column :header="t('registry.audit.timestamp')">
                <template #body="{ data }">{{ new Date(data.timestamp).toLocaleString() }}</template>
              </Column>
              <Column :header="t('registry.audit.source')">
                <template #body="{ data }">
                  <Tag :value="auditSourceLabel(data.source)" severity="info" />
                </template>
              </Column>
              <Column field="fieldChanged" :header="t('registry.audit.field')" />
              <Column :header="t('registry.audit.diff')">
                <template #body="{ data }">
                  <span class="text-red-500 line-through mr-1">{{ data.oldValue }}</span>
                  <span class="text-green-600">{{ data.newValue }}</span>
                </template>
              </Column>
            </DataTable>
          </AccordionContent>
        </AccordionPanel>
      </Accordion>
    </div>

    <!-- Sticky action bar at the bottom of the form (desktop ≥1024px only).
         Narrow viewports keep normal padding (no full-bleed edge-to-edge band). -->
    <div
      class="lg:sticky bottom-0 bg-white p-3 flex justify-end gap-2 lg:-mx-4 lg:border-t z-10"
      data-testid="registry-action-bar"
    >
      <Button
        :label="t('registry.actions.cancel')"
        icon="pi pi-times"
        text
        @click="router.push('/registry')"
      />
      <Button
        :label="t('registry.form.save')"
        icon="pi pi-check"
        :loading="isSaving"
        @click="save"
      />
    </div>

    <!-- Hoisted classifier Popover (AC #5): single instance, appendTo=body,
         z-index above the sidebar drawer layer to avoid the menu-stuck regression.
         Uses `pt.root.class` so the z-index lands on the teleported overlay root
         (a plain `class` on <Popover> applies to the component wrapper, which the
         teleport leaves behind when appendTo=body moves the overlay to document.body).
         The @hide listener mops up state even when PrimeVue auto-dismisses the
         overlay (e.g. outside-click), preventing activeClassifyTempId leak. -->
    <Popover
      ref="classifyPopover"
      append-to="body"
      :pt="{ root: { class: 'z-[60]' } }"
      @hide="resetClassifyState"
    >
      <div v-if="activeClassifySuggestion" class="flex flex-col gap-2 p-1 min-w-40">
        <div class="flex items-center gap-2">
          <span class="font-mono font-semibold text-lg">{{ activeClassifySuggestion.suggestions[0].kfCode }}</span>
          <Tag
            :value="t(`registry.classify.confidence.${activeClassifySuggestion.suggestions[0].score >= 0.8 ? 'HIGH' : 'MEDIUM'}`)"
            :severity="activeClassifySuggestion.suggestions[0].score >= 0.8 ? 'success' : 'warn'"
            size="small"
          />
        </div>
        <div
          v-if="activeClassifySuggestion.suggestions[0].suggestedComponentDescriptions.length"
          class="text-xs text-slate-500"
        >
          {{ activeClassifySuggestion.suggestions[0].suggestedComponentDescriptions.join(', ') }}
        </div>
        <Button
          :label="t('registry.classify.accept')"
          size="small"
          icon="pi pi-check"
          data-testid="accept-kf"
          @click="acceptSuggestion"
        />
      </div>
    </Popover>
  </div>
</template>
