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

    protected <R extends org.jooq.Record> SelectWhereStep<R> selectFromTenant(Table<R> table) {
        // Automatically filtered by TenantAwareDSLContext
        return dsl.selectFrom(table);
    }
    
    // Additional helpers for join, etc.
}
