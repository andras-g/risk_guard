package hu.riskguard.screening.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a company snapshot.
 *
 * <p>Story 2.3 added the VerdictEngine but the snapshot remains a raw JSONB blob.
 * The typed snapshot view ({@link hu.riskguard.screening.domain.SnapshotData}) is internal
 * to the domain layer and is not exposed via API. This DTO may evolve to include parsed
 * fields (e.g., source availability) when the Verdict Card UI (Story 2.4) requires them.
 *
 * @param id           snapshot ID
 * @param taxNumber    the company's tax number
 * @param snapshotData raw data source results as JSON string
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
    /**
     * Map from primitive values to API response DTO.
     * The JSONB → String conversion should happen in the repository/service layer before calling this.
     */
    public static CompanySnapshotResponse from(UUID id, String taxNumber, String snapshotData,
                                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new CompanySnapshotResponse(id, taxNumber,
                snapshotData != null ? snapshotData : "{}",
                createdAt, updatedAt);
    }
}
