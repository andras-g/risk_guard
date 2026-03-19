package hu.riskguard.core.util;

/**
 * PII (Personally Identifiable Information) masking utilities for logging.
 *
 * <p>Per the project's PII zero-tolerance policy, raw tax numbers, names, and emails
 * must NEVER appear in log output. This utility provides canonical masking methods
 * shared by all modules — eliminating duplication across background jobs.
 *
 * @see hu.riskguard.screening.domain.AsyncIngestor
 * @see hu.riskguard.screening.domain.WatchlistMonitor
 */
public final class PiiUtil {

    private PiiUtil() {
        // Static utility — no instantiation
    }

    /**
     * Mask a tax number for logging — at most the first 4 characters are visible.
     * An 8-digit Hungarian tax number "12345678" becomes "1234****".
     *
     * @param taxNumber the raw tax number (may be null)
     * @return masked representation safe for logging
     */
    public static String maskTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.length() <= 4) {
            return "****";
        }
        return taxNumber.substring(0, 4) + "****";
    }
}
