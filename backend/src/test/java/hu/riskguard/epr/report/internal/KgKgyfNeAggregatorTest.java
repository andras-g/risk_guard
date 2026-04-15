package hu.riskguard.epr.report.internal;

import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.KfCodeAggregate;
import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.RegistryWeightContribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KgKgyfNeAggregator}.
 * AC 4: aggregation must be deterministic, correct, and alphabetically sorted.
 */
class KgKgyfNeAggregatorTest {

    private KgKgyfNeAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new KgKgyfNeAggregator();
    }

    @Test
    void empty_input_returns_empty_list() {
        assertThat(aggregator.aggregate(List.of())).isEmpty();
    }

    @Test
    void null_input_returns_empty_list() {
        assertThat(aggregator.aggregate(null)).isEmpty();
    }

    @Test
    void single_kf_code_aggregated_correctly() {
        List<RegistryWeightContribution> input = List.of(
                new RegistryWeightContribution("CS01012B", new BigDecimal("1.500")),
                new RegistryWeightContribution("CS01012B", new BigDecimal("2.250"))
        );
        List<KfCodeAggregate> result = aggregator.aggregate(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).kfCode()).isEqualTo("CS01012B");
        assertThat(result.get(0).totalWeightKg()).isEqualByComparingTo("3.750");
        assertThat(result.get(0).lineCount()).isEqualTo(2);
    }

    @Test
    void multi_kf_sorted_alphabetically() {
        List<RegistryWeightContribution> input = List.of(
                new RegistryWeightContribution("PL02011A", new BigDecimal("5.000")),
                new RegistryWeightContribution("CS01012B", new BigDecimal("2.000")),
                new RegistryWeightContribution("AL03021B", new BigDecimal("1.000"))
        );
        List<KfCodeAggregate> result = aggregator.aggregate(input);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).kfCode()).isEqualTo("AL03021B");
        assertThat(result.get(1).kfCode()).isEqualTo("CS01012B");
        assertThat(result.get(2).kfCode()).isEqualTo("PL02011A");
    }

    @Test
    void rounding_applied_half_up_to_three_decimal_places() {
        // 1.0005 rounds to 1.001 with HALF_UP
        List<RegistryWeightContribution> input = List.of(
                new RegistryWeightContribution("CS01012B", new BigDecimal("0.5003")),
                new RegistryWeightContribution("CS01012B", new BigDecimal("0.5002"))
        );
        List<KfCodeAggregate> result = aggregator.aggregate(input);
        assertThat(result).hasSize(1);
        // 0.5003 + 0.5002 = 1.0005 → rounds to 1.001 (HALF_UP)
        assertThat(result.get(0).totalWeightKg()).isEqualByComparingTo("1.001");
    }

    @Test
    void large_numbers_do_not_overflow() {
        List<RegistryWeightContribution> input = List.of(
                new RegistryWeightContribution("CS01012B", new BigDecimal("999999.999")),
                new RegistryWeightContribution("CS01012B", new BigDecimal("999999.999"))
        );
        List<KfCodeAggregate> result = aggregator.aggregate(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalWeightKg()).isEqualByComparingTo("1999999.998");
    }

    @Test
    void determinism_same_input_same_output() {
        List<RegistryWeightContribution> input = List.of(
                new RegistryWeightContribution("PL02011A", new BigDecimal("3.14")),
                new RegistryWeightContribution("CS01012B", new BigDecimal("2.71")),
                new RegistryWeightContribution("PL02011A", new BigDecimal("1.00"))
        );
        List<KfCodeAggregate> result1 = aggregator.aggregate(input);
        List<KfCodeAggregate> result2 = aggregator.aggregate(input);
        assertThat(result1).hasSize(result2.size());
        for (int i = 0; i < result1.size(); i++) {
            assertThat(result1.get(i).kfCode()).isEqualTo(result2.get(i).kfCode());
            assertThat(result1.get(i).totalWeightKg()).isEqualByComparingTo(result2.get(i).totalWeightKg());
        }
    }
}
