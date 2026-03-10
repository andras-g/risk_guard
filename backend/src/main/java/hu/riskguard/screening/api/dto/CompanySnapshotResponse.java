package hu.riskguard.screening.api.dto;

import org.jooq.JSONB;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a company snapshot.
 *
 * @param id           snapshot ID
 * @param taxNumber    the company's tax number
 * @param snapshotData raw scraping results as JSON
 * @param createdAt    when the snapshot was created
 * @param updatedAt    when the snapshot was last updated
 */
public record CompanySnapshotResponse(
        UUID id,
        String taxNumber,
        String snapshotData,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CompanySnapshotResponse from(UUID id, String taxNumber, JSONB snapshotData,
                                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new CompanySnapshotResponse(id, taxNumber,
                snapshotData != null ? snapshotData.data() : "{}",
                createdAt, updatedAt);
    }
}
