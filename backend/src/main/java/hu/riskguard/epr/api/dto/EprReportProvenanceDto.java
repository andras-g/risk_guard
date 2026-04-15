package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.report.EprReportProvenance;
import hu.riskguard.epr.report.ProvenanceTag;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO representation of a provenance line for REST responses.
 */
public record EprReportProvenanceDto(
        String invoiceNumber,
        int lineNumber,
        String vtszCode,
        String productName,
        BigDecimal quantity,
        String unitOfMeasure,
        ProvenanceTag tag,
        String resolvedKfCode,
        BigDecimal aggregatedWeightKg,
        UUID productId
) {

    public static EprReportProvenanceDto from(EprReportProvenance p) {
        return new EprReportProvenanceDto(
                p.invoiceNumber(), p.lineNumber(), p.vtszCode(), p.productName(),
                p.quantity(), p.unitOfMeasure(), p.tag(), p.resolvedKfCode(),
                p.aggregatedWeightKg(), p.productId()
        );
    }
}
