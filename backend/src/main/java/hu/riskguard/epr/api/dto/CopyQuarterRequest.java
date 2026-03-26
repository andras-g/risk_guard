package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for copying material templates from a previous quarter.
 *
 * @param sourceYear          the source year (e.g., 2026)
 * @param sourceQuarter       the source quarter (1–4)
 * @param includeNonRecurring whether to include non-recurring (one-time) templates in the copy
 */
public record CopyQuarterRequest(
        @Positive int sourceYear,
        @Min(1) @Max(4) int sourceQuarter,
        boolean includeNonRecurring
) {
}
