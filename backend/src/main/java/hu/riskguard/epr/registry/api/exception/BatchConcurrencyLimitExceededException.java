package hu.riskguard.epr.registry.api.exception;

/**
 * Thrown by the batch classifier controller when the per-tenant concurrent-batch
 * cap (Story 10.3 AC #15, default 3) is reached. Mapped to {@code 429 Too Many
 * Requests} with body {@code "Concurrent batch limit (3) exceeded for tenant"}
 * and {@code Retry-After: 5}.
 */
public class BatchConcurrencyLimitExceededException extends RuntimeException {

    private final int permitsPerTenant;

    public BatchConcurrencyLimitExceededException(int permitsPerTenant) {
        super("Concurrent batch limit (" + permitsPerTenant + ") exceeded for tenant");
        this.permitsPerTenant = permitsPerTenant;
    }

    public int permitsPerTenant() {
        return permitsPerTenant;
    }
}
