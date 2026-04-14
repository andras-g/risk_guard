import type { InvoiceAutoFillResponse, InvoiceAutoFillLineDto } from '~/types/epr'

/**
 * Returns the first day of the previous quarter.
 * EPR filings are submitted for the most recently completed quarter.
 */
function startOfCurrentQuarter(): Date {
  const now = new Date()
  const q = Math.floor(now.getMonth() / 3)
  const prevQ = q === 0 ? 3 : q - 1
  const year = q === 0 ? now.getFullYear() - 1 : now.getFullYear()
  return new Date(year, prevQ * 3, 1)
}

/**
 * Returns the last day of the previous quarter.
 * EPR filings are submitted for the most recently completed quarter.
 */
function endOfCurrentQuarter(): Date {
  const now = new Date()
  const q = Math.floor(now.getMonth() / 3)
  const prevQ = q === 0 ? 3 : q - 1
  const year = q === 0 ? now.getFullYear() - 1 : now.getFullYear()
  return new Date(year, prevQ * 3 + 3, 0)
}

/**
 * Composable for the invoice-driven EPR auto-fill feature.
 * POSTs to /api/v1/epr/filing/invoice-autofill and returns aggregated VTSZ line suggestions.
 */
export function useInvoiceAutoFill() {
  const response = ref<InvoiceAutoFillResponse | null>(null)
  const pending = ref(false)
  const error = ref<string | null>(null)
  const registeredTaxNumber = ref('')

  /**
   * Fetches the tenant's registered tax number from NAV credentials.
   * Returns empty string if no credentials are configured.
   */
  async function fetchRegisteredTaxNumber(): Promise<string> {
    try {
      const config = useRuntimeConfig()
      const data = await $fetch<{ taxNumber: string }>(
        '/api/v1/epr/filing/registered-tax-number',
        {
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        },
      )
      registeredTaxNumber.value = data.taxNumber ?? ''
      return registeredTaxNumber.value
    }
    catch {
      return ''
    }
  }

  async function fetchAutoFill(taxNumber: string, from: Date, to: Date): Promise<InvoiceAutoFillResponse> {
    pending.value = true
    error.value = null
    response.value = null

    try {
      const config = useRuntimeConfig()
      const toIso = (d: Date) => d.toISOString().slice(0, 10)
      const data = await $fetch<InvoiceAutoFillResponse>(
        '/api/v1/epr/filing/invoice-autofill',
        {
          method: 'POST',
          body: { taxNumber, from: toIso(from), to: toIso(to) },
          baseURL: config.public.apiBase as string,
          credentials: 'include',
        },
      )
      response.value = data
      return data
    }
    catch (err: unknown) {
      const message = (err as { message?: string })?.message ?? 'Unknown error'
      error.value = message
      throw err
    }
    finally {
      pending.value = false
    }
  }

  return {
    response,
    pending,
    error,
    registeredTaxNumber,
    fetchAutoFill,
    fetchRegisteredTaxNumber,
    startOfCurrentQuarter,
    endOfCurrentQuarter,
  }
}

export type { InvoiceAutoFillResponse, InvoiceAutoFillLineDto }
