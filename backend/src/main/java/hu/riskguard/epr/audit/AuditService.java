package hu.riskguard.epr.audit;

import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.audit.internal.RegistryAuditRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final Map<AuditSource, Counter> writeCounters;

    public AuditService(RegistryAuditRepository registryAuditRepository,
                        MeterRegistry meterRegistry) {
        this.registryAuditRepository = registryAuditRepository;
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
