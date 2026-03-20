package hu.riskguard.screening.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.config.WatchlistMonitorHealthState;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.PiiUtil;
import hu.riskguard.notification.domain.MonitoredPartner;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.screening.domain.ScreeningService.SnapshotVerdictResult;
import hu.riskguard.core.events.PartnerStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Background scheduled job that monitors watchlist entries for verdict status changes.
 *
 * <p>Runs daily on a configurable cron schedule (default: 04:00 UTC / 06:00 Budapest),
 * AFTER the {@link AsyncIngestor} (02:00 UTC) has refreshed snapshot data. Iterates over
 * all watchlist entries cross-tenant and compares the latest verdict against the stored
 * {@code last_verdict_status} on each {@code watchlist_entries} row.
 *
 * <p><b>Module placement:</b> Lives in {@code screening.domain} alongside {@link AsyncIngestor}
 * to avoid a circular module dependency (screening ↔ notification). Both background jobs
 * live in screening and call the {@link NotificationService} facade for watchlist data access.
 *
 * <p><b>Design:</b> The monitor reads EXISTING verdict data via {@link ScreeningService}.
 * It does NOT re-fetch data from external sources or re-compute verdicts. The
 * AsyncIngestor handles data freshness; this monitor handles change detection.
 *
 * <p><b>Failure Resilience:</b> If the latest verdict indicates a transient failure
 * (INCOMPLETE/UNAVAILABLE), the existing {@code last_verdict_status} is NOT overwritten
 * (transient failures do not constitute status changes). Processing continues to the
 * next entry — no list-wide abort.
 *
 * <p><b>Demo Mode:</b> In demo mode, the monitor still iterates all entries and checks
 * for changes. Since demo data is static, no changes are detected, but the infrastructure
 * is validated and {@code last_checked_at} is updated.
 *
 * @see WatchlistMonitorHealthState
 * @see AsyncIngestor
 */
@Component
public class WatchlistMonitor {

    private static final Logger log = LoggerFactory.getLogger(WatchlistMonitor.class);

    private final NotificationService notificationService;
    private final ScreeningService screeningService;
    private final ApplicationEventPublisher eventPublisher;
    private final RiskGuardProperties properties;
    private final WatchlistMonitorHealthState healthState;

    public WatchlistMonitor(NotificationService notificationService,
                            ScreeningService screeningService,
                            ApplicationEventPublisher eventPublisher,
                            RiskGuardProperties properties,
                            WatchlistMonitorHealthState healthState) {
        this.notificationService = notificationService;
        this.screeningService = screeningService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.healthState = healthState;
    }

    /**
     * Scheduled entry point — triggered by Spring's @Scheduled mechanism.
     * Runs sequentially on the scheduler thread with configurable inter-evaluation delay.
     */
    @Scheduled(cron = "${risk-guard.watchlist-monitor.cron:0 0 4 * * ?}")
    public void monitor() {
        String mode = properties.getDataSource().getMode();
        boolean isDemoMode = "demo".equalsIgnoreCase(mode);

        log.info("WatchlistMonitor starting mode={}", mode);

        List<MonitoredPartner> partners = notificationService.getMonitoredPartnersWithVerdicts();
        int processed = 0;
        int changes = 0;
        int errors = 0;

        for (MonitoredPartner partner : partners) {
            try {
                TenantContext.setCurrentTenant(partner.tenantId());
                OffsetDateTime now = OffsetDateTime.now();

                SnapshotVerdictResult current = screeningService
                        .getLatestSnapshotWithVerdict(partner.tenantId(), partner.taxNumber());

                if (current == null) {
                    // No verdict exists — nothing to compare. Update checked_at only.
                    notificationService.updateCheckedAt(
                            partner.tenantId(), partner.taxNumber(), now);
                    processed++;
                    continue;
                }

                if (current.transientFailure()) {
                    // Transient failure — do NOT overwrite existing status with INCOMPLETE.
                    notificationService.updateCheckedAt(
                            partner.tenantId(), partner.taxNumber(), now);
                    errors++;
                    log.warn("Transient failure during monitoring tax_number={} tenant={}",
                            PiiUtil.maskTaxNumber(partner.taxNumber()), partner.tenantId());
                    processed++;
                    continue;
                }

                String newStatus = current.verdictStatus();
                String oldStatus = partner.lastVerdictStatus();

                if (!Objects.equals(oldStatus, newStatus)) {
                    changes++;
                    log.info("Verdict changed tax_number={} tenant={} previous={} new={}",
                            PiiUtil.maskTaxNumber(partner.taxNumber()), partner.tenantId(),
                            oldStatus, newStatus);
                    String sha256Hash = screeningService.getAuditHashByVerdictId(current.verdictId());
                    eventPublisher.publishEvent(PartnerStatusChanged.of(
                            current.verdictId(), partner.tenantId(), partner.taxNumber(),
                            oldStatus, newStatus, sha256Hash));
                    // PartnerStatusChangedListener handles the verdict status + timestamp update
                    // for status changes. Calling updateVerdictStatus here would cause a double-write.
                } else {
                    // No change — still update last_checked_at and refresh the timestamp.
                    // The event listener only fires on changes, so unchanged entries need direct update.
                    notificationService.updateVerdictStatus(
                            partner.tenantId(), partner.taxNumber(), newStatus, now);
                }
                processed++;
            } catch (Exception e) {
                errors++;
                // Log exception class + message only — full stack trace could contain
                // PII (tax numbers) from DB constraint violations or downstream calls.
                log.error("Monitor entry failed tenant={} error={}",
                        partner.tenantId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                TenantContext.clear();
                if (!isDemoMode) {
                    sleepIfNeeded();
                }
            }
        }

        healthState.recordRun(processed, changes, errors);

        if (isDemoMode) {
            log.info("WatchlistMonitor completed [demo mode] entries_processed={} changes_detected={}",
                    processed, changes);
        } else {
            log.info("WatchlistMonitor completed mode={} entries_processed={} changes_detected={} errors={}",
                    mode, processed, changes, errors);
        }
    }

    /**
     * Apply configurable inter-evaluation delay between successive evaluations.
     * Skipped in demo mode (fixture data is in-memory, no rate limit concern).
     */
    private void sleepIfNeeded() {
        long delayMs = properties.getWatchlistMonitor().getDelayBetweenEvaluationsMs();
        if (delayMs > 0) {
            sleepBetweenEvaluations(delayMs);
        }
    }

    /**
     * Performs the actual thread sleep for rate limiting. Package-visible for testability.
     */
    void sleepBetweenEvaluations(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("WatchlistMonitor sleep interrupted");
        }
    }

}
