package hu.riskguard.core.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Health indicator for the background async ingestor.
 * Reports the last run timestamp, entries processed, and error count via {@code /actuator/health}.
 *
 * <p>The ingestor is always considered {@code UP} — partial failures (non-zero error count)
 * are expected and non-fatal (e.g., demo mode with empty watchlist, transient source outages).
 * Operators can monitor the {@code lastErrorCount} detail for trend analysis.
 *
 * <p>Before the ingestor has ever run, reports {@code UP} with {@code lastRun: "never"}.
 *
 * @see AsyncIngestorHealthState
 */
@Component
public class AsyncIngestorHealthIndicator implements HealthIndicator {

    private final AsyncIngestorHealthState healthState;

    public AsyncIngestorHealthIndicator(AsyncIngestorHealthState healthState) {
        this.healthState = healthState;
    }

    @Override
    public Health health() {
        if (!healthState.hasRunAtLeastOnce()) {
            return Health.up()
                    .withDetail("lastRun", "never")
                    .withDetail("lastEntriesProcessed", 0)
                    .withDetail("lastErrorCount", 0)
                    .build();
        }

        OffsetDateTime lastRun = healthState.getLastRun();
        int entriesProcessed = healthState.getLastEntriesProcessed();
        int errorCount = healthState.getLastErrorCount();

        return Health.up()
                .withDetail("lastRun", lastRun.toString())
                .withDetail("lastEntriesProcessed", entriesProcessed)
                .withDetail("lastErrorCount", errorCount)
                .build();
    }
}
