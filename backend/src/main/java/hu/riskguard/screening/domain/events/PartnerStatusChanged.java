package hu.riskguard.screening.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Placeholder event for Story 2.3+ — published when a verdict status changes
 * (e.g., from INCOMPLETE to RELIABLE after scraper data arrives).
 *
 * <p>PII policy: No tax number or user data included.
 *
 * @param verdictId     the verdict whose status changed
 * @param tenantId      the tenant that owns the verdict
 * @param previousStatus the status before the change
 * @param newStatus      the status after the change
 * @param timestamp      when the change occurred
 */
public record PartnerStatusChanged(
        UUID verdictId,
        UUID tenantId,
        String previousStatus,
        String newStatus,
        OffsetDateTime timestamp
) {
    public static PartnerStatusChanged of(UUID verdictId, UUID tenantId, String previousStatus, String newStatus) {
        return new PartnerStatusChanged(verdictId, tenantId, previousStatus, newStatus, OffsetDateTime.now());
    }
}
