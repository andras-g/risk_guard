package hu.riskguard.notification.domain;

import java.util.UUID;

/**
 * Represents a monitored partner from the watchlist — tenant + tax number pair.
 * Used by {@code AsyncIngestor} to iterate all actively monitored partners cross-tenant.
 *
 * @param tenantId  the tenant that owns this watchlist entry
 * @param taxNumber the Hungarian tax number being monitored (8 or 11 digits)
 */
public record WatchlistPartner(UUID tenantId, String taxNumber) {}
