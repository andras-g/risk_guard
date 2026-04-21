package hu.riskguard.epr.aggregation.domain;

import hu.riskguard.epr.aggregation.api.dto.ProvenanceTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Internal per-component provenance capture produced during the aggregation pass (Story 10.8).
 *
 * <p>Immutable domain value — controller maps it to the public {@code ProvenanceLine} DTO.
 * {@code weightContributionKg} is stored rounded to 4 decimal places HALF_UP per AC #2.
 */
public record AggregationProvenanceLine(
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
) {

    /** Factory for UNRESOLVED / UNSUPPORTED_UNIT invoice lines — no component data. */
    static AggregationProvenanceLine unresolved(
            String invoiceNumber, int lineNumber, String vtsz, String description,
            BigDecimal quantity, String unitOfMeasure, ProvenanceTag tag) {
        return new AggregationProvenanceLine(
                invoiceNumber, lineNumber, vtsz, description, quantity, unitOfMeasure,
                null, null, null, null, null, BigDecimal.ZERO, tag);
    }

    /** Factory for a resolved component contribution. */
    static AggregationProvenanceLine resolved(
            String invoiceNumber, int lineNumber, String vtsz, String description,
            BigDecimal quantity, String unitOfMeasure,
            UUID resolvedProductId, String productName,
            UUID componentId, int wrappingLevel, String componentKfCode,
            BigDecimal rawWeight, ProvenanceTag tag) {
        BigDecimal rounded = rawWeight.setScale(4, RoundingMode.HALF_UP);
        return new AggregationProvenanceLine(
                invoiceNumber, lineNumber, vtsz, description, quantity, unitOfMeasure,
                resolvedProductId, productName, componentId, wrappingLevel, componentKfCode,
                rounded, tag);
    }
}
