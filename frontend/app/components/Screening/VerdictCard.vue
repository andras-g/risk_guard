<script setup lang="ts">
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import type { VerdictResponse, SnapshotProvenanceResponse } from '~/types/api'
import { useWatchlistStore } from '~/stores/watchlist'
import { useVerdictPdf } from '~/composables/api/useVerdictPdf'

const { t } = useI18n()
const toast = useToast()
const watchlistStore = useWatchlistStore()

const props = defineProps<{
  verdict: VerdictResponse
  provenance?: SnapshotProvenanceResponse | null
}>()

const { isGenerating, exportVerdict } = useVerdictPdf()

// ─── Freshness Guard ────────────────────────────────────────────────────────
// Force Grey Shield if confidence is UNAVAILABLE (data > 48h) regardless of status.
const isStaleOverride = computed(() =>
  props.verdict.confidence === 'UNAVAILABLE'
)

// ─── Status-to-Visual Mapping ────────────────────────────────────────────────
/**
 * Per-spec mapping (story Dev Notes — Technical Requirements).
 * TAX_SUSPENDED → Amber, AT_RISK → Crimson, RELIABLE → Emerald, INCOMPLETE/UNAVAILABLE → Grey.
 * If stale override is active, force grey regardless of status.
 */
const statusConfig = computed(() => {
  if (isStaleOverride.value) {
    return {
      borderClass: 'border-slate-400',
      iconClass: 'pi pi-clock text-slate-500',
      pillClass: 'bg-slate-100 text-slate-700',
      textClass: 'text-slate-700',
      label: t('screening.verdict.unavailable'),
    }
  }

  switch (props.verdict.status) {
    case 'RELIABLE':
      return {
        borderClass: 'border-emerald-700',
        iconClass: 'pi pi-check-circle text-reliable-text',
        pillClass: 'bg-emerald-100 text-reliable-text',
        textClass: 'text-reliable-text',
        label: t('screening.verdict.reliable'),
      }
    case 'AT_RISK':
      return {
        borderClass: 'border-rose-700',
        iconClass: 'pi pi-times-circle text-at-risk-text',
        pillClass: 'bg-rose-100 text-at-risk-text',
        textClass: 'text-at-risk-text',
        label: t('screening.verdict.atRisk'),
      }
    case 'TAX_SUSPENDED':
      return {
        borderClass: 'border-amber-600',
        iconClass: 'pi pi-exclamation-triangle text-stale-text',
        pillClass: 'bg-amber-100 text-stale-text',
        textClass: 'text-stale-text',
        label: t('screening.verdict.taxSuspended'),
      }
    case 'INCOMPLETE':
    case 'UNAVAILABLE':
    default:
      return {
        borderClass: 'border-slate-400',
        iconClass: 'pi pi-clock text-slate-500',
        pillClass: 'bg-slate-100 text-slate-700',
        textClass: 'text-slate-700',
        label: t('screening.verdict.incomplete'),
      }
  }
})

// ─── Risk Signals ────────────────────────────────────────────────────────────
/**
 * Map backend risk signal codes to human-readable i18n labels.
 * SOURCE_UNAVAILABLE signals include a dynamic source name suffix.
 */
function riskSignalLabel(signal: string): string {
  if (signal.startsWith('SOURCE_UNAVAILABLE:')) {
    const sourceName = signal.split(':')[1] ?? ''
    return t('screening.riskSignals.SOURCE_UNAVAILABLE', { name: sourceName })
  }
  const key = `screening.riskSignals.${signal}`
  return t(key)
}

const hasRiskSignals = computed(() =>
  props.verdict.riskSignals && props.verdict.riskSignals.length > 0
)

// ─── SHA-256 Hash Display ────────────────────────────────────────────────────
const HASH_UNAVAILABLE_SENTINEL = 'HASH_UNAVAILABLE'

const truncatedHash = computed(() => {
  const hash = props.verdict.sha256Hash
  if (!hash) return null
  if (hash === HASH_UNAVAILABLE_SENTINEL) return t('screening.verdict.hashUnavailable')
  return hash.slice(0, 16) + '...'
})

/**
 * True only when the hash is a real 64-char hex value.
 * False for null (cached) and for the HASH_UNAVAILABLE sentinel.
 */
const isRealHash = computed(() =>
  !!props.verdict.sha256Hash && props.verdict.sha256Hash !== HASH_UNAVAILABLE_SENTINEL
)

async function copyHash() {
  // Guard: only copy when isRealHash — never copy the HASH_UNAVAILABLE sentinel string.
  // The template v-if="isRealHash" prevents the button from rendering for sentinel/null,
  // but this guard ensures safety if copyHash() is ever called programmatically.
  if (isRealHash.value) {
    await navigator.clipboard.writeText(props.verdict.sha256Hash!)
  }
}

// ─── Add to Watchlist ────────────────────────────────────────────────────
const isOnWatchlist = computed(() =>
  watchlistStore.isOnWatchlist(props.verdict.taxNumber)
)

const isAddingToWatchlist = ref(false)

async function addToWatchlist() {
  if (isOnWatchlist.value || isAddingToWatchlist.value) return

  isAddingToWatchlist.value = true
  try {
    const result = await watchlistStore.addEntry(props.verdict.taxNumber, props.verdict.companyName, props.verdict.status)
    if (result.duplicate) {
      toast.add({
        severity: 'warn',
        summary: t('notification.watchlist.duplicateToast'),
        life: 3000,
      })
    }
    else {
      toast.add({
        severity: 'success',
        summary: t('notification.watchlist.addedToast'),
        life: 3000,
      })
    }
  }
  catch {
    toast.add({
      severity: 'error',
      summary: t('common.states.error'),
      life: 3000,
    })
  }
  finally {
    isAddingToWatchlist.value = false
  }
}

</script>

<template>
  <div
    role="region"
    :aria-label="statusConfig.label"
    class="rounded-xl shadow-md bg-white p-6 border-2"
    :class="statusConfig.borderClass"
    data-testid="verdict-card"
  >
    <!-- ─── Shield Icon + Status Badge ─────────────────────────────────── -->
    <div
      class="flex items-center gap-4 mb-4"
      data-testid="verdict-status-badge"
    >
      <i
        :class="[statusConfig.iconClass, 'text-[80px]']"
        aria-hidden="true"
        data-testid="shield-icon"
      />
      <div>
        <!-- Status label — uses active locale (hu/en) -->
        <div
          class="text-xl font-bold"
          :class="statusConfig.textClass"
          data-testid="verdict-status-label"
        >
          {{ statusConfig.label }}
        </div>
      </div>
    </div>

    <!-- ─── Stale Warning Banner ──────────────────────────────────────────── -->
    <div
      v-if="isStaleOverride"
      class="mb-4 px-4 py-2 bg-slate-100 border border-slate-300 rounded-lg text-slate-600 text-sm font-medium"
      data-testid="stale-warning-banner"
    >
      ⚠ {{ t('screening.verdict.staleWarning') }}
    </div>

    <!-- ─── TAX_SUSPENDED Warning Badge ──────────────────────────────────── -->
    <div
      v-if="verdict.status === 'TAX_SUSPENDED' && !isStaleOverride"
      class="mb-4 px-4 py-2 bg-amber-50 border border-amber-300 rounded-lg text-amber-800 text-sm font-semibold"
      data-testid="tax-suspended-badge"
    >
      ⚠ {{ t('screening.verdict.manualReviewRequired') }}
    </div>

    <!-- ─── Company Name ──────────────────────────────────────────────────── -->
    <div
      v-if="verdict.companyName"
      class="mb-3"
    >
      <span class="text-xs text-secondary-text uppercase tracking-wide">
        {{ t('screening.verdict.companyLabel') }}
      </span>
      <div
        class="text-lg font-semibold text-slate-800"
        data-testid="company-name"
      >
        {{ verdict.companyName }}
      </div>
    </div>

    <!-- ─── Tax Number ────────────────────────────────────────────────────── -->
    <div class="mb-3">
      <span class="text-xs text-secondary-text uppercase tracking-wide">
        {{ t('screening.verdict.taxNumber') }}
      </span>
      <div
        class="font-mono text-slate-800 text-base"
        data-testid="tax-number"
      >
        {{ verdict.taxNumber }}
      </div>
    </div>

    <!-- ─── SHA-256 Hash ──────────────────────────────────────────────────── -->
    <div
      v-if="truncatedHash"
      class="mb-4 flex items-center gap-2"
    >
      <span class="text-xs text-secondary-text uppercase tracking-wide">
        {{ t('screening.verdict.hashLabel') }}
      </span>
      <span
        v-if="isRealHash"
        class="font-mono text-sm text-slate-600"
        data-testid="truncated-hash"
      >
        {{ truncatedHash }}
      </span>
      <span
        v-else
        class="text-sm text-secondary-text italic"
        data-testid="hash-unavailable"
      >
        {{ truncatedHash }}
      </span>
      <button
        v-if="isRealHash"
        type="button"
        class="text-xs text-blue-500 hover:text-blue-700 underline cursor-pointer"
        data-testid="copy-hash-button"
        :title="verdict.sha256Hash ?? ''"
        :aria-label="t('screening.actions.copyHash')"
        @click="copyHash"
      >
        <i class="pi pi-copy" aria-hidden="true" />
      </button>
    </div>

    <!-- ─── Cached Indicator ──────────────────────────────────────────────── -->
    <div
      v-if="verdict.cached"
      class="mb-4 text-sm text-secondary-text italic"
      data-testid="cached-indicator"
    >
      🗂 {{ t('screening.verdict.cached') }}
    </div>

    <!-- ─── Risk Signals Section ──────────────────────────────────────────── -->
    <div
      class="mb-4"
    >
      <div class="text-xs text-secondary-text uppercase tracking-wide mb-2">
        {{ t('screening.riskSignals.title') }}
      </div>
      <div
        v-if="verdict.cached && !hasRiskSignals"
        class="text-sm text-slate-500 italic"
        data-testid="cached-no-signals"
      >
        {{ t('screening.riskSignals.noSignalsForCached') }}
      </div>
      <ul
        v-else-if="hasRiskSignals"
        class="space-y-1"
        data-testid="risk-signals-list"
      >
        <li
          v-for="signal in verdict.riskSignals"
          :key="signal"
          class="flex items-center gap-2 text-sm text-slate-700"
          :data-testid="`risk-signal-${signal}`"
        >
          <i class="pi pi-exclamation-circle text-rose-500" />
          {{ riskSignalLabel(signal) }}
        </li>
      </ul>
      <div
        v-else
        class="text-sm text-emerald-600"
        data-testid="no-risk-signals"
      >
        ✓ {{ t('screening.riskSignals.noSignals') }}
      </div>
    </div>

    <!-- ─── Action Buttons ───────────────────────────────────────────────── -->
    <div class="flex gap-3 mt-4">
      <Button
        :label="t('screening.actions.exportPdf')"
        icon="pi pi-file-pdf"
        severity="primary"
        :disabled="isGenerating"
        :loading="isGenerating"
        :title="t('screening.actions.exportPdfTooltip')"
        data-testid="export-pdf-button"
        @click="exportVerdict(props.verdict, props.provenance ?? null, t)"
      />
      <Button
        :label="isOnWatchlist ? t('notification.watchlist.onWatchlist') : t('screening.actions.addToWatchlist')"
        icon="pi pi-bookmark"
        severity="secondary"
        :disabled="isOnWatchlist || isAddingToWatchlist"
        :loading="isAddingToWatchlist"
        data-testid="add-watchlist-button"
        @click="addToWatchlist"
      />
    </div>
  </div>
</template>
