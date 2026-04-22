package hu.riskguard.epr.audit;

import hu.riskguard.epr.audit.events.EprScopeChangeEvent;
import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.audit.internal.AggregationAuditRepository;
import hu.riskguard.epr.audit.internal.RegistryAuditRepository;
import io.micrometer.core.instrument.Counter;

import java.time.LocalDate;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Single entry point for every audit-row read and write inside the EPR module.
 *
 * <p>Per ADR-0003, no class outside {@code hu.riskguard.epr.audit} may depend on
 * {@link RegistryAuditRepository} (nor, when Story 10.8 ships, {@code AggregationAuditRepository}).
 * All audit access routes through this service.
 *
 * <p><b>Transactional contract:</b> this service does NOT declare {@code @Transactional}.
 * It inherits the caller's transaction so writes commit atomically with the mutation
 * that prompted them. Marking this class {@code @Transactional} would re-couple with
 * the forbidden pattern removed in the Story 10.1 tx-pool refactor. The ArchUnit rule
 * {@code audit_service_is_the_facade} enforces this invariant at build time.
 *
 * <p><b>Tenant-scope contract for read methods:</b> {@link #listRegistryEntryAudit(UUID, UUID, int, int)}
 * and {@link #countRegistryEntryAudit(UUID, UUID)} DO NOT verify that the product
 * belongs to the tenant. The caller MUST invoke the registry's tenant-scoped lookup
 * (e.g., {@code RegistryService.listAuditLog} pre-verifies via {@code findProductByIdAndTenant})
 * BEFORE calling these methods. This is a deliberate design choice (ADR-0003 code-review
 * 2026-04-17, decision D2) to avoid double-work in the common path where the caller
 * has already loaded the product.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 500;
    private static final int BATCH_FLUSH_SIZE = 500;

    private final RegistryAuditRepository registryAuditRepository;
    private final AggregationAuditRepository aggregationAuditRepository;
    private final Map<AuditSource, Counter> writeCounters;

    public AuditService(RegistryAuditRepository registryAuditRepository,
                        AggregationAuditRepository aggregationAuditRepository,
                        MeterRegistry meterRegistry) {
        this.registryAuditRepository = registryAuditRepository;
        this.aggregationAuditRepository = aggregationAuditRepository;
        this.writeCounters = new EnumMap<>(AuditSource.class);
        for (AuditSource s : AuditSource.values()) {
            writeCounters.put(s, Counter.builder("audit.writes")
                    .description("Count of audit rows written via AuditService, tagged by source.")
                    .tag("source", s.name())
                    .register(meterRegistry));
        }
    }

    // ─── Registry-entry audit — writes ───────────────────────────────────────

    /**
     * Record a single field-level change. The compact constructor of
     * {@link FieldChangeEvent} already enforces null-guards on {@code productId},
     * {@code tenantId}, {@code source}, and a non-blank {@code fieldChanged}.
     * Rejects a null event up-front so a caller mistake fails before DB contact.
     */
    public void recordRegistryFieldChange(FieldChangeEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        registryAuditRepository.insertAuditRow(
                event.productId(),
                event.tenantId(),
                event.fieldChanged(),
                event.oldValue(),
                event.newValue(),
                event.changedByUserId(),
                event.source(),
                event.classificationStrategy(),
                event.modelVersion()
        );
        writeCounters.get(event.source()).increment();
    }

    /**
     * Bulk-write path for Story 10.4 NAV bootstrap (ADR-0003 §"Batch-write path for Story 10.4").
     *
     * <p>Uses jOOQ's batched-connection pattern: events are flushed in sub-batches of
     * {@value #BATCH_FLUSH_SIZE} rows, producing one JDBC round-trip per sub-batch (NOT one
     * round-trip per row). Do NOT use {@code DSLContext.batchInsert()} — it holds a
     * {@code PreparedStatement} open for the entire collection and is significantly slower
     * at 500+ rows (ADR-0003 §"Batch-write path" note).
     *
     * <p><b>Transactional contract:</b> inherits the caller's transaction, same as
     * {@link #recordRegistryFieldChange}. Each per-pair REQUIRES_NEW transaction calls this
     * method inside the tx boundary so audit rows commit atomically with the registry write.
     *
     * @param events list of events; null or empty → no-op
     */
    public void recordRegistryBootstrapBatch(List<FieldChangeEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (int i = 0; i < events.size(); i += BATCH_FLUSH_SIZE) {
            List<FieldChangeEvent> subBatch = events.subList(i, Math.min(i + BATCH_FLUSH_SIZE, events.size()));
            registryAuditRepository.insertAuditRowBatch(subBatch);
            subBatch.forEach(e -> writeCounters.get(e.source()).increment());
            log.debug("audit-batch flush: {} rows (offset {})", subBatch.size(), i);
        }
    }

    // ─── Story 10.11: EPR-scope audit writes ─────────────────────────────────

    /**
     * Record a single product {@code epr_scope} change — Story 10.11 AC #12.
     *
     * <p>Persists a {@code registry_entry_audit_log} row with
     * {@code field_changed='epr_scope'}, {@code old_value=fromScope}, {@code new_value=toScope}
     * (Deviation D1 in the story's Dev Agent Record: no separate {@code change_type} column).
     * Source is {@link AuditSource#MANUAL} — scope changes are always user-initiated.
     *
     * <p>The caller must ensure the product belongs to {@code tenantId} BEFORE calling this.
     */
    public void recordEprScopeChanged(UUID productId, UUID tenantId,
                                       String fromScope, String toScope, UUID userId) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        registryAuditRepository.insertAuditRow(
                productId, tenantId,
                "epr_scope",
                fromScope, toScope,
                userId,
                AuditSource.MANUAL);
        writeCounters.get(AuditSource.MANUAL).increment();
    }

    /**
     * Record a company-wide {@code default_epr_scope} change — Story 10.11 AC #12.
     *
     * <p>This is a tenant-scoped (no productId) event so it lands as a structured log entry
     * rather than a {@code registry_entry_audit_log} row (the existing schema requires a
     * non-null {@code product_id}). Storage as a log line keeps the audit trail visible in
     * standard ops tooling without inventing a new table just for a one-off preference change.
     */
    public void recordDefaultEprScopeChanged(UUID tenantId, String fromScope, String toScope, UUID userId) {
        log.info("[audit source={} event=default_epr_scope_changed tenant={} user={} from={} to={}]",
                AuditSource.MANUAL, tenantId, userId, fromScope, toScope);
        writeCounters.get(AuditSource.MANUAL).increment();
    }

    /**
     * Record a batch of {@code epr_scope} changes — Story 10.11 AC #12, bulk PATCH path.
     *
     * <p>Uses the batched-connection path inherited from Story 10.4's bootstrap pattern so 500
     * events produce roughly one JDBC round-trip. Empty / null event lists are a no-op.
     */
    public void recordEprScopeChangedBatch(UUID tenantId, List<EprScopeChangeEvent> events, UUID userId) {
        if (events == null || events.isEmpty()) return;
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<FieldChangeEvent> fieldEvents = new ArrayList<>(events.size());
        for (EprScopeChangeEvent e : events) {
            fieldEvents.add(new FieldChangeEvent(
                    e.productId(), tenantId,
                    "epr_scope",
                    e.fromScope(), e.toScope(),
                    userId,
                    AuditSource.MANUAL,
                    null, null));
        }
        recordRegistryBootstrapBatch(fieldEvents);
    }

    // ─── Aggregation audit ───────────────────────────────────────────────────

    /**
     * Record that an aggregation run completed (Story 10.5 AC #9; Story 10.8 AC #14 adds DB persist).
     *
     * <p>Structured log + DB row in {@code aggregation_audit_log} with event_type=AGGREGATION_RUN.
     */
    public void recordAggregationRun(UUID tenantId, LocalDate periodStart, LocalDate periodEnd,
                                      long durationMs, int resolvedCount, int unresolvedCount) {
        log.info("[audit source={}] aggregation_run tenant={} period={}/{} duration_ms={} resolved={} unresolved={}",
                AuditSource.EPR_AGGREGATION, tenantId, periodStart, periodEnd,
                durationMs, resolvedCount, unresolvedCount);
        aggregationAuditRepository.insertAggregationRun(
                tenantId, periodStart, periodEnd, durationMs, resolvedCount, unresolvedCount);
        writeCounters.get(AuditSource.EPR_AGGREGATION).increment();
    }

    /**
     * Record that a user fetched a provenance page (Story 10.8 AC #15).
     *
     * <p>Persists event_type=PROVENANCE_FETCH to {@code aggregation_audit_log}.
     * Called from the controller after a successful response (ADR-0003 §caller-initiates pattern).
     */
    public void recordProvenanceFetch(UUID tenantId, UUID userId,
                                      LocalDate periodStart, LocalDate periodEnd,
                                      int page, int pageSize) {
        aggregationAuditRepository.insertProvenanceFetch(tenantId, userId, periodStart, periodEnd, page, pageSize);
    }

    /**
     * Record that a user triggered a CSV export (Story 10.8 AC #15).
     *
     * <p>Persists event_type=CSV_EXPORT to {@code aggregation_audit_log}.
     * Called from the controller after a successful response (ADR-0003 §caller-initiates pattern).
     */
    public void recordCsvExport(UUID tenantId, UUID userId,
                                 LocalDate periodStart, LocalDate periodEnd) {
        aggregationAuditRepository.insertCsvExport(tenantId, userId, periodStart, periodEnd);
    }

    /**
     * Record that a user downloaded a past OKIRkapu XML submission (Story 10.9 AC #12).
     *
     * <p>Persists event_type=SUBMISSION_DOWNLOAD to {@code aggregation_audit_log}.
     * Called from the controller after a successful response (ADR-0003 §caller-initiates pattern).
     */
    public void recordSubmissionDownload(UUID tenantId, UUID userId, UUID submissionId) {
        aggregationAuditRepository.insertSubmissionDownload(tenantId, userId, submissionId);
        writeCounters.get(AuditSource.EPR_AGGREGATION).increment();
    }

    // ─── Registry-entry audit — reads ────────────────────────────────────────

    /**
     * Page through audit rows for a product, newest first.
     *
     * <p><b>Tenant-scope:</b> this method does NOT verify product-tenant membership.
     * See class-level Javadoc for the caller-side contract.
     *
     * <p>{@code page} is clamped to {@code >= 0}; {@code size} is clamped to
     * {@code [1, 500]} to protect the DB from unbounded fetches.
     */
    public List<RegistryAuditEntry> listRegistryEntryAudit(UUID productId, UUID tenantId,
                                                             int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return registryAuditRepository.listAuditByProduct(productId, tenantId, safePage, safeSize);
    }

    /**
     * Total audit-row count for a product.
     *
     * <p><b>Tenant-scope:</b> this method does NOT verify product-tenant membership.
     * See class-level Javadoc for the caller-side contract.
     */
    public long countRegistryEntryAudit(UUID productId, UUID tenantId) {
        return registryAuditRepository.countAuditByProduct(productId, tenantId);
    }
}
