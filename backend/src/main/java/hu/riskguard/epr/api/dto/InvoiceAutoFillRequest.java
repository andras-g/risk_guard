package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Request body for the invoice auto-fill endpoint.
 *
 * @param taxNumber 8–13 digit Hungarian tax number of the company to query invoices for
 * @param from      start of invoice issue date range (inclusive)
 * @param to        end of invoice issue date range (inclusive)
 */
public record InvoiceAutoFillRequest(
        @NotBlank @Pattern(regexp = "\\d{8,13}") String taxNumber,
        @NotNull LocalDate from,
        @NotNull LocalDate to
) {}
