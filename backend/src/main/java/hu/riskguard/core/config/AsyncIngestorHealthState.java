package hu.riskguard.core.config;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe shared state between {@link hu.riskguard.screening.domain.AsyncIngestor} and
 * {@link AsyncIngestorHealthIndicator}.
 *
 * <p>The ingestor records run results; the health indicator reads them for {@code /actuator/health}.
 * All fields are atomic to ensure visibility across threads (scheduler thread writes,
 * HTTP request thread reads).
 */
@Component
public class AsyncIngestorHealthState {

    private final AtomicReference<OffsetDateTime> lastRun = new AtomicReference<>(null);
    private final AtomicInteger lastEntriesProcessed = new AtomicInteger(0);
    private final AtomicInteger lastErrorCount = new AtomicInteger(0);

    /**
     * Record the result of an ingestor run.
     *
     * @param entriesProcessed count of watchlist entries processed
     * @param errorCount       count of adapter failures during this run
     */
    public void recordRun(int entriesProcessed, int errorCount) {
        this.lastRun.set(OffsetDateTime.now());
        this.lastEntriesProcessed.set(entriesProcessed);
        this.lastErrorCount.set(errorCount);
    }

    /** @return timestamp of the last completed run, or {@code null} if never run */
    public OffsetDateTime getLastRun() {
        return lastRun.get();
    }

    /** @return count of watchlist entries processed in the last run */
    public int getLastEntriesProcessed() {
        return lastEntriesProcessed.get();
    }

    /** @return count of adapter failures in the last run (0 = clean run) */
    public int getLastErrorCount() {
        return lastErrorCount.get();
    }

    /** @return true if the ingestor has run at least once */
    public boolean hasRunAtLeastOnce() {
        return lastRun.get() != null;
    }

    /**
     * Reset state to initial values — for testing only.
     * Clears last-run timestamp, entries processed, and error count.
     */
    void reset() {
        this.lastRun.set(null);
        this.lastEntriesProcessed.set(0);
        this.lastErrorCount.set(0);
    }
}
