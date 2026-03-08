package hu.riskguard.identity.domain.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Consumes {@link TenantContextSwitchedEvent} and logs the tenant switch for audit purposes.
 *
 * <p>Uses {@link TransactionalEventListener} with {@link TransactionPhase#BEFORE_COMMIT} so the
 * listener fires WITHIN the calling transaction before it commits. If this listener throws, the
 * entire transaction rolls back — guaranteeing that event failure aborts the tenant switch with
 * no silent audit loss.
 *
 * <p>A future story may persist audit events to a dedicated {@code tenant_audit_log} table if
 * regulatory requirements demand it.
 *
 * <p>Note: Only {@link org.springframework.context.annotation.Conditional @LogSafe}-safe fields
 * are logged (userId, tenantIds). Email is NOT logged per PII zero-tolerance policy.
 */
@Slf4j
@Component
public class TenantContextSwitchedEventListener {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTenantContextSwitched(TenantContextSwitchedEvent event) {
        // Log audit-safe fields only — email is PII and must NOT appear in logs.
        log.info("AUDIT: Tenant context switched — userId={}, from={}, to={}, timestamp={}",
                event.userId(), event.previousTenantId(), event.newTenantId(), event.timestamp());
    }
}
