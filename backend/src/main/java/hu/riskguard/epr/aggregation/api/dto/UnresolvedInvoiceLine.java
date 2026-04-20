package hu.riskguard.epr.aggregation.api.dto;

import hu.riskguard.epr.aggregation.api.UnresolvedReason;

import java.math.BigDecimal;

public record UnresolvedInvoiceLine(
        String invoiceNumber,
        int lineNumber,
        String vtsz,
        String description,
        BigDecimal quantity,
        String unitOfMeasure,
        UnresolvedReason reason
) {}
