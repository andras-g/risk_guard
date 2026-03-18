package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.SnapshotData;
import hu.riskguard.screening.domain.SourceStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for snapshot provenance data.
 * Exposes per-source availability details parsed from the snapshot JSONB.
 *
 * <p>Consumed by the Provenance Sidebar in the Verdict Detail page (Story 2.4).
 *
 * @param snapshotId  the snapshot this provenance data belongs to
 * @param taxNumber   the tax number associated with this snapshot
 * @param checkedAt   when the data was last fetched from the sources
 * @param sources     list of per-source provenance entries
 */
public record ProvenanceResponse(
        UUID snapshotId,
        String taxNumber,
        OffsetDateTime checkedAt,
        List<SourceProvenance> sources
) {

    /**
     * Per-source provenance entry with availability status and metadata.
     *
     * @param sourceName  canonical adapter name (e.g., "nav-debt", "e-cegjegyzek", "demo")
     * @param available   true if the source was reachable and returned data
     * @param checkedAt   when this source was last polled (same as snapshot checkedAt)
     * @param sourceUrl   URL of the data source for display as a tertiary link; may be null
     */
    public record SourceProvenance(
            String sourceName,
            boolean available,
            OffsetDateTime checkedAt,
            String sourceUrl
    ) {}

    /**
     * Build a ProvenanceResponse from a {@link SnapshotData} and snapshot metadata.
     * Parses per-source availability from the typed domain model.
     *
     * @param snapshotId the snapshot ID
     * @param taxNumber  the associated tax number
     * @param checkedAt  when the snapshot data was last fetched
     * @param parsed     typed snapshot data produced by {@code SnapshotDataParser}
     * @param sourceUrls map of adapter name to source URL (may be empty)
     * @return populated ProvenanceResponse
     */
    public static ProvenanceResponse from(UUID snapshotId, String taxNumber, OffsetDateTime checkedAt,
                                          SnapshotData parsed, Map<String, String> sourceUrls) {
        List<SourceProvenance> sources = parsed.sourceAvailability().entrySet().stream()
                .map(entry -> new SourceProvenance(
                        entry.getKey(),
                        entry.getValue() == SourceStatus.AVAILABLE,
                        checkedAt,
                        sourceUrls.getOrDefault(entry.getKey(), null)))
                .collect(Collectors.toList());

        return new ProvenanceResponse(snapshotId, taxNumber, checkedAt, sources);
    }
}
