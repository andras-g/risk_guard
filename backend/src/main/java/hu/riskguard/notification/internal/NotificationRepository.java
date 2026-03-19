package hu.riskguard.notification.internal;

import hu.riskguard.notification.domain.MonitoredPartner;
import hu.riskguard.notification.domain.WatchlistPartner;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

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

    private static final Logger log = LoggerFactory.getLogger(NotificationRepository.class);

    private final DSLContext dsl;

    public NotificationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all watchlist entries across ALL tenants — used by the background {@code AsyncIngestor}
     * and {@code WatchlistMonitor}.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> This method deliberately bypasses
     * {@code TenantContext} because it is called from a background job with no user session.
     * It reads every tenant's watchlist entries to refresh partner data proactively.
     *
     * <p>Only call this from the scheduled background jobs — never from user-facing code.
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

    /**
     * Find all watchlist entries across ALL tenants with last verdict status.
     * Used by the background {@code WatchlistMonitor} to compare old vs new verdict.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants.
     * Only call from the scheduled WatchlistMonitor — never from user-facing code.
     *
     * @return all watchlist entries as {@code MonitoredPartner} records (tenantId + taxNumber + lastVerdictStatus)
     */
    public List<MonitoredPartner> findAllMonitoredPartners() {
        return dsl.select(
                        field("tenant_id", java.util.UUID.class),
                        field("tax_number", String.class),
                        field("last_verdict_status", String.class))
                .from(table("watchlist_entries"))
                .fetch(r -> new MonitoredPartner(
                        r.get(field("tenant_id", java.util.UUID.class)),
                        r.get(field("tax_number", String.class)),
                        r.get(field("last_verdict_status", String.class))));
    }

    // ─── Tenant-Scoped CRUD (Story 3.6) ─────────────────────────────────────

    /**
     * Find all watchlist entries for a specific tenant, with denormalized verdict data.
     *
     * <p>Reads {@code last_verdict_status} and {@code last_checked_at} directly from the
     * {@code watchlist_entries} table columns (populated by WatchlistMonitor and
     * PartnerStatusChangedListener in Story 3.7).
     *
     * <p>This replaces the previous {@code LEFT JOIN LATERAL} on {@code verdicts}/
     * {@code company_snapshots} tables (acknowledged tech debt from Story 3.6).
     * The denormalized columns eliminate the cross-module SQL dependency.
     *
     * @param tenantId the tenant to scope the query to
     * @return list of watchlist entry records with verdict data, ordered by created_at descending
     */
    public List<WatchlistEntryRecord> findByTenantId(UUID tenantId) {
        return dsl.select(
                        field("id", UUID.class),
                        field("tenant_id", UUID.class),
                        field("tax_number", String.class),
                        field("company_name", String.class),
                        field("label", String.class),
                        field("created_at", OffsetDateTime.class),
                        field("updated_at", OffsetDateTime.class),
                        field("last_verdict_status", String.class),
                        field("last_checked_at", OffsetDateTime.class))
                .from(table("watchlist_entries"))
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .orderBy(field("created_at", OffsetDateTime.class).desc())
                .fetch(r -> new WatchlistEntryRecord(
                        r.get(field("id", UUID.class)),
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("tax_number", String.class)),
                        r.get(field("company_name", String.class)),
                        r.get(field("label", String.class)),
                        r.get(field("created_at", OffsetDateTime.class)),
                        r.get(field("updated_at", OffsetDateTime.class)),
                        r.get(field("last_verdict_status", String.class)),
                        r.get(field("last_checked_at", OffsetDateTime.class))));
    }

    /**
     * Find a watchlist entry by tenant and tax number — used for duplicate prevention.
     *
     * @param tenantId  the tenant to scope the query to
     * @param taxNumber the tax number to check
     * @return the existing entry if found
     */
    public Optional<WatchlistEntryRecord> findByTenantIdAndTaxNumber(UUID tenantId, String taxNumber) {
        return dsl.select(
                        field("id", UUID.class),
                        field("tenant_id", UUID.class),
                        field("tax_number", String.class),
                        field("company_name", String.class),
                        field("label", String.class),
                        field("created_at", OffsetDateTime.class),
                        field("updated_at", OffsetDateTime.class),
                        field("last_verdict_status", String.class),
                        field("last_checked_at", OffsetDateTime.class))
                .from(table("watchlist_entries"))
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .and(field("tax_number", String.class).eq(taxNumber))
                .fetchOptional(r -> new WatchlistEntryRecord(
                        r.get(field("id", UUID.class)),
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("tax_number", String.class)),
                        r.get(field("company_name", String.class)),
                        r.get(field("label", String.class)),
                        r.get(field("created_at", OffsetDateTime.class)),
                        r.get(field("updated_at", OffsetDateTime.class)),
                        r.get(field("last_verdict_status", String.class)),
                        r.get(field("last_checked_at", OffsetDateTime.class))));
    }

    /**
     * Insert a new watchlist entry.
     *
     * @param id          entry UUID
     * @param tenantId    owning tenant
     * @param taxNumber   Hungarian tax number
     * @param companyName company name at time of add (denormalized)
     * @param label       optional user-defined label
     */
    public void insertEntry(UUID id, UUID tenantId, String taxNumber, String companyName, String label) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("watchlist_entries"))
                .set(field("id", UUID.class), id)
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("tax_number", String.class), taxNumber)
                .set(field("company_name", String.class), companyName)
                .set(field("label", String.class), label)
                .set(field("created_at", OffsetDateTime.class), now)
                .set(field("updated_at", OffsetDateTime.class), now)
                .execute();
    }

    /**
     * Delete a watchlist entry by ID, verifying tenant ownership.
     * Returns the number of deleted rows (0 = not found or not owned).
     *
     * @param id       entry UUID to delete
     * @param tenantId tenant that must own the entry
     * @return number of deleted rows (0 or 1)
     */
    public int deleteByIdAndTenantId(UUID id, UUID tenantId) {
        return dsl.deleteFrom(table("watchlist_entries"))
                .where(field("id", UUID.class).eq(id))
                .and(field("tenant_id", UUID.class).eq(tenantId))
                .execute();
    }

    /**
     * Update the denormalized verdict status and last_checked_at timestamp on a watchlist entry.
     * Used by the WatchlistMonitor after re-evaluation and by PartnerStatusChangedListener on events.
     *
     * @param tenantId      tenant owning the entry
     * @param taxNumber     the tax number to update
     * @param verdictStatus new verdict status (e.g., "RELIABLE", "AT_RISK")
     * @param checkedAt     timestamp of the evaluation
     * @return number of rows updated (0 = no matching entry)
     */
    public int updateVerdictStatus(UUID tenantId, String taxNumber, String verdictStatus, OffsetDateTime checkedAt) {
        return dsl.update(table("watchlist_entries"))
                .set(field("last_verdict_status", String.class), verdictStatus)
                .set(field("last_checked_at", OffsetDateTime.class), checkedAt)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .and(field("tax_number", String.class).eq(taxNumber))
                .execute();
    }

    /**
     * Update only the last_checked_at timestamp without changing the verdict status.
     * Used by the WatchlistMonitor when a transient failure occurs (INCOMPLETE) —
     * the existing verdict status must be preserved, but we record that monitoring was attempted.
     *
     * @param tenantId  tenant owning the entry
     * @param taxNumber the tax number to update
     * @param checkedAt timestamp of the monitoring attempt
     * @return number of rows updated
     */
    public int updateCheckedAt(UUID tenantId, String taxNumber, OffsetDateTime checkedAt) {
        return dsl.update(table("watchlist_entries"))
                .set(field("last_checked_at", OffsetDateTime.class), checkedAt)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .and(field("tax_number", String.class).eq(taxNumber))
                .execute();
    }

    /**
     * Find all watchlist entries across ALL tenants for a given tax number.
     * Used by {@link hu.riskguard.notification.domain.PartnerStatusChangedListener} to update
     * watchlist entries reactively when a PartnerStatusChanged event fires.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> Returns entries from all tenants that have
     * this tax number on their watchlist. Only call from event listeners — never from user-facing code.
     *
     * @param taxNumber the tax number to search for
     * @return list of (tenantId, taxNumber) pairs for all matching watchlist entries
     */
    public List<WatchlistPartner> findWatchlistEntriesByTaxNumber(String taxNumber) {
        return dsl.select(
                        field("tenant_id", UUID.class),
                        field("tax_number", String.class))
                .from(table("watchlist_entries"))
                .where(field("tax_number", String.class).eq(taxNumber))
                .fetchInto(WatchlistPartner.class);
    }

    /**
     * Count watchlist entries for a tenant — used by sidebar badge.
     *
     * @param tenantId the tenant to count entries for
     * @return entry count
     */
    public int countByTenantId(UUID tenantId) {
        return dsl.selectCount()
                .from(table("watchlist_entries"))
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .fetchOne(0, int.class);
    }

    /**
     * Internal record for returning full watchlist entry data from queries.
     * Includes verdict data from denormalized columns on {@code watchlist_entries}.
     * TODO: Replace with jOOQ generated record once codegen includes watchlist_entries.
     */
    public record WatchlistEntryRecord(
            UUID id,
            UUID tenantId,
            String taxNumber,
            String companyName,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String verdictStatus,
            OffsetDateTime lastCheckedAt
    ) {
        /**
         * Convenience constructor without verdict fields (used by findByTenantIdAndTaxNumber, insert read-back).
         */
        public WatchlistEntryRecord(UUID id, UUID tenantId, String taxNumber, String companyName,
                                     String label, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
            this(id, tenantId, taxNumber, companyName, label, createdAt, updatedAt, null, null);
        }
    }
}
