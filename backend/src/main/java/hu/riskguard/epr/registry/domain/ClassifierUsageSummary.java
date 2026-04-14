package hu.riskguard.epr.registry.domain;

import java.util.UUID;

/**
 * Monthly AI classifier usage summary for a single tenant.
 * Used by PLATFORM_ADMIN cost meter (Story 9.3 AC 6).
 */
public record ClassifierUsageSummary(
        UUID tenantId,
        String tenantName,
        int callCount,
        double estimatedCostFt
) {}
