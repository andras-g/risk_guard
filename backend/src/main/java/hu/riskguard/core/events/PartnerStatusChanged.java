package hu.riskguard.core.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Application event published when a verdict status changes for a partner
 * (e.g., from RELIABLE to AT_RISK after fresh data reveals new risk signals).
 *
 * <p>Published by:
 * <ul>
 *   <li>{@link hu.riskguard.screening.domain.ScreeningService#search} — after user-initiated search detects a change</li>
 *   <li>{@link hu.riskguard.screening.domain.WatchlistMonitor} — during 24h background monitoring cycle</li>
 * </ul>
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@link hu.riskguard.notification.domain.PartnerStatusChangedListener} — updates watchlist entries reactively</li>
 * </ul>
 *
 * <p><b>PII note:</b> The {@code taxNumber} field is included for watchlist entry lookup.
 * It must NEVER be logged directly — use masking (e.g., "1234****") in all log statements.
 *
 * @param verdictId      the verdict whose status changed
 * @param tenantId       the tenant that owns the verdict
 * @param taxNumber      the tax number of the partner (needed for watchlist entry lookup)
 * @param previousStatus the status before the change (may be null for first-time evaluations)
 * @param newStatus      the status after the change
 * @param sha256Hash     the SHA-256 audit hash from search_audit_log (for email notification AC4); may be null
 * @param timestamp      when the change occurred
 */
public record PartnerStatusChanged(
        UUID verdictId,
        UUID tenantId,
        String taxNumber,
        String previousStatus,
        String newStatus,
        String sha256Hash,
        OffsetDateTime timestamp
) {
    public static PartnerStatusChanged of(UUID verdictId, UUID tenantId, String taxNumber,
                                          String previousStatus, String newStatus, String sha256Hash) {
        return new PartnerStatusChanged(verdictId, tenantId, taxNumber, previousStatus, newStatus, sha256Hash, OffsetDateTime.now());
    }


}
