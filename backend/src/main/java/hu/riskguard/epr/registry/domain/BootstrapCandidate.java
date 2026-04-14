package hu.riskguard.epr.registry.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a deduplicated NAV invoice line item staged for
 * human triage before promotion to a registry product.
 */
public record BootstrapCandidate(
        UUID id,
        UUID tenantId,
        String productName,
        String vtsz,
        int frequency,
        BigDecimal totalQuantity,
        String unitOfMeasure,
        BootstrapCandidateStatus status,
        String suggestedKfCode,
        String suggestedComponents,   // JSONB stored as raw JSON string
        String classificationStrategy,
        String classificationConfidence,
        UUID resultingProductId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
