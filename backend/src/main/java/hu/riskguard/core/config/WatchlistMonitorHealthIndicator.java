package hu.riskguard.core.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Health indicator for the background watchlist monitor.
 * Reports the last run timestamp, entries processed, changes detected, and error count
 * via {@code /actuator/health}.
 *
 * <p>The monitor is always considered {@code UP} — partial failures (non-zero error count)
 * are expected and non-fatal (e.g., transient source outages, demo mode with no changes).
 * Operators can monitor the {@code lastErrorCount} detail for trend analysis.
 *
 * <p>Before the monitor has ever run, reports {@code UP} with {@code lastRun: "never"}.
 *
 * @see WatchlistMonitorHealthState
 */
@Component
public class WatchlistMonitorHealthIndicator implements HealthIndicator {

    private final WatchlistMonitorHealthState healthState;

    public WatchlistMonitorHealthIndicator(WatchlistMonitorHealthState healthState) {
        this.healthState = healthState;
    }

    @Override
    public Health health() {
        if (!healthState.hasRunAtLeastOnce()) {
            return Health.up()
                    .withDetail("lastRun", "never")
                    .withDetail("lastEntriesProcessed", 0)
                    .withDetail("lastChangesDetected", 0)
                    .withDetail("lastErrorCount", 0)
                    .build();
        }

        OffsetDateTime lastRun = healthState.getLastRun();
        int entriesProcessed = healthState.getLastEntriesProcessed();
        int changesDetected = healthState.getLastChangesDetected();
        int errorCount = healthState.getLastErrorCount();

        return Health.up()
                .withDetail("lastRun", lastRun.toString())
                .withDetail("lastEntriesProcessed", entriesProcessed)
                .withDetail("lastChangesDetected", changesDetected)
                .withDetail("lastErrorCount", errorCount)
                .build();
    }
}
