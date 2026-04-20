package hu.riskguard.epr.registry.bootstrap.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a row in {@code epr_bootstrap_jobs}.
 */
public record BootstrapJobRecord(
        UUID id,
        UUID tenantId,
        BootstrapJobStatus status,
        LocalDate periodFrom,
        LocalDate periodTo,
        int totalPairs,
        int classifiedPairs,
        int vtszFallbackPairs,
        int unresolvedPairs,
        int createdProducts,
        int deletedProducts,
        UUID triggeredByUserId,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {}
