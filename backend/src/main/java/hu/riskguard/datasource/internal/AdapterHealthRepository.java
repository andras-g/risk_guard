package hu.riskguard.datasource.internal;

import hu.riskguard.core.repository.BaseRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * jOOQ repository for {@code adapter_health} and {@code nav_credentials} tables.
 * Uses string-based DSL since these tables are added in Story 6.1 and jOOQ codegen
 * runs from a live DB; typed classes will be generated on next {@code ./gradlew generateJooq}.
 */
@Repository
public class AdapterHealthRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(AdapterHealthRepository.class);

    /**
     * Snapshot of a single adapter_health row.
     */
    public record AdapterHealthRow(
            String adapterName,
            String status,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            int failureCount,
            Double mtbfHours
    ) {}

    public AdapterHealthRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Upsert an adapter health record. Inserts on first occurrence; updates on conflict.
     */
    public void upsertHealth(
            String adapterName,
            String status,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            int failureCount,
            Double mtbfHours
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime successOdt = toOdt(lastSuccessAt);
        OffsetDateTime failureOdt = toOdt(lastFailureAt);
        BigDecimal mtbf = mtbfHours != null ? BigDecimal.valueOf(mtbfHours) : null;

        dsl.execute("""
                INSERT INTO adapter_health
                    (adapter_name, status, last_success_at, last_failure_at, failure_count, mtbf_hours, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (adapter_name) DO UPDATE SET
                    status          = EXCLUDED.status,
                    last_success_at = EXCLUDED.last_success_at,
                    last_failure_at = EXCLUDED.last_failure_at,
                    failure_count   = EXCLUDED.failure_count,
                    mtbf_hours      = EXCLUDED.mtbf_hours,
                    updated_at      = EXCLUDED.updated_at
                """,
                adapterName, status, successOdt, failureOdt, failureCount, mtbf, now
        );
    }

    /**
     * Returns all adapter health rows.
     */
    public List<AdapterHealthRow> findAll() {
        return dsl.select(
                        field("adapter_name"),
                        field("status"),
                        field("last_success_at"),
                        field("last_failure_at"),
                        field("failure_count"),
                        field("mtbf_hours")
                )
                .from(table("adapter_health"))
                .fetch()
                .map(this::mapRow);
    }

    /**
     * Returns the credential status for the given adapter, or {@code "NOT_CONFIGURED"} if no row.
     */
    public String findCredentialStatus(String adapterName) {
        String status = dsl.select(field("status"))
                .from(table("nav_credentials"))
                .where(field("adapter_name").eq(adapterName))
                .fetchOne(r -> r.get(field("status", String.class)));
        return status != null ? status : "NOT_CONFIGURED";
    }

    /**
     * Batch-fetches credential statuses for all given adapter names in a single query.
     * Missing adapter names default to {@code "NOT_CONFIGURED"} — callers should use
     * {@link Map#getOrDefault(Object, Object)} with that sentinel.
     */
    public Map<String, String> findAllCredentialStatuses(List<String> adapterNames) {
        if (adapterNames.isEmpty()) {
            return Map.of();
        }
        return dsl.select(field("adapter_name"), field("status"))
                .from(table("nav_credentials"))
                .where(field("adapter_name").in(adapterNames))
                .fetchMap(
                        r -> r.get(field("adapter_name", String.class)),
                        r -> r.get(field("status", String.class))
                );
    }

    /**
     * Atomically records a successful call — sets status=HEALTHY and last_success_at=now.
     * Does NOT touch failure_count or last_failure_at so concurrent error events are safe.
     */
    public void recordSuccess(String adapterName, Instant now) {
        OffsetDateTime odt = now.atOffset(ZoneOffset.UTC);
        dsl.execute("""
                INSERT INTO adapter_health
                    (adapter_name, status, last_success_at, last_failure_at, failure_count, mtbf_hours, updated_at)
                VALUES (?, 'HEALTHY', ?, NULL, 0, NULL, ?)
                ON CONFLICT (adapter_name) DO UPDATE SET
                    status          = 'HEALTHY',
                    last_success_at = EXCLUDED.last_success_at,
                    updated_at      = EXCLUDED.updated_at
                """,
                adapterName, odt, odt
        );
    }

    /**
     * Atomically records a failed call — increments failure_count, sets last_failure_at=now,
     * and recomputes MTBF in SQL. No prior read is required; concurrent events are safe.
     */
    public void recordFailure(String adapterName, Instant now) {
        OffsetDateTime odt = now.atOffset(ZoneOffset.UTC);
        dsl.execute("""
                INSERT INTO adapter_health
                    (adapter_name, status, last_success_at, last_failure_at, failure_count, mtbf_hours, updated_at)
                VALUES (?, 'DEGRADED', NULL, ?, 1, NULL, ?)
                ON CONFLICT (adapter_name) DO UPDATE SET
                    status          = 'DEGRADED',
                    last_failure_at = EXCLUDED.last_failure_at,
                    failure_count   = adapter_health.failure_count + 1,
                    mtbf_hours      = CASE
                                        WHEN adapter_health.last_success_at IS NOT NULL
                                          THEN EXTRACT(EPOCH FROM (EXCLUDED.last_failure_at - adapter_health.last_success_at))
                                               / 3600.0 / (adapter_health.failure_count + 1)
                                        ELSE NULL
                                      END,
                    updated_at      = EXCLUDED.updated_at
                """,
                adapterName, odt, odt
        );
    }

    /**
     * Records a circuit breaker state transition — updates only the status column.
     * All other counters and timestamps are left unchanged.
     */
    public void recordStateTransition(String adapterName, String status, Instant now) {
        OffsetDateTime odt = now.atOffset(ZoneOffset.UTC);
        dsl.execute("""
                INSERT INTO adapter_health
                    (adapter_name, status, last_success_at, last_failure_at, failure_count, mtbf_hours, updated_at)
                VALUES (?, ?, NULL, NULL, 0, NULL, ?)
                ON CONFLICT (adapter_name) DO UPDATE SET
                    status     = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """,
                adapterName, status, odt
        );
    }

    private AdapterHealthRow mapRow(Record r) {
        OffsetDateTime successOdt = r.get(field("last_success_at", OffsetDateTime.class));
        OffsetDateTime failureOdt = r.get(field("last_failure_at", OffsetDateTime.class));
        BigDecimal mtbfBd = r.get(field("mtbf_hours", BigDecimal.class));

        return new AdapterHealthRow(
                r.get(field("adapter_name", String.class)),
                r.get(field("status", String.class)),
                toInstant(successOdt),
                toInstant(failureOdt),
                r.get(field("failure_count", Integer.class)),
                mtbfBd != null ? mtbfBd.doubleValue() : null
        );
    }

    private OffsetDateTime toOdt(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
