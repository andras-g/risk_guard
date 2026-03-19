package hu.riskguard.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WatchlistMonitorHealthIndicator}.
 * Covers: never run (UP with lastRun:never), clean run (UP with counts),
 * partial failure (UP with non-zero errorCount).
 */
class WatchlistMonitorHealthIndicatorTest {

    private WatchlistMonitorHealthState healthState;
    private WatchlistMonitorHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        healthState = new WatchlistMonitorHealthState();
        indicator = new WatchlistMonitorHealthIndicator(healthState);
    }

    @Test
    void health_neverRun_reportsUpWithLastRunNever() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastRun", "never");
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 0);
        assertThat(health.getDetails()).containsEntry("lastChangesDetected", 0);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 0);
    }

    @Test
    void health_cleanRun_reportsUpWithCounts() {
        healthState.recordRun(10, 2, 0);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("lastRun")).isNotEqualTo("never");
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 10);
        assertThat(health.getDetails()).containsEntry("lastChangesDetected", 2);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 0);
    }

    @Test
    void health_partialFailure_stillReportsUpWithNonZeroErrorCount() {
        healthState.recordRun(5, 1, 3);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 5);
        assertThat(health.getDetails()).containsEntry("lastChangesDetected", 1);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 3);
    }
}
