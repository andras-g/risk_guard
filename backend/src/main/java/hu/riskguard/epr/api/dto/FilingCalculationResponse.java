package hu.riskguard.epr.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record FilingCalculationResponse(
        List<FilingLineResultDto> lines,
        BigDecimal grandTotalWeightKg,
        BigDecimal grandTotalFeeHuf,
        int configVersion
) {
    public static FilingCalculationResponse from(
            List<FilingLineResultDto> lines,
            BigDecimal grandTotalWeightKg,
            BigDecimal grandTotalFeeHuf,
            int configVersion) {
        return new FilingCalculationResponse(lines, grandTotalWeightKg, grandTotalFeeHuf, configVersion);
    }
}
