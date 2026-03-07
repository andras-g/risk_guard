package hu.riskguard.core.security;

import org.jooq.*;
import org.jooq.impl.DefaultVisitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantJooqListener extends DefaultVisitListener implements RecordListener {

    private static final Logger log = LoggerFactory.getLogger(TenantJooqListener.class);
    private static final String TENANT_ID_COLUMN = "tenant_id";

    /**
     * Re-entry guard to prevent infinite recursion in visitEnd().
     * query.getSQL() triggers rendering → triggers VisitListener → infinite loop.
     */
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // --- RecordListener (Single Record Enforcement) ---

    @Override
    public void insertStart(RecordContext context) {
        setTenantId(context.record());
    }

    @Override
    public void updateStart(RecordContext context) {
        setTenantId(context.record());
    }

    @SuppressWarnings("unchecked")
    private void setTenantId(org.jooq.Record record) {
        Field<?> tenantIdField = record.field(TENANT_ID_COLUMN);
        if (tenantIdField != null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new IllegalStateException("CRITICAL: Missing tenant context for record operation on table with tenant_id");
            }
            record.set((Field<UUID>) tenantIdField, currentTenant);
        }
    }

    // --- VisitListener (Query Verification) ---
    // Acts as a secondary guard: verifies that rendered SQL for tenant-aware tables
    // actually contains a tenant_id binding. Logs a warning if it appears missing.

    @Override
    public void visitEnd(VisitContext context) {
        // Prevent infinite recursion: toString()/getSQL() triggers rendering which triggers visitEnd()
        if (RENDERING.get()) {
            return;
        }

        QueryPart part = context.queryPart();

        if (part instanceof Query query) {
            try {
                RENDERING.set(Boolean.TRUE);
                String sql = query.getSQL();
                if (isTenantAwareQuery(sql)) {
                    if (!containsTenantCondition(sql)) {
                        log.warn("SECURITY: Potential tenant isolation bypass detected. "
                                + "Query references tenant-aware table but may be missing tenant_id condition. SQL: {}",
                                sql.substring(0, Math.min(sql.length(), 200)));
                    }
                }
            } finally {
                RENDERING.set(Boolean.FALSE);
            }
        }
    }

    /**
     * Checks if the SQL references any known tenant-aware tables.
     */
    private boolean isTenantAwareQuery(String sql) {
        String sqlLower = sql.toLowerCase();
        return sqlLower.contains("\"users\"")
            || sqlLower.contains("\"tenant_mandates\"")
            || sqlLower.contains("\"guest_sessions\"");
    }

    /**
     * Simple heuristic check: does the SQL contain a tenant_id condition?
     */
    private boolean containsTenantCondition(String sql) {
        String sqlLower = sql.toLowerCase();
        return sqlLower.contains("tenant_id");
    }
}
