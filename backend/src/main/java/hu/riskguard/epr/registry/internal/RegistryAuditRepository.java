package hu.riskguard.epr.registry.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.registry.domain.AuditSource;
import hu.riskguard.epr.registry.domain.RegistryAuditEntry;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.REGISTRY_ENTRY_AUDIT_LOG;

/**
 * jOOQ repository for audit log inserts and reads.
 * Owns {@code registry_entry_audit_log} table.
 */
@Repository
public class RegistryAuditRepository extends BaseRepository {

    public RegistryAuditRepository(DSLContext dsl) {
        super(dsl);
    }

    public void insertAuditRow(UUID productId, UUID tenantId, String fieldChanged,
                                String oldValue, String newValue,
                                UUID changedByUserId, AuditSource source) {
        dsl.insertInto(REGISTRY_ENTRY_AUDIT_LOG)
                .set(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID, productId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID, tenantId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.FIELD_CHANGED, fieldChanged)
                .set(REGISTRY_ENTRY_AUDIT_LOG.OLD_VALUE, oldValue)
                .set(REGISTRY_ENTRY_AUDIT_LOG.NEW_VALUE, newValue)
                .set(REGISTRY_ENTRY_AUDIT_LOG.CHANGED_BY_USER_ID, changedByUserId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.SOURCE, source.name())
                .execute();
    }

    public List<RegistryAuditEntry> listAuditByProduct(UUID productId, UUID tenantId,
                                                         int page, int size) {
        return dsl.select(REGISTRY_ENTRY_AUDIT_LOG.asterisk())
                .from(REGISTRY_ENTRY_AUDIT_LOG)
                .where(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID.eq(productId))
                .and(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID.eq(tenantId))
                .orderBy(REGISTRY_ENTRY_AUDIT_LOG.TIMESTAMP.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(r -> new RegistryAuditEntry(
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.ID),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.FIELD_CHANGED),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.OLD_VALUE),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.NEW_VALUE),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.CHANGED_BY_USER_ID),
                        AuditSource.valueOf(r.get(REGISTRY_ENTRY_AUDIT_LOG.SOURCE)),
                        r.get(REGISTRY_ENTRY_AUDIT_LOG.TIMESTAMP)
                ));
    }

    public long countAuditByProduct(UUID productId, UUID tenantId) {
        return dsl.selectCount()
                .from(REGISTRY_ENTRY_AUDIT_LOG)
                .where(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID.eq(productId))
                .and(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID.eq(tenantId))
                .fetchOne(0, Long.class);
    }
}
