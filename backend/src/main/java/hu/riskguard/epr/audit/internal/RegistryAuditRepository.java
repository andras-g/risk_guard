package hu.riskguard.epr.audit.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.audit.RegistryAuditEntry;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.REGISTRY_ENTRY_AUDIT_LOG;

/**
 * jOOQ repository for {@code registry_entry_audit_log} inserts and reads.
 *
 * <p>Accessed exclusively through {@link hu.riskguard.epr.audit.AuditService};
 * direct dependencies from outside the audit package are forbidden by
 * {@code EpicTenInvariantsTest.only_audit_package_writes_to_audit_tables}.
 */
@Repository
public class RegistryAuditRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(RegistryAuditRepository.class);

    public RegistryAuditRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Insert an audit row with no AI provenance (backward-compatible overload).
     */
    public void insertAuditRow(UUID productId, UUID tenantId, String fieldChanged,
                                String oldValue, String newValue,
                                UUID changedByUserId, AuditSource source) {
        insertAuditRow(productId, tenantId, fieldChanged, oldValue, newValue, changedByUserId, source, null, null);
    }

    /**
     * Insert an audit row with optional AI classification provenance (Story 9.3).
     * {@code strategy} and {@code modelVersion} are non-null only for AI_SUGGESTED_* sources.
     */
    public void insertAuditRow(UUID productId, UUID tenantId, String fieldChanged,
                                String oldValue, String newValue,
                                UUID changedByUserId, AuditSource source,
                                String strategy, String modelVersion) {
        dsl.insertInto(REGISTRY_ENTRY_AUDIT_LOG)
                .set(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID, productId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID, tenantId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.FIELD_CHANGED, fieldChanged)
                .set(REGISTRY_ENTRY_AUDIT_LOG.OLD_VALUE, oldValue)
                .set(REGISTRY_ENTRY_AUDIT_LOG.NEW_VALUE, newValue)
                .set(REGISTRY_ENTRY_AUDIT_LOG.CHANGED_BY_USER_ID, changedByUserId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.SOURCE, source.name())
                .set(REGISTRY_ENTRY_AUDIT_LOG.STRATEGY, strategy)
                .set(REGISTRY_ENTRY_AUDIT_LOG.MODEL_VERSION, modelVersion)
                .execute();
    }

    public List<RegistryAuditEntry> listAuditByProduct(UUID productId, UUID tenantId,
                                                         int page, int size) {
        return dsl.select(REGISTRY_ENTRY_AUDIT_LOG.asterisk())
                .from(REGISTRY_ENTRY_AUDIT_LOG)
                .where(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID.eq(productId))
                .and(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID.eq(tenantId))
                .orderBy(REGISTRY_ENTRY_AUDIT_LOG.TIMESTAMP.desc(), REGISTRY_ENTRY_AUDIT_LOG.ID.desc())
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
                        parseSourceSafely(r.get(REGISTRY_ENTRY_AUDIT_LOG.SOURCE)),
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

    /**
     * Map a persisted {@code source} value to its enum, tolerating unknowns so a row
     * written under a since-removed enum constant does not break audit-log pagination.
     * Audit trails must stay forward-compatible with their own history.
     */
    static AuditSource parseSourceSafely(String raw) {
        if (raw == null) {
            return AuditSource.UNKNOWN;
        }
        try {
            return AuditSource.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown AuditSource '{}' in registry_entry_audit_log; mapping to UNKNOWN", raw);
            return AuditSource.UNKNOWN;
        }
    }
}
