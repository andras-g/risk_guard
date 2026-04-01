package hu.riskguard.screening.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain record for a single admin audit entry returned by the cross-tenant audit query (Story 6.4).
 * Extends the user-facing audit history with tenant and user identification fields.
 *
 * @param id               audit log row UUID
 * @param tenantId         tenant that owns the audit entry
 * @param userId           user who performed the search (nullable — legacy rows)
 * @param companyName      company name from snapshot_data JSONB (nullable)
 * @param taxNumber        Hungarian tax number
 * @param verdictStatus    verdict status literal (e.g., "RELIABLE", "AT_RISK") — nullable for legacy rows
 * @param verdictConfidence verdict confidence literal (e.g., "FRESH", "STALE") — nullable for legacy rows
 * @param searchedAt       timestamp of the search
 * @param sha256Hash       64-char hex hash or "HASH_UNAVAILABLE" sentinel
 * @param dataSourceMode   "DEMO" or "LIVE"
 * @param checkSource      "MANUAL" or "AUTOMATED"
 * @param sourceUrls       list of source URLs from the snapshot (may be empty)
 * @param disclaimerText   disclaimer text included in the hash
 */
public record AdminAuditEntry(
        UUID id,
        UUID tenantId,
        UUID userId,
        String companyName,
        String taxNumber,
        String verdictStatus,
        String verdictConfidence,
        OffsetDateTime searchedAt,
        String sha256Hash,
        String dataSourceMode,
        String checkSource,
        List<String> sourceUrls,
        String disclaimerText
) {}
