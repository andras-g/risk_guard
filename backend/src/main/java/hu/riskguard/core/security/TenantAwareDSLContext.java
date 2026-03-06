package hu.riskguard.core.security;

import org.jooq.*;
import org.jooq.impl.DefaultDSLContext;

import java.util.UUID;

public class TenantAwareDSLContext extends DefaultDSLContext {

    public TenantAwareDSLContext(Configuration configuration) {
        super(configuration);
    }

    @Override
    public <R extends org.jooq.Record> SelectWhereStep<R> selectFrom(TableLike<R> table) {
        SelectWhereStep<R> step = super.selectFrom(table);
        if (table instanceof Table<?> t) {
            Field<UUID> tenantIdField = (Field<UUID>) t.field("tenant_id");
            if (tenantIdField != null) {
                UUID currentTenant = TenantContext.getCurrentTenant();
                if (currentTenant == null) {
                    throw new IllegalStateException("Missing tenant context for tenant-aware table: " + t.getName());
                }
                return (SelectWhereStep<R>) step.where(tenantIdField.eq(currentTenant));
            }
        }
        return step;
    }

    @Override
    public int fetchCount(Table<?> table) {
        Field<UUID> tenantIdField = (Field<UUID>) table.field("tenant_id");
        if (tenantIdField != null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new IllegalStateException("Missing tenant context for tenant-aware table: " + table.getName());
            }
            return super.fetchCount(table, tenantIdField.eq(currentTenant));
        }
        return super.fetchCount(table);
    }

    @Override
    public int fetchCount(Table<?> table, Condition... conditions) {
        Field<UUID> tenantIdField = (Field<UUID>) table.field("tenant_id");
        if (tenantIdField != null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new IllegalStateException("Missing tenant context for tenant-aware table: " + table.getName());
            }
            Condition[] newConditions = new Condition[conditions.length + 1];
            System.arraycopy(conditions, 0, newConditions, 0, conditions.length);
            newConditions[conditions.length] = tenantIdField.eq(currentTenant);
            return super.fetchCount(table, newConditions);
        }
        return super.fetchCount(table, conditions);
    }
}
