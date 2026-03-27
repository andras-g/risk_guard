package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FilingLineRequest(
        @NotNull UUID templateId,
        @NotNull @Min(1) Integer quantityPcs
) {}
