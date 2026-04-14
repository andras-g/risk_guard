package hu.riskguard.epr.registry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight product projection used for list views.
 * Does not include the full component list — use {@link Product} for that.
 */
public record ProductSummary(
        UUID id,
        UUID tenantId,
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        int componentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
