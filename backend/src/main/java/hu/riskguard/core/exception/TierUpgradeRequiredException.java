package hu.riskguard.core.exception;

import hu.riskguard.core.security.Tier;

/**
 * Thrown when a user's subscription tier is insufficient for the requested feature.
 * Handled by {@link TierGateExceptionHandler} to produce an RFC 7807 response.
 */
public class TierUpgradeRequiredException extends RuntimeException {

    private final Tier requiredTier;
    private final Tier currentTier;

    public TierUpgradeRequiredException(Tier requiredTier, Tier currentTier) {
        super("Tier upgrade required: need %s, have %s".formatted(
                requiredTier, currentTier != null ? currentTier : "UNKNOWN"));
        this.requiredTier = requiredTier;
        this.currentTier = currentTier;
    }

    public Tier getRequiredTier() {
        return requiredTier;
    }

    /** May be null if the user's tier could not be determined (fail-closed). */
    public Tier getCurrentTier() {
        return currentTier;
    }
}
