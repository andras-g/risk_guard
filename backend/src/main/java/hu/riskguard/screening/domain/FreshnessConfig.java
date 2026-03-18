package hu.riskguard.screening.domain;

/**
 * Immutable configuration for data freshness thresholds.
 * Values originate from {@code risk-guard-tokens.json} via {@code RiskGuardProperties.Freshness}.
 *
 * @param freshThresholdHours       hours below which confidence is FRESH
 * @param staleThresholdHours       hours below which confidence is STALE (informational, not used in engine directly)
 * @param unavailableThresholdHours hours at or above which confidence is UNAVAILABLE
 */
public record FreshnessConfig(
        int freshThresholdHours,
        int staleThresholdHours,
        int unavailableThresholdHours
) {}
