package hu.riskguard.epr.registry.domain;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * Batch packaging classifier (Story 10.3).
 *
 * <p>Composes the existing Story 9.3 {@link KfCodeClassifierService} (the {@code @Primary}
 * {@code ClassifierRouter}) under bounded concurrency, with per-pair failure isolation,
 * tenant-context propagation across virtual threads, and Micrometer observability.
 *
 * <p><b>Concurrency primitive.</b> Java 25 {@link StructuredTaskScope} with virtual threads,
 * matching {@code CompanyDataAggregator.java:59-88}. All pairs are forked at once; a
 * shared {@link Semaphore} caps the in-flight Gemini calls at
 * {@code risk-guard.classifier.batch.concurrency} (default 10).
 *
 * <p><b>Tenant context.</b> Captured before forking and re-established inside each task
 * via {@link #withTenant(UUID, Supplier)}. Without this, {@code ClassifierRouter} reads
 * a null tenant and silently skips both the cap check and the per-pair usage increment
 * (Story 10.3 Dev Notes §"Tenant context propagation").
 *
 * <p><b>Audit.</b> Stateless — does NOT write to {@code registry_entry_audit_log}
 * (no {@code productId} at classification time; AC #21). Compliance audit lands in
 * Story 10.4 at persist time. This service emits Micrometer counters only.
 *
 * <p><b>Transactional contract.</b> Not {@code @Transactional} — the batch spans
 * Vertex AI HTTP calls, which must NEVER run inside a DB transaction
 * (Story 10.1 tx-pool refactor).
 */
@Service
public class BatchPackagingClassifierService {

    private static final Logger log = LoggerFactory.getLogger(BatchPackagingClassifierService.class);

    public static final String COUNTER_NAME = "classifier.batch.pairs";
    public static final String TIMER_NAME = "classifier.batch.duration";

    private final KfCodeClassifierService classifierService;
    private final int concurrency;
    private final Map<String, Counter> strategyCounters;
    private final Map<String, Timer> durationTimers;
    private final MeterRegistry meterRegistry;

    public BatchPackagingClassifierService(
            KfCodeClassifierService classifierService,
            MeterRegistry meterRegistry,
            @Value("${risk-guard.classifier.batch.concurrency:10}") int concurrency) {
        this.classifierService = classifierService;
        this.meterRegistry = meterRegistry;
        this.concurrency = Math.max(1, concurrency);
        this.strategyCounters = new HashMap<>();
        for (String strategy : List.of(
                BatchPackagingResult.STRATEGY_GEMINI,
                BatchPackagingResult.STRATEGY_VTSZ_FALLBACK,
                BatchPackagingResult.STRATEGY_UNRESOLVED)) {
            strategyCounters.put(strategy, Counter.builder(COUNTER_NAME)
                    .description("Per-pair batch classifier outcomes, tagged by strategy.")
                    .tag("strategy", strategy)
                    .register(meterRegistry));
        }
        this.durationTimers = new HashMap<>();
        for (String bucket : List.of("1-10", "11-50", "51-100")) {
            durationTimers.put(bucket, Timer.builder(TIMER_NAME)
                    .description("Batch classifier total wall-clock time, bucketed by pairCount.")
                    .tag("pairCount", bucket)
                    .register(meterRegistry));
        }

        // Smoke-load the new batch prompt template (AC #16 / Task 8): fail fast at startup
        // if the resource is missing, even though the prompt is not yet routed through
        // VertexAiGeminiClassifier (Story 10.5+ may swap it in).
        try {
            String body = new ClassPathResource("prompts/packaging-stack-v1.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded packaging-stack-v1.txt prompt template ({} chars)", body.length());
        } catch (IOException e) {
            throw new IllegalStateException("prompts/packaging-stack-v1.txt missing on classpath", e);
        }
    }

    /**
     * Classify every pair in the batch under bounded concurrency.
     * Per-pair failures degrade to {@code UNRESOLVED}; the batch never throws.
     *
     * @param pairs    input pairs (already validated by Bean Validation in the controller)
     * @param tenantId tenant UUID extracted from JWT {@code active_tenant_id} (controller responsibility)
     * @return one result per input pair, in input order
     */
    public List<BatchPackagingResult> classify(List<PairRequest> pairs, UUID tenantId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer timer = durationTimers.get(pairCountBucket(pairs.size()));

        Semaphore concurrencyGate = new Semaphore(concurrency);
        AtomicReferenceArray<BatchPackagingResult> results = new AtomicReferenceArray<>(pairs.size());

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Void>awaitAll())) {
            for (int i = 0; i < pairs.size(); i++) {
                final int idx = i;
                final PairRequest pair = pairs.get(i);
                scope.fork(() -> {
                    BatchPackagingResult outcome = classifyOnePair(pair, tenantId, concurrencyGate);
                    results.set(idx, outcome);
                    incrementStrategyCounter(outcome.classificationStrategy());
                    return null;
                });
            }
            try {
                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch classifier interrupted; degrading remaining pairs to UNRESOLVED");
            }
        }

        // Fill any unfilled slot (e.g., interrupted scope) with UNRESOLVED so the response
        // is always size-aligned with the request.
        List<BatchPackagingResult> ordered = new ArrayList<>(pairs.size());
        for (int i = 0; i < pairs.size(); i++) {
            BatchPackagingResult r = results.get(i);
            if (r == null) {
                PairRequest p = pairs.get(i);
                r = BatchPackagingResult.unresolved(p.vtsz(), p.description());
                incrementStrategyCounter(BatchPackagingResult.STRATEGY_UNRESOLVED);
            }
            ordered.add(r);
        }

        sample.stop(timer);
        return ordered;
    }

    private BatchPackagingResult classifyOnePair(PairRequest pair, UUID tenantId, Semaphore gate) {
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for batch concurrency permit (pair vtsz={})", pair.vtsz());
            return BatchPackagingResult.unresolved(pair.vtsz(), pair.description());
        }
        try {
            return withTenant(tenantId, () -> {
                try {
                    ClassificationResult result = classifierService.classify(pair.description(), pair.vtsz());
                    return BatchPackagingResult.from(pair.vtsz(), pair.description(), result);
                } catch (Exception ex) {
                    // AC #5: per-pair failure isolation. Never let one pair abort the batch.
                    log.warn("Batch classifier pair failed vtsz={} : {}", pair.vtsz(), ex.toString());
                    return BatchPackagingResult.unresolved(pair.vtsz(), pair.description());
                }
            });
        } finally {
            gate.release();
        }
    }

    /**
     * Re-establish {@link TenantContext} inside a forked virtual thread (AC #12).
     * Mirrors {@code CompanyDataAggregator.withTenant} — the same pattern is used here
     * because virtual threads do NOT inherit the parent thread's {@code ScopedValue}
     * binding when forked under {@link StructuredTaskScope}.
     *
     * <p>Only clears {@link TenantContext} when this method actually set it, so a
     * null-tenant call path (defensive; the controller never passes null today) does
     * not wipe a pre-existing binding on the carrier thread.
     */
    private static <T> T withTenant(UUID tenantId, Supplier<T> task) {
        boolean set = false;
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            set = true;
        }
        try {
            return task.get();
        } finally {
            if (set) {
                TenantContext.clear();
            }
        }
    }

    private static String pairCountBucket(int n) {
        if (n <= 10) return "1-10";
        if (n <= 50) return "11-50";
        return "51-100";
    }

    private void incrementStrategyCounter(String strategy) {
        Counter counter = strategyCounters.get(strategy);
        if (counter == null) {
            log.warn("classifier.batch.pairs: no registered counter for strategy='{}', " +
                    "falling back to UNRESOLVED counter", strategy);
            counter = strategyCounters.get(BatchPackagingResult.STRATEGY_UNRESOLVED);
        }
        counter.increment();
    }
}
