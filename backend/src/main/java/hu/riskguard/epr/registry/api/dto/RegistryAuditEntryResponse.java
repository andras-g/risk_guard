package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.AuditSource;
import hu.riskguard.epr.registry.domain.RegistryAuditEntry;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistryAuditEntryResponse(
        UUID id,
        UUID productId,
        String fieldChanged,
        String oldValue,
        String newValue,
        UUID changedByUserId,
        AuditSource source,
        OffsetDateTime timestamp
) {
    public static RegistryAuditEntryResponse from(RegistryAuditEntry entry) {
        return new RegistryAuditEntryResponse(
                entry.id(), entry.productId(), entry.fieldChanged(),
                entry.oldValue(), entry.newValue(), entry.changedByUserId(),
                entry.source(), entry.timestamp()
        );
    }
}
