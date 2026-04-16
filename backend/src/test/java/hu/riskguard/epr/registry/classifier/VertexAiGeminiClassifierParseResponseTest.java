package hu.riskguard.epr.registry.classifier;

import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VertexAiGeminiClassifier#parseResponse(String)} (private).
 * Uses reflection to invoke the private method directly — avoids needing live Vertex AI.
 *
 * <p>Covers AC #7 behaviors: multi-layer parsing, layer-order sort, min-score
 * confidence, weightEstimateKg deserialization, and defense-in-depth validations
 * (P1/P3 review findings).
 */
class VertexAiGeminiClassifierParseResponseTest {

    private VertexAiGeminiClassifier classifier;
    private Method parseResponse;

    @BeforeEach
    void setUp() throws Exception {
        classifier = new VertexAiGeminiClassifier(
                "test-project", "us-central1", "gemini-test",
                CircuitBreakerRegistry.ofDefaults()
        );
        parseResponse = VertexAiGeminiClassifier.class.getDeclaredMethod("parseResponse", String.class);
        parseResponse.setAccessible(true);
    }

    private ClassificationResult invoke(String responseBody) throws Exception {
        return (ClassificationResult) parseResponse.invoke(classifier, responseBody);
    }

    private static String wrapGeminiResponse(String jsonArrayText) {
        return """
                {
                  "candidates": [{
                    "content": { "parts": [{ "text": "%s" }] }
                  }],
                  "usageMetadata": { "promptTokenCount": 100, "candidatesTokenCount": 50 }
                }
                """.formatted(jsonArrayText.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    // ─── Multi-layer parsing (AC #7) ──────────────────────────────────────────

    @Test
    void parseResponse_multiLayer_returnsSortedByLayerOrder() throws Exception {
        // Gemini returns tertiary first, but result should be sorted primary→secondary→tertiary
        String json = """
                [
                  {"layer":"tertiary","kfCode":"51010101","description":"Stretch wrap","score":0.60,"weightEstimateKg":0.300,"unitsPerProduct":480},
                  {"layer":"primary","kfCode":"11010101","description":"PET palack","score":0.90,"weightEstimateKg":0.025,"unitsPerProduct":1},
                  {"layer":"secondary","kfCode":"41010201","description":"Karton multipack","score":0.75,"weightEstimateKg":0.050,"unitsPerProduct":6}
                ]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.suggestions().get(0).layer()).isEqualTo("primary");
        assertThat(result.suggestions().get(1).layer()).isEqualTo("secondary");
        assertThat(result.suggestions().get(2).layer()).isEqualTo("tertiary");
    }

    @Test
    void parseResponse_multiLayer_confidenceIsMinScore() throws Exception {
        // Min score = 0.60 → MEDIUM (0.50 ≤ 0.60 < 0.80)
        String json = """
                [
                  {"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":1},
                  {"layer":"secondary","kfCode":"41010201","description":"Karton","score":0.60,"unitsPerProduct":6}
                ]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.MEDIUM);
    }

    @Test
    void parseResponse_multiLayer_allHighConfidence() throws Exception {
        String json = """
                [
                  {"layer":"primary","kfCode":"11010101","description":"PET","score":0.95,"unitsPerProduct":1},
                  {"layer":"secondary","kfCode":"41010201","description":"Karton","score":0.85,"unitsPerProduct":6}
                ]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.HIGH);
    }

    @Test
    void parseResponse_weightEstimateKg_deserializedCorrectly() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"weightEstimateKg":0.025,"unitsPerProduct":1}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).weightEstimateKg())
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("0.025"));
    }

    @Test
    void parseResponse_missingWeight_defaultsToNull() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":1}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).weightEstimateKg()).isNull();
    }

    // ─── P1: Defense-in-depth validations ─────────────────────────────────────

    @Test
    void parseResponse_unitsPerProduct_zeroDefaultsToOne() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":0}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).unitsPerProduct()).isEqualTo(1);
    }

    @Test
    void parseResponse_unitsPerProduct_negativeDefaultsToOne() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":-5}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).unitsPerProduct()).isEqualTo(1);
    }

    @Test
    void parseResponse_negativeWeight_defaultsToNull() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"weightEstimateKg":-0.5,"unitsPerProduct":1}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).weightEstimateKg()).isNull();
    }

    @Test
    void parseResponse_zeroWeight_defaultsToNull() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"weightEstimateKg":0.0,"unitsPerProduct":1}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions().get(0).weightEstimateKg()).isNull();
    }

    // ─── P3: Layer normalization ──────────────────────────────────────────────

    @Test
    void parseResponse_layerUpperCase_normalizedToLowerCase() throws Exception {
        String json = """
                [
                  {"layer":"PRIMARY","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":1},
                  {"layer":"Secondary","kfCode":"41010201","description":"Karton","score":0.80,"unitsPerProduct":6}
                ]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions()).hasSize(2);
        assertThat(result.suggestions().get(0).layer()).isEqualTo("primary");
        assertThat(result.suggestions().get(1).layer()).isEqualTo("secondary");
    }

    // ─── Backward compatibility ───────────────────────────────────────────────

    @Test
    void parseResponse_oldFormatWithoutLayer_defaultsToPrimary() throws Exception {
        String json = """
                [{"kfCode":"11010101","description":"PET palack","score":0.85}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).layer()).isEqualTo("primary");
        assertThat(result.suggestions().get(0).unitsPerProduct()).isEqualTo(1);
        assertThat(result.suggestions().get(0).weightEstimateKg()).isNull();
    }

    // ─── Token counts ─────────────────────────────────────────────────────────

    @Test
    void parseResponse_extractsTokenCounts() throws Exception {
        String json = """
                [{"layer":"primary","kfCode":"11010101","description":"PET","score":0.90,"unitsPerProduct":1}]""";
        ClassificationResult result = invoke(wrapGeminiResponse(json));

        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
    }

    // ─── Empty / malformed responses ──────────────────────────────────────────

    @Test
    void parseResponse_emptyArray_returnsEmptyResult() throws Exception {
        ClassificationResult result = invoke(wrapGeminiResponse("[]"));

        assertThat(result.suggestions()).isEmpty();
        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.LOW);
    }

    @Test
    void parseResponse_malformedJson_returnsEmptyResult() throws Exception {
        String body = """
                { "candidates": [{ "content": { "parts": [{ "text": "not json" }] } }],
                  "usageMetadata": { "promptTokenCount": 0, "candidatesTokenCount": 0 } }
                """;
        ClassificationResult result = invoke(body);

        assertThat(result.suggestions()).isEmpty();
    }
}
