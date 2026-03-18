package hu.riskguard.screening.domain;

import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;

import java.util.List;

/**
 * Immutable result produced by {@link VerdictEngine#evaluate}.
 * Contains the computed verdict status, data confidence level, and accumulated risk signal reason codes.
 *
 * @param status      deterministic verdict status (RELIABLE, AT_RISK, INCOMPLETE, TAX_SUSPENDED)
 * @param confidence  data freshness confidence (FRESH, STALE, UNAVAILABLE)
 * @param riskSignals list of reason codes explaining the verdict (empty when RELIABLE)
 */
public record VerdictResult(
        VerdictStatus status,
        VerdictConfidence confidence,
        List<String> riskSignals
) {
    /**
     * Compact constructor — enforces immutability of the riskSignals list.
     * Callers need not worry about defensive copying; the record handles it.
     */
    public VerdictResult {
        riskSignals = List.copyOf(riskSignals);
    }
}
