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

// ─── Invoice Auto-Fill types (Story 8.3) ────────────────────────────────────
// TODO: Replace with auto-generated types after OpenAPI regen

export interface InvoiceAutoFillRequest {
  taxNumber: string
  from: string
  to: string
}

export interface InvoiceAutoFillLineDto {
  vtszCode: string
  description: string
  suggestedKfCode: string | null
  aggregatedQuantity: number
  unitOfMeasure: string
  hasExistingTemplate: boolean
  existingTemplateId: string | null
}

export interface InvoiceAutoFillResponse {
  lines: InvoiceAutoFillLineDto[]
  navAvailable: boolean
  dataSourceMode: string
}
