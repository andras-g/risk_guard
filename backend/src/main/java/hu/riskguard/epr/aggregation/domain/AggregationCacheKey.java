package hu.riskguard.epr.aggregation.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cache key for Caffeine-based aggregation result cache.
 * Includes registry data freshness (registryMaxUpdatedAt) and active config version
 * so stale results are never served after Registry or config changes.
 */
public record AggregationCacheKey(
        UUID tenantId,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime registryMaxUpdatedAt,
        int activeConfigVersion
) {}
