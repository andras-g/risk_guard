package hu.riskguard.epr.registry.api.dto;

/**
 * Snapshot of a tenant's classifier monthly cap usage (Story 10.3).
 *
 * <p>Returned in both the {@code 200 OK} happy path and the {@code 429} cap-exceeded path
 * of {@code POST /api/v1/classifier/batch-packaging} so the UI can show remaining cap
 * without a second roundtrip (AC #10).
 */
public record ClassifierUsageInfo(
        int callsUsedThisMonth,
        int callsRemaining,
        int monthlyCap
) {
    /**
     * Canonical factory (AC #3 — every response record exposes a {@code from(...)} entry point).
     * Computes {@code callsRemaining = max(0, cap - used)} so the field is never negative.
     */
    public static ClassifierUsageInfo from(int used, int cap) {
        return new ClassifierUsageInfo(used, Math.max(0, cap - used), cap);
    }

    /** Legacy alias — delegates to {@link #from(int, int)} to keep existing call sites compiling. */
    public static ClassifierUsageInfo of(int used, int cap) {
        return from(used, cap);
    }
}
