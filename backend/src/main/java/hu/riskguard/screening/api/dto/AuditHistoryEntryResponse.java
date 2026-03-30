package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.AuditHistoryEntry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO for a single audit history entry returned by {@code GET /api/screening/audit-history}.
 */
public record AuditHistoryEntryResponse(
        String id,
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
) {
    public static AuditHistoryEntryResponse from(AuditHistoryEntry entry) {
        return new AuditHistoryEntryResponse(
                entry.id().toString(),
                entry.companyName(),
                entry.taxNumber(),
                entry.verdictStatus(),
                entry.verdictConfidence(),
                entry.searchedAt(),
                entry.sha256Hash(),
                entry.dataSourceMode(),
                entry.checkSource(),
                entry.sourceUrls(),
                entry.disclaimerText()
        );
    }
}
