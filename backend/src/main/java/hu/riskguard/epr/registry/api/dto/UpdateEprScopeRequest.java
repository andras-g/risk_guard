package hu.riskguard.epr.registry.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PATCH /api/v1/registry/products/{id}/epr-scope} — Story 10.11 AC #7.
 */
public record UpdateEprScopeRequest(
        @NotBlank
        @Pattern(regexp = "^(FIRST_PLACER|RESELLER|UNKNOWN)$",
                message = "scope must be FIRST_PLACER, RESELLER, or UNKNOWN")
        String scope
) {}
