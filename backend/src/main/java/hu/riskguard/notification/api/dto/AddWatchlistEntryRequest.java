package hu.riskguard.notification.api.dto;

import hu.riskguard.core.validation.HungarianTaxNumber;

/**
 * Request DTO for adding a partner to the watchlist.
 *
 * @param taxNumber    Hungarian tax number (8 or 11 digits), validated via {@code @HungarianTaxNumber}
 * @param companyName  company name from the screening result (optional, denormalized for display)
 * @param verdictStatus current verdict status from the screening result (optional, denormalized for immediate display)
 */
public record AddWatchlistEntryRequest(
        @HungarianTaxNumber String taxNumber,
        String companyName,
        String verdictStatus
) {}
