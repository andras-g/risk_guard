package hu.riskguard.core.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Health indicator for the outbox processor.
 * Reports the last run timestamp, pending/failed counts, and emails sent via {@code /actuator/health}.
 *
 * <p>The outbox processor is always considered {@code UP} — processing failures are non-fatal
 * to application health. Failed outbox records are retried or eventually marked FAILED.
 * Operators can monitor {@code failedCount} for trend analysis.
 *
 * <p>Before the processor has ever run, reports {@code UP} with {@code lastRun: "never"}.
 *
 * @see OutboxHealthState
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxHealthState healthState;

    public OutboxHealthIndicator(OutboxHealthState healthState) {
        this.healthState = healthState;
    }

    @Override
    public Health health() {
        if (!healthState.hasRunAtLeastOnce()) {
            return Health.up()
                    .withDetail("lastRun", "never")
                    .withDetail("pendingCount", 0)
                    .withDetail("failedCount", 0)
                    .withDetail("lastEmailsSent", 0)
                    .build();
        }

        OffsetDateTime lastRun = healthState.getLastRun();
        int pendingCount = healthState.getPendingCount();
        int failedCount = healthState.getFailedCount();
        int lastEmailsSent = healthState.getLastEmailsSent();

        return Health.up()
                .withDetail("lastRun", lastRun.toString())
                .withDetail("pendingCount", pendingCount)
                .withDetail("failedCount", failedCount)
                .withDetail("lastEmailsSent", lastEmailsSent)
                .build();
    }
}
