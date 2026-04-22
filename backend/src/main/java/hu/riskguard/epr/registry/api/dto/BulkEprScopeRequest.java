package hu.riskguard.epr.registry.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/registry/products/bulk-epr-scope} — Story 10.11 AC #8.
 *
 * <p>Max 500 IDs per request (sized for typical HU SME registries, which top out around 500–1000
 * SKUs). Entire batch is rejected with 400 if any ID is missing or belongs to another tenant.
 */
public record BulkEprScopeRequest(
        @NotNull
        @NotEmpty
        @Size(max = 500, message = "At most 500 productIds per batch")
        List<UUID> productIds,
        @NotBlank
        @Pattern(regexp = "^(FIRST_PLACER|RESELLER|UNKNOWN)$",
                message = "scope must be FIRST_PLACER, RESELLER, or UNKNOWN")
        String scope
) {}
