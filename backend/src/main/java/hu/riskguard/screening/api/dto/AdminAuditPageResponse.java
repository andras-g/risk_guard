package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.ScreeningService.AdminAuditPage;

import java.util.List;

/**
 * Paginated response for {@code GET /api/v1/admin/screening/audit}.
 */
public record AdminAuditPageResponse(
        List<AdminAuditEntryResponse> content,
        long totalElements,
        int page,
        int size
) {
    public static AdminAuditPageResponse from(AdminAuditPage p) {
        List<AdminAuditEntryResponse> content = p.entries().stream()
                .map(AdminAuditEntryResponse::from)
                .toList();
        return new AdminAuditPageResponse(content, p.totalElements(), p.page(), p.size());
    }
}
