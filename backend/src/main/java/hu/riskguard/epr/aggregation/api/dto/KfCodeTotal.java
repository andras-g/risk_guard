package hu.riskguard.epr.aggregation.api.dto;

import java.math.BigDecimal;

/**
 * Per-KF-code aggregated weight and fee total, used both in the API response and as
 * the input to OkirkapuXmlExporter after Story 10.5's signature refactor.
 */
public record KfCodeTotal(
        String kfCode,
        String classificationLabel,
        BigDecimal totalWeightKg,
        BigDecimal feeRateHufPerKg,
        BigDecimal totalFeeHuf,
        int contributingProductCount,
        boolean hasFallback,
        boolean hasOverflowWarning
) {}
