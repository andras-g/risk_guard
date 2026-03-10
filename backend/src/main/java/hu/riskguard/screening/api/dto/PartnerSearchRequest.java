package hu.riskguard.screening.api.dto;

import hu.riskguard.core.validation.HungarianTaxNumber;

/**
 * Request DTO for partner search.
 *
 * @param taxNumber Hungarian tax number (8-digit or 11-digit format, hyphens allowed)
 */
public record PartnerSearchRequest(
        @HungarianTaxNumber
        String taxNumber
) {
}
