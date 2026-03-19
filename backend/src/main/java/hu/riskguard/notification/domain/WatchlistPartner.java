package hu.riskguard.notification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a monitored partner from the watchlist — tenant + tax number pair.
 * Used by {@code AsyncIngestor} to iterate all actively monitored partners cross-tenant.
 *
 * @param tenantId  the tenant that owns this watchlist entry (must not be null)
 * @param taxNumber the Hungarian tax number being monitored — 8 or 11 digits (must not be null)
 */
public record WatchlistPartner(UUID tenantId, String taxNumber) {

    /**
     * Compact constructor — enforces non-null invariants.
     * Prevents silent NPE downstream in {@code TenantContext.setCurrentTenant(null)}.
     */
    public WatchlistPartner {
        Objects.requireNonNull(tenantId, "WatchlistPartner.tenantId must not be null");
        Objects.requireNonNull(taxNumber, "WatchlistPartner.taxNumber must not be null");
    }
}
