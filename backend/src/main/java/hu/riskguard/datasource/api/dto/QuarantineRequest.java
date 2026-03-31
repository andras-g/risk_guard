package hu.riskguard.datasource.api.dto;

/**
 * Request body for the quarantine toggle endpoint.
 * {@code quarantined = true} forces the adapter's circuit breaker to FORCED_OPEN.
 * {@code quarantined = false} releases it back to CLOSED.
 */
public record QuarantineRequest(boolean quarantined) {}
