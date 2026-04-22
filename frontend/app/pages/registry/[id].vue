<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
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
import type { ClassifyResponse, KfSuggestionDto } from '~/composables/api/useClassifier'
import { useRegistryStore } from '~/stores/registry'
import { useApiError } from '~/composables/api/useApiError'
import { useUnits, DEFAULT_UNIT } from '~/composables/registry/useUnits'
import Dialog from 'primevue/dialog'
import Listbox from 'primevue/listbox'
import KfCodeInput from '~/components/registry/KfCodeInput.vue'
import KfCodeWizardDialog from '~/components/Epr/KfCodeWizardDialog.vue'
import { useMaterialTemplatePicker } from '~/composables/registry/useMaterialTemplatePicker'
import type { MaterialTemplateResponse } from '~/types/epr'
import type {
  ProductResponse,
  RegistryAuditEntryResponse,
} from '~/composables/api/useRegistry'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toast = useToast()
const { mapErrorType, mapErrorDetail } = useApiError()
const { hasAccess, tierName } = useTierGate('PRO_EPR')
const { getProduct, createProduct, updateProduct, getAuditLog, updateProductScope } = useRegistry()
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
// Story 10.11 AC #17 — per-product EPR scope. Default UNKNOWN on the client until the
// server response populates it; set on product load (useRegistry.getProduct already returns it).
const eprScope = ref<'FIRST_PLACER' | 'RESELLER' | 'UNKNOWN'>('UNKNOWN')
const savingEprScope = ref(false)

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
    if (!c.materialDescription?.trim()) {
      return t('registry.form.validation.materialRequired')
    }
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
      // Pass opener as explicit target — after the await, event.currentTarget
      // is null (browser clears it post-dispatch), so PrimeVue would fall back
      // to (0,0) positioning without the second argument.
      classifyPopover.value?.show(event, opener)
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

function acceptSuggestion(suggestion: KfSuggestionDto) {
  const tempId = activeClassifyTempId.value
  const result = activeClassifySuggestion.value
  if (!tempId || !result) return
  const comp = components.value.find(c => c._tempId === tempId)
  if (!comp) return
  comp.kfCode = suggestion.kfCode
  comp.materialDescription = suggestion.description || comp.materialDescription
  if (suggestion.weightEstimateKg != null) comp.weightPerUnitKg = suggestion.weightEstimateKg
  comp.itemsPerParent = suggestion.unitsPerProduct
  comp.classificationSource = result.strategy === 'VTSZ_PREFIX' ? 'VTSZ_FALLBACK' : 'AI_SUGGESTED_CONFIRMED'
  comp.classificationStrategy = result.strategy
  comp.classificationModelVersion = result.modelVersion ?? null
  closeClassifyPopover()
}

function acceptAllSuggestions() {
  const result = activeClassifySuggestion.value
  const tempId = activeClassifyTempId.value
  if (!result || result.suggestions.length === 0 || !tempId) return

  // Guard: if the target component was deleted between clicking Suggest and
  // Accept All, bail out entirely to avoid orphaned secondary/tertiary rows.
  const targetComp = components.value.find(c => c._tempId === tempId)
  if (!targetComp) {
    closeClassifyPopover()
    return
  }

  // The first suggestion replaces the current row
  const first = result.suggestions[0]
  acceptSuggestion(first)

  // Additional layers become new component rows
  for (let i = 1; i < result.suggestions.length; i++) {
    const s = result.suggestions[i]
    const comp = newComponent()
    comp.materialDescription = s.description || ''
    comp.kfCode = s.kfCode
    if (s.weightEstimateKg != null) comp.weightPerUnitKg = s.weightEstimateKg
    comp.itemsPerParent = s.unitsPerProduct ?? 1
    comp.classificationSource = result.strategy === 'VTSZ_PREFIX' ? 'VTSZ_FALLBACK' : 'AI_SUGGESTED_CONFIRMED'
    comp.classificationStrategy = result.strategy
    comp.classificationModelVersion = result.modelVersion ?? null
    components.value.push(comp)
  }
  reorderComponents()
}

function layerLabel(layer: string): string {
  return t(`registry.classify.layer.${layer}`)
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

// ─── Material-template picker (Story 10.1 AC #11) ──────────────────────────────
// Registry-scoped picker over the internal material-template library. The
// `useMaterialTemplatePicker` composable is the only frontend entry point to
// template data outside `components/registry/*`; ESLint rules in Task 10 enforce
// the boundary at build time.
const templatePicker = useMaterialTemplatePicker()
const templatePickerOpen = ref(false)
const templatePickerTempId = ref<string | null>(null)
const templatePickerLoading = ref(false)
const templatePickerSearch = ref('')
const templatePickerOptions = ref<MaterialTemplateResponse[]>([])
const templatePickerSelected = ref<string | null>(null)

// R3-P6: cache resolved template names so preloaded-product rows display the actual template
// name instead of the generic "Template" fallback before the picker is ever opened. Populated
// once on loadProduct for the subset of templates referenced by the product's components.
const resolvedTemplateNames = ref<Record<string, string>>({})

// ─── KF-code wizard "Browse" (Story 10.2) ──────────────────────────────────
// Resolve-only wizard dialog over `EprWizardStepper`. Opens per-row; writes the
// resolved KF-code back onto the row's `kfCode` + `classificationSource=MANUAL_WIZARD`.
// Does NOT link to a material template — that's the template-picker above.
const kfWizardOpen = ref(false)
const kfWizardTargetTempId = ref<string | null>(null)

function openKfWizard(comp: EditableComponent) {
  kfWizardTargetTempId.value = comp._tempId
  kfWizardOpen.value = true
  // The dialog itself calls wizardStore.startResolveOnly() on visible:true.
}

function onKfWizardResolved(payload: { kfCode: string, materialClassification: string, feeRate: number }) {
  const tempId = kfWizardTargetTempId.value
  if (!tempId) return
  const comp = components.value.find(c => c._tempId === tempId)
  if (!comp) return // row was deleted mid-wizard — drop the payload (AC #17)
  comp.kfCode = payload.kfCode
  comp.classificationSource = 'MANUAL_WIZARD'
  // Browse returns only kfCode + classification + feeRate. materialDescription,
  // weightPerUnitKg and itemsPerParent stay as the user entered them — the wizard
  // is a KF-code resolver, not a component generator.
  comp.classificationStrategy = null
  comp.classificationModelVersion = null
  kfWizardOpen.value = false
  kfWizardTargetTempId.value = null
}

function onKfWizardVisibleUpdate(v: boolean) {
  kfWizardOpen.value = v
  if (!v) kfWizardTargetTempId.value = null
}

function openTemplatePicker(tempId: string) {
  templatePickerTempId.value = tempId
  const comp = components.value.find(c => c._tempId === tempId)
  templatePickerSelected.value = comp?.materialTemplateId ?? null
  templatePickerSearch.value = ''
  templatePickerOpen.value = true
  loadTemplatePickerOptions()
}

function closeTemplatePicker() {
  templatePickerOpen.value = false
  templatePickerTempId.value = null
  templatePickerSelected.value = null
  templatePickerSearch.value = ''
  templatePickerOptions.value = []
}

async function loadTemplatePickerOptions() {
  templatePickerLoading.value = true
  try {
    const term = templatePickerSearch.value.trim()
    templatePickerOptions.value = term
      ? await templatePicker.search(term)
      : await templatePicker.list(0, 100)
  }
  catch {
    toast.add({ severity: 'warn', summary: t('registry.templatePicker.loadError'), life: 3000 })
  }
  finally {
    templatePickerLoading.value = false
  }
}

let templatePickerSearchTimer: ReturnType<typeof setTimeout> | null = null
watch(templatePickerSearch, () => {
  if (!templatePickerOpen.value) return
  if (templatePickerSearchTimer) clearTimeout(templatePickerSearchTimer)
  templatePickerSearchTimer = setTimeout(loadTemplatePickerOptions, 200)
})

// R3-P11: clear the debounce timer on unmount so `loadTemplatePickerOptions` cannot fire
// against an unmounted component (toast/store writes on a stale instance).
onBeforeUnmount(() => {
  if (templatePickerSearchTimer) {
    clearTimeout(templatePickerSearchTimer)
    templatePickerSearchTimer = null
  }
})

function confirmTemplateSelection() {
  const tempId = templatePickerTempId.value
  if (!tempId) return closeTemplatePicker()
  const comp = components.value.find(c => c._tempId === tempId)
  if (comp) comp.materialTemplateId = templatePickerSelected.value
  closeTemplatePicker()
}

function clearTemplateSelection() {
  templatePickerSelected.value = null
  confirmTemplateSelection()
}

async function createDraftFromSearch() {
  const draftName = templatePickerSearch.value.trim()
  if (!draftName) return
  templatePickerLoading.value = true
  try {
    const created = await templatePicker.createDraft({ name: draftName })
    templatePickerSelected.value = created.id
    templatePickerOptions.value = [created, ...templatePickerOptions.value]
    resolvedTemplateNames.value[created.id] = created.name
    toast.add({ severity: 'success', summary: t('registry.templatePicker.created'), life: 2500 })
  }
  catch {
    toast.add({ severity: 'warn', summary: t('registry.templatePicker.createError'), life: 3000 })
  }
  finally {
    templatePickerLoading.value = false
  }
}

function templatePickerLabel(comp: EditableComponent): string {
  if (!comp.materialTemplateId) return t('registry.form.pickTemplate')
  // R3-P6: prefer the name cached during loadProduct; fall back to picker's live options,
  // then to the generic label only if neither has seen this templateId yet.
  const cachedName = resolvedTemplateNames.value[comp.materialTemplateId]
  if (cachedName) return cachedName
  const match = templatePickerOptions.value.find(o => o.id === comp.materialTemplateId)
  if (match?.name) {
    resolvedTemplateNames.value[comp.materialTemplateId] = match.name
    return match.name
  }
  return t('registry.form.template')
}

/**
 * R3-P6: fetch the name of every template referenced by the loaded product so the picker
 * button renders the actual template name without requiring the dialog to be opened first.
 * Single `list` call is sufficient because `useMaterialTemplatePicker.list` returns the
 * full page; tenants with > 200 templates would benefit from an id-batched lookup endpoint
 * in a future story.
 */
async function resolveTemplateNamesForComponents() {
  const referencedIds = new Set(
    components.value
      .map(c => c.materialTemplateId)
      .filter((x): x is string => !!x && !resolvedTemplateNames.value[x])
  )
  if (referencedIds.size === 0) return
  try {
    const templates = await templatePicker.list(0, 200)
    for (const t of templates) {
      if (referencedIds.has(t.id)) {
        resolvedTemplateNames.value[t.id] = t.name
      }
    }
  }
  catch {
    // silent — label will fall back to the generic "Template" string; not worth a toast.
  }
}


// ─── Components ────────────────────────────────────────────────────────────────
interface EditableComponent {
  id: string | null
  materialDescription: string
  kfCode: string | null
  weightPerUnitKg: number | null
  componentOrder: number
  itemsPerParent: number
  wrappingLevel: number
  materialTemplateId: string | null
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
    itemsPerParent: 1,
    wrappingLevel: 1,
    materialTemplateId: null,
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

// Story 10.11 AC #17 — EPR scope SelectButton options
const eprScopeOptions = computed(() => [
  { label: t('registry.product.eprScope.values.firstPlacer'), value: 'FIRST_PLACER' as const },
  { label: t('registry.product.eprScope.values.reseller'), value: 'RESELLER' as const },
  { label: t('registry.product.eprScope.values.unknown'), value: 'UNKNOWN' as const },
])

function eprScopeSeverity(scope: string): 'success' | 'secondary' | 'warning' {
  if (scope === 'FIRST_PLACER') return 'success'
  if (scope === 'RESELLER') return 'secondary'
  return 'warning'
}

const eprScopeHelpText = computed(() => {
  if (eprScope.value === 'FIRST_PLACER') return t('registry.product.eprScope.helpText.firstPlacer')
  if (eprScope.value === 'RESELLER') return t('registry.product.eprScope.helpText.reseller')
  return t('registry.product.eprScope.helpText.unknown')
})

// Story 10.11 AC #17 — optimistic scope PATCH with rollback on error.
async function onEprScopeChange(newScope: 'FIRST_PLACER' | 'RESELLER' | 'UNKNOWN' | null) {
  if (isNew.value || !product.value || !newScope) return
  if (newScope === product.value.eprScope) return

  const previous = product.value.eprScope
  eprScope.value = newScope // optimistic
  savingEprScope.value = true
  try {
    const updated = await updateProductScope(product.value.id, newScope)
    product.value = updated
    registryStore.setEditProduct(updated)
    toast.add({
      severity: 'success',
      summary: t('registry.product.eprScope.saveSuccess'),
      life: 3000,
    })
  } catch (err) {
    eprScope.value = previous
    const serverKey = (err as { data?: { errorMessageKey?: string } })?.data?.errorMessageKey
    toast.add({
      severity: 'error',
      summary: serverKey ? t(serverKey) : t('registry.product.eprScope.saveError'),
      life: 5000,
    })
  } finally {
    savingEprScope.value = false
  }
}

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
    eprScope.value = product.value.eprScope ?? 'UNKNOWN'
    components.value = product.value.components.map(c => ({
      id: c.id,
      materialDescription: c.materialDescription,
      kfCode: c.kfCode,
      weightPerUnitKg: c.weightPerUnitKg,
      componentOrder: c.componentOrder,
      itemsPerParent: (c as { itemsPerParent?: number }).itemsPerParent
        ?? (c as { unitsPerProduct?: number }).unitsPerProduct ?? 1,
      wrappingLevel: (c as { wrappingLevel?: number }).wrappingLevel ?? 1,
      materialTemplateId: (c as { materialTemplateId?: string | null }).materialTemplateId ?? null,
      recyclabilityGrade: c.recyclabilityGrade,
      recycledContentPct: c.recycledContentPct,
      reusable: c.reusable,
      supplierDeclarationRef: c.supplierDeclarationRef,
      classificationSource: null,
      classificationStrategy: null,
      classificationModelVersion: null,
      _tempId: c.id,
    }))
    // R3-P6: resolve template names up-front so the Sablon button label renders the
    // template name immediately on product load (not just after the picker is opened).
    await resolveTemplateNamesForComponents()
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
      eprScope: eprScope.value,
      components: components.value.map(c => ({
        id: c.id,
        materialDescription: c.materialDescription,
        kfCode: c.kfCode,
        weightPerUnitKg: c.weightPerUnitKg as number,
        componentOrder: c.componentOrder,
        itemsPerParent: c.itemsPerParent,
        wrappingLevel: c.wrappingLevel,
        materialTemplateId: c.materialTemplateId,
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
    toast.add({ severity: 'error', summary: mapErrorDetail(err), life: 6000 })
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

    <!-- Story 10.11 AC #17 — EPR scope per SKU -->
    <div
      v-if="!isNew"
      class="flex flex-col gap-2 border rounded-lg p-4 bg-surface-50 dark:bg-surface-900"
      data-testid="product-editor-epr-scope"
    >
      <h2 class="text-lg font-semibold">{{ t('registry.product.eprScope.title') }}</h2>
      <div class="flex items-center gap-3 flex-wrap">
        <SelectButton
          :model-value="eprScope"
          :options="eprScopeOptions"
          option-label="label"
          option-value="value"
          :disabled="savingEprScope"
          data-testid="product-editor-epr-scope-select"
          @update:model-value="onEprScopeChange"
        />
        <Tag
          :severity="eprScopeSeverity(eprScope)"
          :value="t(`registry.product.eprScope.values.${eprScope === 'FIRST_PLACER' ? 'firstPlacer' : eprScope === 'RESELLER' ? 'reseller' : 'unknown'}`)"
          data-testid="product-editor-epr-scope-tag"
        />
      </div>
      <small class="text-color-secondary">{{ eprScopeHelpText }}</small>
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

        <Column :header="t('registry.form.kfCode')" style="min-width: 280px">
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
              <!-- Story 10.2: Browse button — opens resolve-only wizard dialog.
                   Never disabled (wizard drill-down works without a product name). -->
              <Button
                icon="pi pi-sitemap"
                :label="t('registry.browse.button')"
                size="small"
                :aria-label="t('registry.browse.tooltip')"
                :data-testid="`browse-kf-${index}`"
                @click="openKfWizard(data)"
              />
            </div>
          </template>
        </Column>

        <Column :header="t('registry.form.weightPerUnitKg')" style="width: 140px">
          <template #body="{ data }">
            <InputNumber
              v-model="data.weightPerUnitKg"
              :min="0"
              :max-fraction-digits="6"
              :use-grouping="false"
              :aria-label="t('registry.form.weightPerUnitKg')"
              class="w-full"
            />
          </template>
        </Column>

        <Column style="width: 130px">
          <template #header>
            <span
              v-tooltip.top="t('registry.form.unitsPerProductTooltip')"
              class="cursor-help border-b border-dotted border-slate-400"
            >
              {{ t('registry.form.unitsPerProduct') }}
            </span>
          </template>
          <template #body="{ data }">
            <InputNumber
              v-model="data.itemsPerParent"
              :min="0.0001"
              :min-fraction-digits="0"
              :max-fraction-digits="4"
              :show-buttons="true"
              :aria-label="t('registry.form.unitsPerProduct')"
              class="w-full"
            />
          </template>
        </Column>

        <Column style="width: 150px">
          <template #header>
            <span>{{ t('registry.form.template') }}</span>
          </template>
          <template #body="{ data }">
            <Button
              size="small"
              outlined
              severity="secondary"
              :label="templatePickerLabel(data)"
              :aria-label="t('registry.form.pickTemplate')"
              data-testid="registry-template-picker-btn"
              @click="openTemplatePicker(data._tempId)"
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
      <div v-if="activeClassifySuggestion" class="flex flex-col gap-3 p-1 min-w-64 max-w-sm">
        <div
          v-for="(s, idx) in activeClassifySuggestion.suggestions"
          :key="idx"
          class="border rounded-lg p-3 flex flex-col gap-1"
        >
          <div class="flex items-center gap-2">
            <Tag :value="layerLabel(s.layer)" size="small" severity="secondary" />
            <span class="font-mono font-semibold">{{ s.kfCode }}</span>
            <Tag
              :value="t(`registry.classify.confidence.${s.score >= 0.8 ? 'HIGH' : 'MEDIUM'}`)"
              :severity="s.score >= 0.8 ? 'success' : 'warn'"
              size="small"
            />
          </div>
          <div v-if="s.description" class="text-xs text-slate-500">
            {{ s.description }}
          </div>
          <div class="flex items-center gap-3 text-xs text-slate-400">
            <span v-if="s.weightEstimateKg != null">
              <span
                v-if="s.score < 0.7"
                v-tooltip.top="t('registry.classify.weightEstimateTooltip')"
                class="italic cursor-help"
              >
                ~{{ s.weightEstimateKg }} kg
              </span>
              <span v-else>~{{ s.weightEstimateKg }} kg</span>
            </span>
            <span v-if="s.unitsPerProduct > 1">{{ s.unitsPerProduct }} {{ t('registry.form.unitsPerProduct') }}</span>
          </div>
          <Button
            :label="t('registry.classify.accept')"
            size="small"
            icon="pi pi-check"
            :data-testid="`accept-kf-layer-${idx}`"
            @click="acceptSuggestion(s)"
          />
        </div>
        <Button
          v-if="activeClassifySuggestion.suggestions.length > 1"
          :label="t('registry.classify.acceptAll')"
          size="small"
          icon="pi pi-check-circle"
          data-testid="accept-kf-all"
          @click="acceptAllSuggestions"
        />
      </div>
    </Popover>

    <!-- Material-template picker dialog (Story 10.1 AC #11). appendTo=body to escape
         DataTable stacking context; z-index above classifier popover. Draft-create
         delegates to useMaterialTemplatePicker.createDraft — no new backend route. -->
    <Dialog
      v-model:visible="templatePickerOpen"
      modal
      append-to="body"
      :header="t('registry.templatePicker.title')"
      :pt="{ root: { class: 'z-[70]' } }"
      class="w-full max-w-md"
      data-testid="template-picker-dialog"
      @hide="closeTemplatePicker"
    >
      <div class="flex flex-col gap-3">
        <InputText
          v-model="templatePickerSearch"
          :placeholder="t('registry.templatePicker.searchPlaceholder')"
          :aria-label="t('registry.templatePicker.search')"
          data-testid="template-picker-search"
        />
        <Listbox
          v-model="templatePickerSelected"
          :options="templatePickerOptions"
          option-label="name"
          option-value="id"
          :empty-message="t('registry.templatePicker.empty')"
          :aria-label="t('registry.templatePicker.title')"
          data-testid="template-picker-list"
          class="max-h-60 overflow-y-auto"
        />
        <div class="flex justify-between items-center gap-2">
          <Button
            v-if="templatePickerSearch.trim().length > 0"
            :label="t('registry.templatePicker.create')"
            size="small"
            icon="pi pi-plus"
            severity="secondary"
            outlined
            :loading="templatePickerLoading"
            data-testid="template-picker-create"
            @click="createDraftFromSearch"
          />
          <span v-else class="flex-1"></span>
          <div class="flex gap-2">
            <Button
              :label="t('registry.templatePicker.clear')"
              size="small"
              severity="secondary"
              text
              data-testid="template-picker-clear"
              @click="clearTemplateSelection"
            />
            <Button
              :label="t('registry.templatePicker.cancel')"
              size="small"
              severity="secondary"
              outlined
              data-testid="template-picker-cancel"
              @click="closeTemplatePicker"
            />
            <Button
              :label="t('registry.templatePicker.confirm')"
              size="small"
              :disabled="templatePickerLoading"
              data-testid="template-picker-confirm"
              @click="confirmTemplateSelection"
            />
          </div>
        </div>
      </div>
    </Dialog>

    <!-- Story 10.2: KF-code wizard "Browse" dialog. Hosts EprWizardStepper in
         resolve-only mode (no template link, no override). Writes the resolved
         KF-code onto the row via `onKfWizardResolved`. -->
    <KfCodeWizardDialog
      :visible="kfWizardOpen"
      @update:visible="onKfWizardVisibleUpdate"
      @resolved="onKfWizardResolved"
    />
  </div>
</template>
