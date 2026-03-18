package hu.riskguard.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AsyncIngestorHealthIndicator}.
 * Covers: (a) never run → UP with "never", (b) clean run → UP with counts,
 * (c) partial failure → UP with non-zero error count.
 */
class AsyncIngestorHealthIndicatorTest {

    AsyncIngestorHealthState healthState;
    AsyncIngestorHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        healthState = new AsyncIngestorHealthState();
        indicator = new AsyncIngestorHealthIndicator(healthState);
    }

    @Test
    void health_neverRun_returnsUpWithNever() {
        // Given — ingestor has never run
        // (healthState is freshly created — no recordRun() calls)

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastRun", "never");
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 0);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 0);
    }

    @Test
    void health_cleanRun_returnsUpWithCounts() {
        // Given — ingestor has run successfully with 5 entries, no errors
        healthState.recordRun(5, 0);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("lastRun")).isNotNull();
        assertThat(health.getDetails().get("lastRun").toString()).isNotEqualTo("never");
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 5);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 0);
    }

    @Test
    void health_partialFailure_returnsUpWithNonZeroErrorCount() {
        // Given — ingestor ran with 3 entries, 1 error
        healthState.recordRun(3, 1);

        // When
        Health health = indicator.health();

        // Then — status is still UP (partial failures are non-fatal)
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("lastRun")).isNotNull();
        assertThat(health.getDetails().get("lastRun").toString()).isNotEqualTo("never");
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 3);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 1);
    }

    @Test
    void health_allFailed_returnsUpWithAllErrors() {
        // Given — ingestor ran with 5 entries, all 5 failed
        healthState.recordRun(5, 5);

        // When
        Health health = indicator.health();

        // Then — still UP per AC6 (partial failures expected, non-fatal)
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 5);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 5);
    }

    @Test
    void health_multipleRuns_reportsLatestRun() {
        // Given — two consecutive runs, second has different counts
        healthState.recordRun(10, 2);
        healthState.recordRun(7, 0);

        // When
        Health health = indicator.health();

        // Then — reports latest run data
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastEntriesProcessed", 7);
        assertThat(health.getDetails()).containsEntry("lastErrorCount", 0);
    }
}
