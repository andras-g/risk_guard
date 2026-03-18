package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Module facade for the notification module.
 * This is the ONLY public entry point into the notification module's business logic.
 *
 * <p>External modules call facade methods here — never the repository directly.
 * Follows the module facade pattern: Controller → NotificationService → NotificationRepository.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Get all actively monitored partners across all tenants.
     * Used by the background {@code AsyncIngestor} (screening module) to refresh partner data.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants.
     * Only call from background jobs — never from user-facing code paths.
     *
     * @return list of {@code WatchlistPartner} records with tenant ID + tax number
     */
    public List<WatchlistPartner> getMonitoredPartners() {
        return notificationRepository.findAllWatchlistEntries();
    }
}
