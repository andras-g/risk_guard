package hu.riskguard.epr.aggregation.api.dto;

import java.time.LocalDate;

public record AggregationMetadata(
        int invoiceLineCount,
        int resolvedLineCount,
        int activeConfigVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        long aggregationDurationMs
) {}
