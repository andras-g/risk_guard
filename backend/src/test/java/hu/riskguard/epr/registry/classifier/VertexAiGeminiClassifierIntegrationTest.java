package hu.riskguard.epr.registry.classifier;

import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test for {@link VertexAiGeminiClassifier}.
 *
 * <p>Gated behind {@code RG_INTEGRATION_VERTEX_AI=true} — requires valid Google ADC
 * (run {@code gcloud auth application-default login} locally, or rely on workload identity
 * in cloud). NOT part of the normal test run.
 *
 * <p>Verifies end-to-end: HTTP client build, auth token fetch, Gemini call,
 * JSON parse, and top-1 suggestion matches expected KF code for representative
 * Hungarian packaging items.
 *
 * <p>CP-5 §8.6 validation gate — see also {@link KfClassifierValidationTest}.
 */
@EnabledIfEnvironmentVariable(named = "RG_INTEGRATION_VERTEX_AI", matches = "true")
class VertexAiGeminiClassifierIntegrationTest {

    private VertexAiGeminiClassifier newClassifier() throws IOException {
        return new VertexAiGeminiClassifier(
                System.getenv().getOrDefault("GCP_PROJECT_ID", "risk-guard-dev"),
                "europe-west1",
                "gemini-3.0-flash-preview",
                CircuitBreakerRegistry.ofDefaults()
        );
    }

    @Test
    void classify_petBottle_returnsPlasticKfCode() throws IOException {
        VertexAiGeminiClassifier classifier = newClassifier();

        ClassificationResult result = classifier.classify("PET palack 0.5L ásványvíz", "39233010");

        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).kfCode()).startsWith("1101"); // plastics family
        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VERTEX_GEMINI);
    }

    @Test
    void classify_aluminumCan_returnsMetalKfCode() throws IOException {
        VertexAiGeminiClassifier classifier = newClassifier();

        ClassificationResult result = classifier.classify("Alumínium üdítős doboz 330 ml", "76129020");

        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).kfCode()).startsWith("1106"); // metals family
    }

    @Test
    void classify_glassBottle_returnsGlassKfCode() throws IOException {
        VertexAiGeminiClassifier classifier = newClassifier();

        ClassificationResult result = classifier.classify("Üvegpalack borhoz 750ml", "70109000");

        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).kfCode()).startsWith("1105"); // glass family
    }
}
