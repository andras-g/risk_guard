package hu.riskguard.epr.registry.classifier;

import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test for the batch packaging classifier (Story 10.3 AC #27).
 *
 * <p>Gated behind {@code RG_INTEGRATION_VERTEX_AI=true} — requires Google ADC
 * and network access to {@code europe-west1} Vertex AI. Mirrors the env-gated
 * pattern from {@link VertexAiGeminiClassifierIntegrationTest}.
 *
 * <p>Wires {@link VertexAiGeminiClassifier} directly into {@link BatchPackagingClassifierService}
 * rather than going through {@code ClassifierRouter}, matching the precedent set by
 * {@link VertexAiGeminiClassifierIntegrationTest} (AC #27 explicitly references that
 * pattern). This test exercises the <i>Gemini + batch orchestration</i> path end-to-end;
 * router-level fallback and cap behaviour are covered by
 * {@code ClassifierRouterTest} (unit) and {@code BatchPackagingClassifierServiceTest}
 * (unit with mocks), so the skip here does not leave the router untested.
 */
@EnabledIfEnvironmentVariable(named = "RG_INTEGRATION_VERTEX_AI", matches = "true")
class BatchPackagingClassifierIntegrationTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void classify_threeHungarianPairs_returnsExpectedFamilyPrefixes() throws IOException {
        VertexAiGeminiClassifier liveClassifier = new VertexAiGeminiClassifier(
                System.getenv().getOrDefault("GCP_PROJECT_ID", "risk-guard-dev"),
                "europe-west1",
                "gemini-3.0-flash-preview",
                CircuitBreakerRegistry.ofDefaults()
        );
        BatchPackagingClassifierService service = new BatchPackagingClassifierService(
                liveClassifier, new SimpleMeterRegistry(), 3);

        List<PairRequest> pairs = List.of(
                new PairRequest("39233010", "PET palack 0,5L ásványvíz"),
                new PairRequest("70109000", "Üvegpalack borhoz 750ml"),
                new PairRequest("76129020", "Alumínium üdítős doboz 330ml"));

        List<BatchPackagingResult> results = service.classify(pairs, TENANT_ID);

        assertThat(results).hasSize(3);
        // Plastic family
        assertThat(results.get(0).layers()).isNotEmpty();
        assertThat(results.get(0).layers().get(0).kfCode()).startsWith("1101");
        // Glass family
        assertThat(results.get(1).layers()).isNotEmpty();
        assertThat(results.get(1).layers().get(0).kfCode()).startsWith("1105");
        // Metals family
        assertThat(results.get(2).layers()).isNotEmpty();
        assertThat(results.get(2).layers().get(0).kfCode()).startsWith("1106");
    }
}
