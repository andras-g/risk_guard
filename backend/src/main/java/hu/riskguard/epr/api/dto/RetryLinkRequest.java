package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request for {@code POST /wizard/retry-link} — retry linking a saved calculation to a template.
 *
 * @param calculationId the UUID of the existing epr_calculations record
 * @param templateId    the UUID of the template to link the KF-code to
 */
public record RetryLinkRequest(
        @NotNull UUID calculationId,
        @NotNull UUID templateId
) {}
