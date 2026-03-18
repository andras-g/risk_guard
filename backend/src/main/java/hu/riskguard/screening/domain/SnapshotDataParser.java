package hu.riskguard.screening.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses raw JSONB snapshot data ({@code Map<String, Object>}) into a typed {@link SnapshotData} record.
 * Pure function — no Spring dependencies, no side effects, no database access.
 *
 * <p>The parser is dynamic — it iterates over ALL keys in the JSONB map rather than
 * hardcoding adapter names. This works with any adapter key: "demo", "nav-debt",
 * future "nav-online-szamla", etc. The contract is the field names inside the adapter data.
 *
 * <p>Parse rules:
 * <ul>
 *   <li>For each adapter key: check {@code available} field to set source availability</li>
 *   <li>Adapter key with {@code available: false} → source UNAVAILABLE</li>
 *   <li>{@code taxNumberStatus == "SUSPENDED"} → taxSuspended true (from any adapter)</li>
 *   <li>{@code hasPublicDebt == true} (boolean) → debt risk signal (from any adapter)</li>
 *   <li>{@code hasInsolvencyProceedings == true} (boolean) → insolvency risk signal (from any adapter)</li>
 *   <li>Any parsing exception for an adapter → treat that source as UNAVAILABLE (defensive)</li>
 * </ul>
 *
 * <p><strong>Important:</strong> Risk-relevant data fields (taxNumberStatus, hasPublicDebt,
 * hasInsolvencyProceedings) are extracted regardless of the {@code available} flag. If an adapter
 * returned partial data with {@code available: false}, the positive evidence is still surfaced
 * because "positive evidence is actionable" per the architecture design decision. The {@code available}
 * flag only controls the {@link SourceStatus} in {@code sourceAvailability}.
 */
public final class SnapshotDataParser {

    private SnapshotDataParser() {
        // Utility class — no instantiation
    }

    /**
     * Parse raw JSONB snapshot data into a typed {@link SnapshotData} record.
     *
     * @param snapshotJsonb raw JSONB data from {@code company_snapshots.snapshot_data}; may be null
     * @return typed snapshot view with defensive defaults for missing/malformed data
     */
    public static SnapshotData parse(Map<String, Object> snapshotJsonb) {
        if (snapshotJsonb == null || snapshotJsonb.isEmpty()) {
            return new SnapshotData(false, false, false, Map.of(), null);
        }

        Map<String, SourceStatus> sourceAvailability = new LinkedHashMap<>();
        boolean taxSuspended = false;
        boolean hasPublicDebt = false;
        boolean hasInsolvencyProceedings = false;
        String companyName = null;

        // Dynamic parsing: iterate over ALL adapter keys in the JSONB map
        for (Map.Entry<String, Object> entry : snapshotJsonb.entrySet()) {
            String adapterName = entry.getKey();
            Map<String, Object> adapterData = extractAdapterMap(entry.getValue());

            if (adapterData == null) {
                sourceAvailability.put(adapterName, SourceStatus.UNAVAILABLE);
                continue;
            }

            // Determine source availability from the "available" field
            sourceAvailability.put(adapterName,
                    isAdapterAvailable(adapterData) ? SourceStatus.AVAILABLE : SourceStatus.UNAVAILABLE);

            // Extract risk-relevant data REGARDLESS of available flag
            // (positive evidence is actionable even from degraded sources)
            if ("SUSPENDED".equals(adapterData.get("taxNumberStatus"))) {
                taxSuspended = true;
            }
            if (Boolean.TRUE.equals(adapterData.get("hasPublicDebt"))) {
                hasPublicDebt = true;
            }
            if (Boolean.TRUE.equals(adapterData.get("hasInsolvencyProceedings"))) {
                hasInsolvencyProceedings = true;
            }

            // Extract companyName from the first adapter that provides it (prefer e-cegjegyzek / nav-online-szamla)
            if (companyName == null && adapterData.get("companyName") instanceof String name && !name.isBlank()) {
                companyName = name;
            }
        }

        return new SnapshotData(taxSuspended, hasPublicDebt, hasInsolvencyProceedings,
                Map.copyOf(sourceAvailability), companyName);
    }

    /**
     * Extract adapter data from a JSONB entry value. Returns null if the value is not a Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractAdapterMap(Object value) {
        try {
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return null;
        } catch (ClassCastException e) {
            // Defensive: malformed data — treat as unavailable
            return null;
        }
    }

    /**
     * Check if adapter data indicates the source was available.
     * Defaults to false if the {@code available} field is missing or not a boolean.
     */
    private static boolean isAdapterAvailable(Map<String, Object> adapterData) {
        return Boolean.TRUE.equals(adapterData.get("available"));
    }
}
