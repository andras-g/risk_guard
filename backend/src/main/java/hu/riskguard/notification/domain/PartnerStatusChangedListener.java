package hu.riskguard.notification.domain;

import hu.riskguard.core.events.PartnerStatusChanged;
import hu.riskguard.core.util.PiiUtil;
import hu.riskguard.notification.internal.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for {@link PartnerStatusChanged} application events and updates the denormalized
 * verdict status on matching {@code watchlist_entries} rows.
 *
 * <p>This listener bridges the screening module (event publisher) and the notification module
 * (watchlist data owner). It ensures that user-initiated searches also update watchlist entries
 * reactively, not just the background WatchlistMonitor cycle.
 *
 * <p>Uses {@code @ApplicationModuleListener}, which Spring Modulith implements as an async
 * transactional listener with {@code Propagation.REQUIRES_NEW}. The handler therefore runs
 * on a separate thread and inside its own transaction, independent of the publisher's
 * transactional state. Both publishers — {@code ScreeningService.search()} (after TX2 commit)
 * and {@code WatchlistMonitor} (non-transactional loop) — publish outside an active
 * transaction, and this listener does not depend on that.
 *
 * <p><b>Cross-tenant:</b> A single tax number may appear on multiple tenants' watchlists.
 * This listener finds ALL matching entries across tenants and updates each one.
 * If the tax number is not on any watchlist, the event is silently ignored (no error).
 *
 * @see PartnerStatusChanged
 */
@Component
public class PartnerStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(PartnerStatusChangedListener.class);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public PartnerStatusChangedListener(NotificationRepository notificationRepository,
                                         NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    /**
     * Handle a partner status change event by updating watchlist entries with the new verdict.
     *
     * <p>Finds all watchlist entries matching the tax number (cross-tenant) and updates
     * each one with the new verdict status and timestamp. If the tax number is not on
     * any watchlist, the event is silently ignored.
     *
     * @param event the status change event
     */
    @ApplicationModuleListener
    public void onPartnerStatusChanged(PartnerStatusChanged event) {
        if (event.taxNumber() == null) {
            log.debug("PartnerStatusChanged received with null taxNumber — ignoring");
            return;
        }

        List<WatchlistPartner> watchlistEntries =
                notificationRepository.findWatchlistEntriesByTaxNumber(event.taxNumber());

        if (watchlistEntries.isEmpty()) {
            // Tax number is not on any watchlist — silently ignore per AC4
            log.debug("PartnerStatusChanged for non-watchlisted tax number — ignoring");
            return;
        }

        for (WatchlistPartner entry : watchlistEntries) {
            notificationRepository.updateVerdictStatusWithHash(
                    entry.tenantId(), entry.taxNumber(),
                    event.newStatus(), event.timestamp(), event.sha256Hash());
        }

        log.debug("PartnerStatusChanged: updated {} watchlist entries with new status={}",
                watchlistEntries.size(), event.newStatus());

        // Story 3.8: Create outbox records for email notification if status actually changed
        if (event.previousStatus() != null && !event.previousStatus().equals(event.newStatus())) {
            createOutboxRecords(event, watchlistEntries);
        }
    }

    /**
     * Create outbox records for each affected tenant's watchlist entry.
     * One outbox record per tenant (a tax number may be on multiple tenants' watchlists).
     * Delegates to NotificationService for digest-mode gating.
     */
    private void createOutboxRecords(PartnerStatusChanged event, List<WatchlistPartner> watchlistEntries) {
        // Look up user_id for each watchlist entry so the OutboxProcessor can resolve their email
        List<NotificationRepository.WatchlistEntryWithUser> entriesWithUsers =
                notificationRepository.findWatchlistEntriesWithUserByTaxNumber(event.taxNumber());

        for (NotificationRepository.WatchlistEntryWithUser entry : entriesWithUsers) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", entry.tenantId().toString());
            payload.put("taxNumber", event.taxNumber());
            payload.put("companyName", entry.companyName());
            payload.put("previousStatus", event.previousStatus());
            payload.put("newStatus", event.newStatus());
            payload.put("verdictId", event.verdictId() != null ? event.verdictId().toString() : null);
            payload.put("changedAt", event.timestamp() != null ? event.timestamp().toString() : null);
            payload.put("sha256Hash", event.sha256Hash());

            notificationService.createAlertNotification(entry.tenantId(), entry.userId(), payload);
        }

        log.debug("PartnerStatusChanged: created outbox records for {} watchlist entries, tax_number={}",
                entriesWithUsers.size(), PiiUtil.maskTaxNumber(event.taxNumber()));
    }
}
