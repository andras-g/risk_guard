package hu.riskguard.epr.registry.api.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/registry/classify}.
 */
public record ClassifyRequest(
        @NotBlank @Size(max = 512) String productName,
        @Nullable @Pattern(regexp = "^[0-9]{4,8}$") String vtsz
) {}
