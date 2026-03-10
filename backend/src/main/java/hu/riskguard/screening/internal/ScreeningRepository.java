package hu.riskguard.screening.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.COMPANY_SNAPSHOTS;
import static hu.riskguard.jooq.Tables.SEARCH_AUDIT_LOG;
import static hu.riskguard.jooq.Tables.VERDICTS;

/**
 * jOOQ repository for the screening module.
 * Scoped to: {@code company_snapshots}, {@code verdicts}, {@code search_audit_log} tables ONLY.
 * All tenant-scoped queries include explicit {@code tenant_id} filter via {@link TenantContext}.
 */
@Repository
public class ScreeningRepository extends BaseRepository {

    public ScreeningRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Find a fresh (< threshold minutes old) snapshot for the same tenant + tax number.
     * Used for the idempotency guard to prevent duplicate searches.
     */
    public Optional<FreshSnapshot> findFreshSnapshot(String taxNumber, int freshnessMinutes) {
        UUID tenantId = requireTenantId();
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(freshnessMinutes);

        return dsl.select(
                        COMPANY_SNAPSHOTS.ID,
                        VERDICTS.ID,
                        VERDICTS.STATUS,
                        VERDICTS.CONFIDENCE,
                        VERDICTS.CREATED_AT
                )
                .from(COMPANY_SNAPSHOTS)
                .join(VERDICTS).on(VERDICTS.SNAPSHOT_ID.eq(COMPANY_SNAPSHOTS.ID))
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .and(COMPANY_SNAPSHOTS.CREATED_AT.gt(threshold))
                .orderBy(COMPANY_SNAPSHOTS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(r -> new FreshSnapshot(
                        r.get(COMPANY_SNAPSHOTS.ID),
                        r.get(VERDICTS.ID),
                        r.get(VERDICTS.STATUS),
                        r.get(VERDICTS.CONFIDENCE),
                        r.get(VERDICTS.CREATED_AT)
                ));
    }

    /**
     * Create a new company snapshot with empty snapshot data.
     */
    public UUID createSnapshot(String taxNumber) {
        UUID id = UUID.randomUUID();
        UUID tenantId = requireTenantId();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.ID, id)
                .set(COMPANY_SNAPSHOTS.TENANT_ID, tenantId)
                .set(COMPANY_SNAPSHOTS.TAX_NUMBER, taxNumber)
                .set(COMPANY_SNAPSHOTS.SNAPSHOT_DATA, JSONB.jsonb("{}"))
                .set(COMPANY_SNAPSHOTS.CREATED_AT, now)
                .set(COMPANY_SNAPSHOTS.UPDATED_AT, now)
                .execute();

        return id;
    }

    /**
     * Create a new verdict with INCOMPLETE status (no scrapers implemented yet).
     */
    public UUID createVerdict(UUID snapshotId) {
        UUID id = UUID.randomUUID();
        UUID tenantId = requireTenantId();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(VERDICTS)
                .set(VERDICTS.ID, id)
                .set(VERDICTS.TENANT_ID, tenantId)
                .set(VERDICTS.SNAPSHOT_ID, snapshotId)
                .set(VERDICTS.STATUS, VerdictStatus.INCOMPLETE)
                .set(VERDICTS.CONFIDENCE, VerdictConfidence.UNAVAILABLE)
                .set(VERDICTS.CREATED_AT, now)
                .set(VERDICTS.UPDATED_AT, now)
                .execute();

        return id;
    }

    /**
     * Write an audit log entry with SHA-256 hash for legal proof.
     */
    public void writeAuditLog(String taxNumber, UUID userId, String disclaimerText) {
        UUID tenantId = requireTenantId();
        String hash = HashUtil.sha256(tenantId.toString(), taxNumber, disclaimerText);

        dsl.insertInto(SEARCH_AUDIT_LOG)
                .set(SEARCH_AUDIT_LOG.ID, UUID.randomUUID())
                .set(SEARCH_AUDIT_LOG.TENANT_ID, tenantId)
                .set(SEARCH_AUDIT_LOG.TAX_NUMBER, taxNumber)
                .set(SEARCH_AUDIT_LOG.SEARCHED_BY, userId)
                .set(SEARCH_AUDIT_LOG.SHA256_HASH, hash)
                .set(SEARCH_AUDIT_LOG.DISCLAIMER_TEXT, disclaimerText)
                .set(SEARCH_AUDIT_LOG.SEARCHED_AT, OffsetDateTime.now())
                .execute();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("CRITICAL: Missing tenant context for screening operation");
        }
        return tenantId;
    }

    /**
     * Internal record for returning fresh snapshot query results.
     */
    public record FreshSnapshot(
            UUID snapshotId,
            UUID verdictId,
            VerdictStatus status,
            VerdictConfidence confidence,
            OffsetDateTime createdAt
    ) {}
}
