package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.ScreeningService.AuditHistoryPage;

import java.util.List;

/**
 * Paginated response for {@code GET /api/screening/audit-history}.
 */
public record AuditHistoryPageResponse(
        List<AuditHistoryEntryResponse> content,
        long totalElements,
        int page,
        int size
) {
    public static AuditHistoryPageResponse from(AuditHistoryPage p) {
        List<AuditHistoryEntryResponse> content = p.entries().stream()
                .map(AuditHistoryEntryResponse::from)
                .toList();
        return new AuditHistoryPageResponse(content, p.totalElements(), p.page(), p.size());
    }
}
