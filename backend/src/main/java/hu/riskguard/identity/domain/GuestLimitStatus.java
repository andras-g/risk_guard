package hu.riskguard.identity.domain;

/**
 * Result of a guest rate limit check.
 * Standalone enum exported through the {@link IdentityService} facade.
 */
public enum GuestLimitStatus {
    OK,
    COMPANY_LIMIT_REACHED,
    DAILY_LIMIT_REACHED
}
