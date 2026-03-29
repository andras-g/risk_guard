package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ExportLineRequest(
        @NotNull UUID templateId,
        @NotBlank String kfCode,
        @NotBlank String name,
        @NotNull @Positive int quantityPcs,
        @NotNull BigDecimal totalWeightKg,
        @NotNull BigDecimal feeAmountHuf
) {}
