package hu.riskguard.epr.registry.api.dto;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/registry/bootstrap}.
 * Both dates are nullable — defaults are applied in the controller.
 */
public record BootstrapTriggerRequest(LocalDate from, LocalDate to) {}
