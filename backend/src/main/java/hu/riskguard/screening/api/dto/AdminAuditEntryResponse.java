package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.AdminAuditEntry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO for a single admin audit entry returned by {@code GET /api/v1/admin/screening/audit}.
 */
public record AdminAuditEntryResponse(
        String id,
        String tenantId,
        String userId,
        String taxNumber,
        String verdictStatus,
        String verdictConfidence,
        OffsetDateTime searchedAt,
        String sha256Hash,
        String dataSourceMode,
        String checkSource,
        List<String> sourceUrls,
        String companyName
) {
    public static AdminAuditEntryResponse from(AdminAuditEntry entry) {
        return new AdminAuditEntryResponse(
                entry.id().toString(),
                entry.tenantId().toString(),
                entry.userId() != null ? entry.userId().toString() : null,
                entry.taxNumber(),
                entry.verdictStatus(),
                entry.verdictConfidence(),
                entry.searchedAt(),
                entry.sha256Hash(),
                entry.dataSourceMode(),
                entry.checkSource(),
                entry.sourceUrls(),
                entry.companyName()
        );
    }
}
