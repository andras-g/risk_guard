package hu.riskguard.epr.registry.api.dto;

import java.util.List;

public record RegistryAuditPageResponse(
        List<RegistryAuditEntryResponse> items,
        long total,
        int page,
        int size
) {
    public static RegistryAuditPageResponse from(List<RegistryAuditEntryResponse> items, long total,
                                                  int page, int size) {
        return new RegistryAuditPageResponse(items, total, page, size);
    }
}
