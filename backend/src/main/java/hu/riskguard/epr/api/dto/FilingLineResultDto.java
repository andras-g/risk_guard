package hu.riskguard.epr.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FilingLineResultDto(
        UUID templateId,
        String name,
        String kfCode,
        int quantityPcs,
        BigDecimal baseWeightGrams,
        BigDecimal totalWeightGrams,
        BigDecimal totalWeightKg,
        BigDecimal feeRateHufPerKg,
        BigDecimal feeAmountHuf
) {}
