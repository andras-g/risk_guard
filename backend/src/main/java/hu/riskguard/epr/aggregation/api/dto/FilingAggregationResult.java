package hu.riskguard.epr.aggregation.api.dto;

import java.util.List;

public record FilingAggregationResult(
        List<SoldProductLine> soldProducts,
        List<KfCodeTotal> kfTotals,
        List<UnresolvedInvoiceLine> unresolved,
        AggregationMetadata metadata
) {}
