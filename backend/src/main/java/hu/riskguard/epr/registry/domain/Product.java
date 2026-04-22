package hu.riskguard.epr.registry.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full product aggregate with its packaging bill-of-materials.
 */
public record Product(
        UUID id,
        UUID tenantId,
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        String eprScope,
        List<ProductPackagingComponent> components,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
