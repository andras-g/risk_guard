package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for publishing a new EPR config version.
 */
public record EprConfigPublishRequest(@NotBlank String configData) {}
