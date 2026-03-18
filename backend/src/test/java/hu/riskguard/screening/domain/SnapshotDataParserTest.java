package hu.riskguard.screening.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SnapshotDataParser}.
 * Pure unit tests — no Spring context required.
 */
class SnapshotDataParserTest {

    // --- Demo adapter format tests (Story 2.2.2) ---

    @Nested
    @DisplayName("Demo adapter format (single 'demo' key)")
    class DemoAdapterFormat {

        @Test
        @DisplayName("demo JSONB with clean company parses to no risk signals")
        void demoCleanCompanyNoRiskSignals() {
            Map<String, Object> jsonb = buildDemoSnapshot("ACTIVE", false, false);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.taxSuspended()).isFalse();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.sourceAvailability()).containsEntry("demo", SourceStatus.AVAILABLE);
        }

        @Test
        @DisplayName("demo JSONB with hasPublicDebt=true parses correctly")
        void demoWithPublicDebt() {
            Map<String, Object> jsonb = buildDemoSnapshot("ACTIVE", true, false);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.hasPublicDebt()).isTrue();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.taxSuspended()).isFalse();
            assertThat(result.sourceAvailability()).containsEntry("demo", SourceStatus.AVAILABLE);
        }

        @Test
        @DisplayName("demo JSONB with taxNumberStatus=SUSPENDED parses correctly")
        void demoWithSuspendedTax() {
            Map<String, Object> jsonb = buildDemoSnapshot("SUSPENDED", false, false);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.taxSuspended()).isTrue();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
        }

        @Test
        @DisplayName("demo JSONB with hasInsolvencyProceedings=true parses correctly")
        void demoWithInsolvency() {
            Map<String, Object> jsonb = buildDemoSnapshot("ACTIVE", false, true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.hasInsolvencyProceedings()).isTrue();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.taxSuspended()).isFalse();
        }

        @Test
        @DisplayName("demo JSONB with all risk signals active")
        void demoWithAllRiskSignals() {
            Map<String, Object> jsonb = buildDemoSnapshot("SUSPENDED", true, true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.taxSuspended()).isTrue();
            assertThat(result.hasPublicDebt()).isTrue();
            assertThat(result.hasInsolvencyProceedings()).isTrue();
        }

        private Map<String, Object> buildDemoSnapshot(String taxStatus, boolean debt, boolean insolvency) {
            Map<String, Object> demo = new HashMap<>();
            demo.put("available", true);
            demo.put("companyName", "Test Company");
            demo.put("registrationNumber", "01-09-123456");
            demo.put("taxNumberStatus", taxStatus);
            demo.put("hasPublicDebt", debt);
            demo.put("hasInsolvencyProceedings", insolvency);
            demo.put("debtAmount", debt ? 1000000 : 0);
            demo.put("debtCurrency", "HUF");
            demo.put("status", "SUSPENDED".equals(taxStatus) ? "INACTIVE" : "ACTIVE");

            Map<String, Object> jsonb = new HashMap<>();
            jsonb.put("demo", demo);
            return jsonb;
        }
    }

    // --- Legacy 3-adapter format (backward compatibility) ---

    @Nested
    @DisplayName("Legacy 3-adapter format (backward compatibility)")
    class LegacyFormat {

        @Test
        @DisplayName("old 3-adapter format still parses correctly")
        void legacyFormatStillWorks() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.taxSuspended()).isFalse();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.AVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("e-cegjegyzek", SourceStatus.AVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("cegkozlony", SourceStatus.AVAILABLE);
        }

        @Test
        @DisplayName("tax number suspended detected in legacy format")
        void taxNumberSuspended() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("taxNumberStatus", "SUSPENDED");

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.taxSuspended()).isTrue();
        }

        @Test
        @DisplayName("public debt detected in legacy format")
        void publicDebtDetected() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("hasPublicDebt", true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.hasPublicDebt()).isTrue();
        }

        @Test
        @DisplayName("insolvency proceedings detected in legacy format")
        void insolvencyProceedingsDetected() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> cegkozlony = (Map<String, Object>) jsonb.get("cegkozlony");
            cegkozlony.put("hasInsolvencyProceedings", true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.hasInsolvencyProceedings()).isTrue();
        }

        @Test
        @DisplayName("adapter with available=false is UNAVAILABLE")
        void adapterAvailableFalse() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("available", false);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("debt detected even when nav-debt adapter is unavailable (positive evidence is actionable)")
        void debtDetectedDespiteUnavailableAdapter() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("available", false);
            navDebt.put("hasPublicDebt", true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.UNAVAILABLE);
            assertThat(result.hasPublicDebt()).isTrue();
        }

        @Test
        @DisplayName("insolvency detected even when cegkozlony adapter is unavailable")
        void insolvencyDetectedDespiteUnavailableAdapter() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> cegkozlony = (Map<String, Object>) jsonb.get("cegkozlony");
            cegkozlony.put("available", false);
            cegkozlony.put("hasInsolvencyProceedings", true);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.sourceAvailability()).containsEntry("cegkozlony", SourceStatus.UNAVAILABLE);
            assertThat(result.hasInsolvencyProceedings()).isTrue();
        }

        @Test
        @DisplayName("TAX_SUSPENDED detected even when nav-debt adapter is unavailable")
        void suspendedDetectedDespiteUnavailableAdapter() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("available", false);
            navDebt.put("taxNumberStatus", "SUSPENDED");

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.UNAVAILABLE);
            assertThat(result.taxSuspended()).isTrue();
        }
    }

    // --- Company name extraction tests (Story 2.4) ---

    @Nested
    @DisplayName("Company name extraction")
    class CompanyNameExtraction {

        @Test
        @DisplayName("extracts companyName from demo adapter")
        void extractsCompanyNameFromDemoAdapter() {
            Map<String, Object> jsonb = Map.of(
                    "demo", Map.of("available", true, "companyName", "Teszt Kft.", "taxNumberStatus", "ACTIVE"));

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.companyName()).isEqualTo("Teszt Kft.");
        }

        @Test
        @DisplayName("extracts companyName from e-cegjegyzek adapter in legacy format")
        void extractsCompanyNameFromCegjegyzekAdapter() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.companyName()).isEqualTo("Example Kft.");
        }

        @Test
        @DisplayName("returns null companyName when no adapter provides it")
        void returnsNullWhenNoCompanyName() {
            Map<String, Object> jsonb = new HashMap<>();
            Map<String, Object> navDebt = new HashMap<>();
            navDebt.put("available", true);
            navDebt.put("hasPublicDebt", false);
            jsonb.put("nav-debt", navDebt);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.companyName()).isNull();
        }

        @Test
        @DisplayName("skips blank companyName strings")
        void skipsBlankCompanyName() {
            Map<String, Object> jsonb = new HashMap<>();
            Map<String, Object> demoData = new HashMap<>();
            demoData.put("available", true);
            demoData.put("companyName", "  ");
            jsonb.put("demo", demoData);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.companyName()).isNull();
        }

        @Test
        @DisplayName("null input produces null companyName")
        void nullInputProducesNullCompanyName() {
            SnapshotData result = SnapshotDataParser.parse(null);

            assertThat(result.companyName()).isNull();
        }
    }

    @Nested
    @DisplayName("Malformed and edge case data")
    class MalformedData {

        @Test
        @DisplayName("null input map produces empty result")
        void nullInput() {
            SnapshotData result = SnapshotDataParser.parse(null);

            assertThat(result.taxSuspended()).isFalse();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.sourceAvailability()).isEmpty();
        }

        @Test
        @DisplayName("empty map produces empty result")
        void emptyMap() {
            SnapshotData result = SnapshotDataParser.parse(Map.of());

            assertThat(result.taxSuspended()).isFalse();
            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.sourceAvailability()).isEmpty();
        }

        @Test
        @DisplayName("adapter value is not a Map — treated as UNAVAILABLE")
        void adapterValueNotAMap() {
            Map<String, Object> jsonb = new HashMap<>();
            jsonb.put("nav-debt", "not-a-map");
            jsonb.put("e-cegjegyzek", 42);
            jsonb.put("cegkozlony", null);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.UNAVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("e-cegjegyzek", SourceStatus.UNAVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("cegkozlony", SourceStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("unknown adapter keys are tracked in sourceAvailability")
        void unknownAdapterKeysTracked() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            jsonb.put("unknown-adapter", Map.of("available", true, "data", "something"));

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            // All known sources parsed correctly
            assertThat(result.sourceAvailability()).containsEntry("nav-debt", SourceStatus.AVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("e-cegjegyzek", SourceStatus.AVAILABLE);
            assertThat(result.sourceAvailability()).containsEntry("cegkozlony", SourceStatus.AVAILABLE);
            // Unknown adapter IS now tracked (dynamic parser tracks all keys)
            assertThat(result.sourceAvailability()).containsEntry("unknown-adapter", SourceStatus.AVAILABLE);
        }

        @Test
        @DisplayName("hasPublicDebt as String 'true' treated as false (strict boolean)")
        void hasPublicDebtStringTrue() {
            Map<String, Object> jsonb = buildFullCleanSnapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> navDebt = (Map<String, Object>) jsonb.get("nav-debt");
            navDebt.put("hasPublicDebt", "true");

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            // String "true" is not boolean true — defensive parsing defaults to false
            assertThat(result.hasPublicDebt()).isFalse();
        }

        @Test
        @DisplayName("missing boolean fields default to false")
        void missingBooleanFields() {
            Map<String, Object> jsonb = new HashMap<>();
            // nav-debt present but without risk fields
            Map<String, Object> navDebt = new HashMap<>();
            navDebt.put("available", true);
            jsonb.put("nav-debt", navDebt);

            SnapshotData result = SnapshotDataParser.parse(jsonb);

            assertThat(result.hasPublicDebt()).isFalse();
            assertThat(result.hasInsolvencyProceedings()).isFalse();
            assertThat(result.taxSuspended()).isFalse();
        }
    }

    // --- Fixture helpers ---

    private static Map<String, Object> buildFullCleanSnapshot() {
        Map<String, Object> jsonb = new HashMap<>();

        Map<String, Object> navDebt = new HashMap<>();
        navDebt.put("available", true);
        navDebt.put("hasPublicDebt", false);
        navDebt.put("taxNumberStatus", "ACTIVE");
        navDebt.put("debtAmount", 0);
        navDebt.put("debtCurrency", "HUF");
        jsonb.put("nav-debt", navDebt);

        Map<String, Object> cegjegyzek = new HashMap<>();
        cegjegyzek.put("available", true);
        cegjegyzek.put("companyName", "Example Kft.");
        cegjegyzek.put("registrationNumber", "01-09-123456");
        cegjegyzek.put("status", "ACTIVE");
        jsonb.put("e-cegjegyzek", cegjegyzek);

        Map<String, Object> cegkozlony = new HashMap<>();
        cegkozlony.put("available", true);
        cegkozlony.put("hasInsolvencyProceedings", false);
        cegkozlony.put("hasActiveProceedings", false);
        jsonb.put("cegkozlony", cegkozlony);

        return jsonb;
    }
}
