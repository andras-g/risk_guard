package hu.riskguard.notification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a monitored partner from the watchlist with last known verdict status.
 * Used by {@code WatchlistMonitor} to compare old vs new verdict during the 24h monitoring cycle.
 *
 * <p>Extends the information in {@link WatchlistPartner} by including the
 * {@code lastVerdictStatus} stored on the {@code watchlist_entries} row.
 *
 * @param tenantId          the tenant that owns this watchlist entry (must not be null)
 * @param taxNumber         the Hungarian tax number being monitored (must not be null)
 * @param lastVerdictStatus the last known verdict status, or null if never evaluated
 */
public record MonitoredPartner(UUID tenantId, String taxNumber, String lastVerdictStatus) {

    /**
     * Compact constructor — enforces non-null invariants for required fields.
     */
    public MonitoredPartner {
        Objects.requireNonNull(tenantId, "MonitoredPartner.tenantId must not be null");
        Objects.requireNonNull(taxNumber, "MonitoredPartner.taxNumber must not be null");
        // lastVerdictStatus may be null (never evaluated)
    }
}
