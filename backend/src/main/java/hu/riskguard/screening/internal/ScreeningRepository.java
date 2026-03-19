package hu.riskguard.screening.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    private static final Logger log = LoggerFactory.getLogger(ScreeningRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Sentinel value written to {@code search_audit_log.sha256_hash} when hash computation fails.
     * Preserved as a named constant so callers can compare against it without hardcoding strings.
     * Must match the value checked in {@code VerdictCard.vue}'s {@code HASH_UNAVAILABLE_SENTINEL}.
     */
    public static final String HASH_UNAVAILABLE_SENTINEL = "HASH_UNAVAILABLE";

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
                .and(COMPANY_SNAPSHOTS.CREATED_AT.gt(threshold)
                        .or(COMPANY_SNAPSHOTS.CHECKED_AT.gt(threshold)))
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
     *
     * @param taxNumber normalized tax number
     * @param now       the search timestamp (shared across snapshot, verdict, and audit log)
     */
    public UUID createSnapshot(String taxNumber, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        UUID tenantId = requireTenantId();

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
     * Create a new verdict with the computed status and confidence from the VerdictEngine.
     *
     * @param snapshotId linked snapshot ID
     * @param status     computed verdict status from VerdictEngine
     * @param confidence computed data confidence from VerdictEngine
     * @param now        the search timestamp (shared across snapshot, verdict, and audit log)
     */
    public UUID createVerdict(UUID snapshotId, VerdictStatus status, VerdictConfidence confidence, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        UUID tenantId = requireTenantId();

        dsl.insertInto(VERDICTS)
                .set(VERDICTS.ID, id)
                .set(VERDICTS.TENANT_ID, tenantId)
                .set(VERDICTS.SNAPSHOT_ID, snapshotId)
                .set(VERDICTS.STATUS, status)
                .set(VERDICTS.CONFIDENCE, confidence)
                .set(VERDICTS.CREATED_AT, now)
                .set(VERDICTS.UPDATED_AT, now)
                .execute();

        return id;
    }

    /**
     * Write an audit log entry with SHA-256 hash for legal proof.
     *
     * <p>Hash covers: {@code snapshotDataJson + verdictStatus + verdictConfidence + disclaimerText}
     * — per Story 2.5 AC and NFR4. This constitutes the full legal proof (Snapshot + Verdict + Disclaimer).
     *
     * <p>If hash computation fails (e.g., null snapshot data), the audit row is written with the
     * sentinel value {@code "HASH_UNAVAILABLE"} so the row is preserved for admin review. The failure
     * is logged at ERROR level with {@code tenantId} only (no PII — @LogSafe).
     *
     * @param taxNumber          normalized tax number
     * @param userId             the user performing the search
     * @param disclaimerText     disclaimer text included in the hash
     * @param snapshotDataJson   serialized JSONB string of the snapshot data
     * @param verdictStatus      verdict status literal (e.g., "RELIABLE", "AT_RISK")
     * @param verdictConfidence  verdict confidence literal (e.g., "FRESH", "STALE")
     * @param verdictId          FK to the verdict created in this search
     * @param now                the search timestamp (shared across snapshot, verdict, and audit log)
     * @return the SHA-256 hash written to the audit log, or {@code "HASH_UNAVAILABLE"} if computation failed
     */
    public String writeAuditLog(String taxNumber, UUID userId, String disclaimerText,
                                String snapshotDataJson, String verdictStatus, String verdictConfidence,
                                UUID verdictId, OffsetDateTime now) {
        UUID tenantId = requireTenantId();

        String hash;
        try {
            hash = HashUtil.sha256(snapshotDataJson, verdictStatus, verdictConfidence, disclaimerText);
        } catch (IllegalArgumentException e) {
            log.error("Audit hash computation failed for tenant {}", tenantId, e);
            hash = HASH_UNAVAILABLE_SENTINEL;
        }

        dsl.insertInto(SEARCH_AUDIT_LOG)
                .set(SEARCH_AUDIT_LOG.ID, UUID.randomUUID())
                .set(SEARCH_AUDIT_LOG.TENANT_ID, tenantId)
                .set(SEARCH_AUDIT_LOG.TAX_NUMBER, taxNumber)
                .set(SEARCH_AUDIT_LOG.SEARCHED_BY, userId)
                .set(SEARCH_AUDIT_LOG.SHA256_HASH, hash)
                .set(SEARCH_AUDIT_LOG.DISCLAIMER_TEXT, disclaimerText)
                .set(SEARCH_AUDIT_LOG.SEARCHED_AT, now)
                .set(SEARCH_AUDIT_LOG.VERDICT_ID, verdictId)
                .execute();

        return hash;
    }

    /**
     * Update a snapshot with data from the data source module.
     * Called after parallel data retrieval completes to persist the aggregated government data.
     *
     * @param snapshotId         the snapshot to update
     * @param snapshotData       consolidated data map from all adapters (serialized to JSONB)
     * @param sourceUrls         list of source URLs accessed during data retrieval (serialized to JSONB)
     * @param domFingerprintHash SHA-256 hash of concatenated data for change detection
     * @param checkedAt          timestamp of when data retrieval was performed
     * @param dataSourceMode     the active data source mode (demo/test/live) — recorded for audit trail
     */
    public void updateSnapshotData(UUID snapshotId, Map<String, Object> snapshotData, List<String> sourceUrls,
                                   String domFingerprintHash, OffsetDateTime checkedAt, String dataSourceMode) {
        UUID tenantId = requireTenantId();

        try {
            String snapshotDataJson = JSON.writeValueAsString(snapshotData);
            String sourceUrlsJson = JSON.writeValueAsString(sourceUrls);

            dsl.update(COMPANY_SNAPSHOTS)
                    .set(COMPANY_SNAPSHOTS.SNAPSHOT_DATA, JSONB.jsonb(snapshotDataJson))
                    .set(COMPANY_SNAPSHOTS.SOURCE_URLS, JSONB.jsonb(sourceUrlsJson))
                    .set(COMPANY_SNAPSHOTS.DOM_FINGERPRINT_HASH, domFingerprintHash)
                    .set(COMPANY_SNAPSHOTS.CHECKED_AT, checkedAt)
                    .set(COMPANY_SNAPSHOTS.DATA_SOURCE_MODE, dataSourceMode)
                    .set(COMPANY_SNAPSHOTS.UPDATED_AT, OffsetDateTime.now())
                    .where(COMPANY_SNAPSHOTS.ID.eq(snapshotId))
                    .and(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                    .execute();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize snapshot data to JSON", e);
        }
    }

    /**
     * Find the most recent verdict status for the same tax number and tenant,
     * excluding a specific verdict (typically the one just created).
     * Used for status-change detection — compares new verdict with previous one.
     *
     * <p>Uses {@link TenantContext} for tenant scoping (consistent with all other repository methods).
     *
     * @param taxNumber        normalized tax number
     * @param excludeVerdictId the verdict ID to exclude (the one just created)
     * @return the previous verdict status, or empty if this is the first-ever search
     */
    public Optional<VerdictStatus> findPreviousVerdict(String taxNumber, UUID excludeVerdictId) {
        UUID tenantId = requireTenantId();

        return dsl.select(VERDICTS.STATUS)
                .from(VERDICTS)
                .join(COMPANY_SNAPSHOTS).on(VERDICTS.SNAPSHOT_ID.eq(COMPANY_SNAPSHOTS.ID))
                .where(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .and(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .and(VERDICTS.ID.ne(excludeVerdictId))
                .orderBy(VERDICTS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(VERDICTS.STATUS);
    }

    /**
     * Find a snapshot by its ID, scoped to the current tenant.
     * Returns the snapshot data needed to build the provenance response.
     *
     * @param snapshotId the snapshot UUID to look up
     * @return populated SnapshotRecord or empty if not found / not owned by current tenant
     */
    public Optional<SnapshotRecord> findSnapshotById(UUID snapshotId) {
        UUID tenantId = requireTenantId();

        return dsl.select(
                        COMPANY_SNAPSHOTS.ID,
                        COMPANY_SNAPSHOTS.TAX_NUMBER,
                        COMPANY_SNAPSHOTS.SNAPSHOT_DATA,
                        COMPANY_SNAPSHOTS.CHECKED_AT
                )
                .from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.ID.eq(snapshotId))
                .and(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .fetchOptional(r -> new SnapshotRecord(
                        r.get(COMPANY_SNAPSHOTS.ID),
                        r.get(COMPANY_SNAPSHOTS.TAX_NUMBER),
                        r.get(COMPANY_SNAPSHOTS.SNAPSHOT_DATA),
                        r.get(COMPANY_SNAPSHOTS.CHECKED_AT)
                ));
    }

    /**
     * Internal record for snapshot query results used in provenance lookups.
     */
    public record SnapshotRecord(
            UUID id,
            String taxNumber,
            org.jooq.JSONB snapshotData,
            OffsetDateTime checkedAt
    ) {}

    /**
     * Update only the {@code checked_at} timestamp of a snapshot — used by the background
     * {@code AsyncIngestor} in demo mode to confirm the scheduling and write-path infrastructure
     * works end-to-end without overwriting the existing snapshot data.
     *
     * <p>This method requires an explicit {@code tenantId} parameter because it is called
     * from a background job that manually sets {@code TenantContext} per partner.
     *
     * @param snapshotId the snapshot to update
     * @param tenantId   explicit tenant ID (background job context)
     * @param checkedAt  the new checked_at timestamp
     */
    public void updateSnapshotCheckedAt(UUID snapshotId, UUID tenantId, OffsetDateTime checkedAt) {
        int rowsAffected = dsl.update(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.CHECKED_AT, checkedAt)
                .set(COMPANY_SNAPSHOTS.UPDATED_AT, OffsetDateTime.now())
                .where(COMPANY_SNAPSHOTS.ID.eq(snapshotId))
                .and(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .execute();
        if (rowsAffected == 0) {
            log.warn("updateSnapshotCheckedAt: 0 rows affected — snapshot may have been deleted. "
                    + "snapshotId={} tenantId={}", snapshotId, tenantId);
        }
    }

    /**
     * Update a snapshot with fresh data from the ingestor — used in live/test mode when
     * real data is returned from the data source adapters.
     *
     * <p>This method requires an explicit {@code tenantId} parameter because it is called
     * from a background job that manually sets {@code TenantContext} per partner.
     *
     * <p>Updates {@code source_urls} and {@code dom_fingerprint_hash} alongside snapshot data
     * to keep provenance and change-detection metadata fresh.
     *
     * @param snapshotId         the snapshot to update
     * @param tenantId           explicit tenant ID (background job context)
     * @param snapshotData       consolidated data map from all adapters (serialized to JSONB)
     * @param sourceUrls         list of source URLs accessed during data retrieval (serialized to JSONB)
     * @param domFingerprintHash SHA-256 hash of concatenated data for change detection; may be null
     * @param checkedAt          timestamp of when data retrieval was performed
     * @param dataSourceMode     the active data source mode (demo/test/live)
     */
    public void updateSnapshotFromIngestor(UUID snapshotId, UUID tenantId,
                                           Map<String, Object> snapshotData,
                                           List<String> sourceUrls,
                                           String domFingerprintHash,
                                           OffsetDateTime checkedAt, String dataSourceMode) {
        try {
            String snapshotDataJson = snapshotData != null
                    ? JSON.writeValueAsString(snapshotData)
                    : "{}";
            String sourceUrlsJson = JSON.writeValueAsString(sourceUrls != null ? sourceUrls : List.of());

            int rowsAffected = dsl.update(COMPANY_SNAPSHOTS)
                    .set(COMPANY_SNAPSHOTS.SNAPSHOT_DATA, JSONB.jsonb(snapshotDataJson))
                    .set(COMPANY_SNAPSHOTS.SOURCE_URLS, JSONB.jsonb(sourceUrlsJson))
                    .set(COMPANY_SNAPSHOTS.DOM_FINGERPRINT_HASH, domFingerprintHash)
                    .set(COMPANY_SNAPSHOTS.CHECKED_AT, checkedAt)
                    .set(COMPANY_SNAPSHOTS.DATA_SOURCE_MODE, dataSourceMode)
                    .set(COMPANY_SNAPSHOTS.UPDATED_AT, OffsetDateTime.now())
                    .where(COMPANY_SNAPSHOTS.ID.eq(snapshotId))
                    .and(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                    .execute();
            if (rowsAffected == 0) {
                log.warn("updateSnapshotFromIngestor: 0 rows affected — snapshot may have been deleted. "
                        + "snapshotId={} tenantId={}", snapshotId, tenantId);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ingestor snapshot data to JSON", e);
        }
    }

    /**
     * Find the most recent snapshot for a given tenant and tax number.
     * Used by the {@code AsyncIngestor} to locate existing snapshots for refresh.
     *
     * <p>This method requires an explicit {@code tenantId} parameter because it is called
     * from a background job that manually sets {@code TenantContext} per partner.
     *
     * @param tenantId  explicit tenant ID (background job context)
     * @param taxNumber normalized tax number
     * @return the most recent snapshot ID, or empty if no snapshot exists
     */
    public Optional<UUID> findLatestSnapshotId(UUID tenantId, String taxNumber) {
        return dsl.select(COMPANY_SNAPSHOTS.ID)
                .from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .orderBy(COMPANY_SNAPSHOTS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(COMPANY_SNAPSHOTS.ID);
    }

    /**
     * Find the latest verdict status for a given tenant and tax number.
     * Used by the WatchlistMonitor (via ScreeningService facade) to read the most recent
     * verdict for comparison against the watchlist entry's stored verdict status.
     *
     * <p>This method requires an explicit {@code tenantId} parameter because it is called
     * from a background job that manually sets {@code TenantContext} per partner.
     *
     * @param tenantId  explicit tenant ID (background job context)
     * @param taxNumber normalized tax number
     * @return the latest verdict status and metadata, or empty if no verdict exists
     */
    public Optional<LatestVerdictRecord> findLatestVerdictByTenantAndTaxNumber(UUID tenantId, String taxNumber) {
        return dsl.select(
                        VERDICTS.ID,
                        VERDICTS.STATUS,
                        VERDICTS.CREATED_AT
                )
                .from(VERDICTS)
                .join(COMPANY_SNAPSHOTS).on(VERDICTS.SNAPSHOT_ID.eq(COMPANY_SNAPSHOTS.ID))
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantId))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .orderBy(VERDICTS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(r -> new LatestVerdictRecord(
                        r.get(VERDICTS.ID),
                        r.get(VERDICTS.STATUS),
                        r.get(VERDICTS.CREATED_AT)
                ));
    }

    /**
     * Internal record for returning latest verdict query results.
     * Used by the WatchlistMonitor to read current verdict status.
     */
    public record LatestVerdictRecord(
            UUID verdictId,
            VerdictStatus status,
            OffsetDateTime createdAt
    ) {}

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
