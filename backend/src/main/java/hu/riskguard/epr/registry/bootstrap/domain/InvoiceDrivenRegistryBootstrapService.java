package hu.riskguard.epr.registry.bootstrap.domain;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.api.dto.PackagingLayerDto;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import hu.riskguard.epr.registry.domain.ComponentUpsertCommand;
import hu.riskguard.epr.registry.domain.ProductStatus;
import hu.riskguard.epr.registry.domain.ProductUpsertCommand;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;
import static hu.riskguard.jooq.Tables.PRODUCTS;

/**
 * Orchestrator for the Story 10.4 "Feltöltés számlák alapján" flow.
 *
 * <p><b>Transaction contract (Story 10.1 retro T4):</b> no method in this class is
 * {@code @Transactional}. NAV HTTP calls and classifier calls run outside any
 * transaction. Persistence happens in per-pair {@code REQUIRES_NEW} transactions via
 * {@link TransactionTemplate}. The {@code NavHttpOutsideTransactionTest} ArchUnit rule
 * enforces this at build time.
 *
 * <p><b>Async execution:</b> {@link #startJob} performs synchronous pre-checks (412/403/409),
 * inserts the job row, and returns the jobId. It then delegates to the private
 * {@link #processJob} method via {@code @Async("taskExecutor")} so the HTTP thread returns
 * immediately with 202. The {@code TenantAwareTaskDecorator} in {@link AsyncConfig}
 * propagates both {@code MDC} and {@code TenantContext} to the async worker thread.
 */
@Service
public class InvoiceDrivenRegistryBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDrivenRegistryBootstrapService.class);

    private static final int CLASSIFIER_CHUNK_SIZE = 100;
    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");

    private final DataSourceService dataSourceService;
    private final ProducerProfileService producerProfileService;
    private final BatchPackagingClassifierService classifierService;
    private final ClassifierUsageService usageService;
    private final BootstrapJobRepository bootstrapJobRepository;
    private final RegistryRepository registryRepository;
    private final AuditService auditService;
    private final DSLContext dsl;
    private final TransactionTemplate requiresNewTx;
    // Self-proxy injection so @Async on processJob is intercepted by Spring AOP proxy
    @Lazy
    @Autowired
    private InvoiceDrivenRegistryBootstrapService self;

    public InvoiceDrivenRegistryBootstrapService(
            DataSourceService dataSourceService,
            ProducerProfileService producerProfileService,
            BatchPackagingClassifierService classifierService,
            ClassifierUsageService usageService,
            BootstrapJobRepository bootstrapJobRepository,
            RegistryRepository registryRepository,
            AuditService auditService,
            DSLContext dsl,
            PlatformTransactionManager transactionManager) {
        this.dataSourceService = dataSourceService;
        this.producerProfileService = producerProfileService;
        this.classifierService = classifierService;
        this.usageService = usageService;
        this.bootstrapJobRepository = bootstrapJobRepository;
        this.registryRepository = registryRepository;
        this.auditService = auditService;
        this.dsl = dsl;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    // ─── Public API: synchronous preamble ────────────────────────────────────

    /**
     * Validates preconditions, creates the job row, and kicks off async processing.
     *
     * @param tenantId       from JWT active_tenant_id (never request body)
     * @param actingUserId   from JWT user_id (nullable for system-triggered jobs)
     * @param periodFrom     null → default last-3-complete-months start
     * @param periodTo       null → default last-3-complete-months end
     * @return the new job's UUID
     * @throws ResponseStatusException 412, 403, or 409 on precondition failures
     */
    public UUID startJob(UUID tenantId, UUID actingUserId,
                         LocalDate periodFrom, LocalDate periodTo) {

        // Resolve default period: last 3 complete calendar months in Europe/Budapest
        LocalDate resolvedFrom = periodFrom != null ? periodFrom : defaultPeriodFrom();
        LocalDate resolvedTo   = periodTo   != null ? periodTo   : defaultPeriodTo();

        // AC #12 — NAV credentials check: structured 412 body { code: NAV_CREDENTIALS_MISSING }.
        // Any exception from the adapter (missing row or decryption failure per AC #12) is mapped
        // to 412: callers cannot recover from a corrupt credential row and must re-enter NAV creds.
        Optional<String> registeredTaxNumber;
        try {
            registeredTaxNumber = dataSourceService.getTenantTaxNumber(tenantId);
        } catch (RuntimeException ex) {
            log.warn("bootstrap preflight: getTenantTaxNumber failed for tenant={}: {}", tenantId, ex.getMessage());
            throw new BootstrapPreconditionException(HttpStatus.PRECONDITION_FAILED,
                    "NAV_CREDENTIALS_MISSING", "NAV credentials missing or not decryptable");
        }
        if (registeredTaxNumber.isEmpty()) {
            throw new BootstrapPreconditionException(HttpStatus.PRECONDITION_FAILED,
                    "NAV_CREDENTIALS_MISSING", "NAV credentials missing or not configured");
        }

        // AC #11 — tenant tax-number ownership. The stored value is resolved via the tenant's
        // own credential row, so the only cross-tenant attack vector here is a misconfigured
        // row that does not satisfy the 8-digit Hungarian adószám prefix invariant. That is
        // the only meaningful check we can enforce without a second source of truth.
        String stored = registeredTaxNumber.get();
        if (stored.length() < 8) {
            throw new BootstrapPreconditionException(HttpStatus.FORBIDDEN,
                    "TAX_NUMBER_MISMATCH", "Tax number does not match tenant's registered tax number");
        }

        // AC #12 — producer profile completeness (ProducerProfileService.get throws 412 if incomplete)
        try {
            producerProfileService.get(tenantId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.PRECONDITION_FAILED) {
                throw new BootstrapPreconditionException(HttpStatus.PRECONDITION_FAILED,
                        "PRODUCER_PROFILE_INCOMPLETE", "Producer profile incomplete");
            }
            throw ex;
        }

        // AC #13 — in-flight guard: structured 409 body { code: ALREADY_RUNNING, jobId }
        Optional<BootstrapJobRecord> inflight = bootstrapJobRepository.findInflightByTenant(tenantId);
        if (inflight.isPresent()) {
            throw BootstrapPreconditionException.alreadyRunning(inflight.get().id());
        }

        // Insert PENDING row via the in-flight guard (partial unique index)
        Optional<UUID> jobId = bootstrapJobRepository.insertIfNoInflight(
                tenantId, resolvedFrom, resolvedTo, actingUserId);
        if (jobId.isEmpty()) {
            // Race: another request won the unique-index race
            BootstrapJobRecord raceWinner = bootstrapJobRepository
                    .findInflightByTenant(tenantId)
                    .orElse(null);
            UUID existingId = raceWinner != null ? raceWinner.id() : null;
            throw BootstrapPreconditionException.alreadyRunning(existingId);
        }

        // Hand off to async worker — @Async kicks in via Spring proxy
        self.processJob(jobId.get(), tenantId, actingUserId, resolvedFrom, resolvedTo,
                registeredTaxNumber.get());

        return jobId.get();
    }

    // ─── Async worker ─────────────────────────────────────────────────────────

    /**
     * Async worker — runs on the shared {@code taskExecutor} pool.
     * {@code TenantAwareTaskDecorator} propagates TenantContext + MDC from the triggering thread.
     *
     * <p>Must NOT be called directly; Spring proxy intercepts {@code @Async}.
     */
    @Async("taskExecutor")
    public void processJob(UUID jobId, UUID tenantId, UUID actingUserId,
                            LocalDate from, LocalDate to, String taxNumber) {
        log.info("bootstrap job {} starting for tenant={}", jobId, tenantId);
        try {
            // Transition to RUNNING. The repository guards against overwriting a terminal
            // state (e.g., an early DELETE that cancelled the job before the async worker
            // woke up), so a zero-rows return means "already cancelled / terminal" and we
            // exit cleanly without doing any work.
            Integer transitioned = requiresNewTx.execute(status ->
                    bootstrapJobRepository.transitionStatus(jobId, BootstrapJobStatus.RUNNING, null));
            if (transitioned == null || transitioned == 0) {
                log.info("bootstrap job {} was already terminal at worker start — skipping", jobId);
                return;
            }

            // Fetch NAV invoices — no transaction held
            InvoiceQueryResult queryResult = dataSourceService.queryInvoices(
                    taxNumber, from, to, InvoiceDirection.OUTBOUND);
            if (!queryResult.serviceAvailable()) {
                failJob(jobId, "NAV service unavailable");
                return;
            }

            // Collect all line items and dedup (in-memory, no tx)
            Map<String, PairEntry> dedupMap = new LinkedHashMap<>();
            for (InvoiceSummary summary : queryResult.summaries()) {
                var detail = dataSourceService.queryInvoiceDetails(summary.invoiceNumber());
                if (detail == null) continue;
                for (InvoiceLineItem item : detail.lineItems()) {
                    String vtsz = item.vtszCode();
                    String desc = item.lineDescription();
                    if (vtsz == null || vtsz.isBlank()) {
                        log.info("bootstrap job {} skipping line: null/blank vtszCode desc={}", jobId, abbreviate(desc));
                        continue;
                    }
                    if (desc == null || desc.isBlank()) {
                        log.info("bootstrap job {} skipping line: null/blank lineDescription vtsz={}", jobId, vtsz);
                        continue;
                    }
                    // Unicode-safe normalization: strip() handles NBSP and other Unicode whitespace,
                    // toLowerCase(Locale.ROOT) avoids locale-dependent casing (Turkish dotless-i).
                    String dedupKey = vtsz + "~" + desc.strip().toLowerCase(Locale.ROOT);
                    dedupMap.putIfAbsent(dedupKey, new PairEntry(vtsz, desc));
                }
            }

            List<PairEntry> uniquePairs = new ArrayList<>(dedupMap.values());

            // Update total_pairs in a small REQUIRES_NEW tx
            requiresNewTx.execute(status -> {
                bootstrapJobRepository.setTotalPairs(jobId, uniquePairs.size());
                return null;
            });

            if (uniquePairs.isEmpty()) {
                completeJob(jobId, 0, 0, List.of());
                return;
            }

            // Check cap before dispatching — abort if insufficient
            int monthlyCap = usageService.getMonthlyCap();
            int used = usageService.getCurrentMonthCallCount(tenantId);
            int remaining = Math.max(0, monthlyCap - used);
            if (remaining < uniquePairs.size()) {
                failJob(jobId, "AI classifier monthly cap exceeded (" + remaining + " pairs remaining, "
                        + uniquePairs.size() + " pairs needed)");
                return;
            }

            // Process in chunks of CLASSIFIER_CHUNK_SIZE
            int totalFailed = 0;
            int chunkStart = 0;
            List<String> failureReasons = new ArrayList<>();
            while (chunkStart < uniquePairs.size()) {
                // Check for cancellation between chunks
                Optional<BootstrapJobStatus> currentStatus = bootstrapJobRepository.readStatus(jobId);
                if (currentStatus.isEmpty() || currentStatus.get() == BootstrapJobStatus.CANCELLED) {
                    log.info("bootstrap job {} was cancelled after {} pairs", jobId, chunkStart);
                    return;
                }

                int chunkEnd = Math.min(chunkStart + CLASSIFIER_CHUNK_SIZE, uniquePairs.size());
                List<PairEntry> chunk = uniquePairs.subList(chunkStart, chunkEnd);

                List<BatchPackagingRequest.PairRequest> pairRequests = chunk.stream()
                        .map(p -> new BatchPackagingRequest.PairRequest(p.vtsz(), p.description()))
                        .toList();

                // Classifier call — no transaction
                List<BatchPackagingResult> results;
                try {
                    results = classifierService.classify(pairRequests, tenantId);
                } catch (Exception ex) {
                    log.error("bootstrap job {} classifier failed on chunk [{},{}): {}",
                            jobId, chunkStart, chunkEnd, ex.getMessage());
                    failJob(jobId, "AI classifier error: " + ex.getMessage());
                    return;
                }

                // AC #32 — misaligned classifier output would silently write wrong packaging
                // data to the wrong pair. Abort the job if the classifier returned a different
                // number of results than pairs we sent.
                if (results.size() != chunk.size()) {
                    log.error("bootstrap job {} classifier returned {} results for {} pairs on chunk [{},{})",
                            jobId, results.size(), chunk.size(), chunkStart, chunkEnd);
                    failJob(jobId, "AI classifier result count mismatch: expected "
                            + chunk.size() + " got " + results.size());
                    return;
                }

                // Persist each pair in its own REQUIRES_NEW transaction
                int chunkClassified = 0, chunkFallback = 0, chunkUnresolved = 0;
                int chunkCreated = 0, chunkDeleted = 0;
                int chunkPairFailed = 0;

                for (int i = 0; i < results.size(); i++) {
                    BatchPackagingResult result = results.get(i);
                    PairEntry pair = chunk.get(i);
                    // Tally strategy counters from the classifier result BEFORE attempting
                    // persist; a DB failure must not erase the fact that the classifier
                    // produced (say) a VTSZ_FALLBACK answer.
                    boolean isFallback = BatchPackagingResult.STRATEGY_VTSZ_FALLBACK.equals(result.classificationStrategy());
                    boolean isUnresolved = BatchPackagingResult.STRATEGY_UNRESOLVED.equals(result.classificationStrategy());
                    try {
                        int[] counters = persistPair(jobId, tenantId, actingUserId, pair, result);
                        chunkClassified++;
                        if (isFallback) chunkFallback++;
                        else if (isUnresolved) chunkUnresolved++;
                        chunkCreated += counters[0];
                        chunkDeleted += counters[1];
                    } catch (Exception ex) {
                        String reason = "vtsz=" + pair.vtsz() + ": " + ex.getMessage();
                        log.warn("bootstrap job {} pair failed {}", jobId, reason);
                        chunkPairFailed++;
                        if (failureReasons.size() < 3) failureReasons.add(reason);
                    }
                }

                totalFailed += chunkPairFailed;

                // Update counters in REQUIRES_NEW tx
                final int fc = chunkClassified, ff = chunkFallback, fu = chunkUnresolved,
                        fc2 = chunkCreated, fd = chunkDeleted;
                requiresNewTx.execute(status -> {
                    bootstrapJobRepository.incrementCounters(jobId, fc, ff, fu, fc2, fd);
                    return null;
                });

                chunkStart = chunkEnd;
            }

            completeJob(jobId, totalFailed, uniquePairs.size(), failureReasons);

        } catch (Exception ex) {
            log.error("bootstrap job {} unexpected failure: {}", jobId, ex.getMessage(), ex);
            failJob(jobId, "Unexpected error: " + ex.getMessage());
        }
    }

    // ─── Per-pair persistence ─────────────────────────────────────────────────

    /**
     * Persist one classified pair in its own REQUIRES_NEW transaction.
     * Returns [createdCount, deletedCount].
     */
    private int[] persistPair(UUID jobId, UUID tenantId, UUID actingUserId,
                               PairEntry pair, BatchPackagingResult result) {
        return requiresNewTx.execute(status -> {
            List<FieldChangeEvent> auditEvents = new ArrayList<>();
            int created = 0;
            int deleted = 0;

            // AC #21 — overwrite: delete existing (tenant_id, vtsz, name) row if any
            // "name" is the raw invoice description (NOT normalized form)
            List<UUID> existingIds = dsl.select(PRODUCTS.ID)
                    .from(PRODUCTS)
                    .where(PRODUCTS.TENANT_ID.eq(tenantId))
                    .and(PRODUCTS.VTSZ.eq(pair.vtsz()))
                    .and(PRODUCTS.NAME.eq(pair.description()))
                    .and(PRODUCTS.STATUS.ne(ProductStatus.ARCHIVED.name()))
                    .fetch(PRODUCTS.ID);

            for (UUID existingId : existingIds) {
                // Delete components first (they reference products)
                registryRepository.deleteComponentsForProduct(existingId, tenantId);
                // Defence-in-depth: TENANT_ID predicate on the DELETE even though existingId
                // was resolved via a tenant-scoped SELECT above.
                dsl.deleteFrom(PRODUCTS)
                        .where(PRODUCTS.ID.eq(existingId))
                        .and(PRODUCTS.TENANT_ID.eq(tenantId))
                        .execute();

                auditEvents.add(new FieldChangeEvent(
                        existingId, tenantId, "bootstrap.deleted",
                        "vtsz=" + pair.vtsz() + "|name=" + pair.description() + "|source=OVERWRITE",
                        null, actingUserId, AuditSource.NAV_BOOTSTRAP, null, null));
                deleted++;
            }

            // AC #20 — tagging rules based on classificationStrategy
            String strategy = result.classificationStrategy();
            AuditSource componentSource;
            String reviewState = null;

            if (BatchPackagingResult.STRATEGY_GEMINI.equals(strategy)) {
                componentSource = AuditSource.AI_SUGGESTED_CONFIRMED;
            } else if (BatchPackagingResult.STRATEGY_VTSZ_FALLBACK.equals(strategy)) {
                componentSource = AuditSource.VTSZ_FALLBACK;
            } else {
                // UNRESOLVED — zero components, MISSING_PACKAGING review_state
                componentSource = AuditSource.NAV_BOOTSTRAP;
                reviewState = "MISSING_PACKAGING";
            }

            // Build components from classifier layers. VTSZ_FALLBACK layers legitimately
            // carry null weight (see PackagingLayerDto.from: rule-based fallback does not
            // estimate weight). The DB column is NOT NULL with CHECK (>= 0), so we coerce
            // null → BigDecimal.ZERO and let the existing MISSING_PACKAGING review flag
            // direct users to re-estimate weight manually.
            List<ComponentUpsertCommand> components = new ArrayList<>();
            if (result.layers() != null) {
                for (PackagingLayerDto layer : result.layers()) {
                    BigDecimal weight = layer.weightEstimateKg() != null
                            ? layer.weightEstimateKg()
                            : BigDecimal.ZERO;
                    components.add(new ComponentUpsertCommand(
                            null,
                            layer.description(),
                            layer.kfCode(),
                            weight,
                            layer.level() - 1,  // component_order is zero-based
                            layer.itemsPerParent() > 0 ? BigDecimal.valueOf(layer.itemsPerParent()) : BigDecimal.ONE,
                            layer.level(),       // wrapping_level = 1, 2, or 3
                            null,                // no materialTemplateId for bootstrap
                            null, null, null, null, null,
                            componentSource.name(),
                            strategy,
                            result.modelVersion()
                    ));
                }
            }

            // A VTSZ_FALLBACK pair with zero-weight components is effectively missing
            // quantitative data — flag for manual review so the user knows to enter weights.
            if (componentSource == AuditSource.VTSZ_FALLBACK
                    && !components.isEmpty()
                    && components.stream().allMatch(c -> c.weightPerUnitKg().signum() == 0)) {
                reviewState = "MISSING_PACKAGING";
            }

            ProductUpsertCommand cmd = new ProductUpsertCommand(
                    null,                       // no articleNumber for bootstrapped products
                    pair.description(),         // raw invoice lineDescription as name
                    pair.vtsz(),
                    null,                       // no primaryUnit from invoice
                    ProductStatus.ACTIVE,
                    components
            );

            // Insert product — PRODUCTS.REVIEW_STATE is the generated jOOQ TableField,
            // so the column name is compile-time-checked.
            UUID newProductId = dsl.insertInto(PRODUCTS)
                    .set(PRODUCTS.TENANT_ID, tenantId)
                    .set(PRODUCTS.NAME, cmd.name())
                    .set(PRODUCTS.VTSZ, cmd.vtsz())
                    .set(PRODUCTS.STATUS, ProductStatus.ACTIVE.name())
                    .set(PRODUCTS.REVIEW_STATE, reviewState)
                    .returning(PRODUCTS.ID)
                    .fetchOne(PRODUCTS.ID);

            if (newProductId == null) {
                throw new IllegalStateException("Failed to insert product for pair " + pair.vtsz());
            }

            // Insert components
            for (ComponentUpsertCommand comp : components) {
                registryRepository.insertComponent(newProductId, tenantId, comp);
            }

            // Build audit event for the new product
            auditEvents.add(new FieldChangeEvent(
                    newProductId, tenantId, "bootstrap.created",
                    null,
                    "vtsz=" + pair.vtsz() + "|name=" + pair.description() + "|source=" + componentSource.name(),
                    actingUserId, AuditSource.NAV_BOOTSTRAP, strategy, result.modelVersion()));
            created++;

            // AC #22 — flush audit events inside this REQUIRES_NEW transaction
            auditService.recordRegistryBootstrapBatch(auditEvents);

            return new int[]{created, deleted};
        });
    }

    // ─── Terminal transitions ─────────────────────────────────────────────────

    /** Upper bound matches the VARCHAR(1000) ceiling on epr_bootstrap_jobs.error_message. */
    private static final int ERROR_MESSAGE_MAX = 999;

    private void completeJob(UUID jobId, int totalFailed, int totalPairs, List<String> failureReasons) {
        requiresNewTx.execute(status -> {
            if (totalFailed == 0) {
                bootstrapJobRepository.transitionStatus(jobId, BootstrapJobStatus.COMPLETED, null);
            } else if (totalFailed < totalPairs) {
                // AC #16: "N/M pairs failed: <first-3-reasons>"
                String reasonSuffix = failureReasons.isEmpty() ? ""
                        : ": " + String.join("; ", failureReasons);
                String msg = truncate(totalFailed + "/" + totalPairs + " pairs failed" + reasonSuffix);
                bootstrapJobRepository.transitionStatus(jobId, BootstrapJobStatus.FAILED_PARTIAL, msg);
            } else {
                String reasonSuffix = failureReasons.isEmpty() ? ""
                        : ": " + String.join("; ", failureReasons);
                String msg = truncate("All " + totalPairs + " pairs failed" + reasonSuffix);
                bootstrapJobRepository.transitionStatus(jobId, BootstrapJobStatus.FAILED, msg);
            }
            return null;
        });
        log.info("bootstrap job {} completed (failed={}/{})", jobId, totalFailed, totalPairs);
    }

    private void failJob(UUID jobId, String errorMessage) {
        String safeMessage = truncate(errorMessage);
        requiresNewTx.execute(status -> {
            bootstrapJobRepository.transitionStatus(jobId, BootstrapJobStatus.FAILED, safeMessage);
            return null;
        });
        log.warn("bootstrap job {} failed: {}", jobId, safeMessage);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_MESSAGE_MAX ? s : s.substring(0, ERROR_MESSAGE_MAX);
    }

    // ─── Default period helpers ───────────────────────────────────────────────

    LocalDate defaultPeriodFrom() {
        YearMonth threeMonthsAgo = YearMonth.now(BUDAPEST).minusMonths(3);
        return threeMonthsAgo.atDay(1);
    }

    LocalDate defaultPeriodTo() {
        YearMonth lastCompleteMonth = YearMonth.now(BUDAPEST).minusMonths(1);
        return lastCompleteMonth.atEndOfMonth();
    }

    // ─── Dedup helpers ────────────────────────────────────────────────────────

    record PairEntry(String vtsz, String description) {}

    private static String abbreviate(String s) {
        if (s == null) return "<null>";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
