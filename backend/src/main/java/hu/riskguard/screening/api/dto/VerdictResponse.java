package hu.riskguard.screening.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a verdict result.
 * In this story (2.1), status will always be {@code INCOMPLETE} since no scrapers are implemented yet.
 *
 * @param id         verdict ID
 * @param snapshotId linked company snapshot ID
 * @param taxNumber  the queried tax number
 * @param status     verdict status (RELIABLE, AT_RISK, INCOMPLETE, TAX_SUSPENDED, UNAVAILABLE)
 * @param confidence data confidence (FRESH, STALE, UNAVAILABLE)
 * @param createdAt  when the verdict was created
 */
public record VerdictResponse(
        UUID id,
        UUID snapshotId,
        String taxNumber,
        String status,
        String confidence,
        OffsetDateTime createdAt
) {
    public static VerdictResponse from(UUID verdictId, UUID snapshotId, String taxNumber,
                                        String status, String confidence, OffsetDateTime createdAt) {
        return new VerdictResponse(verdictId, snapshotId, taxNumber, status, confidence, createdAt);
    }
}
