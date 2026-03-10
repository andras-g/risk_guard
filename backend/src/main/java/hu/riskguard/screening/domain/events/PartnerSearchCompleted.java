package hu.riskguard.screening.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published after a partner search completes successfully.
 * Consumers can use this event for notifications, analytics, or downstream processing.
 *
 * <p>PII policy: Tax number is NOT included per zero-tolerance policy.
 * Use {@code snapshotId} to resolve the tax number via the screening module facade if needed.
 *
 * @param snapshotId the created company snapshot ID
 * @param verdictId  the created verdict ID
 * @param tenantId   the tenant that initiated the search
 * @param timestamp  when the search completed
 */
public record PartnerSearchCompleted(
        UUID snapshotId,
        UUID verdictId,
        UUID tenantId,
        OffsetDateTime timestamp
) {
    public static PartnerSearchCompleted of(UUID snapshotId, UUID verdictId, UUID tenantId) {
        return new PartnerSearchCompleted(snapshotId, verdictId, tenantId, OffsetDateTime.now());
    }
}
