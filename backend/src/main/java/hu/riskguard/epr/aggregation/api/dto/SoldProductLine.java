package hu.riskguard.epr.aggregation.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SoldProductLine(
        UUID productId,
        String vtsz,
        String description,
        BigDecimal totalQuantity,
        String unitOfMeasure,
        int matchingInvoiceLines
) {}
