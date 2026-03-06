package hu.riskguard.core.repository;

import hu.riskguard.core.security.TenantContext;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.UUID;

public abstract class BaseRepository {

    protected final DSLContext dsl;

    protected BaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    protected <R extends org.jooq.Record> SelectConditionStep<R> selectFromTenant(Table<R> table) {
        Field<UUID> tenantIdField = (Field<UUID>) table.field("tenant_id");
        if (tenantIdField == null) {
            return dsl.selectFrom(table).where(DSL.noCondition());
        }
        
        UUID currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new IllegalStateException("No tenant context found for tenant-scoped query");
        }
        
        return dsl.selectFrom(table).where(tenantIdField.eq(currentTenant));
    }
    
    // Additional helpers for join, etc.
}
