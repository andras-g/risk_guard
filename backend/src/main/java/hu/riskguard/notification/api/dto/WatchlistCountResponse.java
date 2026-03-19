package hu.riskguard.notification.api.dto;

/**
 * Response DTO for the watchlist entry count.
 * Used by the sidebar badge to display how many partners are on the watchlist.
 *
 * @param count number of watchlist entries for the current tenant
 */
public record WatchlistCountResponse(int count) {

    /**
     * Factory method from raw count value.
     */
    public static WatchlistCountResponse from(int count) {
        return new WatchlistCountResponse(count);
    }
}
