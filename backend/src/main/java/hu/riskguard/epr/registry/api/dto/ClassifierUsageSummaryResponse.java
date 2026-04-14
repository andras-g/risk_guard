package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.ClassifierUsageSummary;

import java.util.UUID;

/**
 * DTO for PLATFORM_ADMIN AI classifier usage cost meter.
 */
public record ClassifierUsageSummaryResponse(
        UUID tenantId,
        String tenantName,
        int callCount,
        double estimatedCostFt
) {
    public static ClassifierUsageSummaryResponse from(ClassifierUsageSummary summary) {
        return new ClassifierUsageSummaryResponse(
                summary.tenantId(),
                summary.tenantName(),
                summary.callCount(),
                summary.estimatedCostFt()
        );
    }
}
