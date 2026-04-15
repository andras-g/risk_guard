package hu.riskguard.epr.report.internal;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@link RegistryWeightContribution} records into per-KF-code weight totals.
 *
 * <p>Groups by kfCode, sums weightKg with 3-decimal HALF_UP rounding,
 * and returns results sorted alphabetically by kfCode.
 */
@Component
class KgKgyfNeAggregator {

    /**
     * Aggregate a list of registry weight contributions into per-KF-code totals.
     *
     * @param contributions source contributions (may be empty)
     * @return list of aggregates sorted alphabetically by kfCode; empty for empty input
     */
    List<KfCodeAggregate> aggregate(List<RegistryWeightContribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();

        for (RegistryWeightContribution c : contributions) {
            totals.merge(c.kfCode(), c.weightKg(), BigDecimal::add);
            counts.merge(c.kfCode(), 1L, Long::sum);
        }

        List<KfCodeAggregate> result = new ArrayList<>(totals.size());
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            BigDecimal rounded = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            result.add(new KfCodeAggregate(entry.getKey(), rounded, counts.get(entry.getKey())));
        }

        result.sort(Comparator.comparing(KfCodeAggregate::kfCode));
        return result;
    }

    // ─── Internal data types ──────────────────────────────────────────────────

    /**
     * A single weight contribution from a registry-matched invoice line component.
     */
    record RegistryWeightContribution(String kfCode, BigDecimal weightKg) {}

    /**
     * Aggregated result for a single KF code: total weight and number of source lines.
     */
    record KfCodeAggregate(String kfCode, BigDecimal totalWeightKg, long lineCount) {}
}
