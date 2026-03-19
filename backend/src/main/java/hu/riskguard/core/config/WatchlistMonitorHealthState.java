package hu.riskguard.core.config;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe shared state between {@link hu.riskguard.screening.domain.WatchlistMonitor} and
 * {@link WatchlistMonitorHealthIndicator}.
 *
 * <p>The monitor records run results; the health indicator reads them for {@code /actuator/health}.
 * Uses a single {@link AtomicReference} to an immutable {@link RunSnapshot} record to ensure
 * consistent reads — a health check will never see a mix of data from two different runs.
 *
 * <p>Pattern copied from {@link AsyncIngestorHealthState} (Story 3.5).
 */
@Component
public class WatchlistMonitorHealthState {

    /**
     * Immutable snapshot of a single monitor run's result.
     * All fields are read/written atomically via the enclosing {@code AtomicReference}.
     */
    public record RunSnapshot(OffsetDateTime lastRun, int entriesProcessed, int changesDetected, int errorCount) {}

    private final AtomicReference<RunSnapshot> snapshot = new AtomicReference<>(null);

    /**
     * Record the result of a monitor run — atomic single-swap guarantees consistent reads.
     *
     * @param entriesProcessed count of watchlist entries evaluated
     * @param changesDetected  count of verdict status changes detected
     * @param errorCount       count of evaluation failures during this run
     */
    public void recordRun(int entriesProcessed, int changesDetected, int errorCount) {
        this.snapshot.set(new RunSnapshot(OffsetDateTime.now(), entriesProcessed, changesDetected, errorCount));
    }

    /** @return timestamp of the last completed run, or {@code null} if never run */
    public OffsetDateTime getLastRun() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.lastRun() : null;
    }

    /** @return count of watchlist entries evaluated in the last run */
    public int getLastEntriesProcessed() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.entriesProcessed() : 0;
    }

    /** @return count of verdict status changes detected in the last run */
    public int getLastChangesDetected() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.changesDetected() : 0;
    }

    /** @return count of evaluation failures in the last run (0 = clean run) */
    public int getLastErrorCount() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.errorCount() : 0;
    }

    /** @return true if the monitor has run at least once */
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
