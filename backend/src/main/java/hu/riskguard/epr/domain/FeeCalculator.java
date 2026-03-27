package hu.riskguard.epr.domain;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes EPR fee liability for a set of filing lines.
 * Each line: quantity_pcs × base_weight_grams → total_weight_kg × fee_rate → fee_amount_huf.
 * fee_rate is in HUF/kg from the active config (stored in epr_calculations.fee_rate).
 */
@Component
public class FeeCalculator {

    /**
     * Compute a single filing line.
     * @param quantityPcs    number of pieces sold (must be > 0)
     * @param baseWeightGrams weight per piece in grams
     * @param feeRateHufPerKg  EPR fee rate in HUF/kg from the active config
     */
    public FilingLineResult computeLine(int quantityPcs, BigDecimal baseWeightGrams, BigDecimal feeRateHufPerKg) {
        BigDecimal qty = BigDecimal.valueOf(quantityPcs);
        BigDecimal totalWeightGrams = baseWeightGrams.multiply(qty);
        BigDecimal totalWeightKg = totalWeightGrams.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        BigDecimal feeAmountHuf = totalWeightKg.multiply(feeRateHufPerKg).setScale(0, RoundingMode.HALF_UP);
        return new FilingLineResult(totalWeightGrams, totalWeightKg, feeAmountHuf);
    }

    /** Aggregate totals from all lines. */
    public FilingTotals computeTotals(List<FilingLineResult> lines) {
        BigDecimal totalWeightKg = lines.stream()
                .map(FilingLineResult::totalWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFeeHuf = lines.stream()
                .map(FilingLineResult::feeAmountHuf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FilingTotals(totalWeightKg, totalFeeHuf);
    }

    public record FilingLineResult(
            BigDecimal totalWeightGrams,
            BigDecimal totalWeightKg,
            BigDecimal feeAmountHuf
    ) {}

    public record FilingTotals(BigDecimal totalWeightKg, BigDecimal totalFeeHuf) {}
}
