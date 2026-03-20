package hu.riskguard.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutboxHealthIndicator}.
 * Covers: never run state, clean run, has failed records.
 */
class OutboxHealthIndicatorTest {

    private OutboxHealthState healthState;
    private OutboxHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        healthState = new OutboxHealthState();
        indicator = new OutboxHealthIndicator(healthState);
    }

    @Test
    void health_neverRun_reportsUpWithLastRunNever() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastRun", "never");
        assertThat(health.getDetails()).containsEntry("pendingCount", 0);
        assertThat(health.getDetails()).containsEntry("failedCount", 0);
        assertThat(health.getDetails()).containsEntry("lastEmailsSent", 0);
    }

    @Test
    void health_cleanRun_reportsUpWithCounts() {
        // Given — processor has run once: sent 3 emails, 0 pending, 0 failed
        healthState.recordRun(3, 0, 0);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("lastRun")).isNotEqualTo("never");
        assertThat(health.getDetails()).containsEntry("pendingCount", 0);
        assertThat(health.getDetails()).containsEntry("failedCount", 0);
        assertThat(health.getDetails()).containsEntry("lastEmailsSent", 3);
    }

    @Test
    void health_hasFailedRecords_reportsUpWithNonZeroFailedCount() {
        // Given — some records have permanently failed
        healthState.recordRun(5, 2, 3);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pendingCount", 2);
        assertThat(health.getDetails()).containsEntry("failedCount", 3);
        assertThat(health.getDetails()).containsEntry("lastEmailsSent", 5);
    }
}
