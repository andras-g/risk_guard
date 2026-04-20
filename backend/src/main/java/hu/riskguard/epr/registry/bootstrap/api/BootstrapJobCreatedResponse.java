package hu.riskguard.epr.registry.bootstrap.api;

import java.util.UUID;

/**
 * Response body for 202 Accepted on POST /api/v1/registry/bootstrap-from-invoices.
 */
public record BootstrapJobCreatedResponse(
        UUID jobId,
        String location
) {}
