package hu.riskguard.screening.domain;

import java.util.Map;

/**
 * Typed view of the raw JSONB snapshot data stored in {@code company_snapshots.snapshot_data}.
 * Produced by {@link SnapshotDataParser} from the raw {@code Map<String, Object>} JSONB representation.
 *
 * @param taxSuspended              true if NAV reports TAX_SUSPENDED status
 * @param hasPublicDebt             true if NAV reports active public debt
 * @param hasInsolvencyProceedings  true if Cégközlöny reports active insolvency proceedings
 * @param sourceAvailability        per-adapter availability keyed by canonical adapter name
 * @param companyName               company display name from the first available adapter that provides it;
 *                                  null if no adapter returned a company name
 */
public record SnapshotData(
        boolean taxSuspended,
        boolean hasPublicDebt,
        boolean hasInsolvencyProceedings,
        Map<String, SourceStatus> sourceAvailability,
        String companyName
) {
    /**
     * Convenience factory for test usage and cases where companyName is not needed.
     * Equivalent to {@code new SnapshotData(taxSuspended, hasPublicDebt, hasInsolvencyProceedings, sourceAvailability, null)}.
     */
    public SnapshotData(boolean taxSuspended, boolean hasPublicDebt, boolean hasInsolvencyProceedings,
                        Map<String, SourceStatus> sourceAvailability) {
        this(taxSuspended, hasPublicDebt, hasInsolvencyProceedings, sourceAvailability, null);
    }
}
