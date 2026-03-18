<script setup lang="ts">
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import type { SnapshotProvenanceResponse, SourceProvenanceEntry } from '~/types/api'
import { useDateRelative } from '~/composables/formatting/useDateRelative'
import tokens from '~/risk-guard-tokens.json'

const { t } = useI18n()
const { formatRelative } = useDateRelative()

const props = defineProps<{
  provenance: SnapshotProvenanceResponse | null
  confidence: 'FRESH' | 'STALE' | 'UNAVAILABLE'
  /** Whether to render in mobile accordion mode */
  mobile?: boolean
}>()

// ─── Freshness threshold from tokens (not hardcoded) ─────────────────────────
const freshThresholdMs = tokens.freshness.freshThresholdHours * 60 * 60 * 1000

// ─── Source Status Helpers ────────────────────────────────────────────────────
function isSourceStale(source: SourceProvenanceEntry): boolean {
  if (!source.available || !source.checkedAt) return false
  const age = Date.now() - new Date(source.checkedAt).getTime()
  return age > freshThresholdMs
}

function sourceIconClass(source: SourceProvenanceEntry): string {
  if (!source.available) return 'pi pi-times-circle text-rose-500'
  if (isSourceStale(source)) return 'pi pi-clock text-amber-500'
  return 'pi pi-check-circle text-emerald-600'
}

function sourceStatusLabel(source: SourceProvenanceEntry): string {
  if (!source.available) return t('screening.provenance.sourceUnavailable')
  if (isSourceStale(source)) return t('screening.provenance.sourceStale')
  return t('screening.provenance.sourceAvailable')
}

// ─── Freshness Indicator ─────────────────────────────────────────────────────
const freshnessConfig = computed(() => {
  switch (props.confidence) {
    case 'FRESH':
      return {
        colorClass: 'text-emerald-700 bg-emerald-50 border-emerald-200',
        label: t('screening.freshness.fresh'),
      }
    case 'STALE':
      return {
        colorClass: 'text-amber-700 bg-amber-50 border-amber-200',
        label: t('screening.freshness.stale'),
      }
    case 'UNAVAILABLE':
    default:
      return {
        colorClass: 'text-slate-600 bg-slate-50 border-slate-200',
        label: t('screening.freshness.unavailable'),
      }
  }
})

// ─── Display Name Mapping via i18n ───────────────────────────────────────────
/**
 * Maps canonical adapter keys to i18n keys for user-friendly display names.
 * Falls back to the raw adapter key if no i18n key is defined.
 */
function sourceDisplayName(sourceName: string): string {
  const key = `screening.provenance.sources.${sourceName}`
  const translated = t(key)
  // If i18n returns the key itself, the translation is missing — fall back to raw name
  return translated === key ? sourceName : translated
}
</script>

<template>
  <!-- Mobile: Collapsible Accordion -->
  <div
    v-if="mobile"
    class="w-full"
    data-testid="provenance-sidebar-mobile"
  >
    <Accordion value="provenance">
      <AccordionPanel value="provenance">
        <AccordionHeader>
          {{ t('screening.provenance.dataSourceDetails') }}
        </AccordionHeader>
        <AccordionContent>
          <div class="provenance-content">
            <template v-if="provenance && provenance.sources.length > 0">
              <ul
                class="space-y-3"
                data-testid="source-list"
              >
                <li
                  v-for="source in provenance.sources"
                  :key="source.sourceName"
                  class="flex items-start gap-3 text-sm"
                  :data-testid="`source-entry-${source.sourceName}`"
                >
                  <i
                    :class="sourceIconClass(source)"
                    class="mt-0.5 shrink-0"
                    :aria-label="sourceStatusLabel(source)"
                  />
                  <div class="flex-1 min-w-0">
                    <div class="font-medium text-slate-800">
                      {{ sourceDisplayName(source.sourceName) }}
                    </div>
                    <div class="text-slate-500 text-xs">
                      {{ t('screening.provenance.checkedAt') }}
                      {{ source.checkedAt ? formatRelative(source.checkedAt) : '—' }}
                    </div>
                    <a
                      v-if="source.sourceUrl"
                      :href="source.sourceUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="text-blue-500 hover:underline text-xs"
                      :data-testid="`source-url-${source.sourceName}`"
                    >
                      {{ t('screening.provenance.viewSource') }}
                      <i class="pi pi-external-link text-[10px] ml-0.5" />
                    </a>
                  </div>
                </li>
              </ul>
            </template>
            <p
              v-else
              class="text-slate-500 text-sm"
            >
              {{ t('screening.provenance.noSources') }}
            </p>

            <!-- Freshness Indicator -->
            <div
              class="mt-4 flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium"
              :class="freshnessConfig.colorClass"
              data-testid="freshness-indicator"
            >
              <i class="pi pi-info-circle" />
              {{ t('screening.freshness.label') }} {{ freshnessConfig.label }}
            </div>
          </div>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>
  </div>

  <!-- Desktop: Fixed Sidebar -->
  <div
    v-else
    class="flex flex-col gap-4"
    data-testid="provenance-sidebar-desktop"
  >
    <h2 class="text-lg font-semibold text-slate-800">
      {{ t('screening.provenance.title') }}
    </h2>

    <template v-if="provenance && provenance.sources.length > 0">
      <ul
        class="space-y-3"
        data-testid="source-list"
      >
        <li
          v-for="source in provenance.sources"
          :key="source.sourceName"
          class="flex items-start gap-3 text-sm"
          :data-testid="`source-entry-${source.sourceName}`"
        >
          <i
            :class="sourceIconClass(source)"
            class="mt-0.5 shrink-0"
            :aria-label="sourceStatusLabel(source)"
          />
          <div class="flex-1 min-w-0">
            <div class="font-medium text-slate-800">
              {{ sourceDisplayName(source.sourceName) }}
            </div>
            <div class="text-slate-500 text-xs">
              {{ t('screening.provenance.checkedAt') }}
              {{ source.checkedAt ? formatRelative(source.checkedAt) : '—' }}
            </div>
            <a
              v-if="source.sourceUrl"
              :href="source.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="text-blue-500 hover:underline text-xs"
              :data-testid="`source-url-${source.sourceName}`"
            >
              {{ t('screening.provenance.viewSource') }}
              <i class="pi pi-external-link text-[10px] ml-0.5" />
            </a>
          </div>
        </li>
      </ul>
    </template>
    <p
      v-else
      class="text-slate-500 text-sm"
    >
      {{ t('screening.provenance.noSources') }}
    </p>

    <!-- Freshness Indicator at bottom -->
    <div
      class="mt-2 flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium"
      :class="freshnessConfig.colorClass"
      data-testid="freshness-indicator"
    >
      <i class="pi pi-info-circle" />
      {{ t('screening.freshness.label') }} {{ freshnessConfig.label }}
    </div>
  </div>
</template>
