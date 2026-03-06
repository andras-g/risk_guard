package hu.riskguard.core.security;

import org.jooq.*;
import org.jooq.impl.DefaultVisitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantJooqListener extends DefaultVisitListener implements RecordListener {

    private static final String TENANT_ID_COLUMN = "tenant_id";

    // --- RecordListener (Record Enforcement) ---

    @Override
    public void insertStart(RecordContext context) {
        setTenantId(context.record());
    }

    @Override
    public void updateStart(RecordContext context) {
        setTenantId(context.record());
    }

    private void setTenantId(org.jooq.Record record) {
        Field<?> tenantIdField = record.field(TENANT_ID_COLUMN);
        if (tenantIdField != null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new IllegalStateException("Missing tenant context for insert/update on table with tenant_id field");
            }
            record.set((Field<UUID>) tenantIdField, currentTenant);
        }
    }
}
