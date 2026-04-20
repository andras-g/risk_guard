package hu.riskguard.epr.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.classifier.ClassificationConfidence;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.classifier.KfSuggestion;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchPackagingClassifierService} (Story 10.3 AC #25).
 *
 * <p>Stubs the {@link KfCodeClassifierService} so the service is exercised without
 * the live Vertex AI dependency. A real {@link SimpleMeterRegistry} is used so we
 * can assert per-strategy counter increments (AC #19).
 */
class BatchPackagingClassifierServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String MODEL = "gemini-3.0-flash-preview";

    private KfCodeClassifierService classifier;
    private MeterRegistry meterRegistry;
    private BatchPackagingClassifierService service;

    @BeforeEach
    void setUp() {
        classifier = mock(KfCodeClassifierService.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new BatchPackagingClassifierService(classifier, meterRegistry, 10);
    }

    // ─── (a) 10 representative golden pairs → stable multi-layer output ────────

    @Test
    void classify_tenGoldenPairs_returnsStableMultiLayerOutput() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new ClassPathResource("golden/batch-packaging-v1.json").getInputStream());

        List<PairRequest> pairs = new ArrayList<>();
        Map<String, ClassificationResult> mocksByVtsz = new HashMap<>();
        for (JsonNode entry : root) {
            String vtsz = entry.get("input").get("vtsz").asText();
            String desc = entry.get("input").get("description").asText();
            pairs.add(new PairRequest(vtsz, desc));

            List<KfSuggestion> sugs = new ArrayList<>();
            for (JsonNode layer : entry.get("mockLayers")) {
                sugs.add(new KfSuggestion(
                        layer.get("kfCode").asText(),
                        layer.get("description").asText(),
                        layer.get("score").asDouble(),
                        layer.get("layer").asText(),
                        new BigDecimal(layer.get("weightEstimateKg").asText()),
                        layer.get("unitsPerProduct").asInt()
                ));
            }
            mocksByVtsz.put(vtsz, new ClassificationResult(
                    sugs, ClassificationStrategy.VERTEX_GEMINI, ClassificationConfidence.HIGH,
                    MODEL, Instant.now(), 100, 30));
        }
        for (PairRequest p : pairs) {
            when(classifier.classify(p.description(), p.vtsz())).thenReturn(mocksByVtsz.get(p.vtsz()));
        }

        List<BatchPackagingResult> results = service.classify(pairs, TENANT_ID);

        assertThat(results).hasSize(10);
        assertThat(results).extracting(BatchPackagingResult::classificationStrategy)
                .containsOnly(BatchPackagingResult.STRATEGY_GEMINI);
        // Order is preserved
        assertThat(results.get(0).vtsz()).isEqualTo("39233000");
        assertThat(results.get(0).layers()).hasSize(2);
        assertThat(results.get(2).vtsz()).isEqualTo("76129020");
        assertThat(results.get(2).layers()).hasSize(3);
        // Counter recorded 10 GEMINI increments
        assertThat(meterRegistry.counter(BatchPackagingClassifierService.COUNTER_NAME, "strategy",
                BatchPackagingResult.STRATEGY_GEMINI).count()).isEqualTo(10.0);
    }

    // ─── (b) Per-pair failure isolation ───────────────────────────────────────

    @Test
    void classify_oneFailingPair_otherPairsSucceed() {
        List<PairRequest> pairs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pairs.add(new PairRequest("12345678", "item-" + i));
        }
        // Pair index 3 throws; others return a single-layer GEMINI result
        when(classifier.classify(anyString(), anyString())).thenAnswer(invocation -> {
            String desc = invocation.getArgument(0);
            if ("item-3".equals(desc)) {
                throw new RuntimeException("simulated upstream failure");
            }
            return geminiSingleLayer("11010101", "Műanyag");
        });

        List<BatchPackagingResult> results = service.classify(pairs, TENANT_ID);

        assertThat(results).hasSize(20);
        BatchPackagingResult failedPair = results.get(3);
        assertThat(failedPair.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
        assertThat(failedPair.layers()).isEmpty();
        // Other 19 are GEMINI
        long geminiCount = results.stream()
                .filter(r -> r.classificationStrategy().equals(BatchPackagingResult.STRATEGY_GEMINI))
                .count();
        assertThat(geminiCount).isEqualTo(19L);
    }

    // ─── (c) T3 bound violations ──────────────────────────────────────────────

    @Test
    void classify_weightZero_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary", BigDecimal.ZERO, 1));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
        assertThat(result.layers()).isEmpty();
    }

    @Test
    void classify_weightAboveCeiling_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary",
                new BigDecimal("10001"), 1));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
    }

    @Test
    void classify_itemsPerParentZero_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary",
                new BigDecimal("0.025"), 0));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
    }

    @Test
    void classify_itemsPerParentAboveCeiling_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary",
                new BigDecimal("0.025"), 10001));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
    }

    @Test
    void classify_unknownLayerName_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "quaternary",
                new BigDecimal("0.025"), 1));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
    }

    @Test
    void classify_invalidKfCode_dropsLayer() {
        givenClassifierReturnsLayer(new KfSuggestion("123", "PET", 0.9, "primary",
                new BigDecimal("0.025"), 1));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
    }

    // ─── (c) T3 boundary-pass tests (exact upper bound must be accepted) ────────

    @Test
    void classify_weightAtCeiling_layerAccepted() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary",
                new BigDecimal("10000"), 1));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_GEMINI);
        assertThat(result.layers()).hasSize(1);
        assertThat(result.layers().get(0).weightEstimateKg()).isEqualByComparingTo("10000");
    }

    @Test
    void classify_itemsPerParentAtCeiling_layerAccepted() {
        givenClassifierReturnsLayer(new KfSuggestion("11010101", "PET", 0.9, "primary",
                new BigDecimal("0.025"), 10000));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_GEMINI);
        assertThat(result.layers()).hasSize(1);
        assertThat(result.layers().get(0).itemsPerParent()).isEqualTo(10000);
    }

    // ─── (c) Multi-layer all-dropped test ────────────────────────────────────

    @Test
    void classify_allThreeLayersFailDifferentBounds_becomesUnresolved() {
        when(classifier.classify(anyString(), anyString())).thenReturn(new ClassificationResult(
                List.of(
                        // bad weight (≤ 0)
                        new KfSuggestion("11010101", "PET", 0.9, "primary", BigDecimal.ZERO, 1),
                        // bad kfCode (not 8 digits)
                        new KfSuggestion("123", "Karton", 0.8, "secondary", new BigDecimal("0.050"), 6),
                        // bad itemsPerParent (> 10000)
                        new KfSuggestion("11020101", "Fólia", 0.7, "tertiary", new BigDecimal("0.300"), 10001)
                ),
                ClassificationStrategy.VERTEX_GEMINI, ClassificationConfidence.HIGH,
                MODEL, Instant.now(), 100, 30));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
        assertThat(result.layers()).isEmpty();
    }

    // ─── 4-layer Gemini response truncated to first 3 by level ────────────────

    @Test
    void classify_fourLayerResponse_truncatesToFirstThreeByLevel() {
        when(classifier.classify(anyString(), anyString())).thenReturn(new ClassificationResult(
                List.of(
                        new KfSuggestion("11010101", "primary",  0.9, "primary",   new BigDecimal("0.025"), 1),
                        new KfSuggestion("31010102", "secondary",0.8, "secondary", new BigDecimal("0.050"), 6),
                        new KfSuggestion("11020101", "tertiary", 0.7, "tertiary",  new BigDecimal("0.300"), 480),
                        new KfSuggestion("11050101", "extra",    0.6, "primary",   new BigDecimal("0.500"), 1)
                ),
                ClassificationStrategy.VERTEX_GEMINI, ClassificationConfidence.HIGH,
                MODEL, Instant.now(), 100, 30));

        BatchPackagingResult result = singleResult();

        assertThat(result.layers()).hasSize(3);
        // First-occurrence-per-level — first "primary" wins
        assertThat(result.layers().get(0).kfCode()).isEqualTo("11010101");
        assertThat(result.layers().get(0).level()).isEqualTo(1);
        assertThat(result.layers().get(1).level()).isEqualTo(2);
        assertThat(result.layers().get(2).level()).isEqualTo(3);
    }

    // ─── Empty Gemini response → UNRESOLVED ───────────────────────────────────

    @Test
    void classify_emptyClassifierResult_unresolved() {
        when(classifier.classify(anyString(), anyString())).thenReturn(ClassificationResult.empty());

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_UNRESOLVED);
        assertThat(result.layers()).isEmpty();
        assertThat(result.modelVersion()).isNull();
    }

    // ─── (d) Fallback tagging — VTSZ_PREFIX → "VTSZ_PREFIX_FALLBACK" ──────────

    @Test
    void classify_fallbackResult_taggedVtszPrefixFallback() {
        when(classifier.classify(anyString(), anyString())).thenReturn(new ClassificationResult(
                List.of(new KfSuggestion("11010101", "PET", 0.65, "primary", null, 1)),
                ClassificationStrategy.VTSZ_PREFIX, ClassificationConfidence.MEDIUM,
                null, Instant.now(), 0, 0));

        BatchPackagingResult result = singleResult();

        assertThat(result.classificationStrategy()).isEqualTo(BatchPackagingResult.STRATEGY_VTSZ_FALLBACK);
        assertThat(result.modelVersion()).isNull();
        assertThat(result.layers()).hasSize(1);
        assertThat(result.layers().get(0).weightEstimateKg()).isNull();
    }

    // ─── (e) Concurrency honoured — at most `concurrency` calls in flight ─────

    @Test
    void classify_concurrencyLimitHonoured_neverExceedsConfigured() throws InterruptedException {
        // AC #25(e) asks verbatim: "at most `concurrency` (10) calls are in-flight
        // simultaneously when 20 pairs submit". Match those exact numbers.
        int batchSize = 20;
        int configuredConcurrency = 10;

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxObserved = new AtomicInteger(0);
        CountDownLatch readyToFinish = new CountDownLatch(1);

        when(classifier.classify(anyString(), anyString())).thenAnswer(invocation -> {
            int now = inFlight.incrementAndGet();
            maxObserved.updateAndGet(prev -> Math.max(prev, now));
            try {
                // Hold each call until we've collected enough samples to detect the cap.
                readyToFinish.await(500, TimeUnit.MILLISECONDS);
            } finally {
                inFlight.decrementAndGet();
            }
            return geminiSingleLayer("11010101", "PET");
        });

        BatchPackagingClassifierService limited =
                new BatchPackagingClassifierService(classifier, new SimpleMeterRegistry(), configuredConcurrency);

        List<PairRequest> pairs = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) pairs.add(new PairRequest("12345678", "item-" + i));

        Thread observer = new Thread(() -> {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            readyToFinish.countDown();
        });
        observer.setDaemon(true);
        observer.start();

        List<BatchPackagingResult> results = limited.classify(pairs, TENANT_ID);

        assertThat(results).hasSize(batchSize);
        assertThat(maxObserved.get())
                .as("in-flight Gemini calls must never exceed configured concurrency")
                .isLessThanOrEqualTo(configuredConcurrency);
    }

    // ─── Smoke test — packaging-stack-v1.txt loads from classpath ─────────────

    @Test
    void promptTemplate_v1_loadsFromClasspath() throws IOException {
        String body = new ClassPathResource("prompts/packaging-stack-v1.txt")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("packaging-stack");
        assertThat(body).contains("primary");
        assertThat(body).contains("39233000"); // few-shot VTSZ from DemoInvoiceFixtures
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void givenClassifierReturnsLayer(KfSuggestion suggestion) {
        when(classifier.classify(anyString(), anyString())).thenReturn(new ClassificationResult(
                List.of(suggestion), ClassificationStrategy.VERTEX_GEMINI,
                ClassificationConfidence.HIGH, MODEL, Instant.now(), 50, 20));
    }

    private BatchPackagingResult singleResult() {
        List<BatchPackagingResult> results = service.classify(
                List.of(new PairRequest("12345678", "item")), TENANT_ID);
        verify(classifier, times(1)).classify("item", "12345678");
        return results.get(0);
    }

    private static ClassificationResult geminiSingleLayer(String kfCode, String description) {
        return new ClassificationResult(
                List.of(new KfSuggestion(kfCode, description, 0.9, "primary",
                        new BigDecimal("0.025"), 1)),
                ClassificationStrategy.VERTEX_GEMINI, ClassificationConfidence.HIGH,
                MODEL, Instant.now(), 100, 30);
    }
}
