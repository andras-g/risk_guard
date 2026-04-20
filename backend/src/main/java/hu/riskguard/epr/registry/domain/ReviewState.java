package hu.riskguard.epr.registry.domain;

/**
 * Attention flags for products requiring human review (Story 10.4).
 * Stored in {@code products.review_state}; null = no review needed.
 */
public enum ReviewState {
    MISSING_PACKAGING
}
