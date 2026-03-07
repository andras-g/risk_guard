package hu.riskguard.core.repository;

import hu.riskguard.core.security.TenantContext;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SelectWhereStep;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.UUID;

public abstract class BaseRepository {

    protected final DSLContext dsl;

    protected BaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Select from a tenant-aware table — automatically filtered by TenantAwareDSLContext.
     */
    protected <R extends org.jooq.Record> SelectWhereStep<R> selectFromTenant(Table<R> table) {
        return dsl.selectFrom(table);
    }

    /**
     * Returns a tenant_id = currentTenant condition for explicit use in update/delete queries.
     * Use this when building update().set().where() or deleteFrom().where() on tenant-aware tables.
     */
    protected Condition tenantCondition(Field<UUID> tenantIdField) {
        UUID currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new IllegalStateException("CRITICAL: Missing tenant context for tenant-scoped operation");
        }
        return tenantIdField.eq(currentTenant);
    }
}
