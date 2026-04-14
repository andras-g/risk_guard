package hu.riskguard.epr.registry.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/registry/bootstrap/candidates/{id}/reject}.
 *
 * @param rejectionReason either {@code "NOT_OWN_PACKAGING"} or {@code "NEEDS_MANUAL"}
 */
public record BootstrapRejectRequest(
        @NotBlank String rejectionReason
) {}
