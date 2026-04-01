package hu.riskguard.notification.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a full watchlist entry with enrichment data.
 * Exposed to the {@code api} layer via {@link NotificationService} facade methods.
 *
 * <p>This is the public domain type — controllers and DTOs use this instead of
 * the internal {@code NotificationRepository.WatchlistEntryRecord}.
 *
 * @param id                    entry UUID
 * @param tenantId              owning tenant
 * @param taxNumber             Hungarian tax number (8 or 11 digits)
 * @param companyName           company name at time of add (denormalized)
 * @param label                 optional user-defined label
 * @param createdAt             when added to watchlist
 * @param updatedAt             last update timestamp
 * @param verdictStatus         latest verdict status (e.g., RELIABLE, AT_RISK) or null
 * @param lastCheckedAt         timestamp of last screening, or null
 * @param latestSha256Hash      64-char hex SHA-256 hash from the most recent screening, or null
 * @param previousVerdictStatus verdict status before the most recent change, or null
 */
public record WatchlistEntry(
        UUID id,
        UUID tenantId,
        String taxNumber,
        String companyName,
        String label,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String verdictStatus,
        OffsetDateTime lastCheckedAt,
        String latestSha256Hash,
        String previousVerdictStatus
) {

    /**
     * Convenience constructor without verdict fields.
     */
    public WatchlistEntry(UUID id, UUID tenantId, String taxNumber, String companyName,
                          String label, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, tenantId, taxNumber, companyName, label, createdAt, updatedAt, null, null, null, null);
    }
}
