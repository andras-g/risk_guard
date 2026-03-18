package hu.riskguard.core.security;

/**
 * Subscription tier hierarchy: ALAP (free) < PRO < PRO_EPR.
 * A higher tier always satisfies a lower-tier requirement.
 * Ordinal comparison is used — enum order is authoritative.
 */
public enum Tier {
    ALAP,
    PRO,
    PRO_EPR;

    /**
     * Returns true if this tier satisfies (is equal to or higher than) the required tier.
     * Example: PRO.satisfies(ALAP) → true, ALAP.satisfies(PRO) → false.
     */
    public boolean satisfies(Tier required) {
        return this.ordinal() >= required.ordinal();
    }
}
