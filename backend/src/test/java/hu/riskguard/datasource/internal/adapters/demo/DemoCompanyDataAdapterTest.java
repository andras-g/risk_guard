package hu.riskguard.datasource.internal.adapters.demo;

import hu.riskguard.datasource.api.dto.ScrapedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DemoCompanyDataAdapter}.
 * Pure unit tests — no Spring context required.
 */
class DemoCompanyDataAdapterTest {

    private final DemoCompanyDataAdapter adapter = new DemoCompanyDataAdapter();

    @Test
    @DisplayName("adapterName should return 'demo'")
    void adapterNameShouldReturnDemo() {
        assertThat(adapter.adapterName()).isEqualTo("demo");
    }

    @Test
    @DisplayName("health is implicitly always available — adapter returns available=true")
    void fetchAlwaysReturnsAvailable() {
        ScrapedData result = adapter.fetch("12345678");
        assertThat(result.available()).isTrue();
        assertThat(result.errorReason()).isNull();
    }

    @Test
    @DisplayName("fetch clean company — Példa Kereskedelmi Kft. (12345678)")
    void fetchCleanCompany() {
        ScrapedData result = adapter.fetch("12345678");

        assertThat(result.adapterName()).isEqualTo("demo");
        assertThat(result.available()).isTrue();
        assertThat(result.data()).containsEntry("companyName", "Példa Kereskedelmi Kft.");
        assertThat(result.data()).containsEntry("taxNumberStatus", "ACTIVE");
        assertThat(result.data()).containsEntry("hasPublicDebt", false);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", false);
        assertThat(result.data()).containsEntry("debtAmount", 0L);
        assertThat(result.data()).containsEntry("registrationNumber", "01-09-123456");
        assertThat(result.data()).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("fetch established company — Megbízható Építő Zrt. (99887766)")
    void fetchEstablishedCompany() {
        ScrapedData result = adapter.fetch("99887766");

        assertThat(result.data()).containsEntry("companyName", "Megbízható Építő Zrt.");
        assertThat(result.data()).containsEntry("hasPublicDebt", false);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", false);
    }

    @Test
    @DisplayName("fetch company with public debt — Adós Szolgáltató Bt. (11223344)")
    void fetchCompanyWithDebt() {
        ScrapedData result = adapter.fetch("11223344");

        assertThat(result.data()).containsEntry("companyName", "Adós Szolgáltató Bt.");
        assertThat(result.data()).containsEntry("hasPublicDebt", true);
        assertThat(result.data()).containsEntry("debtAmount", 2_450_000L);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", false);
    }

    @Test
    @DisplayName("fetch company with insolvency — Csődben Lévő Kft. (55667788)")
    void fetchCompanyWithInsolvency() {
        ScrapedData result = adapter.fetch("55667788");

        assertThat(result.data()).containsEntry("companyName", "Csődben Lévő Kft.");
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", true);
        assertThat(result.data()).containsEntry("hasPublicDebt", false);
    }

    @Test
    @DisplayName("fetch company with both debt and insolvency (44556677)")
    void fetchCompanyWithDebtAndInsolvency() {
        ScrapedData result = adapter.fetch("44556677");

        assertThat(result.data()).containsEntry("companyName", "Hátralékos és Csődös Kft.");
        assertThat(result.data()).containsEntry("hasPublicDebt", true);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", true);
        assertThat(result.data()).containsEntry("debtAmount", 5_780_000L);
    }

    @Test
    @DisplayName("fetch company with suspended tax number (33445566)")
    void fetchCompanyWithSuspendedTax() {
        ScrapedData result = adapter.fetch("33445566");

        assertThat(result.data()).containsEntry("companyName", "Felfüggesztett Adószámú Kft.");
        assertThat(result.data()).containsEntry("taxNumberStatus", "SUSPENDED");
        assertThat(result.data()).containsEntry("status", "INACTIVE");
    }

    @Test
    @DisplayName("fetch company with missing filings (77889900)")
    void fetchCompanyWithMissingFilings() {
        ScrapedData result = adapter.fetch("77889900");

        assertThat(result.data()).containsEntry("companyName", "Hiányos Bevallású Kft.");
        assertThat(result.data()).containsEntry("hasPublicDebt", true);
    }

    @Test
    @DisplayName("fetch startup company (22334455)")
    void fetchStartupCompany() {
        ScrapedData result = adapter.fetch("22334455");

        assertThat(result.data()).containsEntry("companyName", "Friss Startup Kft.");
        assertThat(result.data()).containsEntry("hasPublicDebt", false);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", false);
    }

    @Test
    @DisplayName("fetch unknown tax number returns generic clean company")
    void fetchUnknownTaxNumber() {
        ScrapedData result = adapter.fetch("00000000");

        assertThat(result.available()).isTrue();
        assertThat(result.adapterName()).isEqualTo("demo");
        assertThat(result.data()).containsEntry("companyName", "Ismeretlen Cég Kft.");
        assertThat(result.data()).containsEntry("taxNumberStatus", "ACTIVE");
        assertThat(result.data()).containsEntry("hasPublicDebt", false);
        assertThat(result.data()).containsEntry("hasInsolvencyProceedings", false);
    }

    @Test
    @DisplayName("fetch with hyphenated 11-digit tax number normalizes to 8 digits")
    void fetchWithHyphenatedTaxNumber() {
        // "12345678-2-42" → strips hyphens → "12345678242" → first 8 → "12345678"
        ScrapedData result = adapter.fetch("12345678-2-42");

        assertThat(result.data()).containsEntry("companyName", "Példa Kereskedelmi Kft.");
    }

    @Test
    @DisplayName("returned data contains all required fields for SnapshotDataParser")
    void returnedDataContainsAllRequiredFields() {
        ScrapedData result = adapter.fetch("12345678");

        assertThat(result.data()).containsKeys(
                "available", "companyName", "registrationNumber",
                "taxNumberStatus", "hasPublicDebt", "hasInsolvencyProceedings",
                "debtAmount", "debtCurrency", "status");
    }

    @Test
    @DisplayName("source URLs contain masked tax number")
    void sourceUrlsContainMaskedTaxNumber() {
        ScrapedData result = adapter.fetch("12345678");

        assertThat(result.sourceUrls()).hasSize(1);
        assertThat(result.sourceUrls().getFirst()).startsWith("demo://in-memory/");
        // Should NOT contain the full raw tax number
        assertThat(result.sourceUrls().getFirst()).doesNotContain("12345678");
    }

    @Test
    @DisplayName("requiredFields returns expected set")
    void requiredFieldsReturnsExpectedSet() {
        assertThat(adapter.requiredFields()).contains(
                "available", "hasPublicDebt", "taxNumberStatus",
                "hasInsolvencyProceedings", "companyName", "registrationNumber");
    }
}
