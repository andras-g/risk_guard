package hu.riskguard.epr.report;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Traceability record linking an invoice line item to its EPR report contribution.
 * Stored in the artifact and returned in preview responses for auditor transparency.
 */
public record EprReportProvenance(
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
) {}
