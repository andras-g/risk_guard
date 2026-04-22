/**
 * EPR module TypeScript types.
 * TODO: Replace with auto-generated types after OpenAPI regen.
 * These types mirror the backend DTOs exactly.
 */

export interface MaterialTemplateResponse {
  id: string
  name: string
  baseWeightGrams: number
  kfCode: string | null
  verified: boolean
  recurring: boolean
  createdAt: string
  updatedAt: string
  overrideKfCode: string | null
  overrideReason: string | null
  confidence: string | null
  feeRate: number | null
  materialClassification: string | null
}

export interface MaterialTemplateRequest {
  name: string
  baseWeightGrams: number
  recurring?: boolean
}

export interface RecurringToggleRequest {
  recurring: boolean
}

export interface CopyQuarterRequest {
  sourceYear: number
  sourceQuarter: number
  includeNonRecurring: boolean
}

// ─── Wizard types ────────────────────────────────────────────────────────
// TODO: Replace with auto-generated types after OpenAPI regen

export interface WizardOption {
  code: string
  label: string
  description: string | null
}

export interface WizardSelection {
  level: string
  code: string
  label: string
}

export interface WizardStartResponse {
  configVersion: number
  level: string
  options: WizardOption[]
}

export interface WizardStepRequest {
  configVersion: number
  traversalPath: WizardSelection[]
  selection: WizardSelection
}

export interface WizardResolveRequest {
  configVersion: number
  traversalPath: WizardSelection[]
}

export interface WizardStepResponse {
  configVersion: number
  currentLevel: string
  nextLevel: string | null
  options: WizardOption[]
  breadcrumb: WizardSelection[]
  autoSelect: boolean
}

export interface WizardResolveResponse {
  kfCode: string
  feeCode: string
  feeRate: number
  currency: string
  materialClassification: string
  traversalPath: WizardSelection[]
  legislationRef: string
  confidenceScore: string
  confidenceReason: string
}

export interface WizardConfirmRequest {
  configVersion: number
  traversalPath: WizardSelection[]
  kfCode: string
  feeRate: number
  materialClassification: string
  templateId: string | null
  confidenceScore: string
  overrideKfCode: string | null
  overrideReason: string | null
}

export interface WizardConfirmResponse {
  calculationId: string
  kfCode: string
  templateUpdated: boolean
}

// ─── Retry Link types ───────────────────────────────────────────────────
// TODO: Replace with auto-generated type after OpenAPI regen

export interface RetryLinkResponse {
  templateUpdated: boolean
  kfCode: string
}

// ─── KF-Code Enumeration types ──────────────────────────────────────────
// TODO: Replace with auto-generated types after OpenAPI regen

export interface KfCodeEntry {
  kfCode: string
  feeCode: string
  feeRate: number
  currency: string
  classification: string
  productStreamLabel: string
}

export interface KfCodeListResponse {
  configVersion: number
  entries: KfCodeEntry[]
}

// ─── Filing Aggregation types (Story 10.5) ──────────────────────────────────

export interface SoldProductLine {
  productId: string | null
  vtsz: string
  description: string
  totalQuantity: number
  unitOfMeasure: string
  matchingInvoiceLines: number
}

export interface KfCodeTotal {
  kfCode: string
  classificationLabel: string | null
  totalWeightKg: number
  feeRateHufPerKg: number
  totalFeeHuf: number
  contributingProductCount: number
  hasFallback: boolean
  hasOverflowWarning: boolean
}

export interface UnresolvedInvoiceLine {
  invoiceNumber: string
  lineNumber: number
  vtsz: string
  description: string
  quantity: number
  unitOfMeasure: string
  reason: 'NO_MATCHING_PRODUCT' | 'UNSUPPORTED_UNIT_OF_MEASURE' | 'ZERO_COMPONENTS' | 'VTSZ_FALLBACK'
}

export interface AggregationMetadata {
  period?: { from: string; to: string }
  generatedAt?: string
  // Story 10.11 AC #5: count of distinct products with epr_scope='UNKNOWN' that contributed
  // resolved invoice lines in the period — drives the filing-page warning banner.
  unknownScopeProductsInPeriod?: number
  invoiceLineCount?: number
  resolvedLineCount?: number
  activeConfigVersion?: number
  periodStart?: string
  periodEnd?: string
  aggregationDurationMs?: number
}

export interface FilingAggregationResult {
  soldProducts: SoldProductLine[]
  kfTotals: KfCodeTotal[]
  unresolved: UnresolvedInvoiceLine[]
  metadata: AggregationMetadata
}

// ─── Provenance types (Story 10.8) ──────────────────────────────────────────

export type ProvenanceTag = 'REGISTRY_MATCH' | 'VTSZ_FALLBACK' | 'UNRESOLVED' | 'UNSUPPORTED_UNIT'

export interface ProvenanceLine {
  invoiceNumber: string
  lineNumber: number
  vtsz: string
  description: string
  quantity: number
  unitOfMeasure: string
  resolvedProductId: string | null
  productName: string | null
  componentId: string | null
  wrappingLevel: number | null
  componentKfCode: string | null
  weightContributionKg: number
  provenanceTag: ProvenanceTag
}

export interface ProvenancePage {
  content: ProvenanceLine[]
  totalElements: number
  page: number
  size: number
}

// ─── Submission History types (Story 10.9) ──────────────────────────────────

export interface EprSubmissionSummary {
  id: string
  periodStart: string
  periodEnd: string
  totalWeightKg: number | null
  totalFeeHuf: number | null
  exportedAt: string
  fileName: string | null
  submittedByUserEmail: string | null
  hasXmlContent: boolean
}

export interface EprSubmissionPage {
  content: EprSubmissionSummary[]
  totalElements: number
  page: number
  size: number
}
