package hu.riskguard.epr.registry.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.registry.domain.ClassifierUsageSummary;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.AI_CLASSIFIER_USAGE;
import static hu.riskguard.jooq.Tables.TENANTS;

/**
 * jOOQ repository for AI classifier usage tracking.
 *
 * <p>{@code findAllForMonth} intentionally does NOT use {@code tenantCondition()} — it is
 * a cross-tenant admin query, restricted to PLATFORM_ADMIN callers at the controller layer.
 */
@Repository
public class ClassifierUsageRepository extends BaseRepository {

    public ClassifierUsageRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Returns the current call count for the tenant + year-month, or {@code 0}
     * if no row exists for that month yet (Story 10.3).
     */
    public int getCallCountForMonth(UUID tenantId, String yearMonth) {
        Integer count = dsl.select(AI_CLASSIFIER_USAGE.CALL_COUNT)
                .from(AI_CLASSIFIER_USAGE)
                .where(AI_CLASSIFIER_USAGE.TENANT_ID.eq(tenantId))
                .and(AI_CLASSIFIER_USAGE.YEAR_MONTH.eq(yearMonth))
                .fetchOne(AI_CLASSIFIER_USAGE.CALL_COUNT);
        return count == null ? 0 : count;
    }

    /**
     * Returns true when the tenant has reached or exceeded the monthly cap.
     */
    public boolean isCapExceeded(UUID tenantId, String yearMonth, int cap) {
        return getCallCountForMonth(tenantId, yearMonth) >= cap;
    }

    /**
     * Atomic upsert: inserts a new row with call_count=1 or increments an existing row.
     * Accumulates input/output token counts for accurate cost metering.
     * Single SQL — no read-then-write race condition.
     */
    public void upsertIncrement(UUID tenantId, String yearMonth, int inputTokens, int outputTokens) {
        dsl.execute(
                "INSERT INTO ai_classifier_usage (tenant_id, year_month, call_count, input_tokens, output_tokens) " +
                "VALUES (?, ?, 1, ?, ?) " +
                "ON CONFLICT (tenant_id, year_month) " +
                "DO UPDATE SET call_count = ai_classifier_usage.call_count + 1, " +
                "input_tokens = ai_classifier_usage.input_tokens + EXCLUDED.input_tokens, " +
                "output_tokens = ai_classifier_usage.output_tokens + EXCLUDED.output_tokens, " +
                "updated_at = now()",
                tenantId, yearMonth, inputTokens, outputTokens
        );
    }

    /**
     * Returns all tenants' usage for the given month, ordered by call_count DESC.
     * Cross-tenant: must be called only from PLATFORM_ADMIN-gated paths.
     */
    public List<ClassifierUsageSummary> findAllForMonth(String yearMonth) {
        return dsl.select(
                        AI_CLASSIFIER_USAGE.TENANT_ID,
                        TENANTS.NAME,
                        AI_CLASSIFIER_USAGE.CALL_COUNT,
                        AI_CLASSIFIER_USAGE.INPUT_TOKENS,
                        AI_CLASSIFIER_USAGE.OUTPUT_TOKENS
                )
                .from(AI_CLASSIFIER_USAGE)
                .join(TENANTS).on(TENANTS.ID.eq(AI_CLASSIFIER_USAGE.TENANT_ID))
                .where(AI_CLASSIFIER_USAGE.YEAR_MONTH.eq(yearMonth))
                .orderBy(AI_CLASSIFIER_USAGE.CALL_COUNT.desc())
                .fetch(r -> new ClassifierUsageSummary(
                        r.get(AI_CLASSIFIER_USAGE.TENANT_ID),
                        r.get(TENANTS.NAME),
                        r.get(AI_CLASSIFIER_USAGE.CALL_COUNT),
                        r.get(AI_CLASSIFIER_USAGE.INPUT_TOKENS),
                        r.get(AI_CLASSIFIER_USAGE.OUTPUT_TOKENS)
                ));
    }
}
