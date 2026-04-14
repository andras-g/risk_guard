package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.BootstrapCandidate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BootstrapCandidateResponse(
        UUID id,
        UUID tenantId,
        String productName,
        String vtsz,
        int frequency,
        BigDecimal totalQuantity,
        String unitOfMeasure,
        String status,
        String suggestedKfCode,
        String suggestedComponents,
        String classificationStrategy,
        String classificationConfidence,
        UUID resultingProductId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static BootstrapCandidateResponse from(BootstrapCandidate candidate) {
        return new BootstrapCandidateResponse(
                candidate.id(),
                candidate.tenantId(),
                candidate.productName(),
                candidate.vtsz(),
                candidate.frequency(),
                candidate.totalQuantity(),
                candidate.unitOfMeasure(),
                candidate.status() != null ? candidate.status().name() : null,
                candidate.suggestedKfCode(),
                candidate.suggestedComponents(),
                candidate.classificationStrategy(),
                candidate.classificationConfidence(),
                candidate.resultingProductId(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }
}
