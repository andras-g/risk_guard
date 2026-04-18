package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.internal.BootstrapRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service for the NAV-invoice-driven registry bootstrap flow.
 *
 * <p>Pulls outbound NAV invoices for the tenant's tax number, deduplicates
 * line items into ranked candidates, runs KF-code classification, and
 * presents a triage queue for human approval.
 *
 * <p><b>Transaction boundaries (Story 10.1 — retro T4 refactor):</b> NAV HTTP
 * ({@link DataSourceService#queryInvoices},
 * {@link DataSourceService#queryInvoiceDetails},
 * {@link DataSourceService#getTenantTaxNumber}) and AI classifier calls
 * ({@link KfCodeClassifierService#classify}) run <em>outside</em> any
 * transaction. Persistence is chunked into per-batch short transactions of
 * {@link #BOOTSTRAP_INSERT_BATCH_SIZE} rows via {@link TransactionTemplate} with
 * {@code PROPAGATION_REQUIRES_NEW}. Rationale: at Story 10.4's target scale
 * (~3000 invoices × ~3 s NAV latency), a method-wide {@code @Transactional}
 * holds one Hikari connection per tenant for the entire bootstrap and saturates
 * the default pool (size 10) on the second concurrent run. Splitting the
 * connection lifetime to just the insert loop keeps the peak active count at
 * ≪ pool size regardless of NAV latency.
 */
@Service
public class RegistryBootstrapService {

    /** Size of each per-batch {@code REQUIRES_NEW} transaction in {@link #triggerBootstrap}. */
    private static final int BOOTSTRAP_INSERT_BATCH_SIZE = 50;

    private final DataSourceService dataSourceService;
    private final KfCodeClassifierService kfCodeClassifierService;
    private final BootstrapRepository bootstrapRepository;
    private final RegistryService registryService;
    private final TransactionTemplate insertBatchTxTemplate;

    public RegistryBootstrapService(DataSourceService dataSourceService,
                                     KfCodeClassifierService kfCodeClassifierService,
                                     BootstrapRepository bootstrapRepository,
                                     RegistryService registryService,
                                     PlatformTransactionManager transactionManager) {
        this.dataSourceService = dataSourceService;
        this.kfCodeClassifierService = kfCodeClassifierService;
        this.bootstrapRepository = bootstrapRepository;
        this.registryService = registryService;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.insertBatchTxTemplate = tx;
    }

    // ─── Trigger ─────────────────────────────────────────────────────────────

    /**
     * Orchestrates the invoice-driven bootstrap: fetch → dedup → classify → insert.
     * NAV HTTP and classifier calls run with NO transaction. Persistence uses
     * per-batch {@code REQUIRES_NEW} transactions of {@link #BOOTSTRAP_INSERT_BATCH_SIZE}
     * candidates. See class-level Javadoc for rationale.
     */
    public BootstrapResult triggerBootstrap(UUID tenantId, UUID actingUserId,
                                             LocalDate from, LocalDate to) {
        // 1. Get tenant's own tax number (NAV HTTP — outside any tx)
        String taxNumber = dataSourceService.getTenantTaxNumber(tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No NAV credentials configured"));

        // 2. Fetch outbound invoice summaries (NAV HTTP — outside any tx)
        InvoiceQueryResult queryResult = dataSourceService.queryInvoices(
                taxNumber, from, to, InvoiceDirection.OUTBOUND);
        if (!queryResult.serviceAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "NAV invoice service unavailable");
        }

        // 3 + 4. Fetch details per summary and accumulate dedup groups (NAV HTTP — outside any tx)
        // Key: normalize(productName) + "/" + normalize(vtsz)
        Map<String, DedupeGroup> groups = new LinkedHashMap<>();

        for (InvoiceSummary summary : queryResult.summaries()) {
            InvoiceDetail detail = dataSourceService.queryInvoiceDetails(summary.invoiceNumber());
            for (InvoiceLineItem item : detail.lineItems()) {
                if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // skip zero/null quantity lines
                }
                if (normalize(item.lineDescription()).isEmpty()) {
                    continue; // P8: skip lines with blank/null product names — they collide on key "/"
                }
                String key = dedupeKey(item.lineDescription(), item.vtszCode());
                groups.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return new DedupeGroup(
                                item.lineDescription(),
                                item.vtszCode(),
                                item.quantity(),
                                1,
                                item.unitOfMeasure()
                        );
                    } else {
                        return existing.merge(item.quantity());
                    }
                });
            }
        }

        // 5. Chunked persistence: classifier call outside tx; insert loop inside REQUIRES_NEW tx.
        List<DedupeGroup> allGroups = new ArrayList<>(groups.values());
        int created = 0;
        int skipped = 0;
        for (int offset = 0; offset < allGroups.size(); offset += BOOTSTRAP_INSERT_BATCH_SIZE) {
            int end = Math.min(offset + BOOTSTRAP_INSERT_BATCH_SIZE, allGroups.size());
            List<DedupeGroup> batch = allGroups.subList(offset, end);

            // Pre-classify already-persisted candidates out so we don't spend AI tokens on dupes.
            List<ClassifiedGroup> toPersist = new ArrayList<>(batch.size());
            for (DedupeGroup g : batch) {
                String normalizedName = normalize(g.productName());
                if (bootstrapRepository.existsByTenantAndDedupeKey(tenantId, normalizedName, g.vtsz())) {
                    skipped++;
                    continue; // 6. do NOT call classifier for skipped
                }
                // Classifier runs OUTSIDE the tx (no DB connection held)
                ClassificationResult classification = kfCodeClassifierService.classify(
                        g.productName(), g.vtsz());
                toPersist.add(new ClassifiedGroup(g, normalizedName, classification));
            }

            if (toPersist.isEmpty()) continue;

            // Short REQUIRES_NEW tx — connection held only for the insert loop
            int[] batchCounts = insertBatchTxTemplate.execute(status -> {
                int c = 0;
                int sk = 0;
                for (ClassifiedGroup cg : toPersist) {
                    DedupeGroup g = cg.group();
                    String normalizedVtsz = normalize(g.vtsz());
                    String storedVtsz = normalizedVtsz.isEmpty() ? null : normalizedVtsz;
                    // P1: ON CONFLICT DO NOTHING absorbs concurrent inserts.
                    boolean inserted = bootstrapRepository.insertCandidateIfNew(
                            tenantId,
                            cg.normalizedName(),
                            storedVtsz,
                            g.frequency(),
                            g.totalQuantity(),
                            g.unitOfMeasure(),
                            cg.classification()
                    );
                    if (inserted) c++; else sk++; // lost concurrent race on unique index
                }
                return new int[]{c, sk};
            });
            created += batchCounts[0];
            skipped += batchCounts[1];
        }

        return new BootstrapResult(created, skipped);
    }

    // ─── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BootstrapCandidatesPage listCandidates(UUID tenantId, BootstrapTriageFilter filter,
                                                   int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), 200);

        var items = bootstrapRepository.listByTenantWithFilter(tenantId, filter, clampedPage, clampedSize);
        long total = bootstrapRepository.countByTenantWithFilter(tenantId, filter);

        return new BootstrapCandidatesPage(items, total, clampedPage, clampedSize);
    }

    // ─── Approve ─────────────────────────────────────────────────────────────

    @Transactional
    public BootstrapCandidate approveCandidateAndCreateProduct(UUID tenantId, UUID candidateId,
                                                                UUID actingUserId,
                                                                ApproveCommand cmd) {
        // 1. Load candidate; guard cross-tenant and missing
        BootstrapCandidate candidate = bootstrapRepository.findByIdAndTenant(candidateId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bootstrap candidate not found: " + candidateId));

        // 2. Guard non-PENDING status
        if (candidate.status() != BootstrapCandidateStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate is not in PENDING status: " + candidate.status());
        }

        // 3. Create registry product (NAV_BOOTSTRAP source, human actingUserId)
        Product product = registryService.create(
                tenantId, actingUserId, cmd.toProductUpsertCommand(), AuditSource.NAV_BOOTSTRAP);

        // 4. Update candidate status — P2: require PENDING in WHERE to detect concurrent approve races
        int updated = bootstrapRepository.updateCandidateStatus(
                tenantId, candidateId, BootstrapCandidateStatus.APPROVED, product.id(),
                BootstrapCandidateStatus.PENDING);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate was already processed by a concurrent request");
        }

        // 5. Return updated candidate
        return bootstrapRepository.findByIdAndTenant(candidateId, tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Candidate disappeared after approve: " + candidateId));
    }

    // ─── Reject ──────────────────────────────────────────────────────────────

    @Transactional
    public void rejectCandidate(UUID tenantId, UUID candidateId, UUID actingUserId,
                                 BootstrapCandidateStatus targetStatus) {
        if (targetStatus != BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING
                && targetStatus != BootstrapCandidateStatus.NEEDS_MANUAL_ENTRY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rejection target status must be REJECTED_NOT_OWN_PACKAGING or NEEDS_MANUAL_ENTRY");
        }

        BootstrapCandidate candidate = bootstrapRepository.findByIdAndTenant(candidateId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bootstrap candidate not found: " + candidateId));

        if (candidate.status() != BootstrapCandidateStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate is not in PENDING status: " + candidate.status());
        }

        int updated = bootstrapRepository.updateCandidateStatus(
                tenantId, candidateId, targetStatus, null, BootstrapCandidateStatus.PENDING);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate was already processed by a concurrent request");
        }
    }

    // ─── Normalization ────────────────────────────────────────────────────────

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private static String dedupeKey(String productName, String vtsz) {
        return normalize(productName) + "/" + normalize(vtsz);
    }

    // ─── Inner types ─────────────────────────────────────────────────────────

    private record DedupeGroup(
            String productName,
            String vtsz,
            BigDecimal totalQuantity,
            int frequency,
            String unitOfMeasure
    ) {
        DedupeGroup merge(BigDecimal additionalQty) {
            return new DedupeGroup(
                    productName, vtsz,
                    totalQuantity.add(additionalQty != null ? additionalQty : BigDecimal.ZERO),
                    frequency + 1,
                    unitOfMeasure
            );
        }
    }

    private record ClassifiedGroup(
            DedupeGroup group,
            String normalizedName,
            ClassificationResult classification
    ) {}
}
