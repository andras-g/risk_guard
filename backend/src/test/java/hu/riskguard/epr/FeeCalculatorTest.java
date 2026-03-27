package hu.riskguard.epr;

import hu.riskguard.epr.domain.FeeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link FeeCalculator}.
 * No Spring context needed — FeeCalculator has no dependencies.
 */
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void computeLine_standardCardboardBox() {
        // 1000 pcs × 120g × 215 HUF/kg = 120 kg × 215 = 25,800 HUF
        FeeCalculator.FilingLineResult result = calculator.computeLine(
                1000,
                new BigDecimal("120"),
                new BigDecimal("215"));

        assertThat(result.totalWeightGrams()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(result.totalWeightKg()).isEqualByComparingTo(new BigDecimal("120.000000"));
        assertThat(result.feeAmountHuf()).isEqualByComparingTo(new BigDecimal("25800"));
    }

    @Test
    void computeLine_singleUnit() {
        // 1 pc × 50g × 215 HUF/kg = 0.05 kg × 215 = 10.75 → rounds to 11 HUF
        FeeCalculator.FilingLineResult result = calculator.computeLine(
                1,
                new BigDecimal("50"),
                new BigDecimal("215"));

        assertThat(result.totalWeightGrams()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.totalWeightKg()).isEqualByComparingTo(new BigDecimal("0.050000"));
        assertThat(result.feeAmountHuf()).isEqualByComparingTo(new BigDecimal("11"));
    }

    @Test
    void computeLine_largeQuantity() {
        // 100000 pcs × 5g × 130 HUF/kg = 500 kg × 130 = 65,000 HUF
        FeeCalculator.FilingLineResult result = calculator.computeLine(
                100000,
                new BigDecimal("5"),
                new BigDecimal("130"));

        assertThat(result.totalWeightGrams()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(result.totalWeightKg()).isEqualByComparingTo(new BigDecimal("500.000000"));
        assertThat(result.feeAmountHuf()).isEqualByComparingTo(new BigDecimal("65000"));
    }

    @Test
    void computeTotals_sumLines() {
        FeeCalculator.FilingLineResult line1 = calculator.computeLine(
                1000, new BigDecimal("120"), new BigDecimal("215")); // 25,800 HUF, 120 kg
        FeeCalculator.FilingLineResult line2 = calculator.computeLine(
                100000, new BigDecimal("5"), new BigDecimal("130")); // 65,000 HUF, 500 kg

        FeeCalculator.FilingTotals totals = calculator.computeTotals(List.of(line1, line2));

        assertThat(totals.totalWeightKg()).isEqualByComparingTo(new BigDecimal("620.000000"));
        assertThat(totals.totalFeeHuf()).isEqualByComparingTo(new BigDecimal("90800"));
    }
}
