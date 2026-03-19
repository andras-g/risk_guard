package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.WatchlistEntryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Module facade for the notification module.
 * This is the ONLY public entry point into the notification module's business logic.
 *
 * <p>External modules call facade methods here — never the repository directly.
 * Follows the module facade pattern: Controller → NotificationService → NotificationRepository.
 *
 * <p>Called by:
 * <ul>
 *   <li>{@code WatchlistController} — tenant-scoped CRUD (add, list, remove, count)</li>
 *   <li>{@code AsyncIngestor} (screening module) — cross-tenant partner list for data refresh</li>
 *   <li>{@code WatchlistMonitor} (screening module) — cross-tenant partner list with verdicts, verdict updates</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Get all actively monitored partners across all tenants.
     * Used by the background {@code AsyncIngestor} (screening module) to refresh partner data.
     *
     * <p><b>PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants.
     * Only call from background jobs — never from user-facing code paths.
     *
     * @return list of {@code WatchlistPartner} records with tenant ID + tax number
     */
    public List<WatchlistPartner> getMonitoredPartners() {
        return notificationRepository.findAllWatchlistEntries();
    }

    /**
     * Get all actively monitored partners with their last known verdict status.
     * Used by the background {@code WatchlistMonitor} to compare old vs new verdict.
     *
     * <p><b>PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants
     * including denormalized verdict status for change detection.
     * Only call from the WatchlistMonitor — never from user-facing code paths.
     *
     * @return list of {@code MonitoredPartner} records with tenant ID, tax number, and last verdict status
     */
    public List<MonitoredPartner> getMonitoredPartnersWithVerdicts() {
        return notificationRepository.findAllMonitoredPartners();
    }

    // --- Tenant-Scoped CRUD (Story 3.6) ---

    /**
     * Add a partner to the tenant's watchlist.
     * Prevents duplicates — returns existing entry if tax number already on watchlist.
     *
     * @param tenantId      current tenant from JWT
     * @param taxNumber     Hungarian tax number (normalized)
     * @param companyName   company name from screening (may be null)
     * @param verdictStatus current verdict status from screening (may be null)
     * @return the watchlist entry (new or existing if duplicate), and whether it was a duplicate
     */
    @Transactional
    public AddResult addToWatchlist(UUID tenantId, String taxNumber, String companyName, String verdictStatus) {
        String normalizedTaxNumber = taxNumber.replaceAll("[\\s-]", "");

        // Duplicate check
        Optional<WatchlistEntryRecord> existing =
                notificationRepository.findByTenantIdAndTaxNumber(tenantId, normalizedTaxNumber);
        if (existing.isPresent()) {
            log.info("Watchlist duplicate prevented for tax_number in tenant");
            return new AddResult(toDomain(existing.get()), true);
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        notificationRepository.insertEntry(id, tenantId, normalizedTaxNumber, companyName, null);

        // Populate denormalized verdict columns immediately so the UI shows
        // verdict status without waiting for the next WatchlistMonitor cycle.
        if (verdictStatus != null && !verdictStatus.isBlank()) {
            notificationRepository.updateVerdictStatus(tenantId, normalizedTaxNumber, verdictStatus, now);
        }

        WatchlistEntry inserted = new WatchlistEntry(
                id, tenantId, normalizedTaxNumber, companyName, null, now, now, verdictStatus, now);

        return new AddResult(inserted, false);
    }

    /**
     * Get all watchlist entries for a tenant.
     *
     * @param tenantId current tenant from JWT
     * @return domain watchlist entries
     */
    @Transactional(readOnly = true)
    public List<WatchlistEntry> getWatchlistEntries(UUID tenantId) {
        return notificationRepository.findByTenantId(tenantId).stream()
                .map(NotificationService::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Remove a partner from the tenant's watchlist.
     * Verifies tenant ownership — returns false if entry not found or not owned.
     *
     * @param tenantId current tenant from JWT
     * @param entryId  watchlist entry UUID to remove
     * @return true if deleted, false if not found (returns 404 to avoid info leakage)
     */
    @Transactional
    public boolean removeFromWatchlist(UUID tenantId, UUID entryId) {
        int deleted = notificationRepository.deleteByIdAndTenantId(entryId, tenantId);
        return deleted > 0;
    }

    /**
     * Get watchlist entry count for a tenant — used by sidebar badge.
     *
     * @param tenantId current tenant from JWT
     * @return entry count
     */
    @Transactional(readOnly = true)
    public int getWatchlistCount(UUID tenantId) {
        return notificationRepository.countByTenantId(tenantId);
    }

    /**
     * Update the denormalized verdict status and last_checked_at on a watchlist entry.
     * Used by the WatchlistMonitor (screening module) after verdict re-evaluation.
     *
     * @param tenantId      tenant owning the entry
     * @param taxNumber     the tax number to update
     * @param verdictStatus new verdict status
     * @param checkedAt     timestamp of the evaluation
     * @return number of rows updated (0 = no matching entry)
     */
    public int updateVerdictStatus(UUID tenantId, String taxNumber, String verdictStatus, OffsetDateTime checkedAt) {
        return notificationRepository.updateVerdictStatus(tenantId, taxNumber, verdictStatus, checkedAt);
    }

    /**
     * Update only the last_checked_at timestamp without changing verdict status.
     * Used by WatchlistMonitor when a transient failure occurs or no verdict exists.
     *
     * @param tenantId  tenant owning the entry
     * @param taxNumber the tax number to update
     * @param checkedAt timestamp of the monitoring attempt
     * @return number of rows updated
     */
    public int updateCheckedAt(UUID tenantId, String taxNumber, OffsetDateTime checkedAt) {
        return notificationRepository.updateCheckedAt(tenantId, taxNumber, checkedAt);
    }

    /**
     * Map internal repository record to public domain type.
     */
    private static WatchlistEntry toDomain(WatchlistEntryRecord rec) {
        return new WatchlistEntry(
                rec.id(), rec.tenantId(), rec.taxNumber(), rec.companyName(), rec.label(),
                rec.createdAt(), rec.updatedAt(), rec.verdictStatus(), rec.lastCheckedAt());
    }

    /**
     * Result of adding a watchlist entry.
     * @param entry     the domain entry (new or existing)
     * @param duplicate true if entry already existed (no insert performed)
     */
    public record AddResult(WatchlistEntry entry, boolean duplicate) {}
}
