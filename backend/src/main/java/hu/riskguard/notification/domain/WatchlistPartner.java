package hu.riskguard.notification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a monitored partner from the watchlist — tenant + tax number + owner user pair.
 * Used by {@code AsyncIngestor} to iterate all actively monitored partners cross-tenant.
 *
 * @param tenantId  the tenant that owns this watchlist entry (must not be null)
 * @param taxNumber the Hungarian tax number being monitored — 8 or 11 digits (must not be null)
 * @param userId    the user (accountant) who owns the watchlist entry; used as the audit log
 *                  {@code searched_by} when the ingestor writes AUTOMATED audit records.
 *                  May be null for legacy entries where no mandate exists.
 */
public record WatchlistPartner(UUID tenantId, String taxNumber, UUID userId) {

    /**
     * Compact constructor — enforces non-null invariants on required fields.
     * Prevents silent NPE downstream in {@code TenantContext.setCurrentTenant(null)}.
     * userId is allowed to be null (no mandate found for tenant).
     */
    public WatchlistPartner {
        Objects.requireNonNull(tenantId, "WatchlistPartner.tenantId must not be null");
        Objects.requireNonNull(taxNumber, "WatchlistPartner.taxNumber must not be null");
    }
}
