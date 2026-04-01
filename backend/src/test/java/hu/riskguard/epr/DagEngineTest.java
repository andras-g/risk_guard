package hu.riskguard.epr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.epr.domain.DagEngine;
import hu.riskguard.epr.domain.DagEngine.Confidence;
import hu.riskguard.epr.domain.DagEngine.KfCodeEntry;
import hu.riskguard.epr.domain.DagEngine.KfCodeResolution;
import hu.riskguard.epr.domain.DagEngine.WizardOption;
import hu.riskguard.epr.domain.DagEngine.WizardStepResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DagEngine} — pure function, no database needed.
 * Uses the real {@code epr-seed-data-2026.json} as a static JSON fixture.
 * Minimum 15 test cases covering all product stream families.
 */
class DagEngineTest {

    private static JsonNode configData;
    private final DagEngine dagEngine = new DagEngine();

    @BeforeAll
    static void loadSeedData() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = DagEngineTest.class.getResourceAsStream("/epr-seed-data-2026.json");
        if (is != null) {
            try (is) {
                configData = mapper.readTree(is);
            }
        } else {
            // Fallback: load from project path (useful when running tests outside the standard build)
            configData = mapper.readTree(
                    new java.io.File("../../../_bmad-output/implementation-artifacts/epr-seed-data-2026.json"));
        }
    }

    // ─── Level 1: Product Streams ────────────────────────────────────────────

    @Nested
    class GetProductStreams {

        @Test
        void shouldReturn18ProductStreams() {
            // 18 product streams: 11,12,13 (packaging), 21-26 (EEE), 31-33 (batteries),
            // 41 (tires), 51 (vehicles), 61 (office paper), 71 (advertising paper),
            // 81 (single-use plastic), 91 (other plastic/chemical)
            List<WizardOption> options = dagEngine.getProductStreams(configData, "hu");
            assertThat(options).hasSize(18);
        }

        @Test
        void shouldReturnHungarianLabels() {
            List<WizardOption> options = dagEngine.getProductStreams(configData, "hu");
            WizardOption ps11 = options.stream().filter(o -> "11".equals(o.code())).findFirst().orElseThrow();
            assertThat(ps11.label()).contains("csomagolás");
        }

        @Test
        void shouldReturnEnglishLabels() {
            List<WizardOption> options = dagEngine.getProductStreams(configData, "en");
            WizardOption ps11 = options.stream().filter(o -> "11".equals(o.code())).findFirst().orElseThrow();
            assertThat(ps11.label()).containsIgnoringCase("packaging");
        }

        @Test
        void shouldBeSortedByCode() {
            List<WizardOption> options = dagEngine.getProductStreams(configData, "hu");
            List<String> codes = options.stream().map(WizardOption::code).toList();
            assertThat(codes).isSorted();
        }
    }

    // ─── Level 2: Material Streams ───────────────────────────────────────────

    @Nested
    class GetMaterialStreams {

        @Test
        void packagingShouldReturnFeeAlignedOptions() {
            // Packaging uses fee-rate-aligned expansion: 11 options (01-11)
            // 01 Paper, 02 Plastic, 03 Wood, 04 Iron/steel, 05 Aluminium,
            // 06 Glass, 07 Textile, 08-11 Composites
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "11", "hu");
            assertThat(result.autoSelect()).isFalse();
            assertThat(result.options()).hasSize(11);
        }

        @Test
        void singleUsePlasticShouldReturn2Options() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "81", "hu");
            assertThat(result.options()).hasSize(2);
            assertThat(result.autoSelect()).isFalse();
        }

        @Test
        void eeeShouldAutoSelectMaterialStream01() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "21", "hu");
            assertThat(result.autoSelect()).isTrue();
            assertThat(result.autoSelectedOption().code()).isEqualTo("01");
        }

        @Test
        void batteriesShouldAutoSelectMaterialStream01() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "31", "hu");
            assertThat(result.autoSelect()).isTrue();
            assertThat(result.autoSelectedOption().code()).isEqualTo("01");
        }

        @Test
        void tiresShouldAutoSelectMaterialStream01() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "41", "hu");
            assertThat(result.autoSelect()).isTrue();
            assertThat(result.autoSelectedOption().code()).isEqualTo("01");
        }

        @Test
        void vehiclesShouldAutoSelectMaterialStream01() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "51", "hu");
            assertThat(result.autoSelect()).isTrue();
            assertThat(result.autoSelectedOption().code()).isEqualTo("01");
        }

        @Test
        void otherPlasticChemicalShouldReturn2Options() {
            WizardStepResult result = dagEngine.getMaterialStreams(configData, "91", "hu");
            assertThat(result.options()).hasSize(2);
            assertThat(result.autoSelect()).isFalse();
        }
    }

    // ─── Level 3: Groups ─────────────────────────────────────────────────────

    @Nested
    class GetGroups {

        @Test
        void packagingNonDepositShouldReturn3Groups() {
            WizardStepResult result = dagEngine.getGroups(configData, "11", "01", "hu");
            assertThat(result.options()).hasSize(3);
            assertThat(result.autoSelect()).isFalse();
        }

        @Test
        void packagingMandatoryDepositShouldReturn3Groups() {
            WizardStepResult result = dagEngine.getGroups(configData, "12", "02", "hu");
            assertThat(result.options()).hasSize(3);
        }

        @Test
        void tiresShouldReturn6Groups() {
            WizardStepResult result = dagEngine.getGroups(configData, "41", "01", "hu");
            assertThat(result.options()).hasSize(6);
            assertThat(result.autoSelect()).isFalse();
        }

        @Test
        void eeeShouldAutoSelectGroup01() {
            WizardStepResult result = dagEngine.getGroups(configData, "21", "01", "hu");
            assertThat(result.autoSelect()).isTrue();
        }

        @Test
        void officePaperShouldAutoSelectGroup01() {
            WizardStepResult result = dagEngine.getGroups(configData, "61", "01", "hu");
            assertThat(result.autoSelect()).isTrue();
        }
    }

    // ─── Level 4: Subgroups ──────────────────────────────────────────────────

    @Nested
    class GetSubgroups {

        @Test
        void packagingNonDepositShouldAutoSelectSubgroup01() {
            WizardStepResult result = dagEngine.getSubgroups(configData, "11", "01", "01", "hu");
            assertThat(result.options()).hasSize(1);
            assertThat(result.autoSelect()).isTrue();
        }

        @Test
        void packagingMandatoryDepositShouldReturn15Subgroups() {
            WizardStepResult result = dagEngine.getSubgroups(configData, "12", "02", "01", "hu");
            assertThat(result.options()).hasSize(15);
            assertThat(result.autoSelect()).isFalse();
        }

        @Test
        void eeeCat1ShouldReturn7Subgroups() {
            WizardStepResult result = dagEngine.getSubgroups(configData, "21", "01", "01", "hu");
            assertThat(result.options()).hasSize(7);
        }

        @Test
        void portableBatteryShouldReturn3Subgroups() {
            WizardStepResult result = dagEngine.getSubgroups(configData, "31", "01", "01", "hu");
            assertThat(result.options()).hasSize(3);
        }

        @Test
        void tiresShouldReturn2Subgroups() {
            WizardStepResult result = dagEngine.getSubgroups(configData, "41", "01", "01", "hu");
            assertThat(result.options()).hasSize(2);
        }
    }

    // ─── KF-Code Resolution: Golden Test Cases ──────────────────────────────

    @Nested
    class ResolveKfCode {

        @Test
        void case1_nonDepositPaperConsumerPackaging() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("11010101");
            assertThat(result.feeCode()).isEqualTo("1101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("20.44"));
            assertThat(result.currency()).isEqualTo("HUF");
        }

        @Test
        void case2_nonDepositPlasticTransportPackaging() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "02", "03", "01");
            assertThat(result.kfCode()).isEqualTo("11020301");
            assertThat(result.feeCode()).isEqualTo("1102");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("42.89"));
        }

        @Test
        void case3_depositPetBottlePlastic() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "12", "02", "01", "01");
            assertThat(result.kfCode()).isEqualTo("12020101");
            assertThat(result.feeCode()).isEqualTo("1202");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("42.89"));
        }

        @Test
        void case4_largeHouseholdApplianceRefrigerator() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "21", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("21010101");
            assertThat(result.feeCode()).isEqualTo("2101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("22.26"));
        }

        @Test
        void case5_portableBatteryButtonCell() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "31", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("31010101");
            assertThat(result.feeCode()).isEqualTo("3101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("189.02"));
        }

        @Test
        void case6_singleUsePlasticEPS() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "81", "02", "01", "01");
            assertThat(result.kfCode()).isEqualTo("81020101");
            assertThat(result.feeCode()).isEqualTo("8102");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("1908.78"));
        }

        @Test
        void case7_passengerCarTire() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "41", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("41010101");
            assertThat(result.feeCode()).isEqualTo("4101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("30.62"));
        }

        @Test
        void case8_officePaper() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "61", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("61010101");
            assertThat(result.feeCode()).isEqualTo("6101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("20.44"));
        }

        @Test
        void case9_advertisingPaper() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "71", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("71010101");
            assertThat(result.feeCode()).isEqualTo("7101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("20.44"));
        }

        @Test
        void case10_compositePaperPackaging() {
            // Composite mainly paper: material stream is "08" (expanded from "07")
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "08", "01", "01");
            assertThat(result.kfCode()).isEqualTo("11080101");
            assertThat(result.feeCode()).isEqualTo("1108");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("20.44"));
        }

        @Test
        void case11_otherPlasticProduct() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "91", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("91010101");
            assertThat(result.feeCode()).isEqualTo("9101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("42.89"));
        }

        @Test
        void case12_vehiclePassengerCar() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "51", "01", "01", "01");
            assertThat(result.kfCode()).isEqualTo("51010101");
            assertThat(result.feeCode()).isEqualTo("5101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("18.90"));
        }

        @Test
        void case13_glassPackaging() {
            // Glass is fee code 06 (not material_stream 05) due to metal expansion
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "06", "01", "01");
            assertThat(result.kfCode()).isEqualTo("11060101");
            assertThat(result.feeCode()).isEqualTo("1106");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("10.22"));
        }

        @Test
        void case14_reusableWoodGroupedPackaging() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "13", "03", "02", "01");
            assertThat(result.kfCode()).isEqualTo("13030201");
            assertThat(result.feeCode()).isEqualTo("1303");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("10.22"));
        }

        @Test
        void case15_heavyTruckTire() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "41", "01", "03", "01");
            assertThat(result.kfCode()).isEqualTo("41010301");
            assertThat(result.feeCode()).isEqualTo("4101");
            assertThat(result.feeRate()).isEqualByComparingTo(new BigDecimal("30.62"));
        }

        @Test
        void shouldIncludeLegislationReference() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "01", "01", "01");
            assertThat(result.legislationRef()).contains("33/2025");
        }
    }

    // ─── Confidence Score Tests ─────────────────────────────────────────────

    @Nested
    class ConfidenceScore {

        @Test
        void case1_standardPackagingPaper_shouldBeHigh() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(result.confidenceReason()).isEqualTo("full_traversal");
        }

        @Test
        void case2_standardPackagingPlastic_shouldBeHigh() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "02", "03", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        }

        @Test
        void case3_depositPackaging_shouldBeHigh() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "12", "02", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        }

        @Test
        void case4_eeeLargeHousehold_shouldBeMedium() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "21", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(result.confidenceReason()).isEqualTo("ref_only_section");
        }

        @Test
        void case5_portableBattery_shouldBeMedium() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "31", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(result.confidenceReason()).isEqualTo("ref_only_section");
        }

        @Test
        void case7_tire_shouldBeMedium() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "41", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(result.confidenceReason()).isEqualTo("ref_only_section");
        }

        @Test
        void compositeMaterial_shouldBeLow() {
            // Material stream 08 = composite (mainly paper)
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "08", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.LOW);
            assertThat(result.confidenceReason()).isEqualTo("composite_material");
        }

        @Test
        void compositeMaterial09_shouldBeLow() {
            // Material stream 09 = composite (mainly plastic)
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "11", "09", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.LOW);
            assertThat(result.confidenceReason()).isEqualTo("composite_material");
        }

        @Test
        void otherPlasticChemical_shouldBeLow() {
            // Product stream 91 = other plastic/chemical (broad category)
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "91", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.LOW);
            assertThat(result.confidenceReason()).isEqualTo("catchall_category");
        }

        @Test
        void reusablePackaging_shouldBeMedium() {
            // Product stream 13 = reusable packaging
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "13", "03", "02", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
        }

        @Test
        void singleUsePlastic_shouldBeHigh() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "81", "02", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        }

        @Test
        void vehicle_shouldBeMedium() {
            KfCodeResolution result = dagEngine.resolveKfCode(configData, "51", "01", "01", "01");
            assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(result.confidenceReason()).isEqualTo("ref_only_section");
        }
    }

    // ─── KF-Code Enumeration Tests ──────────────────────────────────────────

    @Nested
    class EnumerateAllKfCodes {

        @Test
        void shouldReturnAtLeast200Entries() {
            List<KfCodeEntry> entries = dagEngine.enumerateAllKfCodes(configData, "hu");
            assertThat(entries).hasSizeGreaterThanOrEqualTo(200);
        }

        @Test
        void allEntriesShouldHaveValid8DigitCodes() {
            List<KfCodeEntry> entries = dagEngine.enumerateAllKfCodes(configData, "hu");
            assertThat(entries).allSatisfy(entry -> {
                assertThat(entry.kfCode()).matches("\\d{8}");
                assertThat(entry.feeRate()).isNotNull();
                assertThat(entry.feeRate()).isPositive();
                assertThat(entry.currency()).isEqualTo("HUF");
                assertThat(entry.classification()).isNotBlank();
                assertThat(entry.productStreamLabel()).isNotBlank();
            });
        }

        @Test
        void shouldBeSortedByKfCode() {
            List<KfCodeEntry> entries = dagEngine.enumerateAllKfCodes(configData, "hu");
            List<String> codes = entries.stream().map(KfCodeEntry::kfCode).toList();
            assertThat(codes).isSorted();
        }

        @Test
        void shouldReturnEnglishLabelsWhenLocaleIsEn() {
            List<KfCodeEntry> entries = dagEngine.enumerateAllKfCodes(configData, "en");
            assertThat(entries).isNotEmpty();
            // At least one entry should have an English label
            assertThat(entries.stream().anyMatch(e ->
                    e.classification() != null && !e.classification().isEmpty())).isTrue();
        }
    }

    // ─── Validation / Error Cases ────────────────────────────────────────────

    @Nested
    class ValidationErrors {

        @Test
        void invalidProductStreamShouldThrow() {
            assertThatThrownBy(() -> dagEngine.getMaterialStreams(configData, "99", "hu"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product stream");
        }

        @Test
        void nullProductStreamShouldThrow() {
            assertThatThrownBy(() -> dagEngine.getMaterialStreams(configData, null, "hu"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1", "123", ""})
        void invalidCodeFormatShouldThrowOnResolve(String badCode) {
            assertThatThrownBy(() -> dagEngine.resolveKfCode(configData, badCode, "01", "01", "01"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nonexistentFeeCodeShouldThrow() {
            assertThatThrownBy(() -> dagEngine.resolveKfCode(configData, "11", "99", "01", "01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Fee rate not found");
        }
    }
}
