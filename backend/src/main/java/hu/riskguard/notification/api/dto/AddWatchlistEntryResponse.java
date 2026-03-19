package hu.riskguard.notification.api.dto;

import hu.riskguard.notification.domain.NotificationService.AddResult;

/**
 * Response DTO for the add-to-watchlist operation.
 * Wraps the entry data with a duplicate flag so the frontend can distinguish
 * between a newly created entry and a pre-existing one (idempotent return).
 *
 * @param entry     the watchlist entry (new or existing)
 * @param duplicate true if the tax number was already on the watchlist (no insert performed)
 */
public record AddWatchlistEntryResponse(
        WatchlistEntryResponse entry,
        boolean duplicate
) {

    /**
     * Factory method from service result.
     *
     * @param addResult the result from the NotificationService facade
     * @return the API response DTO with entry and duplicate flag
     */
    public static AddWatchlistEntryResponse from(AddResult addResult) {
        return new AddWatchlistEntryResponse(
                WatchlistEntryResponse.from(addResult.entry()),
                addResult.duplicate());
    }
}
