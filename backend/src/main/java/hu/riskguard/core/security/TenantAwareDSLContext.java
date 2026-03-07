package hu.riskguard.core.security;

import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.Field;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SelectSelectStep;
import org.jooq.SelectWhereStep;
import org.jooq.Table;
import org.jooq.TableLike;
import org.jooq.impl.DefaultDSLContext;

import java.util.UUID;

/**
 * A tenant-aware DSLContext that overrides common entry points to ensure tenant isolation.
 * <p>
 * SELECT queries via selectFrom() are automatically filtered by tenant_id.
 * UPDATE and DELETE tenant isolation is enforced by {@link TenantJooqListener} which
 * stamps tenant_id on record operations and logs warnings for queries that may bypass filtering.
 * <p>
 * For queries using select().from() (e.g., joins, projections), developers must explicitly
 * add tenant_id conditions. The VisitListener acts as a secondary verification gate.
 */
public class TenantAwareDSLContext extends DefaultDSLContext {

    public TenantAwareDSLContext(Configuration configuration) {
        super(configuration);
    }

    // --- SELECT ---

    @Override
    public <R extends org.jooq.Record> SelectWhereStep<R> selectFrom(TableLike<R> table) {
        SelectWhereStep<R> step = super.selectFrom(table);
        Field<UUID> tenantIdField = getTenantIdField(table);
        if (tenantIdField != null) {
            @SuppressWarnings("unchecked")
            SelectWhereStep<R> filtered = (SelectWhereStep<R>) step.where(tenantIdField.eq(getCurrentTenantId(table)));
            return filtered;
        }
        return step;
    }

    @Override
    public SelectSelectStep<org.jooq.Record> select(SelectFieldOrAsterisk... fields) {
        // select().from() queries are NOT auto-filtered — developers must add tenant_id
        // conditions explicitly. TenantJooqListener.visitEnd() logs warnings for queries
        // that reference tenant-aware tables without tenant_id conditions.
        return super.select(fields);
    }

    // --- FETCH COUNT ---

    @Override
    public int fetchCount(Table<?> table) {
        Field<UUID> tenantIdField = getTenantIdField(table);
        if (tenantIdField != null) {
            return super.fetchCount(table, tenantIdField.eq(getCurrentTenantId(table)));
        }
        return super.fetchCount(table);
    }

    @Override
    public int fetchCount(Table<?> table, Condition... conditions) {
        Field<UUID> tenantIdField = getTenantIdField(table);
        if (tenantIdField != null) {
            Condition[] newConditions = new Condition[conditions.length + 1];
            System.arraycopy(conditions, 0, newConditions, 0, conditions.length);
            newConditions[conditions.length] = tenantIdField.eq(getCurrentTenantId(table));
            return super.fetchCount(table, newConditions);
        }
        return super.fetchCount(table, conditions);
    }

    // --- HELPERS ---

    @SuppressWarnings("unchecked")
    private Field<UUID> getTenantIdField(TableLike<?> table) {
        if (table instanceof Table<?> t) {
            return (Field<UUID>) t.field("tenant_id");
        }
        return null;
    }

    private UUID getCurrentTenantId(TableLike<?> table) {
        UUID currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new IllegalStateException("CRITICAL: Missing tenant context for tenant-aware table: " + table);
        }
        return currentTenant;
    }
}
