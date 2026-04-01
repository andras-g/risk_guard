package hu.riskguard.notification.api.dto;

import hu.riskguard.notification.domain.WatchlistEntry;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a watchlist entry.
 * Includes denormalized company name and latest verdict status for display.
 *
 * @param id                    entry UUID
 * @param taxNumber             Hungarian tax number (8 or 11 digits)
 * @param companyName           company name at time of add (denormalized)
 * @param label                 optional user-defined label
 * @param currentVerdictStatus  latest verdict status (RELIABLE, AT_RISK, etc.) or null if never screened
 * @param lastCheckedAt         timestamp of last screening, or null
 * @param createdAt             when the entry was added to the watchlist
 * @param latestSha256Hash      64-char hex SHA-256 from most recent screening, or null
 * @param previousVerdictStatus verdict status before the most recent change, or null
 */
public record WatchlistEntryResponse(
        UUID id,
        String taxNumber,
        String companyName,
        String label,
        String currentVerdictStatus,
        OffsetDateTime lastCheckedAt,
        OffsetDateTime createdAt,
        String latestSha256Hash,
        String previousVerdictStatus
) {

    /**
     * Factory method from domain type — the canonical way to create response DTOs.
     * Controllers MUST use this factory, never direct DTO construction.
     *
     * @param entry the domain watchlist entry from the service facade
     * @return the API response DTO
     */
    public static WatchlistEntryResponse from(WatchlistEntry entry) {
        return new WatchlistEntryResponse(
                entry.id(),
                entry.taxNumber(),
                entry.companyName(),
                entry.label(),
                entry.verdictStatus(),
                entry.lastCheckedAt(),
                entry.createdAt(),
                entry.latestSha256Hash(),
                entry.previousVerdictStatus());
    }
}
