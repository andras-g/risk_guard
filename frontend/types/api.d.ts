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
  riskSignals: string[]
  cached: boolean
  companyName: string | null
  /** 64-char hex SHA-256 hash, "HASH_UNAVAILABLE" if computation failed, null for cached results */
  sha256Hash: string | null
}

export interface SourceProvenanceEntry {
  sourceName: string
  available: boolean
  checkedAt: string | null
  sourceUrl: string | null
}

export interface SnapshotProvenanceResponse {
  snapshotId: string
  taxNumber: string
  checkedAt: string | null
  sources: SourceProvenanceEntry[]
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
