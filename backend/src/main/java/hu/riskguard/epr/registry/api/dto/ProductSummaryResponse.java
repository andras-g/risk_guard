package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.ProductStatus;
import hu.riskguard.epr.registry.domain.ProductSummary;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        int componentCount,
        OffsetDateTime updatedAt
) {
    public static ProductSummaryResponse from(ProductSummary summary) {
        return new ProductSummaryResponse(
                summary.id(), summary.articleNumber(), summary.name(), summary.vtsz(),
                summary.primaryUnit(), summary.status(), summary.componentCount(),
                summary.updatedAt()
        );
    }
}
