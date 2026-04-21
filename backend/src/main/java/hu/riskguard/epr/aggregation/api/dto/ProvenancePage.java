package hu.riskguard.epr.aggregation.api.dto;

import java.util.List;

/**
 * Paginated provenance response (Story 10.8 AC #1).
 */
public record ProvenancePage(
        List<ProvenanceLine> content,
        long totalElements,
        int page,
        int size
) {}
