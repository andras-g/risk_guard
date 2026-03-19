package hu.riskguard.core.config;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe shared state between {@link hu.riskguard.screening.domain.AsyncIngestor} and
 * {@link AsyncIngestorHealthIndicator}.
 *
 * <p>The ingestor records run results; the health indicator reads them for {@code /actuator/health}.
 * Uses a single {@link AtomicReference} to an immutable {@link RunSnapshot} record to ensure
 * consistent reads — a health check will never see a mix of data from two different runs.
 */
@Component
public class AsyncIngestorHealthState {

    /**
     * Immutable snapshot of a single ingestor run's result.
     * All three fields are read/written atomically via the enclosing {@code AtomicReference}.
     */
    public record RunSnapshot(OffsetDateTime lastRun, int entriesProcessed, int errorCount) {}

    private final AtomicReference<RunSnapshot> snapshot = new AtomicReference<>(null);

    /**
     * Record the result of an ingestor run — atomic single-swap guarantees consistent reads.
     *
     * @param entriesProcessed count of watchlist entries attempted
     * @param errorCount       count of adapter failures during this run
     */
    public void recordRun(int entriesProcessed, int errorCount) {
        this.snapshot.set(new RunSnapshot(OffsetDateTime.now(), entriesProcessed, errorCount));
    }

    /** @return timestamp of the last completed run, or {@code null} if never run */
    public OffsetDateTime getLastRun() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.lastRun() : null;
    }

    /** @return count of watchlist entries attempted in the last run */
    public int getLastEntriesProcessed() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.entriesProcessed() : 0;
    }

    /** @return count of adapter failures in the last run (0 = clean run) */
    public int getLastErrorCount() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.errorCount() : 0;
    }

    /** @return true if the ingestor has run at least once */
    public boolean hasRunAtLeastOnce() {
        return snapshot.get() != null;
    }

    /**
     * Reset state to initial values — for testing only.
     * Clears the run snapshot so the state appears as "never run".
     */
    void reset() {
        this.snapshot.set(null);
    }
}
