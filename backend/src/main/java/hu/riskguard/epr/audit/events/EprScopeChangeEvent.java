package hu.riskguard.epr.audit.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable event describing a single product's {@code epr_scope} change — one of the
 * compliance-relevant audit triplets required by Story 10.11 AC #12/#14.
 *
 * <p>Used by {@link hu.riskguard.epr.audit.AuditService#recordEprScopeChanged(UUID, UUID, String, String, UUID)}
 * and the batch variant. The compact constructor rejects null {@code productId} and blank/null scope
 * strings up front so an invalid event cannot reach the DB.
 *
 * @param productId target product (non-null)
 * @param fromScope prior scope (nullable — e.g., first-time scope assignment post-Story 10.11 upgrade)
 * @param toScope   new scope (non-null, non-blank)
 */
public record EprScopeChangeEvent(UUID productId, String fromScope, String toScope) {

    public EprScopeChangeEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        if (toScope == null || toScope.isBlank()) {
            throw new IllegalArgumentException("toScope must not be blank");
        }
    }
}
