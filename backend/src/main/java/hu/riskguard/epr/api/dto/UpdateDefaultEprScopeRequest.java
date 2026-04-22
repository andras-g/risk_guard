package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PATCH /api/v1/epr/producer-profile/default-epr-scope} — Story 10.11 AC #9.
 */
public record UpdateDefaultEprScopeRequest(
        @NotBlank
        @Pattern(regexp = "^(FIRST_PLACER|RESELLER|UNKNOWN)$",
                message = "defaultScope must be FIRST_PLACER, RESELLER, or UNKNOWN")
        String defaultScope
) {}
