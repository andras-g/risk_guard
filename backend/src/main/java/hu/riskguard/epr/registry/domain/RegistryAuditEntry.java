package hu.riskguard.epr.registry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single field-level change record from {@code registry_entry_audit_log}.
 */
public record RegistryAuditEntry(
        UUID id,
        UUID productId,
        UUID tenantId,
        String fieldChanged,
        String oldValue,
        String newValue,
        UUID changedByUserId,
        AuditSource source,
        OffsetDateTime timestamp
) {}
