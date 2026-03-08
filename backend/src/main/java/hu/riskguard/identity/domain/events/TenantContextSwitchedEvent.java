package hu.riskguard.identity.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a user switches their active tenant context.
 * Other modules can listen to this event for audit logging, cache invalidation, etc.
 *
 * <p>PII policy: Email has been intentionally removed from this event record.
 * Per the architecture's PII zero-tolerance policy, only {@code @LogSafe} types
 * (UUIDs, enums, primitives) are permitted in event records that may be serialized
 * by the Spring Modulith Event Publication Registry or consumed by arbitrary listeners.
 * The {@code userId} uniquely identifies the actor and can be resolved to an email
 * via the identity repository if an audit trail requires it.
 *
 * @param userId            the user who switched context
 * @param previousTenantId  the tenant context being switched FROM
 * @param newTenantId       the tenant context being switched TO
 * @param timestamp         when the switch occurred
 */
public record TenantContextSwitchedEvent(
        UUID userId,
        UUID previousTenantId,
        UUID newTenantId,
        OffsetDateTime timestamp
) {
    public static TenantContextSwitchedEvent of(UUID userId, UUID previousTenantId, UUID newTenantId) {
        return new TenantContextSwitchedEvent(userId, previousTenantId, newTenantId, OffsetDateTime.now());
    }
}
