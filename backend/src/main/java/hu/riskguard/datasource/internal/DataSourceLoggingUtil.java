package hu.riskguard.datasource.internal;

/**
 * Shared utility for data source logging operations.
 * Centralizes the tax number masking logic used across all adapters and the aggregator.
 */
public final class DataSourceLoggingUtil {

    private DataSourceLoggingUtil() {
        // Utility class — no instantiation
    }

    /**
     * Mask the last 3 digits of a tax number for safe logging.
     * Complies with PII zero-tolerance policy (project-context rule).
     *
     * @param taxNumber the tax number to mask
     * @return masked tax number (e.g., "12345678***") or "***" if too short
     */
    public static String maskTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.length() <= 3) {
            return "***";
        }
        return taxNumber.substring(0, taxNumber.length() - 3) + "***";
    }
}
