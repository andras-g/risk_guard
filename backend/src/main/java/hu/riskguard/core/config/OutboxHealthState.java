package hu.riskguard.core.config;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe shared state between {@link hu.riskguard.notification.domain.OutboxProcessor} and
 * {@link OutboxHealthIndicator}.
 *
 * <p>The processor records run results; the health indicator reads them for {@code /actuator/health}.
 * Uses a single {@link AtomicReference} to an immutable {@link RunSnapshot} record to ensure
 * consistent reads — a health check will never see a mix of data from two different runs.
 *
 * <p>Pattern copied from {@link AsyncIngestorHealthState} (Story 3.5).
 */
@Component
public class OutboxHealthState {

    /**
     * Immutable snapshot of a single outbox processor run's result.
     * All fields are read/written atomically via the enclosing {@code AtomicReference}.
     */
    public record RunSnapshot(OffsetDateTime lastRun, int pendingCount, int failedCount, int lastEmailsSent) {}

    private final AtomicReference<RunSnapshot> snapshot = new AtomicReference<>(null);

    /**
     * Record the result of an outbox processor run — atomic single-swap guarantees consistent reads.
     *
     * @param lastEmailsSent count of emails successfully sent in this run
     * @param pendingCount   current count of PENDING outbox records (after this run)
     * @param failedCount    current count of FAILED outbox records (after this run)
     */
    public void recordRun(int lastEmailsSent, int pendingCount, int failedCount) {
        this.snapshot.set(new RunSnapshot(OffsetDateTime.now(), pendingCount, failedCount, lastEmailsSent));
    }

    /** @return timestamp of the last completed run, or {@code null} if never run */
    public OffsetDateTime getLastRun() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.lastRun() : null;
    }

    /** @return current count of PENDING outbox records */
    public int getPendingCount() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.pendingCount() : 0;
    }

    /** @return current count of FAILED outbox records */
    public int getFailedCount() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.failedCount() : 0;
    }

    /** @return count of emails sent in the last run */
    public int getLastEmailsSent() {
        RunSnapshot s = snapshot.get();
        return s != null ? s.lastEmailsSent() : 0;
    }

    /** @return true if the outbox processor has run at least once */
    public boolean hasRunAtLeastOnce() {
        return snapshot.get() != null;
    }

    /**
     * Reset state to initial values — for testing only.
     */
    void reset() {
        this.snapshot.set(null);
    }
}
