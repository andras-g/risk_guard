package hu.riskguard.epr.registry.api.exception;

import hu.riskguard.epr.registry.api.dto.ClassifierUsageInfo;

/**
 * Thrown by the batch classifier controller when the requested batch size would
 * exceed the tenant's monthly cap (Story 10.3 AC #8). Mapped to
 * {@code 429 Too Many Requests} with the carried {@link ClassifierUsageInfo} as
 * the response body and a {@code Retry-After} header pointing to the next
 * Europe/Budapest month boundary.
 */
public class ClassifierCapExceededException extends RuntimeException {

    private final transient ClassifierUsageInfo usageInfo;
    private final int requestedPairs;

    public ClassifierCapExceededException(ClassifierUsageInfo usageInfo, int requestedPairs) {
        super("Monthly classifier cap would be exceeded: " + requestedPairs
                + " pairs requested, " + usageInfo.callsRemaining() + " remaining.");
        this.usageInfo = usageInfo;
        this.requestedPairs = requestedPairs;
    }

    public ClassifierUsageInfo usageInfo() {
        return usageInfo;
    }

    public int requestedPairs() {
        return requestedPairs;
    }
}
