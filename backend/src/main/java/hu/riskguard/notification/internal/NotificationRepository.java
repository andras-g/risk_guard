package hu.riskguard.notification.internal;

import hu.riskguard.notification.domain.MonitoredPartner;
import hu.riskguard.notification.domain.WatchlistPartner;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import org.jooq.JSONB;

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

    /** Mirrors {@code ScreeningRepository.HASH_UNAVAILABLE_SENTINEL}. Defined locally to avoid cross-module import. */
    private static final String HASH_UNAVAILABLE_SENTINEL = "HASH_UNAVAILABLE";

    private final DSLContext dsl;

    public NotificationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all watchlist entries across ALL tenants — used by the background {@code AsyncIngestor}.
     * Resolves the owning user (accountant) via a LEFT JOIN on {@code tenant_mandates} so the ingestor
     * can write AUTOMATED audit log entries attributed to the correct user.
     *
     * <p>The JOIN is a LEFT JOIN — if no active mandate exists for a tenant the entry is still
     * returned with {@code userId = null}. The ingestor must handle null userId gracefully
     * (skip audit write or use a sentinel user ID).
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> This method deliberately bypasses
     * {@code TenantContext} because it is called from a background job with no user session.
     *
     * <p>Only call this from the scheduled background jobs — never from user-facing code.
     *
     * @return all watchlist entries as {@code WatchlistPartner} records (tenantId + taxNumber + userId)
     */
    public List<WatchlistPartner> findAllWatchlistEntries() {
        return dsl.select(
                        field("we.tenant_id", UUID.class),
                        field("we.tax_number", String.class),
                        field("tm.accountant_user_id", UUID.class))
                .from(table("watchlist_entries").as("we"))
                .leftJoin(table("tenant_mandates").as("tm"))
                    .on(field("tm.tenant_id", UUID.class).eq(field("we.tenant_id", UUID.class)))
                    .and(field("tm.valid_to").isNull()
                        .or(field("tm.valid_to", OffsetDateTime.class).gt(OffsetDateTime.now())))
                .fetch(r -> new WatchlistPartner(
                        r.get(field("we.tenant_id", UUID.class)),
                        r.get(field("we.tax_number", String.class)),
                        r.get(field("tm.accountant_user_id", UUID.class))));
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
                        field("last_checked_at", OffsetDateTime.class),
                        field("latest_sha256_hash", String.class),
                        field("previous_verdict_status", String.class))
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
                        r.get(field("last_checked_at", OffsetDateTime.class)),
                        r.get(field("latest_sha256_hash", String.class)),
                        r.get(field("previous_verdict_status", String.class))));
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
                        field("last_checked_at", OffsetDateTime.class),
                        field("latest_sha256_hash", String.class),
                        field("previous_verdict_status", String.class))
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
                        r.get(field("last_checked_at", OffsetDateTime.class)),
                        r.get(field("latest_sha256_hash", String.class)),
                        r.get(field("previous_verdict_status", String.class))));
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
     * Update the denormalized verdict status, last_checked_at, and (conditionally) latest_sha256_hash.
     * The hash is only overwritten when {@code sha256Hash} is non-null and not the sentinel
     * {@code "HASH_UNAVAILABLE"} — null/sentinel retains the existing column value.
     *
     * @param tenantId      tenant owning the entry
     * @param taxNumber     the tax number to update
     * @param verdictStatus new verdict status
     * @param checkedAt     timestamp of the evaluation
     * @param sha256Hash    SHA-256 hash from search_audit_log (may be null or sentinel)
     * @return number of rows updated
     */
    public int updateVerdictStatusWithHash(UUID tenantId, String taxNumber,
                                           String verdictStatus, OffsetDateTime checkedAt,
                                           String sha256Hash) {
        var update = dsl.update(table("watchlist_entries"))
                .set(field("previous_verdict_status", String.class), field("last_verdict_status", String.class))
                .set(field("last_verdict_status", String.class), verdictStatus)
                .set(field("last_checked_at", OffsetDateTime.class), checkedAt)
                .set(field("updated_at", OffsetDateTime.class), OffsetDateTime.now());
        if (sha256Hash != null && !sha256Hash.isBlank() && !sha256Hash.equals(HASH_UNAVAILABLE_SENTINEL)) {
            update = update.set(field("latest_sha256_hash", String.class), sha256Hash);
        }
        return update.where(field("tenant_id", UUID.class).eq(tenantId))
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
                .fetch(r -> new WatchlistPartner(
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("tax_number", String.class)),
                        null)); // userId not needed for status-change event listener
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
     * Record returned from watchlist entry queries that includes the tenant owner's user_id.
     * Used by PartnerStatusChangedListener to create outbox records with the correct recipient.
     */
    public record WatchlistEntryWithUser(UUID tenantId, String taxNumber, String companyName, UUID userId) {}

    /**
     * Find all watchlist entries across ALL tenants for a given tax number, including the
     * tenant owner's user_id (resolved via tenant_mandates).
     *
     * <p>Since {@code watchlist_entries} doesn't have a {@code user_id} column, we JOIN
     * with {@code tenant_mandates} to find the first active mandated user for each tenant.
     * This user receives the email notification.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> Returns entries from all tenants.
     * Only call from event listeners.
     *
     * @param taxNumber the tax number to search for
     * @return list of entries with tenantId, taxNumber, companyName, and resolved userId
     */
    public List<WatchlistEntryWithUser> findWatchlistEntriesWithUserByTaxNumber(String taxNumber) {
        return dsl.select(
                        field("we.tenant_id", UUID.class),
                        field("we.tax_number", String.class),
                        field("we.company_name", String.class),
                        field("tm.accountant_user_id", UUID.class))
                .from(table("watchlist_entries").as("we"))
                .join(table("tenant_mandates").as("tm"))
                    .on(field("tm.tenant_id", UUID.class).eq(field("we.tenant_id", UUID.class)))
                .where(field("we.tax_number", String.class).eq(taxNumber))
                .and(field("tm.valid_to").isNull()
                        .or(field("tm.valid_to", OffsetDateTime.class).gt(OffsetDateTime.now())))
                .fetch(r -> new WatchlistEntryWithUser(
                        r.get(field("we.tenant_id", UUID.class)),
                        r.get(field("we.tax_number", String.class)),
                        r.get(field("we.company_name", String.class)),
                        r.get(field("tm.accountant_user_id", UUID.class))));
    }

    // ─── Flight Control Aggregation (Story 3.10) ────────────────────────────

    /**
     * Raw aggregated row returned from the flight control watchlist query.
     * The service pivots multiple rows (one per status per tenant) into
     * {@link hu.riskguard.notification.domain.FlightControlTenantSummary} objects.
     */
    public record WatchlistAggregateRow(
            UUID tenantId,
            String lastVerdictStatus,
            int count,
            OffsetDateTime lastChecked
    ) {}

    /**
     * Resolve tenant names for a list of tenant IDs.
     * Used by the Flight Control aggregation service to merge tenant names without
     * importing identity module DTOs.
     *
     * <p>Queries the {@code tenants} table directly (same approach as
     * {@link #findPortfolioAlerts} which also JOINs {@code tenants}).
     *
     * @param tenantIds list of tenant UUIDs to resolve names for
     * @return map of tenantId → tenant name
     */
    public java.util.Map<UUID, String> findTenantNamesByIds(List<UUID> tenantIds) {
        if (tenantIds.isEmpty()) return java.util.Map.of();
        return dsl.select(
                        field("id", UUID.class),
                        field("name", String.class))
                .from(table("tenants"))
                .where(field("id", UUID.class).in(tenantIds))
                .fetch()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> r.get(field("id", UUID.class)),
                        r -> r.get(field("name", String.class))));
    }

    /**
     * Aggregate watchlist entries by tenant and verdict status for the Flight Control dashboard.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ — accountant mandate-scoped:</b>
     * This method explicitly uses {@code WHERE tenant_id IN (:tenantIds)} instead of
     * {@code TenantFilter} context. Authorization is enforced by the caller
     * ({@link hu.riskguard.notification.domain.NotificationService#getFlightControlSummary}).
     *
     * <p>Returns one row per (tenant_id, last_verdict_status) pair, plus the
     * MAX(last_checked_at) and COUNT(*) for each group. The service pivots these
     * into per-tenant summaries and merges with the full tenant name list.
     *
     * @param tenantIds list of tenant UUIDs from active accountant mandates
     * @return grouped aggregate rows — one per (tenant, status) combination
     */
    public List<WatchlistAggregateRow> aggregateWatchlistByTenant(List<UUID> tenantIds) {
        return dsl.select(
                        field("tenant_id", UUID.class),
                        field("last_verdict_status", String.class),
                        count().as("cnt"),
                        max(field("last_checked_at", OffsetDateTime.class)).as("last_checked"))
                .from(table("watchlist_entries"))
                .where(field("tenant_id", UUID.class).in(tenantIds))
                .groupBy(field("tenant_id", UUID.class), field("last_verdict_status", String.class))
                .fetch(r -> new WatchlistAggregateRow(
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("last_verdict_status", String.class)),
                        r.get(field("cnt", Integer.class)),
                        r.get(field("last_checked", OffsetDateTime.class))));
    }

    // ─── Portfolio Alerts (Story 3.9) ──────────────────────────────────────

    /**
     * Internal record for raw outbox data with tenant name — returned by portfolio alerts query.
     * The service layer parses the JSONB payload and converts to domain {@code PortfolioAlert} records.
     */
    public record PortfolioOutboxRecord(
            UUID id,
            UUID tenantId,
            String tenantName,
            String type,
            String payload,
            OffsetDateTime createdAt
    ) {}

    /**
     * Find SENT outbox records (ALERT and DIGEST) across multiple tenants for the portfolio feed.
     * JOINs {@code tenants} to resolve human-readable tenant name for display.
     *
     * <p><b>⚠️ PRIVILEGED CROSS-TENANT READ:</b> This method reads outbox records across
     * multiple tenants for an accountant's portfolio view. Authorization is enforced by the
     * caller (mandate check in NotificationService), not by TenantFilter.
     *
     * @param tenantIds list of tenant UUIDs the accountant has active mandates for
     * @param since     earliest created_at timestamp to include (e.g., 7 days ago)
     * @return raw outbox records with tenant name, ordered by created_at DESC, limited to 100
     */
    public List<PortfolioOutboxRecord> findPortfolioAlerts(List<UUID> tenantIds, OffsetDateTime since) {
        return dsl.select(
                        field("o.id", UUID.class),
                        field("o.tenant_id", UUID.class),
                        field("t.name", String.class),
                        field("o.type", String.class),
                        field("o.payload", String.class),
                        field("o.created_at", OffsetDateTime.class))
                .from(table("notification_outbox").as("o"))
                .join(table("tenants").as("t"))
                    .on(field("t.id", UUID.class).eq(field("o.tenant_id", UUID.class)))
                .where(field("o.tenant_id", UUID.class).in(tenantIds))
                .and(field("o.type", String.class).in("ALERT", "DIGEST"))
                .and(field("o.status", String.class).eq("SENT"))
                .and(field("o.created_at", OffsetDateTime.class).ge(since))
                .orderBy(field("o.created_at", OffsetDateTime.class).desc())
                .limit(100)
                .fetch(r -> new PortfolioOutboxRecord(
                        r.get(field("o.id", UUID.class)),
                        r.get(field("o.tenant_id", UUID.class)),
                        r.get(field("t.name", String.class)),
                        r.get(field("o.type", String.class)),
                        r.get(field("o.payload", String.class)),
                        r.get(field("o.created_at", OffsetDateTime.class))));
    }

    // ─── Outbox CRUD (Story 3.8) ────────────────────────────────────────────

    /**
     * Outbox record returned from queries.
     * TODO: Replace with jOOQ generated record once codegen includes notification_outbox.
     */
    public record OutboxRecord(
            UUID id,
            UUID tenantId,
            UUID userId,
            String type,
            String payload,
            String status,
            int retryCount,
            OffsetDateTime nextRetryAt,
            OffsetDateTime createdAt,
            OffsetDateTime sentAt
    ) {}

    /**
     * Insert a new outbox record for email notification delivery.
     */
    public void insertOutboxRecord(UUID id, UUID tenantId, UUID userId, String type,
                                    String payload, String status) {
        dsl.insertInto(table("notification_outbox"))
                .set(field("id", UUID.class), id)
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("user_id", UUID.class), userId)
                .set(field("type", String.class), type)
                .set(field("payload", JSONB.class), JSONB.valueOf(payload))
                .set(field("status", String.class), status)
                .set(field("retry_count", Integer.class), 0)
                .set(field("created_at", OffsetDateTime.class), OffsetDateTime.now())
                .execute();
    }

    /**
     * Find pending outbox records ready for processing.
     * Returns records where status=PENDING and (next_retry_at <= now OR next_retry_at IS NULL),
     * ordered by created_at ASC, limited to batch size.
     */
    public List<OutboxRecord> findPendingOutboxRecords(int limit) {
        return dsl.select(
                        field("id", UUID.class),
                        field("tenant_id", UUID.class),
                        field("user_id", UUID.class),
                        field("type", String.class),
                        field("payload", String.class),
                        field("status", String.class),
                        field("retry_count", Integer.class),
                        field("next_retry_at", OffsetDateTime.class),
                        field("created_at", OffsetDateTime.class),
                        field("sent_at", OffsetDateTime.class))
                .from(table("notification_outbox"))
                .where(field("status", String.class).eq("PENDING"))
                .and(field("next_retry_at", OffsetDateTime.class).le(OffsetDateTime.now())
                        .or(field("next_retry_at").isNull()))
                .orderBy(field("created_at", OffsetDateTime.class).asc())
                .limit(limit)
                .fetch(r -> new OutboxRecord(
                        r.get(field("id", UUID.class)),
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("user_id", UUID.class)),
                        r.get(field("type", String.class)),
                        r.get(field("payload", String.class)),
                        r.get(field("status", String.class)),
                        r.get(field("retry_count", Integer.class)),
                        r.get(field("next_retry_at", OffsetDateTime.class)),
                        r.get(field("created_at", OffsetDateTime.class)),
                        r.get(field("sent_at", OffsetDateTime.class))));
    }

    /**
     * Mark an outbox record as SENT with current timestamp.
     */
    public void updateOutboxSent(UUID id) {
        dsl.update(table("notification_outbox"))
                .set(field("status", String.class), "SENT")
                .set(field("sent_at", OffsetDateTime.class), OffsetDateTime.now())
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    /**
     * Update outbox record retry state — incremented retry count and next retry timestamp.
     */
    public void updateOutboxRetry(UUID id, int retryCount, OffsetDateTime nextRetryAt) {
        dsl.update(table("notification_outbox"))
                .set(field("retry_count", Integer.class), retryCount)
                .set(field("next_retry_at", OffsetDateTime.class), nextRetryAt)
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    /**
     * Mark an outbox record as permanently FAILED after max retries exceeded.
     */
    public void updateOutboxFailed(UUID id) {
        dsl.update(table("notification_outbox"))
                .set(field("status", String.class), "FAILED")
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    /**
     * Count today's ALERT records for a tenant with PENDING or SENT status —
     * used for digest mode gating (AC6).
     *
     * <p>Per AC6: the daily limit applies to PENDING+SENT combined. FAILED records
     * are excluded because permanently failed deliveries should not prevent new alert
     * attempts from being created individually.
     *
     * @param tenantId   the tenant to count for
     * @param startOfDay start of the current day (truncated to midnight)
     * @return count of PENDING+SENT ALERT records created today
     */
    public int countTodayAlertsByTenant(UUID tenantId, OffsetDateTime startOfDay) {
        return dsl.selectCount()
                .from(table("notification_outbox"))
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .and(field("type", String.class).eq("ALERT"))
                .and(field("status", String.class).in("PENDING", "SENT"))
                .and(field("created_at", OffsetDateTime.class).ge(startOfDay))
                .fetchOne(0, int.class);
    }

    /**
     * Count all PENDING outbox records — used by health indicator.
     */
    public int countPendingTotal() {
        return dsl.selectCount()
                .from(table("notification_outbox"))
                .where(field("status", String.class).eq("PENDING"))
                .fetchOne(0, int.class);
    }

    /**
     * Count all FAILED outbox records — used by health indicator.
     */
    public int countFailedTotal() {
        return dsl.selectCount()
                .from(table("notification_outbox"))
                .where(field("status", String.class).eq("FAILED"))
                .fetchOne(0, int.class);
    }

    /**
     * Find existing PENDING DIGEST record for a tenant today — for digest mode aggregation.
     */
    public Optional<OutboxRecord> findPendingDigestForTenantToday(UUID tenantId, OffsetDateTime startOfDay) {
        return dsl.select(
                        field("id", UUID.class),
                        field("tenant_id", UUID.class),
                        field("user_id", UUID.class),
                        field("type", String.class),
                        field("payload", String.class),
                        field("status", String.class),
                        field("retry_count", Integer.class),
                        field("next_retry_at", OffsetDateTime.class),
                        field("created_at", OffsetDateTime.class),
                        field("sent_at", OffsetDateTime.class))
                .from(table("notification_outbox"))
                .where(field("tenant_id", UUID.class).eq(tenantId))
                .and(field("type", String.class).eq("DIGEST"))
                .and(field("status", String.class).eq("PENDING"))
                .and(field("created_at", OffsetDateTime.class).ge(startOfDay))
                .fetchOptional(r -> new OutboxRecord(
                        r.get(field("id", UUID.class)),
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("user_id", UUID.class)),
                        r.get(field("type", String.class)),
                        r.get(field("payload", String.class)),
                        r.get(field("status", String.class)),
                        r.get(field("retry_count", Integer.class)),
                        r.get(field("next_retry_at", OffsetDateTime.class)),
                        r.get(field("created_at", OffsetDateTime.class)),
                        r.get(field("sent_at", OffsetDateTime.class))));
    }

    /**
     * Update the payload of an existing outbox record — used for digest mode aggregation.
     */
    public void updateOutboxPayload(UUID id, String payload) {
        dsl.update(table("notification_outbox"))
                .set(field("payload", JSONB.class), JSONB.valueOf(payload))
                .where(field("id", UUID.class).eq(id))
                .execute();
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
            OffsetDateTime lastCheckedAt,
            String latestSha256Hash,
            String previousVerdictStatus
    ) {
        /**
         * Convenience constructor without verdict fields (used by findByTenantIdAndTaxNumber, insert read-back).
         */
        public WatchlistEntryRecord(UUID id, UUID tenantId, String taxNumber, String companyName,
                                     String label, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
            this(id, tenantId, taxNumber, companyName, label, createdAt, updatedAt, null, null, null, null);
        }
    }
}
