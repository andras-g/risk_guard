package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for OKIRkapu XML export and preview endpoints.
 */
public record OkirkapuExportRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @NotBlank String taxNumber
) {}
