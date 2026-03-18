package hu.riskguard.screening.domain;

import hu.riskguard.core.config.AsyncIngestorHealthState;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.WatchlistPartner;
import hu.riskguard.screening.internal.ScreeningRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Background scheduled job that proactively refreshes NAV debt data for all monitored partners.
 *
 * <p>Runs daily on a configurable cron schedule (default: 02:00 UTC / 04:00 Budapest).
 * Iterates over all watchlist entries across all tenants (cross-tenant background read)
 * and refreshes their company snapshots via {@link DataSourceService}.
 *
 * <p><b>Thread Isolation:</b> This ingestor does NOT use the virtual thread executor
 * used by {@code CompanyDataAggregator}. The {@code @Scheduled} method runs on Spring's
 * scheduler thread, and processes entries sequentially with a configurable inter-request
 * delay to avoid overwhelming data sources.
 *
 * <p><b>Failure Resilience:</b> If a data source is unavailable for a partner, the existing
 * snapshot is NOT overwritten — the previous {@code snapshot_data} and {@code checked_at}
 * are retained. Failures are logged at WARN level with masked tax number (PII compliance).
 *
 * <p><b>Demo Mode:</b> When {@code riskguard.data-source.mode=demo}, the ingestor still
 * iterates all watchlist entries and calls the demo adapter (which is a no-op fixture
 * refresh), but updates {@code checked_at} to confirm scheduling infrastructure works
 * end-to-end.
 *
 * @see AsyncIngestorHealthState
 */
@Component
public class AsyncIngestor {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestor.class);

    private final NotificationService notificationService;
    private final DataSourceService dataSourceService;
    private final ScreeningRepository screeningRepository;
    private final RiskGuardProperties properties;
    private final AsyncIngestorHealthState healthState;

    public AsyncIngestor(NotificationService notificationService,
                         DataSourceService dataSourceService,
                         ScreeningRepository screeningRepository,
                         RiskGuardProperties properties,
                         AsyncIngestorHealthState healthState) {
        this.notificationService = notificationService;
        this.dataSourceService = dataSourceService;
        this.screeningRepository = screeningRepository;
        this.properties = properties;
        this.healthState = healthState;
    }

    /**
     * Scheduled entry point — triggered by Spring's {@code @Scheduled} mechanism.
     * The method itself is NOT {@code @Async} — it runs on the scheduler thread
     * and processes entries sequentially with rate limiting.
     */
    @Scheduled(cron = "${risk-guard.async-ingestor.cron:0 0 2 * * ?}")
    public void ingest() {
        String mode = properties.getDataSource().getMode();
        boolean isDemoMode = "demo".equalsIgnoreCase(mode);

        log.info("Async ingestor starting mode={}", mode);

        List<WatchlistPartner> partners = notificationService.getMonitoredPartners();
        int processed = 0;
        int errors = 0;

        for (WatchlistPartner partner : partners) {
            boolean dataSourceCalled = false;
            try {
                TenantContext.setCurrentTenant(partner.tenantId());

                OffsetDateTime now = OffsetDateTime.now();
                Optional<UUID> existingSnapshotId =
                        screeningRepository.findLatestSnapshotId(partner.tenantId(), partner.taxNumber());

                if (existingSnapshotId.isEmpty()) {
                    // No existing snapshot — nothing to refresh. The ingestor only refreshes
                    // existing data, it does not create new snapshots (that's the search flow).
                    log.debug("No existing snapshot for partner tenant={} — skipping",
                            partner.tenantId());
                    processed++;
                    continue;
                }

                UUID snapshotId = existingSnapshotId.get();

                if (isDemoMode) {
                    // Demo mode: update checked_at only — fixture data is static, no real refresh
                    screeningRepository.updateSnapshotCheckedAt(snapshotId, partner.tenantId(), now);
                } else {
                    // Live/test mode: fetch fresh data from adapters
                    dataSourceCalled = true;
                    CompanyData data = dataSourceService.fetchCompanyData(partner.taxNumber());

                    if (allSourcesAvailable(data)) {
                        screeningRepository.updateSnapshotFromIngestor(
                                snapshotId, partner.tenantId(),
                                data.snapshotData(), data.sourceUrls(),
                                data.domFingerprintHash(), now, mode);
                    } else {
                        // Source unavailable — retain existing snapshot, do NOT overwrite
                        errors++;
                        log.warn("Source unavailable during ingestion tax_number={} tenant={}",
                                maskTaxNumber(partner.taxNumber()), partner.tenantId());
                    }
                }

                processed++;
            } catch (Exception e) {
                errors++;
                processed++;
                log.error("Ingestor entry failed tenant={}", partner.tenantId(), e);
            } finally {
                TenantContext.clear();
                if (dataSourceCalled) {
                    sleepIfNeeded();
                }
            }
        }

        healthState.recordRun(processed, errors);

        if (isDemoMode) {
            log.info("Async ingestor completed [demo mode] entries_processed={}", processed);
        } else {
            log.info("Async ingestor completed mode={} entries_processed={} errors={}",
                    mode, processed, errors);
        }
    }

    /**
     * Check if all adapter results in the company data indicate available sources.
     */
    private boolean allSourcesAvailable(CompanyData data) {
        if (data.adapterResults() == null || data.adapterResults().isEmpty()) {
            return false;
        }
        return data.adapterResults().values().stream().allMatch(ScrapedData::available);
    }

    /**
     * Apply configurable inter-request delay between successive data source calls.
     * Only called after actual data source calls in live/test mode — never in demo mode
     * (fixtures are in-memory, no rate limit concern) and never for skipped entries.
     */
    private void sleepIfNeeded() {
        long delayMs = properties.getAsyncIngestor().getDelayBetweenRequestsMs();
        if (delayMs > 0) {
            sleepBetweenRequests(delayMs);
        }
    }

    /**
     * Performs the actual thread sleep for rate limiting. Package-visible for testability
     * (allows spy-based verification without fragile timing assertions).
     *
     * @param delayMs the delay in milliseconds
     */
    void sleepBetweenRequests(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ingestor sleep interrupted");
        }
    }

    /**
     * Mask a tax number for logging — PII-compliant: at most first 4 characters visible.
     * An 8-digit Hungarian tax number becomes "1234****".
     */
    private static String maskTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.length() <= 4) {
            return "****";
        }
        int visibleChars = Math.min(4, taxNumber.length());
        return taxNumber.substring(0, visibleChars) + "****";
    }
}
