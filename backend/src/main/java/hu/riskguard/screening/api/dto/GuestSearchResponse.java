package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.ScreeningService.SearchResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for guest (unauthenticated) search results.
 * Extends the standard verdict data with guest usage statistics.
 *
 * @param id              verdict ID
 * @param snapshotId      linked company snapshot ID
 * @param taxNumber       the queried tax number
 * @param status          verdict status (RELIABLE, AT_RISK, INCOMPLETE, TAX_SUSPENDED, UNAVAILABLE)
 * @param confidence      data confidence (FRESH, STALE, UNAVAILABLE)
 * @param createdAt       when the verdict was created
 * @param riskSignals     list of reason codes explaining the verdict
 * @param cached          true if served from idempotency cache
 * @param companyName     company display name (nullable)
 * @param companiesUsed   number of unique companies searched by this guest
 * @param companiesLimit  maximum companies allowed for guests
 * @param dailyChecksUsed number of daily checks used by this guest
 * @param dailyChecksLimit maximum daily checks allowed for guests
 */
public record GuestSearchResponse(
        UUID id,
        UUID snapshotId,
        String taxNumber,
        String status,
        String confidence,
        OffsetDateTime createdAt,
        List<String> riskSignals,
        boolean cached,
        String companyName,
        int companiesUsed,
        int companiesLimit,
        int dailyChecksUsed,
        int dailyChecksLimit
) {
    /**
     * Map from domain SearchResult + guest usage stats to API response DTO.
     */
    public static GuestSearchResponse from(SearchResult result,
                                            int companiesUsed, int companiesLimit,
                                            int dailyChecksUsed, int dailyChecksLimit) {
        return new GuestSearchResponse(
                result.verdictId(),
                result.snapshotId(),
                result.taxNumber(),
                result.status().getLiteral(),
                result.confidence().getLiteral(),
                result.createdAt(),
                result.riskSignals(),
                result.cached(),
                result.companyName(),
                companiesUsed,
                companiesLimit,
                dailyChecksUsed,
                dailyChecksLimit
        );
    }
}
