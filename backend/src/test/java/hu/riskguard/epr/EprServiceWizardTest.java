package hu.riskguard.epr;

import com.fasterxml.jackson.databind.JsonNode;
import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.domain.DagEngine;
import hu.riskguard.epr.domain.DagEngine.KfCodeResolution;
import hu.riskguard.epr.domain.DagEngine.WizardStepResult;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.domain.FeeCalculator;
import hu.riskguard.epr.internal.EprRepository;
import org.jooq.JSONB;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EprService} wizard methods with mocked repository and DagEngine.
 */
@ExtendWith(MockitoExtension.class)
class EprServiceWizardTest {

    @Mock
    private EprRepository eprRepository;

    @Mock
    private DagEngine dagEngine;

    private EprService eprService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final int CONFIG_VERSION = 1;

    @BeforeEach
    void setUp() {
        eprService = new EprService(eprRepository, dagEngine, new FeeCalculator());
    }

    @Nested
    class StartWizard {

        @Test
        void shouldReturnProductStreamOptions() {
            // Mock config loading
            mockConfigLoading();

            List<DagEngine.WizardOption> options = List.of(
                    new DagEngine.WizardOption("11", "Packaging", null),
                    new DagEngine.WizardOption("21", "EEE", null)
            );
            when(dagEngine.getProductStreams(any(JsonNode.class), eq("hu"))).thenReturn(options);

            WizardStartResponse response = eprService.startWizard(CONFIG_VERSION, "hu");

            assertThat(response.configVersion()).isEqualTo(CONFIG_VERSION);
            assertThat(response.level()).isEqualTo("product_stream");
            assertThat(response.options()).hasSize(2);
            assertThat(response.options().get(0).code()).isEqualTo("11");
        }
    }

    @Nested
    class ProcessStep {

        @Test
        void productStreamSelectionShouldReturnMaterialStreams() {
            mockConfigLoading();

            WizardStepResult stepResult = new WizardStepResult(
                    List.of(new DagEngine.WizardOption("01", "Paper", null)),
                    false, null
            );
            when(dagEngine.getMaterialStreams(any(JsonNode.class), eq("11"), eq("hu")))
                    .thenReturn(stepResult);

            WizardStepRequest request = new WizardStepRequest(
                    CONFIG_VERSION,
                    List.of(),
                    new WizardSelection("product_stream", "11", "Packaging")
            );

            WizardStepResponse response = eprService.processStep(request, "hu");

            assertThat(response.currentLevel()).isEqualTo("product_stream");
            assertThat(response.nextLevel()).isEqualTo("material_stream");
            assertThat(response.options()).hasSize(1);
        }

        @Test
        void materialStreamSelectionShouldReturnGroups() {
            mockConfigLoading();

            WizardStepResult stepResult = new WizardStepResult(
                    List.of(new DagEngine.WizardOption("01", "Consumer", null)),
                    false, null
            );
            when(dagEngine.getGroups(any(JsonNode.class), eq("11"), eq("01"), eq("hu")))
                    .thenReturn(stepResult);

            WizardStepRequest request = new WizardStepRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    new WizardSelection("material_stream", "01", "Paper")
            );

            WizardStepResponse response = eprService.processStep(request, "hu");

            assertThat(response.nextLevel()).isEqualTo("group");
        }

        @Test
        void groupSelectionShouldReturnSubgroups() {
            mockConfigLoading();

            WizardStepResult stepResult = new WizardStepResult(
                    List.of(new DagEngine.WizardOption("01", "Default", null)),
                    true, new DagEngine.WizardOption("01", "Default", null)
            );
            when(dagEngine.getSubgroups(any(JsonNode.class), eq("11"), eq("01"), eq("01"), eq("hu")))
                    .thenReturn(stepResult);

            WizardStepRequest request = new WizardStepRequest(
                    CONFIG_VERSION,
                    List.of(
                            new WizardSelection("product_stream", "11", "Packaging"),
                            new WizardSelection("material_stream", "01", "Paper")
                    ),
                    new WizardSelection("group", "01", "Consumer")
            );

            WizardStepResponse response = eprService.processStep(request, "hu");

            assertThat(response.nextLevel()).isEqualTo("subgroup");
            assertThat(response.autoSelect()).isTrue();
        }

        @Test
        void invalidLevelShouldThrow() {
            mockConfigLoading();

            WizardStepRequest request = new WizardStepRequest(
                    CONFIG_VERSION,
                    List.of(),
                    new WizardSelection("invalid_level", "01", "X")
            );

            assertThatThrownBy(() -> eprService.processStep(request, "hu"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid wizard level");
        }
    }

    @Nested
    class ResolveKfCode {

        @Test
        void shouldDelegateAndReturnResolution() {
            mockConfigLoading();

            KfCodeResolution resolution = new KfCodeResolution(
                    "11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "legislation",
                    DagEngine.Confidence.HIGH, "full_traversal"
            );
            when(dagEngine.resolveKfCode(any(JsonNode.class), eq("11"), eq("01"), eq("01"), eq("01"), anyString()))
                    .thenReturn(resolution);

            List<WizardSelection> path = List.of(
                    new WizardSelection("product_stream", "11", "Packaging"),
                    new WizardSelection("material_stream", "01", "Paper"),
                    new WizardSelection("group", "01", "Consumer"),
                    new WizardSelection("subgroup", "01", "Default")
            );

            WizardResolveResponse response = eprService.resolveKfCode(path, CONFIG_VERSION);

            assertThat(response.kfCode()).isEqualTo("11010101");
            assertThat(response.feeCode()).isEqualTo("1101");
            assertThat(response.feeRate()).isEqualByComparingTo(new BigDecimal("20.44"));
        }
    }

    @Nested
    class ConfirmWizard {

        @Test
        void shouldPersistCalculationAndUpdateTemplate() {
            UUID templateId = UUID.randomUUID();
            UUID calculationId = UUID.randomUUID();

            when(eprRepository.insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    eq("Paper"), eq("11010101"), eq(new BigDecimal("20.44")), eq(templateId),
                    eq("HIGH"), isNull(), isNull()
            )).thenReturn(calculationId);
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11010101"))
                    .thenReturn(true);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper",
                    templateId,
                    "HIGH",
                    null,
                    null
            );

            WizardConfirmResponse response = eprService.confirmWizard(request, TENANT_ID);

            assertThat(response.calculationId()).isEqualTo(calculationId);
            assertThat(response.kfCode()).isEqualTo("11010101");
            assertThat(response.templateUpdated()).isTrue();
        }

        @Test
        void shouldSaveCalculationEvenWhenTemplateMissing() {
            UUID templateId = UUID.randomUUID();
            UUID calculationId = UUID.randomUUID();

            when(eprRepository.insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    anyString(), anyString(), any(BigDecimal.class), eq(templateId),
                    anyString(), isNull(), isNull()
            )).thenReturn(calculationId);
            // Template was deleted between wizard start and confirm
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11010101"))
                    .thenReturn(false);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper",
                    templateId,
                    "HIGH",
                    null,
                    null
            );

            WizardConfirmResponse response = eprService.confirmWizard(request, TENANT_ID);

            assertThat(response.calculationId()).isEqualTo(calculationId);
            assertThat(response.templateUpdated()).isFalse();
        }

        @Test
        void shouldSaveWithNullTemplateId() {
            UUID calculationId = UUID.randomUUID();

            when(eprRepository.insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    anyString(), anyString(), any(BigDecimal.class), isNull(),
                    anyString(), isNull(), isNull()
            )).thenReturn(calculationId);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper",
                    null,
                    "HIGH",
                    null,
                    null
            );

            WizardConfirmResponse response = eprService.confirmWizard(request, TENANT_ID);

            assertThat(response.calculationId()).isEqualTo(calculationId);
            assertThat(response.templateUpdated()).isFalse();
            verify(eprRepository, never()).updateTemplateKfCode(any(), any(), any());
        }

        @Test
        void shouldRejectInvalidConfidenceScore() {
            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper",
                    null,
                    "INVALID",
                    null,
                    null
            );

            assertThatThrownBy(() -> eprService.confirmWizard(request, TENANT_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid confidence score");
        }
    }

    @Nested
    class GetAllKfCodes {

        @Test
        void shouldCacheResultsForSameVersionAndLocale() {
            mockConfigLoading();

            List<DagEngine.KfCodeEntry> entries = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "Packaging")
            );
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("hu"))).thenReturn(entries);

            // First call — should invoke DagEngine
            KfCodeListResponse first = eprService.getAllKfCodes(CONFIG_VERSION, "hu");
            assertThat(first.entries()).hasSize(1);
            assertThat(first.configVersion()).isEqualTo(CONFIG_VERSION);

            // Second call with same version+locale — should use cache, NOT call DagEngine again
            KfCodeListResponse second = eprService.getAllKfCodes(CONFIG_VERSION, "hu");
            assertThat(second.entries()).hasSize(1);

            verify(dagEngine, times(1)).enumerateAllKfCodes(any(JsonNode.class), eq("hu"));
        }

        @Test
        void shouldCacheSeparatelyPerLocale() {
            mockConfigLoading();

            List<DagEngine.KfCodeEntry> huEntries = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Papír", "Csomagolás")
            );
            List<DagEngine.KfCodeEntry> enEntries = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "Packaging")
            );
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("hu"))).thenReturn(huEntries);
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("en"))).thenReturn(enEntries);

            eprService.getAllKfCodes(CONFIG_VERSION, "hu");
            eprService.getAllKfCodes(CONFIG_VERSION, "en");

            verify(dagEngine, times(1)).enumerateAllKfCodes(any(JsonNode.class), eq("hu"));
            verify(dagEngine, times(1)).enumerateAllKfCodes(any(JsonNode.class), eq("en"));
        }
    }

    @Nested
    class ConfirmWizardWithOverride {

        @Test
        void shouldValidateAndUseOverrideKfCode() {
            UUID templateId = UUID.randomUUID();
            UUID calculationId = UUID.randomUUID();

            mockConfigLoading();

            // Mock KF-code enumeration for override validation (via cache)
            List<DagEngine.KfCodeEntry> allCodes = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "Packaging"),
                    new DagEngine.KfCodeEntry("11020101", "1102", new BigDecimal("20.44"), "HUF", "Plastic", "Packaging")
            );
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("hu"))).thenReturn(allCodes);

            when(eprRepository.insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    eq("Paper"), eq("11010101"), eq(new BigDecimal("20.44")), eq(templateId),
                    eq("MEDIUM"), eq("11020101"), eq("Better match")
            )).thenReturn(calculationId);
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11020101"))
                    .thenReturn(true);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",            // original wizard suggestion
                    new BigDecimal("20.44"),
                    "Paper",
                    templateId,
                    "MEDIUM",
                    "11020101",            // override KF-code
                    "Better match"         // override reason
            );

            WizardConfirmResponse response = eprService.confirmWizard(request, TENANT_ID);

            assertThat(response.calculationId()).isEqualTo(calculationId);
            assertThat(response.kfCode()).isEqualTo("11020101"); // effective = override
            assertThat(response.templateUpdated()).isTrue();

            // Template should be updated with the OVERRIDE KF-code, not the original
            verify(eprRepository).updateTemplateKfCode(templateId, TENANT_ID, "11020101");
        }

        @Test
        void shouldRejectInvalidOverrideKfCode() {
            mockConfigLoading();

            // Mock KF-code enumeration — override code NOT in the list
            List<DagEngine.KfCodeEntry> allCodes = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "Packaging")
            );
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("hu"))).thenReturn(allCodes);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper",
                    null,
                    "HIGH",
                    "99999999",   // invalid override KF-code
                    null
            );

            assertThatThrownBy(() -> eprService.confirmWizard(request, TENANT_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Override KF-code not found");
        }

        @Test
        void shouldPreserveOriginalKfCodeInCalculation() {
            UUID templateId = UUID.randomUUID();
            UUID calculationId = UUID.randomUUID();

            mockConfigLoading();

            List<DagEngine.KfCodeEntry> allCodes = List.of(
                    new DagEngine.KfCodeEntry("11010101", "1101", new BigDecimal("20.44"), "HUF", "Paper", "Packaging"),
                    new DagEngine.KfCodeEntry("11020101", "1102", new BigDecimal("20.44"), "HUF", "Plastic", "Packaging")
            );
            when(dagEngine.enumerateAllKfCodes(any(JsonNode.class), eq("hu"))).thenReturn(allCodes);

            when(eprRepository.insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    eq("Paper"),
                    eq("11010101"),  // ORIGINAL kf_code preserved
                    eq(new BigDecimal("20.44")), eq(templateId),
                    eq("LOW"), eq("11020101"), eq("Reason")
            )).thenReturn(calculationId);
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11020101"))
                    .thenReturn(true);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    CONFIG_VERSION,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",        // original preserved in kf_code
                    new BigDecimal("20.44"),
                    "Paper",
                    templateId,
                    "LOW",
                    "11020101",        // override stored in override_kf_code
                    "Reason"
            );

            eprService.confirmWizard(request, TENANT_ID);

            // Verify insertCalculation receives ORIGINAL kfCode and the override separately
            verify(eprRepository).insertCalculation(
                    eq(TENANT_ID), eq(CONFIG_VERSION), any(JSONB.class),
                    eq("Paper"),
                    eq("11010101"),  // original kf_code
                    eq(new BigDecimal("20.44")),
                    eq(templateId),
                    eq("LOW"),
                    eq("11020101"),  // override kf_code
                    eq("Reason")
            );
        }
    }

    @Nested
    class RetryLink {

        @Test
        void shouldRetryWithEffectiveKfCode_overrideTakesPrecedence() {
            UUID calculationId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();

            Record calcRecord = mock(Record.class);
            when(calcRecord.get("template_id", UUID.class)).thenReturn(templateId);
            when(calcRecord.get("override_kf_code", String.class)).thenReturn("11020101");
            when(calcRecord.get("kf_code", String.class)).thenReturn("11010101");
            when(eprRepository.findCalculationById(calculationId, TENANT_ID))
                    .thenReturn(Optional.of(calcRecord));
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11020101"))
                    .thenReturn(true);

            var response = eprService.retryLink(calculationId, templateId, TENANT_ID);

            assertThat(response.templateUpdated()).isTrue();
            assertThat(response.kfCode()).isEqualTo("11020101");
            // Verify template was updated with OVERRIDE KF-code
            verify(eprRepository).updateTemplateKfCode(templateId, TENANT_ID, "11020101");
        }

        @Test
        void shouldRetryWithOriginalKfCode_whenNoOverride() {
            UUID calculationId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();

            Record calcRecord = mock(Record.class);
            when(calcRecord.get("template_id", UUID.class)).thenReturn(templateId);
            when(calcRecord.get("override_kf_code", String.class)).thenReturn(null);
            when(calcRecord.get("kf_code", String.class)).thenReturn("11010101");
            when(eprRepository.findCalculationById(calculationId, TENANT_ID))
                    .thenReturn(Optional.of(calcRecord));
            when(eprRepository.updateTemplateKfCode(templateId, TENANT_ID, "11010101"))
                    .thenReturn(true);

            var response = eprService.retryLink(calculationId, templateId, TENANT_ID);

            assertThat(response.templateUpdated()).isTrue();
            assertThat(response.kfCode()).isEqualTo("11010101");
        }

        @Test
        void shouldThrow400_whenCalculationHasNoKfCode() {
            UUID calculationId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();

            Record calcRecord = mock(Record.class);
            when(calcRecord.get("template_id", UUID.class)).thenReturn(templateId);
            when(calcRecord.get("override_kf_code", String.class)).thenReturn(null);
            when(calcRecord.get("kf_code", String.class)).thenReturn(null);
            when(eprRepository.findCalculationById(calculationId, TENANT_ID))
                    .thenReturn(Optional.of(calcRecord));

            assertThatThrownBy(() -> eprService.retryLink(calculationId, templateId, TENANT_ID))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("Calculation has no KF-code to link");
        }

        @Test
        void shouldThrow404_whenCalculationNotFoundForTenant() {
            UUID calculationId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();

            when(eprRepository.findCalculationById(calculationId, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> eprService.retryLink(calculationId, templateId, TENANT_ID))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("Calculation not found");
        }
    }

    @Nested
    class GetActiveConfigVersion {

        @Test
        void shouldReturnVersionFromActiveConfig() {
            Record mockRecord = mock(Record.class);
            when(mockRecord.get("version", Integer.class)).thenReturn(1);
            when(eprRepository.findActiveConfig()).thenReturn(Optional.of(mockRecord));

            int version = eprService.getActiveConfigVersion();

            assertThat(version).isEqualTo(1);
        }

        @Test
        void shouldThrowWhenNoActiveConfig() {
            when(eprRepository.findActiveConfig()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eprService.getActiveConfigVersion())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No active EPR config");
        }
    }

    // Helper: mock config loading from repository
    private void mockConfigLoading() {
        Record mockRecord = mock(Record.class);
        // Return a minimal valid JSON config
        when(mockRecord.get("config_data")).thenReturn(JSONB.jsonb("{\"kf_code_structure\":{},\"fee_rates_2026\":{}}"));
        when(eprRepository.findConfigByVersion(CONFIG_VERSION)).thenReturn(Optional.of(mockRecord));
    }
}
