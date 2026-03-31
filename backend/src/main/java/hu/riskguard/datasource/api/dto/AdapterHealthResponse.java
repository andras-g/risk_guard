package hu.riskguard.datasource.api.dto;

import java.time.Instant;

/**
 * Response DTO for a single data source adapter's health state.
 * Returned from {@code GET /api/v1/admin/datasources/health}.
 */
public record AdapterHealthResponse(
        String adapterName,
        String circuitBreakerState,
        double successRatePct,
        int failureCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Double mtbfHours,
        String dataSourceMode,
        String credentialStatus
) {

    public static AdapterHealthResponse from(
            String adapterName,
            String circuitBreakerState,
            double successRatePct,
            int failureCount,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            Double mtbfHours,
            String dataSourceMode,
            String credentialStatus
    ) {
        return new AdapterHealthResponse(
                adapterName,
                circuitBreakerState,
                successRatePct,
                failureCount,
                lastSuccessAt,
                lastFailureAt,
                mtbfHours,
                dataSourceMode,
                credentialStatus
        );
    }
}
