package hu.riskguard.epr.registry.bootstrap.api;

import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobRecord;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for GET /api/v1/registry/bootstrap-from-invoices/{jobId}.
 */
public record BootstrapJobStatusResponse(
        UUID jobId,
        BootstrapJobStatus status,
        LocalDate periodFrom,
        LocalDate periodTo,
        int totalPairs,
        int classifiedPairs,
        int vtszFallbackPairs,
        int unresolvedPairs,
        int createdProducts,
        int deletedProducts,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
    public static BootstrapJobStatusResponse from(BootstrapJobRecord record) {
        return new BootstrapJobStatusResponse(
                record.id(),
                record.status(),
                record.periodFrom(),
                record.periodTo(),
                record.totalPairs(),
                record.classifiedPairs(),
                record.vtszFallbackPairs(),
                record.unresolvedPairs(),
                record.createdProducts(),
                record.deletedProducts(),
                record.errorMessage(),
                record.createdAt(),
                record.updatedAt(),
                record.completedAt()
        );
    }
}
