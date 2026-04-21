package hu.riskguard.epr.audit.internal;

import hu.riskguard.core.repository.BaseRepository;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.AGGREGATION_AUDIT_LOG;

/**
 * jOOQ repository for {@code aggregation_audit_log} inserts (Story 10.8).
 *
 * <p>Accessed exclusively through {@link hu.riskguard.epr.audit.AuditService};
 * direct dependencies from outside the audit package are forbidden by
 * {@code EpicTenInvariantsTest.only_audit_package_writes_to_aggregation_audit_log}.
 */
@Repository
public class AggregationAuditRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(AggregationAuditRepository.class);

    public AggregationAuditRepository(DSLContext dsl) {
        super(dsl);
    }

    public void insertAggregationRun(UUID tenantId, LocalDate periodStart, LocalDate periodEnd,
                                     long durationMs, int resolvedCount, int unresolvedCount) {
        dsl.insertInto(AGGREGATION_AUDIT_LOG)
                .set(AGGREGATION_AUDIT_LOG.TENANT_ID, tenantId)
                .set(AGGREGATION_AUDIT_LOG.EVENT_TYPE, "AGGREGATION_RUN")
                .set(AGGREGATION_AUDIT_LOG.PERIOD_START, periodStart)
                .set(AGGREGATION_AUDIT_LOG.PERIOD_END, periodEnd)
                .set(AGGREGATION_AUDIT_LOG.DURATION_MS, durationMs)
                .set(AGGREGATION_AUDIT_LOG.RESOLVED_COUNT, resolvedCount)
                .set(AGGREGATION_AUDIT_LOG.UNRESOLVED_COUNT, unresolvedCount)
                .execute();
    }

    public void insertProvenanceFetch(UUID tenantId, UUID userId,
                                      LocalDate periodStart, LocalDate periodEnd,
                                      int page, int pageSize) {
        dsl.insertInto(AGGREGATION_AUDIT_LOG)
                .set(AGGREGATION_AUDIT_LOG.TENANT_ID, tenantId)
                .set(AGGREGATION_AUDIT_LOG.EVENT_TYPE, "PROVENANCE_FETCH")
                .set(AGGREGATION_AUDIT_LOG.PERIOD_START, periodStart)
                .set(AGGREGATION_AUDIT_LOG.PERIOD_END, periodEnd)
                .set(AGGREGATION_AUDIT_LOG.PAGE, page)
                .set(AGGREGATION_AUDIT_LOG.PAGE_SIZE, pageSize)
                .set(AGGREGATION_AUDIT_LOG.PERFORMED_BY_USER_ID, userId)
                .execute();
    }

    public void insertCsvExport(UUID tenantId, UUID userId,
                                LocalDate periodStart, LocalDate periodEnd) {
        dsl.insertInto(AGGREGATION_AUDIT_LOG)
                .set(AGGREGATION_AUDIT_LOG.TENANT_ID, tenantId)
                .set(AGGREGATION_AUDIT_LOG.EVENT_TYPE, "CSV_EXPORT")
                .set(AGGREGATION_AUDIT_LOG.PERIOD_START, periodStart)
                .set(AGGREGATION_AUDIT_LOG.PERIOD_END, periodEnd)
                .set(AGGREGATION_AUDIT_LOG.PERFORMED_BY_USER_ID, userId)
                .execute();
    }

    public void insertSubmissionDownload(UUID tenantId, UUID userId, UUID submissionId) {
        // Persist first, then log — on INSERT failure the log line must not claim success.
        dsl.insertInto(AGGREGATION_AUDIT_LOG)
                .set(AGGREGATION_AUDIT_LOG.TENANT_ID, tenantId)
                .set(AGGREGATION_AUDIT_LOG.EVENT_TYPE, "SUBMISSION_DOWNLOAD")
                .set(AGGREGATION_AUDIT_LOG.PERFORMED_BY_USER_ID, userId)
                .execute();
        log.info("[audit] submission_download tenantId={} userId={} submissionId={}",
                tenantId, userId, submissionId);
    }

    /** Count rows for a tenant and event type — used in round-trip tests. */
    public long countByTenantAndEventType(UUID tenantId, String eventType) {
        return dsl.selectCount()
                .from(AGGREGATION_AUDIT_LOG)
                .where(AGGREGATION_AUDIT_LOG.TENANT_ID.eq(tenantId))
                .and(AGGREGATION_AUDIT_LOG.EVENT_TYPE.eq(eventType))
                .fetchOne(0, Long.class);
    }

    /** Fetch most-recent row for tenant/eventType — used in round-trip tests. */
    public List<AggregationAuditRow> findByTenantAndEventType(UUID tenantId, String eventType) {
        return dsl.select(AGGREGATION_AUDIT_LOG.asterisk())
                .from(AGGREGATION_AUDIT_LOG)
                .where(AGGREGATION_AUDIT_LOG.TENANT_ID.eq(tenantId))
                .and(AGGREGATION_AUDIT_LOG.EVENT_TYPE.eq(eventType))
                .orderBy(AGGREGATION_AUDIT_LOG.CREATED_AT.desc())
                .fetch(r -> new AggregationAuditRow(
                        r.get(AGGREGATION_AUDIT_LOG.ID),
                        r.get(AGGREGATION_AUDIT_LOG.TENANT_ID),
                        r.get(AGGREGATION_AUDIT_LOG.EVENT_TYPE),
                        r.get(AGGREGATION_AUDIT_LOG.PERIOD_START),
                        r.get(AGGREGATION_AUDIT_LOG.PERIOD_END),
                        r.get(AGGREGATION_AUDIT_LOG.RESOLVED_COUNT),
                        r.get(AGGREGATION_AUDIT_LOG.UNRESOLVED_COUNT),
                        r.get(AGGREGATION_AUDIT_LOG.DURATION_MS),
                        r.get(AGGREGATION_AUDIT_LOG.PAGE),
                        r.get(AGGREGATION_AUDIT_LOG.PAGE_SIZE),
                        r.get(AGGREGATION_AUDIT_LOG.PERFORMED_BY_USER_ID)
                ));
    }

    public record AggregationAuditRow(
            UUID id, UUID tenantId, String eventType,
            LocalDate periodStart, LocalDate periodEnd,
            Integer resolvedCount, Integer unresolvedCount, Long durationMs,
            Integer page, Integer pageSize, UUID performedByUserId
    ) {}
}
