package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for EPR config validation — carries the raw JSON to validate.
 */
public record EprConfigValidateRequest(@NotBlank String configData) {}
