package hu.riskguard.notification.internal;

import hu.riskguard.notification.domain.WatchlistPartner;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ repository for the notification module.
 * Scoped to: {@code watchlist_entries}, {@code notification_outbox} tables ONLY.
 *
 * <p>Note: Uses raw jOOQ DSL (table/field references) because jOOQ codegen for the
 * {@code watchlist_entries} table is not yet generated. Once the migration runs and
 * codegen is regenerated, these can be replaced with type-safe table references.
 *
 * <p>TODO: Replace raw {@code field("tenant_id")}, {@code field("tax_number")},
 * {@code table("watchlist_entries")} with type-safe jOOQ generated references once
 * jOOQ codegen includes the {@code watchlist_entries} table (after Story 3.6 Watchlist CRUD).
 */
@Repository
public class NotificationRepository {

    private final DSLContext dsl;

    public NotificationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all watchlist entries across ALL tenants — used by the background {@code AsyncIngestor}.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> This method deliberately bypasses
     * {@code TenantContext} because it is called from a background job with no user session.
     * It reads every tenant's watchlist entries to refresh partner data proactively.
     *
     * <p>Only call this from the scheduled {@code AsyncIngestor} — never from user-facing code.
     *
     * @return all watchlist entries as {@code WatchlistPartner} records (tenantId + taxNumber)
     */
    public List<WatchlistPartner> findAllWatchlistEntries() {
        return dsl.select(
                        field("tenant_id", java.util.UUID.class),
                        field("tax_number", String.class))
                .from(table("watchlist_entries"))
                .fetchInto(WatchlistPartner.class);
    }
}
