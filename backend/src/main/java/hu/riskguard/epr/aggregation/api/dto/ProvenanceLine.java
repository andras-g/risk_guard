package hu.riskguard.epr.aggregation.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-component provenance line returned by the REST provenance endpoint (Story 10.8 AC #2).
 *
 * <p>For UNRESOLVED / UNSUPPORTED_UNIT lines the component fields
 * ({@code resolvedProductId}, {@code productName}, {@code componentId},
 * {@code wrappingLevel}, {@code componentKfCode}) are {@code null} and
 * {@code weightContributionKg} is zero.
 */
public record ProvenanceLine(
        String invoiceNumber,
        int lineNumber,
        String vtsz,
        String description,
        BigDecimal quantity,
        String unitOfMeasure,
        UUID resolvedProductId,
        String productName,
        UUID componentId,
        Integer wrappingLevel,
        String componentKfCode,
        BigDecimal weightContributionKg,
        ProvenanceTag provenanceTag
) {}
