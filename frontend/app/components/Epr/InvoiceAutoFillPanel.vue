<script setup lang="ts">
import { useInvoiceAutoFill } from '~/composables/api/useInvoiceAutoFill'
import type { InvoiceAutoFillLineDto } from '~/composables/api/useInvoiceAutoFill'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Message from 'primevue/message'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const { t } = useI18n()
const toast = useToast()

const emit = defineEmits<{
  apply: [lines: InvoiceAutoFillLineDto[]]
}>()

const { fetchAutoFill, fetchRegisteredTaxNumber, pending, response, startOfCurrentQuarter, endOfCurrentQuarter } = useInvoiceAutoFill()

const taxNumber = ref('')

onMounted(async () => {
  const registered = await fetchRegisteredTaxNumber()
  if (registered && !taxNumber.value) {
    taxNumber.value = registered
  }
})
const fromDate = ref<Date>(startOfCurrentQuarter())
const toDate = ref<Date>(endOfCurrentQuarter())
const selectedLines = ref<InvoiceAutoFillLineDto[]>([])

async function handleFetch() {
  if (!taxNumber.value || !fromDate.value || !toDate.value) return
  selectedLines.value = []
  try {
    await fetchAutoFill(taxNumber.value, fromDate.value, toDate.value)
  }
  catch (err: unknown) {
    const message = (err as { message?: string })?.message ?? t('epr.autofill.fetchError')
    toast.add({ severity: 'error', summary: message, life: 5000 })
  }
}

function handleApply() {
  emit('apply', selectedLines.value)
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <!-- Scope warning: this panel only fits packaging-material distributors -->
    <Message
      severity="info"
      :closable="false"
      data-testid="autofill-scope-warning"
    >
      <div class="font-semibold">
        {{ t('epr.autofill.scopeWarningTitle') }}
      </div>
      <div class="text-sm mt-1">
        {{ t('epr.autofill.scopeWarningBody') }}
      </div>
    </Message>

    <!-- Input row -->
    <div class="flex flex-wrap gap-3 items-end">
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium text-slate-600" for="autofill-tax-number">
          {{ t('epr.autofill.taxNumberLabel') }}
        </label>
        <InputText
          id="autofill-tax-number"
          v-model="taxNumber"
          :placeholder="t('epr.autofill.taxNumberPlaceholder')"
          class="w-36"
          data-testid="autofill-tax-number"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium text-slate-600" for="autofill-from">
          {{ t('epr.autofill.fromLabel') }}
        </label>
        <DatePicker
          id="autofill-from"
          v-model="fromDate"
          date-format="yy-mm-dd"
          data-testid="autofill-from"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium text-slate-600" for="autofill-to">
          {{ t('epr.autofill.toLabel') }}
        </label>
        <DatePicker
          id="autofill-to"
          v-model="toDate"
          date-format="yy-mm-dd"
          data-testid="autofill-to"
        />
      </div>
      <Button
        :label="t('epr.autofill.fetchButton')"
        icon="pi pi-search"
        :loading="pending"
        :disabled="!taxNumber || pending"
        data-testid="autofill-fetch-button"
        @click="handleFetch"
      />
    </div>

    <!-- Loading skeleton -->
    <template v-if="pending">
      <Skeleton height="2rem" class="mb-2" data-testid="autofill-skeleton" />
      <Skeleton height="2rem" class="mb-2" />
      <Skeleton height="2rem" />
    </template>

    <!-- NAV unavailable warning -->
    <Message
      v-else-if="response && !response.navAvailable"
      severity="warn"
      data-testid="autofill-nav-unavailable"
    >
      {{ t('epr.autofill.navUnavailable') }}
      <router-link to="/admin/datasources" class="underline ml-1">
        {{ t('epr.autofill.navUnavailableLink') }}
      </router-link>
    </Message>

    <!-- Results table -->
    <template v-else-if="response && response.lines.length > 0">
      <DataTable
        v-model:selection="selectedLines"
        :value="response.lines"
        selection-mode="multiple"
        data-testid="autofill-results-table"
      >
        <Column selection-mode="multiple" header-style="width: 3rem" />
        <Column field="vtszCode" :header="t('epr.autofill.columns.vtszCode')" />
        <Column field="description" :header="t('epr.autofill.columns.description')" />
        <Column field="suggestedKfCode" :header="t('epr.autofill.columns.suggestedKfCode')">
          <template #body="{ data: line }">
            <span v-if="line.suggestedKfCode">{{ line.suggestedKfCode }}</span>
            <span v-else class="text-slate-400">—</span>
          </template>
        </Column>
        <Column field="aggregatedQuantity" :header="t('epr.autofill.columns.quantity')" />
        <Column field="unitOfMeasure" :header="t('epr.autofill.columns.unit')" />
        <Column :header="t('epr.autofill.columns.template')">
          <template #body="{ data: line }">
            <Tag
              v-if="line.hasExistingTemplate"
              severity="success"
              :value="t('epr.autofill.templateMatched')"
              data-testid="template-matched-badge"
            />
            <span v-else class="text-slate-400 text-xs">—</span>
          </template>
        </Column>
      </DataTable>

      <div class="flex justify-end">
        <Button
          :label="t('epr.autofill.applyButton')"
          icon="pi pi-check"
          :disabled="selectedLines.length === 0"
          data-testid="autofill-apply-button"
          @click="handleApply"
        />
      </div>
    </template>

    <!-- Empty result after fetch -->
    <div
      v-else-if="response && response.lines.length === 0"
      class="text-sm text-slate-400 py-2"
      data-testid="autofill-empty"
    >
      {{ t('epr.autofill.noResults') }}
    </div>
  </div>
</template>
