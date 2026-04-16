package hu.riskguard.epr.registry.classifier;

import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-5 §8.6 validation gate.
 *
 * <p>Gated behind {@code RG_INTEGRATION_VERTEX_AI=true}. Reads
 * {@code kf-classifier-validation/validation-set.csv} and asserts ≥70% top-1
 * match rate against real Gemini responses.
 *
 * <p>CSV format: {@code productName,vtsz,expectedKfCode} (header row skipped).
 */
@EnabledIfEnvironmentVariable(named = "RG_INTEGRATION_VERTEX_AI", matches = "true")
class KfClassifierValidationTest {

    private static final double MIN_TOP1_MATCH_RATE = 0.70;

    @Test
    void validationSet_top1MatchRate_meetsThreshold() throws IOException {
        VertexAiGeminiClassifier classifier = new VertexAiGeminiClassifier(
                System.getenv().getOrDefault("GCP_PROJECT_ID", "risk-guard-dev"),
                "europe-west1",
                "gemini-3.0-flash-preview",
                CircuitBreakerRegistry.ofDefaults()
        );

        List<ValidationRow> rows = loadValidationSet();
        assertThat(rows).as("validation set must not be empty").isNotEmpty();

        int matches = 0;
        for (ValidationRow row : rows) {
            ClassificationResult result = classifier.classify(row.productName, row.vtsz);
            if (!result.suggestions().isEmpty()
                    && row.expectedKfCode.equals(result.suggestions().get(0).kfCode())) {
                matches++;
            }
        }

        double rate = (double) matches / rows.size();
        assertThat(rate)
                .as("Top-1 match rate (%d/%d) must meet CP-5 §8.6 threshold %.0f%%",
                        matches, rows.size(), MIN_TOP1_MATCH_RATE * 100)
                .isGreaterThanOrEqualTo(MIN_TOP1_MATCH_RATE);
    }

    private List<ValidationRow> loadValidationSet() throws IOException {
        List<ValidationRow> rows = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("kf-classifier-validation/validation-set.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     java.util.Objects.requireNonNull(in, "validation-set.csv missing"),
                     StandardCharsets.UTF_8))) {

            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 3) continue;
                rows.add(new ValidationRow(cols[0].trim(), cols[1].trim(), cols[2].trim()));
            }
        }
        return rows;
    }

    private record ValidationRow(String productName, String vtsz, String expectedKfCode) {}
}
