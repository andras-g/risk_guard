package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.Product;
import hu.riskguard.epr.registry.domain.ProductStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID tenantId,
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        String eprScope,
        List<ComponentResponse> components,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(), product.tenantId(), product.articleNumber(), product.name(),
                product.vtsz(), product.primaryUnit(), product.status(), product.eprScope(),
                product.components().stream().map(ComponentResponse::from).toList(),
                product.createdAt(), product.updatedAt()
        );
    }
}
