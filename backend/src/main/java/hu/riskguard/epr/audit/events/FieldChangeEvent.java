package hu.riskguard.epr.audit.events;

import hu.riskguard.epr.audit.AuditSource;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable event describing a single field-level change that must land in
 * {@code registry_entry_audit_log}. Passing these fields via a record (rather
 * than a 9-positional-argument method) is a correctness safeguard: the three
 * {@link UUID} and three {@link String} parameters are positionally ambiguous,
 * and a silent caller-side swap of {@link #oldValue} / {@link #newValue} or
 * {@link #productId} / {@link #tenantId} would be type-safe but semantically wrong.
 *
 * <p>Compact constructor enforces facade invariants (non-null identifiers, non-blank
 * field name, non-null source) so the entire audit path — including
 * {@code AuditService} — can assume a valid event.
 *
 * @param productId             target product (non-null)
 * @param tenantId              tenant owning the product (non-null)
 * @param fieldChanged          registry-column name or {@code "CREATE.*"} / {@code "components[uuid].*"} prefix (non-blank)
 * @param oldValue              prior value, nullable (CREATE rows)
 * @param newValue              new value, nullable (DELETE / field-removal rows)
 * @param changedByUserId       acting user, nullable (NAV bootstrap jobs may have no user)
 * @param source                classification of the write (non-null)
 * @param classificationStrategy AI strategy identifier, only populated for {@code AI_SUGGESTED_*} sources
 * @param modelVersion          AI model version, only populated for {@code AI_SUGGESTED_*} sources
 */
public record FieldChangeEvent(
        UUID productId,
        UUID tenantId,
        String fieldChanged,
        String oldValue,
        String newValue,
        UUID changedByUserId,
        AuditSource source,
        String classificationStrategy,
        String modelVersion
) {
    public FieldChangeEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (fieldChanged == null || fieldChanged.isBlank()) {
            throw new IllegalArgumentException("fieldChanged must not be blank");
        }
    }
}
