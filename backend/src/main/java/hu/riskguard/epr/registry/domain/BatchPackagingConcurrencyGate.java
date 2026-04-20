package hu.riskguard.epr.registry.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Per-tenant concurrent-batch gate (Story 10.3 AC #15).
 *
 * <p>Caps each tenant at {@value #DEFAULT_PERMITS} concurrent batch requests.
 * A 4th simultaneous request gets a {@code false} from {@link #tryAcquire(UUID)};
 * the controller maps that to {@code 429 Too Many Requests} with
 * {@code Retry-After: 5} as a rough hint (no external coordinator).
 *
 * <p>This gate exists to bound damage from runaway concurrency on the cap pre-check
 * (AC #9) and on the shared {@code vertex-gemini} circuit breaker
 * ({@code slidingWindowSize: 10}). It does NOT replace the cap check itself.
 *
 * <p>Per-tenant {@link Semaphore}s are lazily created via {@code computeIfAbsent}
 * and never removed — the {@code ConcurrentHashMap} grows once per tenant and
 * stabilises. Permit count is fixed at construction (3); a future story may make
 * it configurable.
 */
@Component
public class BatchPackagingConcurrencyGate {

    private static final Logger log = LoggerFactory.getLogger(BatchPackagingConcurrencyGate.class);

    static final int DEFAULT_PERMITS = 3;

    private final ConcurrentHashMap<UUID, Semaphore> tenantSemaphores = new ConcurrentHashMap<>();
    private final int permits;

    public BatchPackagingConcurrencyGate(
            @Value("${risk-guard.classifier.batch.per-tenant-concurrent:" + DEFAULT_PERMITS + "}")
            int permits) {
        this.permits = Math.max(1, permits);
    }

    /**
     * Non-blocking permit acquisition. Returns {@code false} immediately when the
     * tenant is at or above the cap.
     */
    public boolean tryAcquire(UUID tenantId) {
        return semaphoreFor(tenantId).tryAcquire();
    }

    /**
     * Release the permit acquired by a successful {@link #tryAcquire(UUID)} call.
     * MUST be called exactly once per successful acquire (typically from a
     * {@code finally} block).
     *
     * <p>No-op (with a WARN log) when called for a tenant whose semaphore was never
     * created via {@link #tryAcquire(UUID)} — defensive guard against an unpaired
     * release inflating permits above the configured cap.
     */
    public void release(UUID tenantId) {
        Semaphore semaphore = tenantSemaphores.get(tenantId);
        if (semaphore == null) {
            log.warn("Unpaired release() call for tenantId={} — no semaphore acquired. Ignoring.", tenantId);
            return;
        }
        semaphore.release();
    }

    /** Configured per-tenant cap (AC body wording uses the literal "(3)"). */
    public int permitsPerTenant() {
        return permits;
    }

    private Semaphore semaphoreFor(UUID tenantId) {
        return tenantSemaphores.computeIfAbsent(tenantId, k -> new Semaphore(permits));
    }
}
