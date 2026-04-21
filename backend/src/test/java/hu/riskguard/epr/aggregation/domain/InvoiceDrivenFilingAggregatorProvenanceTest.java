package hu.riskguard.epr.aggregation.domain;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.api.dto.ProvenanceTag;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Provenance-specific unit tests for {@link InvoiceDrivenFilingAggregator} (Story 10.8 AC #31).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Sum invariant: provenance Σ weightContributionKg per kfCode equals kfTotals weight
 *   <li>UNRESOLVED + UNSUPPORTED_UNIT lines have weightContributionKg = 0
 *   <li>provenanceTag assignment: REGISTRY_MATCH, VTSZ_FALLBACK, UNRESOLVED, UNSUPPORTED_UNIT
 *   <li>Total row count == invoice-line count (with single-component test data)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDrivenFilingAggregatorProvenanceTest {

    @Mock private RegistryRepository registryRepository;
    @Mock private DataSourceService dataSourceService;
    @Mock private EprService eprService;
    @Mock private AuditService auditService;

    private InvoiceDrivenFilingAggregator aggregator;

    private static final UUID TENANT      = UUID.randomUUID();
    private static final UUID PRODUCT_ID  = UUID.randomUUID();
    private static final UUID PRODUCT_ID2 = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END   = LocalDate.of(2026, 3, 31);
    private static final String KF_CODE = "1001 01 00";
    private static final String KF_CODE2 = "1002 01 00";

    @BeforeEach
    void setUp() {
        aggregator = new InvoiceDrivenFilingAggregator(
                registryRepository, dataSourceService, eprService, auditService);

        when(registryRepository.resolveMaxUpdatedAt(any())).thenReturn(OffsetDateTime.now());
        when(eprService.getActiveConfigVersion()).thenReturn(1);
        when(eprService.getAllKfCodes(anyInt(), anyString()))
                .thenReturn(new hu.riskguard.epr.api.dto.KfCodeListResponse(1, List.of()));
        when(dataSourceService.getTenantTaxNumber(any())).thenReturn(Optional.of("12345678-1-01"));
    }

    @Test
    void provenanceTag_registryMatch_forNormalProduct() {
        setupInvoiceLines(List.of(lineItem(1, "73181500", "Screw", "2.000", "DARAB")));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE, "0.002", 1, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(1);
        AggregationProvenanceLine prov = result.provenanceLines().get(0);
        assertThat(prov.provenanceTag()).isEqualTo(ProvenanceTag.REGISTRY_MATCH);
        assertThat(prov.componentKfCode()).isEqualTo(KF_CODE);
        assertThat(prov.weightContributionKg()).isPositive();
        assertThat(prov.resolvedProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void provenanceTag_vtszFallback_forFallbackClassifier() {
        setupInvoiceLines(List.of(lineItem(1, "73181500", "Screw", "2.000", "DARAB")));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE, "0.002", 1, "VTSZ_FALLBACK"))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(1);
        assertThat(result.provenanceLines().get(0).provenanceTag()).isEqualTo(ProvenanceTag.VTSZ_FALLBACK);
    }

    @Test
    void provenanceTag_unresolved_forNoMatchingProduct() {
        setupInvoiceLines(List.of(lineItem(1, "99999999", "Unknown", "1.000", "DARAB")));
        setupRegistry(Map.of());

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(1);
        AggregationProvenanceLine prov = result.provenanceLines().get(0);
        assertThat(prov.provenanceTag()).isEqualTo(ProvenanceTag.UNRESOLVED);
        assertThat(prov.weightContributionKg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(prov.componentId()).isNull();
        assertThat(prov.resolvedProductId()).isNull();
    }

    @Test
    void provenanceTag_unsupportedUnit_forNonDarab() {
        setupInvoiceLines(List.of(lineItem(1, "73181500", "Screw", "1.000", "KG")));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE, "0.002", 1, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(1);
        AggregationProvenanceLine prov = result.provenanceLines().get(0);
        assertThat(prov.provenanceTag()).isEqualTo(ProvenanceTag.UNSUPPORTED_UNIT);
        assertThat(prov.weightContributionKg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumInvariant_provenanceWeightEqualsKfTotal() {
        // 2 invoice lines for the same product (single component)
        setupInvoiceLines(List.of(
                lineItem(1, "73181500", "Screw", "100.000", "DARAB"),
                lineItem(2, "73181500", "Screw", "200.000", "DARAB")
        ));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE, "0.005", 1, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        BigDecimal provenanceSum = result.provenanceLines().stream()
                .filter(p -> KF_CODE.equals(p.componentKfCode()))
                .map(AggregationProvenanceLine::weightContributionKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal kfTotalWeight = result.kfTotals().stream()
                .filter(k -> KF_CODE.equals(k.kfCode()))
                .findFirst()
                .map(hu.riskguard.epr.aggregation.api.dto.KfCodeTotal::totalWeightKg)
                .orElse(BigDecimal.ZERO);

        // provenance uses 4-decimal; kfTotal uses 3-decimal — compare at 3 decimal places
        assertThat(provenanceSum.setScale(3, RoundingMode.HALF_UP))
                .isEqualByComparingTo(kfTotalWeight);
    }

    @Test
    void totalRowCount_equalsComponentInvoicePairCount_singleComponent() {
        // AC #5 (reworded): one provenance row per component-invoice pair.
        // 4 invoice lines, each matched product has exactly one component → 4 rows.
        setupInvoiceLines(List.of(
                lineItem(1, "73181500", "Screw",  "10.000", "DARAB"),
                lineItem(2, "39233000", "Bottle", "5.000",  "DARAB"),
                lineItem(3, "99999999", "Unknown","1.000",  "DARAB"),
                lineItem(4, "73181500", "Screw",  "1.000",  "KG")
        ));
        setupRegistry(Map.of(
                "73181500~Screw",  List.of(aggRow(PRODUCT_ID,  "73181500", "Screw",  KF_CODE,  "0.002", 1, null)),
                "39233000~Bottle", List.of(aggRow(PRODUCT_ID2, "39233000", "Bottle", KF_CODE2, "0.01",  1, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(4);
    }

    @Test
    void totalRowCount_equalsComponentInvoicePairCount_multiComponent() {
        // AC #5 (reworded): product with 2 packaging components yields 2 rows per invoice line.
        // 2 invoice lines × 2 components = 4 provenance rows.
        setupInvoiceLines(List.of(
                lineItem(1, "73181500", "Screw", "10.000", "DARAB"),
                lineItem(2, "73181500", "Screw", "20.000", "DARAB")
        ));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(
                        aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE,  "0.002", 1, null),
                        aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE2, "0.005", 2, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        assertThat(result.provenanceLines()).hasSize(4);
    }

    @Test
    void unresolvedAndUnsupportedUnit_haveZeroWeight() {
        setupInvoiceLines(List.of(
                lineItem(1, "99999999", "Unknown", "1.000", "DARAB"),
                lineItem(2, "73181500", "Screw",   "1.000", "KG")
        ));
        setupRegistry(Map.of(
                "73181500~Screw", List.of(aggRow(PRODUCT_ID, "73181500", "Screw", KF_CODE, "0.002", 1, null))
        ));

        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        result.provenanceLines().forEach(p -> {
            if (p.provenanceTag() == ProvenanceTag.UNRESOLVED
                    || p.provenanceTag() == ProvenanceTag.UNSUPPORTED_UNIT) {
                assertThat(p.weightContributionKg())
                        .as("Weight must be zero for %s", p.provenanceTag())
                        .isEqualByComparingTo(BigDecimal.ZERO);
            }
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setupInvoiceLines(List<InvoiceLineItem> items) {
        InvoiceSummary summary = new InvoiceSummary(
                "INV-001", "CREATE", "12345678", "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND);
        InvoiceDetail detail = new InvoiceDetail(
                "INV-001", "CREATE", "12345678", "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND,
                items, "TRANSFER", Map.of());

        when(dataSourceService.queryInvoices(anyString(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-001")).thenReturn(detail);
    }

    private void setupRegistry(Map<String, List<RegistryRepository.AggregationRow>> data) {
        List<RegistryRepository.AggregationRow> allRows = new ArrayList<>();
        for (var entry : data.entrySet()) {
            if (entry.getValue().isEmpty()) {
                String[] parts = entry.getKey().split("~", 2);
                allRows.add(new RegistryRepository.AggregationRow(
                        UUID.randomUUID(), parts[0], parts.length > 1 ? parts[1] : "",
                        "APPROVED", null, null, null, null, null, null, null, null));
            } else {
                allRows.addAll(entry.getValue());
            }
        }
        when(registryRepository.loadForAggregation(TENANT)).thenReturn(allRows);
    }

    private static RegistryRepository.AggregationRow aggRow(
            UUID productId, String vtsz, String name, String kfCode,
            String weight, int level, String source) {
        return new RegistryRepository.AggregationRow(
                productId, vtsz, name, "APPROVED",
                UUID.randomUUID(), kfCode, level,
                BigDecimal.ONE, new BigDecimal(weight),
                source, level, "Material");
    }

    private static InvoiceLineItem lineItem(int lineNo, String vtsz, String description,
                                            String quantity, String unit) {
        return new InvoiceLineItem(
                lineNo, description, new BigDecimal(quantity), unit,
                BigDecimal.ONE, new BigDecimal(quantity), new BigDecimal(quantity),
                vtsz, "VTSZ", vtsz);
    }
}
