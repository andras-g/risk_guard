package hu.riskguard.datasource.internal.adapters.demo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static demo fixtures providing realistic Hungarian company data for 8 distinct scenarios.
 * Used by {@link DemoCompanyDataAdapter} to return deterministic data in demo mode.
 *
 * <p>Each fixture covers a specific verdict scenario as documented in the story.
 * The data structure matches what {@code SnapshotDataParser} expects from any adapter.
 */
public final class DemoCompanyFixtures {

    private DemoCompanyFixtures() {
        // Utility class — no instantiation
    }

    /** Map of 8-digit tax number → company fixture data. */
    private static final Map<String, Map<String, Object>> FIXTURES = new LinkedHashMap<>();

    static {
        // 1. Clean — no debt, no insolvency, active status → RELIABLE
        FIXTURES.put("12345678", buildFixture(
                true, "Példa Kereskedelmi Kft.", "01-09-123456",
                "ACTIVE", false, false, 0, "HUF", "ACTIVE"));

        // 2. Clean — established construction company → RELIABLE
        FIXTURES.put("99887766", buildFixture(
                true, "Megbízható Építő Zrt.", "13-10-041234",
                "ACTIVE", false, false, 0, "HUF", "ACTIVE"));

        // 3. Has public debt (HUF 2,450,000) → AT_RISK
        FIXTURES.put("11223344", buildFixture(
                true, "Adós Szolgáltató Bt.", "01-06-789012",
                "ACTIVE", true, false, 2_450_000, "HUF", "ACTIVE"));

        // 4. Active insolvency proceedings → AT_RISK
        FIXTURES.put("55667788", buildFixture(
                true, "Csődben Lévő Kft.", "07-09-345678",
                "ACTIVE", false, true, 0, "HUF", "ACTIVE"));

        // 5. Both public debt AND insolvency → AT_RISK
        FIXTURES.put("44556677", buildFixture(
                true, "Hátralékos és Csődös Kft.", "01-09-111222",
                "ACTIVE", true, true, 5_780_000, "HUF", "ACTIVE"));

        // 6. Tax number suspended → TAX_SUSPENDED
        FIXTURES.put("33445566", buildFixture(
                true, "Felfüggesztett Adószámú Kft.", "01-09-333444",
                "SUSPENDED", false, false, 0, "HUF", "INACTIVE"));

        // 7. Missing tax filings (risk signal) → AT_RISK
        FIXTURES.put("77889900", buildFixture(
                true, "Hiányos Bevallású Kft.", "14-09-556677",
                "ACTIVE", true, false, 890_000, "HUF", "ACTIVE"));

        // 8. Recently founded, clean but minimal history → RELIABLE
        FIXTURES.put("22334455", buildFixture(
                true, "Friss Startup Kft.", "01-09-998877",
                "ACTIVE", false, false, 0, "HUF", "ACTIVE"));
    }

    /**
     * Get company fixture data for the given 8-digit tax number.
     * Returns a generic clean company for unknown tax numbers.
     *
     * @param taxNumber8 the first 8 digits of the tax number
     * @return fixture data map matching SnapshotDataParser's expected structure
     */
    public static Map<String, Object> getCompanyData(String taxNumber8) {
        Map<String, Object> fixture = FIXTURES.get(taxNumber8);
        if (fixture != null) {
            return Map.copyOf(fixture);
        }
        // Unknown tax number → generic clean company (demo mode never returns unavailable)
        return buildFixture(
                true, "Ismeretlen Cég Kft.", "01-09-000000",
                "ACTIVE", false, false, 0, "HUF", "ACTIVE");
    }

    /**
     * Get all registered fixture tax numbers (8-digit format) for testing.
     *
     * @return unmodifiable map of tax number → fixture data
     */
    public static Map<String, Map<String, Object>> getAllFixtures() {
        return Map.copyOf(FIXTURES);
    }

    private static Map<String, Object> buildFixture(
            boolean available,
            String companyName,
            String registrationNumber,
            String taxNumberStatus,
            boolean hasPublicDebt,
            boolean hasInsolvencyProceedings,
            long debtAmount,
            String debtCurrency,
            String status) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", available);
        data.put("companyName", companyName);
        data.put("registrationNumber", registrationNumber);
        data.put("taxNumberStatus", taxNumberStatus);
        data.put("hasPublicDebt", hasPublicDebt);
        data.put("hasInsolvencyProceedings", hasInsolvencyProceedings);
        data.put("debtAmount", debtAmount);
        data.put("debtCurrency", debtCurrency);
        data.put("status", status);
        return data;
    }
}
