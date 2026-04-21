package hu.riskguard.epr.aggregation.domain;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.aggregation.api.UnresolvedReason;
import hu.riskguard.epr.aggregation.api.dto.UnresolvedInvoiceLine;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InvoiceDrivenFilingAggregator}.
 * Covers invoice processing, math formula, unresolved reasons, overflow, fee calculation,
 * cache hit, and audit recording.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDrivenFilingAggregatorTest {

    @Mock
    private RegistryRepository registryRepository;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private EprService eprService;

    @Mock
    private AuditService auditService;

    private InvoiceDrivenFilingAggregator aggregator;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        aggregator = new InvoiceDrivenFilingAggregator(registryRepository, dataSourceService, eprService, auditService);

        // Default: empty registry and fixed cache key timestamp
        when(registryRepository.loadForAggregation(any())).thenReturn(List.of());
        when(registryRepository.resolveMaxUpdatedAt(any()))
                .thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        // Default: no NAV credentials
        when(dataSourceService.getTenantTaxNumber(TENANT)).thenReturn(Optional.empty());
        // Default: config version
        when(eprService.getActiveConfigVersion()).thenReturn(1);
        // Default: empty KF code list (no fee rates)
        when(eprService.getAllKfCodes(anyInt(), any()))
                .thenReturn(new hu.riskguard.epr.api.dto.KfCodeListResponse(1, List.of()));
    }

    // ─── Empty invoices ───────────────────────────────────────────────────────

    @Test
    void emptyInvoices_returnsAllZeroCounts() {
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), true));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.kfTotals()).isEmpty();
        assertThat(result.unresolved()).isEmpty();
        assertThat(result.soldProducts()).isEmpty();
        assertThat(result.metadata().invoiceLineCount()).isZero();
        assertThat(result.metadata().resolvedLineCount()).isZero();
    }

    @Test
    void missingActiveConfig_throws412() {
        when(eprService.getActiveConfigVersion())
                .thenThrow(new IllegalStateException("No active EPR config found"));

        assertThatThrownBy(() -> aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));

        assertThat(aggregator.cacheSizeForTest()).isZero();
    }

    @Test
    void navUnavailable_throws503_resultNotCached() {
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), false));

        assertThatThrownBy(() -> aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        // Cache must remain empty — no stale result stored
        assertThat(aggregator.cacheSizeForTest()).isZero();
    }

    // ─── VTSZ filtering ───────────────────────────────────────────────────────

    @Test
    void lineWithNullVtsz_isSkipped_notAddedToUnresolved() {
        setupSingleInvoiceLine(line(null, "100", "DARAB"));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved()).isEmpty();
        assertThat(result.metadata().invoiceLineCount()).isEqualTo(1);
    }

    @Test
    void lineWithBlankVtsz_isSkipped() {
        setupSingleInvoiceLine(line("  ", "100", "DARAB"));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved()).isEmpty();
    }

    // ─── Unsupported unit of measure ─────────────────────────────────────────

    @Test
    void lineWithNonDarabUnit_isUnsupportedUnitOfMeasure() {
        setupSingleInvoiceLine(line("39239090", "100", "KG"));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().get(0).reason()).isEqualTo(UnresolvedReason.UNSUPPORTED_UNIT_OF_MEASURE);
    }

    @Test
    void lineWithNullUnit_isUnsupportedUnitOfMeasure() {
        setupSingleInvoiceLine(line("39239090", "100", null));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().get(0).reason()).isEqualTo(UnresolvedReason.UNSUPPORTED_UNIT_OF_MEASURE);
    }

    // ─── Zero/negative quantity ───────────────────────────────────────────────

    @Test
    void lineWithZeroQuantity_isSkipped() {
        setupSingleInvoiceLine(line("39239090", "0", "DARAB"));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        // Line gets counted in invoiceLineCount (DARAB unit passes gate first),
        // but zero quantity causes early exit before NO_MATCHING_PRODUCT
        assertThat(result.kfTotals()).isEmpty();
    }

    // ─── NO_MATCHING_PRODUCT ─────────────────────────────────────────────────

    @Test
    void lineWithVtszFallback_contributesToKfTotalsAndAppearsInUnresolvedAsWarning() {
        // AC #3 dual presence: VTSZ_FALLBACK lines DO contribute to kfTotals (hasFallback=true)
        // AND appear in `unresolved` as a warning entry.
        String vtsz = "39239090";
        String description = "Polyolefin film bag";
        setupSingleInvoiceLine(lineWithDescription(vtsz, description, "100", "DARAB"));

        UUID componentId = UUID.randomUUID();
        when(registryRepository.loadForAggregation(TENANT)).thenReturn(List.of(
                new RegistryRepository.AggregationRow(
                        PRODUCT_ID, vtsz, description, "REVIEWED",
                        componentId, "11010101", 1,
                        BigDecimal.ONE, new BigDecimal("0.025"),
                        "VTSZ_FALLBACK", 0, "PET label")
        ));
        // Fee rate present so totalFeeHuf is non-zero
        when(eprService.getAllKfCodes(anyInt(), any()))
                .thenReturn(new hu.riskguard.epr.api.dto.KfCodeListResponse(1, List.of(
                        new hu.riskguard.epr.api.dto.KfCodeEntry(
                                "11010101", "PET", new BigDecimal("150.00"),
                                "HUF", "Műanyag", "PET label"))));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved())
                .as("VTSZ_FALLBACK line must appear in unresolved as a warning")
                .extracting(UnresolvedInvoiceLine::reason)
                .containsExactly(UnresolvedReason.VTSZ_FALLBACK);

        assertThat(result.kfTotals())
                .as("VTSZ_FALLBACK line must still contribute weight to kfTotals")
                .hasSize(1);
        KfCodeTotal total = result.kfTotals().get(0);
        assertThat(total.kfCode()).isEqualTo("11010101");
        assertThat(total.hasFallback()).isTrue();
        // Q=100, items=1, weight=0.025 → 100 × 0.025 = 2.500
        assertThat(total.totalWeightKg()).isEqualByComparingTo("2.500");
    }

    @Test
    void lineWithNoRegistryMatch_isNoMatchingProduct() {
        setupSingleInvoiceLine(line("39239090", "100", "DARAB"));
        // Default: DSL deep stub returns empty iterable → empty registry

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().get(0).reason()).isEqualTo(UnresolvedReason.NO_MATCHING_PRODUCT);
    }

    // ─── buildCumulByLevel direct tests ──────────────────────────────────────

    @Test
    void buildCumulByLevel_singleLevel1_returnsItemsPerParent() {
        List<InvoiceDrivenFilingAggregator.ComponentRow> components = List.of(
                component("11010101", "0.025", 1, new BigDecimal("6"))
        );

        Map<Integer, BigDecimal> cumul = aggregator.buildCumulByLevel(components);

        assertThat(cumul.get(1)).isEqualByComparingTo("6");
        assertThat(cumul).doesNotContainKey(2);
        assertThat(cumul).doesNotContainKey(3);
    }

    @Test
    void buildCumulByLevel_level1And2_returnsMultiplied() {
        List<InvoiceDrivenFilingAggregator.ComponentRow> components = List.of(
                component("11010101", "0.025", 1, new BigDecimal("6")),
                component("41010201", "0.050", 2, new BigDecimal("4"))
        );

        Map<Integer, BigDecimal> cumul = aggregator.buildCumulByLevel(components);

        assertThat(cumul.get(1)).isEqualByComparingTo("6");
        // level-2 cumul = items@1 × items@2 = 6 × 4 = 24
        assertThat(cumul.get(2)).isEqualByComparingTo("24");
    }

    @Test
    void buildCumulByLevel_allThreeLevels_returnsCorrectCumulative() {
        List<InvoiceDrivenFilingAggregator.ComponentRow> components = List.of(
                component("11010101", "0.025", 1, new BigDecimal("6")),
                component("41010201", "0.050", 2, new BigDecimal("4")),
                component("52010101", "0.300", 3, new BigDecimal("2"))
        );

        Map<Integer, BigDecimal> cumul = aggregator.buildCumulByLevel(components);

        assertThat(cumul.get(1)).isEqualByComparingTo("6");
        assertThat(cumul.get(2)).isEqualByComparingTo("24");
        // level-3 cumul = 6 × 4 × 2 = 48
        assertThat(cumul.get(3)).isEqualByComparingTo("48");
    }

    @Test
    void buildCumulByLevel_onlyLevel2_treatedAsStandalone() {
        List<InvoiceDrivenFilingAggregator.ComponentRow> components = List.of(
                component("41010201", "0.050", 2, new BigDecimal("10"))
        );

        Map<Integer, BigDecimal> cumul = aggregator.buildCumulByLevel(components);

        assertThat(cumul).doesNotContainKey(1);
        assertThat(cumul.get(2)).isEqualByComparingTo("10");
    }

    @Test
    void buildCumulByLevel_level1AndLevel3_gapAtLevel2_level3Standalone() {
        // AC #7 orphaned chain: L2 missing → L3 is standalone (cumul[3] = 2), not 6 × 2 = 12.
        List<InvoiceDrivenFilingAggregator.ComponentRow> components = List.of(
                component("11010101", "0.025", 1, new BigDecimal("6")),
                component("52010101", "0.300", 3, new BigDecimal("2"))
        );

        Map<Integer, BigDecimal> cumul = aggregator.buildCumulByLevel(components);

        assertThat(cumul.get(1)).isEqualByComparingTo("6");
        assertThat(cumul).doesNotContainKey(2);
        assertThat(cumul.get(3)).isEqualByComparingTo("2");
    }

    // ─── aggregateComponents direct tests (math formula) ─────────────────────

    @Test
    void aggregateComponents_level1_singleComponent_correctWeight() {
        // Q=100, w=0.025, items=1 → weight = 100 × 0.025 / 1 = 2.5
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "0.025", 1, BigDecimal.ONE)
        );

        boolean contributed = aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(contributed).isTrue();
        assertThat(kfAcc).containsKey("11010101");
        assertThat(kfAcc.get("11010101").totalWeightKg).isEqualByComparingTo("2.5");
    }

    @Test
    void aggregateComponents_level1_sixPackRatio_correctWeight() {
        // Q=100, w=0.050, items=6 → weight = 100 × 0.050 / 6 = 0.833333...
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("41010201", "0.050", 1, new BigDecimal("6"))
        );

        aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        // Production formula: units_at_level = Q / cumul, then weight = units × w (divide first, then multiply)
        BigDecimal unitsAtLevel = new BigDecimal("100").divide(new BigDecimal("6"), InvoiceDrivenFilingAggregator.MC);
        BigDecimal expected = unitsAtLevel.multiply(new BigDecimal("0.050"), InvoiceDrivenFilingAggregator.MC);
        assertThat(kfAcc.get("41010201").totalWeightKg).isEqualByComparingTo(expected);
    }

    @Test
    void aggregateComponents_invalidWrappingLevel4_isSkipped() {
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "0.025", 4, BigDecimal.ONE) // invalid level
        );

        boolean contributed = aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(contributed).isFalse();
        assertThat(kfAcc).isEmpty();
    }

    @Test
    void aggregateComponents_zeroWeight_isSkipped() {
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "0.000", 1, BigDecimal.ONE)
        );

        aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(kfAcc).isEmpty();
    }

    @Test
    void aggregateComponents_oversizeWeight_isSkipped() {
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "10001", 1, BigDecimal.ONE) // > 10000
        );

        aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(kfAcc).isEmpty();
    }

    @Test
    void aggregateComponents_zeroItemsPerParent_isSkipped() {
        // AC #7: items_per_parent ≤ 0 → skip + WARN (would otherwise be /0)
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "0.025", 1, BigDecimal.ZERO)
        );

        boolean contributed = aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(contributed).isFalse();
        assertThat(kfAcc).isEmpty();
    }

    @Test
    void aggregateComponents_oversizeItemsPerParent_isSkipped() {
        // AC #7: items_per_parent > 10,000 → skip + WARN
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> kfAcc = new LinkedHashMap<>();
        List<InvoiceDrivenFilingAggregator.ComponentRow> comps = List.of(
                component("11010101", "0.025", 1, new BigDecimal("10001"))
        );

        boolean contributed = aggregator.aggregateComponents(new BigDecimal("100"), comps, kfAcc, false);

        assertThat(contributed).isFalse();
        assertThat(kfAcc).isEmpty();
    }

    // ─── buildKfTotals direct tests ───────────────────────────────────────────

    @Test
    void buildKfTotals_overflowThreshold_setsOverflowFlag() {
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> accMap = new HashMap<>();
        InvoiceDrivenFilingAggregator.KfTotalAccumulator acc =
                new InvoiceDrivenFilingAggregator.KfTotalAccumulator("11010101", "PET");
        acc.accumulate(new BigDecimal("100000001"), PRODUCT_ID, false); // > 100M
        accMap.put("11010101", acc);

        List<KfCodeTotal> totals = aggregator.buildKfTotals(accMap, Map.of());

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).hasOverflowWarning()).isTrue();
    }

    @Test
    void buildKfTotals_feeRateApplied_correctTotalFee() {
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> accMap = new HashMap<>();
        InvoiceDrivenFilingAggregator.KfTotalAccumulator acc =
                new InvoiceDrivenFilingAggregator.KfTotalAccumulator("11010101", "PET");
        acc.accumulate(new BigDecimal("10"), PRODUCT_ID, false);
        accMap.put("11010101", acc);

        Map<String, BigDecimal> feeRates = Map.of("11010101", new BigDecimal("150.00"));
        List<KfCodeTotal> totals = aggregator.buildKfTotals(accMap, feeRates);

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).totalWeightKg()).isEqualByComparingTo("10.000");
        assertThat(totals.get(0).feeRateHufPerKg()).isEqualByComparingTo("150.00");
        // 10 × 150 = 1500, setScale(0, HALF_UP) = 1500
        assertThat(totals.get(0).totalFeeHuf()).isEqualByComparingTo("1500");
    }

    @Test
    void buildKfTotals_weightAtBoundary0005_roundsUp() {
        // AC #4: totalWeightKg rounded to 3 decimals HALF_UP. At X.0005 → X.001 (rounds up).
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> accMap = new HashMap<>();
        InvoiceDrivenFilingAggregator.KfTotalAccumulator acc =
                new InvoiceDrivenFilingAggregator.KfTotalAccumulator("11010101", "PET");
        acc.accumulate(new BigDecimal("1.0005"), PRODUCT_ID, false);
        accMap.put("11010101", acc);

        List<KfCodeTotal> totals = aggregator.buildKfTotals(accMap, Map.of());

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).totalWeightKg()).isEqualByComparingTo("1.001");
    }

    @Test
    void buildKfTotals_weightAtBoundary0004_roundsDown() {
        // AC #4: totalWeightKg rounded to 3 decimals HALF_UP. At X.0004 → X.000 (rounds down).
        Map<String, InvoiceDrivenFilingAggregator.KfTotalAccumulator> accMap = new HashMap<>();
        InvoiceDrivenFilingAggregator.KfTotalAccumulator acc =
                new InvoiceDrivenFilingAggregator.KfTotalAccumulator("11010101", "PET");
        acc.accumulate(new BigDecimal("1.0004"), PRODUCT_ID, false);
        accMap.put("11010101", acc);

        List<KfCodeTotal> totals = aggregator.buildKfTotals(accMap, Map.of());

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).totalWeightKg()).isEqualByComparingTo("1.000");
    }

    // ─── Caching and audit ────────────────────────────────────────────────────

    @Test
    void secondCallWithSameParams_returnsCachedResult_auditCalledOnce() {
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), true));

        aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);
        aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        // Audit should be called exactly once (the second call is a cache hit)
        verify(auditService, times(1)).recordAggregationRun(
                eq(TENANT), eq(Q1_START), eq(Q1_END), anyLong(), anyInt(), anyInt());
    }

    @Test
    void auditCalledAfterNonCachedExecution() {
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), true));

        aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        verify(auditService).recordAggregationRun(
                eq(TENANT), eq(Q1_START), eq(Q1_END), anyLong(), anyInt(), anyInt());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setupSingleInvoiceLine(InvoiceLineItem lineItem) {
        InvoiceSummary summary = new InvoiceSummary(
                "INV-001", "CREATE", "12345678", "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND);
        InvoiceDetail detail = new InvoiceDetail(
                "INV-001", "CREATE", "12345678", "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND,
                List.of(lineItem), "TRANSFER", Map.of());

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-001")).thenReturn(detail);
    }

    private static InvoiceLineItem line(String vtsz, String quantity, String unit) {
        return lineWithDescription(vtsz, "Test product", quantity, unit);
    }

    private static InvoiceLineItem lineWithDescription(String vtsz, String description,
                                                        String quantity, String unit) {
        return new InvoiceLineItem(
                1, description,
                quantity != null ? new BigDecimal(quantity) : BigDecimal.ONE,
                unit, BigDecimal.ONE,
                quantity != null ? new BigDecimal(quantity) : BigDecimal.ONE,
                quantity != null ? new BigDecimal(quantity) : BigDecimal.ONE,
                vtsz, "VTSZ", vtsz);
    }

    private static InvoiceDrivenFilingAggregator.ComponentRow component(
            String kfCode, String weight, int wrappingLevel, BigDecimal itemsPerParent) {
        return new InvoiceDrivenFilingAggregator.ComponentRow(
                UUID.randomUUID(), PRODUCT_ID, kfCode,
                wrappingLevel, itemsPerParent, new BigDecimal(weight),
                null, wrappingLevel, "Test label", "Test Product");
    }
}
