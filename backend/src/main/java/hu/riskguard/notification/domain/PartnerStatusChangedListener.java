package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.core.events.PartnerStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listens for {@link PartnerStatusChanged} application events and updates the denormalized
 * verdict status on matching {@code watchlist_entries} rows.
 *
 * <p>This listener bridges the screening module (event publisher) and the notification module
 * (watchlist data owner). It ensures that user-initiated searches also update watchlist entries
 * reactively, not just the background WatchlistMonitor cycle.
 *
 * <p>Uses {@code @EventListener} (not {@code @TransactionalEventListener}) because the event
 * is published OUTSIDE a transaction boundary — both from {@code ScreeningService.search()}
 * (after TX2 commit) and from {@code WatchlistMonitor} (non-transactional loop).
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

    public PartnerStatusChangedListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
            notificationRepository.updateVerdictStatus(
                    entry.tenantId(), entry.taxNumber(),
                    event.newStatus(), event.timestamp());
        }

        log.debug("PartnerStatusChanged: updated {} watchlist entries with new status={}",
                watchlistEntries.size(), event.newStatus());
    }
}
