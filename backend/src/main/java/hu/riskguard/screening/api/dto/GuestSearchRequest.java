package hu.riskguard.screening.api.dto;

import hu.riskguard.core.validation.HungarianTaxNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for guest (unauthenticated) partner search.
 *
 * @param taxNumber          Hungarian tax number (8-digit or 11-digit format, hyphens allowed)
 * @param sessionFingerprint SHA-256 hash of the browser fingerprint (or random guest token)
 */
public record GuestSearchRequest(
        @HungarianTaxNumber
        String taxNumber,

        @NotBlank(message = "Session fingerprint is required")
        @Size(max = 255, message = "Session fingerprint must not exceed 255 characters")
        String sessionFingerprint
) {
}
