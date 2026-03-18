package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.ScreeningService.SearchResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a verdict result.
 * Maps from the domain's enum-typed SearchResult to JSON-friendly string values.
 *
 * @param id          verdict ID
 * @param snapshotId  linked company snapshot ID
 * @param taxNumber   the queried tax number
 * @param status      verdict status (RELIABLE, AT_RISK, INCOMPLETE, TAX_SUSPENDED, UNAVAILABLE)
 * @param confidence  data confidence (FRESH, STALE, UNAVAILABLE)
 * @param createdAt   when the verdict was created
 * @param riskSignals list of reason codes explaining the verdict (e.g., ["PUBLIC_DEBT_DETECTED", "SOURCE_UNAVAILABLE:nav-debt"]).
 *                    May be empty for cached results — check {@code cached} flag.
 * @param cached      true if this result was served from the idempotency cache. When true,
 *                    {@code riskSignals} may be empty even for non-RELIABLE verdicts. Frontend
 *                    should display "reasons not available for cached result" in this case.
 * @param companyName company display name from the first adapter that provided it; null if unavailable
 * @param sha256Hash  SHA-256 audit hash from the search_audit_log for legal proof display;
 *                    null for cached results (audit log entry was written on the original search)
 */
public record VerdictResponse(
        UUID id,
        UUID snapshotId,
        String taxNumber,
        String status,
        String confidence,
        OffsetDateTime createdAt,
        List<String> riskSignals,
        boolean cached,
        String companyName,
        String sha256Hash
) {
    /**
     * Map from domain SearchResult to API response DTO.
     * Converts enum values to their string literals for JSON serialization.
     */
    public static VerdictResponse from(SearchResult result) {
        return new VerdictResponse(
                result.verdictId(),
                result.snapshotId(),
                result.taxNumber(),
                result.status().getLiteral(),
                result.confidence().getLiteral(),
                result.createdAt(),
                result.riskSignals(),
                result.cached(),
                result.companyName(),
                result.sha256Hash()
        );
    }
}
