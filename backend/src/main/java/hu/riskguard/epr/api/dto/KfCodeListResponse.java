package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

import java.util.List;

/**
 * Response DTO for {@code GET /wizard/kf-codes} — all valid KF-codes for manual override.
 *
 * @param configVersion the config version these codes were enumerated from
 * @param entries       flat list of all valid KF-code entries
 */
public record KfCodeListResponse(
        int configVersion,
        List<KfCodeEntry> entries
) {

    /**
     * Map from DagEngine domain list to API response DTO.
     */
    public static KfCodeListResponse from(int configVersion, List<DagEngine.KfCodeEntry> entries) {
        return new KfCodeListResponse(
                configVersion,
                entries.stream().map(KfCodeEntry::from).toList()
        );
    }
}
