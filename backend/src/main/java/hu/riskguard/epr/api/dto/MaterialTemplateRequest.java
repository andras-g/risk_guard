package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for creating or updating a material template.
 *
 * @param name            the template display name (required, non-blank)
 * @param baseWeightGrams the base weight in grams (required, must be > 0)
 * @param recurring       whether this material recurs every quarter (optional, defaults to true)
 */
public record MaterialTemplateRequest(
        @NotBlank String name,
        @Positive BigDecimal baseWeightGrams,
        Boolean recurring
) {
}
