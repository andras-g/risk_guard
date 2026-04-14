package hu.riskguard.epr.registry.api.dto;

import java.util.List;

public record RegistryPageResponse(
        List<ProductSummaryResponse> items,
        long total,
        int page,
        int size
) {
    public static RegistryPageResponse from(List<ProductSummaryResponse> items, long total,
                                             int page, int size) {
        return new RegistryPageResponse(items, total, page, size);
    }
}
