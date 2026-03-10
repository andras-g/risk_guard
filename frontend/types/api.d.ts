/**
 * Auto-generated TypeScript types from OpenAPI spec.
 * Re-generate with: npm run generate-types (requires backend running)
 *
 * Story 2.1: Manually created pending OpenAPI pipeline execution.
 * These types mirror the backend DTOs exactly.
 */

export interface PartnerSearchRequest {
  taxNumber: string
}

export interface VerdictResponse {
  id: string
  snapshotId: string
  taxNumber: string
  status: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE'
  confidence: 'FRESH' | 'STALE' | 'UNAVAILABLE'
  createdAt: string
}

export interface CompanySnapshotResponse {
  id: string
  taxNumber: string
  snapshotData: string
  createdAt: string
  updatedAt: string
}

export interface UserResponse {
  id: string
  email: string
  name: string | null
  role: string
  preferredLanguage: string
  homeTenantId: string
  activeTenantId: string
}

export interface TenantResponse {
  id: string
  name: string
  tier: string
}

export interface TenantSwitchRequest {
  tenantId: string
}
