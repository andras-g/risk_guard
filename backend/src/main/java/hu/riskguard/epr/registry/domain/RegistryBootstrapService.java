package hu.riskguard.epr.registry.domain;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service for the NAV-invoice-driven registry bootstrap flow.
 *
 * <p>Pulls outbound NAV invoices for the tenant's tax number, deduplicates
 * line items into ranked candidates, runs KF-code classification, and
 * presents a triage queue for human approval.
 */
@Service
public class RegistryBootstrapService {

    private final DataSourceService dataSourceService;
    private final KfCodeClassifierService kfCodeClassifierService;
    private final BootstrapRepository bootstrapRepository;
    private final RegistryService registryService;

    public RegistryBootstrapService(DataSourceService dataSourceService,
                                     KfCodeClassifierService kfCodeClassifierService,
                                     BootstrapRepository bootstrapRepository,
                                     RegistryService registryService) {
        this.dataSourceService = dataSourceService;
        this.kfCodeClassifierService = kfCodeClassifierService;
        this.bootstrapRepository = bootstrapRepository;
        this.registryService = registryService;
    }

    // ─── Trigger ─────────────────────────────────────────────────────────────

    @Transactional
    public BootstrapResult triggerBootstrap(UUID tenantId, UUID actingUserId,
                                             LocalDate from, LocalDate to) {
        // 1. Get tenant's own tax number
        String taxNumber = dataSourceService.getTenantTaxNumber(tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No NAV credentials configured"));

        // 2. Fetch outbound invoice summaries
        InvoiceQueryResult queryResult = dataSourceService.queryInvoices(
                taxNumber, from, to, InvoiceDirection.OUTBOUND);
        if (!queryResult.serviceAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "NAV invoice service unavailable");
        }

        // 3 + 4. Fetch details per summary, accumulate deduplicated groups
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

        // 5. Persist new candidates, skip existing dedup keys
        int created = 0;
        int skipped = 0;

        for (DedupeGroup group : groups.values()) {
            String normalizedName = normalize(group.productName());
            String normalizedVtsz = normalize(group.vtsz());
            String storedVtsz = normalizedVtsz.isEmpty() ? null : normalizedVtsz;

            if (bootstrapRepository.existsByTenantAndDedupeKey(tenantId, normalizedName, group.vtsz())) {
                skipped++;
                continue; // 6. do NOT call classifier for skipped
            }

            // Classify only genuinely new candidates
            ClassificationResult classification = kfCodeClassifierService.classify(
                    group.productName(), group.vtsz());

            // P1: ON CONFLICT DO NOTHING absorbs concurrent inserts; check if row was actually inserted
            boolean inserted = bootstrapRepository.insertCandidateIfNew(
                    tenantId,
                    normalizedName,
                    storedVtsz,
                    group.frequency(),
                    group.totalQuantity(),
                    group.unitOfMeasure(),
                    classification
            );
            if (inserted) {
                created++;
            } else {
                skipped++; // lost concurrent race on unique index
            }
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
}
