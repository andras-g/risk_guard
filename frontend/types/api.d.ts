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

// ─── Guest Search DTOs (Story 3.12) ─────────────────────────────────────────
// TODO: Remove manual definitions below once CI OpenAPI pipeline generates these
// from GuestSearchRequest.java / GuestSearchResponse.java / GuestLimitResponse.java.
// See: npm run generate-types (requires backend running with OpenAPI spec enabled).

export interface GuestSearchRequest {
  taxNumber: string
  sessionFingerprint: string
}

export interface GuestSearchResponse {
  id: string
  snapshotId: string
  taxNumber: string
  status: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE'
  confidence: 'FRESH' | 'STALE' | 'UNAVAILABLE'
  createdAt: string
  riskSignals: string[]
  cached: boolean
  companyName: string | null
  companiesUsed: number
  companiesLimit: number
  dailyChecksUsed: number
  dailyChecksLimit: number
}

export interface GuestLimitResponse {
  error: 'COMPANY_LIMIT_REACHED' | 'DAILY_LIMIT_REACHED'
  companiesUsed: number | null
  companiesLimit: number | null
  dailyChecksUsed: number | null
  dailyChecksLimit: number | null
}

// ─── Company Snapshot DTOs ──────────────────────────────────────────────────

export interface CompanySnapshotResponse {
  id: string
  taxNumber: string
  snapshotData: string
  createdAt: string
  updatedAt: string
}

// ─── Public Company DTOs (Story 3.11) ───────────────────────────────────────

export interface PublicCompanyResponse {
  taxNumber: string
  companyName: string | null
  address: string | null
}

// ─── Identity DTOs ──────────────────────────────────────────────────────────

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

// ─── Watchlist DTOs (Story 3.6) ─────────────────────────────────────────────

export interface AddWatchlistEntryRequest {
  taxNumber: string
  companyName?: string | null
  verdictStatus?: string | null
}

export interface WatchlistEntryResponse {
  id: string
  taxNumber: string
  companyName: string | null
  label: string | null
  currentVerdictStatus: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE' | null
  lastCheckedAt: string | null
  createdAt: string
  // TODO: Remove manual addition once CI OpenAPI pipeline regenerates types from backend (Story 5.1)
  latestSha256Hash?: string | null
}

export interface AddWatchlistEntryResponse {
  entry: WatchlistEntryResponse
  duplicate: boolean
}

export interface WatchlistCountResponse {
  count: number
}

// ─── Portfolio Alerts DTOs (Story 3.9) ──────────────────────────────────────

export interface PortfolioAlertResponse {
  alertId: string
  tenantId: string
  tenantName: string
  taxNumber: string
  companyName: string | null
  previousStatus: string | null
  newStatus: string | null
  changedAt: string
  sha256Hash: string | null
  verdictId: string | null
}

// ─── Flight Control DTOs (Story 3.10) ───────────────────────────────────────
// NOTE: These are temporary placeholders until CI auto-regenerates api.d.ts from OpenAPI spec.
// Once regenerated, remove these manual additions and verify `tsc --noEmit` passes.

export interface FlightControlTenantSummaryResponse {
  tenantId: string
  tenantName: string
  reliableCount: number
  atRiskCount: number
  staleCount: number
  incompleteCount: number
  totalPartners: number
  lastCheckedAt: string | null
}

export interface FlightControlTotals {
  totalClients: number
  totalAtRisk: number
  totalStale: number
  totalPartners: number
}

export interface FlightControlResponse {
  totals: FlightControlTotals
  tenants: FlightControlTenantSummaryResponse[]
}

// ─── Audit History DTOs (Story 5.1a) ────────────────────────────────────────
// TODO: Remove manual definitions below once CI OpenAPI pipeline regenerates types
// from AuditHistoryEntryResponse.java / AuditHistoryPageResponse.java / AuditHashVerifyResponse.java.

export interface AuditHistoryEntryResponse {
  id: string
  companyName: string | null
  taxNumber: string
  verdictStatus: 'RELIABLE' | 'AT_RISK' | 'INCOMPLETE' | 'TAX_SUSPENDED' | 'UNAVAILABLE' | null
  verdictConfidence: 'FRESH' | 'STALE' | 'UNAVAILABLE' | null
  searchedAt: string
  sha256Hash: string
  dataSourceMode: 'DEMO' | 'LIVE'
  checkSource: 'MANUAL' | 'AUTOMATED'
  sourceUrls: string[]
  disclaimerText: string | null
}

export interface AuditHistoryPageResponse {
  content: AuditHistoryEntryResponse[]
  totalElements: number
  page: number
  size: number
}

export interface AuditHashVerifyResponse {
  match: boolean
  computedHash: string
  storedHash: string
  unavailable: boolean
}
