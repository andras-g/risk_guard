package hu.riskguard.screening.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain record for a single audit history entry returned by the audit history query.
 * Combines data from search_audit_log, verdicts, and company_snapshots.
 *
 * @param id               audit log row UUID
 * @param companyName      company name from snapshot_data JSONB (nullable — legacy rows or unavailable data)
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
public record AuditHistoryEntry(
        UUID id,
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
