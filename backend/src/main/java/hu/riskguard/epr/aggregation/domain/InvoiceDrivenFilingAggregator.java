package hu.riskguard.epr.aggregation.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.aggregation.api.dto.AggregationMetadata;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.aggregation.api.dto.ProvenanceTag;
import hu.riskguard.epr.aggregation.api.dto.SoldProductLine;
import hu.riskguard.epr.aggregation.api.UnresolvedReason;
import hu.riskguard.epr.aggregation.api.dto.UnresolvedInvoiceLine;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * Invoice-driven EPR filing aggregator (Story 10.5).
 *
 * <p>Walks invoice lines → Registry products → multi-layer packaging components to compute
 * per-KF-code EPR weight and fee totals. Replaces the manual {@code EprService.calculateFiling}.
 *
 * <p><b>No {@code @Transactional}</b> — reads run in auto-commit per ADR-0003.
 * <p><b>No {@code double}/{@code float}</b> — all arithmetic uses BigDecimal + DECIMAL64 (T3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceDrivenFilingAggregator {

    static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal OVERFLOW_THRESHOLD = new BigDecimal("100000000");
    private static final BigDecimal MAX_WEIGHT_PER_UNIT = new BigDecimal("10000");
    private static final BigDecimal MAX_ITEMS_PER_PARENT = new BigDecimal("10000");

    private final RegistryRepository registryRepository;
    private final DataSourceService dataSourceService;
    private final EprService eprService;
    private final AuditService auditService;

    // Direct Caffeine cache — no Spring @EnableCaching (per AC #14, consistent with EprService.configCache)
    private final Cache<AggregationCacheKey, FilingAggregationResult> cache =
            Caffeine.newBuilder()
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(200)
                    .build();

    /**
     * Test-only: current cache entry count.
     * Package-private so tests in the same package can assert cache state without exposing the
     * cache to other production beans. Do NOT call from production code.
     */
    long cacheSizeForTest() {
        return cache.estimatedSize();
    }

    /**
     * Test-only: drop all cached entries.
     * Package-private so tests can reset between scenarios. Do NOT call from production code.
     */
    void invalidateCacheForTest() {
        cache.invalidateAll();
    }

    public FilingAggregationResult aggregateForPeriod(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        long startNs = System.nanoTime();

        // Resolve cache key before any heavy work
        OffsetDateTime registryMaxUpdatedAt = resolveRegistryMaxUpdatedAt(tenantId);
        int activeConfigVersion;
        try {
            activeConfigVersion = eprService.getActiveConfigVersion();
        } catch (IllegalStateException e) {
            // Keep the producer-profile 412 pattern: missing active config is a precondition failure,
            // not a server error.
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "No active EPR config found — activate a config before running aggregation");
        }
        AggregationCacheKey cacheKey = new AggregationCacheKey(
                tenantId, periodStart, periodEnd, registryMaxUpdatedAt, activeConfigVersion);

        // Caffeine `get(key, loader)` is single-flight per key: concurrent callers for the same
        // key see exactly one compute. Audit fires only on the miss path so cache hits stay cheap.
        boolean[] miss = {false};
        FilingAggregationResult result = cache.get(cacheKey, k -> {
            miss[0] = true;
            return compute(tenantId, periodStart, periodEnd, activeConfigVersion, startNs);
        });

        if (miss[0]) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            auditService.recordAggregationRun(tenantId, periodStart, periodEnd, durationMs,
                    result.metadata().resolvedLineCount(), result.unresolved().size());
        }

        return result;
    }

    private FilingAggregationResult compute(UUID tenantId, LocalDate periodStart, LocalDate periodEnd,
                                             int activeConfigVersion, long startNs) {
        // Fetch invoice lines for the period
        String taxNumber = dataSourceService.getTenantTaxNumber(tenantId).orElse("");
        InvoiceQueryResult queryResult = dataSourceService.queryInvoices(
                taxNumber, periodStart, periodEnd, InvoiceDirection.OUTBOUND);

        if (!queryResult.serviceAvailable()) {
            log.warn("NAV service unavailable for tenant={} period={}/{} — refusing to cache empty result",
                    tenantId, periodStart, periodEnd);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NAV invoice service temporarily unavailable — please retry shortly");
        }

        record TaggedLine(String invoiceNumber, InvoiceLineItem item) {}
        List<TaggedLine> allTaggedLines = new ArrayList<>();
        for (InvoiceSummary summary : queryResult.summaries()) {
            InvoiceDetail detail = dataSourceService.queryInvoiceDetails(summary.invoiceNumber());
            for (InvoiceLineItem item : detail.lineItems()) {
                allTaggedLines.add(new TaggedLine(summary.invoiceNumber(), item));
            }
        }

        // Bulk load active Registry (one query for all tenant products + components)
        Map<String, List<ComponentRow>> registryByKey = loadRegistry(tenantId);

        // Fee rates from active config
        Map<String, BigDecimal> feeRates = loadFeeRates(activeConfigVersion);

        // Accumulate results
        Map<String, KfTotalAccumulator> kfAccumulators = new LinkedHashMap<>();
        Map<String, SoldProductAccumulator> soldAccumulators = new LinkedHashMap<>();
        List<UnresolvedInvoiceLine> unresolved = new ArrayList<>();
        List<AggregationProvenanceLine> provenanceLines = new ArrayList<>();
        int invoiceLineCount = 0;
        int resolvedLineCount = 0;

        for (TaggedLine tagged : allTaggedLines) {
            String invoiceNumber = tagged.invoiceNumber();
            InvoiceLineItem line = tagged.item();
            invoiceLineCount++;

            String vtszCode = line.vtszCode();
            String description = line.lineDescription() != null ? line.lineDescription() : "";
            BigDecimal quantity = line.quantity();
            String unitOfMeasure = line.unitOfMeasure();

            if (vtszCode == null || vtszCode.isBlank()) continue;

            // AC #3: UNSUPPORTED_UNIT_OF_MEASURE
            if (unitOfMeasure == null || !unitOfMeasure.strip().equalsIgnoreCase("DARAB")) {
                unresolved.add(new UnresolvedInvoiceLine(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, UnresolvedReason.UNSUPPORTED_UNIT_OF_MEASURE));
                provenanceLines.add(AggregationProvenanceLine.unresolved(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, ProvenanceTag.UNSUPPORTED_UNIT));
                continue;
            }

            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) continue;

            // AC #3: NO_MATCHING_PRODUCT
            String registryKey = vtszCode + "~" + description;
            List<ComponentRow> components = registryByKey.get(registryKey);
            if (components == null) {
                unresolved.add(new UnresolvedInvoiceLine(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, UnresolvedReason.NO_MATCHING_PRODUCT));
                provenanceLines.add(AggregationProvenanceLine.unresolved(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, ProvenanceTag.UNRESOLVED));
                continue;
            }

            // AC #3: ZERO_COMPONENTS (product found but no components)
            if (components.isEmpty()) {
                unresolved.add(new UnresolvedInvoiceLine(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, UnresolvedReason.ZERO_COMPONENTS));
                provenanceLines.add(AggregationProvenanceLine.unresolved(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, ProvenanceTag.UNRESOLVED));
                continue;
            }

            // AC #3: VTSZ_FALLBACK — contributes to kfTotals AND appears in unresolved as warning
            boolean hasFallback = components.stream()
                    .anyMatch(c -> "VTSZ_FALLBACK".equals(c.classifierSource()));
            if (hasFallback) {
                unresolved.add(new UnresolvedInvoiceLine(
                        invoiceNumber, line.lineNumber(), vtszCode, description,
                        quantity, unitOfMeasure, UnresolvedReason.VTSZ_FALLBACK));
            }

            // Accumulate sold-product summary
            UUID productId = components.get(0).productId();
            soldAccumulators.computeIfAbsent(
                    productId.toString(),
                    k -> new SoldProductAccumulator(productId, vtszCode, description, unitOfMeasure))
                    .add(quantity);

            // Aggregate components per AC #2 math formula + capture provenance
            boolean contributed = aggregateComponentsWithProvenance(
                    quantity, components, kfAccumulators, hasFallback,
                    invoiceNumber, line.lineNumber(), vtszCode, description, unitOfMeasure,
                    provenanceLines);
            if (contributed) resolvedLineCount++;
        }

        List<KfCodeTotal> kfTotals = buildKfTotals(kfAccumulators, feeRates);
        List<SoldProductLine> soldProducts = soldAccumulators.values().stream()
                .map(SoldProductAccumulator::build)
                .toList();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        AggregationMetadata metadata = new AggregationMetadata(
                invoiceLineCount, resolvedLineCount, activeConfigVersion,
                periodStart, periodEnd, durationMs);

        return new FilingAggregationResult(soldProducts, kfTotals, unresolved, metadata, provenanceLines);
    }

    // ─── AC #2 aggregation math ───────────────────────────────────────────────

    /** Called from tests — delegates to the provenance-capturing variant with a no-op list. */
    boolean aggregateComponents(BigDecimal quantity, List<ComponentRow> components,
                                         Map<String, KfTotalAccumulator> kfAccumulators,
                                         boolean lineFallback) {
        return aggregateComponentsWithProvenance(
                quantity, components, kfAccumulators, lineFallback,
                null, 0, null, null, null, new ArrayList<>());
    }

    private boolean aggregateComponentsWithProvenance(
            BigDecimal quantity, List<ComponentRow> components,
            Map<String, KfTotalAccumulator> kfAccumulators, boolean lineFallback,
            String invoiceNumber, int lineNumber, String vtsz, String description,
            String unitOfMeasure, List<AggregationProvenanceLine> provenanceLines) {

        List<ComponentRow> sorted = components.stream()
                .sorted((a, b) -> Integer.compare(a.componentOrder(), b.componentOrder()))
                .toList();

        Map<Integer, BigDecimal> cumulByLevel = buildCumulByLevel(sorted);
        boolean contributed = false;

        for (ComponentRow comp : sorted) {
            int level = comp.wrappingLevel();

            // AC #7: wrapping_level ∉ {1,2,3}
            if (level < 1 || level > 3) {
                log.warn("Skipping component {} with invalid wrapping_level={}", comp.componentId(), level);
                continue;
            }

            BigDecimal weightPerUnit = comp.weightPerUnitKg();
            BigDecimal itemsPerParent = comp.itemsPerParent();

            // AC #7: defensive bounds
            if (weightPerUnit == null || weightPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Skipping component {} weight_per_unit_kg <= 0: {}", comp.componentId(), weightPerUnit);
                continue;
            }
            if (weightPerUnit.compareTo(MAX_WEIGHT_PER_UNIT) > 0) {
                log.warn("Skipping component {} weight_per_unit_kg > 10000: {}", comp.componentId(), weightPerUnit);
                continue;
            }
            if (itemsPerParent == null || itemsPerParent.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Skipping component {} items_per_parent <= 0: {}", comp.componentId(), itemsPerParent);
                continue;
            }
            if (itemsPerParent.compareTo(MAX_ITEMS_PER_PARENT) > 0) {
                log.warn("Skipping component {} items_per_parent > 10000: {}", comp.componentId(), itemsPerParent);
                continue;
            }

            // buildCumulByLevel emits an entry for every level present in `sorted` (standalone
            // when the direct predecessor is absent — see AC #7 orphaned-chain rule). Combined
            // with the level ∈ [1,3] pre-check above, cumul is guaranteed non-null here.
            BigDecimal cumul = cumulByLevel.get(level);

            // units_at_level_N = Q / cumul[N]
            BigDecimal unitsAtLevel = quantity.divide(cumul, MC);
            // weight_contribution_kg = units_at_level_N × weight_per_unit_kg
            BigDecimal weightContribution = unitsAtLevel.multiply(weightPerUnit, MC);

            String kfCode = comp.kfCode();
            if (kfCode == null || kfCode.isBlank()) continue;

            boolean isFallback = lineFallback || "VTSZ_FALLBACK".equals(comp.classifierSource());
            kfAccumulators.computeIfAbsent(kfCode,
                    k -> new KfTotalAccumulator(k, comp.classificationLabel()))
                    .accumulate(weightContribution, comp.productId(), isFallback);
            contributed = true;

            // Capture per-component provenance (AC #6 sum invariant relies on this)
            if (invoiceNumber != null) {
                ProvenanceTag tag = isFallback ? ProvenanceTag.VTSZ_FALLBACK : ProvenanceTag.REGISTRY_MATCH;
                provenanceLines.add(AggregationProvenanceLine.resolved(
                        invoiceNumber, lineNumber, vtsz, description, quantity, unitOfMeasure,
                        comp.productId(), comp.productName(),
                        comp.componentId(), level, kfCode,
                        weightContribution, tag));
            }
        }
        return contributed;
    }

    Map<Integer, BigDecimal> buildCumulByLevel(List<ComponentRow> sorted) {
        Map<Integer, BigDecimal> levelToItems = new HashMap<>();
        for (ComponentRow c : sorted) {
            levelToItems.put(c.wrappingLevel(), c.itemsPerParent());
        }
        // Only multiply through the direct predecessor level: if L(N-1) is absent,
        // L(N) is standalone per AC #7 ("orphaned chain" rule).
        Map<Integer, BigDecimal> cumul = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            BigDecimal items = levelToItems.get(level);
            if (items == null) continue;
            BigDecimal prev = cumul.get(level - 1);
            if (prev == null) {
                if (level > 1) {
                    log.info("Orphaned component at wrapping_level={} — treating as standalone", level);
                }
                cumul.put(level, items);
            } else {
                cumul.put(level, prev.multiply(items, MC));
            }
        }
        return cumul;
    }

    // ─── AC #4 rounding and fee computation ──────────────────────────────────

    List<KfCodeTotal> buildKfTotals(Map<String, KfTotalAccumulator> accumulators,
                                              Map<String, BigDecimal> feeRates) {
        List<KfCodeTotal> result = new ArrayList<>(accumulators.size());
        for (KfTotalAccumulator acc : accumulators.values()) {
            // AC #4: round totalWeightKg to 3 decimal places HALF_UP
            BigDecimal totalWeightKg = acc.totalWeightKg.setScale(3, RoundingMode.HALF_UP);

            // AC #8: overflow threshold check
            boolean hasOverflow = false;
            if (totalWeightKg.compareTo(OVERFLOW_THRESHOLD) > 0) {
                log.warn("KF-code {} totalWeightKg={} exceeds 100,000,000 kg overflow threshold",
                        acc.kfCode, totalWeightKg);
                hasOverflow = true;
            }

            BigDecimal feeRate = feeRates.getOrDefault(acc.kfCode, BigDecimal.ZERO);
            // AC #4: totalFeeHuf = setScale(0, HALF_UP)
            BigDecimal totalFeeHuf = totalWeightKg.multiply(feeRate, MC)
                    .setScale(0, RoundingMode.HALF_UP);

            result.add(new KfCodeTotal(
                    acc.kfCode, acc.classificationLabel, totalWeightKg, feeRate,
                    totalFeeHuf, acc.contributingProductIds.size(),
                    acc.hasFallback, hasOverflow));
        }
        // Deterministic ordering by KF code (matches the prior KgKgyfNeAggregator behaviour
        // the 10.5 refactor displaced — downstream XML row order and summary listing stay stable).
        result.sort(Comparator.comparing(KfCodeTotal::kfCode));
        return result;
    }

    // ─── Registry bulk load ───────────────────────────────────────────────────

    private Map<String, List<ComponentRow>> loadRegistry(UUID tenantId) {
        Map<String, List<ComponentRow>> byKey = new HashMap<>();
        for (RegistryRepository.AggregationRow row : registryRepository.loadForAggregation(tenantId)) {
            String vtsz = row.vtsz();
            String name = row.name();
            if (vtsz == null || name == null) continue;

            String key = vtsz + "~" + name;
            byKey.computeIfAbsent(key, k -> new ArrayList<>());

            UUID componentId = row.componentId();
            if (componentId == null) continue; // LEFT JOIN null = product has zero components

            byKey.get(key).add(new ComponentRow(
                    componentId,
                    row.productId(),
                    row.kfCode(),
                    defaultInt(row.wrappingLevel(), 1),
                    defaultDecimal(row.itemsPerParent(), BigDecimal.ONE),
                    defaultDecimal(row.weightPerUnitKg(), BigDecimal.ZERO),
                    row.classifierSource(),
                    defaultInt(row.componentOrder(), 0),
                    row.materialDescription(),
                    row.name()
            ));
        }
        return byKey;
    }

    private OffsetDateTime resolveRegistryMaxUpdatedAt(UUID tenantId) {
        return registryRepository.resolveMaxUpdatedAt(tenantId);
    }

    private Map<String, BigDecimal> loadFeeRates(int configVersion) {
        Map<String, BigDecimal> rates = new HashMap<>();
        for (var entry : eprService.getAllKfCodes(configVersion, "hu").entries()) {
            if (entry.kfCode() != null && entry.feeRate() != null) {
                rates.put(entry.kfCode(), entry.feeRate());
            }
        }
        return rates;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static int defaultInt(Integer v, int def) { return v != null ? v : def; }
    private static BigDecimal defaultDecimal(BigDecimal v, BigDecimal def) { return v != null ? v : def; }

    // ─── Internal data types ──────────────────────────────────────────────────

    record ComponentRow(
            UUID componentId,
            UUID productId,
            String kfCode,
            int wrappingLevel,
            BigDecimal itemsPerParent,
            BigDecimal weightPerUnitKg,
            String classifierSource,
            int componentOrder,
            String classificationLabel,
            String productName
    ) {}

    static class KfTotalAccumulator {
        final String kfCode;
        final String classificationLabel;
        BigDecimal totalWeightKg = BigDecimal.ZERO;
        final Set<UUID> contributingProductIds = new HashSet<>();
        boolean hasFallback;

        KfTotalAccumulator(String kfCode, String classificationLabel) {
            this.kfCode = kfCode;
            this.classificationLabel = classificationLabel;
        }

        void accumulate(BigDecimal weight, UUID productId, boolean fallback) {
            totalWeightKg = totalWeightKg.add(weight, MC);
            contributingProductIds.add(productId);
            if (fallback) hasFallback = true;
        }
    }

    private static class SoldProductAccumulator {
        final UUID productId;
        final String vtsz;
        final String description;
        final String unitOfMeasure;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        int lineCount = 0;

        SoldProductAccumulator(UUID productId, String vtsz, String description, String unitOfMeasure) {
            this.productId = productId;
            this.vtsz = vtsz;
            this.description = description;
            this.unitOfMeasure = unitOfMeasure;
        }

        void add(BigDecimal qty) {
            totalQuantity = totalQuantity.add(qty, MC);
            lineCount++;
        }

        SoldProductLine build() {
            return new SoldProductLine(productId, vtsz, description, totalQuantity, unitOfMeasure, lineCount);
        }
    }
}
