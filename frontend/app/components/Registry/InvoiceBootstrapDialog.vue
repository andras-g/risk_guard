<script setup lang="ts">
import Dialog from 'primevue/dialog'
import DatePicker from 'primevue/datepicker'
import Button from 'primevue/button'
import ProgressBar from 'primevue/progressbar'
import { useInvoiceBootstrap } from '~/composables/api/useInvoiceBootstrap'
import { useRegistry } from '~/composables/api/useRegistry'
import type { BootstrapJobStatusResponse } from '~/composables/api/useInvoiceBootstrap'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'completed': [status: BootstrapJobStatusResponse]
}>()

const { t } = useI18n()
const router = useRouter()
const { triggerBootstrap, getJobStatus, cancelJob } = useInvoiceBootstrap()
const { listProducts } = useRegistry()

// Format a Date as a local YYYY-MM-DD string. Using `.toISOString().slice(0,10)` here
// would convert local midnight through UTC and silently shift the date by one day for
// non-UTC users (e.g., Europe/Budapest periodFrom=2026-01-01 → "2025-12-31").
function toLocalIsoDate(d: Date): string {
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

// ─── Default period: last 3 complete calendar months ─────────────────────────
// Matches the server default (YearMonth.minusMonths(3) …  YearMonth.minusMonths(1).atEndOfMonth()):
// invoked 2026-04-20 → from = 2026-01-01, to = 2026-03-31 (3 months, not 2).
function computeDefaultPeriod(): { from: Date; to: Date } {
  const now = new Date()
  const to = new Date(now.getFullYear(), now.getMonth(), 0) // last day of prev month
  const from = new Date(to.getFullYear(), to.getMonth() - 2, 1) // first day, 3 months back from `to`
  return { from, to }
}

const defaultPeriod = computeDefaultPeriod()
const periodFrom = ref<Date>(defaultPeriod.from)
const periodTo = ref<Date>(defaultPeriod.to)

// ─── Overwrite warning ────────────────────────────────────────────────────────
const existingProductCount = ref(0)
const overwriteConfirmed = ref(false)
const isLoadingSummary = ref(false)

async function loadExistingCount() {
  isLoadingSummary.value = true
  try {
    const result = await listProducts({ size: 1 })
    existingProductCount.value = Number(result.total)
  }
  catch {
    existingProductCount.value = 0
  }
  finally {
    isLoadingSummary.value = false
  }
}

const needsOverwriteConfirm = computed(() => existingProductCount.value > 0 && !overwriteConfirmed.value)

// ─── Job state ────────────────────────────────────────────────────────────────
type Phase = 'idle' | 'running' | 'done' | 'error'
const phase = ref<Phase>('idle')
const jobStatus = ref<BootstrapJobStatusResponse | null>(null)
const triggerError = ref<string | null>(null)
let pollInterval: ReturnType<typeof setInterval> | null = null

function clearPoll() {
  if (pollInterval !== null) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

const isTerminal = computed(() => {
  const s = jobStatus.value?.status
  return s === 'COMPLETED' || s === 'FAILED' || s === 'FAILED_PARTIAL' || s === 'CANCELLED'
})

const progressPct = computed(() => {
  const j = jobStatus.value
  if (!j || j.totalPairs === 0) return 0
  return Math.round((j.classifiedPairs / j.totalPairs) * 100)
})

async function poll() {
  if (!jobStatus.value) return
  try {
    jobStatus.value = await getJobStatus(jobStatus.value.jobId)
    if (isTerminal.value) {
      clearPoll()
      phase.value = 'done'
      emit('completed', jobStatus.value!)
    }
  }
  catch {
    clearPoll()
  }
}

// ─── Trigger ──────────────────────────────────────────────────────────────────

async function onStart() {
  if (needsOverwriteConfirm.value) {
    overwriteConfirmed.value = true
    return
  }
  // Guard against rapid double-click: if polling is already running, ignore this click.
  if (pollInterval !== null) return
  triggerError.value = null
  try {
    const created = await triggerBootstrap(toLocalIsoDate(periodFrom.value), toLocalIsoDate(periodTo.value))
    jobStatus.value = await getJobStatus(created.jobId)
    phase.value = 'running'
    pollInterval = setInterval(poll, 2000)
  }
  catch (err: unknown) {
    const e = err as { data?: { code?: string; message?: string; jobId?: string } }
    const code = e.data?.code
    const mapped = code ? camelFromCode(code) : undefined
    if (mapped === 'alreadyRunning' && e.data?.jobId) {
      // AC #13: if a bootstrap is already running, attach this dialog to it so the user
      // can observe progress or cancel — rather than presenting a dead-end error.
      try {
        jobStatus.value = await getJobStatus(e.data.jobId)
        phase.value = 'running'
        pollInterval = setInterval(poll, 2000)
        return
      }
      catch { /* fall through to the generic error render below */ }
    }
    if (mapped) {
      triggerError.value = t(`registry.bootstrap.errors.${mapped}`)
    }
    else {
      triggerError.value = e.data?.message ?? t('common.states.error')
    }
    phase.value = 'error'
  }
}

// Unknown codes return undefined so the caller falls back to the server message
// or a generic error — instead of silently rendering the navCredentialsMissing copy.
function camelFromCode(code: string): string | undefined {
  const map: Record<string, string> = {
    NAV_CREDENTIALS_MISSING: 'navCredentialsMissing',
    PRODUCER_PROFILE_INCOMPLETE: 'producerProfileIncomplete',
    ALREADY_RUNNING: 'alreadyRunning',
    TAX_NUMBER_MISMATCH: 'taxNumberMismatch',
    CAP_EXCEEDED: 'capExceeded',
  }
  return map[code]
}

async function onCancel() {
  if (!jobStatus.value) return
  clearPoll()
  try {
    await cancelJob(jobStatus.value.jobId)
    jobStatus.value = await getJobStatus(jobStatus.value.jobId)
  }
  catch { /* ignore */ }
  phase.value = 'done'
}

function onClose() {
  clearPoll()
  phase.value = 'idle'
  jobStatus.value = null
  triggerError.value = null
  overwriteConfirmed.value = false
  const def = computeDefaultPeriod()
  periodFrom.value = def.from
  periodTo.value = def.to
  emit('update:visible', false)
}

function onOpenRegistry() {
  const j = jobStatus.value
  onClose()
  if (j && j.unresolvedPairs > 0) {
    router.push('/registry?reviewState=MISSING_PACKAGING')
  }
  else if (j && j.vtszFallbackPairs > 0) {
    router.push('/registry?classifierSource=VTSZ_FALLBACK')
  }
  else {
    router.push('/registry')
  }
}

watch(() => props.visible, (visible) => {
  if (visible) {
    overwriteConfirmed.value = false
    loadExistingCount()
  }
  else {
    clearPoll()
  }
})

// Guard against route navigation or hot-reload unmounting the dialog without first
// toggling `visible=false` — without this hook, a lingering interval would keep
// polling a stale jobId after the parent page has navigated away.
onBeforeUnmount(clearPoll)
</script>

<template>
  <Dialog
    :visible="props.visible"
    :header="t('registry.bootstrap.dialogTitle')"
    modal
    append-to="body"
    :style="{ width: '640px' }"
    :closable="phase !== 'running'"
    data-testid="invoice-bootstrap-dialog"
    @update:visible="onClose"
  >
    <!-- ── Idle / confirm phase ── -->
    <template v-if="phase === 'idle' || phase === 'error'">
      <div class="flex flex-col gap-4">
        <div class="flex gap-4">
          <div class="flex flex-col gap-1 flex-1">
            <label for="bootstrap-period-from" class="text-sm font-medium">{{ t('registry.bootstrap.periodFromLabel') }}</label>
            <DatePicker
              id="bootstrap-period-from"
              v-model="periodFrom"
              date-format="yy-mm-dd"
              :max-date="periodTo"
              data-testid="bootstrap-period-from"
            />
          </div>
          <div class="flex flex-col gap-1 flex-1">
            <label for="bootstrap-period-to" class="text-sm font-medium">{{ t('registry.bootstrap.periodToLabel') }}</label>
            <DatePicker
              id="bootstrap-period-to"
              v-model="periodTo"
              date-format="yy-mm-dd"
              :min-date="periodFrom"
              :max-date="new Date()"
              data-testid="bootstrap-period-to"
            />
          </div>
        </div>

        <div
          v-if="existingProductCount > 0 && !overwriteConfirmed"
          class="p-3 bg-amber-50 border border-amber-200 rounded text-sm text-amber-800"
          data-testid="bootstrap-overwrite-warning"
        >
          {{ t('registry.bootstrap.overwriteWarning', { count: existingProductCount }) }}
        </div>

        <div v-if="triggerError" class="p-3 bg-red-50 border border-red-200 rounded text-sm text-red-800" data-testid="bootstrap-trigger-error">
          {{ triggerError }}
        </div>
      </div>
    </template>

    <!-- ── Running phase ── -->
    <template v-else-if="phase === 'running'">
      <div class="flex flex-col gap-4">
        <p class="text-sm text-slate-600">
          {{ jobStatus?.status === 'PENDING' ? t('registry.bootstrap.progress.pending') : t('registry.bootstrap.progress.running') }}
        </p>

        <ProgressBar
          :value="progressPct"
          :mode="jobStatus?.totalPairs === 0 ? 'indeterminate' : 'determinate'"
          data-testid="bootstrap-progress-bar"
        />

        <p v-if="jobStatus && jobStatus.totalPairs > 0" class="text-sm text-slate-500" data-testid="bootstrap-counter">
          {{ t('registry.bootstrap.progress.counterLabel', {
            created: jobStatus.createdProducts,
            uncertain: jobStatus.vtszFallbackPairs,
            incomplete: jobStatus.unresolvedPairs,
          }) }}
        </p>
      </div>
    </template>

    <!-- ── Done phase ── -->
    <template v-else-if="phase === 'done' && jobStatus">
      <div class="flex flex-col gap-3" data-testid="bootstrap-completion">
        <template v-if="jobStatus.status === 'COMPLETED' && jobStatus.totalPairs === 0">
          <p class="text-sm text-slate-700" data-testid="bootstrap-completion-empty">
            {{ t('registry.bootstrap.completion.noInvoicesFound') }}
          </p>
        </template>
        <template v-else-if="jobStatus.status === 'COMPLETED'">
          <p class="text-sm text-green-700">
            {{ t('registry.bootstrap.completion.success', {
              created: jobStatus.createdProducts,
              uncertain: jobStatus.vtszFallbackPairs,
              incomplete: jobStatus.unresolvedPairs,
            }) }}
          </p>
        </template>
        <template v-else-if="jobStatus.status === 'FAILED_PARTIAL'">
          <p class="text-sm text-amber-700">
            {{ t('registry.bootstrap.completion.partial', {
              created: jobStatus.createdProducts,
              uncertain: jobStatus.vtszFallbackPairs,
              incomplete: jobStatus.unresolvedPairs,
            }) }}
          </p>
        </template>
        <template v-else-if="jobStatus.status === 'FAILED'">
          <p class="text-sm text-red-700">
            {{ t('registry.bootstrap.completion.failed', { message: jobStatus.errorMessage }) }}
          </p>
        </template>
        <template v-else-if="jobStatus.status === 'CANCELLED'">
          <p class="text-sm text-slate-600">
            {{ t('registry.bootstrap.completion.cancelled') }}
          </p>
          <p v-if="jobStatus.createdProducts > 0" class="text-sm text-slate-500">
            {{ t('registry.bootstrap.progress.counterLabel', {
              created: jobStatus.createdProducts,
              uncertain: jobStatus.vtszFallbackPairs,
              incomplete: jobStatus.unresolvedPairs,
            }) }}
          </p>
        </template>
      </div>
    </template>

    <template #footer>
      <!-- Idle/error: start or overwrite-confirm -->
      <template v-if="phase === 'idle' || phase === 'error'">
        <Button :label="t('registry.actions.cancel')" severity="secondary" text @click="onClose" />
        <Button
          v-if="needsOverwriteConfirm"
          :label="t('registry.bootstrap.confirmOverwriteButton')"
          severity="warn"
          :loading="isLoadingSummary"
          data-testid="bootstrap-confirm-overwrite-btn"
          @click="onStart"
        />
        <Button
          v-else
          :label="t('registry.bootstrap.startButton')"
          :loading="isLoadingSummary"
          data-testid="bootstrap-start-btn"
          @click="onStart"
        />
      </template>

      <!-- Running: cancel only -->
      <template v-else-if="phase === 'running'">
        <Button
          :label="t('registry.bootstrap.cancelButton')"
          severity="secondary"
          data-testid="bootstrap-cancel-btn"
          @click="onCancel"
        />
      </template>

      <!-- Done: close or open registry -->
      <template v-else-if="phase === 'done' && jobStatus">
        <Button :label="t('registry.bootstrap.completion.close')" severity="secondary" text @click="onClose" />
        <Button
          v-if="jobStatus.status === 'COMPLETED' || jobStatus.status === 'FAILED_PARTIAL'"
          :label="t('registry.bootstrap.completion.openRegistry')"
          data-testid="bootstrap-open-registry-btn"
          @click="onOpenRegistry"
        />
      </template>
    </template>
  </Dialog>
</template>
