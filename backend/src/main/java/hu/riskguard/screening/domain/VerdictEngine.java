package hu.riskguard.screening.domain;

import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic verdict state-machine — the intellectual core of RiskGuard.
 *
 * <p>This is a <strong>pure function</strong>: no Spring beans, no database access, no side effects.
 * All inputs are received as method parameters. Trivially testable without a Spring context.
 *
 * <p>Evaluation priority order (most severe first):
 * <ol>
 *   <li>TAX_SUSPENDED — absolute priority (legal status from tax authority)</li>
 *   <li>UNAVAILABLE confidence — stale data forces INCOMPLETE</li>
 *   <li>Risk signals (debt/insolvency) — positive evidence is actionable → AT_RISK</li>
 *   <li>Source unavailability — missing data blocks RELIABLE → INCOMPLETE</li>
 *   <li>RELIABLE — only if all checks pass</li>
 * </ol>
 */
final class VerdictEngine {

    private VerdictEngine() {
        // Utility class — no instantiation
    }

    /**
     * Evaluate a company snapshot and produce a deterministic verdict.
     *
     * <p>This method is a <strong>pure function</strong>: given identical inputs it always produces
     * identical output. The {@code evaluationTime} parameter makes the clock dependency explicit,
     * ensuring deterministic behavior in tests without relying on system clock timing.
     *
     * @param data           typed snapshot data from {@link SnapshotDataParser}
     * @param checkedAt      timestamp when the snapshot data was collected; may be null
     * @param config         freshness thresholds from application configuration
     * @param evaluationTime the "now" reference point for freshness calculation (typically {@code OffsetDateTime.now()})
     * @return immutable verdict result with status, confidence, and risk signal reason codes
     */
    static VerdictResult evaluate(SnapshotData data, OffsetDateTime checkedAt,
                                  FreshnessConfig config, OffsetDateTime evaluationTime) {
        Objects.requireNonNull(data, "SnapshotData must not be null");
        Objects.requireNonNull(config, "FreshnessConfig must not be null");
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
        var riskSignals = new ArrayList<String>();

        // 1. TAX_SUSPENDED — absolute priority
        if (data.taxSuspended()) {
            riskSignals.add("TAX_NUMBER_SUSPENDED");
            return new VerdictResult(VerdictStatus.TAX_SUSPENDED,
                    computeConfidence(checkedAt, config, evaluationTime), List.copyOf(riskSignals));
        }

        // 2. Confidence/freshness — stale data invalidates verdict
        VerdictConfidence confidence = computeConfidence(checkedAt, config, evaluationTime);
        if (confidence == VerdictConfidence.UNAVAILABLE) {
            riskSignals.add("DATA_EXPIRED");
            return new VerdictResult(VerdictStatus.INCOMPLETE, confidence, List.copyOf(riskSignals));
        }

        // 3. Source availability — collect unavailable sources for risk signals
        boolean anyUnavailable = data.sourceAvailability().values().stream()
                .anyMatch(s -> s == SourceStatus.UNAVAILABLE);
        if (anyUnavailable) {
            data.sourceAvailability().forEach((name, status) -> {
                if (status == SourceStatus.UNAVAILABLE) {
                    riskSignals.add("SOURCE_UNAVAILABLE:" + name);
                }
            });
        }

        // 4. Risk signals — positive evidence is actionable
        if (data.hasPublicDebt()) riskSignals.add("PUBLIC_DEBT_DETECTED");
        if (data.hasInsolvencyProceedings()) riskSignals.add("INSOLVENCY_PROCEEDINGS_ACTIVE");

        // 5. Determine final status
        boolean hasRisk = data.hasPublicDebt() || data.hasInsolvencyProceedings();
        if (hasRisk) {
            return new VerdictResult(VerdictStatus.AT_RISK, confidence, List.copyOf(riskSignals));
        }
        if (anyUnavailable) {
            return new VerdictResult(VerdictStatus.INCOMPLETE, confidence, List.copyOf(riskSignals));
        }
        return new VerdictResult(VerdictStatus.RELIABLE, confidence, List.copyOf(riskSignals));
    }

    /**
     * Compute data freshness confidence from the snapshot timestamp.
     * Uses integer hour comparison via {@link Duration} — no floating-point arithmetic.
     *
     * <p>Note: {@code config.staleThresholdHours()} (24h) is intentionally not used as a decision
     * boundary here. The VerdictConfidence enum has three values (FRESH/STALE/UNAVAILABLE), so the
     * engine maps: &lt; freshThreshold (6h) → FRESH, &lt; unavailableThreshold (48h) → STALE,
     * ≥ unavailableThreshold → UNAVAILABLE. The staleThresholdHours value is retained in config
     * for future use (e.g., a "STALE with warning" UI indicator in Story 2-4) and for documentation
     * of the tiered freshness model defined in risk-guard-tokens.json.
     */
    private static VerdictConfidence computeConfidence(OffsetDateTime checkedAt, FreshnessConfig config,
                                                       OffsetDateTime evaluationTime) {
        if (checkedAt == null) return VerdictConfidence.UNAVAILABLE;
        long hours = Duration.between(checkedAt, evaluationTime).toHours();
        if (hours < config.freshThresholdHours()) return VerdictConfidence.FRESH;
        if (hours < config.unavailableThresholdHours()) return VerdictConfidence.STALE;
        return VerdictConfidence.UNAVAILABLE;
    }
}
