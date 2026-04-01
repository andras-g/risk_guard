package hu.riskguard.epr.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.epr.api.dto.EprConfigValidateResponse;
import hu.riskguard.epr.api.dto.WizardSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service that validates a candidate EPR config JSON by running 5 hard-coded
 * validation cases through {@link DagEngine#resolveKfCode}.
 *
 * <p>Validation cases are derived from the seeded 2026 config (version 1) and cover
 * the major EPR product streams. A config is valid when all 5 cases produce the expected
 * KF-code and fee rate.
 */
@Component
@RequiredArgsConstructor
public class EprConfigValidator {

    private final DagEngine dagEngine;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * A single validation case: expected traversal path → expected KF-code and fee rate.
     */
    record ValidationCase(
            String name,
            List<WizardSelection> path,
            String expectedKfCode,
            BigDecimal expectedFeeRateHufPerKg
    ) {}

    private static final List<ValidationCase> VALIDATION_CASES = List.of(
            new ValidationCase(
                    "Paper/cardboard consumer non-deposit packaging",
                    List.of(
                            new WizardSelection("product_stream", "11", null),
                            new WizardSelection("material_stream", "01", null),
                            new WizardSelection("group", "01", null),
                            new WizardSelection("subgroup", "01", null)
                    ),
                    "11010101", new BigDecimal("20.44")),
            new ValidationCase(
                    "Plastic consumer non-deposit packaging",
                    List.of(
                            new WizardSelection("product_stream", "11", null),
                            new WizardSelection("material_stream", "02", null),
                            new WizardSelection("group", "01", null),
                            new WizardSelection("subgroup", "01", null)
                    ),
                    "11020101", new BigDecimal("42.89")),
            new ValidationCase(
                    "Portable batteries — general purpose",
                    List.of(
                            new WizardSelection("product_stream", "31", null),
                            new WizardSelection("material_stream", "01", null),
                            new WizardSelection("group", "01", null),
                            new WizardSelection("subgroup", "02", null)
                    ),
                    "31010102", new BigDecimal("189.02")),
            new ValidationCase(
                    "Office paper",
                    List.of(
                            new WizardSelection("product_stream", "61", null),
                            new WizardSelection("material_stream", "01", null),
                            new WizardSelection("group", "01", null),
                            new WizardSelection("subgroup", "01", null)
                    ),
                    "61010101", new BigDecimal("20.44")),
            new ValidationCase(
                    "EPS single-use plastic product",
                    List.of(
                            new WizardSelection("product_stream", "81", null),
                            new WizardSelection("material_stream", "02", null),
                            new WizardSelection("group", "01", null),
                            new WizardSelection("subgroup", "02", null)
                    ),
                    "81020102", new BigDecimal("1908.78"))
    );

    /**
     * Validates the given config JSON by running all 5 validation cases.
     *
     * @param configData raw JSON string of the candidate EPR config
     * @return {@link EprConfigValidateResponse#ok()} if all cases pass,
     *         {@link EprConfigValidateResponse#failed(List)} with error details otherwise
     */
    public EprConfigValidateResponse validate(String configData) {
        if (configData == null || configData.isBlank()) {
            return EprConfigValidateResponse.failed(List.of("Invalid JSON: config data is null or empty"));
        }
        JsonNode configNode;
        try {
            configNode = OBJECT_MAPPER.readTree(configData);
        } catch (JsonProcessingException e) {
            return EprConfigValidateResponse.failed(List.of("Invalid JSON: " + e.getMessage()));
        }

        List<String> errors = new ArrayList<>();

        for (ValidationCase testCase : VALIDATION_CASES) {
            String productStream = getCode(testCase.path(), "product_stream");
            String materialStream = getCode(testCase.path(), "material_stream");
            String group = getCode(testCase.path(), "group");
            String subgroup = getCode(testCase.path(), "subgroup");

            try {
                DagEngine.KfCodeResolution resolution = dagEngine.resolveKfCode(
                        configNode, productStream, materialStream, group, subgroup, "hu");

                if (!testCase.expectedKfCode().equals(resolution.kfCode())) {
                    errors.add("Test '" + testCase.name() + "' FAILED: expected kfCode="
                            + testCase.expectedKfCode() + ", got " + resolution.kfCode());
                }
                if (testCase.expectedFeeRateHufPerKg().compareTo(resolution.feeRate()) != 0) {
                    errors.add("Test '" + testCase.name() + "' FAILED: expected feeRate="
                            + testCase.expectedFeeRateHufPerKg() + ", got " + resolution.feeRate());
                }
            } catch (Exception e) {
                errors.add("Test '" + testCase.name() + "' FAILED with exception: " + e.getMessage());
            }
        }

        return errors.isEmpty() ? EprConfigValidateResponse.ok() : EprConfigValidateResponse.failed(errors);
    }

    private String getCode(List<WizardSelection> path, String level) {
        return path.stream()
                .filter(s -> level.equals(s.level()))
                .findFirst()
                .map(WizardSelection::code)
                .orElseThrow(() -> new IllegalArgumentException("Missing " + level + " in test case path"));
    }
}
