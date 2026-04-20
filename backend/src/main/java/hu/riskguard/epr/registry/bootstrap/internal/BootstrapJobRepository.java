package hu.riskguard.epr.registry.bootstrap.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobRecord;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;

/**
 * jOOQ repository for {@code epr_bootstrap_jobs}.
 *
 * <p>All methods are plain jOOQ — no {@code @Transactional}. Callers own
 * the transaction boundaries via {@code TransactionTemplate.execute(...)}.
 *
 * <p>The in-flight guard uses the partial index
 * {@code idx_epr_bootstrap_jobs_tenant_inflight} created in V20260420_001.
 * The INSERT…WHERE NOT EXISTS pattern leverages that index for fast lookup.
 */
@Repository
public class BootstrapJobRepository extends BaseRepository {

    public BootstrapJobRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Inserts a new PENDING job only when no PENDING/RUNNING job exists for the tenant.
     *
     * @return the new job's UUID, or {@link Optional#empty()} when the in-flight guard trips
     */
    public Optional<UUID> insertIfNoInflight(UUID tenantId, LocalDate periodFrom, LocalDate periodTo,
                                              UUID triggeredByUserId) {
        // WHERE NOT EXISTS exploits the partial index on (tenant_id) WHERE status IN ('PENDING','RUNNING')
        UUID id = dsl.insertInto(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.TENANT_ID, tenantId)
                .set(EPR_BOOTSTRAP_JOBS.STATUS, BootstrapJobStatus.PENDING.name())
                .set(EPR_BOOTSTRAP_JOBS.PERIOD_FROM, periodFrom)
                .set(EPR_BOOTSTRAP_JOBS.PERIOD_TO, periodTo)
                .set(EPR_BOOTSTRAP_JOBS.TRIGGERED_BY_USER_ID, triggeredByUserId)
                .onConflictDoNothing()  // partial unique index prevents double-PENDING for same tenant
                .returning(EPR_BOOTSTRAP_JOBS.ID)
                .fetchOne(EPR_BOOTSTRAP_JOBS.ID);
        return Optional.ofNullable(id);
    }

    /**
     * Look up a job by id, verifying tenant ownership.
     * Returns {@link Optional#empty()} for unknown id or cross-tenant access.
     */
    public Optional<BootstrapJobRecord> findByIdAndTenant(UUID jobId, UUID tenantId) {
        return dsl.selectFrom(EPR_BOOTSTRAP_JOBS)
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .and(EPR_BOOTSTRAP_JOBS.TENANT_ID.eq(tenantId))
                .fetchOptional(r -> new BootstrapJobRecord(
                        r.getId(), r.getTenantId(),
                        BootstrapJobStatus.valueOf(r.getStatus()),
                        r.getPeriodFrom(), r.getPeriodTo(),
                        nullToZero(r.getTotalPairs()),
                        nullToZero(r.getClassifiedPairs()),
                        nullToZero(r.getVtszFallbackPairs()),
                        nullToZero(r.getUnresolvedPairs()),
                        nullToZero(r.getCreatedProducts()),
                        nullToZero(r.getDeletedProducts()),
                        r.getTriggeredByUserId(),
                        r.getErrorMessage(),
                        r.getCreatedAt(), r.getUpdatedAt(), r.getCompletedAt()
                ));
    }

    /**
     * Look up the tenant owner of a job id. Returns {@link Optional#empty()} only for
     * unknown ids — cross-tenant access is disambiguated at the controller layer by
     * comparing the returned {@code tenantId} against the caller's JWT claim, which lets
     * the API return 404 for unknown vs. 403 for cross-tenant (AC #8, AC #9).
     */
    public Optional<UUID> findTenantForJob(UUID jobId) {
        return dsl.select(EPR_BOOTSTRAP_JOBS.TENANT_ID)
                .from(EPR_BOOTSTRAP_JOBS)
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .fetchOptional(r -> r.get(EPR_BOOTSTRAP_JOBS.TENANT_ID));
    }

    /**
     * Look up the in-flight (PENDING or RUNNING) job for a tenant.
     * Used by the in-flight guard check at API layer.
     */
    public Optional<BootstrapJobRecord> findInflightByTenant(UUID tenantId) {
        return dsl.selectFrom(EPR_BOOTSTRAP_JOBS)
                .where(EPR_BOOTSTRAP_JOBS.TENANT_ID.eq(tenantId))
                .and(EPR_BOOTSTRAP_JOBS.STATUS.in(
                        BootstrapJobStatus.PENDING.name(), BootstrapJobStatus.RUNNING.name()))
                .fetchOptional(r -> new BootstrapJobRecord(
                        r.getId(), r.getTenantId(),
                        BootstrapJobStatus.valueOf(r.getStatus()),
                        r.getPeriodFrom(), r.getPeriodTo(),
                        nullToZero(r.getTotalPairs()),
                        nullToZero(r.getClassifiedPairs()),
                        nullToZero(r.getVtszFallbackPairs()),
                        nullToZero(r.getUnresolvedPairs()),
                        nullToZero(r.getCreatedProducts()),
                        nullToZero(r.getDeletedProducts()),
                        r.getTriggeredByUserId(),
                        r.getErrorMessage(),
                        r.getCreatedAt(), r.getUpdatedAt(), r.getCompletedAt()
                ));
    }

    /** Atomically increment counters after processing a classifier chunk result. */
    public void incrementCounters(UUID jobId, int classified, int fallback, int unresolved,
                                   int created, int deleted) {
        dsl.update(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.CLASSIFIED_PAIRS,
                        EPR_BOOTSTRAP_JOBS.CLASSIFIED_PAIRS.add(classified))
                .set(EPR_BOOTSTRAP_JOBS.VTSZ_FALLBACK_PAIRS,
                        EPR_BOOTSTRAP_JOBS.VTSZ_FALLBACK_PAIRS.add(fallback))
                .set(EPR_BOOTSTRAP_JOBS.UNRESOLVED_PAIRS,
                        EPR_BOOTSTRAP_JOBS.UNRESOLVED_PAIRS.add(unresolved))
                .set(EPR_BOOTSTRAP_JOBS.CREATED_PRODUCTS,
                        EPR_BOOTSTRAP_JOBS.CREATED_PRODUCTS.add(created))
                .set(EPR_BOOTSTRAP_JOBS.DELETED_PRODUCTS,
                        EPR_BOOTSTRAP_JOBS.DELETED_PRODUCTS.add(deleted))
                .set(EPR_BOOTSTRAP_JOBS.UPDATED_AT, OffsetDateTime.now())
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .execute();
    }

    /** Update total_pairs after dedup completes (before classifier dispatch). */
    public void setTotalPairs(UUID jobId, int total) {
        dsl.update(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.TOTAL_PAIRS, total)
                .set(EPR_BOOTSTRAP_JOBS.UPDATED_AT, OffsetDateTime.now())
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .execute();
    }

    /**
     * Atomic cancel — conditionally UPDATE only when the current status is non-terminal
     * and the tenant matches. Returns the number of rows affected: 0 means the job is
     * either unknown/cross-tenant or already terminal (disambiguated by the caller).
     *
     * <p>Uses a single SQL statement to avoid the read-then-write race between
     * {@code findByIdAndTenant} and {@code transitionStatus(CANCELLED)} that could let
     * a worker-completed job be flipped back to CANCELLED.
     */
    public int cancelIfActive(UUID jobId, UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.update(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.STATUS, BootstrapJobStatus.CANCELLED.name())
                .set(EPR_BOOTSTRAP_JOBS.UPDATED_AT, now)
                .set(EPR_BOOTSTRAP_JOBS.COMPLETED_AT, now)
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .and(EPR_BOOTSTRAP_JOBS.TENANT_ID.eq(tenantId))
                .and(EPR_BOOTSTRAP_JOBS.STATUS.in(
                        BootstrapJobStatus.PENDING.name(), BootstrapJobStatus.RUNNING.name()))
                .execute();
    }

    /**
     * Transition the job to a new status, but only if the current status is non-terminal
     * (i.e., PENDING or RUNNING). This prevents the worker from overwriting a CANCELLED
     * row with COMPLETED/FAILED in the narrow window between the last {@link #readStatus}
     * check and the terminal UPDATE.
     *
     * <p>For terminal states, sets {@code completed_at}. For non-terminal transitions
     * ({@code PENDING → RUNNING}), leaves {@code completed_at} untouched so a prior
     * terminal cancel timestamp is not clobbered.
     *
     * @return number of rows updated — 0 means the job had already reached a terminal state
     */
    public int transitionStatus(UUID jobId, BootstrapJobStatus newStatus, String errorMessage) {
        boolean terminal = newStatus.isTerminal();
        var update = dsl.update(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.STATUS, newStatus.name())
                .set(EPR_BOOTSTRAP_JOBS.ERROR_MESSAGE, errorMessage)
                .set(EPR_BOOTSTRAP_JOBS.UPDATED_AT, OffsetDateTime.now());
        if (terminal) {
            update = update.set(EPR_BOOTSTRAP_JOBS.COMPLETED_AT, OffsetDateTime.now());
        }
        // Only advance when the job is still non-terminal. This closes the worker↔cancel race.
        return update
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .and(EPR_BOOTSTRAP_JOBS.STATUS.in(
                        BootstrapJobStatus.PENDING.name(), BootstrapJobStatus.RUNNING.name()))
                .execute();
    }

    /**
     * Re-read the job status in a fresh SELECT — used by the worker between batches
     * to detect CANCELLED while respecting per-pair REQUIRES_NEW tx boundaries.
     */
    public Optional<BootstrapJobStatus> readStatus(UUID jobId) {
        return dsl.select(EPR_BOOTSTRAP_JOBS.STATUS)
                .from(EPR_BOOTSTRAP_JOBS)
                .where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId))
                .fetchOptional(r -> BootstrapJobStatus.valueOf(r.get(EPR_BOOTSTRAP_JOBS.STATUS)));
    }

    private static int nullToZero(Integer v) {
        return v == null ? 0 : v;
    }
}
