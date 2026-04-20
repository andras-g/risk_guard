package hu.riskguard.epr.registry.bootstrap.api;

import java.time.LocalDate;

/**
 * Request body for POST /api/v1/registry/bootstrap-from-invoices.
 * Both fields are nullable — null means server-default last-3-complete-months.
 */
public record BootstrapTriggerRequest(
        LocalDate periodFrom,
        LocalDate periodTo
) {}
